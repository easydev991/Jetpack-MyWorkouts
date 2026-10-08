package com.myworkouts.domain.model

/**
 * Перечисление доступных тем приложения.
 *
 * LIGHT — Светлая тема
 * DARK — Тёмная тема
 * SYSTEM — Системная тема (следует настройкам системы)
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Возвращает true, если приложение должно рендериться в тёмной теме.
 *
 * [systemDark] — текущая системная тема (результат `isSystemInDarkTheme()`),
 * используется только если [this] == [AppTheme.SYSTEM]
 * @return true, если приложение должно рендериться в тёмной теме
 *
 * @implNote единая точка истины AppTheme→dark/light. Дублировать
 * inline-when в MainActivity/Theme.kt нельзя — рассинхрон при
 * добавлении новой темы.
 */
fun AppTheme.isDark(systemDark: Boolean): Boolean =
    when (this) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> systemDark
    }
