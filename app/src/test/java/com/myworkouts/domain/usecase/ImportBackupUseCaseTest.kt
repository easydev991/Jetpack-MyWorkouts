package com.myworkouts.domain.usecase

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.ByteArrayInputStream

/**
 * Unit-тесты восстановления из резервной копии (app-data-screen, design
 * Decisions 4–5): merge-алгоритм и ошибки файла проверяются на уровне
 * `invoke` через mockk — Uri и contentResolver замоканы, файл подаётся
 * потоком из строки (Decision 8).
 */
class ImportBackupUseCaseTest {
    private val uri = mockk<Uri>()
    private val pullUps =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
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

    private fun repository(
        exercises: List<Exercise> = emptyList(),
        entries: List<ExerciseEntry> = emptyList()
    ): WorkoutDataRepository =
        mockk<WorkoutDataRepository>(relaxed = true).also { repo ->
            coEvery { repo.getAllExercises() } returns Result.success(exercises)
            coEvery { repo.getAllEntries() } returns Result.success(entries)
            coEvery { repo.importData(any(), any()) } returns Result.success(Unit)
        }

    private fun useCase(
        repository: WorkoutDataRepository,
        fileContent: String
    ): ImportBackupUseCase {
        val contentResolver = mockk<ContentResolver>()
        every { contentResolver.openInputStream(any()) } answers {
            ByteArrayInputStream(fileContent.toByteArray())
        }
        val context = mockk<Context>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        return ImportBackupUseCase(repository, context)
    }

    @Test
    fun import_when_new_exercise_then_inserted_with_isCustom_true_and_zero_id() =
        runTest {
            // Given: пустой каталог, файл с одним упражнением (isCustom = false
            // в файле — недоверенное значение) и записью на него
            val repository = repository()
            val useCase = useCase(repository, plankFile)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(result.isSuccess, "Новое упражнение и запись добавлены")
            val exercisesSlot = slot<List<Exercise>>()
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(capture(exercisesSlot), capture(entriesSlot)) }
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
            val useCase = useCase(repository, file)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(result.isSuccess, "Упражнение не дублируется, запись добавляется")
            val exercisesSlot = slot<List<Exercise>>()
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(capture(exercisesSlot), capture(entriesSlot)) }
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
            val useCase = useCase(repository, file)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(
                result.isSuccess,
                "Дубликат содержимого не вставляется (createdAt не входит в ключ)"
            )
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(any(), capture(entriesSlot)) }
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
            val useCase = useCase(repository, file)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(result.isSuccess, "Импорт не падает, вставлена одна запись")
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(any(), capture(entriesSlot)) }
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
            val useCase = useCase(repository, file)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(
                result.isSuccess,
                "Невалидная запись пропущена, валидная вставлена"
            )
            val entriesSlot = slot<List<BackupEntry>>()
            coVerify { repository.importData(any(), capture(entriesSlot)) }
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
                    category = ExerciseCategory.CORE,
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
            val useCase = useCase(repository, plankFile)

            // When
            val first = useCase(uri)
            coEvery { repository.getAllExercises() } returns Result.success(listOf(insertedPlank))
            coEvery { repository.getAllEntries() } returns Result.success(listOf(insertedEntry))
            val second = useCase(uri)

            // Then — счётчиков у use-case нет, поэтому контракт «повторный
            // импорт ничего не вставляет» проверяем по аргументам вызовов
            // репозитория: первый импорт передаёт данные файла, второй —
            // пустые списки
            assertTrue(first.isSuccess, "Первый импорт добавляет данные файла")
            assertTrue(second.isSuccess, "Повторный импорт проходит без ошибок")
            val exercisesSlots = mutableListOf<List<Exercise>>()
            val entriesSlots = mutableListOf<List<BackupEntry>>()
            coVerify(exactly = 2) {
                repository.importData(capture(exercisesSlots), capture(entriesSlots))
            }
            assertEquals(1, exercisesSlots[0].size, "Первый импорт вставляет упражнение файла")
            assertEquals(1, entriesSlots[0].size, "Первый импорт вставляет запись файла")
            assertTrue(exercisesSlots[1].isEmpty(), "Повторный импорт не дублирует упражнение")
            assertTrue(entriesSlots[1].isEmpty(), "Повторный импорт того же файла ничего не добавляет")
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
            val useCase = useCase(repository, file)

            // When
            val result = useCase(uri)

            // Then — импорт успешен, иконка null
            assertTrue(result.isSuccess, "Старый файл без iconNumber остаётся валидным")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(capture(exercisesSlot), any()) }
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
            val useCase = useCase(repository, plankWithIconFile)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(result.isSuccess, "Вставка проходит")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(capture(exercisesSlot), any()) }
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
                    category = ExerciseCategory.CORE,
                    isCustom = false,
                    comment = null,
                    createdAt = 1_757_371_150_000L,
                    measuredBy = ExerciseMeasuredBy.TIME,
                    iconNumber = 2
                )
            val repository = repository(exercises = listOf(existingPlank))
            val useCase = useCase(repository, plankWithIconFile)

            // When
            val result = useCase(uri)

            // Then — дубликат не вставляется, существующее не трогается
            assertTrue(result.isSuccess, "Дедупликация не ломает импорт")
            val exercisesSlot = slot<List<Exercise>>()
            coVerify { repository.importData(capture(exercisesSlot), any()) }
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

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidFiles")
    fun import_when_file_invalid_then_failure_without_data_change(fileContent: String) =
        runTest {
            // Given: файл с ошибкой формата (design Decision 5)
            val repository = repository()
            val useCase = useCase(repository, fileContent)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(result.isFailure, "Ошибочный файл — Result.failure")
            val exception = assertInstanceOf(BackupException::class.java, result.exceptionOrNull())
            assertEquals(BackupError.INVALID_FILE, exception.error, "Тип ошибки — неверный файл")
            coVerify(exactly = 0) { repository.importData(any(), any()) }
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
            val useCase = useCase(repository, file)

            // When
            val result = useCase(uri)

            // Then
            assertTrue(result.isSuccess, "Неизвестные поля не мешают импорту")
        }

    companion object {
        /**
         * Источник данных для параметризованного теста ошибок файла.
         *
         * `LongMethod` подавлен осознанно: это таблица данных design
         * Decision 5, а не логика — по одному JSON-кейсу на строку.
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
                        "version не 1",
                        """{"version": 2, "exercises": [], "entries": []}"""
                    )
                ),
                Arguments.of(
                    Named.of(
                        "version строкой",
                        """{"version": "1", "exercises": [], "entries": []}"""
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
                        "неизвестный category",
                        """{"version": 1, "exercises": [{"name": "X", "category": "STRENGTH",
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
                        "sets строкой",
                        """{"version": 1, "exercises": [],
                       "entries": [{"exerciseName": "X", "date": 1, "sets": "4", "createdAt": 1}]}"""
                    )
                )
            )
    }
}
