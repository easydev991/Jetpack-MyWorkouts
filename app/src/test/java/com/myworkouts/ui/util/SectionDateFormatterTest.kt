package com.myworkouts.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Тесты [sectionDateLabel] — правила заголовков секций дат главного
 * экрана, переиспользованные обзором (drill-down статистики и рекорды).
 */
class SectionDateFormatterTest {
    private val today = LocalDate.of(2026, 9, 24)
    private val yesterdayLabel = "Вчера"
    private val currentYearFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale("ru"))
    private val archiveFormatter = DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy", Locale("ru"))

    private fun label(date: LocalDate): String? =
        sectionDateLabel(
            date = date,
            today = today,
            yesterdayLabel = yesterdayLabel,
            currentYearFormatter = currentYearFormatter,
            archiveFormatter = archiveFormatter
        )

    @Test
    fun today_returnsNull() {
        assertNull(label(today))
    }

    @Test
    fun yesterday_returnsYesterdayLabel() {
        assertEquals("Вчера", label(today.minusDays(1)))
    }

    @Test
    fun currentYear_usesShortFormat() {
        // 5 сентября 2026 — суббота
        assertEquals("сб, 5 сент.", label(LocalDate.of(2026, 9, 5)))
    }

    @Test
    fun previousYear_usesArchiveFormat() {
        // 26 декабря 2025 — пятница
        assertEquals("пт, 26.12.2025", label(LocalDate.of(2025, 12, 26)))
    }
}
