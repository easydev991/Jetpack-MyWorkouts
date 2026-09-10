package com.myworkouts.ui.viewmodel

import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExercisesUiState
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

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override fun entries(): Flow<List<com.myworkouts.domain.model.EntryListItem>> = MutableStateFlow(emptyList())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

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
        repository = ExercisesViewModelFakeRepository(initialCatalog = listOf(pullups, plank))
    }

    @AfterEach
    fun tearDown() {
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
}
