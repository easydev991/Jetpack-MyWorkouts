package com.myworkouts.ui.viewmodel

import android.util.Log
import com.myworkouts.data.repository.LinkedEntriesDeletionException
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.DeleteExerciseError
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExercisesUiState
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Fake [WorkoutDataRepository] на `MutableStateFlow` для JVM-тестов
 * [ExercisesViewModel] (образец — `MainViewModelFakeRepository`):
 * реактивный каталог — единственное, что ViewModel читает.
 *
 * Имя `ExercisesViewModelFakeRepository` — чтобы не конфликтовать с
 * одноимёнными fake-классами других тестов (test source set — одна
 * единица компиляции).
 */
private class ExercisesViewModelFakeRepository(
    initialCatalog: List<Exercise> = emptyList()
) : WorkoutDataRepository {
    val catalog = MutableStateFlow(initialCatalog)

    /**
     * Управляемый из тестов набор id упражнений с записями (для
     * pre-check в [ExercisesViewModel.requestDelete], tasks §2).
     * Публичное поле — тесты §2.1/§2.4 устанавливают значение через
     * `linkedEntries.value = setOf(...)`.
     */
    val linkedEntries: MutableStateFlow<Set<Long>> = MutableStateFlow(emptySet())

    /**
     * Сценарий `deleteExercise` для тестов §2.2/§2.3. По умолчанию —
     * успех; для теста ошибки FK (`LinkedEntriesDeletionException`)
     * тест устанавливает `deleteExerciseResult`.
     */
    var deleteExerciseResult: Result<Unit> = Result.success(Unit)

    var lastDeletedExerciseId: Long? = null

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override fun entries(): Flow<List<com.myworkouts.domain.model.EntryListItem>> = MutableStateFlow(emptyList())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteExercise(id: Long): Result<Unit> {
        lastDeletedExerciseId = id
        return deleteExerciseResult
    }

    override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = linkedEntries

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)
}

/**
 * Unit-тесты для [ExercisesViewModel] (exercises-tab, task 1.1).
 *
 * Подписка на `uiState` — через `backgroundScope` (запускает
 * `WhileSubscribed`); значения читаются синхронно через `.value`
 * после `advanceUntilIdle` (образец — `MainViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExercisesViewModelTest {
    private lateinit var testDispatcher: TestDispatcher

    private val pullups =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = null,
            createdAt = 1_721_000_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val plank =
        Exercise(
            id = 2L,
            name = "Планка",
            category = ExerciseCategory.CORE,
            isCustom = false,
            comment = "тело — прямая линия",
            createdAt = 1_721_100_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private lateinit var repository: ExercisesViewModelFakeRepository

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        // ExercisesViewModel.confirmDelete логирует через android.util.Log в
        // failure-ветках: на JVM android.util.Log — stub, роняет
        // короутину операции (аналогично AppDataViewModelTest).
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
        repository = ExercisesViewModelFakeRepository(initialCatalog = listOf(pullups, plank))
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ExercisesViewModel = ExercisesViewModel(repository)

    @Test
    fun state_initial_before_subscription_then_loading() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()

            // When — без подписки initialValue stateIn остаётся Loading

            // Then
            assertTrue(
                viewModel.uiState.value is ExercisesUiState.Loading,
                "До первой эмиссии репозитория состояние — Loading"
            )
        }

    @Test
    fun state_when_subscribed_then_success_with_exercises() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()

            // When — подписка активирует WhileSubscribed, каталог доезжает
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is ExercisesUiState.Success, "После эмиссии — Success")
            assertEquals(
                listOf(pullups, plank),
                (state as ExercisesUiState.Success).exercises,
                "Каталог приходит из реактивного источника"
            )
        }

    @Test
    fun state_when_catalog_changes_then_success_updated() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — в каталоге добавили упражнение (Flow эмитит новый список)
            val squats =
                Exercise(
                    id = 3L,
                    name = "Приседания",
                    category = ExerciseCategory.LEGS,
                    isCustom = false,
                    comment = null,
                    createdAt = 1_721_200_000_000L
                )
            repository.catalog.value += squats
            advanceUntilIdle()

            // Then — обновление без перезапуска ViewModel
            val state = viewModel.uiState.value
            assertTrue(state is ExercisesUiState.Success, "После новой эмиссии — Success")
            assertEquals(
                listOf(pullups, plank, squats),
                (state as ExercisesUiState.Success).exercises,
                "Список реактивно обновился"
            )

            // When — удалили упражнение
            repository.catalog.value = repository.catalog.value.filterNot { it.id == pullups.id }
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(plank, squats),
                (viewModel.uiState.value as ExercisesUiState.Success).exercises,
                "Удаление упражнения тоже доезжает реактивно"
            )
        }

    // ---------- §2. TDD: pendingDeleteId + deleteError ----------

    /**
     * `requestDelete` для упражнения БЕЗ связанных записей открывает
     * алерт: `pendingDeleteId == item.id` в `Success`. `cancelDelete`
     * сбрасывает обратно в `null`. Pre-check (design Decision 8) даёт
     * снекбар `HasLinkedEntries` только когда id в
     * `exerciseIdsWithLinkedEntries` — здесь пусто.
     *
     * (edit-delete-exercise, tasks §2.1.)
     */
    @Test
    fun requestDelete_whenNoLinkedEntries_setsPendingDeleteId_and_cancelDeleteResetsIt() =
        runTest(testDispatcher) {
            // Given — пустой `linkedEntries` (setUp)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.requestDelete(pullups)
            advanceUntilIdle()

            // Then — pendingDeleteId установлен, deleteError не тронут
            val afterRequest = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                pullups.id,
                afterRequest.pendingDeleteId,
                "requestDelete упражнения без записей — pendingDeleteId = id"
            )
            assertNull(afterRequest.deleteError, "Ошибки нет")

            // When — отмена
            viewModel.cancelDelete()
            advanceUntilIdle()

            // Then — pending сброшен
            val afterCancel = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(afterCancel.pendingDeleteId, "cancelDelete сбрасывает pendingDeleteId")
        }

    /**
     * `confirmDelete()` вызывает `repository.deleteExercise(id)`;
     * при `Result.success` pending сбрасывается, `deleteError == null`
     * (one-shot не сработал).
     *
     * (edit-delete-exercise, tasks §2.2.)
     */
    @Test
    fun confirmDelete_onSuccess_clearsPending_andNoError() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestDelete(pullups)
            advanceUntilIdle()
            assertEquals(pullups.id, (viewModel.uiState.value as ExercisesUiState.Success).pendingDeleteId)

            // When — подтверждение, по умолчанию `deleteExercise` возвращает success
            viewModel.confirmDelete()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(state.pendingDeleteId, "pending сброшен после успешного удаления")
            assertNull(state.deleteError, "deleteError == null при success")
            assertEquals(
                pullups.id,
                repository.lastDeletedExerciseId,
                "confirmDelete вызвал deleteExercise(id)"
            )
        }

    /**
     * `confirmDelete()` при `Result.failure(LinkedEntriesDeletionException)`:
     * pending сброшен, `deleteError == HasLinkedEntries`; повторный
     * `requestDelete` возможен (т.е. requestDelete не блокируется
     * ошибкой).
     *
     * (edit-delete-exercise, tasks §2.3.)
     */
    @Test
    fun confirmDelete_onLinkedEntriesFailure_setsHasLinkedEntriesError_andAllowsRetry() =
        runTest(testDispatcher) {
            // Given
            repository.deleteExerciseResult =
                Result.failure(LinkedEntriesDeletionException())
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestDelete(pullups)
            advanceUntilIdle()

            // When — подтверждение
            viewModel.confirmDelete()
            advanceUntilIdle()

            // Then
            val afterConfirm = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(afterConfirm.pendingDeleteId, "pending сброшен и при ошибке FK")
            assertEquals(
                DeleteExerciseError.HasLinkedEntries,
                afterConfirm.deleteError,
                "FK → DeleteExerciseError.HasLinkedEntries"
            )

            // When — повторный requestDelete после ошибки (т.е. ошибка не
            // «блокирует» дальнейшие действия; design Decision 3 — pending
            // не остаётся открытым на ошибке)
            viewModel.requestDelete(plank)
            advanceUntilIdle()

            // Then — повторный requestDelete ставит pendingDeleteId;
            // deleteError остаётся (one-shot — отдельный onErrorShown)
            val retryState = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                plank.id,
                retryState.pendingDeleteId,
                "Повторный requestDelete открывает новый pending"
            )
            assertEquals(
                DeleteExerciseError.HasLinkedEntries,
                retryState.deleteError,
                "deleteError не сбрасывается повторным requestDelete " +
                    "(one-shot через onErrorShown)"
            )

            // When — onErrorShown() сбрасывает deleteError
            viewModel.onErrorShown()
            advanceUntilIdle()

            // Then
            val afterShown = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(
                afterShown.deleteError,
                "onErrorShown() сбрасывает deleteError"
            )
        }
}
