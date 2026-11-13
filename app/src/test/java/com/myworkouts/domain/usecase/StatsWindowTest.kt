package com.myworkouts.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Unit-тесты окна периода [StatsPeriod.window] (add-stats-period-pager,
 * tasks 1.1–1.2, design D1): окно смещения `offset` — это
 * `[today − (offset+1)·days + 1, today − offset·days]`; смещение 0 —
 * актуальное окно, заканчивающееся сегодня, смещение 1 — предыдущее окно
 * той же длины. Границы — календарные дни (`LocalDate.minusDays`),
 * зона в расчёт не входит.
 */
class StatsWindowTest {
    @Test
    fun window_without_offset_ends_today() {
        // Given — фиксированный «сегодня», неделя (days = 7)
        val today = LocalDate.of(2026, 9, 14)

        // When — offset по умолчанию 0
        val window = StatsPeriod.WEEK.window(today)

        // Then — окно [today − 6, today] включительно
        assertEquals(LocalDate.of(2026, 9, 8), window.start, "Старт окна недели — сегодня минус 6 дней")
        assertEquals(today, window.end, "Актуальное окно заканчивается сегодня включительно")
    }

    @Test
    fun window_offset_one_is_previous_window_of_same_length() {
        // Given — неделя, два соседних смещения
        val today = LocalDate.of(2026, 9, 14)
        val current = StatsPeriod.WEEK.window(today, offset = 0)
        val previous = StatsPeriod.WEEK.window(today, offset = 1)

        // Then — предыдущее окно [today − 13, today − 7] включительно,
        // той же длины и смежное с актуальным
        assertEquals(LocalDate.of(2026, 9, 1), previous.start, "Старт предыдущего окна — сегодня минус 13 дней")
        assertEquals(LocalDate.of(2026, 9, 7), previous.end, "Предыдущее окно заканчивается сегодня минус days")
        assertEquals(
            current.start,
            previous.end.plusDays(1),
            "Окна смежны: предыдущее заканчивается за день до старта актуального"
        )
    }

    @Test
    fun window_boundaries_are_calendar_days_across_month_boundary() {
        // Given — «сегодня» 2026-03-05: окно недели пересекает границу
        // февраля (28 дней в 2026-м) и марта — проверка календарных дней
        // на LocalDate, а не арифметики millis
        val today = LocalDate.of(2026, 3, 5)

        // When
        val current = StatsPeriod.WEEK.window(today, offset = 0)
        val previous = StatsPeriod.WEEK.window(today, offset = 1)

        // Then — [2026-02-27, 2026-03-05] и [2026-02-20, 2026-02-26]
        assertEquals(
            LocalDate.of(2026, 2, 27),
            current.start,
            "Старт окна недели уходит в февраль — границы в календарных днях"
        )
        assertEquals(LocalDate.of(2026, 3, 5), current.end, "Актуальное окно заканчивается сегодня")
        assertEquals(LocalDate.of(2026, 2, 20), previous.start, "Старт предыдущего окна — 2026-02-20")
        assertEquals(LocalDate.of(2026, 2, 26), previous.end, "Предыдущее окно целиком в феврале")
    }
}
