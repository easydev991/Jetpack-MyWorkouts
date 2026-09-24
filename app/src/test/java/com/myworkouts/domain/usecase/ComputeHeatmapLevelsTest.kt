package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.time.LocalDate
import java.time.ZoneId
import java.util.stream.Stream

/**
 * Unit-тесты [computeHeatmapLevels] (overview-screen, tasks 2.7–2.8,
 * design Decisions 3 и 6): пороги уровней, окно до сегодня, выравнивание
 * сетки на понедельник, дни без записей.
 */
class ComputeHeatmapLevelsTest {
    private val today = LocalDate.of(2026, 9, 14)

    /** Epoch millis записи в полдень заданной даты (однозначный день). */
    private fun millis(date: LocalDate): Long =
        date
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun entry(
        id: Long,
        date: Long
    ): EntryWithExercise =
        EntryWithExercise(
            id = id,
            exerciseId = 1L,
            exerciseName = "Подтягивания",
            category = com.myworkouts.domain.model.ExerciseCategory.PULL,
            measuredBy = com.myworkouts.domain.model.ExerciseMeasuredBy.REPS,
            sets = 3,
            reps = 10,
            durationSec = null,
            date = date
        )

    /** Сценарии порогов: число записей в дне -> уровень 0..4. */
    companion object {
        /** `@MethodSource` требует static. */
        @JvmStatic
        fun thresholdCases(): Stream<Arguments> =
            Stream.of(
                Arguments.of(Named.of("0 записей — уровень 0", 0), 0),
                Arguments.of(Named.of("1 запись — уровень 1", 1), 1),
                Arguments.of(Named.of("2 записи — уровень 2", 2), 2),
                Arguments.of(Named.of("3 записи — уровень 3", 3), 3),
                Arguments.of(Named.of("4 записи — уровень 3", 4), 3),
                Arguments.of(Named.of("5 записей — уровень 4", 5), 4),
                Arguments.of(Named.of("6 записей — уровень 4", 6), 4)
            )
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("thresholdCases")
    fun level_by_thresholds(
        entryCount: Int,
        expectedLevel: Int
    ) {
        // Given — entryCount записей сегодня, период Месяц
        val entries = (1L..entryCount).map { id -> entry(id, millis(today)) }

        // When
        val levels = computeHeatmapLevels(entries, ActivityPeriod.MONTH, today)

        // Then — уровень = число пройденных порогов [1, 2, 3, 5]
        val todayCell =
            checkNotNull(levels.lastOrNull { it.date == today }) { "Ячейка сегодняшнего дня" }
        assertEquals(expectedLevel, todayCell.level, "Уровень по порогам HEATMAP_THRESHOLDS")
    }

    @Test
    fun window_ends_today() {
        // Given — месяцный период и запись сегодня

        // When
        val levels = computeHeatmapLevels(emptyList(), ActivityPeriod.MONTH, today)

        // Then — сетка всегда заканчивается сегодня
        assertEquals(today, levels.last().date, "Последний день сетки — сегодня")
    }

    @Test
    fun grid_starts_on_monday_with_leading_empty_cells() {
        // Given — окно месяца начинается в воскресенье 16.08, today — понедельник

        // When
        val levels = computeHeatmapLevels(emptyList(), ActivityPeriod.MONTH, today)

        // Then — первая ячейка: понедельник недели начала окна (10.08), пустая
        assertEquals(LocalDate.of(2026, 8, 10), levels.first().date, "Сетка выровнена на понедельник")
        assertEquals(0, levels.first().level, "Ведущая ячейка пустая")
        assertEquals(LocalDate.of(2026, 8, 16), levels[6].date, "Первый день окна — 7-я ячейка строки")
    }

    @Test
    fun entries_before_window_not_counted() {
        // Given — записи в ведущей зоне (10–15.08) и в окне (16.08)
        val entries =
            listOf(
                entry(1L, millis(LocalDate.of(2026, 8, 10))),
                entry(2L, millis(LocalDate.of(2026, 8, 15))),
                entry(3L, millis(LocalDate.of(2026, 8, 16)))
            )

        // When
        val levels = computeHeatmapLevels(entries, ActivityPeriod.MONTH, today)

        // Then — вне окна считаются нулями, первый день окна — записью
        assertEquals(0, levels.first().count, "Ведущие дни не в окне")
        val windowFirstDay =
            checkNotNull(levels.lastOrNull { it.date == LocalDate.of(2026, 8, 16) }) {
                "Первый день окна в сетке"
            }
        assertEquals(1, windowFirstDay.count, "Первый день окна считает записи")
        assertEquals(1, windowFirstDay.level)
    }

    @Test
    fun day_without_entries_has_zero_count() {
        // Given — период без записей

        // When
        val levels = computeHeatmapLevels(emptyList(), ActivityPeriod.MONTH, today)

        // Then — все дни без записей: count 0, уровень 0
        assertTrue(
            levels.all { it.count == 0 && it.level == 0 },
            "Дни без записей — пустые ячейки"
        )
    }

    @Test
    fun multiple_entries_same_day_raise_count() {
        // Given — две записи сегодня
        val entries = listOf(entry(1L, millis(today)), entry(2L, millis(today)))

        // When
        val levels = computeHeatmapLevels(entries, ActivityPeriod.MONTH, today)

        // Then — счётчик дней = число записей (не повторов)
        val todayCell =
            checkNotNull(levels.lastOrNull { it.date == today }) { "Ячейка сегодня" }
        assertEquals(2, todayCell.count, "Счётчик — число записей дня")
    }

    @Test
    fun quarter_grid_size_matches_window_and_alignment() {
        // Given — квартал 91 день: окно 16.06..14.09, сетка с пн 15.06

        // When
        val levels = computeHeatmapLevels(emptyList(), ActivityPeriod.QUARTER, today)

        // Then — 92 ячейки (ведущий понедельник + 91 день окна)
        assertEquals(92, levels.size, "Ведущий понедельник + дни окна")
    }
}
