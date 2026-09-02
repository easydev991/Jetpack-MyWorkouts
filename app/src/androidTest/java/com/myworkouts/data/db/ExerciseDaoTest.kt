package com.myworkouts.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented-тест для [ExerciseDao].
 *
 * Спецификация «Поиск и сортировка каталога»:
 * - подстрока «под» без учёта регистра возвращает только совпадения;
 * - пустой запрос возвращает весь каталог;
 * - сортировка по имени без учёта регистра.
 */
@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        dao = database.exerciseDao()

        // Заполняем каталог: по «под» должно матчить 2, по «Подъёмы» — 1,
        // по пустой подстроке — все 4.
        runBlocking {
            listOf(
                ExerciseEntity(name = "Подтягивания", category = "PULL", isCustom = false, createdAt = 0L),
                ExerciseEntity(name = "Подъёмы ног в висе", category = "CORE", isCustom = false, createdAt = 0L),
                ExerciseEntity(name = "Отжимания", category = "PUSH", isCustom = false, createdAt = 0L),
                ExerciseEntity(name = "Приседания", category = "LEGS", isCustom = false, createdAt = 0L)
            ).forEach { dao.insert(it) }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun search_substring_matchesCaseInsensitive() =
        runBlocking {
            // When
            val result = dao.observeExercises("под").first()

            // Then
            val names = result.map { it.name }
            assertEquals(2, result.size)
            assertTrue(
                "Expected 'Подтягивания' and 'Подъёмы ног в висе', got $names",
                names.containsAll(listOf("Подтягивания", "Подъёмы ног в висе"))
            )
        }

    @Test
    fun search_lowercase_substring_matchesUppercaseNames() =
        runBlocking {
            // When — запрос в нижнем регистре
            val result = dao.observeExercises("под").first()

            // Then — находит имена с заглавной «П»
            assertTrue(result.any { it.name == "Подтягивания" })
        }

    @Test
    fun search_emptyQuery_returnsWholeCatalog() =
        runBlocking {
            // When
            val result = dao.observeExercises("").first()

            // Then
            assertEquals(4, result.size)
        }

    @Test
    fun search_results_areSortedByNameCaseInsensitive() =
        runBlocking {
            // Given — SQLite NOCASE-коллация покрывает только ASCII, поэтому
            // проверяем контракт на ASCII-выборке. Перезальём каталог.
            database.close()
            database =
                Room
                    .inMemoryDatabaseBuilder(
                        androidx.test.core.app.ApplicationProvider
                            .getApplicationContext(),
                        WorkoutDatabase::class.java
                    ).allowMainThreadQueries()
                    .build()
            dao = database.exerciseDao()
            listOf("alpha", "Beta", "CHARLIE", "delta").forEach {
                dao.insert(ExerciseEntity(name = it, category = "PUSH", isCustom = false, createdAt = 0L))
            }

            // When
            val result = dao.observeExercises("").first().map { it.name }

            // Then — NOCASE: лексикографический порядок без учёта регистра
            assertEquals(listOf("alpha", "Beta", "CHARLIE", "delta"), result)
        }

    @Test
    fun search_returnsEmpty_whenNoMatch() =
        runBlocking {
            // When
            val result = dao.observeExercises("зимбабве").first()

            // Then
            assertTrue(result.isEmpty())
        }

    @Test
    fun getById_returnsNull_whenMissing() =
        runBlocking {
            // When
            val result = dao.getById(9_999L)

            // Then
            assertEquals(null, result)
        }

    @Test
    fun getById_returnsEntity_whenExists() =
        runBlocking {
            // When
            val first = dao.observeExercises("").first()
            val byId = dao.getById(first[0].id)

            // Then
            assertEquals(first[0].name, byId?.name)
        }
}
