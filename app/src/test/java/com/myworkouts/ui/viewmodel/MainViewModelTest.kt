package com.myworkouts.ui.viewmodel

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.ReviewAutoRequestOutcome
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.ReviewCountersDataStore
import com.myworkouts.data.preferences.ReviewCountersDataStore.Companion.REVIEW_COUNTERS_FAILURE_CONTEXT
import com.myworkouts.domain.crash.FakeCrashReporter
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.domain.review.ReviewErrorType
import com.myworkouts.domain.review.ReviewFlowLauncher
import com.myworkouts.domain.review.ReviewFlowOutcome
import com.myworkouts.ui.review.ReviewSessionHolder
import com.myworkouts.ui.state.MainUiState
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Path

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

    /** Сценарий провала для теста ошибки; по умолчанию успех. */
    var deleteEntryResult: Result<Unit> = Result.success(Unit)

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override fun entries(): Flow<List<EntryListItem>> = entriesFlow

    override fun entryById(id: Long): Flow<EntryListItem?> = flowOf(null)

    override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> {
        deletedIds += id
        if (deleteEntryResult.isFailure) return deleteEntryResult
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

    // Новый метод контракта (entry-prefilled-reps-and-duration, задача 1.4):
    // заглушка — VM этого экрана историю упражнений не читает.
    override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        categories: List<Category>,
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> {
        entriesFlow.value = emptyList()
        catalog.value = emptyList()
        return Result.success(Unit)
    }

    // Новые методы контракта (exercises-archive, §2.7): заглушки —
    // MainViewModel их не вызывает.

    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> = Result.success(Unit)

    override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    // Новый контракт категорий (custom-categories, этап 2): заглушки —
    // MainViewModel их не вызывает.

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

    override fun hasArchived(): Flow<Boolean> = MutableStateFlow(false)

    override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = MutableStateFlow(emptySet())

    override suspend fun getAllCategories(): Result<List<Category>> = Result.success(emptyList())
}

/**
 * Unit-тесты для [MainViewModel] (workout-entries-list, task 3.1/3.2;
 * отложенный показ pending review-майлстоуна — add-rustore-in-app-review,
 * задача 7.3).
 *
 * Подписка на `uiState` — через `backgroundScope` (запускает
 * `WhileSubscribed`); значения читаются синхронно через `.value`
 * после `advanceUntilIdle` (образец — `EntryCreateViewModelTest`).
 * Review-сценарии: fake-лаунчер с фиксацией вызовов, реальное хранилище
 * счётчиков над temp-файлом, задержка ~0.8 с — виртуальным временем
 * [testDispatcher].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private lateinit var testDispatcher: TestDispatcher

    /** Задержка показа review-флоу — зеркало константы [MainViewModel] (~0.8 с). */
    private val reviewAutoRequestDelayMs = 800L

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
    private val fakeAnalytics = FakeAnalyticsProvider()
    private val fakeCrash = FakeCrashReporter()

    /**
     * Fake-лаунчер review-флоу по умолчанию (задача 7.3): фиксирует
     * вызовы, исход — «форма показана». Тесты, которым нужен другой
     * исход или снапшот счётчиков в момент запуска, создают свой
     * [MainViewModelRecordingLauncher] и передают в [createViewModel].
     */
    private val recordingReviewLauncher = MainViewModelRecordingLauncher()

    /**
     * Temp-каталог для реального хранилища счётчиков ниже: свежий на каждый
     * тест — singleton-per-file коллизий между тестами нет.
     */
    @TempDir
    lateinit var tempDir: Path

    /**
     * Реальное хранилище счётчиков review-майлстоунов поверх temp-файла
     * (прецедент `ReviewCountersDataStoreTest` — JVM без Robolectric).
     * Scope DataStore привязан к [testDispatcher]: внутренний актёр
     * DataStore живёт на планировщике теста, поэтому чтения и записи
     * счётчиков детерминированы виртуальным временем — с дефолтным
     * `Dispatchers.IO` resumption чтения гоняло бы против
     * `advanceTimeBy`/`advanceUntilIdle` (реальный IO против виртуального
     * времени — недетерминизм).
     */
    private val reviewCountersDataStore: ReviewCountersDataStore by lazy {
        ReviewCountersDataStore(
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(SupervisorJob() + testDispatcher),
                produceFile = { tempDir.resolve("review_counters.preferences_pb").toFile() }
            )
        )
    }

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

    /**
     * Фабрика ViewModel с review-зависимостями (задача 7.3): по умолчанию
     * гейт выключен — существующие тесты списка/удаления не задевают
     * review-последовательность; review-сценарии включают гейт явно и
     * при необходимости подменяют хранилище/лаунчер/holder.
     */
    private fun createViewModel(
        isReviewEnabled: Boolean = false,
        reviewCountersDataStore: ReviewCountersDataStore = this.reviewCountersDataStore,
        reviewFlowLauncher: ReviewFlowLauncher = recordingReviewLauncher,
        reviewSessionHolder: ReviewSessionHolder = ReviewSessionHolder()
    ): MainViewModel =
        MainViewModel(
            repository = repository,
            analyticsProvider = fakeAnalytics,
            crashReporter = fakeCrash,
            isReviewEnabled = isReviewEnabled,
            reviewCountersDataStore = reviewCountersDataStore,
            reviewFlowLauncher = reviewFlowLauncher,
            reviewSessionHolder = reviewSessionHolder
        )

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

    /**
     * `confirmDelete` эмитит `UserAction(DELETE)` — на вызове операции,
     * до/независимо от исхода (design D10).
     */
    @Test
    fun confirm_delete_logs_user_action_delete() =
        runTest(testDispatcher) {
            // Given — открыт алерт удаления
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
                listOf(AnalyticsEvent.UserAction(UserActionType.DELETE)),
                fakeAnalytics.events,
                "confirmDelete логирует UserAction(DELETE)"
            )
        }

    /** Провал deleteEntry логируется в crash-репортер, pending остаётся для повтора. */
    @Test
    fun confirm_delete_on_failure_then_logs_exception_and_keeps_pending() =
        runTest(testDispatcher) {
            // Given — провал репозитория и открытый алерт удаления
            val error = IllegalStateException("Сбой БД")
            repository.deleteEntryResult = Result.failure(error)
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
                listOf(FakeCrashReporter.Entry(error, "Удаление записи")),
                fakeCrash.logged,
                "Провал удаления записывается в crash-репортер с контекстом"
            )
            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Success, "После провала — Success")
            val success = state as MainUiState.Success
            assertEquals(
                item1.id,
                success.pendingDeleteId,
                "При провале pendingDeleteId сохраняется для повтора"
            )
        }

    // ---------- Отложенный показ pending review-майлстоуна (задача 7.3, design D5) ----------

    /**
     * Happy path: при наличии pending лаунчер вызывается после задержки
     * ~0.8 с; milestone помечен пройденным и pending снят атомарно ДО
     * вызова лаунчера; событие SHOWN залогировано.
     */
    @Test
    fun review_shown_happy_path_launches_after_delay_and_marks_milestone_before_launch() =
        runTest(testDispatcher) {
            // Given — pending milestone 1 запланирован; снапшот счётчиков
            // в момент запуска флоу снимается хуком fake-лаунчера
            var pendingAtLaunch: Int? = null
            var passedAtLaunch: Set<Int> = emptySet()
            val launcher =
                MainViewModelRecordingLauncher {
                    val snapshot = reviewCountersDataStore.counters.first()
                    pendingAtLaunch = snapshot.pendingMilestone
                    passedAtLaunch = snapshot.passedMilestones
                }
            reviewCountersDataStore.setPendingMilestone(1)
            val viewModel = createViewModel(isReviewEnabled = true, reviewFlowLauncher = launcher)

            // When — init ViewModel запустил отложенный показ; идёт окно задержки
            runCurrent()
            assertEquals(
                0,
                launcher.callCount,
                "В окне задержки (~0.8 с) лаунчер ещё не вызван"
            )
            advanceTimeBy(reviewAutoRequestDelayMs)
            advanceUntilIdle()

            // Then — запуск после задержки, атомарная отметка до лаунчера
            assertEquals(1, launcher.callCount, "После задержки ~0.8 с лаунчер вызван")
            assertNull(
                pendingAtLaunch,
                "В момент вызова лаунчера pending уже снят атомарно"
            )
            assertTrue(
                1 in passedAtLaunch,
                "В момент вызова лаунчера milestone уже помечен пройденным"
            )
            val counters = reviewCountersDataStore.counters.first()
            assertTrue(1 in counters.passedMilestones, "Milestone 1 в пройденных")
            assertNull(counters.pendingMilestone, "Совпадающий pending снят")
            assertEquals(
                listOf(AnalyticsEvent.ReviewAutoRequest(ReviewAutoRequestOutcome.SHOWN)),
                fakeAnalytics.events,
                "Исход SHOWN залогирован событием review_auto_request"
            )
        }

    /**
     * Ошибка лаунчера — значение исхода, а не исключение: интерфейс не
     * меняется (без падения и crash-лога), событие ERROR логируется
     * с типом ошибки, pending при этом снят и milestone пройден.
     */
    @Test
    fun review_launcher_error_logged_as_error_event_with_type() =
        runTest(testDispatcher) {
            // Given — pending есть, лаунчер вернёт типизированную ошибку
            reviewCountersDataStore.setPendingMilestone(1)
            val launcher =
                MainViewModelRecordingLauncher().apply {
                    outcome = ReviewFlowOutcome.Error(ReviewErrorType.NOT_INSTALLED)
                }
            val viewModel = createViewModel(isReviewEnabled = true, reviewFlowLauncher = launcher)

            // When
            advanceUntilIdle()

            // Then — исход ERROR значением, без падения
            assertEquals(1, launcher.callCount, "Лаунчер вызван")
            assertTrue(
                fakeCrash.logged.isEmpty(),
                "Ошибка флоу не падает и не логируется как сбой приложения"
            )
            assertEquals(
                listOf(
                    AnalyticsEvent.ReviewAutoRequest(
                        ReviewAutoRequestOutcome.ERROR,
                        errorType = ReviewErrorType.NOT_INSTALLED
                    )
                ),
                fakeAnalytics.events,
                "Исход ERROR залогирован с типом ошибки"
            )
            val counters = reviewCountersDataStore.counters.first()
            assertTrue(
                1 in counters.passedMilestones,
                "Milestone помечен пройденным и при ошибке лаунчера"
            )
            assertNull(counters.pendingMilestone, "Pending снят и при ошибке лаунчера")
        }

    /**
     * Pending снимается в момент запуска ДО вызова лаунчера и при ошибке
     * лаунчера тоже (design D5 — жизненный цикл pending замыкается
     * запуском флоу: состояние «pending ∈ passed» невозможно).
     */
    @Test
    fun review_pending_cleared_before_launch_on_launcher_error() =
        runTest(testDispatcher) {
            // Given — pending есть, снапшот снимается внутри launch()
            var pendingAtLaunch: Int? = null
            var passedAtLaunch: Set<Int> = emptySet()
            reviewCountersDataStore.setPendingMilestone(1)
            val launcher =
                MainViewModelRecordingLauncher(
                    onLaunch = {
                        val snapshot = reviewCountersDataStore.counters.first()
                        pendingAtLaunch = snapshot.pendingMilestone
                        passedAtLaunch = snapshot.passedMilestones
                    }
                ).apply { outcome = ReviewFlowOutcome.Error(ReviewErrorType.UNKNOWN) }
            val viewModel = createViewModel(isReviewEnabled = true, reviewFlowLauncher = launcher)

            // When
            advanceUntilIdle()

            // Then
            assertEquals(1, launcher.callCount)
            assertNull(pendingAtLaunch, "При ошибке лаунчера pending снят ДО вызова")
            assertTrue(1 in passedAtLaunch, "Milestone помечен пройденным ДО вызова")
        }

    /** Без pending лаунчер не вызывается, событий и отметок пройденным нет. */
    @Test
    fun review_no_pending_then_launcher_not_called() =
        runTest(testDispatcher) {
            // Given — счётчики без pending
            val viewModel = createViewModel(isReviewEnabled = true)

            // When
            advanceUntilIdle()

            // Then
            assertEquals(0, recordingReviewLauncher.callCount, "Лаунчер не вызван")
            assertTrue(fakeAnalytics.events.isEmpty(), "Событий нет")
            val counters = reviewCountersDataStore.counters.first()
            assertTrue(counters.passedMilestones.isEmpty(), "Milestone не помечен пройденным")
            assertNull(counters.pendingMilestone, "Pending не появился")
        }

    /**
     * Holder сообщает, что авто-запрос в сессии уже выполнялся: запуск
     * не инициируется, pending остаётся нетронутым (design D5 — блокировка
     * holder до задержки и до отметки milestone).
     */
    @Test
    fun review_holder_flag_preset_blocks_run_and_keeps_pending() =
        runTest(testDispatcher) {
            // Given — флаг сессии закрыт до создания ViewModel
            reviewCountersDataStore.setPendingMilestone(1)
            val holder =
                ReviewSessionHolder().apply { hasAutoRequestRun = true }
            val viewModel =
                createViewModel(isReviewEnabled = true, reviewSessionHolder = holder)

            // When
            advanceUntilIdle()

            // Then
            assertEquals(
                0,
                recordingReviewLauncher.callCount,
                "Флаг сессии блокирует запуск"
            )
            assertEquals(
                1,
                reviewCountersDataStore.counters.first().pendingMilestone,
                "Pending не снимается — запуск не выполнялся"
            )
            assertTrue(fakeAnalytics.events.isEmpty(), "Событий нет")
        }

    /**
     * Повторный вызов в сессии, в которой авто-запрос уже выполнялся,
     * второй запуск не инициирует — даже при новом pending (design D5:
     * флаг сессии закрыт ДО запуска, поэтому повтор отрезан полностью).
     */
    @Test
    fun review_holder_flag_blocks_second_trigger_in_session() =
        runTest(testDispatcher) {
            // Given — первый показ состоялся, флаг сессии закрыт
            reviewCountersDataStore.setPendingMilestone(1)
            val viewModel = createViewModel(isReviewEnabled = true)
            advanceUntilIdle()
            assertEquals(1, recordingReviewLauncher.callCount)

            // When — в той же сессии запланирован следующий pending,
            // пользователь возвращается на экран
            reviewCountersDataStore.setPendingMilestone(5)
            viewModel.onMainScreenShown()
            advanceUntilIdle()

            // Then
            assertEquals(
                1,
                recordingReviewLauncher.callCount,
                "Второй запуск в сессии не инициируется"
            )
            assertEquals(
                5,
                reviewCountersDataStore.counters.first().pendingMilestone,
                "Новый pending не потерян и не показан"
            )
            assertEquals(1, fakeAnalytics.events.size, "Новое событие не залогировано")
        }

    /**
     * Двойной триггер (init ViewModel + «возврат на экран») сериализуется
     * мьютексом: вторая последовательность после освобождения мьютекса
     * видит закрытую сессию и снятый pending — лаунчер вызван один раз.
     */
    @Test
    fun review_double_trigger_init_and_screen_shown_serialized_to_single_launch() =
        runTest(testDispatcher) {
            // Given — pending есть; обе точки запуска встали в очередь
            // до выполнения первой последовательности
            reviewCountersDataStore.setPendingMilestone(1)
            val viewModel = createViewModel(isReviewEnabled = true)

            // When
            viewModel.onMainScreenShown()
            advanceUntilIdle()

            // Then
            assertEquals(
                1,
                recordingReviewLauncher.callCount,
                "Мьютекс сериализовал повторы — один запуск"
            )
            assertEquals(1, fakeAnalytics.events.size, "Одно событие SHOWN")
            assertNull(reviewCountersDataStore.counters.first().pendingMilestone)
        }

    /**
     * Счётчики сброшены за окно задержки («Удалить все данные»):
     * перепроверка после задержки видит отсутствие pending — тихий
     * пропуск без запуска и без отметки milestone пройденным
     * (design D5, повтор при следующем возврате).
     */
    @Test
    fun review_counters_reset_during_delay_window_skips_launch() =
        runTest(testDispatcher) {
            // Given — последовательность вошла в окно задержки
            reviewCountersDataStore.setPendingMilestone(1)
            val viewModel = createViewModel(isReviewEnabled = true)
            runCurrent()

            // When — счётчики сброшены за окно задержки
            reviewCountersDataStore.reset()
            advanceUntilIdle()

            // Then
            assertEquals(
                0,
                recordingReviewLauncher.callCount,
                "Снятый pending — тихий пропуск"
            )
            assertTrue(fakeAnalytics.events.isEmpty(), "Событий нет")
            val counters = reviewCountersDataStore.counters.first()
            assertNull(counters.pendingMilestone)
            assertTrue(
                counters.passedMilestones.isEmpty(),
                "Milestone не помечен пройденным — флоу не запускался"
            )
        }

    /**
     * Pending изменился за окно задержки: перепроверка видит другой
     * milestone — тихий пропуск, новый pending не потерян и будет
     * показан при следующем возврате на экран (design D5).
     */
    @Test
    fun review_pending_changed_during_delay_window_skips_launch() =
        runTest(testDispatcher) {
            // Given — последовательность вошла в окно задержки с pending 1
            reviewCountersDataStore.setPendingMilestone(1)
            val viewModel = createViewModel(isReviewEnabled = true)
            runCurrent()

            // When — pending перезаписан другим milestone за окно задержки
            reviewCountersDataStore.setPendingMilestone(5)
            advanceUntilIdle()

            // Then
            assertEquals(
                0,
                recordingReviewLauncher.callCount,
                "Изменённый pending — тихий пропуск"
            )
            assertTrue(fakeAnalytics.events.isEmpty(), "Событий нет")
            assertEquals(
                5,
                reviewCountersDataStore.counters.first().pendingMilestone,
                "Новый pending сохранён для следующего возврата на экран"
            )
        }

    /**
     * Сбой чтения счётчиков (IOException) — тихий выход без падения:
     * лаунчер не вызывается, событий нет; сбой фиксируется тихим логом
     * через crash-репортер (design D5 — «тихий лог и выход», повтор при
     * следующем возврате на экран).
     */
    @Test
    fun review_counters_read_io_exception_silently_skips_with_crash_log() =
        runTest(testDispatcher) {
            // Given — чтение счётчиков падает IOException
            val readFailure = IOException("Сбой файла счётчиков")
            val failingStore = mockk<ReviewCountersDataStore>(relaxed = true)
            every { failingStore.counters } returns flow { throw readFailure }
            val viewModel =
                createViewModel(isReviewEnabled = true, reviewCountersDataStore = failingStore)

            // When
            advanceUntilIdle()

            // Then — тихий пропуск без падения и событий
            assertEquals(
                0,
                recordingReviewLauncher.callCount,
                "Сбой чтения — лаунчер не вызывается"
            )
            assertTrue(fakeAnalytics.events.isEmpty(), "Событий нет")
            assertEquals(
                listOf(FakeCrashReporter.Entry(readFailure, REVIEW_COUNTERS_FAILURE_CONTEXT)),
                fakeCrash.logged,
                "Сбой фиксируется тихим логом через crash-репортер"
            )
        }

    /**
     * Новая сессия (свежий holder и лаунчер, то же хранилище): показанный
     * milestone пройден и pending снят — повторного запуска для него нет
     * (спека «Пройденный milestone не повторяется»).
     */
    @Test
    fun review_new_session_fresh_holder_no_rerun_for_passed_milestone() =
        runTest(testDispatcher) {
            // Given — сессия 1: pending показан, milestone 1 пройден
            reviewCountersDataStore.setPendingMilestone(1)
            createViewModel(isReviewEnabled = true)
            advanceUntilIdle()
            assertEquals(1, recordingReviewLauncher.callCount)

            // When — новая сессия приложения: ViewModel пересоздан
            val secondLauncher = MainViewModelRecordingLauncher()
            createViewModel(isReviewEnabled = true, reviewFlowLauncher = secondLauncher)
            advanceUntilIdle()

            // Then
            assertEquals(
                0,
                secondLauncher.callCount,
                "Показанный milestone не повторяется в новой сессии"
            )
            assertEquals(1, fakeAnalytics.events.size, "Новое событие не залогировано")
        }

    /**
     * Гейт `isReviewEnabled = false`: ни обращений к хранилищу, ни
     * вызовов лаунчера, ни событий — ни из init, ни из явного
     * [MainViewModel.onMainScreenShown] (github-вариант).
     */
    @Test
    fun review_disabled_gate_no_storage_or_launcher_access() =
        runTest(testDispatcher) {
            // Given — гейт выключен, хранилище — mock с проверкой обращений
            val countersStore = mockk<ReviewCountersDataStore>(relaxed = true)
            val viewModel =
                createViewModel(isReviewEnabled = false, reviewCountersDataStore = countersStore)

            // When — оба триггера сессии
            viewModel.onMainScreenShown()
            advanceUntilIdle()

            // Then
            assertEquals(0, recordingReviewLauncher.callCount, "Лаунчер не вызывается")
            assertTrue(fakeAnalytics.events.isEmpty(), "Событий нет")
            verify { countersStore wasNot Called }
        }
}

/**
 * Fake-лаунчер review-флоу (задача 7.3): фиксирует вызовы [launch] и
 * возвращает настраиваемый исход. Опциональный хук [onLaunch] выполняется
 * внутри launch() до возврата исхода — им тесты снимают снапшот счётчиков
 * «в момент запуска» (проверка «pending снят и milestone пройден до
 * вызова лаунчера»).
 *
 * Отдельное имя — чтобы не пересекаться с fake-ами других тест-файлов
 * (test source set компилируется одной единицей, образец —
 * [MainViewModelFakeRepository]).
 */
private class MainViewModelRecordingLauncher(
    private val onLaunch: (suspend () -> Unit)? = null
) : ReviewFlowLauncher {
    /** Число фактических вызовов launch(). */
    var callCount: Int = 0
        private set

    /** Исход, возвращаемый launch() (по умолчанию — форма показана). */
    var outcome: ReviewFlowOutcome = ReviewFlowOutcome.Shown

    override suspend fun launch(): ReviewFlowOutcome {
        callCount += 1
        onLaunch?.invoke()
        return outcome
    }
}
