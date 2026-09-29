package com.myworkouts.data.db

import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.icons.ExerciseIcons
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
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
 * Исключение — тест отсутствия деструктивного фоллбека: он работает
 * с файловой БД на production-имени [WorkoutDatabase.DATABASE_NAME]
 * (см. KDoc теста), файл удаляется в [tearDown].
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
        // Файловый тест отсутствия фоллбека работает на production-имени —
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
    fun createDatabase_thenStandardExercisesHaveAssignedIconNumbers() =
        runBlocking {
            // Given — БД с prepopulate из setUp (exercise-icon-display,
            // spec-сценарий «Стандартный набор prepopulate с номерами
            // иконок»: назначение по фиксированной таблице design
            // Decision 5; планка переиспользует иконку отжиманий)
            val expectedByName =
                mapOf(
                    "Подтягивания" to ExerciseIcons.PULLUPS,
                    "Австралийские подтягивания" to ExerciseIcons.PULLUPS_AUSTRALIAN,
                    "Приседания" to ExerciseIcons.SQUATS,
                    "Отжимания" to ExerciseIcons.PUSHUPS,
                    "Отжимания на коленях" to ExerciseIcons.PUSHUPS_KNEE,
                    "Выпады" to ExerciseIcons.LUNGES,
                    "Отжимания на брусьях" to ExerciseIcons.BARS,
                    "Планка" to ExerciseIcons.PUSHUPS,
                    "Скручивания" to ExerciseIcons.CRUNCHES,
                    "Подъёмы ног в висе" to ExerciseIcons.ABS_HANGING_LEG_RAISES,
                    "Приседания пистолетиком" to ExerciseIcons.SQUATS_PISTOL,
                    "Бег" to ExerciseIcons.RUN,
                    "Скакалка" to ExerciseIcons.JUMPING_ROPE
                )

            // When
            val exercises = database.exerciseDao().observeExercises("").first()

            // Then — все 13 стандартных имеют ненулевой iconNumber
            // согласно фиксированному соответствию
            assertEquals(13, exercises.size)
            exercises.forEach { exercise ->
                assertEquals(
                    "Иконка «${exercise.name}» не соответствует таблице design Decision 5",
                    expectedByName[exercise.name],
                    exercise.iconNumber
                )
            }
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

    /**
     * Проекция `observeEntriesWithExercise` (overview-screen, tasks 7.1,
     * design Decision 1): запись JOIN-ится с каталогом — в строке есть
     * `exercise_id`, категория и тип измерения упражнения, а также
     * комментарий упражнения (`exerciseComment`) рядом с комментарием
     * самой записи.
     */
    @Test
    fun observeEntriesWithExercise_whenEntryInserted_thenJoinsCategoryAndMeasuredBy() =
        runBlocking {
            // Given — упражнение PULL/REPS с комментарием и запись на него
            val exerciseId =
                database.exerciseDao().insert(
                    ExerciseEntity(
                        name = "Тестовые подтягивания",
                        category = ExerciseCategory.PULL.name,
                        isCustom = true,
                        comment = "широкий хват",
                        createdAt = 1L
                    )
                )
            database.exerciseEntryDao().insert(
                ExerciseEntryEntity(
                    exerciseId = exerciseId,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 4,
                    reps = 10,
                    weightKg = null,
                    durationSec = null,
                    comment = "до сна",
                    createdAt = 1L,
                    modifiedAt = 1L
                )
            )

            // When
            val rows = database.exerciseEntryDao().observeEntryRows().first()

            // Then — проекция содержит JOIN-колонки из каталога
            assertEquals(1, rows.size)
            val row = rows.first()
            assertEquals(exerciseId, row.exerciseId)
            assertEquals("Тестовые подтягивания", row.exerciseName)
            assertEquals(ExerciseCategory.PULL.name, row.category)
            assertEquals(ExerciseMeasuredBy.REPS.name, row.measuredBy)
            assertEquals("широкий хват", row.exerciseComment)

            // Then — поля самой записи на месте
            assertEquals(4, row.sets)
            assertEquals(10, row.reps)
            assertEquals("до сна", row.comment)
        }

    @Test
    fun noDestructiveFallback_whenUserVersionDowngraded_thenThrowsAndKeepsData() {
        val ctx = ruContext()

        // Given — файловая БД на production-имени DATABASE_NAME:
        // WorkoutDatabase.create(...) открывает DATABASE_NAME и не принимает
        // имени в параметрах, изолированное имя тесту недоступно. Гигиена —
        // удаление файла в tearDown. Plain-схема создаётся сразу в v3 (без
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
                "VALUES ('Пользовательское до отката версии', 'PUSH', 1, NULL, 1, 'REPS', NULL)"
        )
        plain.openHelper.writableDatabase.execSQL("PRAGMA user_version = 2")
        plain.close()

        // When — переоткрытие production-путём: schema v3, файл в v2,
        // зарегистрированных миграций нет → Room обязан бросить исключение
        // при первом открытии (build() базу не открывает, инстанс присваиваем
        // полю — tearDown закроет его)
        database =
            WorkoutDatabase.create(
                context = ctx,
                standardExercises = standardExercises(ctx)
            )

        assertThrows(
            "Без миграции открытие БД с чужой версией должно падать исключением",
            IllegalStateException::class.java
        ) {
            database.openHelper.writableDatabase
        }
        // Неудачный инстанс закрываем до raw-проверки файла (повторный close
        // в tearDown безопасен — SQLiteOpenHelper идемпотентен)
        database.close()

        // Then — файл не тронут: версия по-прежнему 2, пользовательская
        // строка на месте, пересоздания не было. Raw-доступ мимо Room,
        // без валидации схемы (spec-сценарий «Изменение схемы без миграции
        // отклоняется»)
        val raw =
            SQLiteDatabase.openDatabase(
                ctx.getDatabasePath(WorkoutDatabase.DATABASE_NAME).absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            )
        raw.use { db ->
            assertEquals(
                "Версия файла БД не должна измениться при неудачном открытии",
                2,
                db.version
            )
            assertEquals(
                "Пользовательские данные должны остаться в файле без изменений",
                1L,
                DatabaseUtils.longForQuery(
                    db,
                    "SELECT count(*) FROM exercises WHERE is_custom = 1",
                    null
                )
            )
        }
    }
}
