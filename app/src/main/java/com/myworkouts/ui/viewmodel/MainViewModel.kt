package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.ReviewAutoRequestOutcome
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.ReviewCountersDataStore
import com.myworkouts.data.preferences.ReviewCountersDataStore.Companion.REVIEW_COUNTERS_FAILURE_CONTEXT
import com.myworkouts.domain.crash.CrashReporter
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.ReviewCounters
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.domain.review.ReviewFlowLauncher
import com.myworkouts.domain.review.ReviewFlowOutcome
import com.myworkouts.ui.review.ReviewSessionHolder
import com.myworkouts.ui.state.MainUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * ViewModel главного экрана (вкладка «Тренировки»).
 *
 * Список записей приходит реактивно из [WorkoutDataRepository.entries]
 * (workout-entries-list, spec «Реактивный список записей»). Удаление
 * через long-press меню — три шага: [requestDelete] выставляет
 * `pendingDeleteId` (UI показывает алерт), [confirmDelete] вызывает
 * [WorkoutDataRepository.deleteEntry] (список обновляется сам через
 * реактивный источник — ручного refresh не нужно), [cancelDelete]
 * очищает pending без удаления.
 *
 * UI — «глупый» рендер [MainUiState]; вся логика выбора/удаления —
 * здесь. `pendingDeleteId` живёт в отдельном `MutableStateFlow` и
 * подмешивается в [MainUiState.Success] через `combine`, чтобы
 * обновление списка (новая запись, удаление) не сбрасывало
 * идентификатор открытого алерта до явного подтверждения/отмены.
 *
 * @property repository Репозиторий данных тренировок
 * @property analyticsProvider Провайдер аналитики
 * @property crashReporter репортер сбоев: failure-ветки удаления записи
 *
 * Review-флоу (add-rustore-in-app-review, design D5): [onMainScreenShown]
 * при показе экрана показывает отложенный pending review-майлстоуна —
 * гейт [isReviewEnabled], чтение pending из [reviewCountersDataStore],
 * задержка ~0.8 с, атомарная отметка milestone пройденным со снятием
 * совпадающего pending, запуск через [reviewFlowLauncher]; флаг сессии
 * «авто-запрос выполнялся» — в [reviewSessionHolder]. Ошибки — тихо,
 * без UI и фолбэка.
 *
 * @property isReviewEnabled Гейт review-функциональности (`BuildConfig.RUSTORE_FEATURES`)
 * @property reviewCountersDataStore Хранилище счётчиков review-майлстоунов (process-singleton)
 * @property reviewFlowLauncher Лаунчер нативной формы оценки (flavor-реализация)
 * @property reviewSessionHolder Флаг сессии «авто-запрос выполнялся»
 */
@Suppress("LongParameterList")
class MainViewModel(
    private val repository: WorkoutDataRepository,
    private val analyticsProvider: AnalyticsProvider,
    private val crashReporter: CrashReporter,
    private val isReviewEnabled: Boolean,
    private val reviewCountersDataStore: ReviewCountersDataStore,
    private val reviewFlowLauncher: ReviewFlowLauncher,
    private val reviewSessionHolder: ReviewSessionHolder
) : ViewModel() {
    /** Транзитное: id записи, для которой открыт алерт удаления. */
    private val pendingDeleteId = MutableStateFlow<Long?>(null)

    /** Сериализация повторов [onMainScreenShown] (init + LaunchedEffect) — design D5. */
    private val reviewAutoRequestMutex = Mutex()

    init {
        // Персистентный pending на старте сессии (design D5): ViewModel
        // переживает навигацию, init выполняется один раз за сессию.
        onMainScreenShown()
    }

    /**
     * UI State главного экрана: `Loading` до первой эмиссии
     * репозитория, затем `Success` со списком записей и `pendingDeleteId`.
     */
    val uiState: StateFlow<MainUiState> =
        combine(
            pendingDeleteId,
            repository.entries()
        ) { pendingId, entries ->
            MainUiState.Success(
                entries = entries,
                pendingDeleteId = pendingId
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
            initialValue = MainUiState.Loading
        )

    /**
     * Запрашивает удаление записи: выставляет её id в `pendingDeleteId`,
     * UI открывает алерт подтверждения (design Decision 4).
     */
    fun requestDelete(item: EntryListItem) {
        pendingDeleteId.update { item.id }
    }

    /**
     * Подтверждает удаление: вызывает [WorkoutDataRepository.deleteEntry]
     * для текущего `pendingDeleteId`. Список обновляется сам реактивно;
     * `pendingDeleteId` очищается после успеха — при провале репозитория
     * pending сохраняется, чтобы пользователь мог повторить попытку.
     */
    fun confirmDelete() {
        val id = pendingDeleteId.value ?: return
        // Действие на вызове операции, до/независимо от исхода (design D10)
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.DELETE))
        viewModelScope.launch {
            repository
                .deleteEntry(id)
                .onSuccess {
                    pendingDeleteId.update { null }
                }
                // Провал не виден в UI (pending остаётся для повтора),
                // поэтому сбой должен дойти до телеметрии — как в
                // ExercisesViewModel.confirmDelete.
                .onFailure { error ->
                    crashReporter.logException(error, "Удаление записи")
                }
        }
    }

    /** Отменяет удаление: очищает `pendingDeleteId`, репозиторий не трогается. */
    fun cancelDelete() {
        pendingDeleteId.update { null }
    }

    /**
     * Авто-запрос оценки приложения при показе главного экрана
     * (add-rustore-in-app-review, задача 7.4, design D5).
     *
     * Две точки запуска одного метода: [init] ViewModel (персистентный
     * pending на старте сессии) и `LaunchedEffect(Unit)` в
     * [com.myworkouts.ui.screens.main.MainScreen] при каждом входе на
     * экран (pending, запланированный в той же сессии при создании
     * записи). Повторы сериализуются [reviewAutoRequestMutex]:
     * последовательность, дошедшая до флага второй, видит закрытую
     * сессию и выходит, не доходя до лаунчера.
     *
     * Запуск в [viewModelScope]: уход с экрана в окне задержки показ
     * НЕ отменяет — ViewModel переживает навигацию, SDK-форма системная
     * и закрывается BACK (design D5). Последовательность: гейт
     * [isReviewEnabled] → чтение pending (сбой `IOException` — тихий лог
     * и выход, повтор при следующем возврате на экран) → блокировки
     * holder/pending → задержка [REVIEW_AUTO_REQUEST_DELAY_MS] (даём
     * экрану отрисоваться) → перепроверка pending (изменился/снят —
     * тихий пропуск) → закрытие флага сессии ДО запуска (повторных
     * авто-запросов в сессии не будет даже при ошибке) → отметка
     * milestone пройденным атомарно со снятием совпадающего pending →
     * запуск review-флоу → лог исхода. Ошибки — тихо, без UI и фолбэка.
     */
    fun onMainScreenShown() {
        if (!isReviewEnabled) return
        viewModelScope.launch {
            reviewAutoRequestMutex.withLock {
                runReviewAutoRequest()
            }
        }
    }

    /**
     * Сериализованная последовательность авто-запроса (design D5):
     * чтение pending с блокировками → задержка → перепроверка → показ.
     */
    private suspend fun runReviewAutoRequest() {
        val plannedMilestone = readPlannedMilestone() ?: return
        delay(REVIEW_AUTO_REQUEST_DELAY_MS)
        val confirmedMilestone = readConfirmedMilestone(plannedMilestone) ?: return
        runReviewFlow(confirmedMilestone)
    }

    /**
     * Читает запланированный milestone до задержки. Блокировки holder и
     * отсутствие pending — тоже null (design D5); сбой чтения — тихий
     * лог и null в [readCounters].
     */
    private suspend fun readPlannedMilestone(): Int? =
        readCounters()
            ?.pendingMilestone
            ?.takeIf { !reviewSessionHolder.hasAutoRequestRun }

    /**
     * Перепроверяет pending после задержки: за окно ожидания состояние
     * могло измениться — изменившийся/снятый pending тихо пропускается,
     * повтор при следующем возврате на экран (design D5).
     */
    private suspend fun readConfirmedMilestone(plannedMilestone: Int): Int? =
        readCounters()
            ?.pendingMilestone
            ?.takeIf { it == plannedMilestone }

    /**
     * Читает снимок счётчиков; сбой `IOException` (файл DataStore) —
     * тихий лог через crash-репортер и null без падения (design D5).
     */
    private suspend fun readCounters(): ReviewCounters? =
        try {
            reviewCountersDataStore.counters.first()
        } catch (e: IOException) {
            crashReporter.logException(e, REVIEW_COUNTERS_FAILURE_CONTEXT)
            null
        }

    /**
     * Показ review-флоу: закрытие флага сессии ДО запуска, атомарная
     * отметка milestone пройденным со снятием ТОЛЬКО совпадающего
     * pending (design D3), запуск лаунчера и лог исхода событием
     * review_auto_request. Сбой отметки — тихий выход без запуска.
     */
    private suspend fun runReviewFlow(milestone: Int) {
        reviewSessionHolder.hasAutoRequestRun = true
        try {
            reviewCountersDataStore.markMilestonePassed(milestone)
        } catch (e: IOException) {
            crashReporter.logException(e, REVIEW_COUNTERS_FAILURE_CONTEXT)
            return
        }
        analyticsProvider.log(reviewAutoRequestEvent(reviewFlowLauncher.launch()))
    }

    /** Собирает событие review_auto_request по исходу запуска флоу (design D9). */
    private fun reviewAutoRequestEvent(outcome: ReviewFlowOutcome): AnalyticsEvent =
        when (outcome) {
            is ReviewFlowOutcome.Shown ->
                AnalyticsEvent.ReviewAutoRequest(ReviewAutoRequestOutcome.SHOWN)
            is ReviewFlowOutcome.Error ->
                AnalyticsEvent.ReviewAutoRequest(
                    outcome = ReviewAutoRequestOutcome.ERROR,
                    errorType = outcome.errorType
                )
        }

    companion object {
        /**
         * Задержка перед показом review-флоу после входа на главный экран:
         * даём экрану отрисоваться (design D5, ~0.8 с).
         */
        private const val REVIEW_AUTO_REQUEST_DELAY_MS = 800L

        /**
         * Factory для создания [MainViewModel]. Ручной DI вместо Hilt,
         * по образцу [EntryCreateViewModel.factory].
         *
         * @param repository Репозиторий данных тренировок
         * @param analyticsProvider Провайдер аналитики
         * @param crashReporter репортер сбоев
         * @param isReviewEnabled Гейт review-функциональности (add-rustore-in-app-review)
         * @param reviewCountersDataStore Хранилище счётчиков review-майлстоунов
         * @param reviewFlowLauncher Лаунчер нативной формы оценки
         * @param reviewSessionHolder Флаг сессии «авто-запрос выполнялся»
         * @return Factory для создания ViewModel
         */
        @Suppress("LongParameterList")
        fun factory(
            repository: WorkoutDataRepository,
            analyticsProvider: AnalyticsProvider,
            crashReporter: CrashReporter,
            isReviewEnabled: Boolean,
            reviewCountersDataStore: ReviewCountersDataStore,
            reviewFlowLauncher: ReviewFlowLauncher,
            reviewSessionHolder: ReviewSessionHolder
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    MainViewModel(
                        repository,
                        analyticsProvider,
                        crashReporter,
                        isReviewEnabled,
                        reviewCountersDataStore,
                        reviewFlowLauncher,
                        reviewSessionHolder
                    )
                }
            }
    }
}
