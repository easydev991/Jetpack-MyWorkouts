package com.myworkouts.ui.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM-тесты чистых форматтеров значений (тесты зеркалируют source —
 * `ui/util/DurationFormatter.kt`).
 *
 * `formatDuration` получает готовый текст часовой части: склонение
 * («час/часа/часов») делает plurals-ресурс, форматтер только расставляет
 * части. «мин»/«сек» — локализуемые сокращения без склонения.
 *
 * `formatStatsDurationCompact` — компактная одноединицная форма
 * длительности легенды статистики с адаптивной единицей и округлением.
 * `legendValueText` — сборка значения легенды из текстов повторов и
 * длительности по составу записей.
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

    @Test
    fun format_stats_duration_compact_when_below_minute_then_seconds() {
        assertEquals("59 сек.", formatStatsDurationCompact(59, "сек.", "мин.", "ч.", "дн."))
    }

    @Test
    fun format_stats_duration_compact_when_exact_minute_then_one_minute() {
        assertEquals("1 мин.", formatStatsDurationCompact(60, "сек.", "мин.", "ч.", "дн."))
    }

    @Test
    fun format_stats_duration_compact_when_fractional_minute_then_rounded() {
        assertEquals("2 мин.", formatStatsDurationCompact(90, "сек.", "мин.", "ч.", "дн."))
    }

    @Test
    fun format_stats_duration_compact_when_below_hour_then_minutes() {
        assertEquals("59 мин.", formatStatsDurationCompact(59 * 60, "сек.", "мин.", "ч.", "дн."))
    }

    @Test
    fun format_stats_duration_compact_when_exact_hour_then_one_hour() {
        assertEquals("1 ч.", formatStatsDurationCompact(60 * 60, "сек.", "мин.", "ч.", "дн."))
    }

    @Test
    fun format_stats_duration_compact_when_below_day_then_hours_rounded() {
        assertEquals(
            "24 ч.",
            formatStatsDurationCompact(23 * 3600 + 59 * 60, "сек.", "мин.", "ч.", "дн.")
        )
    }

    @Test
    fun format_stats_duration_compact_when_exact_day_then_one_day() {
        assertEquals("1 дн.", formatStatsDurationCompact(24 * 3600, "сек.", "мин.", "ч.", "дн."))
    }

    @Test
    fun legend_value_text_when_reps_only_then_reps_text() {
        assertEquals("45 повт.", legendValueText(45, 0, "45 повт.", "2 ч.", " · "))
    }

    @Test
    fun legend_value_text_when_duration_only_then_duration_text() {
        assertEquals("2 ч.", legendValueText(0, 7200, "0 повт.", "2 ч.", " · "))
    }

    @Test
    fun legend_value_text_when_mixed_then_both_with_separator() {
        assertEquals("30 повт. · 3 мин.", legendValueText(30, 180, "30 повт.", "3 мин.", " · "))
    }
}
