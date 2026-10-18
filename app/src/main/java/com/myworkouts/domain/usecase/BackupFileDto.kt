package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.BackupSettings
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.model.OverviewTab
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

/**
 * Формат файла резервной копии (app-data-screen, design Decisions 2 и 5):
 * DTO зеркалят JSON-схему v1 (ключи, порядок полей, явные null для
 * отсутствующих значений), доменные модели в файл не пишутся — файл
 * недоверенный при импорте.
 *
 * Порядок свойств [BackupFileDto] задает порядок ключей в файле при
 * экспорте (kotlinx-serialization пишет свойства по порядку объявления).
 */
@Serializable
private data class BackupFileDto(
    val version: Int,
    val exercises: List<ExerciseDto>,
    // categories[] и settings — обратно совместимые расширения (design D9,
    // overview-screen D8): в бэкапах v1 отсутствуют
    val categories: List<CategoryDto> = emptyList(),
    val entries: List<EntryDto>,
    // settings — JsonElement, а не DTO: блок разбирается вручную с
    // толерантностью прежнего org.json-парсера (parseBackupSettings) —
    // неверно типизированный блок игнорируется, а не отвергает файл
    val settings: JsonElement? = null
)

@Serializable
private data class ExerciseDto(
    val name: String,
    val category: String,
    val measuredBy: ExerciseMeasuredBy,
    val isCustom: Boolean,
    val comment: String? = null,
    val iconNumber: Int? = null,
    val createdAt: Long,
    val isArchived: Boolean = false
)

@Serializable
private data class CategoryDto(
    val name: String,
    val colorArgb: Int,
    val isArchived: Boolean
)

@Serializable
private data class EntryDto(
    val exerciseName: String,
    val date: Long,
    val sets: Int,
    val reps: Int? = null,
    val weightKg: Double? = null,
    val durationSec: Int? = null,
    val comment: String? = null,
    val createdAt: Long
)

/** Экспорт: человекочитаемый файл, отступ 2, null и значения по умолчанию пишутся явно. */
private val backupWriteFormat =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

/** Импорт: толерантный парсинг — неизвестные поля игнорируются (design Decision 5). */
private val backupReadFormat = Json { ignoreUnknownKeys = true }

/**
 * Результат разбора файла бэкапа: категории, каталог, записи и блок настроек.
 */
internal data class BackupFileContent(
    val categories: List<Category>,
    val exercises: List<Exercise>,
    val entries: List<BackupEntry>,
    val settings: BackupSettings?
)

/**
 * Чистая функция сборки JSON резервной копии (design Decision 2): связь
 * записей — по имени упражнения; id, exerciseId, workoutId, modifiedAt и
 * isCustom категорий в файл не пишутся. Отступ 2, явные null — стабильная
 * форма файла; isArchived упражнений и пустой categories[] пишутся всегда.
 */
internal fun buildBackupJson(
    exercises: List<Exercise>,
    entries: List<ExerciseEntry>,
    settings: BackupSettings = BackupSettings.defaults(),
    categories: List<Category> = emptyList()
): String {
    val namesById = exercises.associate { it.id to it.name }
    val file =
        BackupFileDto(
            version = 1,
            exercises =
                exercises.map { exercise ->
                    ExerciseDto(
                        name = exercise.name,
                        category = exercise.category,
                        measuredBy = exercise.measuredBy,
                        isCustom = exercise.isCustom,
                        comment = exercise.comment,
                        iconNumber = exercise.iconNumber,
                        createdAt = exercise.createdAt,
                        isArchived = exercise.isArchived
                    )
                },
            categories =
                categories.map { category ->
                    CategoryDto(
                        name = category.name,
                        colorArgb = category.colorArgb,
                        isArchived = category.isArchived
                    )
                },
            entries =
                entries.map { entry ->
                    val exerciseName =
                        checkNotNull(namesById[entry.exerciseId]) {
                            "Запись id=${entry.id} ссылается на упражнение " +
                                "id=${entry.exerciseId}, которого нет в снимке каталога"
                        }
                    EntryDto(
                        exerciseName = exerciseName,
                        date = entry.date,
                        sets = entry.sets,
                        reps = entry.reps,
                        weightKg = entry.weightKg,
                        durationSec = entry.durationSec,
                        comment = entry.comment,
                        createdAt = entry.createdAt
                    )
                },
            settings = buildSettingsJson(settings)
        )
    return backupWriteFormat.encodeToString(BackupFileDto.serializer(), file)
}

/**
 * Секция `settings` файла (overview-screen, design D8): карта видимости
 * вкладок и последняя вкладка. Отдельная функция — [buildBackupJson]
 * остаётся в лимите `LongMethod`.
 */
private fun buildSettingsJson(settings: BackupSettings): JsonObject =
    buildJsonObject {
        put(
            "overviewTabVisibility",
            buildJsonObject {
                settings.tabVisibility.forEach { (tab, visible) -> put(tab.name, visible) }
            }
        )
        put("lastOverviewTab", settings.lastOverviewTab.name)
    }

/**
 * Чистая функция парсинга файла резервной копии (design Decision 5):
 * строгая валидация структуры; типы — с расширениями декодера (ниже),
 * неизвестные поля игнорируются. Неверно типизированный блок `settings`
 * файл не отвергает — блок игнорируется (см. `parseBackupSettings`).
 * Любая ошибка формата — [BackupException] с [BackupError.INVALID_FILE].
 *
 * Проверки поверх декодера (сохранён контракт прежнего org.json-парсера):
 * поддерживаемая версия (1 или 2), непустые обязательные строки
 * (name/category/exerciseName). Расширения толерантности декодера
 * kotlinx (сужения нет): JSON-целое в Double-поле (`"weightKg": 75` →
 * 75.0) и числовые значения в строковых литералах (`"sets": "4"` → 4)
 * прежний парсер отвергал — экспорт такие формы не пишет.
 *
 * `SwallowedException` подавлен осознанно: исключение используется (его
 * `message` попадает в диагностику), правило не распознаёт доступ через
 * свойство. `ThrowsCount` подавлен осознанно: валидация формата — по
 * одному `throw` на правило Decision 5, иначе потеряется точная причина
 * отказа. Декомпозиция — по урокам JetpackDays (detekt `LongMethod`).
 */
@Suppress("SwallowedException", "ThrowsCount")
internal fun parseBackupJson(raw: String): BackupFileContent {
    val file =
        try {
            backupReadFormat.decodeFromString(BackupFileDto.serializer(), raw)
        } catch (e: SerializationException) {
            throw invalidFile("Файл не является корректным JSON бэкапа: ${e.message}")
        } catch (e: IllegalArgumentException) {
            throw invalidFile("Файл не является корректным JSON: ${e.message}")
        }
    if (file.version != 1 && file.version != 2) {
        throw invalidFile("Поле version отсутствует, не целое или не поддерживается: ${file.version}")
    }
    validateRequiredStrings(file)
    return BackupFileContent(
        categories = file.categories.map { it.toDomainCategory() },
        exercises = file.exercises.map { it.toDomainExercise() },
        entries = file.entries.map { it.toDomainBackupEntry() },
        settings = parseBackupSettings(file.settings)
    )
}

/**
 * Обязательные строки файла не бывают пустыми (Decision 5): name и
 * category упражнения, exerciseName записи, name категории.
 *
 * `ThrowsCount` подавлен осознанно: по одному `throw` на правило —
 * точная причина отказа в диагностике.
 */
@Suppress("ThrowsCount")
private fun validateRequiredStrings(file: BackupFileDto) {
    file.exercises.forEach { exercise ->
        if (exercise.name.isBlank()) {
            throw invalidFile("Поле name упражнения отсутствует, не строка или пустое")
        }
        if (exercise.category.isBlank()) {
            throw invalidFile("Поле category отсутствует, не строка или пустое")
        }
    }
    file.entries.forEach { entry ->
        if (entry.exerciseName.isBlank()) {
            throw invalidFile("Поле exerciseName отсутствует, не строка или пустое")
        }
    }
    file.categories.forEach { category ->
        if (category.name.isBlank()) {
            throw invalidFile("Поле name категории отсутствует, не строка или пустое")
        }
    }
}

/** isCustom из файла игнорируется: файл недоверенный, вставленные категории всегда пользовательские (design D9). */
private fun CategoryDto.toDomainCategory(): Category =
    Category(
        id = 0L,
        name = name,
        colorArgb = colorArgb,
        isCustom = true,
        isArchived = isArchived
    )

/** Категория — имя-строка без enum-валидации (задача 3.2): резолв по (БД ∪ файл) — в mergeBackup (design D9). */
private fun ExerciseDto.toDomainExercise(): Exercise =
    Exercise(
        id = 0L,
        name = name,
        category = category,
        isCustom = isCustom,
        comment = comment,
        iconNumber = iconNumber,
        createdAt = createdAt,
        measuredBy = measuredBy,
        isArchived = isArchived
    )

private fun EntryDto.toDomainBackupEntry(): BackupEntry =
    BackupEntry(
        exerciseName = exerciseName,
        date = date,
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        comment = comment,
        createdAt = createdAt
    )

/**
 * Блок `settings` разбирается вручную поверх [JsonElement] — толерантность
 * прежнего org.json-парсера сохранена целиком (overview-screen, design D8;
 * Decision 5): неверно типизированный блок не отвергает файл, а
 * игнорируется — целиком (`settings` не объект → `null`, DataStore не
 * трогается), по карте (`overviewTabVisibility` не объект → пустая карта)
 * или по значению (не-Boolean значение вкладки и объект в
 * `lastOverviewTab` → значение отброшено). Неизвестные имена вкладок
 * отбрасывались и прежним парсером.
 */
private fun parseBackupSettings(element: JsonElement?): BackupSettings? {
    val settings = element as? JsonObject ?: return null
    val tabVisibility =
        (settings["overviewTabVisibility"] as? JsonObject)
            ?.mapNotNull { (name, value) ->
                OverviewTab.entries.firstOrNull { it.name == name }?.let { tab ->
                    (value as? JsonPrimitive)?.booleanOrNull?.let { tab to it }
                }
            }?.toMap()
            ?: emptyMap()
    val lastOverviewTab =
        (settings["lastOverviewTab"] as? JsonPrimitive)
            ?.contentOrNull
            ?.let { raw -> OverviewTab.entries.firstOrNull { it.name == raw } }
            ?: OverviewTab.STATS
    return BackupSettings(tabVisibility = tabVisibility, lastOverviewTab = lastOverviewTab)
}

private fun invalidFile(message: String): BackupException = BackupException(message, BackupError.INVALID_FILE)
