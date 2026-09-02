package com.myworkouts.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented-тест для обновления записей о выполнении.
 *
 * Спецификация «Редактирование записи о выполнении»:
 * - update записи меняет поля;
 * - `modifiedAt` обновляется, `createdAt` не меняется;
 * - вес `null` vs `0.0` различимы при чтении;
 * - «планка 60 сек» сохраняется с `reps = null`.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseEntryUpdateTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var entryDao: ExerciseEntryDao
    private lateinit var exerciseDao: ExerciseDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        entryDao = database.exerciseEntryDao()
        exerciseDao = database.exerciseDao()
        runBlocking {
            exerciseDao.insert(
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
    fun update_changesFields_andUpdatesModifiedAt_keepsCreatedAt() =
        runBlocking {
            // Given
            val originalCreatedAt = 1_000L
            val originalModifiedAt = 1_500L
            val insertedId =
                entryDao.insert(
                    ExerciseEntryEntity(
                        exerciseId = 1L,
                        workoutId = null,
                        date = 1_700_000_000_000L,
                        sets = 4,
                        reps = 10,
                        weightKg = null,
                        durationSec = null,
                        comment = null,
                        createdAt = originalCreatedAt,
                        modifiedAt = originalModifiedAt
                    )
                )

            // When
            val newModifiedAt = originalModifiedAt + 1_000L
            entryDao.update(
                ExerciseEntryEntity(
                    id = insertedId,
                    exerciseId = 1L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 5,
                    reps = 8,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = originalCreatedAt,
                    modifiedAt = newModifiedAt
                )
            )
            val updated = entryDao.getById(insertedId)

            // Then
            assertNotNull(updated)
            assertEquals(5, updated?.sets)
            assertEquals(8, updated?.reps)
            assertEquals(newModifiedAt, updated?.modifiedAt)
            // createdAt не трогается
            assertEquals(originalCreatedAt, updated?.createdAt)
            // modifiedAt больше прежнего
            assertNotEquals(originalModifiedAt, updated?.modifiedAt)
        }

    @Test
    fun weight_null_and_zero_areDistinctOnRead() =
        runBlocking {
            // Given — две записи: без веса и с весом 0.0
            val nullWeightId =
                entryDao.insert(
                    ExerciseEntryEntity(
                        exerciseId = 1L,
                        workoutId = null,
                        date = 1_700_000_000_000L,
                        sets = 4,
                        reps = 10,
                        weightKg = null,
                        createdAt = 1L,
                        modifiedAt = 1L
                    )
                )
            val zeroWeightId =
                entryDao.insert(
                    ExerciseEntryEntity(
                        exerciseId = 1L,
                        workoutId = null,
                        date = 1_700_000_000_000L + 1,
                        sets = 1,
                        reps = 5,
                        weightKg = 0.0,
                        createdAt = 1L,
                        modifiedAt = 1L
                    )
                )

            // When
            val nullRead = entryDao.getById(nullWeightId)
            val zeroRead = entryDao.getById(zeroWeightId)

            // Then — состояния различимы
            assertNull(nullRead?.weightKg)
            assertNotNull(zeroRead?.weightKg)
            assertEquals(0.0, zeroRead?.weightKg)
        }

    @Test
    fun plank_60sec_savesWithRepsNull() =
        runBlocking {
            // When
            val id =
                entryDao.insert(
                    ExerciseEntryEntity(
                        exerciseId = 1L,
                        workoutId = null,
                        date = 1_700_000_000_000L,
                        sets = 1,
                        reps = null,
                        weightKg = null,
                        durationSec = 60,
                        comment = null,
                        createdAt = 1L,
                        modifiedAt = 1L
                    )
                )

            // Then
            val read = entryDao.getById(id)
            assertNotNull(read)
            assertEquals(1, read?.sets)
            assertNull(read?.reps)
            assertEquals(60, read?.durationSec)
        }

    @Test
    fun update_canChangeWeightNullToZeroAndBack() =
        runBlocking {
            // Given
            val id =
                entryDao.insert(
                    ExerciseEntryEntity(
                        exerciseId = 1L,
                        workoutId = null,
                        date = 1_700_000_000_000L,
                        sets = 1,
                        reps = 5,
                        weightKg = null,
                        createdAt = 1L,
                        modifiedAt = 1L
                    )
                )

            // When — null → 0.0
            entryDao.update(
                ExerciseEntryEntity(
                    id = id,
                    exerciseId = 1L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 1,
                    reps = 5,
                    weightKg = 0.0,
                    createdAt = 1L,
                    modifiedAt = 2L
                )
            )
            val afterZero = entryDao.getById(id)

            // Then — стали 0.0
            assertEquals(0.0, afterZero?.weightKg)

            // When — 0.0 → null
            entryDao.update(
                ExerciseEntryEntity(
                    id = id,
                    exerciseId = 1L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 1,
                    reps = 5,
                    weightKg = null,
                    createdAt = 1L,
                    modifiedAt = 3L
                )
            )
            val afterNull = entryDao.getById(id)

            // Then — снова null, не 0.0
            assertNull(afterNull?.weightKg)
        }
}
