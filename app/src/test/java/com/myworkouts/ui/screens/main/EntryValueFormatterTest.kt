package com.myworkouts.ui.screens.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * JVM-тесты форматтера значения записи главного экрана.
 *
 * Локализованный разделитель (« х »/« x ») передаётся параметром —
 * функция тестируется без Android-ресурсов. В UI разделитель берётся
 * из `stringResource`. Значение — готовая к показу строка: повторы
 * или отформатированная длительность (`formatDurationResource`); склейка
 * тестируется отдельно от текста длительности.
 */
class EntryValueFormatterTest {
    /** Локализованный разделитель для теста (ASCII — locale-neutral). */
    private val separator: String = " x "

    @Test
    fun format_entry_value_when_multiple_sets_of_reps_then_sets_x_reps() {
        // Given — 4 подхода по 10 повторов

        // When
        val result =
            formatEntryValue(
                sets = 4,
                value = "10",
                separator = separator
            )

        // Then — «4 x 10»
        assertEquals("4 x 10", result)
    }

    @Test
    fun format_entry_value_when_single_set_of_reps_then_only_value() {
        // Given — 1 подход по 10 повторов

        // When
        val result =
            formatEntryValue(
                sets = 1,
                value = "10",
                separator = separator
            )

        // Then — «10»
        assertEquals("10", result)
    }

    @Test
    fun format_entry_value_when_multiple_sets_of_duration_then_sets_x_duration() {
        // Given — 3 подхода по 20 секунд («20 сек» — готовый текст длительности)

        // When
        val result =
            formatEntryValue(
                sets = 3,
                value = "20 сек",
                separator = separator
            )

        // Then — «3 x 20 сек»
        assertEquals("3 x 20 сек", result)
    }

    @Test
    fun format_entry_value_when_multiple_sets_minutes_and_seconds_then_both_parts() {
        // Given — 4 подхода по 150 секунд

        // When
        val result =
            formatEntryValue(
                sets = 4,
                value = "2 мин 30 сек",
                separator = separator
            )

        // Then — «4 x 2 мин 30 сек»
        assertEquals("4 x 2 мин 30 сек", result)
    }

    @Test
    fun format_entry_value_when_single_set_of_duration_then_only_duration() {
        // Given — 1 подход длительностью 3600 секунд

        // When
        val result =
            formatEntryValue(
                sets = 1,
                value = "1 час",
                separator = separator
            )

        // Then — «1 час» (без разделителя при одном подходе)
        assertEquals("1 час", result)
    }

    @Test
    fun format_entry_value_when_separator_changes_then_output_uses_passed_separator() {
        // Given — русский разделитель « х » (U+0445)

        // When
        val result =
            formatEntryValue(
                sets = 4,
                value = "10",
                separator = " х "
            )

        // Then
        assertEquals("4 х 10", result)
    }

    @Test
    fun format_weight_kg_when_whole_number_then_no_fractional_part() {
        // Given — целый вес 5.0 кг

        // When
        val result = formatWeightKg(weightKg = 5.0)

        // Then — «5», не «5.0» (spec «Дополнительный вес в строке записи»)
        assertEquals("5", result)
    }

    @Test
    fun format_weight_kg_when_fractional_number_then_keeps_fractional_part() {
        // Given — дробный вес 2.5 кг

        // When
        val result = formatWeightKg(weightKg = 2.5)

        // Then — «2.5»
        assertEquals("2.5", result)
    }

    @Test
    fun format_weight_kg_when_two_fractional_digits_then_keeps_both() {
        // Given — вес 12.25 кг (двойная точность, как введено)

        // When
        val result = formatWeightKg(weightKg = 12.25)

        // Then — «12.25» без округления
        assertEquals("12.25", result)
    }
}
