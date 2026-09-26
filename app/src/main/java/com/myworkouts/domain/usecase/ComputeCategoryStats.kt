package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseCategory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/** Доля категории в донате: сумма подходов её записей за период. */
data class CategoryTotal(
    val category: ExerciseCategory,
    val totalSets: Int = 0
)

/**
 * Суммы одного упражнения для легенды drill-down — те же единицы, что у
 * [CategoryTotal]; доля и сортировка — по `totalSets`.
 */
data class ExerciseTotal(
    val exerciseName: String,
    val totalSets: Int = 0
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
 * Процент строки легенды от базы её уровня (overview-periods-and-shares,
 * design D6): целое, округление до ближайшего целого (`roundToInt`);
 * сумма процентов строк может отличаться от 100 % — это допустимо по
 * спеке и не «чинится». База — сумма `totalSets` отображаемых строк
 * уровня: у уровня категорий — общая, в drill-down — своя у каждой
 * категории. Вырожденная нулевая база (строки не показываются) — 0.
 */
@Suppress("MagicNumber") // 100.0 — перевод доли в процент (overview-periods-and-shares, design D6)
internal fun percentOf(
    totalSets: Int,
    baseSets: Int
): Int = if (baseSets <= 0) 0 else (totalSets * 100.0 / baseSets).roundToInt()

/**
 * Рассчитывает вкладку «Статистика» (overview-screen, design Decision 4
 * + overview-stats-time-support Decision 1): чистая функция, `today` —
 * параметром. Доли доната и сортировка — по сумме подходов, TIME- и
 * REPS-записи наравне. Окно периода — `[statsPeriodStart, today]`
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
                    totalSets = list.sumOf { it.sets }
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
                            totalSets = perExercise.sumOf { it.sets }
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
