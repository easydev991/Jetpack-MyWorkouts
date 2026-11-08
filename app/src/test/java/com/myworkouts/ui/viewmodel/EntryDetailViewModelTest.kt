package com.myworkouts.ui.viewmodel

import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.domain.crash.FakeCrashReporter
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.ui.state.EntryDetailUiState
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Fake [WorkoutDataRepository] для JVM-тестов [EntryDetailViewModel]
 * (задача 2.1): запись — hot-поток `MutableStateFlow<EntryListItem?>`,
 * реактивность симулируется сменой значения потока (реальный источник —
 * Room, покрыт `RoomWorkoutDataRepositoryTest`, задача 1.2).
 * Имя `EntryDetailFakeRepository` — чтобы не конфликтовать с
 * `private`-фейками других тестовых файлов (test source set компилируется
 * одной единицей).
 */
private class EntryDetailFakeRepository(
    initialEntry: EntryListItem?
) : WorkoutDataRepository {
    /** Hot-поток записи: `null` — запись отсутствует (контракт `entryById`). */
    val entryFlow = MutableStateFlow(initialEntry)

    /** Идентификаторы, переданные в [deleteEntry], в порядке вызова. */
    val deletedIds: MutableList<Long> = mutableListOf()

    /** Сценарий провала для теста ошибки; по умолчанию успех. */
    var deleteEntryResult: Result<Unit> = Result.success(Unit)

    override fun entryById(id: Long): Flow<EntryListItem?> = entryFlow

    override suspend fun deleteEntry(id: Long): Result<Unit> {
        deletedIds += id
        if (deleteEntryResult.isFailure) return deleteEntryResult
        entryFlow.value = null
        return Result.success(Unit)
    }

    override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

    override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(emptyList())

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        categories: List<Category>,
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)

    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> = Result.success(Unit)

    override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override fun hasArchived(): Flow<Boolean> = MutableStateFlow(false)

    override suspend fun addCategory(
        name: String,
        colorArgb: Int
    ): Result<Category> = Result.success(Category(id = 0L, name = name.trim(), colorArgb = colorArgb, isCustom = true))

    override suspend fun updateCategory(
        id: Long,
        newName: String,
        colorArgb: Int
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteCategory(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun setCategoryArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> = Result.success(Unit)

    override fun categories(): Flow<List<Category>> = MutableStateFlow(emptyList())

    override fun archivedCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())

    override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = MutableStateFlow(emptySet())

    override suspend fun getAllCategories(): Result<List<Category>> = Result.success(emptyList())
}

/**
 * Unit-тесты [EntryDetailViewModel] (workout-entry-detail-screen, задачи
 * 2.1/2.2). Подписка на `uiState` — через `backgroundScope` (запускает
 * `WhileSubscribed`); значения читаются синхронно через `.value` после
 * `advanceUntilIdle` (образец — [MainViewModelTest]).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryDetailViewModelTest {
    private lateinit var testDispatcher: TestDispatcher

    private val item =
        EntryListItem(
            id = 7L,
            exerciseName = "Подтягивания",
            iconNumber = 3,
            sets = 4,
            reps = 10,
            durationSec = null,
            weightKg = 12.5,
            comment = "Комментарий к записи",
            date = 1_721_000_000_000L
        )

    private lateinit var repository: EntryDetailFakeRepository
    private val fakeAnalytics = FakeAnalyticsProvider()
    private val fakeCrash = FakeCrashReporter()

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        repository = EntryDetailFakeRepository(initialEntry = item)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(entryId: Long = 7L): EntryDetailViewModel =
        EntryDetailViewModel(repository, fakeAnalytics, fakeCrash, entryId)

    @Test
    fun state_initial_before_subscription_then_loading() =
        runTest(testDispatcher) {
            // Given: VM создан, подписки нет
            val viewModel = createViewModel()

            // Then: до первой эмиссии — Loading (initialValue stateIn)
            assertEquals(EntryDetailUiState.Loading, viewModel.uiState.value)
        }

    @Test
    fun state_existing_id_then_success_with_entry_data() =
        runTest(testDispatcher) {
            // Given: запись с id 7 существует в потоке репозитория
            val viewModel = createViewModel(entryId = 7L)

            // When: подписка активна, поток эмитит значение
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then: Success с данными записи
            assertEquals(EntryDetailUiState.Success(item), viewModel.uiState.value)
        }

    @Test
    fun state_missing_id_then_error_not_eternal_loading() =
        runTest(testDispatcher) {
            // Given: поток эмитит null (несуществующий id)
            repository.entryFlow.value = null
            val viewModel = createViewModel(entryId = 7L)

            // When: подписка активна
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then: Error — не вечный Loading
            assertEquals(EntryDetailUiState.Error, viewModel.uiState.value)
        }

    @Test
    fun state_entry_updated_in_flow_then_success_with_new_data() =
        runTest(testDispatcher) {
            // Given: VM подписан на существующую запись
            val viewModel = createViewModel(entryId = 7L)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When: запись изменилась в потоке (реактивное обновление)
            val updated = item.copy(sets = 5, comment = "Обновлено")
            repository.entryFlow.value = updated
            advanceUntilIdle()

            // Then: Success с новыми данными
            assertEquals(EntryDetailUiState.Success(updated), viewModel.uiState.value)
        }

    @Test
    fun request_delete_sets_pending_and_cancel_delete_clears_it() =
        runTest(testDispatcher) {
            // Given: VM на существующей записи
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When: запрос удаления
            viewModel.requestDelete()

            // Then: pending выставлен
            advanceUntilIdle()
            assertTrue(viewModel.pendingDelete.value)

            // When: отмена
            viewModel.cancelDelete()
            advanceUntilIdle()

            // Then: pending снят
            assertFalse(viewModel.pendingDelete.value)
        }

    @Test
    fun confirm_delete_then_deletes_entry_logs_action_and_calls_on_deleted() =
        runTest(testDispatcher) {
            // Given: VM с открытым алертом удаления
            val viewModel = createViewModel()
            viewModel.requestDelete()
            var onDeletedCalled = false

            // When: подтверждение удаления
            viewModel.confirmDelete(onDeleted = { onDeletedCalled = true })
            advanceUntilIdle()

            // Then: репозиторий вызван с id записи, событие DELETE залогировано,
            // onDeleted вызван, pending снят
            assertEquals(listOf(7L), repository.deletedIds)
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.DELETE)),
                fakeAnalytics.events
            )
            assertTrue(onDeletedCalled)
            assertFalse(viewModel.pendingDelete.value)
        }

    @Test
    fun confirm_delete_on_repository_failure_then_logs_exception_and_not_calls_on_deleted() =
        runTest(testDispatcher) {
            // Given: репозиторий отвечает провалом
            val failure = Result.failure<Unit>(IllegalStateException("Ошибка БД"))
            repository.deleteEntryResult = failure
            val viewModel = createViewModel()
            viewModel.requestDelete()
            var onDeletedCalled = false

            // When: подтверждение удаления
            viewModel.confirmDelete(onDeleted = { onDeletedCalled = true })
            advanceUntilIdle()

            // Then: сбой ушёл в телеметрию, onDeleted НЕ вызван, pending открыт
            assertEquals(1, fakeCrash.logged.size)
            assertEquals("Удаление записи", fakeCrash.logged.single().message)
            assertEquals(failure.exceptionOrNull(), fakeCrash.logged.single().throwable)
            assertFalse(onDeletedCalled)
            assertTrue(viewModel.pendingDelete.value)
        }
}
