package com.myworkouts.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myworkouts.data.db.ExerciseEntity
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
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
 * Instrumented-тест для [RoomWorkoutDataRepository] на Room in-memory.
 *
 * Покрывает спецификации workout-data через репозиторий (а не только DAO):
 * - «Ссылка на несуществующее упражнение» — `addEntry` с несуществующим
 *   `exerciseId` возвращает `Result.isFailure`, таблица записей остаётся
 *   пустой. Это требование важно держать на уровне API, который
 *   использует UI: VM-код не должен ловить исключения, репозиторий
 *   заворачивает их в `Result`.
 * - «Создание пользовательского упражнения» — `addExercise` создаёт
 *   упражнение с `isCustom=true`, оно появляется в каталоге.
 * - «Редактирование комментария стандартного упражнения» —
 *   `updateExercise` меняет комментарий ровно у одной записи, не трогая
 *   `isCustom`.
 * - «Изменение подходов и повторов» — `updateEntry` обновляет поля и
 *   проставляет `modifiedAt = now`, `createdAt` не трогает.
 */
@RunWith(AndroidJUnit4::class)
class RoomWorkoutDataRepositoryTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        repository =
            RoomWorkoutDataRepository(
                exerciseDao = database.exerciseDao(),
                exerciseEntryDao = database.exerciseEntryDao()
            )
        // Подложим одно упражнение, чтобы были валидные `exerciseId`.
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
    fun addEntry_withNonExistingExerciseId_returnsFailure_andNothingIsPersisted() =
        runBlocking {
            // Given
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 9_999L, // не существует в каталоге
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 1,
                    reps = 10,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val result = repository.addEntry(entry)

            // Then
            assertTrue("Expected failure for FK violation", result.isFailure)
        }

    @Test
    fun addEntry_withNonExistingExerciseId_doesNotWriteAnyEntry() =
        runBlocking {
            // Given
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 9_999L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 1,
                    reps = 10,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val result = repository.addEntry(entry)

            // Then — Result.failure
            assertTrue(result.isFailure)

            // Then — таблица записей пуста: getById для любого id возвращает null
            assertEquals(null, database.exerciseEntryDao().getById(1L))
            assertEquals(null, database.exerciseEntryDao().getById(2L))
        }

    @Test
    fun addEntry_withExistingExerciseId_succeeds_andIsReadable() =
        runBlocking {
            // Given
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 1L, // существующее упражнение из setUp
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 4,
                    reps = 10,
                    weightKg = 5.0,
                    durationSec = null,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val result = repository.addEntry(entry)

            // Then
            assertTrue(result.isSuccess)
            val id = result.getOrThrow()
            val fetched = repository.getEntry(id).getOrThrow()
            assertEquals(4, fetched?.sets)
            assertEquals(10, fetched?.reps)
            assertEquals(5.0, fetched?.weightKg)
        }

    @Test
    fun addEntry_withInvalidSets_returnsFailure_withoutTouchingDatabase() =
        runBlocking {
            // Given — валидатор должен отклонить до попытки записи в БД
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 1L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 0, // невалидно
                    reps = 10,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val result = repository.addEntry(entry)

            // Then
            assertTrue(result.isFailure)
            assertTrue(
                result.exceptionOrNull() is EntryValidationException
            )
            assertEquals(null, database.exerciseEntryDao().getById(1L))
        }

    @Test
    fun updateEntry_setsModifiedAt_toNow_andKeepsCreatedAt() =
        runBlocking {
            // Given — вставим запись
            val injected =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 1L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 4,
                    reps = 10,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 1_000L,
                    modifiedAt = 1_000L
                )
            val id = repository.addEntry(injected).getOrThrow()

            val before = System.currentTimeMillis()
            val result =
                repository.updateEntry(
                    injected.copy(
                        id = id,
                        sets = 5,
                        reps = 8
                    )
                )
            val after = System.currentTimeMillis()

            // Then
            assertTrue(result.isSuccess)
            val fetched = repository.getEntry(id).getOrThrow()!!
            assertEquals(5, fetched.sets)
            assertEquals(8, fetched.reps)
            // createdAt не должен меняться
            assertEquals(1_000L, fetched.createdAt)
            // modifiedAt — где-то между `before` и `after` (System.currentTimeMillis())
            assertTrue(
                "modifiedAt=${fetched.modifiedAt} not in [$before, $after]",
                fetched.modifiedAt in before..after
            )
        }

    @Test
    fun addExercise_createsUserExercise_visibleWithIsCustomTrue() =
        runBlocking {
            // Given — пользовательское упражнение с комментарием (spec: «Создание
            // пользовательского упражнения»)
            val exercise =
                Exercise(
                    id = 0L,
                    name = "Отжимания на турнике",
                    category = ExerciseCategory.PULL,
                    isCustom = true,
                    comment = "широкий хват",
                    createdAt = 1_700_000_000_000L
                )

            // When
            val id =
                repository
                    .addExercise(exercise)
                    .getOrThrow()

            // Then — упражнение появилось в каталоге с пометкой «пользовательское»
            val fetched =
                repository
                    .exercises("")
                    .first()
                    .single { it.id == id }
            assertTrue(
                "Expected isCustom=true for user-created exercise",
                fetched.isCustom
            )
            assertEquals("широкий хват", fetched.comment)
            assertEquals(ExerciseCategory.PULL, fetched.category)
        }

    @Test
    fun addExercise_keepsStandardExercisesUnchanged() =
        runBlocking {
            // Given — в setUp уже лежит «Подтягивания» (стандартное, isCustom=false)
            val standardBefore =
                database
                    .exerciseDao()
                    .observeExercises("")
                    .first()
                    .single { it.name == "Подтягивания" }

            // When — добавляем пользовательское упражнение
            repository
                .addExercise(
                    Exercise(
                        id = 0L,
                        name = "Отжимания на турнике",
                        category = ExerciseCategory.PULL,
                        isCustom = true,
                        comment = "широкий хват",
                        createdAt = 1_700_000_000_000L
                    )
                ).getOrThrow()

            // Then — стандартное упражнение не затронуто (id, isCustom, name)
            val standardAfter =
                database
                    .exerciseDao()
                    .getById(standardBefore.id)
            assertEquals(standardBefore.id, standardAfter?.id)
            assertEquals("Подтягивания", standardAfter?.name)
            assertFalse(
                "Standard exercise must remain isCustom=false",
                standardAfter?.isCustom ?: true
            )
        }

    @Test
    fun updateExercise_changesComment_keepsIsCustomFalse() =
        runBlocking {
            // Given — id стандартного упражнения из setUp
            val pullupsId =
                database
                    .exerciseDao()
                    .observeExercises("")
                    .first()
                    .single { it.name == "Подтягивания" }
                    .id

            // When — меняем комментарий через репозиторий
            val result =
                repository.updateExercise(
                    Exercise(
                        id = pullupsId,
                        name = "Подтягивания",
                        category = ExerciseCategory.PULL,
                        isCustom = false,
                        comment = "хват сверху, лопатки сведены",
                        createdAt = 0L
                    )
                )

            // Then — Result.success
            assertTrue(result.isSuccess)

            // Then — изменена ровно одна строка: комментарий обновился,
            // isCustom остался false, name не затронут
            val after = database.exerciseDao().getById(pullupsId)
            assertEquals("хват сверху, лопатки сведены", after?.comment)
            assertFalse(
                "isCustom must remain false for standard exercise",
                after?.isCustom ?: true
            )
            assertEquals("Подтягивания", after?.name)
        }
}
