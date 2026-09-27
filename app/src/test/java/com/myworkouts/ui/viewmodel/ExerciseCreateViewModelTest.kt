package com.myworkouts.ui.viewmodel

import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Fake [WorkoutDataRepository] для JVM-тестов [ExerciseCreateViewModel]:
 * `addExercise`/`updateExercise` записывают переданное упражнение и
 * возвращают настраиваемый результат, `getExercise` отвечает настраиваемым
 * упражнением (`null` — «не найдено»), остальные операции — заглушки.
 */
private class FakeExerciseRepository : WorkoutDataRepository {
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())

    /** Упражнения, переданные в `addExercise`, в порядке вызовов. */
    val addedExercises = mutableListOf<Exercise>()

    /** Упражнения, переданные в `updateExercise`, в порядке вызовов. */
    val updatedExercises = mutableListOf<Exercise>()

    /** Настраиваемый результат `addExercise`. */
    var addExerciseResult: Result<Long> = Result.success(1L)

    /** Настраиваемый результат `updateExercise`. */
    var updateExerciseResult: Result<Unit> = Result.success(Unit)

    /** Ответ `getExercise`; `null` — упражнение не найдено (design Decision 5). */
    var exerciseById: Exercise? = null

    /** Настраиваемый провал `getExercise` (`null` — успех с [exerciseById]). */
    var getExerciseError: Throwable? = null

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override suspend fun addExercise(exercise: Exercise): Result<Long> {
        addedExercises.add(exercise)
        return addExerciseResult
    }

    override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow<List<EntryListItem>>(emptyList())

    override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> {
        updatedExercises.add(exercise)
        return updateExerciseResult
    }

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun getExercise(id: Long): Result<Exercise?> {
        getExerciseError?.let { return Result.failure(it) }
        return Result.success(exerciseById)
    }

    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    // Новый метод контракта (entry-prefilled-reps-and-duration, задача 1.4):
    // заглушка — VM этого экрана историю упражнений не читает.
    override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)

    // Новые методы контракта (exercises-archive, §2.7): заглушки —
    // ExerciseCreateViewModel их не вызывает.

    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> = Result.success(Unit)

    override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override fun archivedCount(): Flow<Int> = MutableStateFlow(0)
}

/**
 * Unit-тесты для [ExerciseCreateViewModel].
 *
 * Подписка на `uiState` — через `backgroundScope` (запускает
 * WhileSubscribed), значения читаются синхронно через `.value` после
 * `advanceUntilIdle` (образец — `EntryCreateViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseCreateViewModelTest {
    private lateinit var testDispatcher: TestDispatcher

    private val repository = FakeExerciseRepository()

    private val fakeAnalytics = FakeAnalyticsProvider()

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(exerciseId: Long = Screen.ExerciseCreate.NO_EXERCISE_ID): ExerciseCreateViewModel =
        ExerciseCreateViewModel(repository, exerciseId, fakeAnalytics)

    /**
     * `save` в режиме создания эмитит `UserAction(CREATE)` — на вызове
     * операции (design D10).
     */
    @Test
    fun save_in_create_mode_logs_user_action_create() =
        runTest(testDispatcher) {
            // Given — заполненная форма в режиме создания
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.CREATE)),
                fakeAnalytics.events,
                "save в режиме создания логирует UserAction(CREATE)"
            )
        }

    /**
     * `save` в режиме редактирования эмитит `UserAction(EDIT)` — различение
     * создания и правки по наличию `exerciseId` (design D10).
     */
    @Test
    fun save_in_edit_mode_logs_user_action_edit() =
        runTest(testDispatcher) {
            // Given — edit-режим, изменено имя
            repository.exerciseById = editExercise
            val viewModel = createViewModel(exerciseId = editExercise.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onNameChange("Тяга")
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.EDIT)),
                fakeAnalytics.events,
                "save в режиме редактирования логирует UserAction(EDIT)"
            )
        }

    /** Кастомное упражнение, отдаваемое fake-репозиторием в edit-тестах. */
    private val editExercise =
        Exercise(
            id = 5L,
            name = "Жим лёжа",
            category = ExerciseCategory.PUSH,
            isCustom = true,
            comment = "узкий хват",
            createdAt = 1_234_567_890L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    @Test
    fun initial_state_when_created_then_measured_by_is_reps() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When
            advanceUntilIdle()

            // Then
            assertEquals(
                ExerciseMeasuredBy.REPS,
                viewModel.uiState.value.measuredBy,
                "По умолчанию measuredBy = REPS без вызовов onMeasuredByChange"
            )
        }

    @Test
    fun can_save_when_form_is_empty_then_false() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.canSave,
                "Пустая форма — canSave = false"
            )
        }

    @Test
    fun can_save_when_only_name_then_false() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When — имя введено, категория не выбрана
            viewModel.onNameChange("Жим")
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.canSave,
                "Имя без категории — canSave = false"
            )
        }

    @Test
    fun can_save_when_name_and_category_then_true() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.canSave,
                "Имя и категория заполнены — canSave = true"
            )
        }

    @Test
    fun on_measured_by_change_when_form_is_empty_then_time_selected_and_cannot_save() =
        runTest(testDispatcher) {
            // Given — только что созданный VM, все поля пустые
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onMeasuredByChange(ExerciseMeasuredBy.TIME)
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertEquals(
                ExerciseMeasuredBy.TIME,
                state.measuredBy,
                "measuredBy переключён на TIME"
            )
            assertFalse(
                state.canSave,
                "measuredBy не влияет на canSave: name/category по-прежнему пусты"
            )
        }

    @Test
    fun on_measured_by_change_after_name_and_category_then_time_selected_and_can_save() =
        runTest(testDispatcher) {
            // Given — заполненная форма
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canSave, "Пре-условие: canSave = true")

            // When — переключение REPS → TIME
            viewModel.onMeasuredByChange(ExerciseMeasuredBy.TIME)
            advanceUntilIdle()

            // Then — переключение не меняет значение canSave
            val state = viewModel.uiState.value
            assertEquals(ExerciseMeasuredBy.TIME, state.measuredBy, "measuredBy переключён на TIME")
            assertTrue(state.canSave, "Переключение measuredBy НЕ меняет canSave")
        }

    @Test
    fun on_comment_change_then_stores_raw_value() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — ввод с пробелами по краям
            viewModel.onCommentChange("  узкий хват  ")
            advanceUntilIdle()

            // Then — сырое значение, без trim на входе (trim при сохранении)
            assertEquals(
                "  узкий хват  ",
                viewModel.uiState.value.comment,
                "comment хранит сырой ввод без trim"
            )
        }

    /* hasUnsavedChanges (spec «BackHandler + hasUnsavedChanges —
     * подтверждение закрытия»): формула — name/comment с отсечением blank
     * или выбранная категория; measuredBy не входит. */

    @Test
    fun has_unsaved_changes_when_form_is_fresh_then_false() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.hasUnsavedChanges,
                "Только что созданный VM (все поля null) — hasUnsavedChanges = false"
            )
        }

    @Test
    fun has_unsaved_changes_when_name_changed_then_true() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When
            viewModel.onNameChange("Жим")
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.hasUnsavedChanges,
                "Введённое имя — hasUnsavedChanges = true"
            )
        }

    @Test
    fun has_unsaved_changes_when_only_category_selected_then_true() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When — категория без имени/комментария
            viewModel.onCategoryChange(ExerciseCategory.PULL)
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.hasUnsavedChanges,
                "Выбранная категория — hasUnsavedChanges = true"
            )
        }

    @Test
    fun has_unsaved_changes_when_only_comment_then_true() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When — комментарий без имени/категории
            viewModel.onCommentChange("коммент")
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.hasUnsavedChanges,
                "Введённый комментарий — hasUnsavedChanges = true"
            )
        }

    @Test
    fun has_unsaved_changes_when_only_measured_by_changed_then_false() =
        runTest(testDispatcher) {
            // Given — только что созданный VM
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — измерение переключено при пустых name/category/comment
            viewModel.onMeasuredByChange(ExerciseMeasuredBy.TIME)
            advanceUntilIdle()

            // Then — измерение не считается изменением формы
            assertFalse(
                viewModel.uiState.value.hasUnsavedChanges,
                "Изменение measuredBy при пустых name/category/comment — hasUnsavedChanges = false"
            )
        }

    @Test
    fun has_unsaved_changes_when_name_cleared_then_false() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.hasUnsavedChanges, "Пре-условие: имя введено")

            // When — имя стёрто до пустой строки
            viewModel.onNameChange("")
            advanceUntilIdle()

            // Then — пустое имя после trim() не считается изменением
            assertFalse(
                viewModel.uiState.value.hasUnsavedChanges,
                "Пустое имя после trim() — hasUnsavedChanges = false"
            )
        }

    // save().

    @Test
    fun save_when_form_is_empty_then_no_repository_call_and_no_on_saved() =
        runTest(testDispatcher) {
            // Given — пустая форма
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            var onSavedCalled = false

            // When
            viewModel.save { onSavedCalled = true }
            advanceUntilIdle()

            // Then
            assertTrue(
                repository.addedExercises.isEmpty(),
                "addExercise НЕ вызывается при пустой форме"
            )
            assertFalse(onSavedCalled, "onSaved НЕ вызывается при пустой форме")
        }

    @Test
    fun save_when_form_is_filled_then_adds_custom_exercise_once() =
        runTest(testDispatcher) {
            // Given — заполненная форма
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then — addExercise вызван ровно один раз с Exercise(isCustom = true)
            assertEquals(1, repository.addedExercises.size, "addExercise вызван ровно один раз")
            val exercise = repository.addedExercises.single()
            assertEquals(0L, exercise.id, "id = 0 для нового упражнения")
            assertEquals("Жим", exercise.name, "name из формы")
            assertEquals(ExerciseCategory.PUSH, exercise.category, "category из формы")
            assertTrue(exercise.isCustom, "isCustom = true — пользовательское упражнение")
            assertNull(exercise.comment, "comment = null — поле не заполнялось")
            assertEquals(
                ExerciseMeasuredBy.REPS,
                exercise.measuredBy,
                "measuredBy = REPS — дефолт формы"
            )
            val nowMillis = System.currentTimeMillis()
            assertTrue(
                abs(nowMillis - exercise.createdAt) <= 5_000L,
                "createdAt ≈ System.currentTimeMillis() (±5s)"
            )
        }

    @Test
    fun save_when_name_has_surrounding_spaces_then_trimmed() =
        runTest(testDispatcher) {
            // Given — имя с пробелами по краям
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("  Жим  ")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then — нормализация trim() при сохранении
            assertEquals(
                "Жим",
                repository.addedExercises.single().name,
                "name тримится при сохранении"
            )
        }

    @Test
    fun save_when_comment_has_surrounding_spaces_then_trimmed() =
        runTest(testDispatcher) {
            // Given — комментарий с пробелами по краям
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            viewModel.onCommentChange("  узкий хват  ")
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then — нормализация trim() при сохранении
            assertEquals(
                "узкий хват",
                repository.addedExercises.single().comment,
                "comment тримится при сохранении"
            )
        }

    @Test
    fun save_when_comment_is_blank_then_saves_null() =
        runTest(testDispatcher) {
            // Given — комментарий только из пробелов
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            viewModel.onCommentChange("   ")
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then — trim() + takeIf { isNotEmpty() } → null
            assertNull(
                repository.addedExercises.single().comment,
                "blank-комментарий сохраняется как null"
            )
        }

    @Test
    fun save_when_repository_fails_then_error_flag_and_no_on_saved() =
        runTest(testDispatcher) {
            // Given — репозиторий падает, форма заполнена
            repository.addExerciseResult = Result.failure(IllegalStateException("БД недоступна"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()
            var onSavedCalled = false

            // When
            viewModel.save { onSavedCalled = true }
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.isSaveError, "isSaveError = true после провала")
            assertTrue(
                viewModel.uiState.value.hasUnsavedChanges,
                "hasUnsavedChanges остаётся true — данные не потеряны"
            )
            assertFalse(onSavedCalled, "onSaved НЕ вызывается при провале")
        }

    @Test
    fun input_callback_after_failure_then_resets_save_error() =
        runTest(testDispatcher) {
            // Таблица колбэков ввода: каждый сбрасывает isSaveError
            // (паттерн EntryCreateViewModel.onXChange)
            val actions: List<Pair<String, (ExerciseCreateViewModel) -> Unit>> =
                listOf(
                    "onNameChange" to { it.onNameChange("Тяга") },
                    "onCategoryChange" to { it.onCategoryChange(ExerciseCategory.LEGS) },
                    "onMeasuredByChange" to { it.onMeasuredByChange(ExerciseMeasuredBy.TIME) },
                    "onCommentChange" to { it.onCommentChange("коммент") }
                )
            repository.addExerciseResult = Result.failure(IllegalStateException("БД недоступна"))

            actions.forEach { (actionName, action) ->
                // Given — неуспешное сохранение заполненной формы
                val viewModel = createViewModel()
                backgroundScope.launch { viewModel.uiState.collect { } }
                viewModel.onNameChange("Жим")
                viewModel.onCategoryChange(ExerciseCategory.PUSH)
                advanceUntilIdle()
                viewModel.save { }
                advanceUntilIdle()
                assertTrue(
                    viewModel.uiState.value.isSaveError,
                    "Пре-условие: ошибка после провала ($actionName)"
                )

                // When — колбэк ввода
                action(viewModel)
                advanceUntilIdle()

                // Then
                assertFalse(
                    viewModel.uiState.value.isSaveError,
                    "«$actionName» сбрасывает isSaveError"
                )
            }
        }

    @Test
    fun save_when_repository_succeeds_then_on_saved_called_once_without_error() =
        runTest(testDispatcher) {
            // Given — заполненная форма, репозиторий отвечает успехом
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()
            var onSavedCalls = 0

            // When
            viewModel.save { onSavedCalls++ }
            advanceUntilIdle()

            // Then
            assertEquals(1, onSavedCalls, "onSaved вызван ровно раз")
            assertFalse(viewModel.uiState.value.isSaveError, "isSaveError остаётся false")
            assertNotNull(repository.addedExercises.single(), "addExercise вызван")
        }

    /* ===== edit-режим (edit-delete-exercise, раздел 7): загрузка,
     * hasChanges, save через updateExercise, отсутствующее упражнение. ===== */

    @Test
    fun edit_mode_when_opened_then_loads_exercise_and_prefills_state() =
        runTest(testDispatcher) {
            // Given — репозиторий отдаёт упражнение по id
            repository.exerciseById = editExercise
            val viewModel = createViewModel(exerciseId = editExercise.id)
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When
            advanceUntilIdle()

            // Then — UiState предзаполнен из БД, режим редактирования
            val state = viewModel.uiState.value
            assertTrue(state.isEditMode, "Экран открыт с exerciseId — isEditMode = true")
            assertEquals(editExercise.name, state.name, "name предзаполнен из БД")
            assertEquals(editExercise.category, state.category, "category предзаполнена из БД")
            assertEquals(editExercise.measuredBy, state.measuredBy, "measuredBy предзаполнен из БД")
            assertEquals(editExercise.comment, state.comment, "comment предзаполнен из БД")
            assertFalse(state.isExerciseMissing, "Упражнение найдено — isExerciseMissing = false")
        }

    @Test
    fun edit_mode_when_form_untouched_then_has_changes_false() =
        runTest(testDispatcher) {
            // Given — edit-режим с загруженным упражнением
            repository.exerciseById = editExercise
            val viewModel = createViewModel(exerciseId = editExercise.id)
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When — форма не тронута
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.hasChanges,
                "Предзаполненные поля сами по себе — hasChanges = false"
            )
        }

    @Test
    fun edit_mode_when_name_changed_then_has_changes_true() =
        runTest(testDispatcher) {
            // Given — edit-режим с загруженным упражнением
            repository.exerciseById = editExercise
            val viewModel = createViewModel(exerciseId = editExercise.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onNameChange("Тяга")
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.hasChanges,
                "Изменение name относительно оригинала — hasChanges = true"
            )
        }

    @Test
    fun edit_mode_when_name_changed_back_to_original_then_has_changes_false() =
        runTest(testDispatcher) {
            // Given — edit-режим, имя изменено
            repository.exerciseById = editExercise
            val viewModel = createViewModel(exerciseId = editExercise.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onNameChange("Тяга")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.hasChanges, "Пре-условие: имя изменено")

            // When — возврат к исходному значению
            viewModel.onNameChange(editExercise.name)
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.hasChanges,
                "Возврат к исходному значению — hasChanges = false"
            )
        }

    @Test
    fun edit_mode_when_only_measured_by_changed_then_has_changes_true() =
        runTest(testDispatcher) {
            // Given — edit-режим с загруженным упражнением (measuredBy = TIME)
            repository.exerciseById = editExercise
            val viewModel = createViewModel(exerciseId = editExercise.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — изменён только measuredBy
            viewModel.onMeasuredByChange(ExerciseMeasuredBy.REPS)
            advanceUntilIdle()

            // Then — measuredBy входит в формулу hasChanges (в отличие от
            // create-формулы hasUnsavedChanges)
            assertTrue(
                viewModel.uiState.value.hasChanges,
                "Изменение только measuredBy — hasChanges = true"
            )
        }

    @Test
    fun save_in_edit_mode_then_updates_with_original_id_created_at_and_is_custom() =
        runTest(testDispatcher) {
            // Given — edit-режим, изменено имя
            repository.exerciseById = editExercise
            val viewModel = createViewModel(exerciseId = editExercise.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onNameChange("Тяга")
            advanceUntilIdle()
            var onSavedCalls = 0

            // When
            viewModel.save { onSavedCalls++ }
            advanceUntilIdle()

            // Then — updateExercise с неизменяемыми полями оригинала
            assertTrue(
                repository.addedExercises.isEmpty(),
                "addExercise НЕ вызывается в edit-режиме"
            )
            val updated = repository.updatedExercises.single()
            assertEquals(editExercise.id, updated.id, "id из оригинала")
            assertEquals(editExercise.createdAt, updated.createdAt, "createdAt из оригинала")
            assertTrue(updated.isCustom, "isCustom из оригинала (design Decision 2)")
            assertEquals("Тяга", updated.name, "name из формы")
            assertEquals(
                editExercise.measuredBy,
                updated.measuredBy,
                "measuredBy из формы (не изменялся)"
            )
            assertEquals(1, onSavedCalls, "onSaved вызван ровно раз после успеха")
        }

    @Test
    fun save_in_create_mode_then_adds_and_does_not_update() =
        runTest(testDispatcher) {
            // Given — режим создания с заполненной формой
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then — регрессия: create идёт в addExercise, updateExercise не трогается
            assertEquals(1, repository.addedExercises.size, "addExercise вызван ровно раз")
            assertTrue(
                repository.updatedExercises.isEmpty(),
                "updateExercise НЕ вызывается в режиме создания"
            )
        }

    @Test
    fun edit_mode_when_exercise_missing_then_hint_and_save_is_no_op() =
        runTest(testDispatcher) {
            // Given — getExercise → success(null)
            val viewModel = createViewModel(exerciseId = 404L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.isExerciseMissing,
                "success(null) — isExerciseMissing = true"
            )

            // When — форма заполнена, нажато «Сохранить»
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()
            var onSavedCalls = 0
            viewModel.save { onSavedCalls++ }
            advanceUntilIdle()

            // Then — canSave заблокирован guard-ом, save no-op
            assertFalse(
                viewModel.uiState.value.canSave,
                "isExerciseMissing — canSave = false независимо от полей формы"
            )
            assertTrue(repository.addedExercises.isEmpty(), "addExercise НЕ вызван")
            assertTrue(repository.updatedExercises.isEmpty(), "updateExercise НЕ вызван")
            assertEquals(0, onSavedCalls, "onSaved НЕ вызван")
        }

    @Test
    fun edit_mode_when_get_exercise_fails_then_hint_and_save_is_no_op() =
        runTest(testDispatcher) {
            // Given — getExercise → failure
            repository.getExerciseError = IllegalStateException("БД недоступна")
            val viewModel = createViewModel(exerciseId = editExercise.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            assertTrue(
                viewModel.uiState.value.isExerciseMissing,
                "failure — isExerciseMissing = true (ошибка загрузки приравнена к отсутствию)"
            )

            // When — форма заполнена, нажато «Сохранить»
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()
            var onSavedCalls = 0
            viewModel.save { onSavedCalls++ }
            advanceUntilIdle()

            // Then — save no-op
            assertFalse(viewModel.uiState.value.canSave, "canSave = false при isExerciseMissing")
            assertTrue(repository.addedExercises.isEmpty(), "addExercise НЕ вызван")
            assertTrue(repository.updatedExercises.isEmpty(), "updateExercise НЕ вызван")
            assertEquals(0, onSavedCalls, "onSaved НЕ вызван")
        }

    /* iconNumber: обработчик обновляет state и сбрасывает isSaveError,
     * «Без иконки» (null) — допустимое значение, edit-предзаполнение,
     * смена иконки → hasChanges = true, save пишет iconNumber в
     * addExercise/updateExercise. */

    /** Кастомное упражнение с иконкой — для icon-тестов edit-режима. */
    private val exerciseWithIcon =
        Exercise(
            id = 7L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = true,
            comment = "широкий хват",
            createdAt = 1_700_000_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS,
            iconNumber = 5
        )

    @Test
    fun on_icon_number_change_updates_state_and_resets_save_error_after_failure() =
        runTest(testDispatcher) {
            // Given — неуспешный save с заполненной формой
            repository.addExerciseResult = Result.failure(IllegalStateException("БД недоступна"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            advanceUntilIdle()
            viewModel.save { }
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isSaveError, "Пре-условие: isSaveError = true после провала")

            // When — выбор иконки
            viewModel.onIconNumberChange(7)
            advanceUntilIdle()

            // Then
            assertEquals(
                7,
                viewModel.uiState.value.iconNumber,
                "onIconNumberChange(7) — iconNumber = 7"
            )
            assertFalse(
                viewModel.uiState.value.isSaveError,
                "onIconNumberChange сбрасывает isSaveError (паттерн onXChange)"
            )
        }

    @Test
    fun on_icon_number_change_to_null_resets_icon_number() =
        runTest(testDispatcher) {
            // Given — выбрана иконка
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onIconNumberChange(7)
            advanceUntilIdle()
            assertEquals(7, viewModel.uiState.value.iconNumber, "Пре-условие: иконка 7")

            // When — повторный выбор «Без иконки»
            viewModel.onIconNumberChange(null)
            advanceUntilIdle()

            // Then — «Без иконки» — допустимое значение null
            assertNull(
                viewModel.uiState.value.iconNumber,
                "Повторный выбор «Без иконки» — iconNumber = null"
            )
        }

    @Test
    fun edit_mode_prefill_loads_icon_number_into_state() =
        runTest(testDispatcher) {
            // Given — упражнение с iconNumber = 5 в репозитории
            repository.exerciseById = exerciseWithIcon
            val viewModel = createViewModel(exerciseId = exerciseWithIcon.id)
            backgroundScope.launch { viewModel.uiState.collect { } }

            // When
            advanceUntilIdle()

            // Then — предзаполнен iconNumber из БД
            assertEquals(
                exerciseWithIcon.iconNumber,
                viewModel.uiState.value.iconNumber,
                "iconNumber предзаполнен из БД"
            )
        }

    @Test
    fun edit_mode_when_icon_changed_then_has_changes_true() =
        runTest(testDispatcher) {
            // Given — edit-режим с упражнением (iconNumber = 5)
            repository.exerciseById = exerciseWithIcon
            val viewModel = createViewModel(exerciseId = exerciseWithIcon.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.hasChanges, "Пре-условие: форма не тронута")

            // When — смена иконки 5 → 9
            viewModel.onIconNumberChange(9)
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.hasChanges,
                "Смена иконки в edit-режиме — hasChanges = true (data-сравнение)"
            )
        }

    @Test
    fun edit_mode_when_icon_reverted_to_original_then_has_changes_false() =
        runTest(testDispatcher) {
            // Given — edit-режим, иконка изменена 5 → 9
            repository.exerciseById = exerciseWithIcon
            val viewModel = createViewModel(exerciseId = exerciseWithIcon.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onIconNumberChange(9)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.hasChanges, "Пре-условие: иконка изменена")

            // When — возврат к исходной иконке 5
            viewModel.onIconNumberChange(exerciseWithIcon.iconNumber)
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.hasChanges,
                "Возврат к исходной иконке — hasChanges = false"
            )
        }

    @Test
    fun save_in_create_mode_passes_selected_icon_number_to_add_exercise() =
        runTest(testDispatcher) {
            // Given — заполненная create-форма с выбранной иконкой 7
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            viewModel.onNameChange("Жим")
            viewModel.onCategoryChange(ExerciseCategory.PUSH)
            viewModel.onIconNumberChange(7)
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then — addExercise получил Exercise с iconNumber = 7
            val saved = repository.addedExercises.single()
            assertEquals(7, saved.iconNumber, "iconNumber = 7 передан в addExercise")
        }

    @Test
    fun save_in_edit_mode_passes_new_icon_number_with_original_id_to_update_exercise() =
        runTest(testDispatcher) {
            // Given — edit-режим, оригинал iconNumber = 5, выбрана новая 9
            repository.exerciseById = exerciseWithIcon
            val viewModel = createViewModel(exerciseId = exerciseWithIcon.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onIconNumberChange(9)
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then — updateExercise с id оригинала и новой iconNumber = 9
            val updated = repository.updatedExercises.single()
            assertEquals(exerciseWithIcon.id, updated.id, "id из оригинала")
            assertEquals(9, updated.iconNumber, "iconNumber = 9 (новая выбранная)")
        }
}
