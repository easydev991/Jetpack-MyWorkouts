package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Доля категории в донате: суммарный REPS-объём за период. */
data class CategoryTotal(
    val category: ExerciseCategory,
    val totalReps: Int
)

/** Сумма повторов одного упражнения для легенды drill-down. */
data class ExerciseTotal(
    val exerciseName: String,
    val totalReps: Int
)

/** Записи одного дня drill-down. */
data class DrillDateGroup(
    val date: LocalDate,
    val entries: List<EntryWithExercise>
)

/**
 * Полный расчёт вкладки «Статистика» за период (overview-screen, design
 * Decision 4): доли категорий, суммы упражнений по категориям и записи
 * каждой категории по датам.
 *
 * @property categoryTotals Категории с REPS-объёмом > 0, убывание суммы
 * @property exerciseTotals REPS-упражнения категории с объёмом > 0, убывание
 * @property drillGroups Все записи категории (включая TIME) по датам, убывание
 */
data class CategoryStatsResult(
    val categoryTotals: List<CategoryTotal>,
    val exerciseTotals: Map<ExerciseCategory, List<ExerciseTotal>>,
    val drillGroups: Map<ExerciseCategory, List<DrillDateGroup>>
)

/**
 * Старт периода статистики включительно (overview-screen, design
 * Decision 3): день = сегодня, неделя = ISO-неделя с понедельника,
 * месяц и год — календарные. Системная зона.
 */
internal fun statsPeriodStart(
    period: StatsPeriod,
    today: LocalDate
): LocalDate =
    when (period) {
        StatsPeriod.DAY -> today
        StatsPeriod.WEEK -> today.minusDays((today.dayOfWeek.value - 1).toLong())
        StatsPeriod.MONTH -> today.withDayOfMonth(1)
        StatsPeriod.YEAR -> today.withDayOfYear(1)
    }

/**
 * Календарный день записи (epoch millis → LocalDate), паттерн
 * `groupEntriesByDate` главного экрана.
 */
internal fun entryDay(
    entry: EntryWithExercise,
    zone: ZoneId
): LocalDate = Instant.ofEpochMilli(entry.date).atZone(zone).toLocalDate()

/**
 * Объём записи для доната и легенды: подходы x повторы REPS-записи;
 * TIME-запись объёма не даёт (design Decision 4) — суммы демо-данных
 * макета (60/день = 360/неделя) соответствуют объёму.
 */
private fun EntryWithExercise.repsVolume(): Int =
    if (measuredBy == ExerciseMeasuredBy.REPS) {
        sets * (reps ?: 0)
    } else {
        0
    }

/**
 * Рассчитывает вкладку «Статистика» (overview-screen, design Decision 4):
 * чистая функция, `today` — параметром. Донат и легенда — только REPS;
 * TIME-записи входят в drill-down своей категории. Окно периода —
 * `[statsPeriodStart, today]` включительно; будущие записи с кривой датой
 * не рвут логику.
 */
internal fun computeCategoryStats(
    entries: List<EntryWithExercise>,
    period: StatsPeriod,
    today: LocalDate
): CategoryStatsResult {
    val zone = ZoneId.systemDefault()
    val start = statsPeriodStart(period, today)
    val inPeriod =
        entries.filter { entry ->
            val day = entryDay(entry, zone)
            !day.isBefore(start) && !day.isAfter(today)
        }

    val repsEntries = inPeriod.filter { it.measuredBy == ExerciseMeasuredBy.REPS }
    val categoryTotals =
        repsEntries
            .groupBy { it.category }
            .map { (category, list) -> CategoryTotal(category, list.sumOf { it.repsVolume() }) }
            .filter { it.totalReps > 0 }
            .sortedWith(compareByDescending<CategoryTotal> { it.totalReps }.thenBy { it.category.name })
    val exerciseTotals =
        repsEntries
            .groupBy { it.category }
            .mapValues { (_, list) ->
                list
                    .groupBy { it.exerciseName }
                    .map { (name, perExercise) ->
                        ExerciseTotal(name, perExercise.sumOf { it.repsVolume() })
                    }.filter { it.totalReps > 0 }
                    .sortedWith(
                        compareByDescending<ExerciseTotal> { it.totalReps }
                            .thenBy { it.exerciseName }
                    )
            }
    val drillGroups =
        inPeriod
            .groupBy { it.category }
            .mapValues { (_, categoryEntries) ->
                categoryEntries
                    .groupBy { entryDay(it, zone) }
                    .map { (day, dayEntries) ->
                        DrillDateGroup(
                            date = day,
                            entries = dayEntries
                        )
                    }.sortedByDescending { it.date }
            }
    return CategoryStatsResult(categoryTotals, exerciseTotals, drillGroups)
}
