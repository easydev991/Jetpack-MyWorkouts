package com.myworkouts.ui.viewmodel

import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ArchivedExercisesUiState
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Fake [WorkoutDataRepository] для JVM-тестов
 * [ArchivedExercisesViewModel]: реактивный `archivedExercises()` — на
 * `MutableStateFlow` (тесты управляют списком), `setArchived` —
 * `Result.success(Unit)` с записью вызова.
 */
private class ArchivedExercisesFakeRepository(
    initialArchived: List<Exercise> = emptyList()
) : WorkoutDataRepository {
    val archived: MutableStateFlow<List<Exercise>> = MutableStateFlow(initialArchived)

    /** Вызовы `setArchived` в порядке обращений. */
    val setArchivedCalls = mutableListOf<Pair<Long, Boolean>>()

    override fun archivedExercises(): Flow<List<Exercise>> = archived

    override fun archivedCount(): Flow<Int> = MutableStateFlow(archived.value.size)

    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> {
        setArchivedCalls.add(id to isArchived)
        return Result.success(Unit)
    }

    override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

    override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(emptyList())

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)
}

/**
 * Unit-тесты для [ArchivedExercisesViewModel] (exercises-archive, tasks §4).
 *
 * Подписка на `uiState` — через `backgroundScope` (запускает
 * `WhileSubscribed`); значения читаются синхронно через `.value` после
 * `advanceUntilIdle` (образец — `ExercisesViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArchivedExercisesViewModelTest {
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
            comment = null,
            createdAt = 1_721_100_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: ArchivedExercisesFakeRepository): ArchivedExercisesViewModel =
        ArchivedExercisesViewModel(repository)

    /**
     * До первой эмиссии `repository.archivedExercises()` (без активной
     * подписки) — `Loading`. Проверка initialValue у `stateIn` с
     * `SharingStarted.WhileSubscribed(5000)` (exercises-archive, tasks §4.1).
     */
    @Test
    fun state_initial_before_subscription_then_loading() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel(ArchivedExercisesFakeRepository())

            // Then — без подписки initialValue stateIn остаётся Loading
            assertTrue(
                viewModel.uiState.value is ArchivedExercisesUiState.Loading,
                "До первой эмиссии репозитория состояние — Loading"
            )
        }

    /**
     * После первой эмиссии `repository.archivedExercises()` —
     * `Success` с этим списком (exercises-archive, tasks §4.1).
     */
    @Test
    fun state_when_subscribed_then_success_with_archived_exercises() =
        runTest(testDispatcher) {
            // Given
            val repository = ArchivedExercisesFakeRepository(initialArchived = listOf(pullups, plank))
            val viewModel = createViewModel(repository)

            // When — подписка активирует WhileSubscribed, список доезжает
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(
                state is ArchivedExercisesUiState.Success,
                "После эмиссии репозитория — Success"
            )
            assertEquals(
                listOf(pullups, plank),
                (state as ArchivedExercisesUiState.Success).exercises,
                "Список архивных приходит из реактивного источника"
            )
        }

    /**
     * Новая эмиссия `repository.archivedExercises()` обновляет `Success`
     * без перезапуска ViewModel (exercises-archive, tasks §4.1).
     */
    @Test
    fun state_when_repository_emits_new_list_then_success_updated_without_restart() =
        runTest(testDispatcher) {
            // Given — стартовый список с одним упражнением
            val repository = ArchivedExercisesFakeRepository(initialArchived = listOf(pullups))
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            assertEquals(
                listOf(pullups),
                (viewModel.uiState.value as ArchivedExercisesUiState.Success).exercises
            )

            // When — репозиторий эмитит расширенный список
            repository.archived.value = listOf(pullups, plank)
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is ArchivedExercisesUiState.Success)
            assertEquals(
                listOf(pullups, plank),
                (state as ArchivedExercisesUiState.Success).exercises,
                "Список реактивно обновился без перезапуска VM"
            )
        }

    /**
     * `unarchive(item)` вызывает `repository.setArchived(item.id, false)`
     * (exercises-archive, tasks §4.2). Параметр `isArchived = false` —
     * направление восстановления (design Decision 3 — один метод для
     * обоих направлений).
     */
    @Test
    fun unarchive_calls_setArchived_with_false() =
        runTest(testDispatcher) {
            // Given
            val repository = ArchivedExercisesFakeRepository(initialArchived = listOf(pullups))
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.unarchive(pullups)
            advanceUntilIdle()

            // Then — репозиторию передан id и направление false
            assertEquals(
                listOf(pullups.id to false),
                repository.setArchivedCalls,
                "unarchive(item) вызвал setArchived(item.id, false)"
            )
        }
}
