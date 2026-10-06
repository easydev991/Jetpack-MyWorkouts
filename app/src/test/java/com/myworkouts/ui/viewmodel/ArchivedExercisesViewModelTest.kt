package com.myworkouts.ui.viewmodel

import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ArchivedExercisesUiState
import com.myworkouts.ui.state.ArchivedTab
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
 * [ArchivedExercisesViewModel]: реактивные `archivedExercises()` /
 * `archivedCategories()` — на `MutableStateFlow` (тесты управляют
 * списками), `setArchived` / `setCategoryArchived` —
 * `Result.success(Unit)` с записью вызова.
 */
private class ArchivedExercisesFakeRepository(
    initialArchived: List<Exercise> = emptyList(),
    initialArchivedCategories: List<Category> = emptyList()
) : WorkoutDataRepository {
    val archived: MutableStateFlow<List<Exercise>> = MutableStateFlow(initialArchived)

    val archivedCategories: MutableStateFlow<List<Category>> =
        MutableStateFlow(initialArchivedCategories)

    /** Вызовы `setArchived` в порядке обращений. */
    val setArchivedCalls = mutableListOf<Pair<Long, Boolean>>()

    /** Вызовы `setCategoryArchived` в порядке обращений. */
    val setCategoryArchivedCalls = mutableListOf<Pair<Long, Boolean>>()

    override fun archivedExercises(): Flow<List<Exercise>> = archived

    override fun hasArchived(): Flow<Boolean> = MutableStateFlow(archived.value.isNotEmpty())

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

    override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(emptyList())

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    // Новый метод контракта (entry-prefilled-reps-and-duration, задача 1.4):
    // заглушка — VM этого экрана историю упражнений не читает.
    override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        categories: List<Category>,
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)

    // Новый контракт категорий (custom-categories, этап 2): заглушки —
    // категорийные поведения экрана архива и расширение fake приходят
    // в этапе 7.

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
    ): Result<Unit> {
        setCategoryArchivedCalls.add(id to isArchived)
        return Result.success(Unit)
    }

    override fun categories(): Flow<List<Category>> = MutableStateFlow(emptyList())

    override fun archivedCategories(): Flow<List<Category>> = archivedCategories

    override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = MutableStateFlow(emptySet())

    override suspend fun getAllCategories(): Result<List<Category>> = Result.success(emptyList())
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
            category = "PULL",
            isCustom = false,
            comment = null,
            createdAt = 1_721_000_000_000L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val plank =
        Exercise(
            id = 2L,
            name = "Планка",
            category = "CORE",
            isCustom = false,
            comment = null,
            createdAt = 1_721_100_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private val archivedCore =
        Category(
            id = 10L,
            name = "CORE",
            colorArgb = 0xFF4CAF50.toInt(),
            isCustom = false,
            isArchived = true
        )

    private val archivedCustom =
        Category(
            id = 11L,
            name = "Дома",
            colorArgb = 0xFF9C27B0.toInt(),
            isCustom = true,
            isArchived = true
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

    private val fakeAnalytics = FakeAnalyticsProvider()

    private fun createViewModel(repository: ArchivedExercisesFakeRepository): ArchivedExercisesViewModel =
        ArchivedExercisesViewModel(repository, fakeAnalytics)

    /**
     * `unarchive` эмитит `UserAction(UNARCHIVE)` — на вызове операции,
     * до/независимо от исхода (design D10).
     */
    @Test
    fun unarchive_logs_user_action_unarchive() =
        runTest(testDispatcher) {
            // Given
            val repository = ArchivedExercisesFakeRepository(initialArchived = listOf(pullups))
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.unarchive(pullups.id)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.UNARCHIVE)),
                fakeAnalytics.events,
                "unarchive логирует UserAction(UNARCHIVE)"
            )
        }

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
            viewModel.unarchive(pullups.id)
            advanceUntilIdle()

            // Then — репозиторию передан id и направление false
            assertEquals(
                listOf(pullups.id to false),
                repository.setArchivedCalls,
                "unarchive(item) вызвал setArchived(item.id, false)"
            )
        }

    /**
     * custom-categories, tasks 7.1: после эмиссии обоих источников —
     * `Success(exercises, categories, selectedTab)`; вкладка по
     * умолчанию — «Упражнения».
     */
    @Test
    fun state_when_subscribed_then_success_with_exercises_categories_and_default_tab() =
        runTest(testDispatcher) {
            // Given — в архиве оба типа
            val repository =
                ArchivedExercisesFakeRepository(
                    initialArchived = listOf(pullups),
                    initialArchivedCategories = listOf(archivedCore)
                )
            val viewModel = createViewModel(repository)

            // When
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is ArchivedExercisesUiState.Success, "После эмиссии — Success")
            state as ArchivedExercisesUiState.Success
            assertEquals(listOf(pullups), state.exercises, "Список архивных упражнений")
            assertEquals(listOf(archivedCore), state.categories, "Список архивных категорий")
            assertEquals(
                ArchivedTab.EXERCISES,
                state.selectedTab,
                "Вкладка по умолчанию — «Упражнения»"
            )
        }

    /**
     * custom-categories, tasks 7.1: `selectTab(CATEGORIES)` отражается в
     * `Success.selectedTab` (выбранная вкладка хранится в ViewModel и
     * переживает поворот) и логирует `UserAction(SELECT_CATEGORIES_TAB)`
     * с именем вкладки в `value` (design D11).
     */
    @Test
    fun selectTab_then_reflected_in_success() =
        runTest(testDispatcher) {
            // Given — в архиве оба типа
            val repository =
                ArchivedExercisesFakeRepository(
                    initialArchived = listOf(pullups),
                    initialArchivedCategories = listOf(archivedCore)
                )
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.selectTab(ArchivedTab.CATEGORIES)
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is ArchivedExercisesUiState.Success)
            assertEquals(
                ArchivedTab.CATEGORIES,
                (state as ArchivedExercisesUiState.Success).selectedTab,
                "selectTab(CATEGORIES) отражается в Success.selectedTab"
            )
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.SELECT_CATEGORIES_TAB, "categories")),
                fakeAnalytics.events,
                "selectTab(CATEGORIES) логирует UserAction(SELECT_CATEGORIES_TAB) с value=categories"
            )
        }

    /**
     * custom-categories, tasks 7.1 + spec «Восстановление последнего
     * упражнения переключает вкладку»: опустошение вкладки «Упражнения»
     * при непустых категориях — автовыбор «Категорий».
     */
    @Test
    fun state_when_exercises_emptied_then_auto_switch_to_categories() =
        runTest(testDispatcher) {
            // Given — в архиве оба типа, выбрана вкладка «Упражнения»
            val repository =
                ArchivedExercisesFakeRepository(
                    initialArchived = listOf(pullups),
                    initialArchivedCategories = listOf(archivedCore)
                )
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — последнее упражнение восстановлено (репозиторий
            // эмитит пустой список упражнений)
            repository.archived.value = emptyList()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is ArchivedExercisesUiState.Success)
            assertEquals(
                ArchivedTab.CATEGORIES,
                (state as ArchivedExercisesUiState.Success).selectedTab,
                "Опустошение вкладки упражнений — автовыбор «Категорий»"
            )
        }

    /**
     * custom-categories, tasks 7.1: опустошение вкладки «Категории»
     * при непустых упражнениях — автовыбор «Упражнений».
     */
    @Test
    fun state_when_categories_emptied_then_auto_switch_to_exercises() =
        runTest(testDispatcher) {
            // Given — в архиве оба типа, выбрана вкладка «Категории»
            val repository =
                ArchivedExercisesFakeRepository(
                    initialArchived = listOf(pullups),
                    initialArchivedCategories = listOf(archivedCore)
                )
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.selectTab(ArchivedTab.CATEGORIES)
            advanceUntilIdle()

            // When — последняя категория восстановлена
            repository.archivedCategories.value = emptyList()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is ArchivedExercisesUiState.Success)
            assertEquals(
                ArchivedTab.EXERCISES,
                (state as ArchivedExercisesUiState.Success).selectedTab,
                "Опустошение вкладки категорий — автовыбор «Упражнений»"
            )
        }

    /**
     * custom-categories, spec «Пустое состояние списка»: опустошение
     * обеих вкладок — заглушка без закрытия экрана; автовыбора нет
     * (непустой вкладки не осталось), выбранная вкладка сохраняется.
     */
    @Test
    fun state_when_both_emptied_then_tab_kept() =
        runTest(testDispatcher) {
            // Given — в архиве оба типа, выбрана вкладка «Категории»
            val repository =
                ArchivedExercisesFakeRepository(
                    initialArchived = listOf(pullups),
                    initialArchivedCategories = listOf(archivedCore)
                )
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.selectTab(ArchivedTab.CATEGORIES)
            advanceUntilIdle()

            // When — архив опустел целиком
            repository.archivedCategories.value = emptyList()
            repository.archived.value = emptyList()
            advanceUntilIdle()

            // Then — вкладка не переключилась, списки пусты (заглушка —
            // вычисляется на use site из пустых списков)
            val state = viewModel.uiState.value
            assertTrue(state is ArchivedExercisesUiState.Success)
            state as ArchivedExercisesUiState.Success
            assertEquals(ArchivedTab.CATEGORIES, state.selectedTab, "Непустой вкладки нет — таб сохранён")
            assertTrue(state.exercises.isEmpty())
            assertTrue(state.categories.isEmpty())
        }

    /**
     * custom-categories, tasks 7.1 + spec «Восстановление категории
     * обновляет список»: `unarchiveCategory(category)` вызывает
     * `repository.setCategoryArchived(category.id, false)`.
     */
    @Test
    fun unarchive_category_calls_setCategoryArchived_with_false() =
        runTest(testDispatcher) {
            // Given
            val repository =
                ArchivedExercisesFakeRepository(
                    initialArchivedCategories = listOf(archivedCore)
                )
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.unarchiveCategory(archivedCore.id)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(archivedCore.id to false),
                repository.setCategoryArchivedCalls,
                "unarchiveCategory(category) вызвал setCategoryArchived(category.id, false)"
            )
        }

    /**
     * custom-categories, design D11: восстановление категории логирует
     * `UserAction(UNARCHIVE_CATEGORY)` — на вызове операции,
     * до/независимо от исхода (зеркально `unarchive` упражнения, но
     * отдельный тип события под категорию).
     */
    @Test
    fun unarchive_category_logs_user_action_unarchive_category() =
        runTest(testDispatcher) {
            // Given
            val repository =
                ArchivedExercisesFakeRepository(
                    initialArchivedCategories = listOf(archivedCustom)
                )
            val viewModel = createViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.unarchiveCategory(archivedCustom.id)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.UNARCHIVE_CATEGORY)),
                fakeAnalytics.events,
                "unarchiveCategory логирует UserAction(UNARCHIVE_CATEGORY)"
            )
        }
}
