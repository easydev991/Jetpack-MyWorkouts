package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Доля категории в донате: суммы её записей за период — долю даёт
 * `totalSets` (подходы, overview-stats-time-support design Decision 1),
 * `totalReps`/`totalDurationSec` — значения легенды в естественных
 * единицах. Поля с нулевыми дефолтами — демо-данные макета задают
 * только то, что отображают.
 */
data class CategoryTotal(
    val category: ExerciseCategory,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val totalDurationSec: Int = 0
)

/**
 * Суммы одного упражнения для легенды drill-down — те же единицы, что у
 * [CategoryTotal]; доля и сортировка — по `totalSets`.
 */
data class ExerciseTotal(
    val exerciseName: String,
    val totalSets: Int = 0,
    val totalReps: Int = 0,
    val totalDurationSec: Int = 0
)

/** Записи одного дня drill-down. */
data class DrillDateGroup(
    val date: LocalDate,
    val entries: List<EntryWithExercise>
)

/**
 * Полный расчёт вкладки «Статистика» за период (overview-screen, design
 * Decision 4 + overview-stats-time-support Decision 1–2): доли категорий,
 * суммы упражнений по категориям и записи каждой категории по датам.
 *
 * @property categoryTotals Категории с подходами > 0, убывание подходов
 * @property exerciseTotals Упражнения категории с подходами > 0, убывание
 * @property drillGroups Все записи категории (оба типа измерения) по датам, убывание
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
 * Сумма повторов записи: подходы x повторы REPS-записи; TIME-запись
 * повторов не даёт — секунды и повторения не смешиваются.
 */
private fun EntryWithExercise.repsVolume(): Int =
    if (measuredBy == ExerciseMeasuredBy.REPS) {
        sets * (reps ?: 0)
    } else {
        0
    }

/**
 * Сумма длительности записи: подходы x длительность TIME-записи;
 * REPS-запись длительности не даёт.
 */
private fun EntryWithExercise.durationVolume(): Int =
    if (measuredBy == ExerciseMeasuredBy.TIME) {
        sets * (durationSec ?: 0)
    } else {
        0
    }

/**
 * Рассчитывает вкладку «Статистика» (overview-screen, design Decision 4
 * + overview-stats-time-support Decision 1): чистая функция, `today` —
 * параметром. Доли доната и сортировка — по сумме подходов, TIME- и
 * REPS-записи наравне; `totalReps`/`totalDurationSec` — значения легенды
 * в естественных единицах. Окно периода — `[statsPeriodStart, today]`
 * включительно; будущие записи с кривой датой не рвут логику.
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

    val categoryTotals =
        inPeriod
            .groupBy { it.category }
            .map { (category, list) ->
                CategoryTotal(
                    category = category,
                    totalSets = list.sumOf { it.sets },
                    totalReps = list.sumOf { it.repsVolume() },
                    totalDurationSec = list.sumOf { it.durationVolume() }
                )
            }.filter { it.totalSets > 0 }
            .sortedWith(compareByDescending<CategoryTotal> { it.totalSets }.thenBy { it.category.name })
    val exerciseTotals =
        inPeriod
            .groupBy { it.category }
            .mapValues { (_, list) ->
                list
                    .groupBy { it.exerciseName }
                    .map { (name, perExercise) ->
                        ExerciseTotal(
                            exerciseName = name,
                            totalSets = perExercise.sumOf { it.sets },
                            totalReps = perExercise.sumOf { it.repsVolume() },
                            totalDurationSec = perExercise.sumOf { it.durationVolume() }
                        )
                    }.filter { it.totalSets > 0 }
                    .sortedWith(
                        compareByDescending<ExerciseTotal> { it.totalSets }
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
