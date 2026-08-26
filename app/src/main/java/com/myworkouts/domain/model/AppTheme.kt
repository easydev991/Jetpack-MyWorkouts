package com.myworkouts.domain.model

/**
 * Перечисление доступных тем приложения.
 *
 * @property LIGHT Светлая тема
 * @property DARK Тёмная тема
 * @property SYSTEM Системная тема (следует настройкам системы)
 */
enum class AppTheme {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Разрешает [AppTheme] в конкретный dark/light для UI.
 *
 * @param systemDark текущее состояние системной темы (от `isSystemInDarkTheme()`);
 * используется только если [this] == [AppTheme.SYSTEM]
 * @return true, если приложение должно рендериться в тёмной теме
 */
// ponytail: единая точка истины AppTheme→dark/light. Дублировать
// inline-when в MainActivity/Theme.kt нельзя — рассинхрон при
// добавлении новой темы (см. design.md Decision 6).
fun AppTheme.isDark(systemDark: Boolean): Boolean =
    when (this) {
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
        AppTheme.SYSTEM -> systemDark
    }
