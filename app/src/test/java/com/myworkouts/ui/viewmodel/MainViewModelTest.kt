package com.myworkouts.ui.viewmodel

import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.ui.state.MainUiState
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Fake [WorkoutDataRepository] на `MutableStateFlow` для JVM-тестов
 * [MainViewModel]. Каталог и записи реактивные; `deleteEntry` удаляет
 * запись из локального Flow и фиксирует вызов — этого достаточно
 * для проверки реактивного обновления списка на уровне ViewModel.
 * Спека workout-data «Список актуализируется при изменениях»
 * покрывается инструментированным `ExerciseEntryDaoTest` на реальном
 * Room — здесь реактивность симулирует сам fake.
 *
 * Имя `MainViewModelFakeRepository` — чтобы не конфликтовать с
 * одноимённым `private` классом в [EntryCreateViewModelTest]
 * (Kotlin компилирует весь test source set как одну единицу —
 * `private` на top-level изолирует только в пределах модуля).
 */
private class MainViewModelFakeRepository(
    initialEntries: List<EntryListItem> = emptyList()
) : WorkoutDataRepository {
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())
    private val entriesFlow = MutableStateFlow(initialEntries)

    /** Идентификаторы, переданные в [deleteEntry], в порядке вызова. */
    val deletedIds: MutableList<Long> = mutableListOf()

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override fun entries(): Flow<List<EntryListItem>> = entriesFlow

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> {
        deletedIds += id
        entriesFlow.value = entriesFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

    // Новые методы контракта (app-data-screen, задача 1.4): реализованы на
    // состоянии Flow только для компиляции существующих тестов — MainViewModel
    // их не вызывает; запись-Entity фейк не хранит, снимок записей пуст.

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> {
        entriesFlow.value = emptyList()
        catalog.value = emptyList()
        return Result.success(Unit)
    }
}

/**
 * Unit-тесты для [MainViewModel] (workout-entries-list, task 3.1/3.2).
 *
 * Подписка на `uiState` — через `backgroundScope` (запускает
 * `WhileSubscribed`); значения читаются синхронно через `.value`
 * после `advanceUntilIdle` (образец — `EntryCreateViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private lateinit var testDispatcher: TestDispatcher

    private val item1 =
        EntryListItem(
            id = 1L,
            exerciseName = "Подтягивания",
            sets = 4,
            reps = 10,
            durationSec = null,
            date = 1_721_000_000_000L
        )

    private val item2 =
        EntryListItem(
            id = 2L,
            exerciseName = "Планка",
            sets = 1,
            reps = null,
            durationSec = 20,
            date = 1_721_100_000_000L
        )

    private lateinit var repository: MainViewModelFakeRepository

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        repository = MainViewModelFakeRepository(initialEntries = listOf(item1, item2))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MainViewModel = MainViewModel(repository)

    @Test
    fun state_initial_before_subscription_then_loading() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()

            // When — без подписки initialValue stateIn остаётся Loading

            // Then
            assertTrue(
                viewModel.uiState.value is MainUiState.Loading,
                "До первой эмиссии репозитория состояние — Loading"
            )
        }

    @Test
    fun state_when_subscribed_then_success_with_entries() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()

            // When — подписка активирует WhileSubscribed, список доезжает
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Success, "После эмиссии — Success")
            val success = state as MainUiState.Success
            assertEquals(
                listOf(item1, item2),
                success.entries,
                "Записи приходят из реактивного источника"
            )
            assertFalse(success.entries.isEmpty(), "Непустой список")
            assertNull(
                success.pendingDeleteId,
                "Без действия пользователя pendingDeleteId = null"
            )
        }

    @Test
    fun success_when_entries_empty_then_entries_is_empty() =
        runTest(testDispatcher) {
            // Given — пустой репозиторий
            repository = MainViewModelFakeRepository(initialEntries = emptyList())
            val viewModel = createViewModel()

            // When
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Success, "Даже пустой список — Success")
            val success = state as MainUiState.Success
            assertTrue(success.entries.isEmpty(), "Пустой список → entries.isEmpty()")
        }

    @Test
    fun request_delete_then_pending_delete_id_set() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.requestDelete(item1)
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Success, "requestDelete не меняет sealed-тип")
            val success = state as MainUiState.Success
            assertEquals(
                item1.id,
                success.pendingDeleteId,
                "pendingDeleteId = id переданной записи"
            )
        }

    @Test
    fun cancel_delete_clears_pending_without_calling_delete() =
        runTest(testDispatcher) {
            // Given — пользователь открыл алерт удаления
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestDelete(item1)
            advanceUntilIdle()

            // When
            viewModel.cancelDelete()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Success, "cancelDelete не меняет sealed-тип")
            val success = state as MainUiState.Success
            assertNull(
                success.pendingDeleteId,
                "pendingDeleteId очищен после cancelDelete"
            )
            assertTrue(
                repository.deletedIds.isEmpty(),
                "cancelDelete не вызывает deleteEntry"
            )
        }

    @Test
    fun confirm_delete_calls_delete_entry_and_clears_pending() =
        runTest(testDispatcher) {
            // Given — пользователь открыл алерт удаления
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestDelete(item1)
            advanceUntilIdle()

            // When
            viewModel.confirmDelete()
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(item1.id),
                repository.deletedIds,
                "deleteEntry вызван с id запрошенной записи"
            )
            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Success, "После удаления — Success")
            val success = state as MainUiState.Success
            assertNull(
                success.pendingDeleteId,
                "pendingDeleteId очищен после успешного удаления"
            )
            assertEquals(
                listOf(item2),
                success.entries,
                "Список реактивно обновился: удалённой записи нет"
            )
        }
}
