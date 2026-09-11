package com.myworkouts.ui.viewmodel

import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
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
 * Fake [WorkoutDataRepository] на `MutableStateFlow` для JVM-тестов
 * [ExerciseCreateViewModel]: `addExercise` записывает переданное упражнение
 * и возвращает настраиваемый результат, остальные операции — заглушки.
 */
private class FakeExerciseRepository : WorkoutDataRepository {
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())

    /** Упражнения, переданные в `addExercise`, в порядке вызовов. */
    val addedExercises = mutableListOf<Exercise>()

    /** Настраиваемый результат `addExercise`. */
    var addExerciseResult: Result<Long> = Result.success(1L)

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override suspend fun addExercise(exercise: Exercise): Result<Long> {
        addedExercises.add(exercise)
        return addExerciseResult
    }

    override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow<List<EntryListItem>>(emptyList())

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)
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

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ExerciseCreateViewModel = ExerciseCreateViewModel(repository)

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
}
