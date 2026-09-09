package com.myworkouts.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myworkouts.data.db.ExerciseEntity
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented-тест методов управления данными
 * [RoomWorkoutDataRepository] (app-data-screen, design Decision 6) на
 * Room in-memory:
 * - `getAllExercises`/`getAllEntries` — разовые доменные снимки;
 * - `importData` — вставка с разрешением имён в id, `isCustom = true`
 *   у вставляемых, `createdAt` из файла, `modifiedAt` = моменту импорта;
 * - `importData` — атомарность: неразрешимое имя записи —
 *   `IllegalStateException` внутри транзакции, откат без частичных
 *   вставок (единственный детерминированный сбой через публичный
 *   контракт; unique-индекса на имени упражнения в БД нет);
 * - `deleteAllData` — удаляет всё и восстанавливает стандартный набор
 *   (`isCustom = false`).
 */
@RunWith(AndroidJUnit4::class)
class RoomWorkoutDataRepositoryDataManagementTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository

    /**
     * Стандартный набор для проверки восстановления `deleteAllData`;
     * с задачи 6.2 передаётся в конструктор репозитория.
     */
    private val standardExercises =
        listOf(
            StandardExercise(name = "Подтягивания", category = ExerciseCategory.PULL),
            StandardExercise(
                name = "Планка",
                category = ExerciseCategory.CORE,
                measuredBy = ExerciseMeasuredBy.TIME
            )
        )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        repository = RoomWorkoutDataRepository(database, standardExercises)
        // Стандартное упражнение каталога — «Подтягивания» (REPS).
        runBlocking {
            database.exerciseDao().insert(
                ExerciseEntity(
                    name = "Подтягивания",
                    category = "PULL",
                    isCustom = false,
                    createdAt = 0L
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun getAll_returnsDomainSnapshots_ofCatalogAndEntries() =
        runBlocking {
            // Given — запись для упражнения из setUp
            database
                .exerciseEntryDao()
                .insert(
                    com.myworkouts.data.db.ExerciseEntryEntity(
                        exerciseId = 1L,
                        workoutId = null,
                        date = 1_700_000_000_000L,
                        sets = 4,
                        reps = 10,
                        weightKg = null,
                        durationSec = null,
                        comment = null,
                        createdAt = 1L,
                        modifiedAt = 1L
                    )
                )

            // When
            val exercises = repository.getAllExercises()
            val entries = repository.getAllEntries()

            // Then — доменные снимки: каталог и записи
            assertTrue(exercises.isSuccess)
            assertTrue(entries.isSuccess)
            val catalog = exercises.getOrThrow()
            assertEquals(1, catalog.size)
            assertEquals("Подтягивания", catalog[0].name)
            assertFalse("Стандартное упражнение снимается как isCustom=false", catalog[0].isCustom)
            val snapshot = entries.getOrThrow()
            assertEquals(1, snapshot.size)
            assertEquals(4, snapshot[0].sets)
            assertEquals(10, snapshot[0].reps)
        }

    @Test
    fun importData_insertsExercisesAndEntries_withResolvedIds() =
        runBlocking {
            // Given — новое упражнение из копии и две записи: одна на новое,
            // другая на существующее из каталога
            val burpee =
                Exercise(
                    id = 0L,
                    name = "Бёрпи",
                    category = ExerciseCategory.PUSH,
                    isCustom = true,
                    comment = null,
                    createdAt = 99L,
                    measuredBy = ExerciseMeasuredBy.REPS
                )
            val onBurpee =
                BackupEntry(
                    exerciseName = "Бёрпи",
                    date = 1_700_000_000_000L,
                    sets = 3,
                    reps = 12,
                    weightKg = null,
                    durationSec = null,
                    comment = "из копии",
                    createdAt = 42L
                )
            val onPullups =
                BackupEntry(
                    exerciseName = "Подтягивания",
                    date = 1_700_000_100_000L,
                    sets = 4,
                    reps = 8,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 43L
                )

            val before = System.currentTimeMillis()
            val result = repository.importData(listOf(burpee), listOf(onBurpee, onPullups))
            val after = System.currentTimeMillis()

            // Then — успех; упражнение вставлено с сохранением полей
            assertTrue(result.isSuccess)
            val catalog = repository.getAllExercises().getOrThrow()
            assertEquals(2, catalog.size)
            val inserted = catalog.single { it.name == "Бёрпи" }
            assertTrue("Вставленное из копии должно быть isCustom=true", inserted.isCustom)
            assertEquals(99L, inserted.createdAt)

            // Then — записи вставлены с разрешёнными id и метками времени
            val entries = repository.getAllEntries().getOrThrow()
            assertEquals(2, entries.size)
            val burpeeEntry = entries.single { it.comment == "из копии" }
            assertEquals(inserted.id, burpeeEntry.exerciseId)
            assertEquals(42L, burpeeEntry.createdAt)
            assertEquals(null, burpeeEntry.workoutId)
            assertTrue(
                "modifiedAt=${burpeeEntry.modifiedAt} должен быть в [$before, $after]",
                burpeeEntry.modifiedAt in before..after
            )
            val pullupsEntry = entries.single { it.createdAt == 43L }
            assertEquals(1L, pullupsEntry.exerciseId)
        }

    @Test
    fun importData_withUnresolvedName_fails_andRollsBackEverything() =
        runBlocking {
            // Given — упражнение вставляется, но запись ссылается на
            // имя, которого нет ни в файле, ни в каталоге
            val burpee =
                Exercise(
                    id = 0L,
                    name = "Бёрпи",
                    category = ExerciseCategory.PUSH,
                    isCustom = true,
                    comment = null,
                    createdAt = 99L
                )
            val ghost =
                BackupEntry(
                    exerciseName = "Призрачное упражнение",
                    date = 1_700_000_000_000L,
                    sets = 3,
                    reps = 12,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 42L
                )

            // When
            val result = repository.importData(listOf(burpee), listOf(ghost))

            // Then — отказ: IllegalArgumentException не подходит — репозиторий
            // защищается IllegalStateException (design Decision 6)
            assertTrue(result.isFailure)
            assertTrue(
                "Неразрешимое имя — IllegalStateException, получили ${result.exceptionOrNull()}",
                result.exceptionOrNull() is IllegalStateException
            )

            // Then — транзакция откатилась целиком: ни упражнения, ни записи
            assertTrue(
                "Упражнение должно быть откачено вместе с транзакцией",
                repository.getAllExercises().getOrThrow().none { it.name == "Бёрпи" }
            )
            assertTrue(
                "Записи не должны быть вставлены после отката",
                repository.getAllEntries().getOrThrow().isEmpty()
            )
        }

    @Test
    fun deleteAllData_clearsEverything_andRestoresStandardSet() =
        runBlocking {
            // Given — стандартное из setUp, пользовательское и запись
            val customId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = "Бёрпи",
                            category = ExerciseCategory.PUSH,
                            isCustom = true,
                            comment = null,
                            createdAt = 99L
                        )
                    ).getOrThrow()
            repository
                .addEntry(
                    com.myworkouts.domain.model.ExerciseEntry(
                        id = 0L,
                        exerciseId = customId,
                        workoutId = null,
                        date = 1_700_000_000_000L,
                        sets = 3,
                        reps = 12,
                        weightKg = null,
                        durationSec = null,
                        comment = null,
                        createdAt = 1L,
                        modifiedAt = 1L
                    )
                ).getOrThrow()

            // When
            val result = repository.deleteAllData()

            // Then — успех; записей нет, каталог — ровно стандартный набор
            assertTrue(result.isSuccess)
            assertTrue(
                "Все записи должны быть удалены",
                repository.getAllEntries().getOrThrow().isEmpty()
            )
            val catalog = repository.getAllExercises().getOrThrow()
            assertEquals(standardExercises.size, catalog.size)
            assertEquals(
                standardExercises.map { it.name }.toSet(),
                catalog.map { it.name }.toSet()
            )
            assertTrue(
                "Восстановленный набор — только стандартные упражнения",
                catalog.none { it.isCustom }
            )
            // Реактивный список тоже пуст
            assertTrue(repository.entries().first().isEmpty())
        }
}
