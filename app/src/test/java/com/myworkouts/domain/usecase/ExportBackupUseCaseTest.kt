package com.myworkouts.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupSettings
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.model.OverviewTab
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.concurrent.CancellationException

/**
 * Unit-тесты формата экспорта (app-data-screen, design Decisions 2–3):
 * сборка JSON проверяется напрямую через [buildBackupJson] — чистая функция,
 * SAF-обвязка не нужна; `invoke` — через mockk (Decision 8).
 */
class ExportBackupUseCaseTest {
    private val pullUps =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = null,
            createdAt = 1_757_371_100_000L
        )
    private val plank =
        Exercise(
            id = 2L,
            name = "Планка",
            category = ExerciseCategory.CORE,
            isCustom = true,
            comment = "Держать ровно",
            createdAt = 1_757_371_150_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )
    private val repsEntry =
        ExerciseEntry(
            id = 5L,
            exerciseId = 1L,
            workoutId = null,
            date = 1_757_371_200_000L,
            sets = 4,
            reps = 10,
            weightKg = null,
            durationSec = null,
            comment = null,
            createdAt = 1_757_371_200_000L,
            modifiedAt = 1_757_371_300_000L
        )
    private val timeEntry =
        ExerciseEntry(
            id = 6L,
            exerciseId = 2L,
            workoutId = null,
            date = 1_757_371_250_000L,
            sets = 3,
            reps = null,
            weightKg = null,
            durationSec = 90,
            comment = "Спина прямая",
            createdAt = 1_757_371_250_000L,
            modifiedAt = 1_757_371_350_000L
        )

    @Test
    fun buildBackupJson_when_data_present_then_version_and_arrays() {
        // Given
        val exercises = listOf(pullUps, plank)
        val entries = listOf(repsEntry, timeEntry)

        // When
        val json = JSONObject(buildBackupJson(exercises, entries))

        // Then — формат v1: settings и categories — обратно совместимые
        // расширения, версию не поднимают (канон custom-categories, Decision 9)
        assertEquals(1, json.getInt("version"), "Версия формата — 1")
        assertEquals(2, json.getJSONArray("exercises").length(), "Все упражнения каталога в файле")
        assertEquals(2, json.getJSONArray("entries").length(), "Все записи в файле")
        val settingsJson = checkNotNull(json.optJSONObject("settings")) { "Блок settings присутствует" }
        assertEquals("STATS", settingsJson.optString("lastOverviewTab"), "Дефолтная последняя вкладка")
    }

    @Test
    fun buildBackupJson_when_exercise_present_then_all_schema_fields() {
        // Given
        val exercises = listOf(pullUps, plank)

        // When
        val fileExercises =
            JSONObject(buildBackupJson(exercises, emptyList()))
                .getJSONArray("exercises")

        // Then
        val exportedPullUps = fileExercises.getJSONObject(0)
        assertEquals("Подтягивания", exportedPullUps.getString("name"), "Имя упражнения")
        assertEquals("PULL", exportedPullUps.getString("category"), "Категория — имя enum-значения")
        assertEquals("REPS", exportedPullUps.getString("measuredBy"), "Тип измерения — имя enum-значения")
        assertFalse(exportedPullUps.getBoolean("isCustom"), "Признак пользовательского упражнения")
        assertEquals(1_757_371_100_000L, exportedPullUps.getLong("createdAt"), "Момент создания")

        val exportedPlank = fileExercises.getJSONObject(1)
        assertEquals("TIME", exportedPlank.getString("measuredBy"), "Второй тип измерения сериализуется")
        assertTrue(exportedPlank.getBoolean("isCustom"), "Пользовательское упражнение")
        assertEquals("Держать ровно", exportedPlank.getString("comment"), "Комментарий упражнения")
    }

    @Test
    fun buildBackupJson_when_iconNumberPresent_then_serializedAsNumber() {
        // Given — упражнение с назначенной иконкой 5
        // (app-data-management, spec-сценарий «Номер иконки в файле»)
        val withIcon = pullUps.copy(iconNumber = 5)

        // When
        val exported =
            JSONObject(buildBackupJson(listOf(withIcon), emptyList()))
                .getJSONArray("exercises")
                .getJSONObject(0)

        // Then — номер иконки попал в файл числом
        assertEquals(5, exported.getInt("iconNumber"), "Номер иконки сериализуется как Int")
    }

    @Test
    fun buildBackupJson_when_iconNumberAbsent_then_explicitNull() {
        // Given — упражнение без иконки (iconNumber = null по умолчанию)
        // (spec-сценарий «Номер иконки в файле»: поле пишется всегда)

        // When
        val exported =
            JSONObject(buildBackupJson(listOf(pullUps), emptyList()))
                .getJSONArray("exercises")
                .getJSONObject(0)

        // Then — null сериализуется явно, поле присутствует (стабильная форма файла)
        assertTrue(exported.has("iconNumber"), "Поле iconNumber пишется всегда, даже как null")
        assertTrue(exported.isNull("iconNumber"), "Отсутствующая иконка — явный null")
    }

    @Test
    fun buildBackupJson_when_entry_present_then_linked_by_name_without_db_ids() {
        // Given
        val entries = listOf(repsEntry, timeEntry)

        // When
        val fileEntries =
            JSONObject(buildBackupJson(listOf(pullUps, plank), entries))
                .getJSONArray("entries")

        // Then
        val exportedReps = fileEntries.getJSONObject(0)
        assertEquals("Подтягивания", exportedReps.getString("exerciseName"), "Связь записи — по имени упражнения")
        assertEquals(1_757_371_200_000L, exportedReps.getLong("date"), "Дата выполнения")
        assertEquals(4, exportedReps.getInt("sets"), "Количество подходов")
        assertEquals(10, exportedReps.getInt("reps"), "Количество повторов")
        assertEquals(1_757_371_200_000L, exportedReps.getLong("createdAt"), "Момент создания записи")

        val exportedTime = fileEntries.getJSONObject(1)
        assertEquals("Планка", exportedTime.getString("exerciseName"), "Связь второй записи")
        assertEquals(90, exportedTime.getInt("durationSec"), "Длительность")
        assertEquals("Спина прямая", exportedTime.getString("comment"), "Комментарий записи")

        listOf(exportedReps, exportedTime).forEach { entryJson ->
            assertFalse(entryJson.has("id"), "id строки БД в файле быть не должно")
            assertFalse(entryJson.has("exerciseId"), "exerciseId в файле быть не должно")
            assertFalse(entryJson.has("workoutId"), "workoutId в файле быть не должно")
            assertFalse(entryJson.has("modifiedAt"), "modifiedAt в файле быть не должно")
        }
    }

    @Test
    fun buildBackupJson_when_optional_fields_null_then_explicitly_serialized() {
        // Given
        val entries = listOf(repsEntry, timeEntry)

        // When
        val fileEntries =
            JSONObject(buildBackupJson(listOf(pullUps, plank), entries))
                .getJSONArray("entries")

        // Then
        val exportedReps = fileEntries.getJSONObject(0)
        assertTrue(exportedReps.has("weightKg"), "null-вес сериализуется явно — стабильная форма файла")
        assertTrue(exportedReps.isNull("weightKg"), "Отсутствующий вес — null")
        assertTrue(exportedReps.has("durationSec"), "null-длительность сериализуется явно")
        assertTrue(exportedReps.isNull("durationSec"), "Отсутствующая длительность — null")
        assertTrue(exportedReps.has("comment"), "null-комментарий записи сериализуется явно")
        assertTrue(exportedReps.isNull("comment"), "Отсутствующий комментарий — null")

        val exportedTime = fileEntries.getJSONObject(1)
        assertTrue(exportedTime.has("reps"), "null-повторы сериализуются явно")
        assertTrue(exportedTime.isNull("reps"), "У упражнения на время повторы — null")
    }

    @Test
    fun buildBackupJson_when_built_then_indented_with_two_spaces() {
        // Given
        val exercises = listOf(pullUps)
        val entries = listOf(repsEntry)

        // When
        val raw = buildBackupJson(exercises, entries)

        // Then
        assertTrue(
            raw.contains("\n  \"version\": 1"),
            "Файл человекочитаем: отступ 2 (JSONObject.toString(2))"
        )
    }

    @Test
    fun buildBackupJson_when_exerciseArchived_then_isArchivedSerializedAsTrue() {
        // Given — архивное упражнение (spec-сценарий «Признак архивации в файле»)
        val archived = pullUps.copy(isArchived = true)

        // When
        val exported =
            JSONObject(buildBackupJson(listOf(archived), emptyList()))
                .getJSONArray("exercises")
                .getJSONObject(0)

        // Then
        assertTrue(
            exported.has("isArchived"),
            "Поле isArchived пишется всегда, даже для архивных — стабильная форма файла"
        )
        assertTrue(
            exported.getBoolean("isArchived"),
            "Архивное упражнение сериализуется как isArchived = true"
        )
    }

    @Test
    fun buildBackupJson_when_exerciseActive_then_isArchivedSerializedAsFalse() {
        // Given — активное упражнение (isArchived = false по умолчанию,
        // spec-сценарий «Признак архивации в файле»)
        val active = pullUps.copy(isArchived = false)

        // When
        val exported =
            JSONObject(buildBackupJson(listOf(active), emptyList()))
                .getJSONArray("exercises")
                .getJSONObject(0)

        // Then
        assertTrue(
            exported.has("isArchived"),
            "Поле isArchived пишется всегда, и для активных"
        )
        assertFalse(
            exported.getBoolean("isArchived"),
            "Активное упражнение сериализуется как isArchived = false"
        )
    }

    @Test
    fun buildBackupJson_when_empty_data_then_valid_file_with_empty_arrays() {
        // Given
        val exercises = emptyList<Exercise>()
        val entries = emptyList<ExerciseEntry>()

        // When
        val json = JSONObject(buildBackupJson(exercises, entries))

        // Then
        assertEquals(1, json.getInt("version"), "Пустой экспорт — тоже версия 1")
        assertEquals(0, json.getJSONArray("exercises").length(), "Пустой каталог — пустой массив")
        assertEquals(0, json.getJSONArray("entries").length(), "Нет записей — пустой массив")
    }

    @Test
    fun invoke_when_repository_fails_then_failure_and_no_stream() =
        runTest {
            // Given
            val dbError = RuntimeException("Сбой базы")
            val repository = mockk<WorkoutDataRepository>(relaxed = true)
            coEvery { repository.getAllExercises() } returns Result.failure(dbError)
            coEvery { repository.getAllEntries() } returns Result.failure(dbError)
            val contentResolver = mockk<ContentResolver>(relaxed = true)
            val context = mockk<Context>(relaxed = true)
            every { context.contentResolver } returns contentResolver
            val uri = mockk<Uri>()
            val useCase = ExportBackupUseCase(repository, mockk(relaxed = true), context)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(result.isFailure, "Ошибка репозитория — Result.failure")
            assertTrue(
                result.exceptionOrNull() === dbError,
                "Ошибка БД проходит в Result как есть, не оборачивается"
            )
            verify(exactly = 0) { contentResolver.openOutputStream(any()) }
        }

    @Test
    fun invoke_when_cancelled_then_rethrows_cancellation() =
        runTest {
            // Given — SAF-поток записи прерывается отменой корутины
            // (спека «Отмена корутины не является ошибкой»)
            val repository = mockk<WorkoutDataRepository>(relaxed = true)
            coEvery { repository.getAllExercises() } returns Result.success(listOf(pullUps))
            coEvery { repository.getAllEntries() } returns Result.success(listOf(repsEntry))
            val dataStore = mockk<AppSettingsDataStore>()
            every { dataStore.overviewTabVisibility } returns
                flowOf(OverviewTab.entries.associateWith { true })
            every { dataStore.lastOverviewTab } returns flowOf(OverviewTab.STATS)
            val contentResolver = mockk<ContentResolver>(relaxed = true)
            val context = mockk<Context>(relaxed = true)
            every { context.contentResolver } returns contentResolver
            every { contentResolver.openOutputStream(any()) } throws
                CancellationException("Операция отменена")
            val uri = mockk<Uri>()
            val useCase = ExportBackupUseCase(repository, dataStore, context)

            // When / Then — CancellationException прокидывается дальше,
            // не превращаясь в Result.failure
            assertThrows<CancellationException> { useCase(uri) }
        }

    // ---------- overview-screen: настройки вкладок в бэкапе (design D8) ----------

    @Test
    fun buildBackupJson_when_settings_present_then_version2_with_settings_block() {
        // Given — RECORDS скрыта, последняя вкладка ACTIVITY
        val settings =
            BackupSettings(
                tabVisibility = OverviewTab.entries.associateWith { it != OverviewTab.RECORDS },
                lastOverviewTab = OverviewTab.ACTIVITY
            )

        // When
        val json = JSONObject(buildBackupJson(listOf(pullUps), listOf(repsEntry), settings))

        // Then
        assertEquals(1, json.getInt("version"), "Настройки вкладок — расширение без подъёма версии")
        val settingsJson =
            checkNotNull(json.optJSONObject("settings")) { "Блок settings присутствует" }
        val visibility =
            checkNotNull(settingsJson.optJSONObject("overviewTabVisibility")) { "Карта видимости" }
        assertEquals(false, visibility.optBoolean("RECORDS"), "Скрытая вкладка — false")
        assertEquals(true, visibility.optBoolean("STATS"), "Видимая вкладка — true")
        assertEquals("ACTIVITY", settingsJson.optString("lastOverviewTab"), "Последняя вкладка")
    }

    @Test
    fun buildBackupJson_when_default_settings_then_all_visible_and_stats() {
        // Given — настройки по умолчанию (все вкладки видимы, STATS активна)

        // When
        val json = JSONObject(buildBackupJson(emptyList(), emptyList(), BackupSettings.defaults()))

        // Then
        assertEquals(1, json.getInt("version"))
        val visibility =
            checkNotNull(
                json.optJSONObject("settings")?.optJSONObject("overviewTabVisibility")
            ) { "Карта видимости" }
        assertEquals(
            OverviewTab.entries.associateWith { true },
            OverviewTab.entries.associateWith { visibility.optBoolean(it.name) },
            "Все вкладки сериализованы как видимые"
        )
        assertEquals(
            "STATS",
            checkNotNull(json.optJSONObject("settings")) { "Блок settings" }.optString("lastOverviewTab")
        )
    }
}
