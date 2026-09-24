package com.myworkouts.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
 *
 * Спецификация «Реактивный список записей» (workout-entries-list):
 * - пустая БД → пустой список;
 * - вставка двух записей с разными датами → обе в проекции с названием
 *   упражнения (JOIN) и порядком по дате убыванием;
 * - подписка до insert/delete получает обновлённые списки (ре-эмиссия
 *   через InvalidationTracker, сценарий «Список актуализируется при
 *   изменениях»).
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

    @Test
    fun observeEntryRows_whenNoEntries_emitsEmptyList() =
        runBlocking {
            // Given — пустая БД (только одно упражнение из setUp, без записей)

            // When
            val items = entryDao.observeEntryRows().first()

            // Then
            assertTrue(
                "Пустая БД должна эммитить пустой список",
                items.isEmpty()
            )
        }

    @Test
    fun observeEntryRows_returnsProjectionWithExerciseName_andOrderedByDateDesc() =
        runBlocking {
            // Given — две записи с разными датами (старая и новая)
            val olderDate = 1_700_000_000_000L
            val newerDate = 1_700_000_100_000L
            entryDao.insert(
                sampleEntry(exerciseId = existingExerciseId).copy(date = olderDate)
            )
            entryDao.insert(
                sampleEntry(exerciseId = existingExerciseId).copy(date = newerDate)
            )

            // When
            val items = entryDao.observeEntryRows().first()

            // Then — обе записи в проекции с названием упражнения, новые первыми
            assertEquals(2, items.size)
            assertEquals(newerDate, items[0].date)
            assertEquals(olderDate, items[1].date)
            items.forEach {
                assertEquals(
                    "Проекция должна содержать название упражнения из JOIN",
                    "Подтягивания",
                    it.exerciseName
                )
            }
        }

    @Test
    fun observeEntryRows_whenInsertAndDelete_emitsUpdatedListsToSubscriber() =
        runBlocking {
            // Given — подписка ДО мутаций; первая эмиссия — пустой список
            val emissions = Channel<List<EntryListItemWithExerciseEntity>>(Channel.UNLIMITED)
            val collectJob =
                launch {
                    entryDao.observeEntryRows().collect { emissions.send(it) }
                }
            assertTrue(
                "Первая эмиссия подписчика — пустой список",
                emissions.receive().isEmpty()
            )

            // When — вставляем запись
            val id = entryDao.insert(sampleEntry(existingExerciseId))

            // Then — подписчик получил список с этой записью
            val afterInsert = emissions.receive()
            assertEquals("Ре-эмиссия после insert содержит запись", 1, afterInsert.size)
            assertEquals(id, afterInsert[0].id)

            // When — удаляем запись
            entryDao.deleteById(id)

            // Then — подписчик получил пустой список
            assertTrue(
                "Ре-эмиссия после delete — пустой список",
                emissions.receive().isEmpty()
            )

            collectJob.cancel()
        }

    /**
     * Спецификация «Комментарий в строке записи» (mainscreen-itemrow-comment):
     * проекция observeEntryRows доносит `comment` из таблицы записей —
     * непустой переносится, `null` остаётся `null`. Соответствие id →
     * комментарий проверяем по id: ORDER BY date DESC при равных датах
     * не гарантирует стабильный порядок строк.
     */
    @Test
    fun observeEntryRows_whenEntriesWithAndWithoutComment_thenProjectionCarriesComment() =
        runBlocking {
            // Given — запись с комментарием и запись без
            val idWithComment =
                entryDao.insert(
                    sampleEntry(existingExerciseId).copy(comment = "сразу после сна")
                )
            val idWithoutComment =
                entryDao.insert(sampleEntry(existingExerciseId).copy(comment = null))

            // When
            val items = entryDao.observeEntryRows().first()

            // Then — обе записи в проекции, комментарий у своего id
            assertEquals(2, items.size)
            val byId = items.associateBy { it.id }
            assertEquals(
                "Проекция должна содержать комментарий записи",
                "сразу после сна",
                byId[idWithComment]?.comment
            )
            assertNull(byId[idWithoutComment]?.comment)
        }

    @Test
    fun getAll_returnsSnapshotOfAllEntries() =
        runBlocking {
            // Given — две записи с разными датами
            entryDao.insert(sampleEntry(existingExerciseId))
            entryDao.insert(sampleEntry(existingExerciseId).copy(date = 1_700_000_100_000L))

            // When
            val snapshot = entryDao.getAll()

            // Then — обе в снимке, поля доезжают без потерь
            assertEquals(2, snapshot.size)
            assertTrue(snapshot.all { it.exerciseId == existingExerciseId })
            assertTrue(snapshot.any { it.date == 1_700_000_100_000L })
        }

    @Test
    fun insertAll_writesEveryEntity() =
        runBlocking {
            // Given
            val entries =
                listOf(
                    sampleEntry(existingExerciseId),
                    sampleEntry(existingExerciseId).copy(sets = 5)
                )

            // When
            entryDao.insertAll(entries)

            // Then
            assertEquals(2, entryDao.getAll().size)
            assertTrue(entryDao.getAll().any { it.sets == 5 })
        }

    @Test
    fun deleteAll_removesEveryRow() =
        runBlocking {
            // Given
            entryDao.insert(sampleEntry(existingExerciseId))

            // When
            entryDao.deleteAll()

            // Then
            assertTrue(entryDao.getAll().isEmpty())
        }
}
