package com.myworkouts.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.myworkouts.R
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.domain.usecase.StatsWindow
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Подпись дат окна периода статистики (add-stats-period-pager, design
 * D6): «День» — дата дня в формате `dd.MM.yyyy` (числовой формат от
 * локали не зависит); «Неделя»/«Месяц»/«Год» — диапазон дат окна:
 * обе границы в одном месяце и году — «6 – 12 ноября» (первая граница —
 * только число), в разных месяцах одного года — «29 октября – 4 ноября»,
 * в разных календарных годах — год в обеих границах («13 ноября 2025 –
 * 12 ноября 2026»). Правило года общее для всех трёх периодов: год
 * появляется в подписи только когда границы окна лежат в разных годах.
 *
 * Названия месяцев — форматная форма ICU (`Month.getDisplayName` с
 * [TextStyle.FULL]): в ru это родительный падеж («ноября»), в en —
 * «November»; standalone-форма («ноябрь») и капитализация, как в
 * `MonthsHeader` (ActivityScreen), к дате с числом не применяются.
 *
 * Локаль и разделитель — параметры: чистая функция тестируется на JVM,
 * UI-обёртка [statsWindowLabelResource] подтягивает ресурсы.
 *
 * @param window Окно периода, обе границы включительно
 * @param period Тип периода окна (DAY — одна дата, остальные — диапазон)
 * @param locale Локаль названий месяцев и числового формата дня
 * @param separator Разделитель диапазона (например, « – »)
 * @return Готовая подпись дат окна
 */
internal fun formatStatsWindowLabel(
    window: StatsWindow,
    period: StatsPeriod,
    locale: Locale,
    separator: String
): String {
    if (period == StatsPeriod.DAY) {
        return window.start.format(DateTimeFormatter.ofPattern("dd.MM.yyyy", locale))
    }
    val yearsDiffer = window.start.year != window.end.year
    val startYear = if (yearsDiffer) " ${window.start.year}" else ""
    val endYear = if (yearsDiffer) " ${window.end.year}" else ""
    val start =
        if (window.start.month == window.end.month && !yearsDiffer) {
            window.start.dayOfMonth.toString()
        } else {
            "${window.start.dayOfMonth} ${window.start.month.getDisplayName(TextStyle.FULL, locale)}$startYear"
        }
    val end = "${window.end.dayOfMonth} ${window.end.month.getDisplayName(TextStyle.FULL, locale)}$endYear"
    return start + separator + end
}

/**
 * UI-обёртка [formatStatsWindowLabel]: локаль — первая из
 * `LocalConfiguration.current.locales`, разделитель — строковый ресурс
 * `overview_months_separator` (одинаков для ru/en).
 *
 * @param window Окно периода, обе границы включительно
 * @param period Тип периода окна
 * @return Готовая подпись дат окна
 */
@Composable
internal fun statsWindowLabelResource(
    window: StatsWindow,
    period: StatsPeriod
): String {
    val locale: Locale = LocalConfiguration.current.locales[0]
    val separator = stringResource(R.string.overview_months_separator)
    return formatStatsWindowLabel(
        window = window,
        period = period,
        locale = locale,
        separator = separator
    )
}
