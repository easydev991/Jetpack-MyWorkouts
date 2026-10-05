package com.myworkouts.data.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.myworkouts.data.db.ExerciseEntity
import com.myworkouts.data.db.StandardExercise
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.repository.LinkedEntriesDeletionException
import com.myworkouts.data.repository.StandardExerciseDeletionException
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

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
 * - «Удаление записи о выполнении» — `deleteEntry` удаляет запись,
 *   повторный вызов с тем же id — успех (идемпотентность).
 * - «Удаление упражнения из каталога» — пользовательское упражнение
 *   без записей удаляется; стандартное и упражнение с записями
 *   неудаляемы (`Result.isFailure`); несуществующий id упражнения —
 *   ошибка.
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
                database = database,
                standardExercises =
                    listOf(
                        StandardExercise(
                            name = "Подтягивания",
                            category = "PULL"
                        ),
                        StandardExercise(
                            name = "Планка",
                            category = "CORE",
                            measuredBy = ExerciseMeasuredBy.TIME
                        )
                    )
            )
        // Подложим два упражнения — REPS (Подтягивания) и TIME (Планка) —
        // чтобы были валидные `exerciseId` обоих типов измерения.
        runBlocking {
            database.exerciseDao().insert(
                ExerciseEntity(
                    name = "Подтягивания",
                    category = "PULL",
                    isCustom = false,
                    createdAt = 0L
                )
            )
            database.exerciseDao().insert(
                ExerciseEntity(
                    name = "Планка",
                    category = "CORE",
                    isCustom = false,
                    createdAt = 0L,
                    measuredBy = "TIME"
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    /**
     * Живая invalidation каталога: активный коллектор [RoomWorkoutDataRepository.exercises]
     * получает вторую эмиссию после `addExercise` — без пере-подписки.
     * Репро баги «каталог упражнений заморожен до перезапуска приложения»
     * (create-exercise, ручная проверка этапа 5).
     */
    @Test
    fun exercisesFlow_emitsAfterInsert_whileSubscribed() {
        runBlocking {
            // Given — активная подписка на каталог, initial-эмиссия получена
            val emissions = CopyOnWriteArrayList<List<Exercise>>()
            val job =
                launch(Dispatchers.Unconfined) {
                    repository.exercises("").collect { emissions.add(it) }
                }
            try {
                withTimeout(5_000L) {
                    while (emissions.isEmpty()) delay(50L)
                }

                // When — вставка при живой подписке
                repository.addExercise(
                    Exercise(
                        id = 0L,
                        name = "Жим",
                        category = "PUSH",
                        isCustom = true,
                        comment = null,
                        createdAt = 1L,
                        measuredBy = ExerciseMeasuredBy.REPS
                    )
                )

                // Then — вторая эмиссия содержит новое упражнение
                withTimeout(5_000L) {
                    while (emissions.none { list -> list.any { it.name == "Жим" } }) {
                        delay(50L)
                    }
                }
            } finally {
                job.cancel()
            }
        }
    }

    /**
     * Живая invalidation записей (контроль к репро каталога): активный
     * коллектор [RoomWorkoutDataRepository.entries] получает вторую эмиссию
     * после `addEntry` — главный экран обновляется живьём этим же механизмом.
     */
    @Test
    fun entriesFlow_emitsAfterInsert_whileSubscribed() {
        runBlocking {
            // Given — активная подписка на записи, initial-эмиссия получена
            val emissions = CopyOnWriteArrayList<List<EntryListItem>>()
            val job =
                launch(Dispatchers.Unconfined) {
                    repository.entries().collect { emissions.add(it) }
                }
            try {
                withTimeout(5_000L) {
                    while (emissions.isEmpty()) delay(50L)
                }

                // When — вставка при живой подписке (валидный exerciseId из setUp)
                repository.addEntry(
                    ExerciseEntry(
                        id = 0L,
                        exerciseId = 1L,
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
                )

                // Then — вторая эмиссия содержит новую запись
                withTimeout(5_000L) {
                    while (emissions.none { list -> list.isNotEmpty() }) {
                        delay(50L)
                    }
                }
            } finally {
                job.cancel()
            }
        }
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
    fun addEntry_forRepsMeasuredExercise_withOnlyDuration_returnsFailure() =
        runBlocking {
            // Given — упражнение «Подтягивания» (id = 1, measuredBy = REPS),
            // запись только с длительностью, без повторов — слой данных
            // теперь требует повторы для REPS (workout-entries-list,
            // design Decision 7, spec «Слой данных навязывает соответствие»)
            val entry =
                ExerciseEntry(
                    id = 0L,
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

            // When
            val result = repository.addEntry(entry)

            // Then — отказ; в БД ничего не записано
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is EntryValidationException)
            assertEquals(null, database.exerciseEntryDao().getById(1L))
        }

    @Test
    fun addEntry_forRepsMeasuredExercise_withoutReps_returnsFailure() =
        runBlocking {
            // Given — REPS-упражнение (Подтягивания), повторы не заданы
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 1L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 3,
                    reps = null,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val result = repository.addEntry(entry)

            // Then — отказ с MISSING_REPS, в БД пусто
            assertTrue(result.isFailure)
            assertEquals(
                EntryValidationError.MISSING_REPS,
                (result.exceptionOrNull() as? EntryValidationException)?.error
            )
            assertEquals(null, database.exerciseEntryDao().getById(1L))
        }

    @Test
    fun addEntry_forTimeMeasuredExercise_withoutDuration_returnsFailure() =
        runBlocking {
            // Given — упражнение «Планка» (id = 2, measuredBy = TIME),
            // длительность не задана
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 2L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 1,
                    reps = null,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val result = repository.addEntry(entry)

            // Then — отказ с MISSING_DURATION, в БД пусто
            assertTrue(result.isFailure)
            assertEquals(
                EntryValidationError.MISSING_DURATION,
                (result.exceptionOrNull() as? EntryValidationException)?.error
            )
            assertEquals(null, database.exerciseEntryDao().getById(1L))
        }

    @Test
    fun addEntry_forTimeMeasuredExercise_withZeroDuration_returnsFailure() =
        runBlocking {
            // Given
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 2L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 1,
                    reps = null,
                    weightKg = null,
                    durationSec = 0,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val result = repository.addEntry(entry)

            // Then
            assertTrue(result.isFailure)
            assertEquals(
                EntryValidationError.INVALID_DURATION,
                (result.exceptionOrNull() as? EntryValidationException)?.error
            )
        }

    @Test
    fun addEntry_forTimeMeasuredExercise_withRepsAndDuration_returnsFailure() =
        runBlocking {
            // Given — одновременно повторы и длительность
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 2L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 1,
                    reps = 10,
                    weightKg = null,
                    durationSec = 60,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val result = repository.addEntry(entry)

            // Then — отказ с INCOMPATIBLE_REPS_AND_DURATION
            assertTrue(result.isFailure)
            assertEquals(
                EntryValidationError.INCOMPATIBLE_REPS_AND_DURATION,
                (result.exceptionOrNull() as? EntryValidationException)?.error
            )
        }

    @Test
    fun addEntry_identicalEntries_areSavedAsSeparateRows() =
        runBlocking {
            // Given — две записи с одинаковыми упражнениями и параметрами
            val template =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = 1L,
                    workoutId = null,
                    date = 1_700_000_000_000L,
                    sets = 3,
                    reps = 8,
                    weightKg = null,
                    durationSec = null,
                    comment = null,
                    createdAt = 1L,
                    modifiedAt = 1L
                )

            // When
            val firstId = repository.addEntry(template).getOrThrow()
            val secondId = repository.addEntry(template).getOrThrow()

            // Then — обе сохранены отдельными строками со своими id
            assertTrue("id должны различаться", secondId > firstId)
            assertNotNull(database.exerciseEntryDao().getById(firstId))
            assertNotNull(database.exerciseEntryDao().getById(secondId))
            // И обе видны в реактивном списке
            assertEquals(2, repository.entries().first().size)
        }

    @Test
    fun updateEntry_forTimeMeasuredExercise_changingToMissingDuration_returnsFailure() =
        runBlocking {
            // Given — вставим TIME-запись, потом обновим, убрав длительность
            val insertedId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = 2L,
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
                    ).getOrThrow()

            // And — TIME-запись читается через репозиторий: reps = null,
            // длительность = 60 (spec «Сохранение планки»)
            val saved = repository.getEntry(insertedId).getOrThrow()
            assertEquals(null, saved?.reps)
            assertEquals(60, saved?.durationSec)

            // When — обновляем с durationSec = null
            val result =
                repository.updateEntry(
                    ExerciseEntry(
                        id = insertedId,
                        exerciseId = 2L,
                        workoutId = null,
                        date = 1_700_000_000_000L,
                        sets = 1,
                        reps = null,
                        weightKg = null,
                        durationSec = null,
                        comment = null,
                        createdAt = 1L,
                        modifiedAt = 1L
                    )
                )

            // Then — отказ, длительность не обнулена
            assertTrue(result.isFailure)
            assertEquals(
                EntryValidationError.MISSING_DURATION,
                (result.exceptionOrNull() as? EntryValidationException)?.error
            )
            val stillThere = database.exerciseEntryDao().getById(insertedId)
            assertNotNull(stillThere)
            assertEquals(60, stillThere?.durationSec)
        }

    @Test
    fun addEntry_withNonExistingExerciseId_returnsFailure_withExerciseNotFoundMessage() =
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

            // Then — отказ с IllegalStateException (workout-entries-list,
            // design Decision 7: «Упражнение не найдено»)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertEquals(null, database.exerciseEntryDao().getById(1L))
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
                    category = "PULL",
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
            assertEquals("PULL", fetched.category)
        }

    @Test
    fun addExercise_withoutExplicitMeasuredBy_savesAndReturnsReps() =
        runBlocking {
            // Given — пользовательское упражнение без явного типа измерения
            // (spec-сценарий «Тип измерения по умолчанию при создании»)
            val exercise =
                Exercise(
                    id = 0L,
                    name = "Стойка на руках",
                    category = "PUSH",
                    isCustom = true,
                    comment = null,
                    createdAt = 1_700_000_000_000L
                )

            // When
            val id =
                repository
                    .addExercise(exercise)
                    .getOrThrow()

            // Then — дефолт REPS сохранён и читается обратно
            val fetched =
                repository
                    .exercises("")
                    .first()
                    .single { it.id == id }
            assertEquals(ExerciseMeasuredBy.REPS, fetched.measuredBy)
        }

    @Test
    fun addExercise_withMeasuredByTime_readBackAsTime() =
        runBlocking {
            // Given — упражнение «на время»
            // (spec-сценарий «Создание упражнения «на время»»)
            val exercise =
                Exercise(
                    id = 0L,
                    name = "Стойка на руках",
                    category = "PUSH",
                    isCustom = true,
                    comment = null,
                    createdAt = 1_700_000_000_000L,
                    measuredBy = ExerciseMeasuredBy.TIME
                )

            // When
            val id =
                repository
                    .addExercise(exercise)
                    .getOrThrow()

            // Then
            val fetched =
                repository
                    .exercises("")
                    .first()
                    .single { it.id == id }
            assertEquals(ExerciseMeasuredBy.TIME, fetched.measuredBy)
        }

    /**
     * Номер иконки доезжает через репозиторий без искажений, отсутствие
     * иконки и её наличие различимы (exercise-icon-mvp, spec-сценарии
     * «Сохранение и чтение номера иконки» / «Упражнение без иконки» /
     * «Отсутствие иконки и её наличие различимы»).
     */
    @Test
    fun addExercise_iconNumber_isPersistedAndDistinguishableFromNull() =
        runBlocking {
            // Given — три упражнения: с иконкой 5, без иконки и с иконкой 0
            val withIconId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = "С иконкой",
                            category = "PUSH",
                            isCustom = true,
                            comment = null,
                            createdAt = 1L,
                            iconNumber = 5
                        )
                    ).getOrThrow()
            val withoutIconId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = "Без иконки",
                            category = "PUSH",
                            isCustom = true,
                            comment = null,
                            createdAt = 2L
                        )
                    ).getOrThrow()
            val zeroIconId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = "Нулевая иконка",
                            category = "PUSH",
                            isCustom = true,
                            comment = null,
                            createdAt = 3L,
                            iconNumber = 0
                        )
                    ).getOrThrow()

            // When
            val catalog = repository.exercises("").first()

            // Then — 5 и 0 сохранены как есть, отсутствие иконки — null
            assertEquals(5, catalog.single { it.id == withIconId }.iconNumber)
            assertEquals(null, catalog.single { it.id == withoutIconId }.iconNumber)
            assertEquals(0, catalog.single { it.id == zeroIconId }.iconNumber)
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
                        category = "PULL",
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
                        category = "PULL",
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

    @Test
    fun deleteEntry_withExistingId_succeeds_andRepeatedCallIsIdempotent() =
        runBlocking {
            // Given — запись, добавленная через репозиторий
            val entryId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = 1L,
                            workoutId = null,
                            date = 1_700_000_000_000L,
                            sets = 3,
                            reps = 8,
                            weightKg = null,
                            durationSec = null,
                            comment = null,
                            createdAt = 1L,
                            modifiedAt = 1L
                        )
                    ).getOrThrow()

            // When
            val result = repository.deleteEntry(entryId)

            // Then — запись удалена
            assertTrue(result.isSuccess)
            assertEquals(null, database.exerciseEntryDao().getById(entryId))

            // Then — повторное удаление того же id — успех
            // (spec-сценарий «Удаление несуществующей записи»)
            assertTrue(
                "Повторное deleteEntry должно быть успехом (идемпотентность)",
                repository.deleteEntry(entryId).isSuccess
            )
        }

    @Test
    fun deleteExercise_withCustomExerciseWithoutEntries_succeeds_andMissingIdFails() =
        runBlocking {
            // Given — пользовательское упражнение без записей
            val customId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = "Отжимания на турнике",
                            category = "PULL",
                            isCustom = true,
                            comment = null,
                            createdAt = 1_700_000_000_000L
                        )
                    ).getOrThrow()

            // When
            val result = repository.deleteExercise(customId)

            // Then — упражнение удалено из каталога
            assertTrue(result.isSuccess)
            assertEquals(null, database.exerciseDao().getById(customId))

            // Then — несуществующий id → ошибка
            // (spec-сценарий «Удаление несуществующего упражнения отклоняется»)
            assertTrue(
                "Удаление несуществующего упражнения должно завершаться ошибкой",
                repository.deleteExercise(9_999L).isFailure
            )
        }

    @Test
    fun deleteExercise_withStandardExercise_returnsFailure_andExerciseRemains() =
        runBlocking {
            // Given — стандартное упражнение из setUp («Подтягивания»)
            val standardId =
                database
                    .exerciseDao()
                    .observeExercises("")
                    .first()
                    .single { it.name == "Подтягивания" }
                    .id

            // When
            val result = repository.deleteExercise(standardId)

            // Then — отказ типизированным исключением, упражнение остаётся
            assertTrue(
                "Удаление стандартного упражнения должно быть отклонено",
                result.isFailure
            )
            assertTrue(
                "Ожидается StandardExerciseDeletionException для маппинга в sealed-тип",
                result.exceptionOrNull() is StandardExerciseDeletionException
            )
            assertEquals(standardId, database.exerciseDao().getById(standardId)?.id)
        }

    @Test
    fun deleteExercise_withCustomExerciseHavingEntries_returnsFailure_andNothingIsDeleted() =
        runBlocking {
            // Given — пользовательское упражнение с одной записью
            val customId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = "Становая тяга",
                            category = "PULL",
                            isCustom = true,
                            comment = null,
                            createdAt = 1_700_000_000_000L
                        )
                    ).getOrThrow()
            val entryId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = customId,
                            workoutId = null,
                            date = 1_700_000_000_000L,
                            sets = 5,
                            reps = 5,
                            weightKg = 100.0,
                            durationSec = null,
                            comment = null,
                            createdAt = 1L,
                            modifiedAt = 1L
                        )
                    ).getOrThrow()

            // When
            val result = repository.deleteExercise(customId)

            // Then — отказ типизированным FK-исключением; упражнение и записи
            assertTrue(
                "FK-нарушение должно превращаться в Result.failure",
                result.isFailure
            )
            assertTrue(
                "Ожидается LinkedEntriesDeletionException для маппинга в sealed-тип",
                result.exceptionOrNull() is LinkedEntriesDeletionException
            )
            assertEquals(customId, database.exerciseDao().getById(customId)?.id)
            assertEquals(entryId, database.exerciseEntryDao().getById(entryId)?.id)
        }

    /**
     * `getExercise` существующего id возвращает доменную модель с теми же
     * полями, что в БД (edit-delete-exercise, tasks §1.1; prefill
     * `ExerciseCreateScreen` в edit-режиме).
     */
    @Test
    fun getExercise_withExistingId_returnsDomainExercise() =
        runBlocking<Unit> {
            // Given — стандартное упражнение «Подтягивания» из setUp (id = 1)
            val expectedId = 1L

            // When
            val result = repository.getExercise(expectedId)

            // Then — success с полной доменной моделью
            assertTrue(result.isSuccess)
            val exercise = result.getOrThrow()
            assertNotNull(exercise)
            assertEquals(expectedId, exercise!!.id)
            assertEquals("Подтягивания", exercise.name)
            assertEquals("PULL", exercise.category)
            assertFalse("Стандартное упражнение из setUp — isCustom=false", exercise.isCustom)
            assertEquals(ExerciseMeasuredBy.REPS, exercise.measuredBy)
        }

    /**
     * `getExercise` для несуществующего id возвращает
     * `Result.success(null)` (не failure): VM различает «ошибка слоя
     * данных» и «запись отсутствует при открытии edit-режима»
     * (edit-delete-exercise, tasks §1.2; design Decision 3 ветка
     * «не найдено»).
     */
    @Test
    fun getExercise_withNonExistingId_returnsSuccessNull() =
        runBlocking<Unit> {
            // When
            val result = repository.getExercise(9_999L)

            // Then — success(null), не failure
            assertTrue("Ожидается success, не failure", result.isSuccess)
            assertNull(result.getOrThrow())
        }

    @Test
    fun entries_whenEmpty_emitsEmptyList() =
        runBlocking {
            // Given — в БД только упражнение из setUp, ни одной записи

            // When
            val items = repository.entries().first()

            // Then
            assertTrue(
                "Пустой репозиторий должен эммитить пустой список",
                items.isEmpty()
            )
        }

    /**
     * Иконка упражнения читается JOIN-ом и попадает в элемент списка
     * записей (exercise-icon-display, spec-сценарий «Номер иконки в
     * элементе списка»). Отличать от
     * [addExercise_iconNumber_isPersistedAndDistinguishableFromNull]:
     * тот проверяет round-trip через каталог, этот — проекцию `entries()`.
     */
    @Test
    fun entries_exerciseIconNumber_reachesListItem() =
        runBlocking {
            // Given — упражнение с иконкой 13 и запись по нему; запись
            // по «Подтягивания» из setUp (иконка не назначена)
            val withIconId =
                database.exerciseDao().insert(
                    ExerciseEntity(
                        name = "Отжимания",
                        category = "PUSH",
                        isCustom = false,
                        createdAt = 0L,
                        iconNumber = 13
                    )
                )
            val withIconEntryId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = withIconId,
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
                    ).getOrThrow()
            val nullIconEntryId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = 1L,
                            workoutId = null,
                            date = 1_700_000_100_000L,
                            sets = 3,
                            reps = 8,
                            weightKg = null,
                            durationSec = null,
                            comment = null,
                            createdAt = 2L,
                            modifiedAt = 2L
                        )
                    ).getOrThrow()

            // When
            val items = repository.entries().first()

            // Then — иконка дошла через JOIN, отсутствие иконки — null
            assertEquals(13, items.single { it.id == withIconEntryId }.iconNumber)
            assertEquals(null, items.single { it.id == nullIconEntryId }.iconNumber)
        }

    @Test
    fun entries_preserveWeightKg_nullAndValue() =
        runBlocking {
            // Given — запись с весом 5.0 и запись без веса
            // (spec «Дополнительный вес в строке записи»: вес доезжает
            // от БД до списка главного экрана без потерь)
            val withWeightId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = 1L,
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
                    ).getOrThrow()
            val withoutWeightId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = 1L,
                            workoutId = null,
                            date = 1_700_000_100_000L,
                            sets = 3,
                            reps = 8,
                            weightKg = null,
                            durationSec = null,
                            comment = null,
                            createdAt = 2L,
                            modifiedAt = 2L
                        )
                    ).getOrThrow()

            // When
            val items = repository.entries().first()

            // Then — вес сохранён в проекции списка: 5.0 и null
            val withWeight = items.single { it.id == withWeightId }
            val withoutWeight = items.single { it.id == withoutWeightId }
            assertEquals(5.0, withWeight.weightKg)
            assertEquals(null, withoutWeight.weightKg)
        }

    @Test
    fun entries_returnsDomainEntryListItems_withExerciseNameAndDateDesc() =
        runBlocking {
            // Given — две записи: одна для REPS-упражнения из setUp,
            // вторая — для TIME-упражнения, добавленного здесь.
            // Разные даты — проверяем сортировку по убыванию.
            val timeExerciseId =
                repository
                    .addExercise(
                        Exercise(
                            id = 0L,
                            name = "Планка",
                            category = "CORE",
                            isCustom = true,
                            comment = null,
                            createdAt = 1_700_000_000_000L,
                            measuredBy = ExerciseMeasuredBy.TIME
                        )
                    ).getOrThrow()
            val olderEntryId =
                repository
                    .addEntry(
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
                            createdAt = 1L,
                            modifiedAt = 1L
                        )
                    ).getOrThrow()
            val newerEntryId =
                repository
                    .addEntry(
                        ExerciseEntry(
                            id = 0L,
                            exerciseId = timeExerciseId,
                            workoutId = null,
                            date = 1_700_000_100_000L,
                            sets = 1,
                            reps = null,
                            weightKg = null,
                            durationSec = 60,
                            comment = null,
                            createdAt = 2L,
                            modifiedAt = 2L
                        )
                    ).getOrThrow()

            // When
            val items = repository.entries().first()

            // Then — обе записи, проекция доменная, новые первыми, названия из JOIN
            assertEquals(2, items.size)
            assertEquals(newerEntryId, items[0].id)
            assertEquals(olderEntryId, items[1].id)
            assertEquals("Планка", items[0].exerciseName)
            assertEquals("Подтягивания", items[1].exerciseName)
            assertEquals(1, items[0].sets)
            assertNotNull(items[0].durationSec)
            assertEquals(60, items[0].durationSec)
            assertEquals(null, items[0].reps)
            assertEquals(4, items[1].sets)
            assertEquals(10, items[1].reps)
            assertEquals(null, items[1].durationSec)
        }

    // Архивация (exercises-archive, tasks §2): setArchived, archivedExercises,
    // hasArchived, фильтрация архивных в exercises().

    /**
     * `setArchived(id, true)` для активного упражнения → `Result.success`,
     * упражнение пропадает из реактивного каталога и появляется в архиве
     * (spec-сценарий «Архивация активного упражнения»). Упражнение —
     * стандартное: архивация разрешена независимо от `isCustom` (spec
     * «Архивация стандартного упражнения»).
     */
    @Test
    fun setArchived_withActiveExercise_succeeds_andRemovesFromCatalog() =
        runBlocking {
            // Given — стандартное упражнение из setUp
            val pullupsId =
                database
                    .exerciseDao()
                    .getAll()
                    .single { it.name == "Подтягивания" }
                    .id

            // When
            val result = repository.setArchived(pullupsId, true)

            // Then — успех; пропало из каталога, появилось в архиве
            assertTrue(result.isSuccess)
            val catalog = repository.exercises("").first()
            assertNull(
                "Заархивированное упражнение не должно попадать в каталог",
                catalog.firstOrNull {
                    it.id ==
                        pullupsId
                }
            )
            assertEquals(
                pullupsId,
                repository
                    .archivedExercises()
                    .first()
                    .single()
                    .id
            )
        }

    /**
     * `setArchived(id, false)` для архивного упражнения → `Result.success`,
     * упражнение возвращается в каталог с `isArchived = false` и пропадает
     * из архива (spec-сценарий «Восстановление архивного упражнения»).
     */
    @Test
    fun setArchived_withArchivedExercise_succeeds_andRestoresToCatalog() =
        runBlocking {
            // Given — заархивированное стандартное упражнение
            val pullupsId =
                database
                    .exerciseDao()
                    .getAll()
                    .single { it.name == "Подтягивания" }
                    .id
            repository.setArchived(pullupsId, true).getOrThrow()

            // When
            val result = repository.setArchived(pullupsId, false)

            // Then — успех; снова в каталоге, из архива пропало, флаг сброшен
            assertTrue(result.isSuccess)
            assertNotNull(
                "Восстановленное упражнение должно вернуться в каталог",
                repository.exercises("").first().firstOrNull { it.id == pullupsId }
            )
            assertTrue(
                "После восстановления архив должен быть пуст",
                repository.archivedExercises().first().isEmpty()
            )
            assertFalse(
                repository.getExercise(pullupsId).getOrThrow()?.isArchived ?: true
            )
        }

    /**
     * `setArchived` для несуществующего id →
     * `Result.failure(IllegalStateException("Упражнение не найдено"))`,
     * каталог не изменён (spec-сценарий «Архивация несуществующего
     * упражнения отклоняется»; паттерн `deleteExercise`).
     */
    @Test
    fun setArchived_withMissingId_returnsFailure_exerciseNotFound() =
        runBlocking {
            // When
            val result = repository.setArchived(9_999L, true)

            // Then — failure с ожидаемым типом и сообщением
            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(
                "Ожидается IllegalStateException, получено: $exception",
                exception is IllegalStateException
            )
            assertEquals("Упражнение не найдено", exception?.message)

            // And — каталог остался без изменений (2 упражнения из setUp)
            assertEquals(2, repository.exercises("").first().size)
        }

    /**
     * `archivedExercises()` эмитит только упражнения с `is_archived = 1`,
     * отсортированные по контракту `is_custom DESC, created_at DESC,
     * name COLLATE NOCASE`; `hasArchived()` эмитит `false` на пустом
     * архиве и `true` после архивации (spec-сценарии «Архив пуст»,
     * «Реактивный список архивных упражнений», «Кнопка архива в
     * TopAppBar»).
     */
    @Test
    fun archivedExercises_emitsOnlyArchived_sortedByContract_andHasArchivedFlag() =
        runBlocking {
            // Given — пустой архив: признак false, список пуст
            assertFalse(
                "На пустом архиве hasArchived() должен быть false",
                repository.hasArchived().first()
            )
            assertTrue(repository.archivedExercises().first().isEmpty())

            // And — четыре упражнения с разными ключами сортировки:
            // кастомные (created_at DESC): Отжимания(2) → Жим(1);
            // стандартные (created_at DESC, при равенстве name NOCASE):
            // Бег(5) → Ходьба(5).
            suspend fun insert(
                name: String,
                isCustom: Boolean,
                createdAt: Long
            ): Long =
                database.exerciseDao().insert(
                    ExerciseEntity(
                        name = name,
                        category = "PULL",
                        isCustom = isCustom,
                        createdAt = createdAt
                    )
                )
            val ids =
                listOf(
                    insert("Отжимания", isCustom = true, createdAt = 2L),
                    insert("Жим", isCustom = true, createdAt = 1L),
                    insert("Бег", isCustom = false, createdAt = 5L),
                    insert("Ходьба", isCustom = false, createdAt = 5L)
                )
            ids.forEach { id -> repository.setArchived(id, true).getOrThrow() }

            // When
            val archived = repository.archivedExercises().first()

            // Then — только архивные, в порядке контракта сортировки
            assertEquals(listOf("Отжимания", "Жим", "Бег", "Ходьба"), archived.map { it.name })
            assertTrue("Все элементы архива должны быть isArchived=true", archived.all { it.isArchived })
            assertTrue(
                "После архивации хотя бы одного упражнения hasArchived() должен быть true",
                repository.hasArchived().first()
            )
        }

    /**
     * `exercises("")` НЕ возвращает архивные упражнения (фильтрация
     * `WHERE is_archived = 0` в SQL); поиск по подстроке тоже фильтрует
     * архивные (spec «Поиск и сортировка каталога»: архивные не попадают
     * в результат `exercises(query)`).
     */
    @Test
    fun exercises_excludesArchived_emptyQuery_andSubstringSearch() =
        runBlocking {
            // Given — кастомное «Подъёмы ног» заархивировано;
            // «Подтягивания» из setUp активно; оба содержат подстроку «од»
            val archivedId =
                database.exerciseDao().insert(
                    ExerciseEntity(
                        name = "Подъёмы ног",
                        category = "PULL",
                        isCustom = true,
                        createdAt = 3L
                    )
                )
            repository.setArchived(archivedId, true).getOrThrow()

            // When — пустой запрос
            val all = repository.exercises("").first()

            // Then — архивные не попадают, активные на месте
            assertNull(
                "Пустой запрос не должен возвращать архивные",
                all.firstOrNull { it.id == archivedId }
            )
            assertNotNull(all.firstOrNull { it.name == "Подтягивания" })
            assertNotNull(all.firstOrNull { it.name == "Планка" })

            // When — поиск по подстроке, совпадающей с обоими названиями
            val bySubstring = repository.exercises("од").first()

            // Then — архивные отфильтрованы и в поиске
            assertTrue(bySubstring.isNotEmpty())
            assertNull(
                "Поиск по подстроке не должен возвращать архивные",
                bySubstring.firstOrNull { it.id == archivedId }
            )
            assertNotNull(bySubstring.firstOrNull { it.name == "Подтягивания" })
        }

    // Снимок истории упражнения (entry-prefilled-reps-and-duration, задача 1.3).

    /** Шаблон записи для тестов истории: поля переопределяются copy-стилем. */
    private fun historyEntry(
        exerciseId: Long,
        date: Long,
        reps: Int? = 10,
        weightKg: Double? = null,
        durationSec: Int? = null,
        createdAt: Long = 1L
    ): ExerciseEntry =
        ExerciseEntry(
            id = 0L,
            exerciseId = exerciseId,
            workoutId = null,
            date = date,
            sets = 4,
            reps = reps,
            weightKg = weightKg,
            durationSec = durationSec,
            comment = null,
            createdAt = createdAt,
            modifiedAt = createdAt
        )

    /**
     * `exerciseHistory` возвращает доменные записи в порядке снимка DAO —
     * `date DESC` (design D2): «последняя запись» упражнения — первый элемент.
     */
    @Test
    fun exerciseHistory_withEntries_thenReturnsDomainEntriesNewestFirst() =
        runBlocking {
            // Given — три записи «Подтягиваний» (id = 1 из setUp) с разными датами
            val oldest = 1_700_000_000_000L
            val middle = 1_700_000_100_000L
            val newest = 1_700_000_200_000L
            repository.addEntry(historyEntry(exerciseId = 1L, date = oldest))
            repository.addEntry(historyEntry(exerciseId = 1L, date = newest))
            repository.addEntry(historyEntry(exerciseId = 1L, date = middle))

            // When
            val result = repository.exerciseHistory(exerciseId = 1L)

            // Then
            assertTrue(result.isSuccess)
            assertEquals(
                "История доменных записей упорядочена date DESC",
                listOf(newest, middle, oldest),
                result.getOrThrow().map { it.date }
            )
        }

    /** Упражнение без записей — `success(emptyList())`, не failure. */
    @Test
    fun exerciseHistory_whenNoEntries_thenReturnsSuccessEmptyList() =
        runBlocking {
            // Given — «Планка» (id = 2 из setUp), записей нет

            // When
            val result = repository.exerciseHistory(exerciseId = 2L)

            // Then
            assertTrue("Ожидается success, не failure", result.isSuccess)
            assertTrue(
                "Пустая история — пустой список",
                result.getOrThrow().isEmpty()
            )
        }
}
