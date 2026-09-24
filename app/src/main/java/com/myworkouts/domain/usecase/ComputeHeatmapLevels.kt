package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import java.time.LocalDate
import java.time.ZoneId

/**
 * Пороги уровней хитмапа по числу записей дня (overview-screen, design
 * Decision 6). `MagicNumber` подавлен осознанно: числа 1/2/3/5 — сами
 * пороги, единственная правляемая точка; значение уровня = число
 * пройденных порогов.
 */
@Suppress("MagicNumber")
internal val HEATMAP_THRESHOLDS = listOf(1, 2, 3, 5)

/**
 * Ячейка хитмапа (overview-screen, design Decision 6).
 *
 * @property date Календарный день ячейки (включая ведущие пустые до понедельника)
 * @property count Число записей дня; `0` — день вне окна периода или без записей
 * @property level Уровень 0..4: число пройденных порогов [HEATMAP_THRESHOLDS]
 */
data class HeatmapDayData(
    val date: LocalDate,
    val count: Int,
    val level: Int
)

/**
 * Рассчитывает уровни хитмапа (overview-screen, design Decisions 3 и 6):
 * окно фиксированной длины [ActivityPeriod.days] от сегодня назад — сетка
 * всегда заканчивается сегодня. Сетка выровнена на понедельники: стартовая
 * ячейка — понедельник недели, содержащей первый день окна, ведущие дни
 * до начала окна — пустые (0/0). Счётчик дня — число записей (не повторы:
 * универсально для REPS и TIME, семантика «дня тренировки» серии).
 * Уровень = количество порогов [HEATMAP_THRESHOLDS], которые день проходит
 * (0 записей → 0; 1 → 1; 2 → 2; 3–4 → 3; >= 5 → 4).
 */
internal fun computeHeatmapLevels(
    entries: List<EntryWithExercise>,
    period: ActivityPeriod,
    today: LocalDate
): List<HeatmapDayData> {
    val zone = ZoneId.systemDefault()
    val windowStart = today.minusDays((period.days - 1).toLong())
    val gridStart = windowStart.minusDays((windowStart.dayOfWeek.value - 1).toLong())
    val counts =
        entries
            .groupingBy { entryDay(it, zone) }
            .eachCount()
    return generateSequence(gridStart) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { day ->
            val count = if (day.isBefore(windowStart)) 0 else counts[day] ?: 0
            HeatmapDayData(date = day, count = count, level = HEATMAP_THRESHOLDS.count { count >= it })
        }.toList()
}
