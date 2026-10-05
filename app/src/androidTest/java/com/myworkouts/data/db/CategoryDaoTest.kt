package com.myworkouts.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented-тест контрактов [CategoryDao] (custom-categories,
 * задача 1.2, по образцу [ExerciseDaoTest]):
 * - unique-индекс `name` отклоняет дубль точного совпадения
 *   (регистронезависимый дедуп — задача репозитория, раздел 2);
 * - сортировка `is_custom DESC, created_at DESC, name COLLATE NOCASE`;
 * - архивный фильтр активного/архивного списков;
 * - счётчик архивных.
 */
@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var dao: CategoryDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        dao = database.categoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun category(
        name: String,
        isCustom: Boolean = true,
        isArchived: Boolean = false,
        createdAt: Long = 0L
    ): CategoryEntity =
        CategoryEntity(
            name = name,
            colorArgb = 0xFF448AFF.toInt(),
            isCustom = isCustom,
            isArchived = isArchived,
            createdAt = createdAt
        )

    @Test
    fun insert_whenDuplicateExactName_thenThrowsConstraintException() {
        // Given — категория «Спина» уже в БД
        runBlocking { dao.insert(category("Спина")) }

        // When + Then — unique-индекс name отклоняет точный дубль;
        // дедуп без учёта регистра/краёв — валидация репозитория
        assertThrows(
            "Точный дубль имени должен отклоняться unique-индексом",
            android.database.sqlite.SQLiteConstraintException::class.java
        ) {
            runBlocking { dao.insert(category("Спина")) }
        }
    }

    @Test
    fun observeCategories_whenSorted_thenCustomFirst_thenNewer_thenNameNocase() =
        runBlocking {
            // Given — кастомные выше стандартных; среди кастомных —
            // свежее выше; при равном createdAt — name COLLATE NOCASE.
            // SQLite NOCASE-коллация покрывает только ASCII — выборка ASCII.
            dao.insert(category("alpha", isCustom = false, createdAt = 0L))
            dao.insert(category("Beta", isCustom = false, createdAt = 0L))
            dao.insert(category("CHARLIE", isCustom = true, createdAt = 1_000L))
            dao.insert(category("delta", isCustom = true, createdAt = 2_000L))

            // When
            val names = dao.observeCategories().first().map { it.name }

            // Then
            assertEquals(listOf("delta", "CHARLIE", "alpha", "Beta"), names)
        }

    @Test
    fun lists_whenArchivedExists_thenActiveExcludes_andArchivedContains() =
        runBlocking {
            // Given — две активные и одна архивная
            dao.insert(category("Спина", isCustom = true, isArchived = false))
            dao.insert(category("Плечи", isCustom = true, isArchived = false))
            dao.insert(category("CARDIO", isCustom = false, isArchived = true))

            // When
            val active = dao.observeCategories().first().map { it.name }
            val archived = dao.observeArchivedCategories().first().map { it.name }

            // Then — архивный фильтр разводит списки
            assertEquals(setOf("Спина", "Плечи"), active.toSet())
            assertEquals(listOf("CARDIO"), archived)
        }

    @Test
    fun observeArchivedCount_whenTwoArchivedOfFive_thenEmitsTwo() =
        runBlocking {
            // Given — 3 активные и 2 архивные
            dao.insert(category("Спина", isArchived = false))
            dao.insert(category("Плечи", isArchived = false))
            dao.insert(category("PUSH", isCustom = false, isArchived = false))
            dao.insert(category("LEGS", isCustom = false, isArchived = true))
            dao.insert(category("CARDIO", isCustom = false, isArchived = true))

            // When
            val count = dao.observeArchivedCount().first()

            // Then — счётчик считает только архивные
            assertEquals(2, count)
        }

    @Test
    fun deleteAll_whenCalled_thenRemovesEveryCategoryRow() =
        runBlocking {
            // Given
            dao.insert(category("Спина"))
            assertTrue(dao.observeCategories().first().isNotEmpty())

            // When
            dao.deleteAll()

            // Then
            assertTrue(dao.observeCategories().first().isEmpty())
            assertEquals(0, dao.observeArchivedCount().first())
        }
}
