package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit-тесты [computeStreaks] (overview-screen, tasks 2.5–2.6, design
 * Decision 5): сегодня засчитывается, серия жива до полного пропуска
 * дня, рекорд = максимум по прогонам. День = календарный день с >= 1
 * записью, системная зона.
 */
class ComputeStreaksTest {
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
            category = "PULL",
            measuredBy = com.myworkouts.domain.model.ExerciseMeasuredBy.REPS,
            sets = 3,
            reps = 10,
            durationSec = null,
            date = date
        )

    @Test
    fun today_in_streak_counts_full_run() {
        // Given — тренировки сегодня, вчера и позавчера
        val entries =
            listOf(
                entry(1L, millis(today)),
                entry(2L, millis(today.minusDays(1))),
                entry(3L, millis(today.minusDays(2)))
            )

        // When
        val result = computeStreaks(entries, today)

        // Then — текущая серия 3, она же рекорд
        assertEquals(3, result.currentStreak, "Серия заканчивается сегодня")
        assertEquals(3, result.recordStreak, "Рекорд = текущая серия")
    }

    @Test
    fun yesterday_still_alive_series() {
        // Given — последняя тренировка вчера, сегодня ещё нет
        val entries =
            listOf(
                entry(1L, millis(today.minusDays(1))),
                entry(2L, millis(today.minusDays(2)))
            )

        // When
        val result = computeStreaks(entries, today)

        // Then — серия жива: сегодня не кончился
        assertEquals(2, result.currentStreak, "Серия с вчера не потеряна")
        assertEquals(2, result.recordStreak)
    }

    @Test
    fun gap_of_full_day_breaks_streak() {
        // Given — пропущен вчерашний день, последняя тренировка позавчера
        val entries =
            listOf(
                entry(1L, millis(today.minusDays(2))),
                entry(2L, millis(today.minusDays(3)))
            )

        // When
        val result = computeStreaks(entries, today)

        // Then — серия прервана (пропуск >= 2 дней от сегодня)
        assertEquals(0, result.currentStreak, "Вчерашний день пропущен — серия 0")
        assertEquals(2, result.recordStreak, "Но рекорд прошлой серии сохранён")
    }

    @Test
    fun record_is_longest_of_past_runs() {
        // Given — трёхдневный прогон на прошлой неделе и один день сегодня
        val entries =
            listOf(
                entry(1L, millis(today)),
                entry(2L, millis(today.minusDays(7))),
                entry(3L, millis(today.minusDays(8))),
                entry(4L, millis(today.minusDays(9)))
            )

        // When
        val result = computeStreaks(entries, today)

        // Then — текущая 1, рекорд 3
        assertEquals(1, result.currentStreak)
        assertEquals(3, result.recordStreak, "Рекорд = максимум по всем прогонам")
    }

    @Test
    fun empty_entries_give_zeroes() {
        // Given — записей нет

        // When
        val result = computeStreaks(emptyList(), today)

        // Then
        assertEquals(0, result.currentStreak, "Пустые данные — серия 0")
        assertEquals(0, result.recordStreak, "Пустые данные — рекорд 0")
    }

    @Test
    fun duplicate_entries_same_day_count_once() {
        // Given — две записи сегодня и одна вчера
        val entries =
            listOf(
                entry(1L, millis(today)),
                entry(2L, millis(today)),
                entry(3L, millis(today.minusDays(1)))
            )

        // When
        val result = computeStreaks(entries, today)

        // Then — день считается один раз
        assertEquals(2, result.currentStreak, "Несколько записей дня = один день серии")
        assertEquals(2, result.recordStreak)
    }

    @Test
    fun worked_out_today_when_trained_today_then_true() {
        // Given — запись сегодня
        val entries = listOf(entry(1L, millis(today)))

        // When
        val result = computeStreaks(entries, today)

        // Then — тренировка сегодня была (StreakCardState.Active)
        assertEquals(true, result.workedOutToday, "Запись сегодня — workedOutToday true")
    }

    @Test
    fun worked_out_today_when_series_alive_without_today_then_false() {
        // Given — последняя тренировка вчера, сегодня записей нет
        val entries =
            listOf(
                entry(1L, millis(today.minusDays(1))),
                entry(2L, millis(today.minusDays(2)))
            )

        // When
        val result = computeStreaks(entries, today)

        // Then — серия жива, но сегодняшней тренировки нет (Pending)
        assertEquals(false, result.workedOutToday, "Сегодня записей нет — workedOutToday false")
    }

    @Test
    fun worked_out_today_when_streak_broken_then_false() {
        // Given — последняя тренировка два дня назад, серия прервана
        val entries = listOf(entry(1L, millis(today.minusDays(2))))

        // When
        val result = computeStreaks(entries, today)

        // Then
        assertEquals(false, result.workedOutToday, "Серия прервана — workedOutToday false")
    }

    @Test
    fun worked_out_today_when_no_entries_then_false() {
        // Given/When — записей нет
        val result = computeStreaks(emptyList(), today)

        // Then
        assertEquals(false, result.workedOutToday, "Пустые данные — тренировок не было")
    }
}
