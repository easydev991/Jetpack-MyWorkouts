package com.myworkouts.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Палитра Material 3: роли light- и dark-схем. Присвоение ролям —
 * в Theme.kt.
 */

// ===== Light =====

val primaryLight = Color(0xFF33618D)
val onPrimaryLight = Color(0xFFFFFFFF)
val primaryContainerLight = Color(0xFFD0E4FF)
val onPrimaryContainerLight = Color(0xFF144974)
val secondaryLight = Color(0xFF32618D)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD0E4FF)
val onSecondaryContainerLight = Color(0xFF144A74)
val tertiaryLight = Color(0xFF695779)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFF1DBFF)
val onTertiaryContainerLight = Color(0xFF514060)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF93000A)
val backgroundLight = Color(0xFFF8F9FF)
val onBackgroundLight = Color(0xFF191C20)
val surfaceLight = Color(0xFFF8F9FF)
val onSurfaceLight = Color(0xFF191C20)
val surfaceVariantLight = Color(0xFFDEE3EB)
val onSurfaceVariantLight = Color(0xFF42474E)
val outlineLight = Color(0xFF73777F)
val outlineVariantLight = Color(0xFFC2C7CF)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2D3135)
val inverseOnSurfaceLight = Color(0xFFEFF1F6)
val inversePrimaryLight = Color(0xFF9ECAFC)
val surfaceDimLight = Color(0xFFD8DAE0)
val surfaceBrightLight = Color(0xFFF8F9FF)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF2F3F9)
val surfaceContainerLight = Color(0xFFECEEF4)
val surfaceContainerHighLight = Color(0xFFE6E8EE)
val surfaceContainerHighestLight = Color(0xFFE0E2E8)

// ===== Dark =====

val primaryDark = Color(0xFF9ECAFC)
val onPrimaryDark = Color(0xFF003256)
val primaryContainerDark = Color(0xFF144974)
val onPrimaryContainerDark = Color(0xFFD0E4FF)
val secondaryDark = Color(0xFF9ECAFC)
val onSecondaryDark = Color(0xFF003355)
val secondaryContainerDark = Color(0xFF144A74)
val onSecondaryContainerDark = Color(0xFFD0E4FF)
val tertiaryDark = Color(0xFFD5BEE5)
val onTertiaryDark = Color(0xFF3A2A48)
val tertiaryContainerDark = Color(0xFF514060)
val onTertiaryContainerDark = Color(0xFFF1DBFF)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF101418)
val onBackgroundDark = Color(0xFFE0E2E8)
val surfaceDark = Color(0xFF101418)
val onSurfaceDark = Color(0xFFE0E2E8)
val surfaceVariantDark = Color(0xFF42474E)
val onSurfaceVariantDark = Color(0xFFC2C7CF)
val outlineDark = Color(0xFF8C9199)
val outlineVariantDark = Color(0xFF42474E)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE0E2E8)
val inverseOnSurfaceDark = Color(0xFF2D3135)
val inversePrimaryDark = Color(0xFF33618D)
val surfaceDimDark = Color(0xFF101418)
val surfaceBrightDark = Color(0xFF36393E)
val surfaceContainerLowestDark = Color(0xFF0B0E12)
val surfaceContainerLowDark = Color(0xFF191C20)
val surfaceContainerDark = Color(0xFF1D2024)
val surfaceContainerHighDark = Color(0xFF272A2F)
val surfaceContainerHighestDark = Color(0xFF32353A)

/*
 * Цвета экрана «Обзор» (overview-screen): одна версия на обе темы —
 * зелёные уровни хитмапа одинаковы в dark/light (уровень 0 хитмапа берётся
 * из темы — `surfaceContainerHighest`). Цвета категорий живут в модели
 * (`colorArgb`, custom-categories design Decision 2/10) — здесь их нет.
 */

/** Хитмап: уровни 1..4, от светлого к тёмному — больше записей, темнее ячейка. */
val HeatmapLevel1 = Color(0xFF81C784)
val HeatmapLevel2 = Color(0xFF4CAF50)
val HeatmapLevel3 = Color(0xFF2E7D32)
val HeatmapLevel4 = Color(0xFF1B5E20)

/** Градиент карточки серии тренировок. */
val StreakOrangeStart = Color(0xFFFF5722)
val StreakOrangeEnd = Color(0xFFFF9800)
