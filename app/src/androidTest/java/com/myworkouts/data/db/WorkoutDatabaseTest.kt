package com.myworkouts.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Instrumented-тест для [WorkoutDatabase].
 *
 * Покрывает spec-сценарий «Первый запуск приложения»: после создания БД
 * каталог содержит ровно 13 стандартных упражнений, 0 пользовательских.
 * Состав и категории — design.md Decision 4.
 *
 * БД создаётся под русской локалью, чтобы состав упражнений читался
 * предсказуемо (AVD по умолчанию может иметь английскую локаль).
 * Локализация как таковая покрыта [StandardExercisesLocalizationTest].
 *
 * Исключение — тест деструктивной миграции: он работает с файловой БД
 * на production-имени [WorkoutDatabase.DATABASE_NAME] (см. KDoc теста),
 * файл удаляется в [tearDown].
 */
@RunWith(AndroidJUnit4::class)
class WorkoutDatabaseTest {
    private lateinit var database: WorkoutDatabase

    private fun ruContext(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val config = base.resources.configuration
        @Suppress("DEPRECATION")
        config.setLocale(Locale("ru"))
        return base.createConfigurationContext(config)
    }

    @Before
    fun setUp() {
        // In-memory-БД: проверяем production-настройку и prepopulate,
        // не затирая файл данных приложения на устройстве
        val ctx = ruContext()
        database =
            WorkoutDatabase.createInMemory(
                context = ctx,
                standardExercises = standardExercises(ctx)
            )
    }

    @After
    fun tearDown() {
        database.close()
        // Файловый тест деструктивной миграции работает на production-имени —
        // не оставляем после себя изменённый файл данных приложения
        ruContext().deleteDatabase(WorkoutDatabase.DATABASE_NAME)
    }

    @Test
    fun createDatabase_thenCatalogHasExactlyThirteenStandardExercises() =
        runBlocking {
            // When
            val exercises = database.exerciseDao().observeExercises("").first()

            // Then
            assertEquals(13, exercises.size)
            assertTrue(exercises.none { it.isCustom })
            assertTrue(exercises.all { it.comment == null })
        }

    @Test
    fun createDatabase_thenAllThirteenStandardExercisesHaveNullIconNumber() =
        runBlocking {
            // Given — БД с prepopulate из setUp (exercise-icon-mvp, spec-сценарий
            // «Стандартный набор без иконок при prepopulate»: MVP номера
            // не проставляет, seed-SQL пишет NULL в icon_number)

            // When
            val exercises = database.exerciseDao().observeExercises("").first()

            // Then — все 13 стандартных читаются с iconNumber = null
            assertEquals(13, exercises.size)
            assertTrue(
                "Prepopulate не назначает иконки: iconNumber = null у всех стандартных",
                exercises.all { it.iconNumber == null }
            )
        }

    @Test
    fun createDatabase_thenAllThirteenExercisesPresent() =
        runBlocking {
            // Given — ожидаемый состав по design.md Decision 4
            val expectedNames =
                listOf(
                    "Австралийские подтягивания",
                    "Бег",
                    "Выпады",
                    "Отжимания",
                    "Отжимания на брусьях",
                    "Отжимания на коленях",
                    "Планка",
                    "Подтягивания",
                    "Подъёмы ног в висе",
                    "Приседания",
                    "Приседания пистолетиком",
                    "Скакалка",
                    "Скручивания"
                )

            // When
            val names =
                database
                    .exerciseDao()
                    .observeExercises("")
                    .first()
                    .map { it.name }
                    .toSet()

            // Then
            assertEquals(expectedNames.toSet(), names)
        }

    @Test
    fun createDatabase_thenExercisesAssignedToExpectedCategories() =
        runBlocking {
            // Given — полное покрытие категорий (PUSH/PULL/LEGS/CORE/CARDIO)
            // проверяется в ExerciseCategoryTest, здесь — что состав упражнений
            // реально попадает в нужные категории.
            val expectedByCategory =
                mapOf(
                    ExerciseCategory.PULL to setOf("Подтягивания", "Австралийские подтягивания"),
                    ExerciseCategory.LEGS to setOf("Приседания", "Выпады", "Приседания пистолетиком"),
                    ExerciseCategory.PUSH to setOf("Отжимания", "Отжимания на коленях", "Отжимания на брусьях"),
                    ExerciseCategory.CORE to setOf("Планка", "Скручивания", "Подъёмы ног в висе"),
                    ExerciseCategory.CARDIO to setOf("Бег", "Скакалка")
                )

            // When
            val all = database.exerciseDao().observeExercises("").first()

            // Then
            expectedByCategory.forEach { (category, names) ->
                val actualNames = all.filter { it.category == category.name }.map { it.name }.toSet()
                assertEquals(
                    "Category $category: expected $names, got $actualNames",
                    names,
                    actualNames
                )
            }
        }

    @Test
    fun createDatabase_thenMeasuredByAssignedToExpectedExercises() =
        runBlocking {
            // Given — бег, планка и скакалка на время, остальные 10 на повторы
            val timeNames = setOf("Бег", "Планка", "Скакалка")

            // When
            val all = database.exerciseDao().observeExercises("").first()

            // Then
            val timeExercises =
                all
                    .filter { it.measuredBy == ExerciseMeasuredBy.TIME.name }
                    .map { it.name }
                    .toSet()
            assertEquals(timeNames, timeExercises)
            assertEquals(
                10,
                all.count { it.measuredBy == ExerciseMeasuredBy.REPS.name }
            )
        }

    @Test
    fun createDatabase_thenExerciseDaoIsAvailable() {
        // Given
        val dao = database.exerciseDao()

        // Then
        assertNotNull(dao)
    }

    @Test
    fun createDatabase_thenExerciseEntryDaoIsAvailable() {
        // Given
        val dao = database.exerciseEntryDao()

        // Then
        assertNotNull(dao)
    }

    @Test
    fun createDatabase_thenNoEntriesExist() =
        runBlocking {
            // When — через прямой запрос
            val entries = database.exerciseEntryDao().getById(1L)

            // Then — таблица записей пустая, prepopulate только каталог
            assertEquals(null, entries)
        }

    @Test
    fun prepopulate_callback_runsOnce_perDatabaseCreation() =
        runBlocking {
            // Given — prepopulate уже отработал в setUp
            val firstCount =
                database
                    .exerciseDao()
                    .observeExercises("")
                    .first()
                    .size
            assertEquals(13, firstCount)

            // When — закрываем БД и открываем новую: callback.onCreate
            // срабатывает при каждом создании in-memory-базы, поэтому
            // контракт «ровно 13» проверяется на свежем экземпляре
            database.close()
            val ctx = ruContext()
            database =
                WorkoutDatabase.createInMemory(
                    context = ctx,
                    standardExercises = standardExercises(ctx)
                )

            // Then — тот же контракт: ровно 13 стандартных, ни одного пользовательского
            val second = database.exerciseDao().observeExercises("").first()
            assertEquals(13, second.size)
            assertFalse(second.any { it.isCustom })
        }

    @Test
    fun destructiveMigration_whenUserVersionDowngraded_thenReseedsThirteenStandardExercises() {
        val ctx = ruContext()

        // Given — файловая БД без prepopulate. Имя файла — production-имя:
        // WorkoutDatabase.create(...) открывает DATABASE_NAME и не принимает
        // имени в параметрах, изолированное имя тесту недоступно. Гигиена —
        // удаление файла в tearDown. Схема создаётся сразу в v3 (без
        // seed-callback), в неё вставляется пользовательская строка, затем
        // версия файла занижается до 2.
        database.close()
        ctx.deleteDatabase(WorkoutDatabase.DATABASE_NAME)
        val plain =
            Room
                .databaseBuilder(ctx, WorkoutDatabase::class.java, WorkoutDatabase.DATABASE_NAME)
                .build()
        plain.openHelper.writableDatabase.execSQL(
            "INSERT INTO exercises " +
                "(name, category, is_custom, comment, created_at, measured_by, icon_number) " +
                "VALUES ('Пользовательское до миграции', 'PUSH', 1, NULL, 1, 'REPS', NULL)"
        )
        plain.openHelper.writableDatabase.execSQL("PRAGMA user_version = 2")
        plain.close()

        // When — переоткрытие production-путём: schema v3, файл в v2 →
        // fallbackToDestructiveMigration, tables пересоздаются; Room вызывает
        // onDestructiveMigration, но НЕ onCreate — до фикса каталог пустой
        database =
            WorkoutDatabase.create(
                context = ctx,
                standardExercises = standardExercises(ctx)
            )

        // Then — после деструктивной миграции стандартный набор восстановлен:
        // ровно 13 строк с is_custom = 0 (spec-сценарий «Деструктивная
        // миграция восстанавливает стандартный набор»)
        val standardCount = countExercisesByCustomFlag(isCustom = false)
        assertEquals(
            "После деструктивной миграции каталог должен содержать 13 стандартных упражнений",
            13L,
            standardCount
        )

        // Then — пользовательские данные до миграции не восстанавливаются
        // (AND-условие сценария: миграция остаётся деструктивной)
        val customCount = countExercisesByCustomFlag(isCustom = true)
        assertEquals(
            "Пользовательские упражнения не должны восстанавливаться после деструктивной миграции",
            0L,
            customCount
        )
    }

    /** Считает строки `exercises` с заданным значением `is_custom`. */
    private fun countExercisesByCustomFlag(isCustom: Boolean): Long =
        database
            .openHelper
            .readableDatabase
            .query(
                "SELECT count(*) FROM exercises WHERE is_custom = " +
                    if (isCustom) "1" else "0"
            )
            .use { cursor ->
                assertTrue("Ожидается строка результата count(*)", cursor.moveToFirst())
                cursor.getLong(0)
            }
}
