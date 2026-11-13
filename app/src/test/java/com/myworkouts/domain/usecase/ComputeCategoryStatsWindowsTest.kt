package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit-тесты [computeCategoryStatsWindows] (add-stats-period-pager, задачи
 * 1.3–1.4, design D2): раскладка записей по окнам периода одним проходом —
 * смещение окна = отставание записи в календарных днях от сегодня, делённое
 * на длину окна (целочисленно); записи позже сегодня отбрасываются до
 * раскладки; ключи карты — все смещения 0..maxOffset, окно без записей даёт
 * пустой результат, а не отсутствующий ключ. Фикстуры — как в
 * [ComputeCategoryStatsTest]: чистая функция, `today` параметром, зона
 * системная.
 */
class ComputeCategoryStatsWindowsTest {
    private val today = LocalDate.of(2026, 9, 14)

    /** Epoch millis записи в полдень заданной даты (однозначный день). */
    private fun millis(date: LocalDate): Long =
        date
            .atTime(12, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    // Тестовая фабрика: параметр на поле записи, дефолты покрывают типовой
    // случай — свёртывать в объект-параметр не стоит.
    @Suppress("LongParameterList")
    private fun entry(
        id: Long,
        exerciseName: String = "Подтягивания",
        category: String = "PULL",
        measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS,
        date: Long,
        sets: Int = 3,
        reps: Int? = 10,
        durationSec: Int? = null,
        weightKg: Double? = null,
        comment: String? = null
    ): EntryWithExercise =
        EntryWithExercise(
            id = id,
            exerciseId = id,
            exerciseName = exerciseName,
            category = category,
            measuredBy = measuredBy,
            sets = sets,
            reps = reps,
            durationSec = durationSec,
            weightKg = weightKg,
            comment = comment,
            date = date
        )

    @Test
    fun week_entry_today_minus_7_lands_in_offset_1_not_0() {
        // Given — запись today − 7: последний день предыдущего WEEK-окна
        val entries = listOf(entry(id = 1L, date = millis(today.minusDays(7))))

        // When
        val result = computeCategoryStatsWindows(entries, StatsPeriod.WEEK, today)

        // Then — смещение 7 / 7 = 1: в актуальном окне записи нет, в окне 1 — есть
        assertTrue(result.getValue(0).categoryTotals.isEmpty(), "today−7 не входит в окно 0")
        val total = result.getValue(1).categoryTotals.single()
        assertEquals("PULL", total.category, "today−7 попадает в окно смещения 1")
        assertEquals(3, total.totalSets, "Подходы записи в окне смещения 1")
    }

    @Test
    fun same_window_entries_aggregated_together() {
        // Given — обе записи в окне смещения 1 (7 и 8 дней назад при WEEK)
        val entries =
            listOf(
                entry(id = 1L, date = millis(today.minusDays(7))),
                entry(id = 2L, date = millis(today.minusDays(8)), sets = 2)
            )

        // When
        val result = computeCategoryStatsWindows(entries, StatsPeriod.WEEK, today)

        // Then — агрегация внутри окна: одна строка доната, drill-down по датам убыванием
        val offset1 = result.getValue(1)
        val total = offset1.categoryTotals.single()
        assertEquals(5, total.totalSets, "Записи одного окна агрегируются вместе (3 + 2)")
        val groups =
            checkNotNull(offset1.drillGroups["PULL"]) { "Drill-down PULL окна 1" }
        assertEquals(
            listOf(today.minusDays(7), today.minusDays(8)),
            groups.map { it.date },
            "Даты drill-down внутри окна — убыванием"
        )
        assertTrue(result.getValue(0).categoryTotals.isEmpty(), "Окно 0 без записей — донат пуст")
    }

    @Test
    fun empty_window_keeps_key_with_empty_result() {
        // Given — записи в смещениях 0 (today) и 2 (today − 14), окно 1 пустое
        val entries =
            listOf(
                entry(id = 1L, date = millis(today)),
                entry(id = 2L, date = millis(today.minusDays(14)))
            )

        // When
        val result = computeCategoryStatsWindows(entries, StatsPeriod.WEEK, today)

        // Then — ключи карты — все смещения 0..maxOffset, окно 1 — пустой
        // результат, а не отсутствующий ключ
        assertEquals(setOf(0, 1, 2), result.keys, "Ключи карты — все смещения 0..maxOffset")
        val empty = result.getValue(1)
        assertTrue(empty.categoryTotals.isEmpty(), "Пустое окно — пустой донат")
        assertTrue(empty.exerciseTotals.isEmpty(), "Пустое окно — пустая легенда")
        assertTrue(empty.drillGroups.isEmpty(), "Пустое окно — пустой drill-down")
        assertEquals(
            3,
            result
                .getValue(2)
                .categoryTotals
                .single()
                .totalSets,
            "Запись в окне 2 на месте"
        )
    }

    @Test
    fun future_entry_falls_into_no_window() {
        // Given — запись сегодня и запись завтра (дата позже today)
        val entries =
            listOf(
                entry(id = 1L, date = millis(today)),
                entry(id = 2L, date = millis(today.plusDays(1)), sets = 9)
            )

        // When
        val result = computeCategoryStatsWindows(entries, StatsPeriod.WEEK, today)

        // Then — запись из будущего отброшена до раскладки: единственное окно 0,
        // в агрегатах только запись today (иначе −1 / 7 == 0 отнесло бы «завтра» к окну 0)
        assertEquals(setOf(0), result.keys, "Запись позже today не попадает ни в одно окно")
        assertEquals(
            listOf(3),
            result.getValue(0).categoryTotals.map { it.totalSets },
            "В агрегатах только запись today, не завтра"
        )
    }

    @Test
    fun no_entries_on_or_before_today_single_empty_offset_0() {
        // Given — единственная запись датирована позже сегодня
        val entries = listOf(entry(id = 1L, date = millis(today.plusDays(3))))

        // When
        val result = computeCategoryStatsWindows(entries, StatsPeriod.WEEK, today)

        // Then — карта содержит единственный ключ 0 с пустым результатом
        assertEquals(setOf(0), result.keys, "Без записей ≤ today карта содержит только ключ 0")
        val only = result.getValue(0)
        assertTrue(only.categoryTotals.isEmpty(), "Пустой донат окна 0")
        assertTrue(only.exerciseTotals.isEmpty(), "Пустая легенда окна 0")
        assertTrue(only.drillGroups.isEmpty(), "Пустой drill-down окна 0")
    }

    @Test
    fun month_boundary_29_and_30_days_ago_split_offsets_0_and_1() {
        // Given — MONTH: today − 29 в окне 0 (29 / 30 = 0), today − 30 в окне 1 (30 / 30 = 1)
        val entries =
            listOf(
                entry(id = 1L, date = millis(today.minusDays(29))),
                entry(id = 2L, date = millis(today.minusDays(30)))
            )

        // When
        val result = computeCategoryStatsWindows(entries, StatsPeriod.MONTH, today)

        // Then — целочисленное деление отставания на длину окна
        assertEquals(setOf(0, 1), result.keys, "Два окна месяца с записями")
        assertEquals(
            3,
            result
                .getValue(0)
                .categoryTotals
                .single()
                .totalSets,
            "today−29 в окне 0"
        )
        assertEquals(
            3,
            result
                .getValue(1)
                .categoryTotals
                .single()
                .totalSets,
            "today−30 в окне 1"
        )
    }

    @Test
    fun window_0_shares_and_sorts_match_computeCategoryStats() {
        // Given — смешанные записи: REPS и TIME, две категории, два окна
        val entries =
            listOf(
                entry(id = 1L, date = millis(today)),
                entry(
                    id = 2L,
                    category = "CORE",
                    exerciseName = "Планка",
                    measuredBy = ExerciseMeasuredBy.TIME,
                    date = millis(today.minusDays(2)),
                    sets = 2,
                    reps = null,
                    durationSec = 60
                ),
                entry(
                    id = 3L,
                    exerciseName = "Австралийские подтягивания",
                    date = millis(today.minusDays(8)),
                    sets = 2
                )
            )

        // When
        val windows = computeCategoryStatsWindows(entries, StatsPeriod.WEEK, today)

        // Then — срез смещения 0 совпадает с текущим computeCategoryStats
        // (тот же фильтр !day.isAfter(today), те же доли и сортировки)
        val expected = computeCategoryStats(entries, StatsPeriod.WEEK, today)
        assertEquals(expected, windows.getValue(0), "Окно 0 — результат computeCategoryStats")
        val offset1 = windows.getValue(1).categoryTotals.single()
        assertEquals(2, offset1.totalSets, "Запись окна 1 агрегирована отдельно от окна 0")
    }
}
