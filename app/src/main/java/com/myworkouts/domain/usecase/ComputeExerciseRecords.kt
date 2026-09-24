package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import java.time.LocalDate
import java.time.ZoneId

/**
 * Строка списка рекордов: лучший результат одного упражнения
 * (overview-screen, design Decision 4).
 *
 * @property exerciseName Название упражнения
 * @property measuredBy Тип измерения (подпись значения: «повт.» / длительность)
 * @property value Значение рекорда: PER_SET — reps/durationSec подхода,
 *   PER_DAY — суточная сумма sets x value
 * @property dateMillis Дата записи-рекордсмена (epoch millis); при равных
 *   максимумах — наиболее ранняя дата
 * @property comment Комментарий строки: комментарий записи-рекордсмена,
 *   иначе комментарий упражнения, иначе `null`
 */
data class ExerciseRecordData(
    val exerciseName: String,
    val measuredBy: ExerciseMeasuredBy,
    val value: Int,
    val dateMillis: Long,
    val comment: String?
)

/** Значение одного подхода записи: повторы, для TIME — секунды. */
private fun EntryWithExercise.setValue(): Int = reps ?: durationSec ?: 0

/**
 * Лучший день упражнения (design Decision 4): суточная сумма sets x value,
 * максимум; при равных суммах — более ранний день. [bestDay] — первая
 * (ранняя) запись дня: её дата и комментарий идут в строку рекорда.
 */
private fun bestDay(
    perExercise: List<EntryWithExercise>,
    zone: ZoneId
): Pair<Int, EntryWithExercise>? {
    data class DayTotal(
        val date: LocalDate,
        val total: Int,
        val firstEntry: EntryWithExercise
    )

    val dayTotals =
        perExercise
            .groupBy { entryDay(it, zone) }
            .map { (day, dayEntries) ->
                val first =
                    checkNotNull(dayEntries.minByOrNull { it.date }) { "Группа дня не пуста" }
                DayTotal(
                    date = day,
                    total = dayEntries.sumOf { it.sets * it.setValue() },
                    firstEntry = first
                )
            }.filter { it.total > 0 }
    val maxTotal = dayTotals.maxOfOrNull { it.total } ?: return null
    val best =
        checkNotNull(dayTotals.filter { it.total == maxTotal }.minByOrNull { it.date }) {
            "maxTotal найден в непустом списке"
        }
    return best.total to best.firstEntry
}

/**
 * Рассчитывает список рекордов (overview-screen, design Decision 4):
 * по одному лучшему результату на упражнение. PER_SET — максимум значения
 * подхода; PER_DAY — максимум суточных сумм sets x value (записи одного
 * упражнения группируются по календарному дню — двух записей одного
 * упражнения за день не исключено). Значение <= 0 не рекорд; равные
 * максимумы — наиболее ранняя дата. Комментарий: записи-рекордсмена →
 * упражнения → `null`. Результат отсортирован по дате убыванием.
 */
internal fun computeExerciseRecords(
    entries: List<EntryWithExercise>,
    mode: RecordsMode
): List<ExerciseRecordData> =
    entries
        .groupBy { it.exerciseId }
        .mapNotNull { (_, perExercise) ->
            val sample =
                checkNotNull(perExercise.first()) { "Группа упражнения не пуста" }
            val best: EntryWithExercise
            val value: Int
            when (mode) {
                RecordsMode.PER_SET -> {
                    val positive = perExercise.filter { it.setValue() > 0 }
                    val maxValue = positive.maxOfOrNull { it.setValue() } ?: return@mapNotNull null
                    best = positive.filter { it.setValue() == maxValue }.minBy { it.date }
                    value = best.setValue()
                }
                RecordsMode.PER_DAY -> {
                    val (total, firstEntry) =
                        bestDay(perExercise, ZoneId.systemDefault()) ?: return@mapNotNull null
                    best = firstEntry
                    value = total
                }
            }
            ExerciseRecordData(
                exerciseName = sample.exerciseName,
                measuredBy = sample.measuredBy,
                value = value,
                dateMillis = best.date,
                comment = best.comment ?: sample.exerciseComment
            )
        }.sortedWith(
            compareByDescending<ExerciseRecordData> { it.dateMillis }.thenBy { it.exerciseName }
        )
