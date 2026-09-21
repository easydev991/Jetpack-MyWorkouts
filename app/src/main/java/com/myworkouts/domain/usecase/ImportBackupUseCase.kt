// `TooManyFunctions` подавлен осознанно: файл — один use-case и его
// чистые помощники парсинга/merge (app-data-screen, design Decisions 4–5);
// декомпозиция по файлам усложнит связку «парсер + merge + invoke».
@file:Suppress("TooManyFunctions")

package com.myworkouts.domain.usecase

import android.content.Context
import android.net.Uri
import com.myworkouts.crash.CrashlyticsHelper
import com.myworkouts.data.repository.EntryValidator
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.CancellationException

/**
 * Восстановление данных из файла резервной копии слиянием
 * (app-data-screen, design Decisions 3–5).
 *
 * Читает файл через `context.contentResolver.openInputStream(uri)` на
 * [Dispatchers.IO], целиком парсит и валидирует его до любой записи в БД
 * (битый файл не может изменить данные частично), мержит с текущими
 * данными (ничего не перезаписывается, добавляется только недостающее)
 * и вставляет подготовленное одной транзакцией
 * [WorkoutDataRepository.importData].
 *
 * @property repository доступ к данным приложения
 * @property context контекст приложения; поток открывается через
 *   `contentResolver` (в [com.myworkouts.ui.viewmodel.AppDataViewModel]
 *   передаётся `context.applicationContext`)
 */
class ImportBackupUseCase(
    private val repository: WorkoutDataRepository,
    private val context: Context
) {
    /**
     * Сливает данные файла по [uri] с текущими: ошибки файла —
     * `Result.failure([BackupException])` с [BackupError.INVALID_FILE],
     * ошибки БД — как есть.
     *
     * `ReturnCount` и `TooGenericExceptionCaught` подавлены осознанно:
     * early-return на каждом шаге конвейера «чтение → парсинг → снимок →
     * вставка» (по design Decision 5) и барьер «любая ошибка чтения →
     * BackupException» требуют общий `catch` и несколько точек выхода.
     */
    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    suspend operator fun invoke(uri: Uri): Result<Unit> {
        val raw =
            try {
                withContext(Dispatchers.IO) {
                    val stream =
                        context.contentResolver.openInputStream(uri)
                            ?: throw BackupException(
                                "Поток чтения недоступен: $uri",
                                BackupError.INVALID_FILE
                            )
                    stream.use { it.readBytes().toString(Charsets.UTF_8) }
                }
            } catch (e: CancellationException) {
                // Отмена корутины — не ошибка: rethrow строго до
                // логирования (спека «Отмена корутины не является ошибкой»).
                throw e
            } catch (e: BackupException) {
                CrashlyticsHelper.logException(e, "Восстановление резервной копии: сбой чтения файла")
                return Result.failure(e)
            } catch (e: Exception) {
                CrashlyticsHelper.logException(e, "Восстановление резервной копии: сбой чтения файла")
                return Result.failure(
                    BackupException(
                        "Не удалось прочитать файл: ${e.message}",
                        BackupError.INVALID_FILE
                    )
                )
            }
        val (fileExercises, fileEntries) =
            try {
                parseBackupJson(raw)
            } catch (e: BackupException) {
                CrashlyticsHelper.logException(e, "Восстановление резервной копии: некорректный файл копии")
                return Result.failure(e)
            }
        val existingExercises =
            repository.getAllExercises().getOrElse { error ->
                return Result.failure(error)
            }
        val existingEntries =
            repository.getAllEntries().getOrElse { error ->
                return Result.failure(error)
            }
        val (exercisesToInsert, entriesToInsert, idsToArchive) =
            mergeBackup(existingExercises, existingEntries, fileExercises, fileEntries)
        val importResult = repository.importData(exercisesToInsert, entriesToInsert)
        importResult.getOrElse { error ->
            return Result.failure(error)
        }
        // Односторонняя архивация после успешного слияния (design Decision 2):
        // id взяты из снимка, сделанного в этом же вызове, поэтому сбой
        // архивации практически недостижим и проваливает импорт loud'ом;
        // повторный импорт безопасен — merge идемпотентен по построению.
        idsToArchive.forEach { id ->
            repository.setArchived(id, true).getOrElse { error ->
                return Result.failure(error)
            }
        }
        return importResult
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
 * Чистая функция merge (design Decision 4): возвращает тройку «упражнения к
 * вставке, записи к вставке, id существующих упражнений к архивации».
 * Упражнения — отсутствующие в текущем каталоге по ключу
 * `name.trim().lowercase()`, всегда с `isCustom = true` (файл недоверенный)
 * и `id = 0` (присвоит БД). Записи — с разрешённым по имени упражнением,
 * прошедшие [EntryValidator] против его `measuredBy` и не дублирующие по
 * содержимому ни текущие, уже взятые из этого же файла. «Плохие» элементы
 * пропускаются без провала всего импорта. Третий элемент — односторонняя
 * архивация (design Decision 1): id существующих упражнений, которые в файле
 * помечены `isArchived = true`, а сами активны; разархивация из файла
 * не выполняется.
 */
internal fun mergeBackup(
    existingExercises: List<Exercise>,
    existingEntries: List<ExerciseEntry>,
    fileExercises: List<Exercise>,
    fileEntries: List<BackupEntry>
): Triple<List<Exercise>, List<BackupEntry>, List<Long>> {
    val existingByName =
        existingExercises.associateBy { it.name.trim().lowercase() }
    val exercisesToInsert =
        fileExercises
            .filter { it.name.trim().lowercase() !in existingByName }
            .distinctBy { it.name.trim().lowercase() }
            .map { it.copy(isCustom = true, id = 0L) }
    val idsToArchive =
        fileExercises
            .filter { it.isArchived }
            .mapNotNull { existingByName[it.name.trim().lowercase()] }
            .filter { !it.isArchived }
            .map { it.id }
            .distinct()
    val measuredByKey =
        buildMap {
            existingExercises.forEach { put(it.name.trim().lowercase(), it.measuredBy) }
            exercisesToInsert.forEach { put(it.name.trim().lowercase(), it.measuredBy) }
        }
    val namesById = existingExercises.associate { it.id to it.name }
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
        val dedupKey =
            EntryContentKey(
                exerciseKey = key,
                date = probe.date,
                sets = probe.sets,
                reps = probe.reps,
                weightKg = probe.weightKg,
                durationSec = probe.durationSec,
                comment = probe.comment
            )
        if (!seenEntryKeys.add(dedupKey)) {
            return@forEach
        }
        entriesToInsert.add(backupEntry)
    }
    return Triple(exercisesToInsert, entriesToInsert, idsToArchive)
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

/**
 * Чистая функция парсинга файла резервной копии (design Decision 5):
 * строгая валидация структуры и типов, неизвестные поля игнорируются.
 * Любая ошибка формата — [BackupException] с [BackupError.INVALID_FILE].
 *
 * `ThrowsCount` подавлен осознанно: валидация формата — по одному
 * `throw` на правило Decision 5, иначе потеряется точная причина отказа.
 * `SwallowedException` — исключение используется (его `message` попадает
 * в диагностику), правило не распознаёт доступ через свойство.
 */
@Suppress("ThrowsCount", "SwallowedException")
internal fun parseBackupJson(raw: String): Pair<List<Exercise>, List<BackupEntry>> {
    val root =
        try {
            JSONObject(raw)
        } catch (e: JSONException) {
            throw invalidFile("Файл не является корректным JSON: ${e.message}")
        }
    val version = root.opt("version")
    if (version !is Int || version != 1) {
        throw invalidFile("Поле version отсутствует, не целое или не поддерживается: $version")
    }
    val exercisesArray =
        root.optJSONArray("exercises")
            ?: throw invalidFile("Поле exercises отсутствует или не массив")
    val entriesArray =
        root.optJSONArray("entries")
            ?: throw invalidFile("Поле entries отсутствует или не массив")
    val exercises =
        (0 until exercisesArray.length()).map { index ->
            parseExercise(exercisesArray.optJSONObject(index) ?: throw invalidFile("Элемент exercises не объект"))
        }
    val entries =
        (0 until entriesArray.length()).map { index ->
            parseEntry(entriesArray.optJSONObject(index) ?: throw invalidFile("Элемент entries не объект"))
        }
    return exercises to entries
}

private fun parseExercise(obj: JSONObject): Exercise =
    Exercise(
        id = 0L,
        name = obj.requiredString("name"),
        category = obj.requiredEnum("category", ExerciseCategory.entries),
        isCustom =
            obj.opt("isCustom") as? Boolean
                ?: throw invalidFile("Поле isCustom упражнения отсутствует или не Boolean"),
        comment = obj.optionalString("comment"),
        iconNumber = obj.optionalInt("iconNumber"),
        createdAt = obj.requiredLong("createdAt"),
        measuredBy = obj.requiredEnum("measuredBy", ExerciseMeasuredBy.entries),
        isArchived = obj.optionalBoolean("isArchived") ?: false
    )

private fun parseEntry(obj: JSONObject): BackupEntry =
    BackupEntry(
        exerciseName = obj.requiredString("exerciseName"),
        date = obj.requiredLong("date"),
        sets = obj.requiredInt("sets"),
        reps = obj.optionalInt("reps"),
        weightKg = obj.optionalDouble("weightKg"),
        durationSec = obj.optionalInt("durationSec"),
        comment = obj.optionalString("comment"),
        createdAt = obj.requiredLong("createdAt")
    )

private fun invalidFile(message: String): BackupException = BackupException(message, BackupError.INVALID_FILE)

private fun JSONObject.requiredString(key: String): String {
    val value = opt(key)
    if (value !is String || value.isBlank()) {
        throw invalidFile("Поле $key отсутствует, не строка или пустое")
    }
    return value
}

private fun JSONObject.requiredLong(key: String): Long {
    val value = opt(key)
    return when {
        value is Long -> value
        value is Int -> value.toLong()
        else -> throw invalidFile("Поле $key отсутствует или не целое число")
    }
}

private fun JSONObject.requiredInt(key: String): Int {
    val value = opt(key)
    return when {
        value is Int -> value
        value is Long -> value.toInt()
        else -> throw invalidFile("Поле $key отсутствует или не целое число")
    }
}

private fun <T : Enum<T>> JSONObject.requiredEnum(
    key: String,
    values: Collection<T>
): T {
    val raw = opt(key)
    return (raw as? String)?.let { name -> values.firstOrNull { it.name == name } }
        ?: throw invalidFile("Поле $key отсутствует или имеет неизвестное значение: $raw")
}

private fun JSONObject.optionalString(key: String): String? {
    val value = opt(key)
    return when {
        value == null || value === JSONObject.NULL -> null
        value is String -> value
        else -> throw invalidFile("Поле $key не строка")
    }
}

private fun JSONObject.optionalInt(key: String): Int? {
    val value = opt(key)
    return when {
        value == null || value === JSONObject.NULL -> null
        value is Int -> value
        value is Long -> value.toInt()
        else -> throw invalidFile("Поле $key не целое число")
    }
}

private fun JSONObject.optionalDouble(key: String): Double? {
    val value = opt(key)
    return when {
        value == null || value === JSONObject.NULL -> null
        value is Double -> value
        else -> throw invalidFile("Поле $key не число")
    }
}

private fun JSONObject.optionalBoolean(key: String): Boolean? {
    val value = opt(key)
    return when {
        value == null || value === JSONObject.NULL -> null
        value is Boolean -> value
        else -> throw invalidFile("Поле $key не Boolean")
    }
}
