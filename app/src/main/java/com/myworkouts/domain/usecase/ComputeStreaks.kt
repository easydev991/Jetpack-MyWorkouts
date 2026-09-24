package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import java.time.LocalDate
import java.time.ZoneId

/**
 * Серия тренировок (overview-screen, design Decision 5).
 *
 * @property currentStreak Длина текущей серии (0 — стрик прерван)
 * @property recordStreak  Рекордная длина серии за всё время
 * @property workedOutToday Есть ли тренировка сегодня — нужно UI, чтобы
 *   отличать [com.myworkouts.ui.state.StreakCardState.Active]
 *   (тренировались сегодня) от Pending (сегодня ещё нет, но вчера была)
 */
data class StreakResult(
    val currentStreak: Int,
    val recordStreak: Int,
    val workedOutToday: Boolean = false
)

/**
 * Рассчитывает серию тренировок (overview-screen, design Decision 5):
 * день тренировки = календарный день с >= 1 записью (системная зона,
 * несколько записей дня считаются один раз). Текущая серия: если сегодня
 * есть запись — длина последовательности, заканчивающейся сегодня; иначе
 * если есть вчера — длина до вчера (сегодня ещё не кончился); иначе 0.
 * Рекорд — максимум по всем последовательным прогонам дат, включая
 * текущий: один проход по отсортированным distinct-датам. Пустые
 * данные — 0/0.
 */
internal fun computeStreaks(
    entries: List<EntryWithExercise>,
    today: LocalDate
): StreakResult {
    val zone = ZoneId.systemDefault()
    val daySet = entries.map { entryDay(it, zone) }.toHashSet()
    if (daySet.isEmpty()) {
        return StreakResult(currentStreak = 0, recordStreak = 0)
    }

    /** Длина последовательности дней, заканчивающейся в [end]. */
    fun runEndingAt(end: LocalDate): Int {
        var day = end
        var length = 0
        while (day in daySet) {
            length++
            day = day.minusDays(1)
        }
        return length
    }

    val yesterday = today.minusDays(1)
    val currentStreak =
        when {
            today in daySet -> runEndingAt(today)
            yesterday in daySet -> runEndingAt(yesterday)
            else -> 0
        }

    val days = daySet.sorted()
    var longest = 0
    var run = 0
    days.forEachIndexed { index, day ->
        run =
            if (index > 0 && day == days[index - 1].plusDays(1)) {
                run + 1
            } else {
                1
            }
        if (run > longest) {
            longest = run
        }
    }
    return StreakResult(
        currentStreak = currentStreak,
        recordStreak = maxOf(longest, currentStreak),
        workedOutToday = today in daySet
    )
}
