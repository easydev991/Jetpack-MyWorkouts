package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.domain.crash.CrashReporter
import com.myworkouts.domain.repository.WorkoutDataRepository
import com.myworkouts.ui.state.EntryDetailUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel экрана деталей записи (workout-entry-detail-screen, design
 * Decision 2/4).
 *
 * Данные записи приходят реактивно из [WorkoutDataRepository.entryById]:
 * обновление/удаление записи приходит следующей эмиссией потока без
 * пере-подписки. `null` от потока («запись не найдена») и `Loading`
 * различаются состояниями [EntryDetailUiState] — см. design Decision 3.
 *
 * Удаление — трёхшаговое, как в [MainViewModel]: [requestDelete]
 * выставляет `pendingDelete` (UI показывает алерт), [confirmDelete]
 * вызывает [WorkoutDataRepository.deleteEntry], [cancelDelete] снимает
 * pending без удаления.
 *
 * @property repository Репозиторий данных тренировок
 * @property analyticsProvider Провайдер аналитики
 * @property crashReporter репортер сбоев: failure-ветка удаления записи
 * @property entryId Идентификатор отображаемой записи (design Decision 2:
 *   фиксирован на время жизни экрана — это параметр маршрута)
 */
class EntryDetailViewModel(
    private val repository: WorkoutDataRepository,
    private val analyticsProvider: AnalyticsProvider,
    private val crashReporter: CrashReporter,
    private val entryId: Long
) : ViewModel() {
    /** Транзитное: алерт подтверждения удаления открыт. */
    private val pendingDeleteInternal = MutableStateFlow(false)

    /** UI-флаг открытого алерта удаления. */
    val pendingDelete: StateFlow<Boolean> = pendingDeleteInternal.asStateFlow()

    /**
     * UI State экрана: `Loading` до первой эмиссии, `Success` с данными
     * записи, `Error` если поток эмитит `null` (запись не найдена).
     */
    val uiState: StateFlow<EntryDetailUiState> =
        repository
            .entryById(entryId)
            .map { entry ->
                entry?.let(EntryDetailUiState::Success) ?: EntryDetailUiState.Error
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
                initialValue = EntryDetailUiState.Loading
            )

    /**
     * Запрашивает удаление записи: открывает алерт подтверждения
     * (design Decision 4).
     */
    fun requestDelete() {
        pendingDeleteInternal.update { true }
    }

    /**
     * Подтверждает удаление: вызывает [WorkoutDataRepository.deleteEntry]
     * и при успехе вызывает [onDeleted] (навигация «назад» — design
     * Decision 4). При провале репозитория алерт остаётся открытым,
     * [onDeleted] не вызывается, сбой уходит в [crashReporter] — как в
     * [MainViewModel.confirmDelete].
     *
     * @param onDeleted навигационный колбэк после успешного удаления
     */
    fun confirmDelete(onDeleted: () -> Unit) {
        // Действие на вызове операции, до/независимо от исхода (design D10)
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.DELETE))
        viewModelScope.launch {
            repository
                .deleteEntry(entryId)
                .onSuccess {
                    pendingDeleteInternal.update { false }
                    onDeleted()
                }
                // Провал не виден в UI (алерт остаётся для повтора),
                // поэтому сбой должен дойти до телеметрии — как в
                // MainViewModel.confirmDelete.
                .onFailure { error ->
                    crashReporter.logException(error, "Удаление записи")
                }
        }
    }

    /** Отменяет удаление: закрывает алерт, репозиторий не трогается. */
    fun cancelDelete() {
        pendingDeleteInternal.update { false }
    }

    companion object {
        /**
         * Factory для создания [EntryDetailViewModel]. Ручной DI вместо Hilt,
         * по образцу [EntryCreateViewModel.factory].
         *
         * @param repository Репозиторий данных тренировок
         * @param analyticsProvider Провайдер аналитики
         * @param crashReporter репортер сбоев
         * @param entryId Id отображаемой записи (параметр маршрута)
         * @return Factory для создания ViewModel
         */
        fun factory(
            repository: WorkoutDataRepository,
            analyticsProvider: AnalyticsProvider,
            crashReporter: CrashReporter,
            entryId: Long
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    EntryDetailViewModel(repository, analyticsProvider, crashReporter, entryId)
                }
            }
    }
}
