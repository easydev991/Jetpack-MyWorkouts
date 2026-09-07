package com.myworkouts.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM-тесты чистого форматтера длительности (тесты зеркалируют source —
 * `ui/util/DurationFormatter.kt`).
 *
 * `formatDuration` получает готовый текст часовой части: склонение
 * («час/часа/часов») делает plurals-ресурс, форматтер только расставляет
 * части. «мин»/«сек» — локализуемые сокращения без склонения.
 */
class DurationFormatterTest {
    @Test
    fun format_duration_when_seconds_only_then_seconds() {
        assertEquals("5 сек", formatDuration(5, "", "мин", "сек"))
        assertEquals("45 сек", formatDuration(45, "", "мин", "сек"))
    }

    @Test
    fun format_duration_when_exact_minutes_then_minutes_without_seconds() {
        assertEquals("1 мин", formatDuration(60, "", "мин", "сек"))
    }

    @Test
    fun format_duration_when_minutes_and_seconds_then_both() {
        assertEquals("2 мин 30 сек", formatDuration(150, "", "мин", "сек"))
    }

    @Test
    fun format_duration_when_exact_hour_then_hour_word() {
        assertEquals("1 час", formatDuration(3600, "1 час", "мин", "сек"))
        assertEquals("2 часа", formatDuration(7200, "2 часа", "мин", "сек"))
    }

    @Test
    fun format_duration_when_hour_minutes_and_seconds_then_all_parts() {
        assertEquals(
            "1 час 1 мин 5 сек",
            formatDuration(3665, "1 час", "мин", "сек")
        )
    }
}
