package com.myworkouts.domain.usecase

import java.time.LocalDate

/**
 * Окно периода статистики в календарных днях (add-stats-period-pager,
 * design D1): обе границы включительно. Публичный тип — по конвенции
 * домена публичные модели данных (`CategoryTotal`, `CategoryStatsResult`
 * в `ComputeCategoryStats.kt`): границы окна экспонирует публичный
 * `StatsUiState` (ui/state/OverviewUiState.kt), а публичное свойство
 * internal-типа не компилируется. Функции, работающие с окном,
 * остаются `internal`.
 *
 * @property start Первый календарный день окна включительно
 * @property end Последний календарный день окна включительно
 */
data class StatsWindow(
    val start: LocalDate,
    val end: LocalDate
)

/**
 * Окно периода [StatsPeriod] со смещением назад (add-stats-period-pager,
 * design D1): окно смещения `offset` — это
 * `[today − (offset+1)·days + 1, today − offset·days]`. Смещение 0 —
 * актуальное окно, заканчивающееся сегодня включительно; смещение 1 —
 * предыдущее окно той же длины [StatsPeriod.days]. Границы — календарные
 * дни (`LocalDate.minusDays`), зона в расчёте не участвует.
 * Extension-функция объявлена в собственном файле — членом enum
 * `StatsPeriod` она не является.
 *
 * @param today Календарный день, от которого отсчитываются окна
 * @param offset Смещение назад в окнах (0 — актуальное окно)
 * @return Окно периода с обеими границами включительно
 */
internal fun StatsPeriod.window(
    today: LocalDate,
    offset: Int = 0
): StatsWindow =
    StatsWindow(
        start = today.minusDays((offset + 1).toLong() * days - 1),
        end = today.minusDays(offset.toLong() * days)
    )
