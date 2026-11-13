package com.myworkouts.ui.util

import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.domain.usecase.StatsWindow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Locale

/**
 * Тесты [formatStatsWindowLabel] — подписи дат окна периода статистики
 * (add-stats-period-pager, design D6): «День» — одна дата, остальные
 * периоды — диапазон дат окна с общим правилом года.
 */
class StatsWindowFormatterTest {
    private fun label(
        period: StatsPeriod,
        start: LocalDate,
        end: LocalDate,
        locale: Locale,
        separator: String
    ): String =
        formatStatsWindowLabel(
            window = StatsWindow(start = start, end = end),
            period = period,
            locale = locale,
            separator = separator
        )

    @Test
    fun dayPeriod_formatsSingleDate() {
        // Окно дня — одна дата (start == end), числовой формат от локали не зависит
        assertEquals(
            "12.11.2026",
            label(
                StatsPeriod.DAY,
                LocalDate.of(2026, 11, 12),
                LocalDate.of(2026, 11, 12),
                Locale("ru"),
                " – "
            )
        )
    }

    @Test
    fun weekRange_sameMonthAndYear_ru() {
        // Обе границы в одном месяце и году — первая граница только числом, без года
        assertEquals(
            "6 – 12 ноября",
            label(
                StatsPeriod.WEEK,
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 12),
                Locale("ru"),
                " – "
            )
        )
    }

    @Test
    fun weekRange_sameMonthAndYear_en() {
        assertEquals(
            "6 – 12 November",
            label(
                StatsPeriod.WEEK,
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 12),
                Locale("en"),
                " – "
            )
        )
    }

    @Test
    fun weekRange_acrossMonthsSameYear_ru() {
        // Переход через месяц одного года — «Неделя» внутри года, без года
        assertEquals(
            "29 октября – 4 ноября",
            label(
                StatsPeriod.WEEK,
                LocalDate.of(2025, 10, 29),
                LocalDate.of(2025, 11, 4),
                Locale("ru"),
                " – "
            )
        )
    }

    @Test
    fun monthRange_acrossMonthsSameYear_ru() {
        // Переход через месяц одного года — «Месяц» внутри года, без года
        assertEquals(
            "29 октября – 4 ноября",
            label(
                StatsPeriod.MONTH,
                LocalDate.of(2025, 10, 29),
                LocalDate.of(2025, 11, 4),
                Locale("ru"),
                " – "
            )
        )
    }

    @Test
    fun yearRange_acrossYears_yearInBothBounds_ru() {
        // Переход через год — год в обеих границах (родительный падеж месяцев ru)
        assertEquals(
            "13 ноября 2025 – 12 ноября 2026",
            label(
                StatsPeriod.YEAR,
                LocalDate.of(2025, 11, 13),
                LocalDate.of(2026, 11, 12),
                Locale("ru"),
                " – "
            )
        )
    }

    @Test
    fun yearWindow_withinSingleCalendarYear_omitsYear() {
        // Окно «Год» (365 дней) целиком в одном календарном году — без года
        assertEquals(
            "2 января – 31 декабря",
            label(
                StatsPeriod.YEAR,
                LocalDate.of(2028, 1, 2),
                LocalDate.of(2028, 12, 31),
                Locale("ru"),
                " – "
            )
        )
    }

    @Test
    fun range_separatorIsPassedParameter() {
        // Разделитель диапазона — параметр функции, а не константа
        assertEquals(
            "6 .. 12 ноября",
            label(
                StatsPeriod.WEEK,
                LocalDate.of(2026, 11, 6),
                LocalDate.of(2026, 11, 12),
                Locale("ru"),
                " .. "
            )
        )
    }
}
