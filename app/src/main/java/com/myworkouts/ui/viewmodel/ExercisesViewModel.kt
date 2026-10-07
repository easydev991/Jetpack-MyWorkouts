package com.myworkouts.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.AppErrorOperation
import com.myworkouts.analytics.UserActionType
import com.myworkouts.crash.CrashlyticsHelper
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.CategoryInUseDeletionException
import com.myworkouts.data.repository.LastActiveCategoryException
import com.myworkouts.data.repository.LastActiveExerciseException
import com.myworkouts.data.repository.LinkedEntriesDeletionException
import com.myworkouts.data.repository.StandardCategoryDeletionException
import com.myworkouts.data.repository.StandardExerciseDeletionException
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.DeleteCategoryError
import com.myworkouts.domain.model.DeleteExerciseError
import com.myworkouts.domain.model.Exercise
import com.myworkouts.ui.state.ExercisesTab
import com.myworkouts.ui.state.ExercisesUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Тег для [Log.e] в failure-ветках [confirmDelete] (design Decision 3). */
private const val LOG_TAG: String = "ExercisesViewModel"

/**
 * ViewModel экрана «Упражнения» (вкладка каталога).
 *
 * Список приходит реактивно из [WorkoutDataRepository.exercises]
 * с пустым запросом — весь каталог (exercises-tab, design Decision 1;
 * НЕ разовый `getAllExercises()` — тот для экспорта/импорта). Благодаря
 * реактивности будущие создание/удаление упражнений обновляют список
 * без действий экрана и без ручного refresh.
 *
 * Удаление кастомного упражнения — двухшаговое (дизайн `MainScreen` +
 * edit-delete-exercise Decision 3, 8): `requestDelete(item)` →
 * pre-check FK → либо снекбар без диалога (`HasLinkedEntries`), либо
 * `pendingDeleteId = item.id` для алерта; `confirmDelete()` вызывает
 * репозиторий и сбрасывает pending; ошибки репозитория маппит в
 * [DeleteExerciseError]. Алерт не открывается для стандартных упражнений
 * (нет меню — Decision 4) — репозиторий всё равно вернёт
 * [StandardExerciseDeletionException] как safety net, и VM покажет
 * generic снекбар через [DeleteExerciseError.StandardExercise].
 *
 * @property repository Репозиторий данных тренировок
 * @property dataStore Настройки приложения (используется для opt-out
 *   диалога подтверждения архивации; archive-alert-hide, tasks §1.2)
 * @property analyticsProvider Провайдер аналитики: user_action на
 *   подтверждённых действиях, AppError на сбое удаления (design D4, D10)
 */
class ExercisesViewModel(
    private val repository: WorkoutDataRepository,
    private val dataStore: AppSettingsDataStore,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    /**
     * Id упражнения, для которого сейчас показан алерт подтверждения
     * удаления; `null` — алерт закрыт. Обновляется через [requestDelete]
     * / [confirmDelete] / [cancelDelete] (edit-delete-exercise,
     * tasks §2.4, design Decision 3).
     */
    private val pendingDeleteId: MutableStateFlow<Long?> = MutableStateFlow(null)

    /**
     * One-shot причина ошибки удаления для снекбара; `null` — нет ошибки.
     * Сбрасывается в `null` через [onErrorShown] после показа снекбара
     * (design Decision 6 — прецедент `MainViewModel`).
     */
    private val deleteError: MutableStateFlow<DeleteExerciseError?> = MutableStateFlow(null)

    /**
     * One-shot флаг ошибки архивации упражнения для снекбара; `false` —
     * нет ошибки. Выставляется только guard-фейлом
     * (`LastActiveExerciseException`, design Decision 5); race-failure
     * остаётся тихим. Сбрасывается в `false` через [onErrorShown] вместе
     * с [deleteError] (пара каналов на сущность, prevent-from-archiving-all
     * design Decision 4).
     */
    private val archiveError: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * Id упражнения, для которого сейчас показан алерт подтверждения
     * архивации; `null` — алерт закрыт. Обновляется через [requestArchive]
     * / [confirmArchive] / [cancelArchive] (exercises-archive design
     * Decision 7 — паттерн `pendingDeleteId`).
     */
    private val pendingArchiveId: MutableStateFlow<Long?> = MutableStateFlow(null)

    // ---------- Категории (custom-categories, этап 5) ----------

    /**
     * Выбранная вкладка экрана (custom-categories, design Decision 6):
     * переживает поворот экрана (ViewModel), НЕ персистится между
     * запусками.
     */
    private val selectedTab: MutableStateFlow<ExercisesTab> =
        MutableStateFlow(ExercisesTab.EXERCISES)

    /**
     * Id категории, для которой открыт алерт подтверждения удаления;
     * `null` — алерт закрыт (spec «Алерт удаления категории и пре-чек
     * использования»).
     */
    private val pendingCategoryDeleteId: MutableStateFlow<Long?> = MutableStateFlow(null)

    /**
     * One-shot причина ошибки удаления категории для снекбара; `null` —
     * нет ошибки. Сбрасывается через [onCategoryErrorShown] (паттерн
     * [deleteError]).
     */
    private val categoryDeleteError: MutableStateFlow<DeleteCategoryError?> =
        MutableStateFlow(null)

    /**
     * One-shot флаг ошибки архивации категории для снекбара; `false` —
     * нет ошибки. Выставляется только guard-фейлом
     * (`LastActiveCategoryException`); race-failure остаётся тихим.
     * Сбрасывается через [onCategoryErrorShown] вместе с
     * [categoryDeleteError] (паттерн [archiveError]).
     */
    private val categoryArchiveError: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /**
     * Id категории, для которой открыт алерт подтверждения архивации;
     * `null` — алерт закрыт (custom-categories, design Decision 8).
     */
    private val pendingCategoryArchiveId: MutableStateFlow<Long?> = MutableStateFlow(null)

    /**
     * Реактивный признак «в архиве есть упражнения или категории»
     * (exercises-archive design Decision 2 + custom-categories, spec
     * «Кнопка архива в TopAppBar»): значение попадает в
     * [ExercisesUiState.Success.hasArchived] для условной видимости
     * кнопки архива в `TopAppBar`. `WhileSubscribed(defaultSubscriptionTimeoutMs())`
     * — прецедент `MainViewModel`: подписка живёт 5 секунд после отписки,
     * повторные открытия экрана не дёргают SQL.
     */
    private val hasArchived =
        repository
            .hasArchived()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
                initialValue = false
            )

    /**
     * Реактивный список id упражнений с записями для pre-check в
     * [requestDelete] (design Decision 8). Приватное поле VM — в UiState
     * не нужно (UI его не рендерит напрямую, кнопки в меню нет, состояние
     * существует только на время тапа).
     *
     * `SharingStarted.Eagerly` — pre-check читает `value` синхронно на
     * тапе, нужен свежий кеш до подписки `uiState` (race в
     * `WhileSubscribed`: первая эмиссия upstream приходит только
     * после того, как `uiState.collectAsState()` подпишется на
     * `combine`). Upstream — одна SQL-выборка (`GROUP BY exercise_id`),
     * постоянная подписка дешёвая.
     */
    private val exerciseIdsWithLinkedEntries =
        repository
            .exerciseIdsWithLinkedEntries()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptySet()
            )

    /**
     * Реактивный набор имён категорий с упражнениями для pre-check в
     * [requestCategoryDelete] (custom-categories, design Decision 4).
     * Паттерн — [exerciseIdsWithLinkedEntries]: `Eagerly` + синхронное
     * чтение `value` на тапе.
     */
    private val categoryNamesWithLinkedExercises =
        repository
            .categoryNamesWithLinkedExercises()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptySet()
            )

    /**
     * UI State экрана: `Loading` до первой эмиссии репозитория, затем
     * `Success` со списками упражнений и категорий, выбранной вкладкой,
     * pending-состояниями подтверждений, one-shot ошибками и признаком
     * непустого архива.
     *
     * 12 потоков — выше типизированного overload `combine`, поэтому
     * группировка в каскад (design Decision 6, Risks): списочные данные
     * ([ExercisesCatalog]), диалоговые состояния — упражнения
     * ([ExercisesDialogs]) и категории ([ExercisesCategoryDialogs]) —
     * собираются типизированными `combine(≤5)`; финальный типизированный
     * `combine(5)` склеивает группы с каналами архивных ошибок
     * ([archiveError], [categoryArchiveError]) в один `stateIn`.
     */
    val uiState: StateFlow<ExercisesUiState> =
        combine(
            combine(
                repository.exercises(""),
                repository.categories(),
                hasArchived,
                selectedTab
            ) { exercises, categories, archived, tab ->
                ExercisesCatalog(exercises, categories, archived, tab)
            },
            combine(
                pendingDeleteId,
                deleteError,
                pendingArchiveId
            ) { pendingId, error, pendingArchive ->
                ExercisesDialogs(pendingId, error, pendingArchive)
            },
            archiveError,
            combine(
                pendingCategoryDeleteId,
                categoryDeleteError,
                categoryArchiveError,
                pendingCategoryArchiveId
            ) { pendingCategoryId, categoryError, categoryArchive, pendingCategoryArchive ->
                ExercisesCategoryDialogs(
                    pendingCategoryId,
                    categoryError,
                    categoryArchive,
                    pendingCategoryArchive
                )
            },
            categoryArchiveError
        ) { catalog, dialogs, archive, categoryDialogs, categoryArchive ->
            ExercisesUiState.Success(
                exercises = catalog.exercises,
                categories = catalog.categories,
                selectedTab = catalog.selectedTab,
                pendingDeleteId = dialogs.pendingDeleteId,
                deleteError = dialogs.deleteError,
                archiveError = archive,
                pendingArchiveId = dialogs.pendingArchiveId,
                pendingCategoryDeleteId = categoryDialogs.pendingCategoryDeleteId,
                categoryDeleteError = categoryDialogs.categoryDeleteError,
                categoryArchiveError = categoryArchive,
                pendingCategoryArchiveId = categoryDialogs.pendingCategoryArchiveId,
                hasArchived = catalog.hasArchived
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
            initialValue = ExercisesUiState.Loading
        )

    /**
     * Запрашивает удаление упражнения: если на него ссылаются записи
     * (FK) — сразу снекбар [DeleteExerciseError.HasLinkedEntries] без
     * открытия алерта; иначе — открывает алерт через
     * [ExercisesUiState.Success.pendingDeleteId]. Решение синхронное по
     * кешу [exerciseIdsWithLinkedEntries] — без async-обвязки на тапе
     * (design Decision 8).
     *
     * @param item Упражнение из каталога
     */
    fun requestDelete(item: Exercise) {
        if (item.id in exerciseIdsWithLinkedEntries.value) {
            deleteError.value = DeleteExerciseError.HasLinkedEntries
            return
        }
        pendingDeleteId.value = item.id
    }

    /**
     * Закрывает алерт подтверждения удаления без удаления (кнопка
     * «Отмена» / системная «Назад»).
     */
    fun cancelDelete() {
        pendingDeleteId.value = null
    }

    /**
     * Подтверждает удаление из алерта: вызывает
     * [WorkoutDataRepository.deleteExercise]; при `success` сбрасывает
     * pending, при `failure` маппит исключение в [DeleteExerciseError]
     * (design Decision 3):
     * - [LinkedEntriesDeletionException] → `HasLinkedEntries` (race pre-check);
     * - [StandardExerciseDeletionException] → `StandardExercise`
     *   (safety net — UI не должен сюда дойти, Decision 4);
     * - [LastActiveExerciseException] → `LastActive` — guard последнего
     *   активного (prevent-from-archiving-all);
     * - прочее — `Other` (включая «упражнение не найдено» —
     *   race между pre-check и confirm).
     *
     * Каждая failure-ветка логирует текст исключения через [Log.e] для
     * диагностики (Decision 3).
     */
    fun confirmDelete() {
        val id = pendingDeleteId.value ?: return
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.DELETE))
        viewModelScope.launch {
            val result = repository.deleteExercise(id)
            pendingDeleteId.value = null
            result
                .onSuccess {
                    deleteError.value = null
                }.onFailure { error ->
                    val mapped: DeleteExerciseError =
                        when (error) {
                            is LinkedEntriesDeletionException -> DeleteExerciseError.HasLinkedEntries
                            is StandardExerciseDeletionException -> DeleteExerciseError.StandardExercise
                            is LastActiveExerciseException -> DeleteExerciseError.LastActive
                            else -> DeleteExerciseError.Other
                        }
                    CrashlyticsHelper.logException(error, "Удаление упражнения")
                    analyticsProvider.log(
                        AnalyticsEvent.AppError(AppErrorOperation.DELETE_EXERCISE, error)
                    )
                    Log.e(LOG_TAG, "deleteExercise failed for id=$id: ${error.message}", error)
                    deleteError.value = mapped
                }
        }
    }

    /**
     * Сбрасывает пару каналов ошибок упражнения —
     * [ExercisesUiState.Success.deleteError] и
     * [ExercisesUiState.Success.archiveError] — после показа снекбара,
     * one-shot семантика (design Decision 6; пара каналов на сущность,
     * prevent-from-archiving-all design Decision 4).
     */
    fun onErrorShown() {
        deleteError.value = null
        archiveError.value = false
    }

    // ---------- Архивация (exercises-archive, §3.5) ----------

    /**
     * Единый обработчик guard-фейла «последний активный»
     * (prevent-from-archiving-all): Crashlytics + `AppError` + `Log.e` +
     * выставление ошибки в канал UiState. Вызывается только после
     * `is`-проверки конкретного исключения на call-site — прочие фейлы
     * (race «не найдено», design Decision 5) остаются тихими.
     */
    private fun onArchiveGuardFailure(
        error: IllegalStateException,
        operation: AppErrorOperation,
        logMessage: String,
        set: () -> Unit
    ) {
        CrashlyticsHelper.logException(error, logMessage)
        analyticsProvider.log(AnalyticsEvent.AppError(operation, error))
        Log.e(LOG_TAG, "$logMessage, ${error.message}", error)
        set()
    }

    /**
     * Запрашивает архивацию упражнения: открывает алерт подтверждения
     * через [ExercisesUiState.Success.pendingArchiveId]. Доступно и для
     * стандартных, и для пользовательских упражнений (exercises-archive
     * design Decision 4). Аналог [requestDelete], но без pre-check
     * (архивация не имеет FK-ограничений и доступна для всех типов).
     *
     * Opt-out (archive-alert-hide): если пользователь ранее отметил
     * «больше не показывать» и флаг `dontShowArchiveDialog == true` —
     * архивация выполняется сразу (`repository.setArchived(item.id,
     * true)`), диалог НЕ открывается; guard-фейл на этом пути — тот же
     * `archiveError` (design Decision 1 — второй барьер). Иначе —
     * обычный путь через pendingArchiveId (tasks §3.1).
     */
    fun requestArchive(item: Exercise) {
        viewModelScope.launch {
            val dontShow = dataStore.dontShowArchiveDialog.first()
            if (dontShow) {
                // Диалог не показывается: фактическое действие — здесь
                // (design D10 — user_action на точке действия). Suppress-путь
                // доходит до guard'а (design Decision 1 — второй барьер).
                analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.ARCHIVE))
                repository
                    .setArchived(item.id, true)
                    .onFailure { error ->
                        if (error is LastActiveExerciseException) {
                            onArchiveGuardFailure(
                                error,
                                AppErrorOperation.ARCHIVE_EXERCISE,
                                "Архивация упражнения заблокирована: id=${item.id}"
                            ) { archiveError.value = true }
                        }
                    }
            } else {
                pendingArchiveId.value = item.id
            }
        }
    }

    /**
     * Закрывает алерт подтверждения архивации без архивации (кнопка
     * «Отмена» / системная «Назад»).
     */
    fun cancelArchive() {
        pendingArchiveId.value = null
    }

    /**
     * Подтверждает архивацию из алерта: вызывает
     * [WorkoutDataRepository.setArchived] с `isArchived = true`. При
     * `Result.success` и `Result.failure` pending сбрасывается — failure
     * НЕ показывается пользователю (архивация обратима, повторный
     * `requestArchive` достаточен; spec «Failure при подтверждении —
     * алерт закрыт, каталог без изменений»; tasks §3.3) —
     * единственное исключение: guard-фейл последнего активного
     * ([LastActiveExerciseException]) выставляет `archiveError` для
     * снекбара + `AppError(ARCHIVE_EXERCISE)` (prevent-from-archiving-all,
     * design Decision 5). `success` — реактивный `Flow` каталога сам
     * уберёт упражнение из списка.
     *
     * @param suppress `true` — вместе с архивацией записать флаг
     *   «больше не показывать диалог» в [AppSettingsDataStore]
     *   (archive-alert-hide, tasks §3.2). Параметр введён в §1.3;
     *   здесь — поведение записи pref. Default `false` сохраняет
     *   обратную совместимость call-сайта диалога удаления (которому
     *   suppress не нужен).
     */
    fun confirmArchive(suppress: Boolean = false) {
        val id = pendingArchiveId.value ?: return
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.ARCHIVE))
        viewModelScope.launch {
            repository
                .setArchived(id, true)
                .onFailure { error ->
                    // Только guard-фейл ломает тишину (design Decision 5):
                    // race «не найдено» остаётся тихим — повторный запрос.
                    if (error is LastActiveExerciseException) {
                        onArchiveGuardFailure(
                            error,
                            AppErrorOperation.ARCHIVE_EXERCISE,
                            "Архивация упражнения заблокирована: id=$id"
                        ) { archiveError.value = true }
                    }
                }
            pendingArchiveId.value = null
            if (suppress) {
                dataStore.setDontShowArchiveDialog(true)
            }
        }
    }

    // ---------- Категории: вкладка, удаление, архивация (этап 5) ----------

    /**
     * Выбирает вкладку экрана (custom-categories, spec «Выбор вкладки
     * отражается в Success»). Выбор не персистится. Аналитика — имя вкладки
     * в `value` (канон [OverviewViewModel.selectTab], design D11).
     */
    fun selectTab(tab: ExercisesTab) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.SELECT_CATEGORIES_TAB, tab.name.lowercase())
        )
        selectedTab.value = tab
    }

    /**
     * Запрашивает удаление категории: если на неё ссылается хотя бы
     * одно упражнение (активное или архивное) — сразу снекбар
     * [DeleteCategoryError.UsedByExercises] без открытия алерта; иначе
     * открывает алерт через [pendingCategoryDeleteId] (spec «Алерт
     * удаления категории и пре-чек использования», design Decision 4).
     * Решение синхронное по кешу [categoryNamesWithLinkedExercises].
     *
     * @param category Категория из списка вкладки «Категории»
     */
    fun requestCategoryDelete(category: Category) {
        if (category.name in categoryNamesWithLinkedExercises.value) {
            categoryDeleteError.value = DeleteCategoryError.UsedByExercises
            return
        }
        pendingCategoryDeleteId.value = category.id
    }

    /** Закрывает алерт удаления категории без удаления. */
    fun cancelCategoryDelete() {
        pendingCategoryDeleteId.value = null
    }

    /**
     * Подтверждает удаление категории из алерта: вызывает
     * [WorkoutDataRepository.deleteCategory]; при `success` сбрасывает
     * pending (список обновится реактивно), при `failure` маппит
     * исключение в [DeleteCategoryError] (design Decision 4):
     * - [CategoryInUseDeletionException] → `UsedByExercises` (safety net
     *   после пре-чека);
     * - [StandardCategoryDeletionException] → `StandardCategory`
     *   (safety net — UI не предлагает удаление стандартных);
     * - [LastActiveCategoryException] → `LastActive` — guard последней
     *   активной (prevent-from-archiving-all);
     * - прочее — `Other`.
     *
     * Каждая failure-ветка логирует текст исключения через [Log.e].
     */
    fun confirmCategoryDelete() {
        val id = pendingCategoryDeleteId.value ?: return
        // На вызове операции, до/независимо от исхода (design D10 — канон confirmDelete)
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.DELETE_CATEGORY))
        viewModelScope.launch {
            val result = repository.deleteCategory(id)
            pendingCategoryDeleteId.value = null
            result
                .onSuccess {
                    categoryDeleteError.value = null
                }.onFailure { error ->
                    val mapped: DeleteCategoryError =
                        when (error) {
                            is CategoryInUseDeletionException -> DeleteCategoryError.UsedByExercises
                            is StandardCategoryDeletionException -> DeleteCategoryError.StandardCategory
                            is LastActiveCategoryException -> DeleteCategoryError.LastActive
                            else -> DeleteCategoryError.Other
                        }
                    CrashlyticsHelper.logException(error, "Удаление категории")
                    analyticsProvider.log(
                        AnalyticsEvent.AppError(AppErrorOperation.DELETE_CATEGORY, error)
                    )
                    Log.e(LOG_TAG, "deleteCategory failed for id=$id: ${error.message}", error)
                    categoryDeleteError.value = mapped
                }
        }
    }

    /**
     * Сбрасывает пару каналов ошибок категории —
     * [ExercisesUiState.Success.categoryDeleteError] и
     * [ExercisesUiState.Success.categoryArchiveError] — после показа
     * снекбара, one-shot семантика (паттерн [onErrorShown]).
     */
    fun onCategoryErrorShown() {
        categoryDeleteError.value = null
        categoryArchiveError.value = false
    }

    /**
     * Запрашивает архивацию категории: при
     * `dataStore.dontShowCategoryArchiveDialog == true` — немедленно
     * `repository.setCategoryArchived(id, true)` без алерта; guard-фейл
     * на этом пути — тот же `categoryArchiveError` (design Decision 1 —
     * второй барьер); иначе открывает алерт через
     * [pendingCategoryArchiveId] (custom-categories, design Decision 8,
     * паттерн [requestArchive]). Флаг упражнений
     * [AppSettingsDataStore.dontShowArchiveDialog] НЕ читается — ключи
     * независимы.
     *
     * @param id Идентификатор категории из списка вкладки «Категории»
     */
    fun requestCategoryArchive(id: Long) {
        viewModelScope.launch {
            val dontShow = dataStore.dontShowCategoryArchiveDialog.first()
            if (dontShow) {
                // Диалог не показывается: фактическое действие — здесь
                // (design D10 — канон requestArchive). Suppress-путь доходит
                // до guard'а (design Decision 1 — второй барьер).
                analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.ARCHIVE_CATEGORY))
                repository
                    .setCategoryArchived(id, true)
                    .onFailure { error ->
                        if (error is LastActiveCategoryException) {
                            onArchiveGuardFailure(
                                error,
                                AppErrorOperation.ARCHIVE_CATEGORY,
                                "Архивация категории заблокирована: id=$id"
                            ) { categoryArchiveError.value = true }
                        }
                    }
            } else {
                pendingCategoryArchiveId.value = id
            }
        }
    }

    /** Закрывает алерт архивации категории без архивации. */
    fun cancelCategoryArchive() {
        pendingCategoryArchiveId.value = null
    }

    /**
     * Подтверждает архивацию категории из алерта: вызывает
     * [WorkoutDataRepository.setCategoryArchived] с `isArchived = true`.
     * При любом исходе pending сбрасывается — failure не показывается
     * (архивация обратима, паттерн [confirmArchive]); единственное
     * исключение: guard-фейл последней активной
     * ([LastActiveCategoryException]) выставляет `categoryArchiveError`
     * + `AppError(ARCHIVE_CATEGORY)` (design Decision 5).
     *
     * @param suppress `true` — вместе с архивацией записать отдельный
     *   категорийный флаг «больше не показывать»
     *   ([AppSettingsDataStore.setDontShowCategoryArchiveDialog]).
     */
    fun confirmCategoryArchive(suppress: Boolean = false) {
        val id = pendingCategoryArchiveId.value ?: return
        // На вызове операции, до/независимо от исхода (design D10 — канон confirmArchive)
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.ARCHIVE_CATEGORY))
        viewModelScope.launch {
            repository
                .setCategoryArchived(id, true)
                .onFailure { error ->
                    // Только guard-фейл ломает тишину (design Decision 5).
                    if (error is LastActiveCategoryException) {
                        onArchiveGuardFailure(
                            error,
                            AppErrorOperation.ARCHIVE_CATEGORY,
                            "Архивация категории заблокирована: id=$id"
                        ) { categoryArchiveError.value = true }
                    }
                }
            pendingCategoryArchiveId.value = null
            if (suppress) {
                dataStore.setDontShowCategoryArchiveDialog(true)
            }
        }
    }

    companion object {
        /**
         * Factory для создания [ExercisesViewModel]. Ручной DI вместо Hilt,
         * по образцу [MainViewModel.factory].
         *
         * @param repository Репозиторий данных тренировок
         * @param dataStore Настройки приложения (для opt-out диалога
         *   архивации, archive-alert-hide, tasks §1.2)
         * @param analyticsProvider Провайдер аналитики
         * @return Factory для создания ViewModel
         */
        fun factory(
            repository: WorkoutDataRepository,
            dataStore: AppSettingsDataStore,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ExercisesViewModel(repository, dataStore, analyticsProvider) }
            }
    }
}

/**
 * Списочные данные Success для каскада [ExercisesViewModel.uiState]
 * (design Decision 6 — группировка потоков): каталог, категории,
 * признак непустого архива и выбранная вкладка.
 */
private data class ExercisesCatalog(
    val exercises: List<Exercise>,
    val categories: List<Category>,
    val hasArchived: Boolean,
    val selectedTab: ExercisesTab
)

/**
 * Диалоговые состояния Success (упражнения) для каскада
 * [ExercisesViewModel.uiState]: pending-подтверждения и one-shot ошибка
 * удаления упражнения.
 */
private data class ExercisesDialogs(
    val pendingDeleteId: Long?,
    val deleteError: DeleteExerciseError?,
    val pendingArchiveId: Long?
)

/**
 * Диалоговые состояния Success (категории) для каскада
 * [ExercisesViewModel.uiState]: pending-подтверждения и one-shot ошибка
 * удаления категории.
 */
private data class ExercisesCategoryDialogs(
    val pendingCategoryDeleteId: Long?,
    val categoryDeleteError: DeleteCategoryError?,
    val categoryArchiveError: Boolean,
    val pendingCategoryArchiveId: Long?
)
