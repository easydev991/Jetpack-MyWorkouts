package com.myworkouts.domain.usecase

import java.time.LocalDate

// Пороги видимости периодов в днях от «сегодня» (overview-periods-and-shares,
// design D1; канон значений — дельта спеки overview-screen). Пороги намеренно
// ниже длин окон: «Квартал» виден уже при истории от 31 дня — ровная лестница
// периодов важнее плотности сетки.

/** «Неделя» статистики — история строго раньше сегодняшнего дня. */
private const val STATS_WEEK_THRESHOLD_DAYS = 1L

/** «Месяц» статистики — история не короче недели. */
private const val STATS_MONTH_THRESHOLD_DAYS = 7L

/** «Год» статистики (максимальный период) — история от месяца. */
private const val STATS_YEAR_THRESHOLD_DAYS = 30L

/** «Квартал» хитмапа — история от месяца. */
private const val ACTIVITY_QUARTER_THRESHOLD_DAYS = 30L

/** «Полгода» хитмапа — история от квартала. */
private const val ACTIVITY_HALFYEAR_THRESHOLD_DAYS = 91L

/** «Год» хитмапа — история от полугода. */
private const val ACTIVITY_YEAR_THRESHOLD_DAYS = 182L

/**
 * Видимые периоды пикера «Статистики» по дате самой ранней тренировки
 * (design D1): «День» — всегда; «Неделя» — если [earliest] строго раньше
 * сегодняшнего дня; «Месяц» — если не позднее «сегодня минус 7 дней»;
 * «Год» — если не позднее «сегодня минус 30 дней». Состав и порядок
 * периодов не меняются — только видимость; [earliest] = `null` (пустая БД,
 * защитный случай) — только «День». Порядок результата — порядок enum.
 */
internal fun visibleStatsPeriods(
    earliest: LocalDate?,
    today: LocalDate
): List<StatsPeriod> {
    if (earliest == null) return listOf(StatsPeriod.DAY)
    return listOfNotNull(
        StatsPeriod.DAY,
        StatsPeriod.WEEK.takeIf { earliest <= today.minusDays(STATS_WEEK_THRESHOLD_DAYS) },
        StatsPeriod.MONTH.takeIf { earliest <= today.minusDays(STATS_MONTH_THRESHOLD_DAYS) },
        StatsPeriod.YEAR.takeIf { earliest <= today.minusDays(STATS_YEAR_THRESHOLD_DAYS) }
    )
}

/**
 * Видимые периоды пикера хитмапа по дате самой ранней тренировки
 * (design D1): «Месяц» — всегда; «Квартал» — если [earliest] не позднее
 * «сегодня минус 30 дней»; «Полгода» — минус 91 день; «Год» — минус
 * 182 дня. [earliest] = `null` (защитный случай) — только «Месяц».
 * Порядок результата — порядок enum.
 */
internal fun visibleActivityPeriods(
    earliest: LocalDate?,
    today: LocalDate
): List<ActivityPeriod> {
    if (earliest == null) return listOf(ActivityPeriod.MONTH)
    return listOfNotNull(
        ActivityPeriod.MONTH,
        ActivityPeriod.QUARTER.takeIf { earliest <= today.minusDays(ACTIVITY_QUARTER_THRESHOLD_DAYS) },
        ActivityPeriod.HALFYEAR.takeIf { earliest <= today.minusDays(ACTIVITY_HALFYEAR_THRESHOLD_DAYS) },
        ActivityPeriod.YEAR.takeIf { earliest <= today.minusDays(ACTIVITY_YEAR_THRESHOLD_DAYS) }
    )
}
