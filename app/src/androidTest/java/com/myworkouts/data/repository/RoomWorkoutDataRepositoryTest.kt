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
import com.myworkouts.domain.model.ExerciseCategory
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
                            category = ExerciseCategory.PULL
                        ),
                        StandardExercise(
                            name = "Планка",
                            category = ExerciseCategory.CORE,
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
                        category = ExerciseCategory.PUSH,
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
    fun addExercise_withoutExplicitMeasuredBy_savesAndReturnsReps() =
        runBlocking {
            // Given — пользовательское упражнение без явного типа измерения
            // (spec-сценарий «Тип измерения по умолчанию при создании»)
            val exercise =
                Exercise(
                    id = 0L,
                    name = "Стойка на руках",
                    category = ExerciseCategory.PUSH,
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
                    category = ExerciseCategory.PUSH,
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
                            category = ExerciseCategory.PULL,
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
                            category = ExerciseCategory.PULL,
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
            assertEquals(ExerciseCategory.PULL, exercise.category)
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
                            category = ExerciseCategory.CORE,
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
}
