package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit-тесты [computeCategoryStats] (overview-screen, tasks 2.1–2.2):
 * фильтрация календарного периода, доли по сумме подходов для обоих
 * типов измерения (REPS и TIME), суммы повторов и длительности,
 * сортировка и drill-down данные. Чистая функция, `today` — параметром,
 * зона системная (как на главном экране).
 */
class ComputeCategoryStatsTest {
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
        exerciseName: String = "Подтягивания",
        category: ExerciseCategory = ExerciseCategory.PULL,
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
    fun day_period_excludes_yesterday_entries() {
        // Given — запись вчера, сегодня записей нет
        val entries = listOf(entry(id = 1L, date = millis(today.minusDays(1))))

        // When
        val result = computeCategoryStats(entries, StatsPeriod.DAY, today)

        // Then — период «день» = только сегодня
        assertTrue(result.categoryTotals.isEmpty(), "Вчерашняя запись не входит в день")
        assertTrue(result.drillGroups.isEmpty(), "Drill-down за день пуст")
    }

    @Test
    fun week_period_starts_monday_includes_today_excludes_sunday() {
        // Given — вс предыдущей недели, пн текущей (= today) и today+1
        val entries =
            listOf(
                entry(id = 1L, date = millis(today.minusDays(1))),
                entry(id = 2L, date = millis(today)),
                entry(id = 3L, date = millis(today.plusDays(1)))
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.WEEK, today)

        // Then — окно [пн 14.09, today]; воскресенье и «завтра» не входят
        val totals = result.categoryTotals
        assertEquals(1, totals.size, "Только запись сегодняшнего дня в неделе")
        assertEquals(30, totals.first().totalReps, "Объём = 3 x 10")
    }

    @Test
    fun month_period_includes_first_day_excludes_last_of_previous() {
        // Given — записи 1 сентября и 31 августа
        val entries =
            listOf(
                entry(id = 1L, date = millis(LocalDate.of(2026, 9, 1))),
                entry(id = 2L, date = millis(LocalDate.of(2026, 8, 31)))
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.MONTH, today)

        // Then — календарный месяц: только 1 сентября
        assertEquals(1, result.categoryTotals.size, "Только запись 1 сентября в месяце")
    }

    @Test
    fun year_period_includes_january_excludes_last_year() {
        // Given — записи в январе текущего года и в декабре прошлого
        val entries =
            listOf(
                entry(id = 1L, date = millis(LocalDate.of(2026, 1, 5))),
                entry(id = 2L, date = millis(LocalDate.of(2025, 12, 31)))
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.YEAR, today)

        // Then — календарный год: только январь 2026
        assertEquals(1, result.categoryTotals.size, "Только запись января 2026 в году")
    }

    @Test
    fun mixed_category_carries_sets_reps_and_duration() {
        // Given — PULL: REPS 3x10 и TIME 2x90 в одном периоде
        val entries =
            listOf(
                entry(id = 1L, date = millis(today)),
                entry(
                    id = 2L,
                    exerciseName = "Планка",
                    measuredBy = ExerciseMeasuredBy.TIME,
                    date = millis(today),
                    sets = 2,
                    reps = null,
                    durationSec = 90
                )
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.WEEK, today)

        // Then — смешанная категория несёт оба значения, доля по подходам
        val total = result.categoryTotals.single()
        assertEquals(5, total.totalSets, "Доля = подходы REPS + TIME записей")
        assertEquals(30, total.totalReps, "Повторы — сумма подходы x повторы только REPS")
        assertEquals(180, total.totalDurationSec, "Длительность — сумма подходы x длительность только TIME")
    }

    @Test
    fun total_reps_is_sets_times_reps_volume() {
        // Given — 3x10 и 2x12 одного дня
        val entries =
            listOf(
                entry(id = 1L, date = millis(today), sets = 3, reps = 10),
                entry(id = 1L, date = millis(today), sets = 2, reps = 12)
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.WEEK, today)

        // Then — объём = подходы x повторы (демо-данные макета: 60/день = 360/неделя)
        assertEquals(54, result.categoryTotals.single().totalReps, "Сумма объёмов записей")
    }

    @Test
    fun time_category_in_category_and_exercise_totals() {
        // Given — категория CORE только с TIME-записью: планка 3 подхода по 90 сек
        val entries =
            listOf(
                entry(
                    id = 1L,
                    category = ExerciseCategory.CORE,
                    exerciseName = "Планка",
                    measuredBy = ExerciseMeasuredBy.TIME,
                    date = millis(today),
                    sets = 3,
                    reps = null,
                    durationSec = 90
                )
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.WEEK, today)

        // Then — TIME-категория видна в донате и легенде drill-down
        val total = result.categoryTotals.single()
        assertEquals(ExerciseCategory.CORE, total.category, "TIME-категория в донате")
        assertEquals(3, total.totalSets, "Доля по подходам")
        assertEquals(0, total.totalReps, "У TIME-записей нет повторов")
        assertEquals(270, total.totalDurationSec, "Длительность = подходы x длительность")
        val exerciseTotal =
            checkNotNull(result.exerciseTotals[ExerciseCategory.CORE]) { "Легенда drill-down CORE" }
                .single()
        assertEquals("Планка", exerciseTotal.exerciseName, "TIME-упражнение в легенде")
        assertEquals(3, exerciseTotal.totalSets, "Доля упражнения по подходам")
        assertEquals(270, exerciseTotal.totalDurationSec, "Длительность упражнения")
    }

    @Test
    fun totals_sorted_by_sets_not_reps_volume() {
        // Given — PUSH: 2 подхода по 100 повторов; CORE: 3 подхода по 60 сек
        val entries =
            listOf(
                entry(
                    id = 1L,
                    category = ExerciseCategory.PUSH,
                    exerciseName = "Отжимания",
                    date = millis(today),
                    sets = 2,
                    reps = 100
                ),
                entry(
                    id = 2L,
                    category = ExerciseCategory.CORE,
                    exerciseName = "Планка",
                    measuredBy = ExerciseMeasuredBy.TIME,
                    date = millis(today),
                    sets = 3,
                    reps = null,
                    durationSec = 60
                )
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.WEEK, today)

        // Then — сортировка по подходам: CORE (3) выше PUSH (2), хотя REPS-объём меньше
        assertEquals(
            listOf(ExerciseCategory.CORE, ExerciseCategory.PUSH),
            result.categoryTotals.map { it.category },
            "Доли и сортировка по сумме подходов обоих типов измерения"
        )
    }

    @Test
    fun category_totals_sorted_by_total_desc() {
        // Given — PULL 30, PUSH 40, LEGS 20
        val entries =
            listOf(
                entry(id = 1L, category = ExerciseCategory.PULL, date = millis(today)),
                entry(
                    id = 2L,
                    category = ExerciseCategory.PUSH,
                    exerciseName = "Отжимания",
                    date = millis(today),
                    sets = 4,
                    reps = 10
                ),
                entry(
                    id = 3L,
                    category = ExerciseCategory.LEGS,
                    exerciseName = "Приседания",
                    date = millis(today),
                    sets = 2,
                    reps = 10
                )
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.WEEK, today)

        // Then — убывание суммы: PUSH 40, PULL 30, LEGS 20 (по подходам 4, 3, 2)
        assertEquals(
            listOf(40, 30, 20),
            result.categoryTotals.map { it.totalReps },
            "Категории отсортированы по сумме убыванием"
        )
        assertEquals(
            listOf(4, 3, 2),
            result.categoryTotals.map { it.totalSets },
            "Доли категорий — суммы подходов"
        )
    }

    @Test
    fun exercise_totals_grouped_by_category_sorted_desc() {
        // Given — PULL: Подтягивания 3x10 + Австралийские 2x10, PUSH 4x10
        val entries =
            listOf(
                entry(id = 1L, date = millis(today)),
                entry(
                    id = 2L,
                    exerciseName = "Австралийские подтягивания",
                    date = millis(today),
                    sets = 2
                ),
                entry(
                    id = 3L,
                    category = ExerciseCategory.PUSH,
                    exerciseName = "Отжимания",
                    date = millis(today),
                    sets = 4
                )
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.WEEK, today)

        // Then — упражнения внутри категории, убывание суммы
        val pullTotals =
            checkNotNull(result.exerciseTotals[ExerciseCategory.PULL]) { "PULL-легенда drill-down" }
        assertEquals(
            listOf(30, 20),
            pullTotals.map { it.totalReps },
            "Упражнения категории по сумме убыванием"
        )
        assertEquals(
            listOf(3, 2),
            pullTotals.map { it.totalSets },
            "Доли упражнений — суммы подходов"
        )
    }

    @Test
    fun drill_groups_sorted_by_date_desc_and_carry_fields() {
        // Given — две записи разных дней месяца, с весом и комментарием
        val entries =
            listOf(
                entry(
                    id = 11L,
                    date = millis(today.minusDays(2)),
                    weightKg = 5.0,
                    comment = "сразу после сна"
                ),
                entry(id = 12L, date = millis(today))
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.MONTH, today)

        // Then — даты убыванием, поля записи доезжают
        val groups =
            checkNotNull(result.drillGroups[ExerciseCategory.PULL]) { "Drill-down PULL" }
        assertEquals(2, groups.size, "Две дневные группы")
        assertEquals(today, groups.first().date, "Свежая дата первой")
        val fresh = groups.first().entries.single()
        assertEquals(12L, fresh.id)
        val old = groups.last().entries.single()
        assertEquals(5.0, old.weightKg, "Вес drill-записи")
        assertEquals("сразу после сна", old.comment, "Комментарий drill-записи")
    }

    @Test
    fun drill_groups_include_time_entries() {
        // Given — TIME-запись планки
        val entries =
            listOf(
                entry(
                    id = 1L,
                    category = ExerciseCategory.CORE,
                    exerciseName = "Планка",
                    measuredBy = ExerciseMeasuredBy.TIME,
                    date = millis(today),
                    reps = null,
                    durationSec = 90
                )
            )

        // When
        val result = computeCategoryStats(entries, StatsPeriod.WEEK, today)

        // Then — TIME-запись видна в списке drill-down своей категории
        val coreGroups =
            checkNotNull(result.drillGroups[ExerciseCategory.CORE]) { "Drill-down CORE" }
        val drillEntry = coreGroups.single().entries.single()
        assertEquals("Планка", drillEntry.exerciseName)
        assertEquals(null, drillEntry.reps, "У TIME-записи нет повторов")
        assertEquals(90, drillEntry.durationSec, "Длительность drill-записи")
    }
}
