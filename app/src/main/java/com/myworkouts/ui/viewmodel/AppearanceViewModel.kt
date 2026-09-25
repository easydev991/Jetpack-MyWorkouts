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
import com.myworkouts.domain.model.AppTheme
import com.myworkouts.ui.state.AppearanceUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel для экрана «Оформление приложения». Управляет настройками темы
 * и динамических цветов.
 *
 * Настройки загружаются из DataStore при инициализации, чтобы при каждом
 * запуске приложения выбор пользователя не сбрасывался.
 *
 * @property dataStore DataStore для сохранения настроек
 * @property analyticsProvider Провайдер аналитики
 */
class AppearanceViewModel(
    private val dataStore: AppSettingsDataStore,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    companion object {
        /**
         * Factory для создания [AppearanceViewModel]. Используется для ручного DI
         * вместо Hilt.
         *
         * @param dataStore DataStore для настроек приложения
         * @param analyticsProvider Провайдер аналитики
         * @return Factory для создания ViewModel
         */
        fun factory(
            dataStore: AppSettingsDataStore,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AppearanceViewModel(dataStore, analyticsProvider) }
            }
    }

    /**
     * UI State экрана. Объединяет поток настроек из DataStore.
     *
     * @implNote combine в один stateIn; два отдельных stateIn = два
     * collectAsState в UI + ручной data class-merge. Если появятся
     * независимые подписки на theme/useDynamicColors — переделать на
     * раздельные Flow.
     */
    val uiState: StateFlow<AppearanceUiState> =
        combine(
            dataStore.theme,
            dataStore.useDynamicColors
        ) { theme, useDynamicColors ->
            AppearanceUiState(
                theme = theme,
                useDynamicColors = useDynamicColors
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
            initialValue = AppearanceUiState()
        )

    /**
     * Обновляет тему приложения. Сохраняет выбор в DataStore.
     *
     * @param theme Новая тема приложения
     */
    fun updateTheme(theme: AppTheme) {
        // value — wire-имя темы в lowercase: light/dark/system (design D10)
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.SELECT_THEME, theme.name.lowercase())
        )
        viewModelScope.launch { dataStore.setTheme(theme) }
    }

    /**
     * Обновляет настройку использования динамических цветов.
     * Сохраняет выбор в DataStore.
     *
     * @param useDynamicColors Использовать динамические цвета
     */
    fun updateDynamicColors(useDynamicColors: Boolean) {
        // value — on/off (design D10)
        analyticsProvider.log(
            AnalyticsEvent.UserAction(
                UserActionType.TOGGLE_DYNAMIC_COLORS,
                if (useDynamicColors) "on" else "off"
            )
        )
        viewModelScope.launch { dataStore.setUseDynamicColors(useDynamicColors) }
    }
}
