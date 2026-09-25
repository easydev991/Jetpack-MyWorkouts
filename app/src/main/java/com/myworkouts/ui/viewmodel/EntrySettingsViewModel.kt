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
import com.myworkouts.domain.model.EntryField
import com.myworkouts.ui.state.EntrySettingsUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel экрана «Настроить экран»: видимость опциональных полей
 * формы записи (entry-settings-screen). Читает
 * [AppSettingsDataStore.entryFieldVisibility], сохраняет переключения
 * через [setFieldVisible].
 *
 * @property dataStore DataStore настроек приложения
 * @property analyticsProvider Провайдер аналитики
 */
class EntrySettingsViewModel(
    private val dataStore: AppSettingsDataStore,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    companion object {
        /**
         * Factory для создания [EntrySettingsViewModel]. Ручной DI вместо Hilt.
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
                initializer { EntrySettingsViewModel(dataStore, analyticsProvider) }
            }
    }

    /**
     * UI State экрана: карта видимости полей из DataStore. Отсутствие
     * ключей (первый запуск) — дефолт all-true из [EntrySettingsUiState].
     */
    val uiState: StateFlow<EntrySettingsUiState> =
        dataStore.entryFieldVisibility
            .map { visibility -> EntrySettingsUiState(visibility = visibility) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
                initialValue = EntrySettingsUiState()
            )

    /**
     * Переключает видимость поля: аналитика (`<поле>_on`/`<поле>_off`,
     * design D6), затем запись в DataStore.
     *
     * @param field Поле формы
     * @param visible Показывать ли поле
     */
    fun setFieldVisible(
        field: EntryField,
        visible: Boolean
    ) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(
                UserActionType.TOGGLE_ENTRY_FIELD_VISIBILITY,
                "${field.name.lowercase()}_${if (visible) "on" else "off"}"
            )
        )
        viewModelScope.launch { dataStore.setFieldVisible(field, visible) }
    }

    /**
     * Включает все поля: ровно один общий ивент
     * [UserActionType.ENABLE_ALL_ENTRY_FIELDS] до записи — отдельные
     * toggle-ивенты не логируются (design D6); запись — один
     * `edit` всех ключей ([AppSettingsDataStore.setAllFieldsVisible]).
     */
    fun enableAll() {
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.ENABLE_ALL_ENTRY_FIELDS))
        viewModelScope.launch { dataStore.setAllFieldsVisible() }
    }
}
