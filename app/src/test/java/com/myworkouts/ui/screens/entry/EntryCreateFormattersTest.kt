package com.myworkouts.ui.screens.entry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * JVM-тесты чистых функций форматирования и конвертации даты/времени
 * экрана создания записи (design.md Decisions 4–5).
 *
 * `formatDuration` получает слово «час» через лямбду: в JVM-тесте —
 * русские формы, в UI — `pluralStringResource`. «мин»/«сек» —
 * локализуемые сокращения без склонения.
 */
class EntryCreateFormattersTest {
    /** Русские формы «час/часа/часов» для теста (в UI — plurals-ресурс). */
    private fun ruHours(count: Int): String =
        when {
            count % 10 == 1 && count % 100 != 11 -> "$count час"
            count % 10 in 2..4 && count % 100 !in 12..14 -> "$count часа"
            else -> "$count часов"
        }

    @Test
    fun format_duration_when_seconds_only_then_seconds() {
        assertEquals("5 сек", formatDuration(5, ::ruHours, "мин", "сек"))
        assertEquals("45 сек", formatDuration(45, ::ruHours, "мин", "сек"))
    }

    @Test
    fun format_duration_when_exact_minutes_then_minutes_without_seconds() {
        assertEquals("1 мин", formatDuration(60, ::ruHours, "мин", "сек"))
    }

    @Test
    fun format_duration_when_minutes_and_seconds_then_both() {
        assertEquals("2 мин 30 сек", formatDuration(150, ::ruHours, "мин", "сек"))
    }

    @Test
    fun format_duration_when_exact_hour_then_hour_word() {
        assertEquals("1 час", formatDuration(3600, ::ruHours, "мин", "сек"))
        assertEquals("2 часа", formatDuration(7200, ::ruHours, "мин", "сек"))
    }

    @Test
    fun format_duration_when_hour_minutes_and_seconds_then_all_parts() {
        assertEquals(
            "1 час 1 мин 5 сек",
            formatDuration(3665, ::ruHours, "мин", "сек")
        )
    }

    @Test
    fun local_date_to_picker_millis_and_back_roundtrip() {
        // Given — DatePicker работает в UTC
        val date = LocalDate.of(2024, 7, 15)

        // When
        val millis = localDateToPickerMillis(date)
        val back = pickerMillisToLocalDate(millis)

        // Then — конвертация без смещения даты независимо от системной зоны
        assertEquals(date, back)
        assertEquals(
            Instant.parse("2024-07-15T00:00:00Z").toEpochMilli(),
            millis,
            "Полночь выбранной даты в UTC"
        )
    }

    @Test
    fun entry_date_millis_when_fixed_zone_then_combines_date_and_time() {
        // Given — фиксированная зона (системная в рантайме, в тесте — явная)
        val zone = ZoneId.of("Europe/Moscow")

        // When — 14:30 10 июля 2024 в Москве (UTC+3) = 11:30 UTC
        val millis = entryDateMillis(LocalDate.of(2024, 7, 10), 14, 30, zone)

        // Then
        assertEquals(
            Instant.parse("2024-07-10T11:30:00Z").toEpochMilli(),
            millis,
            "LocalDate + час/минута → epoch millis в переданной зоне"
        )
    }
}
