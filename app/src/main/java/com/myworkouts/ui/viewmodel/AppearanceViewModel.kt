package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
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
 */
class AppearanceViewModel(
    private val dataStore: AppSettingsDataStore
) : ViewModel() {
    companion object {
        /**
         * Factory для создания [AppearanceViewModel]. Используется для ручного DI
         * вместо Hilt.
         *
         * @param dataStore DataStore для настроек приложения
         * @return Factory для создания ViewModel
         */
        fun factory(dataStore: AppSettingsDataStore): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { AppearanceViewModel(dataStore) }
            }
    }

    /** UI State экрана. Объединяет поток настроек из DataStore. */
    // ponytail: combine в один stateIn; два отдельных stateIn = два
    // collectAsState в UI + ручной data class-merge. Если появятся
    // независимые подписки на theme/useDynamicColors — переделать на
    // раздельные Flow (см. design.md Decision 8).
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
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = AppearanceUiState()
        )

    /**
     * Обновляет тему приложения. Сохраняет выбор в DataStore.
     *
     * @param theme Новая тема приложения
     */
    fun updateTheme(theme: AppTheme) {
        viewModelScope.launch { dataStore.setTheme(theme) }
    }

    /**
     * Обновляет настройку использования динамических цветов.
     * Сохраняет выбор в DataStore.
     *
     * @param useDynamicColors Использовать динамические цвета
     */
    fun updateDynamicColors(useDynamicColors: Boolean) {
        viewModelScope.launch { dataStore.setUseDynamicColors(useDynamicColors) }
    }
}
