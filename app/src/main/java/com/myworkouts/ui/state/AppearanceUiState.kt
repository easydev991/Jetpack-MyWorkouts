package com.myworkouts.ui.state

import com.myworkouts.domain.model.AppTheme

/**
 * UI State для экрана «Оформление приложения».
 *
 * @property theme Текущая выбранная тема приложения
 * @property useDynamicColors Использовать динамические цвета (true по умолчанию)
 */
data class AppearanceUiState(
    val theme: AppTheme = AppTheme.SYSTEM,
    val useDynamicColors: Boolean = true
)
