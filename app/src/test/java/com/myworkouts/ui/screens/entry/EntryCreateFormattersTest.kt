package com.myworkouts.ui.screens.entry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * JVM-тесты чистых функций конвертации даты/времени экрана создания записи
 * (design.md Decisions 4–5). Форматтер длительности живёт в `ui/util`,
 * его тесты — `ui/util/DurationFormatterTest`.
 */
class EntryCreateFormattersTest {
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
