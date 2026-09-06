package com.myworkouts.ui.screens.main

import com.myworkouts.domain.model.EntryListItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * JVM-тесты группировки записей главного экрана по календарной дате
 * (design Decision 1 «Контракт группировки», Decision 3 — фиксированная
 * `zone` вместо системных настроек).
 *
 * Конвертация epoch millis → `LocalDate` выполняется в фиксированной зоне,
 * поэтому граница полуночи проверяется детерминированно. Порядок входного
 * списка не влияет на результат: группировщик сам сортирует вход по
 * `date` убыванием перед `groupBy`.
 */
class EntryDateGrouperTest {
    /** Фиксированная зона теста — без привязки к машине (design Decision 3). */
    private val zone: ZoneId = ZoneId.of("UTC")

    @Test
    fun group_entries_when_empty_list_then_returns_no_sections() {
        // Given — пустой список записей

        // When
        val result = groupEntriesByDate(entries = emptyList(), zone = zone)

        // Then — секций нет
        assertTrue(result.isEmpty(), "Пустой список должен давать ноль секций")
    }

    @Test
    fun group_entries_when_single_entry_then_single_section_with_entry_date() {
        // Given — одна запись за 6 сентября 2026, 10:00 UTC
        val entry = entry(id = 1, date = millis(2026, 9, 6, 10, 0))

        // When
        val result = groupEntriesByDate(entries = listOf(entry), zone = zone)

        // Then — одна секция с датой записи и этой записью внутри
        assertEquals(1, result.size, "Должна быть ровно одна секция")
        assertEquals(LocalDate.of(2026, 9, 6), result.firstOrNull()?.date, "Дата секции — дата записи")
        assertEquals(listOf(entry), result.firstOrNull()?.items, "Запись попадает в свою секцию")
    }

    @Test
    fun group_entries_when_same_date_then_single_section_with_items_descending() {
        // Given — две записи одного дня: 10:00 и 08:00 UTC
        val later = entry(id = 1, date = millis(2026, 9, 6, 10, 0))
        val earlier = entry(id = 2, date = millis(2026, 9, 6, 8, 0))

        // When
        val result =
            groupEntriesByDate(entries = listOf(later, earlier), zone = zone)

        // Then — одна секция, внутри записи по убыванию времени
        assertEquals(1, result.size, "Записи одного дня — одна секция")
        assertEquals(listOf(later, earlier), result.firstOrNull()?.items, "Поздняя запись выше ранней")
    }

    @Test
    fun group_entries_when_different_dates_then_sections_descending() {
        // Given — записи за 6 и 5 сентября
        val todayEntry = entry(id = 1, date = millis(2026, 9, 6, 10, 0))
        val yesterdayEntry = entry(id = 2, date = millis(2026, 9, 5, 10, 0))

        // When
        val result =
            groupEntriesByDate(
                entries = listOf(yesterdayEntry, todayEntry),
                zone = zone
            )

        // Then — секции по убыванию даты: 6 сентября выше 5 сентября
        assertEquals(
            listOf(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 5)),
            result.map { it.date },
            "Секции идут по убыванию даты"
        )
    }

    @Test
    fun group_entries_when_midnight_boundary_then_entries_in_different_sections() {
        // Given — запись за 23:59 вчера и 00:01 сегодня (граница полуночи,
        // конвертация millis → LocalDate в фиксированной зоне UTC)
        val beforeMidnight = entry(id = 1, date = millis(2026, 9, 5, 23, 59))
        val afterMidnight = entry(id = 2, date = millis(2026, 9, 6, 0, 1))

        // When
        val result =
            groupEntriesByDate(
                entries = listOf(beforeMidnight, afterMidnight),
                zone = zone
            )

        // Then — разные секции: 6 сентября (сегодня) и 5 сентября (вчера)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 5)),
            result.map { it.date },
            "Полночь разделяет записи по разным секциям"
        )
        assertEquals(
            listOf(afterMidnight),
            result.firstOrNull()?.items,
            "Ночная запись сегодняшнего дня — в секции 6 сентября"
        )
        assertEquals(
            listOf(beforeMidnight),
            result.lastOrNull()?.items,
            "Запись 23:59 — в секции 5 сентября"
        )
    }

    @Test
    fun group_entries_when_unordered_input_then_sections_and_items_strictly_descending() {
        // Given — вход в произвольном порядке (design Decision 1:
        // контракт не зависит от порядка входного списка)
        val morning = entry(id = 1, date = millis(2026, 9, 6, 8, 0))
        val noon = entry(id = 2, date = millis(2026, 9, 6, 12, 0))
        val yesterdayNoon = entry(id = 3, date = millis(2026, 9, 5, 12, 0))
        val yesterdayEvening = entry(id = 4, date = millis(2026, 9, 5, 20, 0))
        val shuffledInput = listOf(morning, yesterdayNoon, noon, yesterdayEvening)

        // When
        val result = groupEntriesByDate(entries = shuffledInput, zone = zone)

        // Then — секции и записи строго по убыванию date
        assertEquals(
            listOf(noon, morning),
            result.firstOrNull()?.items,
            "В первой секции (6 сентября) записи по убыванию времени"
        )
        assertEquals(
            listOf(yesterdayEvening, yesterdayNoon),
            result.lastOrNull()?.items,
            "Во второй секции (5 сентября) записи по убыванию времени"
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 5)),
            result.map { it.date },
            "Секции по убыванию даты независимо от порядка входа"
        )
    }

    @Test
    fun group_entries_when_result_built_then_no_empty_sections() {
        // Given — записи за два дня (секция без записей невозможна:
        // после удаления последней записи даты секция исчезает целиком)
        val entries =
            listOf(
                entry(id = 1, date = millis(2026, 9, 6, 10, 0)),
                entry(id = 2, date = millis(2026, 9, 5, 10, 0))
            )

        // When
        val result = groupEntriesByDate(entries = entries, zone = zone)

        // Then — ни одной секции с пустым items
        assertTrue(
            result.all { it.items.isNotEmpty() },
            "Пустых секций быть не должно"
        )
    }

    /** Запись-фикстура с фиксированной датой (epoch millis). */
    private fun entry(
        id: Long,
        date: Long
    ): EntryListItem =
        EntryListItem(
            id = id,
            exerciseName = "Жим лёжа",
            sets = 3,
            reps = 10,
            durationSec = null,
            date = date
        )

    /** Epoch millis для указанной даты/времени в фиксированной зоне теста. */
    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()
}
