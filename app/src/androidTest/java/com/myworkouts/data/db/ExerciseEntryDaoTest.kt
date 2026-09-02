package com.myworkouts.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented-тест для [ExerciseEntryDao].
 *
 * Спецификация «Запись о выполнении ссылается на упражнение каталога»:
 * - вставка записи с существующим `exerciseId` сохраняется;
 * - с несуществующим — FK-ошибка, запись не создаётся.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseEntryDaoTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var entryDao: ExerciseEntryDao
    private lateinit var exerciseDao: ExerciseDao

    private val existingExerciseId: Long = 1L

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
        // Заполняем каталог одним упражнением, чтобы был валидный exerciseId.
        runBlocking {
            exerciseDao.insert(
                ExerciseEntity(
                    name = "Подтягивания",
                    category = "PULL",
                    isCustom = false,
                    comment = null,
                    createdAt = 0L
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun sampleEntry(exerciseId: Long): ExerciseEntryEntity =
        ExerciseEntryEntity(
            exerciseId = exerciseId,
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

    @Test
    fun insert_with_existing_exerciseId_persists_and_isReadable() =
        runBlocking {
            // Given
            val entry = sampleEntry(existingExerciseId)

            // When
            val id = entryDao.insert(entry)
            val read = entryDao.getById(id)

            // Then
            assertNotNull(read)
            assertEquals(id, read?.id)
            assertEquals(existingExerciseId, read?.exerciseId)
            assertEquals(4, read?.sets)
            assertEquals(10, read?.reps)
        }

    @Test
    fun insert_with_nonExisting_exerciseId_fails_andNothingIsWritten() =
        runBlocking {
            // Given
            val entry = sampleEntry(exerciseId = 9_999L)

            // When + Then — FK-ошибка Room
            try {
                entryDao.insert(entry)
                fail("Expected FK constraint to fail for non-existing exerciseId")
            } catch (expected: Exception) {
                // Room оборачивает SQLiteConstraintException, нас устраивает любой
                // throwable — важно, что insert не прошёл.
                assertNotNull(expected.message)
            }

            // Then — таблица записей пуста
            assertNull(entryDao.getById(1L))
        }
}
