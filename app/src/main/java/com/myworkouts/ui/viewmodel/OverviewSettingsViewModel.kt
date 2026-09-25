package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.ui.state.OverviewSettingsUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel экрана настроек обзора: видимость вкладок обзора
 * (overview-screen, design D9). Копия [EntrySettingsViewModel] с guard:
 * выключение последней включённой вкладки игнорируется целиком — ни
 * аналитики, ни записи (spec «Выключение последней вкладки
 * игнорируется»).
 *
 * @property dataStore DataStore настроек приложения
 * @property analyticsProvider Провайдер аналитики
 */
class OverviewSettingsViewModel(
    private val dataStore: AppSettingsDataStore,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    companion object {
        /**
         * Factory для создания [OverviewSettingsViewModel]. Ручной DI вместо Hilt.
         *
         * @param dataStore DataStore настроек приложения
         * @param analyticsProvider Провайдер аналитики
         * @return Factory для создания ViewModel
         */
        fun factory(
            dataStore: AppSettingsDataStore,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { OverviewSettingsViewModel(dataStore, analyticsProvider) }
            }
    }

    /**
     * UI State экрана: карта видимости вкладок из DataStore. Отсутствие
     * ключей (первый запуск) — дефолт all-true из [OverviewSettingsUiState].
     */
    val uiState: StateFlow<OverviewSettingsUiState> =
        dataStore.overviewTabVisibility
            .map { visibility -> OverviewSettingsUiState(visibility = visibility) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
                initialValue = OverviewSettingsUiState()
            )

    /**
     * Переключает видимость вкладки: guard выключения последней включённой
     * (текущее состояние читается из DataStore — [first], а не из
     * `uiState.value`, которое при отписанных коллекторах держит
     * устаревший initialValue), затем аналитика (`<вкладка>_on|<вкладка>_off`,
     * design D11) и запись в DataStore.
     *
     * @param tab Вкладка обзора
     * @param visible Показывать ли вкладку
     */
    fun setTabVisible(
        tab: OverviewTab,
        visible: Boolean
    ) {
        viewModelScope.launch {
            val enabledCount = dataStore.overviewTabVisibility.first().count { it.value }
            if (!visible && enabledCount <= 1) {
                return@launch
            }
            analyticsProvider.log(
                AnalyticsEvent.UserAction(
                    UserActionType.TOGGLE_OVERVIEW_TAB_VISIBILITY,
                    "${tab.name.lowercase()}_${if (visible) "on" else "off"}"
                )
            )
            dataStore.setOverviewTabVisible(tab, visible)
        }
    }

    /**
     * Включает все вкладки: ровно один общий ивент
     * [UserActionType.ENABLE_ALL_OVERVIEW_TABS] до записи — отдельные
     * toggle-ивенты не логируются (design D9); запись — один `edit` всех
     * ключей ([AppSettingsDataStore.setAllOverviewTabsVisible]).
     */
    fun enableAll() {
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.ENABLE_ALL_OVERVIEW_TABS))
        viewModelScope.launch { dataStore.setAllOverviewTabsVisible() }
    }
}
