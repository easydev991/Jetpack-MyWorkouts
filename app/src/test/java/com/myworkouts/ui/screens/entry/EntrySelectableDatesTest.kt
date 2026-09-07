package com.myworkouts.ui.screens.entry

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * JVM-тесты границы доступности дат [entrySelectableDates] для пикера
 * (design Decision 2): граница по UTC-millis локальной даты открытия,
 * сам день открытия — ещё доступен.
 */
class EntrySelectableDatesTest {
    /** День открытия экрана для тестов. */
    private val openDate = LocalDate.of(2024, 7, 15)

    @Test
    fun is_selectable_when_utc_millis_of_open_day_then_true() {
        // Given — граница от дня открытия
        val dates = entrySelectableDates(openDate)

        // When/Then — сам день открытия доступен (UTC-полночь пикера)
        assertTrue(
            dates.isSelectableDate(localDateToPickerMillis(openDate)),
            "День открытия (15 июля) должен быть доступен"
        )
    }

    @Test
    fun is_selectable_when_day_after_open_then_false() {
        // Given — граница от дня открытия
        val dates = entrySelectableDates(openDate)

        // When/Then — следующий день недоступен
        assertFalse(
            dates.isSelectableDate(localDateToPickerMillis(openDate.plusDays(1))),
            "День после дня открытия (16 июля) должен быть недоступен"
        )
    }

    @Test
    fun is_selectable_when_day_before_open_then_true() {
        // Given — граница от дня открытия
        val dates = entrySelectableDates(openDate)

        // When/Then — прошлый день доступен
        assertTrue(
            dates.isSelectableDate(localDateToPickerMillis(openDate.minusDays(1))),
            "Прошлый день (14 июля) должен быть доступен"
        )
    }
}
