package com.myworkouts.domain.usecase

import com.myworkouts.crash.NoopCrashReporter
import com.myworkouts.data.db.standardCategories
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.domain.repository.WorkoutDataRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * Unit-тесты восстановления из резервной копии (app-data-screen, design
 * Decisions 4–5): merge-алгоритм и ошибки файла проверяются на уровне
 * `invoke` — файл подаётся потоком из строки
 * (ByteArrayInputStream).
 */
class ImportBackupUseCaseTest {
    private val pullUps =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = "PULL",
            isCustom = false,
            comment = null,
            createdAt = 1_757_371_100_000L
        )
    private val existingEntry =
        ExerciseEntry(
            id = 9L,
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

    private val plankFile =
        """
        {
          "version": 1,
          "exercises": [
            {
              "name": "Планка",
              "category": "CORE",
              "measuredBy": "TIME",
              "isCustom": false,
              "comment": "Держать ровно",
              "createdAt": 1757371150000
            }
          ],
          "entries": [
            {
              "exerciseName": "Планка",
              "date": 1757371250000,
              "sets": 3,
              "reps": null,
              "weightKg": null,
              "durationSec": 90,
              "comment": null,
              "createdAt": 1757371250000
            }
          ]
        }
        """.trimIndent()

    /** `plankFile` с назначенной иконкой: `"iconNumber": 5` (exercise-icon-mvp). */
    private val plankWithIconFile =
        """
        {
          "version": 1,
          "exercises": [
            {
              "name": "Планка",
              "category": "CORE",
              "measuredBy": "TIME",
              "isCustom": false,
              "comment": "Держать ровно",
              "iconNumber": 5,
              "createdAt": 1757371150000
            }
          ],
          "entries": []
        }
        """.trimIndent()

    /**
     * Снимок стандартного набора категорий: БД всегда предзаполняется
     * пятью стандартными (custom-categories, spec «Предзаполнение
     * стандартных категорий»), поэтому дефолтный снимок репозитория
     * в тестах — они же; упражнения файлов с "PULL"/"CORE" резолвятся.
     */
    private fun standardDomainCategories(): List<Category> =
        standardCategories.map { standard ->
            Category(
                id = 0L,
                name = standard.name,
                colorArgb = standard.colorArgb,
                isCustom = false
            )
        }

    private fun repository(
        exercises: List<Exercise> = emptyList(),
        entries: List<ExerciseEntry> = emptyList(),
        categories: List<Category> = standardDomainCategories()
    ): WorkoutDataRepository =
        mockk<WorkoutDataRepository>(relaxed = true).also { repo ->
            coEvery { repo.getAllExercises() } returns Result.success(exercises)
            coEvery { repo.getAllEntries() } returns Result.success(entries)
            coEvery { repo.getAllCategories() } returns Result.success(categories)
            coEvery { repo.importData(any(), any(), any()) } returns Result.success(Unit)
        }

    private fun useCase(repository: WorkoutDataRepository): ImportBackupUseCase =
        ImportBackupUseCase(repository, NoopCrashReporter())

    /** Открывает файл-строку потоком — opener-фабрика для `invoke`. */
    private fun streamOf(fileContent: String): InputStream = ByteArrayInputStream(fileContent.toByteArray())

    @Test
    fun import_when_new_exercise_then_inserted_with_isCustom_true_and_zero_id() =
        runTest {
            // Given: пустой каталог, файл с одним упражнением (isCustom = false
            // в файле — недоверенное значение) и записью на него
            val repository = repository()
            val useCase = useCase(repository)
            val stream = streamOf(plankFile)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Новое упражнение и запись добавлены")
            val exercisesSlot = slot<List<Exercise>>()
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), capture(entriesSlot)) }
            val inserted = exercisesSlot.captured.single()
            assertTrue(inserted.isCustom, "Вставленному из файла всегда isCustom = true")
            assertEquals(0L, inserted.id, "id присвоит БД — в файле его нет")
            assertEquals("Планка", inserted.name, "Имя из файла")
            assertEquals("Держать ровно", inserted.comment, "Комментарий из файла")
            assertEquals(1_757_371_150_000L, inserted.createdAt, "createdAt из файла")
            assertEquals("Планка", entriesSlot.captured.single().exerciseName, "Запись передана для вставки")
        }

    @Test
    fun import_when_name_matches_existing_ignoring_case_then_not_duplicated() =
        runTest {
            // Given: в каталоге «Подтягивания», файл содержит «подтягивания »
            // (тот же ключ trim().lowercase()) и запись «ПОДТЯГИВАНИЯ»
            val repository = repository(exercises = listOf(pullUps))
            val file =
                """
                {
                  "version": 1,
                  "exercises": [
                    {"name": "  подтягивания ", "category": "PULL", "measuredBy": "REPS",
                     "isCustom": true, "comment": null, "createdAt": 1757371150000}
                  ],
                  "entries": [
                    {"exerciseName": "ПОДТЯГИВАНИЯ", "date": 1757371200000, "sets": 4,
                     "reps": 10, "weightKg": null, "durationSec": null, "comment": null,
                     "createdAt": 1757371200000}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Упражнение не дублируется, запись добавляется")
            val exercisesSlot = slot<List<Exercise>>()
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), capture(entriesSlot)) }
            assertTrue(exercisesSlot.captured.isEmpty(), "Дубликат имени не создаёт новое упражнение")
            assertEquals(
                "ПОДТЯГИВАНИЯ",
                entriesSlot.captured.single().exerciseName,
                "Запись связана с существующим по имени"
            )
        }

    @Test
    fun import_when_entry_content_duplicate_then_skipped() =
        runTest {
            // Given: запись с тем же содержимым уже есть (имя-ключ, дата,
            // подходы, повторы, вес, длительность, комментарий)
            val repository =
                repository(
                    exercises = listOf(pullUps),
                    entries = listOf(existingEntry)
                )
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [
                    {"exerciseName": "Подтягивания", "date": 1757371200000, "sets": 4,
                     "reps": 10, "weightKg": null, "durationSec": null, "comment": null,
                     "createdAt": 1757379999000}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(
                result.isSuccess,
                "Дубликат содержимого не вставляется (createdAt не входит в ключ)"
            )
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(any(), any(), capture(entriesSlot)) }
            assertTrue(entriesSlot.captured.isEmpty(), "Дубликат записи не доходит до репозитория")
        }

    @Test
    fun import_when_entry_without_exercise_then_skipped_without_failure() =
        runTest {
            // Given: запись ссылается на имя, которого нет ни в файле, ни в
            // каталоге; вторая запись валидна
            val repository = repository(exercises = listOf(pullUps))
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [
                    {"exerciseName": "Несуществующее", "date": 1757371200000, "sets": 4,
                     "reps": 10, "weightKg": null, "durationSec": null, "comment": null,
                     "createdAt": 1757371200000},
                    {"exerciseName": "Подтягивания", "date": 1757371300000, "sets": 3,
                     "reps": 8, "weightKg": null, "durationSec": null, "comment": null,
                     "createdAt": 1757371300000}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Импорт не падает, вставлена одна запись")
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(any(), any(), capture(entriesSlot)) }
            assertFalse(
                entriesSlot.captured.any { it.exerciseName == "Несуществующее" },
                "Неразрешимое имя не доходит до importData"
            )
        }

    @Test
    fun import_when_entry_fails_validator_then_skipped() =
        runTest {
            // Given: запись с sets = 0 не проходит EntryValidator, вторая валидна
            val repository = repository(exercises = listOf(pullUps))
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [
                    {"exerciseName": "Подтягивания", "date": 1757371200000, "sets": 0,
                     "reps": 10, "weightKg": null, "durationSec": null, "comment": null,
                     "createdAt": 1757371200000},
                    {"exerciseName": "Подтягивания", "date": 1757371400000, "sets": 5,
                     "reps": 12, "weightKg": null, "durationSec": null, "comment": null,
                     "createdAt": 1757371400000}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(
                result.isSuccess,
                "Невалидная запись пропущена, валидная вставлена"
            )
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(any(), any(), capture(entriesSlot)) }
            assertEquals(5, entriesSlot.captured.single().sets, "Вставлена только валидная запись")
        }

    @Test
    fun import_when_same_file_twice_then_idempotent() =
        runTest {
            // Given: первый импорт прошёл, репозиторий теперь возвращает
            // состояние после вставки (упражнение с id БД, запись с exerciseId)
            val repository = repository()
            val insertedPlank =
                Exercise(
                    id = 100L,
                    name = "Планка",
                    category = "CORE",
                    isCustom = true,
                    comment = "Держать ровно",
                    createdAt = 1_757_371_150_000L
                )
            val insertedEntry =
                ExerciseEntry(
                    id = 200L,
                    exerciseId = 100L,
                    workoutId = null,
                    date = 1_757_371_250_000L,
                    sets = 3,
                    reps = null,
                    weightKg = null,
                    durationSec = 90,
                    comment = null,
                    createdAt = 1_757_371_250_000L,
                    modifiedAt = 1_757_371_250_000L
                )
            val useCase = useCase(repository)
            val stream = streamOf(plankFile)

            // When
            val first = useCase { stream }
            coEvery { repository.getAllExercises() } returns Result.success(listOf(insertedPlank))
            coEvery { repository.getAllEntries() } returns Result.success(listOf(insertedEntry))
            val second = useCase { streamOf(plankFile) }

            // Then — счётчиков у use-case нет, поэтому контракт «повторный
            // импорт ничего не вставляет» проверяем по аргументам вызовов
            // репозитория: первый импорт передаёт данные файла, второй —
            // пустые списки
            assertTrue(first.isSuccess, "Первый импорт добавляет данные файла")
            assertTrue(second.isSuccess, "Повторный импорт проходит без ошибок")
            val exercisesSlots = mutableListOf<List<Exercise>>()
            val entriesSlots = mutableListOf<List<BackupEntry>>()
            coVerify(exactly = 2) {
                repository.importData(any(), capture(exercisesSlots), capture(entriesSlots))
            }
            assertEquals(1, exercisesSlots[0].size, "Первый импорт вставляет упражнение файла")
            assertEquals(1, entriesSlots[0].size, "Первый импорт вставляет запись файла")
            assertTrue(exercisesSlots[1].isEmpty(), "Повторный импорт не дублирует упражнение")
            assertTrue(entriesSlots[1].isEmpty(), "Повторный импорт того же файла ничего не добавляет")
        }

    @Test
    fun import_when_exercise_category_unknown_then_exercise_and_entries_skipped() =
        runTest {
            // Given: упражнение ссылается на категорию, которой нет ни в
            // categories[] файла, ни в БД — упражнение пропускается вместе
            // со своими записями, файл не отвергается (spec-сценарии
            // «Упражнение с неизвестной категорией пропускается» и
            // «Категория упражнения с произвольной строкой»)
            val repository = repository()
            val file =
                """
                {
                  "version": 1,
                  "exercises": [
                    {"name": "X", "category": "STRENGTH", "measuredBy": "REPS",
                     "isCustom": true, "createdAt": 1}
                  ],
                  "entries": [
                    {"exerciseName": "X", "date": 1757371200000, "sets": 4,
                     "reps": 10, "weightKg": null, "durationSec": null,
                     "comment": null, "createdAt": 1757371200000}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Неизвестное имя категории не отвергает файл")
            val exercisesSlot = slot<List<Exercise>>()
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify {
                repository.importData(any(), capture(exercisesSlot), capture(entriesSlot))
            }
            assertTrue(
                exercisesSlot.captured.isEmpty(),
                "Упражнение с неразрешимой категорией пропускается"
            )
            assertTrue(
                entriesSlot.captured.isEmpty(),
                "Записи пропущенного упражнения тоже пропускаются"
            )
        }

    // ---------- custom-categories: секция categories[] (design D9) ----------

    @Test
    fun import_when_new_category_then_inserted_with_fileColorArchive_and_isCustom_true() =
        runTest {
            // Given: файл содержит две отсутствующие в БД категории —
            // активную и архивную; isCustom в файле (false) — недоверенное
            // значение (spec-сценарий «Категория из файла создаётся при
            // отсутствии»)
            val repository = repository()
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [],
                  "categories": [
                    {"name": "Спина", "colorArgb": -11637006, "isArchived": false,
                     "isCustom": false},
                    {"name": "Руки", "colorArgb": -6501275, "isArchived": true,
                     "isCustom": true}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then — вставленные: цвет и архив из файла, isCustom всегда true
            assertTrue(result.isSuccess, "Категории файла вставляются")
            val categoriesSlot = slot<List<Category>>()
            coVerify { repository.importData(capture(categoriesSlot), any(), any()) }
            val spine = categoriesSlot.captured.single { it.name == "Спина" }
            assertEquals(0xFF4E6EF2.toInt(), spine.colorArgb, "Цвет категории — из файла")
            assertFalse(spine.isArchived, "Активная категория из файла")
            assertTrue(spine.isCustom, "Вставленной категории всегда isCustom = true")
            assertEquals(0L, spine.id, "id присвоит БД — в файле его нет")
            val arms = categoriesSlot.captured.single { it.name == "Руки" }
            assertTrue(arms.isArchived, "Архивная категория вставляется архивной")
        }

    @Test
    fun import_when_category_duplicate_by_key_then_not_duplicated_and_existing_untouched() =
        runTest {
            // Given: в БД категория «Спина», файл содержит « спина » (тот же
            // ключ name.trim().lowercase()) с другим цветом — дедуп, цвет
            // существующей не меняется (spec-сценарии «Дубликаты
            // пропускаются» и «Цвет существующей категории не меняется»)
            val existing = Category(id = 7L, name = "Спина", colorArgb = 1, isCustom = true)
            val repository = repository(categories = listOf(existing))
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [],
                  "categories": [
                    {"name": " спина ", "colorArgb": 999, "isArchived": false}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Дедуп не ломает импорт")
            val categoriesSlot = slot<List<Category>>()
            coVerify { repository.importData(capture(categoriesSlot), any(), any()) }
            assertTrue(
                categoriesSlot.captured.isEmpty(),
                "Дубликат по name.trim().lowercase() не создаёт категорию"
            )
            coVerify(exactly = 0) { repository.setCategoryArchived(any(), any()) }
        }

    @Test
    fun import_when_duplicate_categories_within_file_then_single_insert() =
        runTest {
            // Given: в файле «Спина» и «СПИНА» — один и тот же ключ дедупа
            val repository = repository()
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [],
                  "categories": [
                    {"name": "Спина", "colorArgb": 1, "isArchived": false},
                    {"name": "СПИНА", "colorArgb": 2, "isArchived": false}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Импорт успешен")
            val categoriesSlot = slot<List<Category>>()
            coVerify { repository.importData(capture(categoriesSlot), any(), any()) }
            assertEquals(
                listOf("Спина"),
                categoriesSlot.captured.map { it.name },
                "Дубликаты внутри файла схлопываются в одну вставку"
            )
        }

    @Test
    fun import_when_file_category_archived_then_existing_archived() =
        runTest {
            // Given: в БД активная «Спина», файл дублирует имя с
            // isArchived = true — односторонняя архивация (spec-сценарий
            // «Архивация существующей категории из файла применяется»)
            val existing = Category(id = 7L, name = "Спина", colorArgb = 1, isCustom = true)
            val repository = repository(categories = listOf(existing))
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [],
                  "categories": [
                    {"name": "Спина", "colorArgb": 999, "isArchived": true}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Дедуп не ломает импорт")
            coVerify(exactly = 1) {
                repository.setCategoryArchived(existing.id, true)
            }
        }

    @Test
    fun import_when_file_category_active_and_existing_archived_then_no_unarchive() =
        runTest {
            // Given: в БД архивная «Спина», файл содержит её с
            // isArchived = false — обратная разархивация не выполняется
            // (spec-сценарий «Разархивация существующей категории из файла
            // не выполняется»)
            val existing =
                Category(id = 7L, name = "Спина", colorArgb = 1, isCustom = true, isArchived = true)
            val repository = repository(categories = listOf(existing))
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [],
                  "categories": [
                    {"name": "Спина", "colorArgb": 999, "isArchived": false}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Дедуп не ломает импорт")
            coVerify(exactly = 0) { repository.setCategoryArchived(any(), any()) }
        }

    @Test
    fun import_when_exercise_category_resolved_from_file_then_inserted() =
        runTest {
            // Given: категории «Спина» нет в БД, но она есть в categories[]
            // файла — упражнение вставляется и ссылается на категорию файла
            val repository = repository()
            val file =
                """
                {
                  "version": 1,
                  "exercises": [
                    {"name": "Гиперэкстензия", "category": "Спина",
                     "measuredBy": "REPS", "isCustom": true, "createdAt": 1}
                  ],
                  "entries": [],
                  "categories": [
                    {"name": "Спина", "colorArgb": -11637006, "isArchived": false}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Категория файла резолвит упражнение")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            assertEquals(
                "Спина",
                exercisesSlot.captured.single().category,
                "Упражнение ссылается на категорию из файла"
            )
        }

    @Test
    fun import_when_exercise_category_key_matches_db_then_canonicalized() =
        runTest {
            // Given: в БД «Спина», упражнение файла ссылается на « СПИНА » —
            // тот же ключ; вставленное упражнение получает каноническое имя
            val existing = Category(id = 7L, name = "Спина", colorArgb = 1, isCustom = true)
            val repository = repository(categories = listOf(existing))
            val file =
                """
                {
                  "version": 1,
                  "exercises": [
                    {"name": "Гиперэкстензия", "category": " СПИНА ",
                     "measuredBy": "REPS", "isCustom": true, "createdAt": 1}
                  ],
                  "entries": []
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Резолв по ключу без регистра и краёв")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            assertEquals(
                "Спина",
                exercisesSlot.captured.single().category,
                "Категория упражнения нормализуется к имени в БД"
            )
        }

    @Test
    fun import_when_old_file_without_categories_then_imported_from_db_categories() =
        runTest {
            // Given: старый файл без массива categories (бэкап до введения
            // секции) — упражнение со стандартной категорией резолвится
            // по текущей БД (spec-сценарий «Старый файл без категорий
            // импортируется»)
            val repository = repository()
            val useCase = useCase(repository)
            val stream = streamOf(plankFile)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Файл без categories[] остаётся валидным")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            assertEquals(
                "CORE",
                exercisesSlot.captured.single().category,
                "Категория резолвится по текущей БД"
            )
        }

    @Test
    fun import_when_same_file_twice_then_categories_idempotent() =
        runTest {
            // Given: первый импорт вставил категорию «Спина»; повторный —
            // тот же файл поверх состояния после вставки (spec-сценарий
            // «Идемпотентность повторного импорта»)
            val repository = repository()
            val file =
                """
                {
                  "version": 1,
                  "exercises": [],
                  "entries": [],
                  "categories": [
                    {"name": "Спина", "colorArgb": -11637006, "isArchived": false}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val first = useCase { stream }
            coEvery { repository.getAllCategories() } returns
                Result.success(
                    listOf(
                        Category(
                            id = 100L,
                            name = "Спина",
                            colorArgb = 0xFF4E6EF2.toInt(),
                            isCustom = true
                        )
                    )
                )
            val second = useCase { streamOf(file) }

            // Then
            assertTrue(first.isSuccess, "Первый импорт вставляет категорию")
            assertTrue(second.isSuccess, "Повторный импорт проходит без ошибок")
            val categoriesSlots = mutableListOf<List<Category>>()
            coVerify(exactly = 2) {
                repository.importData(capture(categoriesSlots), any(), any())
            }
            assertEquals(1, categoriesSlots[0].size, "Первый импорт вставляет категорию файла")
            assertTrue(
                categoriesSlots[1].isEmpty(),
                "Повторный импорт не дублирует категорию"
            )
        }

    // ---------- Регрессия версии формата (design D9, ранее не покрыто) ----------

    @Test
    fun import_when_version2_with_categories_then_success() =
        runTest {
            // Given: файл с меткой "version": 2 и секцией categories[] —
            // код сторового релиза пишет метку 2, приём сохраняется
            // (design D9)
            val repository = repository()
            val file =
                """
                {
                  "version": 2,
                  "exercises": [],
                  "entries": [],
                  "categories": [
                    {"name": "Спина", "colorArgb": -11637006, "isArchived": false}
                  ],
                  "settings": {"overviewTabVisibility": {}, "lastOverviewTab": "STATS"}
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Версия 2 остаётся читаемой")
            val categoriesSlot = slot<List<Category>>()
            coVerify { repository.importData(capture(categoriesSlot), any(), any()) }
            assertEquals(
                listOf("Спина"),
                categoriesSlot.captured.map { it.name },
                "Категории из файла версии 2 вставляются"
            )
        }

    @Test
    fun import_when_version3_then_rejected() =
        runTest {
            // Given: файл с меткой "version": 3 — формат из будущего,
            // импорт отвергается (design D9)
            val repository = repository()
            val file =
                """
                {
                  "version": 3,
                  "exercises": [],
                  "entries": [],
                  "categories": [
                    {"name": "Спина", "colorArgb": -11637006, "isArchived": false}
                  ]
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isFailure, "Неподдерживаемая версия — Result.failure")
            coVerify(exactly = 0) { repository.importData(any(), any(), any()) }
        }

    @Test
    fun import_when_iconNumberAbsent_then_parsedAsNull() =
        runTest {
            // Given: старый файл v1 без iconNumber в упражнениях
            // (spec-сценарий «Старый файл без номера иконки импортируется»)
            val repository = repository()
            val file =
                """
                {
                  "version": 1,
                  "exercises": [
                    {"name": "Планка", "category": "CORE", "measuredBy": "TIME",
                     "isCustom": true, "comment": null, "createdAt": 1757371150000}
                  ],
                  "entries": []
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then — импорт успешен, иконка null
            assertTrue(result.isSuccess, "Старый файл без iconNumber остаётся валидным")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            assertEquals(
                null,
                exercisesSlot.captured.single().iconNumber,
                "Отсутствующее поле даёт iconNumber = null"
            )
        }

    @Test
    fun import_when_newExercise_then_iconNumberKeptFromBackup() =
        runTest {
            // Given: в каталоге нет «Планки», файл содержит её с iconNumber = 5
            // (spec-сценарий «Номер иконки вставленного упражнения сохраняется»)
            val repository = repository()
            val useCase = useCase(repository)
            val stream = streamOf(plankWithIconFile)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Вставка проходит")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            assertEquals(
                5,
                exercisesSlot.captured.single().iconNumber,
                "Вставленное упражнение сохраняет iconNumber из файла"
            )
        }

    @Test
    fun import_when_duplicateExercise_then_existingIconNumberUnchanged() =
        runTest {
            // Given: в каталоге «Планка» с iconNumber = 2, файл содержит
            // «Планка» с iconNumber = 5 — дедупликация по имени
            // (spec-сценарий «Номер иконки существующего упражнения при
            // дедупликации не меняется»)
            val existingPlank =
                Exercise(
                    id = 1L,
                    name = "Планка",
                    category = "CORE",
                    isCustom = false,
                    comment = null,
                    createdAt = 1_757_371_150_000L,
                    measuredBy = ExerciseMeasuredBy.TIME,
                    iconNumber = 2
                )
            val repository = repository(exercises = listOf(existingPlank))
            val useCase = useCase(repository)
            val stream = streamOf(plankWithIconFile)

            // When
            val result = useCase { stream }

            // Then — дубликат не вставляется, существующее не трогается
            assertTrue(result.isSuccess, "Дедупликация не ломает импорт")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            assertTrue(
                exercisesSlot.captured.isEmpty(),
                "Дубликат имени не создаёт новое упражнение"
            )
            assertEquals(
                2,
                repository
                    .getAllExercises()
                    .getOrThrow()
                    .single()
                    .iconNumber,
                "Существующее упражнение остаётся со своим прежним iconNumber"
            )
        }

    @Test
    fun import_when_newExercise_then_isArchivedKeptFromBackup() =
        runTest {
            // Given: в каталоге нет «Планки», файл содержит её с isArchived = true
            // (spec-сценарий «Признак архивации вставленного упражнения
            // сохраняется» + design Decision 4: «файл недоверенный → isCustom = true»)
            val file =
                """
                {
                  "version": 1,
                  "exercises": [
                    {"name": "Планка", "category": "CORE", "measuredBy": "TIME",
                     "isCustom": false, "comment": null, "createdAt": 1757371150000,
                     "isArchived": true}
                  ],
                  "entries": []
                }
                """.trimIndent()
            val repository = repository()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then — вставленное упражнение попадает в архив и помечается isCustom
            assertTrue(result.isSuccess, "Импорт архивного упражнения проходит")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            val inserted = exercisesSlot.captured.single()
            assertTrue(
                inserted.isArchived,
                "Вставленное упражнение сохраняет isArchived = true из файла"
            )
            assertTrue(
                inserted.isCustom,
                "Решение Decision 4: вставленному всегда isCustom = true " +
                    "поверх пользовательского isArchived"
            )
        }

    @Test
    fun import_when_isArchivedAbsent_then_defaultFalse() =
        runTest {
            // Given: старый файл v1 без isArchived в упражнениях
            // (spec-сценарий «Старый файл без признака архивации импортируется»)
            val repository = repository()
            val file =
                """
                {
                  "version": 1,
                  "exercises": [
                    {"name": "Планка", "category": "CORE", "measuredBy": "TIME",
                     "isCustom": true, "comment": null, "createdAt": 1757371150000}
                  ],
                  "entries": []
                }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then — импорт успешен, isArchived по умолчанию = false
            assertTrue(result.isSuccess, "Старый файл без isArchived остаётся валидным")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            assertFalse(
                exercisesSlot.captured.single().isArchived,
                "Отсутствующее поле даёт isArchived = false (обратная совместимость)"
            )
        }

    @Test
    fun mergeBackup_when_fileCategoryArchived_duplicateOfActiveExisting_then_idInArchiveList() {
        // Given: в БД активная «Спина», файл дублирует имя с
        // isArchived = true — односторонняя архивация (spec-сценарий
        // «Архивация существующей категории из файла применяется»)
        val existing = Category(id = 7L, name = "Спина", colorArgb = 1, isCustom = true)
        val fileCategory = existing.copy(id = 0L, isArchived = true)

        // When
        val result =
            mergeBackup(
                existingCategories = listOf(existing),
                existingExercises = emptyList(),
                existingEntries = emptyList(),
                file = BackupFileContent(listOf(fileCategory), emptyList(), emptyList(), null)
            )

        // Then
        assertTrue(result.categoriesToInsert.isEmpty(), "Дубликат имени не попадает в список вставки")
        assertEquals(
            listOf(existing.id),
            result.categoryIdsToArchive,
            "id существующей категории попадает в список архивации"
        )
    }

    @Test
    fun mergeBackup_when_fileCategoryActive_duplicateOfArchivedExisting_then_idNotInArchiveList() {
        // Given: в БД архивная «Спина», файл содержит её с isArchived = false —
        // разархивация не выполняется (spec-сценарий «Разархивация
        // существующей категории из файла не выполняется»)
        val existing =
            Category(id = 7L, name = "Спина", colorArgb = 1, isCustom = true, isArchived = true)
        val fileCategory = existing.copy(id = 0L, isArchived = false)

        // When
        val result =
            mergeBackup(
                existingCategories = listOf(existing),
                existingExercises = emptyList(),
                existingEntries = emptyList(),
                file = BackupFileContent(listOf(fileCategory), emptyList(), emptyList(), null)
            )

        // Then
        assertTrue(result.categoriesToInsert.isEmpty(), "Дубликат имени не попадает в список вставки")
        assertTrue(
            result.categoryIdsToArchive.isEmpty(),
            "Разархивации нет: id архивной существующей не попадает в список архивации"
        )
    }

    @Test
    fun mergeBackup_when_fileArchived_duplicateOfActiveExisting_then_idInArchiveList() {
        // Given: в каталоге активная «Планка», файл дублирует имя с
        // isArchived = true — односторонняя архивация (spec-сценарий
        // «Архивация существующего упражнения из файла применяется»)
        val existing =
            pullUps.copy(
                name = "Планка",
                category = "CORE",
                createdAt = 1_757_371_150_000L,
                measuredBy = ExerciseMeasuredBy.TIME
            )
        val fileExercise = existing.copy(id = 0L, isArchived = true)

        // When
        val result =
            mergeBackup(
                existingCategories = standardDomainCategories(),
                existingExercises = listOf(existing),
                existingEntries = emptyList(),
                file = BackupFileContent(emptyList(), listOf(fileExercise), emptyList(), null)
            )

        // Then
        assertTrue(result.exercisesToInsert.isEmpty(), "Дубликат имени не попадает в список вставки")
        assertEquals(
            listOf(existing.id),
            result.exerciseIdsToArchive,
            "id существующего упражнения попадает в список архивации"
        )
    }

    @Test
    fun mergeBackup_when_fileActive_duplicateOfArchivedExisting_then_idNotInArchiveList() {
        // Given: в каталоге архивная «Планка», файл содержит её с
        // isArchived = false — разархивация не выполняется (spec-сценарий
        // «Признак архивации существующего упражнения при дедупликации
        // не меняется»)
        val existing =
            pullUps.copy(
                name = "Планка",
                category = "CORE",
                createdAt = 1_757_371_150_000L,
                measuredBy = ExerciseMeasuredBy.TIME,
                isArchived = true
            )
        val fileExercise = existing.copy(id = 0L, isArchived = false)

        // When
        val result =
            mergeBackup(
                existingCategories = standardDomainCategories(),
                existingExercises = listOf(existing),
                existingEntries = emptyList(),
                file = BackupFileContent(emptyList(), listOf(fileExercise), emptyList(), null)
            )

        // Then
        assertTrue(result.exercisesToInsert.isEmpty(), "Дубликат имени не попадает в список вставки")
        assertTrue(
            result.exerciseIdsToArchive.isEmpty(),
            "Разархивации нет: id архивного существующего не попадает в список архивации"
        )
    }

    @Test
    fun mergeBackup_when_nameWithoutMatch_then_toInsertList_notArchiveList() {
        // Given: в каталоге «Подтягивания», файл содержит «Планку» — имени
        // нет в каталоге, даже с isArchived = true упражнение идёт во вставку
        val fileExercise =
            pullUps.copy(
                id = 0L,
                name = "Планка",
                category = "CORE",
                measuredBy = ExerciseMeasuredBy.TIME,
                isArchived = true
            )

        // When
        val result =
            mergeBackup(
                existingCategories = standardDomainCategories(),
                existingExercises = listOf(pullUps),
                existingEntries = emptyList(),
                file = BackupFileContent(emptyList(), listOf(fileExercise), emptyList(), null)
            )

        // Then
        assertEquals(
            listOf("Планка"),
            result.exercisesToInsert.map { it.name },
            "Новое имя попадает в список вставки"
        )
        assertTrue(
            result.exerciseIdsToArchive.isEmpty(),
            "Без совпадения имени в списке архивации пусто"
        )
    }

    @Test
    fun import_when_duplicateExerciseArchivedInFile_then_existingArchived() =
        runTest {
            // Given: в каталоге «Планка» активна (isArchived = false), файл
            // содержит «Планка» с isArchived = true — дедупликация по имени
            // (spec-сценарий «Архивация существующего упражнения из файла
            // применяется»): восстановление после «Удалить все данные»
            // возвращает архивацию
            val existingActivePlank =
                pullUps.copy(
                    name = "Планка",
                    category = "CORE",
                    createdAt = 1_757_371_150_000L,
                    measuredBy = ExerciseMeasuredBy.TIME
                )
            val file =
                """
                {
                  "version": 1,
                  "exercises": [
                    {"name": "Планка", "category": "CORE", "measuredBy": "TIME",
                     "isCustom": true, "comment": null, "createdAt": 1757371150000,
                     "isArchived": true}
                  ],
                  "entries": []
                }
                """.trimIndent()
            val repository = repository(exercises = listOf(existingActivePlank))
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then — дубликат не вставляется, существующее уходит в архив
            assertTrue(result.isSuccess, "Дедупликация не ломает импорт")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(any(), capture(exercisesSlot), any()) }
            assertTrue(
                exercisesSlot.captured.isEmpty(),
                "Дубликат имени не создаёт новое упражнение"
            )
            coVerify {
                repository.setArchived(existingActivePlank.id, true)
            }
        }

    @Test
    fun import_when_cancelled_then_rethrows_cancellation() =
        runTest {
            // Given — чтение файла прерывается отменой корутины
            // (спека «Отмена корутины не является ошибкой»)
            val repository = repository()
            // Анонимный поток вместо mockk: бросок CancellationException из
            // read() на реальном IO-диспетчере + MockK-прокси роняет
            // тест-воркер OOM (coroutine-debug probes)
            val stream =
                object : InputStream() {
                    override fun read(): Int = throw CancellationException("Операция отменена")
                }
            val useCase = ImportBackupUseCase(repository, NoopCrashReporter())

            // When / Then — CancellationException прокидывается дальше,
            // не превращаясь в Result.failure
            assertThrows<CancellationException> { useCase { stream } }
        }

    @Test
    fun invoke_when_stream_open_returns_null_then_failure_invalid_file() =
        runTest {
            // Given — opener (SAF) не отдал поток чтения
            val repository = repository()
            val useCase = useCase(repository)

            // When
            val result = useCase { null }

            // Then
            val exception = result.exceptionOrNull()
            assertTrue(result.isFailure, "Нет потока — Result.failure")
            assertTrue(
                exception is BackupException && exception.error == BackupError.INVALID_FILE,
                "Недоступность потока — BackupException(INVALID_FILE)"
            )
            coVerify(exactly = 0) { repository.importData(any(), any(), any()) }
        }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidFiles")
    fun import_when_file_invalid_then_failure_without_data_change(fileContent: String) =
        runTest {
            // Given: файл с ошибкой формата (design Decision 5)
            val repository = repository()
            val useCase = useCase(repository)
            val stream = streamOf(fileContent)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isFailure, "Ошибочный файл — Result.failure")
            val exception = assertInstanceOf(BackupException::class.java, result.exceptionOrNull())
            assertEquals(BackupError.INVALID_FILE, exception.error, "Тип ошибки — неверный файл")
            coVerify(exactly = 0) { repository.importData(any(), any(), any()) }
        }

    @Test
    fun import_when_unknown_fields_then_ignored() =
        runTest {
            // Given: объекты файла содержат незнакомые поля и не содержат
            // опциональных null-полей — файл собственного формата версии 1
            val repository = repository(exercises = listOf(pullUps))
            val file =
                """
                  {
                    "version": 1,
                    "futureTopLevel": {"a": 1},
                    "exercises": [
                      {"name": "Планка", "category": "CORE", "measuredBy": "TIME",
                       "isCustom": true, "createdAt": 1757371150000, "futureField": "x"}
                    ],
                "entries": [
                  {"exerciseName": "Подтягивания", "date": 1757371200000, "sets": 4,
                   "reps": 10, "comment": "Отлично", "createdAt": 1757371200000,
                   "extra": 7}
                ]
                  }
                """.trimIndent()
            val useCase = useCase(repository)
            val stream = streamOf(file)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Неизвестные поля не мешают импорту")
        }

    @Test
    fun import_when_settings_present_then_result_contains_snapshot() =
        runTest {
            // Given — v2 с блоком settings (снапшот возвращается вызывающему,
            // применение к DataStore — его работа)
            val repository = repository()
            val raw =
                settingsFile(
                    version = 2,
                    settingsJson =
                        """{"overviewTabVisibility": {"ACTIVITY": false}, """ +
                            """"lastOverviewTab": "RECORDS"}"""
                )
            val useCase = useCase(repository)
            val stream = streamOf(raw)

            // When
            val result = useCase { stream }

            // Then
            val settings = result.getOrThrow()
            assertEquals(
                mapOf(OverviewTab.ACTIVITY to false),
                checkNotNull(settings) { "Блок settings распознан" }.tabVisibility,
                "Видимость вкладок — в снапшоте результата"
            )
            assertEquals(OverviewTab.RECORDS, settings?.lastOverviewTab, "Последняя вкладка — в снапшоте")
        }

    @Test
    fun import_when_v1_then_result_settings_null() =
        runTest {
            // Given — файл версии 1 без блока settings: DataStore не трогать
            val repository = repository()
            val useCase = useCase(repository)
            val stream = streamOf(plankFile)

            // When
            val result = useCase { stream }

            // Then
            assertTrue(result.isSuccess, "Бэкап v1 валиден")
            assertEquals(null, result.getOrThrow(), "Настроек в снапшоте нет")
        }

    companion object {
        /**
         * Источник данных для параметризованного теста ошибок файла.
         *
         * `LongMethod` подавлен осознанно: это таблица данных design
         * Decision 5, а не логика — по одному JSON-кейсу на строку.
         * Кейсы «version/sets строкой» удалены: декодер kotlinx-serialization
         * принимает числовые значения в строковых литералах (расширение
         * толерантности, KDoc parseBackupJson), файл больше не невалиден.
         */
        @Suppress("LongMethod")
        @JvmStatic
        fun invalidFiles() =
            listOf(
                Arguments.of(Named.of("не-JSON", "это совсем не json")),
                Arguments.of(Named.of("нет version", """{"exercises": [], "entries": []}""")),
                Arguments.of(Named.of("нет exercises", """{"version": 1, "entries": []}""")),
                Arguments.of(Named.of("нет entries", """{"version": 1, "exercises": []}""")),
                Arguments.of(
                    Named.of(
                        "version не 1 и не 2",
                        """{"version": 3, "exercises": [], "entries": []}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "exercises не массив",
                        """{"version": 1, "exercises": {}, "entries": []}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "упражнение без name",
                        """{"version": 1, "exercises": [{"category": "PULL"}], "entries": []}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "пустое name",
                        """{"version": 1, "exercises": [{"name": "", "category": "PULL",
                       "measuredBy": "REPS", "isCustom": true, "createdAt": 1}], "entries": []}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "неизвестный measuredBy",
                        """{"version": 1, "exercises": [{"name": "X", "category": "PULL",
                       "measuredBy": "COUNT", "isCustom": true, "createdAt": 1}], "entries": []}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "запись без exerciseName",
                        """{"version": 1, "exercises": [],
                       "entries": [{"date": 1, "sets": 1, "createdAt": 1}]}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "категория без name",
                        """{"version": 1, "exercises": [], "entries": [],
                       "categories": [{"colorArgb": 1, "isArchived": false}]}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "colorArgb категории строкой",
                        """{"version": 1, "exercises": [], "entries": [],
                       "categories": [{"name": "X", "colorArgb": "red", "isArchived": false}]}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "isArchived категории строкой",
                        """{"version": 1, "exercises": [], "entries": [],
                       "categories": [{"name": "X", "colorArgb": 1, "isArchived": "yes"}]}"""
                    )
                )
            )
    }

    // ---------- overview-screen: настройки вкладок в бэкапе (design D8) ----------

    /** Файл бэкапа с пустыми данными и переданным блоком settings. */
    private fun settingsFile(
        version: Int,
        settingsJson: String
    ): String =
        """
        {
          "version": $version,
          "exercises": [],
          "entries": [],
          "settings": $settingsJson
        }
        """.trimIndent()

    @Test
    fun parseBackupJson_settings_settings_present_then_parsed() {
        // Given — v2 со скрытой RECORDS и последней вкладкой ACTIVITY
        val raw =
            settingsFile(
                version = 2,
                settingsJson =
                    """{"overviewTabVisibility": {"RECORDS": false}, """ +
                        """"lastOverviewTab": "ACTIVITY"}"""
            )

        // When
        val settings =
            checkNotNull(parseBackupJson(raw).settings) { "Блок settings распознан" }

        // Then
        assertEquals(mapOf(OverviewTab.RECORDS to false), settings.tabVisibility, "Скрытая вкладка")
        assertEquals(OverviewTab.ACTIVITY, settings.lastOverviewTab, "Последняя вкладка")
    }

    @Test
    fun parseBackupJson_settings_block_absent_then_null() {
        // Given — v1 без блока settings

        // When
        val settings = parseBackupJson(plankFile).settings

        // Then — обратная совместимость: settings нет
        assertEquals(null, settings, "Файл версии 1 не содержит настроек")
    }

    @Test
    fun parseBackupJson_settings_garbage_last_tab_then_fallback_stats() {
        // Given — мусорное имя вкладки
        val raw =
            settingsFile(
                version = 2,
                settingsJson = """{"overviewTabVisibility": {}, "lastOverviewTab": "GARBAGE"}"""
            )

        // When
        val settings =
            checkNotNull(parseBackupJson(raw).settings) { "Блок settings распознан" }

        // Then — fallback на STATS, паттерн DataStore-Flow
        assertEquals(OverviewTab.STATS, settings.lastOverviewTab)
    }

    @Test
    fun parseBackupJson_settings_unknown_tab_then_ignored() {
        // Given — неизвестное имя вкладки в карте видимости
        val raw =
            settingsFile(
                version = 2,
                settingsJson =
                    """{"overviewTabVisibility": {"RECORDS": true, "FUTURE_TAB": false}, """ +
                        """"lastOverviewTab": "STATS"}"""
            )

        // When
        val settings =
            checkNotNull(parseBackupJson(raw).settings) { "Блок settings распознан" }

        // Then — неизвестные имена игнорируются, известные читаются
        assertEquals(mapOf(OverviewTab.RECORDS to true), settings.tabVisibility)
    }

    @Test
    fun parseBackupJson_settings_block_not_object_then_null() {
        // Given — settings не объект (строка): прежний парсер молча
        // игнорировал блок, файл восстанавливался без настроек
        val raw = settingsFile(version = 2, settingsJson = """"oops"""")
        val parsed = parseBackupJson(raw)

        // Then — файл не отвергается, настроек в снапшоте нет
        assertTrue(parsed.exercises.isEmpty(), "Файл валиден, данные читаются")
        assertEquals(null, parsed.settings, "Неверный тип блока settings игнорируется")
    }

    @Test
    fun parseBackupJson_settings_visibility_not_object_then_empty_map() {
        // Given — карта видимости не объект
        val raw =
            settingsFile(
                version = 2,
                settingsJson = """{"overviewTabVisibility": "oops", "lastOverviewTab": "STATS"}"""
            )

        // When
        val settings =
            checkNotNull(parseBackupJson(raw).settings) { "Блок settings распознан" }

        // Then — карта игнорируется, файл не отвергается
        assertEquals(emptyMap<OverviewTab, Boolean>(), settings.tabVisibility)
        assertEquals(OverviewTab.STATS, settings.lastOverviewTab)
    }

    @Test
    fun parseBackupJson_settings_visibility_value_not_primitive_then_tab_dropped() {
        // Given — значение карты видимости — объект вместо Boolean
        val raw =
            settingsFile(
                version = 2,
                settingsJson =
                    """{"overviewTabVisibility": {"RECORDS": {"junk": 1}, "ACTIVITY": false}, """ +
                        """"lastOverviewTab": "STATS"}"""
            )

        // When
        val settings =
            checkNotNull(parseBackupJson(raw).settings) { "Блок settings распознан" }

        // Then — некорректное значение отброшено, корректное читается
        assertEquals(mapOf(OverviewTab.ACTIVITY to false), settings.tabVisibility)
    }

    @Test
    fun parseBackupJson_settings_last_tab_not_primitive_then_fallback_stats() {
        // Given — lastOverviewTab — объект вместо строки
        val raw =
            settingsFile(
                version = 2,
                settingsJson = """{"overviewTabVisibility": {}, "lastOverviewTab": {"junk": 1}}"""
            )

        // When
        val settings =
            checkNotNull(parseBackupJson(raw).settings) { "Блок settings распознан" }

        // Then — fallback на STATS, файл не отвергается
        assertEquals(OverviewTab.STATS, settings.lastOverviewTab)
    }
}
