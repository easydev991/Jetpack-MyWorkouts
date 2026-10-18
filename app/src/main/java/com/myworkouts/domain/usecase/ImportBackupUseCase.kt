// `TooManyFunctions` подавлен осознанно: файл — один use-case и его
// чистые помощники merge (app-data-screen, design Decisions 4–5);
// декомпозиция по файлам усложнит связку «merge + invoke». Парсинг
// формата — в BackupFileDto.kt (kotlinx-serialization).
@file:Suppress("TooManyFunctions")

package com.myworkouts.domain.usecase

import com.myworkouts.domain.crash.CrashReporter
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.BackupSettings
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.repository.EntryValidator
import com.myworkouts.domain.repository.WorkoutDataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

/**
 * Восстановление данных из файла резервной копии слиянием
 * (app-data-screen, design Decisions 3–5 — сигнатуры без Android:
 * opener-фабрика потока — параметр вызова, как в экспорте).
 *
 * Файл, открытый [openStream], читается целиком и парсится на
 * [Dispatchers.IO]; открытие и закрытие (`use`) — в одном блоке без
 * точки отмены между ними, иначе отмена корутины в зазоре оставила бы
 * открытый SAF-поток до финализатора. Валидация до любой записи в БД
 * (битый файл не может изменить данные частично), мержит с текущими
 * данными (ничего не перезаписывается, добавляется только недостающее),
 * вставляет подготовленное одной транзакцией
 * [WorkoutDataRepository.importData]. Блок настроек вкладок обзора из
 * бэкапов версии 2 (overview-screen, design D8) возвращается снапшотом
 * — применение к хранилищу (после успешного восстановления) остаётся
 * за вызывающим (`AppSettingsDataStore.applyBackupSettings`); `null` —
 * бэкап v1, хранилище не трогать.
 *
 * @property repository доступ к данным приложения
 * @property crashReporter репортер сбоев: failure-ветки чтения и парсинга
 */
class ImportBackupUseCase(
    private val repository: WorkoutDataRepository,
    private val crashReporter: CrashReporter
) {
    /**
     * Сливает данные файла, открытого [openStream], с текущими и возвращает
     * снапшот настроек: ошибки файла —
     * `Result.failure([BackupException])` с [BackupError.INVALID_FILE]
     * (включая `null` от [openStream] — документ недоступен),
     * ошибки БД — как есть.
     *
     * `ReturnCount` и `TooGenericExceptionCaught` подавлены осознанно:
     * early-return на каждом шаге конвейера «чтение → парсинг → снимок →
     * вставка» (по design Decision 5) и барьер «любая ошибка чтения →
     * BackupException» требуют общий `catch` и несколько точек выхода.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    suspend operator fun invoke(openStream: suspend () -> InputStream?): Result<BackupSettings?> {
        val raw =
            try {
                withContext(Dispatchers.IO) {
                    val stream =
                        openStream() ?: throw BackupException(
                            "Поток чтения недоступен",
                            BackupError.INVALID_FILE
                        )
                    stream.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            } catch (e: CancellationException) {
                // Отмена корутины — не ошибка: rethrow строго до
                // логирования (спека «Отмена корутины не является ошибкой»).
                throw e
            } catch (e: BackupException) {
                crashReporter.logException(e, "Восстановление резервной копии: сбой чтения файла")
                return Result.failure(e)
            } catch (e: Exception) {
                crashReporter.logException(e, "Восстановление резервной копии: сбой чтения файла")
                return Result.failure(
                    BackupException(
                        "Не удалось прочитать файл: ${e.message}",
                        BackupError.INVALID_FILE
                    )
                )
            }
        val fileContent =
            try {
                // Парсинг на IO вместе с чтением: разбор мегабайтного
                // JSON не должен замораживать main (вызывающий — viewModelScope)
                withContext(Dispatchers.IO) { parseBackupJson(raw) }
            } catch (e: BackupException) {
                crashReporter.logException(e, "Восстановление резервной копии: некорректный файл копии")
                return Result.failure(e)
            }
        mergeAndInsert(fileContent).getOrElse { error ->
            return Result.failure(error)
        }
        // Настройки вкладок применяет вызывающий после восстановления
        // (design D8); `null` (бэкап v1) — DataStore не меняется.
        return Result.success(fileContent.settings)
    }

    /**
     * Снимки репозитория → merge → вставка одной транзакцией
     * [WorkoutDataRepository.importData] → односторонняя архивация
     * (design Decision 2 и D9: категории — по правилу упражнений).
     * id для архивации взяты из снимков, сделанных в этом же вызове,
     * поэтому сбой архивации практически недостижим и проваливает импорт
     * loud'ом; повторный импорт безопасен — merge идемпотентен
     * по построению. Вынесено из [invoke] — detekt `LongMethod`.
     *
     * `ReturnCount` подавлен осознанно: early-return на каждом шаге
     * конвейера «снимки → merge → вставка → архивация» — тот же
     * контракт, что у [invoke].
     */
    @Suppress("ReturnCount")
    private suspend fun mergeAndInsert(fileContent: BackupFileContent): Result<Unit> {
        val existingCategories =
            repository.getAllCategories().getOrElse { error ->
                return Result.failure(error)
            }
        val existingExercises =
            repository.getAllExercises().getOrElse { error ->
                return Result.failure(error)
            }
        val existingEntries =
            repository.getAllEntries().getOrElse { error ->
                return Result.failure(error)
            }
        val merge =
            mergeBackup(
                existingCategories = existingCategories,
                existingExercises = existingExercises,
                existingEntries = existingEntries,
                file = fileContent
            )
        repository
            .importData(merge.categoriesToInsert, merge.exercisesToInsert, merge.entriesToInsert)
            .getOrElse { error ->
                return Result.failure(error)
            }
        merge.categoryIdsToArchive.forEach { id ->
            repository.setCategoryArchived(id, true).getOrElse { error ->
                return Result.failure(error)
            }
        }
        merge.exerciseIdsToArchive.forEach { id ->
            repository.setArchived(id, true).getOrElse { error ->
                return Result.failure(error)
            }
        }
        return Result.success(Unit)
    }
}

/**
 * Ключ дедупликации записи по содержимому (design Decision 4): имя
 * упражнения (trim + lowercase), дата, подходы, повторы, вес, длительность,
 * комментарий — без id и `createdAt`.
 */
private data class EntryContentKey(
    val exerciseKey: String,
    val date: Long,
    val sets: Int,
    val reps: Int?,
    val weightKg: Double?,
    val durationSec: Int?,
    val comment: String?
)

/**
 * Результат merge (custom-categories, design D9): списки к вставке
 * (категории, упражнения, записи) и id существующих сущностей к
 * односторонней архивации — категории и упражнения по одному правилу.
 */
internal data class BackupMergeResult(
    val categoriesToInsert: List<Category>,
    val exercisesToInsert: List<Exercise>,
    val entriesToInsert: List<BackupEntry>,
    val categoryIdsToArchive: List<Long>,
    val exerciseIdsToArchive: List<Long>
)

/**
 * Чистая функция merge (design Decision 4 и D9). Категории —
 * отсутствующие в БД по ключу `name.trim().lowercase()` (и внутри файла),
 * всегда с `isCustom = true` (файл недоверенный) и `id = 0`; односторонняя
 * архивация существующих — по правилу упражнений. Упражнения — категория
 * резолвится по ключу (БД ∪ файл) в каноническое имя; неизвестная
 * категория — упражнение пропускается (и его записи — автоматически, они
 * не найдут упражнения в списке вставки); отсутствующие в текущем
 * каталоге по ключу имени, всегда с `isCustom = true` и `id = 0`. Записи —
 * с разрешённым по имени упражнением, прошедшие [EntryValidator] против
 * его `measuredBy` и не дублирующие по содержимому ни текущие, уже взятые
 * из этого же файла. «Плохие» элементы пропускаются без провала всего
 * импорта.
 */
internal fun mergeBackup(
    existingCategories: List<Category>,
    existingExercises: List<Exercise>,
    existingEntries: List<ExerciseEntry>,
    file: BackupFileContent
): BackupMergeResult {
    val existingCategoryByKey =
        existingCategories.associateBy { it.name.trim().lowercase() }
    val categoriesToInsert =
        file.categories
            .filter { it.name.trim().lowercase() !in existingCategoryByKey }
            .distinctBy { it.name.trim().lowercase() }
            .map { it.copy(id = 0L, isCustom = true) }
    val categoryIdsToArchive =
        file.categories
            .filter { it.isArchived }
            .mapNotNull { existingCategoryByKey[it.name.trim().lowercase()] }
            .filter { !it.isArchived }
            .map { it.id }
            .distinct()
    // Резолв категории упражнения: ключ → каноническое имя (БД ∪ файл);
    // ключ совпадает с дедупом категорий (trim + lowercase, design D9).
    val categoryNamesByKey =
        existingCategoryByKey.mapValues { it.value.name } +
            categoriesToInsert.associate { it.name.trim().lowercase() to it.name }
    val resolvableFileExercises =
        file.exercises.mapNotNull { exercise ->
            val categoryName =
                categoryNamesByKey[exercise.category.trim().lowercase()]
                    ?: return@mapNotNull null
            exercise.copy(category = categoryName)
        }
    val existingByName =
        existingExercises.associateBy { it.name.trim().lowercase() }
    val exercisesToInsert =
        resolvableFileExercises
            .filter { it.name.trim().lowercase() !in existingByName }
            .distinctBy { it.name.trim().lowercase() }
            .map { it.copy(isCustom = true, id = 0L) }
    val exerciseIdsToArchive =
        resolvableFileExercises
            .filter { it.isArchived }
            .mapNotNull { existingByName[it.name.trim().lowercase()] }
            .filter { !it.isArchived }
            .map { it.id }
            .distinct()
    val measuredByKey =
        existingExercises.associate { it.name.trim().lowercase() to it.measuredBy } +
            exercisesToInsert.associate { it.name.trim().lowercase() to it.measuredBy }
    val namesById = existingExercises.associate { it.id to it.name }
    val entriesToInsert = newFileEntries(existingEntries, file.entries, measuredByKey, namesById)
    return BackupMergeResult(
        categoriesToInsert = categoriesToInsert,
        exercisesToInsert = exercisesToInsert,
        entriesToInsert = entriesToInsert,
        categoryIdsToArchive = categoryIdsToArchive,
        exerciseIdsToArchive = exerciseIdsToArchive
    )
}

/**
 * Отбирает записи файла к вставке (design Decision 4): прошедшие
 * [EntryValidator] против `measuredBy` упражнения, найденного по имени,
 * и не дублирующие по содержимому ни текущие записи, ни уже взятые из
 * этого же файла. «Плохие» записи пропускаются без провала всего
 * импорта.
 */
private fun newFileEntries(
    existingEntries: List<ExerciseEntry>,
    fileEntries: List<BackupEntry>,
    measuredByKey: Map<String, ExerciseMeasuredBy>,
    namesById: Map<Long, String>
): List<BackupEntry> {
    val seenEntryKeys = mutableSetOf<EntryContentKey>()
    existingEntries.forEach { entry ->
        val name = namesById[entry.exerciseId] ?: return@forEach
        seenEntryKeys.add(entry.contentKey(name))
    }
    val entriesToInsert = mutableListOf<BackupEntry>()
    fileEntries.forEach { backupEntry ->
        val key = backupEntry.exerciseName.trim().lowercase()
        val measuredBy = measuredByKey[key] ?: return@forEach
        val probe =
            ExerciseEntry(
                id = 0L,
                exerciseId = -1L,
                workoutId = null,
                date = backupEntry.date,
                sets = backupEntry.sets,
                reps = backupEntry.reps,
                weightKg = backupEntry.weightKg,
                durationSec = backupEntry.durationSec,
                comment = backupEntry.comment,
                createdAt = backupEntry.createdAt,
                modifiedAt = System.currentTimeMillis()
            )
        if (EntryValidator.validate(probe, measuredBy).isFailure) {
            return@forEach
        }
        val dedupKey = probe.contentKey(key)
        if (!seenEntryKeys.add(dedupKey)) {
            return@forEach
        }
        entriesToInsert.add(backupEntry)
    }
    return entriesToInsert
}

private fun ExerciseEntry.contentKey(name: String): EntryContentKey =
    EntryContentKey(
        exerciseKey = name.trim().lowercase(),
        date = date,
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        comment = comment
    )
