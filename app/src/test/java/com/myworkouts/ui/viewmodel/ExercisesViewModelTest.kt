package com.myworkouts.ui.viewmodel

import android.util.Log
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AppErrorOperation
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.CategoryInUseDeletionException
import com.myworkouts.data.repository.LinkedEntriesDeletionException
import com.myworkouts.data.repository.StandardCategoryDeletionException
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.DeleteCategoryError
import com.myworkouts.domain.model.DeleteExerciseError
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExercisesTab
import com.myworkouts.ui.state.ExercisesUiState
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.jupiter.api.Assertions.assertSame
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

    /**
     * Вызовы `setArchived` в порядке обращений (exercises-archive, §2.7):
     * тесты §3 проверяют id и направление архивации.
     */
    val setArchivedCalls = mutableListOf<Pair<Long, Boolean>>()

    /**
     * Сценарий `setArchived` для теста §3.3: по умолчанию — успех; тест
     * ошибки ставит `Result.failure(IllegalStateException("Упражнение не
     * найдено"))` для имитации race между pre-check и confirm.
     */
    var setArchivedResult: Result<Unit> = Result.success(Unit)

    /**
     * Подменяет `hasArchived()` репозитория (реактивный признак «в архиве
     * есть упражнения или категории»); тест §3.4 устанавливает значение
     * через `hasArchivedFlow.value = ...`.
     */
    val hasArchivedFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // ---------- custom-categories (этап 5): категорийные потоки и сценарии ----------

    /** Управляемый список активных категорий (вкладка «Категории»). */
    val categoriesFlow: MutableStateFlow<List<Category>> = MutableStateFlow(emptyList())

    /** Управляемый набор имён категорий с упражнениями (pre-check удаления). */
    val categoryNamesInUse: MutableStateFlow<Set<String>> = MutableStateFlow(emptySet())

    /** Сценарий `deleteCategory` для тестов §5. */
    var deleteCategoryResult: Result<Unit> = Result.success(Unit)

    var lastDeletedCategoryId: Long? = null

    /** Вызовы `setCategoryArchived` в порядке обращений (тесты §5). */
    val setCategoryArchivedCalls = mutableListOf<Pair<Long, Boolean>>()

    /** Сценарий `setCategoryArchived` для теста §5 (race — по образцу §3.3). */
    var setCategoryArchivedResult: Result<Unit> = Result.success(Unit)

    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> {
        setArchivedCalls.add(id to isArchived)
        return setArchivedResult
    }

    override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override fun hasArchived(): Flow<Boolean> = hasArchivedFlow

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override fun entries(): Flow<List<com.myworkouts.domain.model.EntryListItem>> = MutableStateFlow(emptyList())

    override fun entriesWithExercise(): Flow<List<com.myworkouts.domain.model.EntryWithExercise>> =
        MutableStateFlow(emptyList())

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

    // Новый метод контракта (entry-prefilled-reps-and-duration, задача 1.4):
    // заглушка — VM этого экрана историю упражнений не читает.
    override suspend fun exerciseHistory(exerciseId: Long): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        categories: List<Category>,
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)

    // Новый контракт категорий (custom-categories, этапы 2 и 5):
    // управляемые потоки и запись вызовов для тестов §5.

    override suspend fun addCategory(name: String): Result<Category> =
        Result.success(Category(id = 0L, name = name.trim(), colorArgb = 0, isCustom = true))

    override suspend fun renameCategory(
        id: Long,
        newName: String
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteCategory(id: Long): Result<Unit> {
        lastDeletedCategoryId = id
        return deleteCategoryResult
    }

    override suspend fun setCategoryArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> {
        setCategoryArchivedCalls.add(id to isArchived)
        return setCategoryArchivedResult
    }

    override fun categories(): Flow<List<Category>> = categoriesFlow

    override fun archivedCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())

    override fun categoryNamesWithLinkedExercises(): Flow<Set<String>> = categoryNamesInUse

    override suspend fun getAllCategories(): Result<List<Category>> = Result.success(emptyList())
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
            comment = "тело — прямая линия",
            createdAt = 1_721_100_000_000L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private lateinit var repository: ExercisesViewModelFakeRepository

    /** Перехватчик событий аналитики (design D9). */
    private val analyticsProvider = FakeAnalyticsProvider()

    /** Mock [AppSettingsDataStore] для VM (archive-alert-hide, tasks §1.2).
     *  На этом этапе используется только чтобы компилировались тесты;
     *  реальные stub'ы для `dontShowArchiveDialog` / `setDontShowArchiveDialog`
     *  появятся в §2.x. */
    private lateinit var dataStore: AppSettingsDataStore

    /** Стандартная категория (тесты §5, custom-categories). */
    private val push =
        Category(
            id = 1L,
            name = "PUSH",
            colorArgb = 0xFF4E6EF2.toInt(),
            isCustom = false
        )

    /** Кастомная категория (тесты §5, custom-categories). */
    private val shoulders =
        Category(
            id = 2L,
            name = "Плечи",
            colorArgb = 0xFF22AA66.toInt(),
            isCustom = true
        )

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
        dataStore = mockk(relaxed = true)
        // Default-поведение: opt-out выключен (= показывать диалог) —
        // обратная совместимость для существующих тестов §1–§3. Тесты
        // §4.1 (red) переопределяют stub на `flowOf(true)`.
        every { dataStore.dontShowArchiveDialog } returns flowOf(false)
        // Категорийный opt-out (custom-categories): default — показывать
        // диалог; тесты §5 переопределяют на `flowOf(true)`.
        every { dataStore.dontShowCategoryArchiveDialog } returns flowOf(false)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun createViewModel(): ExercisesViewModel = ExercisesViewModel(repository, dataStore, analyticsProvider)

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
                    category = "LEGS",
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
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.DELETE)),
                analyticsProvider.events,
                "Подтверждённое удаление — user_action DELETE"
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
            val failure = LinkedEntriesDeletionException()
            repository.deleteExerciseResult = Result.failure(failure)
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
            val appError =
                analyticsProvider.events.filterIsInstance<AnalyticsEvent.AppError>().single()
            assertEquals(
                AppErrorOperation.DELETE_EXERCISE,
                appError.operation,
                "Сбой удаления — событие AppError(DELETE_EXERCISE)"
            )
            assertSame(failure, appError.throwable, "В событии — итоговое исключение")

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

    // ---------- §3. TDD: pending-архивация + hasArchived ----------

    /**
     * `requestArchive(item)` ставит `pendingArchiveId == item.id` в
     * `Success`; `cancelArchive()` сбрасывает в `null`. Доступно и для
     * кастомных, и для стандартных (exercises-archive design Decision 4).
     *
     * (exercises-archive, tasks §3.1.)
     */
    @Test
    fun requestArchive_setsPendingArchiveId_and_cancelArchiveResetsIt() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — запрос архивации кастомного (через pullups)
            viewModel.requestArchive(pullups)
            advanceUntilIdle()

            // Then
            val afterRequest = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                pullups.id,
                afterRequest.pendingArchiveId,
                "requestArchive ставит pendingArchiveId = item.id"
            )

            // When — отмена
            viewModel.cancelArchive()
            advanceUntilIdle()

            // Then
            val afterCancel = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(
                afterCancel.pendingArchiveId,
                "cancelArchive сбрасывает pendingArchiveId"
            )

            // When — запрос архивации стандартного (через plank,
            // isCustom = false) — тот же путь, архивация разрешена для обоих
            viewModel.requestArchive(plank)
            advanceUntilIdle()

            // Then
            val afterStandard = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                plank.id,
                afterStandard.pendingArchiveId,
                "requestArchive работает и для стандартного упражнения"
            )
        }

    /**
     * `confirmArchive()` вызывает `repository.setArchived(id, true)`;
     * при `Result.success` pending сбрасывается, ошибок в state не
     * появляется (архивация обратима — снекбар неуместен, design
     * exercises-archive Decision 5; tasks §3.2).
     */
    @Test
    fun confirmArchive_onSuccess_clearsPendingAndCallsSetArchivedTrue() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestArchive(pullups)
            advanceUntilIdle()
            assertEquals(pullups.id, (viewModel.uiState.value as ExercisesUiState.Success).pendingArchiveId)

            // When — подтверждение, по умолчанию `setArchived` возвращает success
            viewModel.confirmArchive()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(state.pendingArchiveId, "pending сброшен после успешной архивации")
            assertEquals(
                listOf(pullups.id to true),
                repository.setArchivedCalls,
                "confirmArchive вызвал setArchived(id, true)"
            )
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.ARCHIVE)),
                analyticsProvider.events,
                "Подтверждённая архивация — user_action ARCHIVE"
            )
        }

    /**
     * `confirmArchive()` при `Result.failure(IllegalStateException("Упражнение
     * не найдено"))` — pending сброшен, повторный `requestArchive`
     * возможен (race между pre-check и confirm — exercises-archive
     * design Decision 3; tasks §3.3). В отличие от `confirmDelete`,
     * никакого `archiveError` в UiState не появляется: архивация
     * обратима, повторный `requestArchive` достаточен (spec «Failure
     * при подтверждении»).
     */
    @Test
    fun confirmArchive_onFailure_clearsPending_andAllowsRetry() =
        runTest(testDispatcher) {
            // Given — форсируем failure для setArchived
            repository.setArchivedResult =
                Result.failure(IllegalStateException("Упражнение не найдено"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestArchive(pullups)
            advanceUntilIdle()

            // When — подтверждение, репозиторий вернёт failure
            viewModel.confirmArchive()
            advanceUntilIdle()

            // Then — pending сброшен
            val afterConfirm = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(
                afterConfirm.pendingArchiveId,
                "pending сброшен и при failure (не блокирует повторный запрос)"
            )

            // When — повторный requestArchive после ошибки
            viewModel.requestArchive(plank)
            advanceUntilIdle()

            // Then — повторный запрос открывает новый pending
            val retryState = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                plank.id,
                retryState.pendingArchiveId,
                "Повторный requestArchive открывает новый pending после failure"
            )
            assertTrue(
                analyticsProvider.events.filterIsInstance<AnalyticsEvent.AppError>().isEmpty(),
                "Архивация вне канона ошибок (design D9) — AppError не эмитится"
            )
        }

    /**
     * `hasArchived` из репозитория реактивно попадает в
     * `Success.hasArchived` (exercises-archive design Decision 2;
     * tasks §3.4). Тест управляет значением через
     * `repository.hasArchivedFlow.value`.
     */
    @Test
    fun hasArchived_fromRepository_reactivelyReflectedInUiState() =
        runTest(testDispatcher) {
            // Given — стартовое значение false
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            val initial = viewModel.uiState.value as ExercisesUiState.Success
            assertFalse(
                initial.hasArchived,
                "Стартовый hasArchived = false (initialValue stateIn)"
            )

            // When — в архиве появился объект любого типа
            repository.hasArchivedFlow.value = true
            advanceUntilIdle()

            // Then
            val afterOne = viewModel.uiState.value as ExercisesUiState.Success
            assertTrue(
                afterOne.hasArchived,
                "hasArchived реактивно = true после изменения в репозитории"
            )

            // When — архив восстановили (true → false)
            repository.hasArchivedFlow.value = false
            advanceUntilIdle()

            // Then
            val afterRestore = viewModel.uiState.value as ExercisesUiState.Success
            assertFalse(
                afterRestore.hasArchived,
                "hasArchived реактивно = false после восстановления"
            )
        }

    // ---------- §4. archive-alert-hide: opt-out диалога через DataStore ----------

    /**
     * `requestArchive(item)` при `dataStore.dontShowArchiveDialog == true` —
     * сразу вызывает `repository.setArchived(item.id, true)` без открытия
     * диалога: `confirmArchive` НЕ вызывается, `pendingArchiveId == null`,
     * `setArchivedCalls` содержит `(item.id, true)`.
     *
     * (archive-alert-hide, tasks §2.1 / §3.1.)
     */
    @Test
    fun requestArchive_when_dontShowDialog_true_then_archive_without_dialog() =
        runTest(testDispatcher) {
            // Given — opt-out включён в DataStore
            every { dataStore.dontShowArchiveDialog } returns flowOf(true)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — запрос архивации (текущее поведение — открыть диалог;
            // новое поведение — архивировать сразу)
            viewModel.requestArchive(pullups)
            advanceUntilIdle()

            // Then — диалог не открыт, архивация прошла
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(
                state.pendingArchiveId,
                "pendingArchiveId == null при dontShowArchiveDialog=true"
            )
            assertEquals(
                listOf(pullups.id to true),
                repository.setArchivedCalls,
                "setArchived(id, true) вызван сразу при requestArchive"
            )
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.ARCHIVE)),
                analyticsProvider.events,
                "Прямая архивация без диалога — user_action ARCHIVE (design D10)"
            )
        }

    /**
     * `requestArchive(item)` при `dataStore.dontShowArchiveDialog == false`
     * (default) — открывает диалог, `setArchived` НЕ вызывается
     * (поведение архивации — как было до feature).
     *
     * (archive-alert-hide, tasks §2.2 / §3.1.)
     */
    @Test
    fun requestArchive_when_dontShowDialog_false_then_open_dialog() =
        runTest(testDispatcher) {
            // Given — opt-out выключен (default)
            every { dataStore.dontShowArchiveDialog } returns flowOf(false)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.requestArchive(pullups)
            advanceUntilIdle()

            // Then — диалог открыт, репозиторий не тронут
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                pullups.id,
                state.pendingArchiveId,
                "pendingArchiveId == item.id при dontShowArchiveDialog=false"
            )
            assertTrue(
                repository.setArchivedCalls.isEmpty(),
                "setArchived НЕ вызван на requestArchive (default-поведение — диалог)"
            )
            assertTrue(
                analyticsProvider.events.isEmpty(),
                "Открытие диалога не логируется (design D10)"
            )
        }

    /**
     * `confirmArchive(suppress = true)` — вызывает
     * `repository.setArchived(id, true)` И
     * `dataStore.setDontShowArchiveDialog(true)` ровно один раз.
     *
     * (archive-alert-hide, tasks §2.3 / §3.2.)
     */
    @Test
    fun confirmArchive_when_suppressTrue_then_writes_pref_and_archives() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestArchive(pullups)
            advanceUntilIdle()

            // When
            viewModel.confirmArchive(suppress = true)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(pullups.id to true),
                repository.setArchivedCalls,
                "confirmArchive(suppress=true) вызвал setArchived(id, true)"
            )
            coVerify(exactly = 1) { dataStore.setDontShowArchiveDialog(true) }
        }

    /**
     * `confirmArchive(suppress = false)` — вызывает
     * `repository.setArchived(id, true)` и НЕ вызывает
     * `dataStore.setDontShowArchiveDialog` (защита от лишней записи pref).
     *
     * (archive-alert-hide, tasks §2.4 — regression-guard; не TDD-red.)
     */
    @Test
    fun confirmArchive_when_suppressFalse_then_does_not_write_pref() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestArchive(pullups)
            advanceUntilIdle()

            // When
            viewModel.confirmArchive(suppress = false)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(pullups.id to true),
                repository.setArchivedCalls,
                "confirmArchive(suppress=false) вызвал setArchived(id, true)"
            )
            coVerify(exactly = 0) { dataStore.setDontShowArchiveDialog(any()) }
        }

    // ---------- §5. custom-categories: табы, вкладка «Категории» ----------

    /**
     * Выбранная вкладка по умолчанию — [ExercisesTab.EXERCISES];
     * `selectTab(CATEGORIES)` отражается в `Success.selectedTab`
     * (spec «Выбор вкладки отражается в Success»).
     */
    @Test
    fun selectTab_then_reflectedInUiState_and_defaultIsExercises() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — дефолт
            val initial = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                ExercisesTab.EXERCISES,
                initial.selectedTab,
                "Дефолтная вкладка — Упражнения"
            )
            assertTrue(
                analyticsProvider.events.isEmpty(),
                "Без selectTab событий аналитики нет"
            )

            // When
            viewModel.selectTab(ExercisesTab.CATEGORIES)
            advanceUntilIdle()

            // Then
            val after = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                ExercisesTab.CATEGORIES,
                after.selectedTab,
                "selectTab(CATEGORIES) отражается в Success.selectedTab"
            )
            assertEquals(
                listOf(
                    AnalyticsEvent.UserAction(
                        UserActionType.SELECT_CATEGORIES_TAB,
                        "categories"
                    )
                ),
                analyticsProvider.events,
                "Выбор вкладки — user_action SELECT_CATEGORIES_TAB (value = categories)"
            )
        }

    /**
     * Реактивный список категорий из репозитория попадает в
     * `Success.categories` (spec «Список упражнений реактивен» —
     * подписка на `categories()`).
     */
    @Test
    fun categories_fromRepository_reactivelyReflectedInUiState() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — в репозитории появилась кастомная категория
            repository.categoriesFlow.value = listOf(push, shoulders)
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                listOf(push, shoulders),
                state.categories,
                "categories реактивно приезжают из repository.categories()"
            )
        }

    /**
     * Пре-чек удаления (spec «Алерт удаления категории и пре-чек
     * использования»): имя категории в `categoryNamesWithLinkedExercises()`
     * → снекбар `UsedByExercises` БЕЗ открытия алерта
     * (`pendingCategoryDeleteId == null`), категория не удаляется.
     */
    @Test
    fun requestCategoryDelete_whenNameInUse_thenErrorWithoutDialog_andRepositoryUntouched() =
        runTest(testDispatcher) {
            // Given — на PUSH ссылается упражнение
            repository.categoryNamesInUse.value = setOf("PUSH")
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.requestCategoryDelete(push)
            advanceUntilIdle()

            // Then — снекбар вместо алерта
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                DeleteCategoryError.UsedByExercises,
                state.categoryDeleteError,
                "Используемая категория — снекбар UsedByExercises"
            )
            assertNull(state.pendingCategoryDeleteId, "Алерт НЕ открыт")
            assertNull(repository.lastDeletedCategoryId, "Репозиторий не тронут на пре-чеке")

            // When — сброс ошибки one-shot
            viewModel.onCategoryErrorShown()
            advanceUntilIdle()

            // Then
            assertNull(
                (viewModel.uiState.value as ExercisesUiState.Success).categoryDeleteError,
                "onCategoryErrorShown() сбрасывает categoryDeleteError"
            )
        }

    /**
     * Пре-чек прошёл (имя свободно) → алерт через
     * `pendingCategoryDeleteId`; `cancelCategoryDelete()` сбрасывает.
     */
    @Test
    fun requestCategoryDelete_whenFree_thenPendingSet_and_cancelResets() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.requestCategoryDelete(shoulders)
            advanceUntilIdle()

            // Then
            val afterRequest = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                shoulders.id,
                afterRequest.pendingCategoryDeleteId,
                "Свободная категория — открывается алерт (pendingCategoryDeleteId = id)"
            )
            assertNull(afterRequest.categoryDeleteError, "Ошибки нет")

            // When — отмена
            viewModel.cancelCategoryDelete()
            advanceUntilIdle()

            // Then
            assertNull(
                (viewModel.uiState.value as ExercisesUiState.Success).pendingCategoryDeleteId,
                "cancelCategoryDelete сбрасывает pendingCategoryDeleteId"
            )
        }

    /**
     * `confirmCategoryDelete()` при `Result.success` — pending сброшен,
     * репозиторий вызван с id категории.
     */
    @Test
    fun confirmCategoryDelete_onSuccess_clearsPending_andCallsRepository() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestCategoryDelete(shoulders)
            advanceUntilIdle()

            // When
            viewModel.confirmCategoryDelete()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(state.pendingCategoryDeleteId, "pending сброшен после успеха")
            assertNull(state.categoryDeleteError, "Ошибки нет")
            assertEquals(shoulders.id, repository.lastDeletedCategoryId, "deleteCategory(id) вызван")
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.DELETE_CATEGORY)),
                analyticsProvider.events,
                "Подтверждённое удаление категории — user_action DELETE_CATEGORY"
            )
        }

    /**
     * Safety-net (spec «Ошибки удаления категории — типизированные
     * причины»): `CategoryInUseDeletionException` →
     * `UsedByExercises`; повторное подтверждение после сброса ошибки
     * возможно (one-shot).
     */
    @Test
    fun confirmCategoryDelete_onUsedByExercisesFailure_mapsError_andOneShotResets() =
        runTest(testDispatcher) {
            // Given
            val failure = CategoryInUseDeletionException()
            repository.deleteCategoryResult = Result.failure(failure)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestCategoryDelete(shoulders)
            advanceUntilIdle()

            // When
            viewModel.confirmCategoryDelete()
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(state.pendingCategoryDeleteId, "pending сброшен и при ошибке")
            assertEquals(
                DeleteCategoryError.UsedByExercises,
                state.categoryDeleteError,
                "CategoryInUseDeletionException → UsedByExercises"
            )
            val appError =
                analyticsProvider.events.filterIsInstance<AnalyticsEvent.AppError>().single()
            assertEquals(
                AppErrorOperation.CATEGORY_DELETE,
                appError.operation,
                "Сбой удаления категории — событие AppError(CATEGORY_DELETE)"
            )
            assertSame(failure, appError.throwable, "В событии — итоговое исключение")

            // When — one-shot сброс
            viewModel.onCategoryErrorShown()
            advanceUntilIdle()

            // Then
            assertNull(
                (viewModel.uiState.value as ExercisesUiState.Success).categoryDeleteError,
                "onCategoryErrorShown() сбрасывает categoryDeleteError"
            )
        }

    /**
     * Safety-net: `StandardCategoryDeletionException` →
     * `StandardCategory`; прочее исключение → `Other`.
     */
    @Test
    fun confirmCategoryDelete_onStandardAndOtherFailures_mapsTypedErrors() =
        runTest(testDispatcher) {
            // Given — стандартная категория (safety net)
            repository.deleteCategoryResult =
                Result.failure(StandardCategoryDeletionException())
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestCategoryDelete(push)
            advanceUntilIdle()
            viewModel.confirmCategoryDelete()
            advanceUntilIdle()

            // Then
            assertEquals(
                DeleteCategoryError.StandardCategory,
                (viewModel.uiState.value as ExercisesUiState.Success).categoryDeleteError,
                "StandardCategoryDeletionException → StandardCategory"
            )
            viewModel.onCategoryErrorShown()
            advanceUntilIdle()

            // When — прочая ошибка (например, «категория не найдена»)
            repository.deleteCategoryResult =
                Result.failure(IllegalStateException("Категория не найдена"))
            viewModel.requestCategoryDelete(shoulders)
            advanceUntilIdle()
            viewModel.confirmCategoryDelete()
            advanceUntilIdle()

            // Then
            assertEquals(
                DeleteCategoryError.Other,
                (viewModel.uiState.value as ExercisesUiState.Success).categoryDeleteError,
                "Прочее исключение → Other"
            )
        }

    /**
     * `requestCategoryArchive(id)` при категорийном флаге `false`
     * (default) открывает алерт; флаг УПРАЖНЕНИЙ на категории не
     * влияет (spec «Флаг упражнений не влияет на категории») —
     * `setCategoryArchived` не вызывается.
     */
    @Test
    fun requestCategoryArchive_whenCategoryFlagFalse_thenOpensDialog_evenIfExerciseFlagSet() =
        runTest(testDispatcher) {
            // Given — флаг упражнений включён, категорийный выключен
            every { dataStore.dontShowArchiveDialog } returns flowOf(true)
            every { dataStore.dontShowCategoryArchiveDialog } returns flowOf(false)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.requestCategoryArchive(push.id)
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertEquals(
                push.id,
                state.pendingCategoryArchiveId,
                "Категорийный флаг false — открывается алерт, несмотря на флаг упражнений"
            )
            assertTrue(
                repository.setCategoryArchivedCalls.isEmpty(),
                "setCategoryArchived НЕ вызван на открытии алерта"
            )
            assertTrue(
                analyticsProvider.events.isEmpty(),
                "ARCHIVE_CATEGORY логируется только на подтверждении"
            )

            // When — отмена
            viewModel.cancelCategoryArchive()
            advanceUntilIdle()

            // Then
            assertNull(
                (viewModel.uiState.value as ExercisesUiState.Success).pendingCategoryArchiveId,
                "cancelCategoryArchive сбрасывает pendingCategoryArchiveId"
            )
        }

    /**
     * `requestCategoryArchive(id)` при `dontShowCategoryArchiveDialog
     * == true` — немедленная архивация БЕЗ алерта (spec «Включённый
     * флаг архивирует без алерта»).
     */
    @Test
    fun requestCategoryArchive_whenCategoryFlagTrue_thenArchivesImmediately_withoutDialog() =
        runTest(testDispatcher) {
            // Given
            every { dataStore.dontShowCategoryArchiveDialog } returns flowOf(true)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.requestCategoryArchive(push.id)
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value as ExercisesUiState.Success
            assertNull(state.pendingCategoryArchiveId, "Алерт НЕ открыт")
            assertEquals(
                listOf(push.id to true),
                repository.setCategoryArchivedCalls,
                "setCategoryArchived(id, true) вызван немедленно"
            )
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.ARCHIVE_CATEGORY)),
                analyticsProvider.events,
                "Архивация без алерта — user_action ARCHIVE_CATEGORY"
            )
        }

    /**
     * `confirmCategoryArchive(suppress = true)` — архивация + запись
     * ОТДЕЛЬНОГО категорийного pref (`setDontShowCategoryArchiveDialog`);
     * `suppress = false` pref не пишет.
     */
    @Test
    fun confirmCategoryArchive_callsSetCategoryArchived_and_suppressWritesCategoryPref() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestCategoryArchive(shoulders.id)
            advanceUntilIdle()

            // When
            viewModel.confirmCategoryArchive(suppress = true)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(shoulders.id to true),
                repository.setCategoryArchivedCalls,
                "confirmCategoryArchive вызвал setCategoryArchived(id, true)"
            )
            coVerify(exactly = 1) { dataStore.setDontShowCategoryArchiveDialog(true) }
            coVerify(exactly = 0) { dataStore.setDontShowArchiveDialog(any()) }
            assertNull(
                (viewModel.uiState.value as ExercisesUiState.Success).pendingCategoryArchiveId,
                "pending сброшен после подтверждения"
            )
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.ARCHIVE_CATEGORY)),
                analyticsProvider.events,
                "Подтверждённая архивация категории — user_action ARCHIVE_CATEGORY"
            )

            // When — suppress = false: pref не пишется
            viewModel.requestCategoryArchive(push.id)
            advanceUntilIdle()
            viewModel.confirmCategoryArchive(suppress = false)
            advanceUntilIdle()

            // Then
            coVerify(exactly = 1) { dataStore.setDontShowCategoryArchiveDialog(any()) }
        }

    /**
     * `confirmCategoryArchive()` при `Result.failure` — pending сброшен
     * молча (race — по образцу архивации упражнений, spec «Failure при
     * подтверждении»); повторный запрос возможен.
     */
    @Test
    fun confirmCategoryArchive_onFailure_clearsPending_andAllowsRetry() =
        runTest(testDispatcher) {
            // Given — форсируем failure (race между списком и подтверждением)
            repository.setCategoryArchivedResult =
                Result.failure(IllegalStateException("Категория не найдена"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestCategoryArchive(push.id)
            advanceUntilIdle()

            // When
            viewModel.confirmCategoryArchive()
            advanceUntilIdle()

            // Then — pending сброшен молча (без снекбара), повторный
            // запрос открывает новый pending
            assertNull(
                (viewModel.uiState.value as ExercisesUiState.Success).pendingCategoryArchiveId,
                "Failure сбрасывает pendingCategoryArchiveId"
            )
            repository.setCategoryArchivedResult = Result.success(Unit)
            viewModel.requestCategoryArchive(shoulders.id)
            advanceUntilIdle()
            assertEquals(
                shoulders.id,
                (viewModel.uiState.value as ExercisesUiState.Success).pendingCategoryArchiveId,
                "Повторный requestCategoryArchive открывает новый pending"
            )
        }
}
