package com.myworkouts.ui.theme

import androidx.compose.ui.graphics.Color
import com.myworkouts.domain.model.ExerciseCategory

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/*
 * Цвета экрана «Обзор» (overview-screen, design D10): одна версия на обе
 * темы — в макете категории и зелёные уровни хитмапа одинаковы в
 * dark/light (уровень 0 хитмапа берётся из темы —
 * `surfaceContainerHighest`). Единственное место объявления hex-значений.
 */

/** Донат и легенда: цвета категорий упражнений. */
val CategoryPush = Color(0xFFFF5252)
val CategoryPull = Color(0xFF448AFF)
val CategoryLegs = Color(0xFF69F0AE)
val CategoryCore = Color(0xFFE040FB)
val CategoryCardio = Color(0xFFFFD740)

/** Хитмап: уровни 1..4, от светлого к тёмному — больше записей, темнее ячейка. */
val HeatmapLevel1 = Color(0xFF81C784)
val HeatmapLevel2 = Color(0xFF4CAF50)
val HeatmapLevel3 = Color(0xFF2E7D32)
val HeatmapLevel4 = Color(0xFF1B5E20)

/** Градиент карточки серии тренировок. */
val StreakOrangeStart = Color(0xFFFF5722)
val StreakOrangeEnd = Color(0xFFFF9800)

/**
 * Цвет категории для доната, легенды и оттенков drill-down (design D10):
 * единственное место связи enum → цвет.
 */
val ExerciseCategory.color: Color
    get() =
        when (this) {
            ExerciseCategory.PUSH -> CategoryPush
            ExerciseCategory.PULL -> CategoryPull
            ExerciseCategory.LEGS -> CategoryLegs
            ExerciseCategory.CORE -> CategoryCore
            ExerciseCategory.CARDIO -> CategoryCardio
        }
