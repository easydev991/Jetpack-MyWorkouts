package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * Unit-тесты [computeExerciseRecords] (overview-screen, tasks 2.3–2.4,
 * design Decision 4): режимы «за подход»/«за день», тай-брейк по ранней
 * дате, приоритет комментария, смешение TIME и REPS.
 */
class ComputeExerciseRecordsTest {
    private val monday = LocalDate.of(2026, 9, 14)
    private val tuesday = monday.plusDays(1)
    private val wednesday = monday.plusDays(2)

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
        exerciseId: Long = 1L,
        exerciseName: String = "Подтягивания",
        measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS,
        exerciseComment: String? = null,
        date: Long,
        sets: Int = 3,
        reps: Int? = 10,
        durationSec: Int? = null,
        comment: String? = null
    ): EntryWithExercise =
        EntryWithExercise(
            id = id,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            category = "PULL",
            measuredBy = measuredBy,
            exerciseComment = exerciseComment,
            sets = sets,
            reps = reps,
            durationSec = durationSec,
            comment = comment,
            date = date
        )

    @Test
    fun per_set_returns_max_value() {
        // Given — подходы на 10 и 12 повторов
        val entries =
            listOf(
                entry(id = 1L, date = millis(monday), reps = 10),
                entry(id = 2L, date = millis(tuesday), reps = 12)
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_SET)

        // Then — рекорд = максимальный подход
        assertEquals(12, records.single().value, "Максимум за подход")
        assertEquals(millis(tuesday), records.single().dateMillis, "Дата записи-рекордсмена")
    }

    @Test
    fun equal_record_dates_are_sorted_by_exercise_name() {
        // Given — два упражнения с рекордами в один день (вход в обратном порядке)
        val entries =
            listOf(
                entry(
                    id = 2L,
                    exerciseId = 2L,
                    exerciseName = "Подтягивания",
                    date = millis(monday),
                    reps = 12
                ),
                entry(
                    id = 1L,
                    exerciseId = 1L,
                    exerciseName = "Отжимания",
                    date = millis(monday),
                    reps = 15
                )
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_SET)

        // Then — при равных датах строки упорядочены по названию возрастанием
        assertEquals(listOf("Отжимания", "Подтягивания"), records.map { it.exerciseName })
    }

    @Test
    fun per_set_time_uses_duration() {
        // Given — TIME-упражнение: 3 подхода по 90 сек
        val entries =
            listOf(
                entry(
                    id = 1L,
                    exerciseName = "Планка",
                    measuredBy = ExerciseMeasuredBy.TIME,
                    date = millis(monday),
                    reps = null,
                    durationSec = 90
                )
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_SET)

        // Then — значение подхода = длительность, тип измерения доезжает
        val record = records.single()
        assertEquals(90, record.value, "Длительность подхода — значение рекорда")
        assertEquals(ExerciseMeasuredBy.TIME, record.measuredBy)
    }

    @Test
    fun per_day_sums_sets_times_value_per_day() {
        // Given — понедельник 3x10 = 30, вторник 4x8 = 32
        val entries =
            listOf(
                entry(id = 1L, date = millis(monday), sets = 3, reps = 10),
                entry(id = 2L, date = millis(tuesday), sets = 4, reps = 8)
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_DAY)

        // Then — лучший день 32, дата — дата лучшего дня
        val record = records.single()
        assertEquals(32, record.value, "Суточная сумма = подходы x повторы")
        assertEquals(millis(tuesday), record.dateMillis)
    }

    @Test
    fun per_day_combines_same_day_entries() {
        // Given — две записи одного упражнения за один день: 3x10 + 2x10
        val entries =
            listOf(
                entry(id = 1L, date = millis(monday), sets = 3, reps = 10),
                entry(id = 2L, date = millis(monday), sets = 2, reps = 10)
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_DAY)

        // Then — группировка по дню честная: 30 + 20 = 50
        assertEquals(50, records.single().value, "Записи одного дня суммируются")
    }

    @Test
    fun per_set_tie_earlier_date_wins() {
        // Given — одинаковый максимум 12 в понедельник и во вторник
        val entries =
            listOf(
                entry(id = 1L, date = millis(tuesday), reps = 12),
                entry(id = 2L, date = millis(monday), reps = 12)
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_SET)

        // Then — рекордом считается наиболее ранняя дата (design Decision 4)
        assertEquals(millis(monday), records.single().dateMillis, "Равные максимумы — ранняя дата")
    }

    @Test
    fun per_day_tie_earlier_day_wins() {
        // Given — дни с одинаковой суммой 30
        val entries =
            listOf(
                entry(id = 1L, date = millis(wednesday), sets = 3, reps = 10),
                entry(id = 2L, date = millis(monday), sets = 3, reps = 10)
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_DAY)

        // Then — из равных дней берётся более ранний
        assertEquals(millis(monday), records.single().dateMillis, "Равные дни — ранняя дата")
    }

    @Test
    fun zero_value_entries_are_not_records() {
        // Given — единственная запись с нулевым значением
        val entries = listOf(entry(id = 1L, date = millis(monday), reps = 0))

        // When
        val perSet = computeExerciseRecords(entries, RecordsMode.PER_SET)
        val perDay = computeExerciseRecords(entries, RecordsMode.PER_DAY)

        // Then — значение <= 0 не считается рекордом, строки нет
        assertTrue(perSet.isEmpty(), "Нулевой подход — не рекорд")
        assertTrue(perDay.isEmpty(), "Нулевой день — не рекорд")
    }

    @Test
    fun no_entries_returns_empty() {
        // Given — записей нет

        // When
        val records = computeExerciseRecords(emptyList(), RecordsMode.PER_SET)

        // Then — пустой список без строк
        assertTrue(records.isEmpty(), "Без записей рекордов нет")
    }

    @Test
    fun comment_prefers_record_entry_comment() {
        // Given — у записи свой комментарий, у упражнения тоже
        val entries =
            listOf(
                entry(
                    id = 1L,
                    exerciseComment = "комментарий упражнения",
                    date = millis(monday),
                    comment = "комментарий записи"
                )
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_SET)

        // Then — комментарий записи-рекордсмена первый в приоритете
        assertEquals("комментарий записи", records.single().comment)
    }

    @Test
    fun comment_falls_back_to_exercise_comment() {
        // Given — у записи комментария нет, у упражнения есть
        val entries =
            listOf(
                entry(id = 1L, exerciseComment = "комментарий упражнения", date = millis(monday))
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_SET)

        // Then — фолбэк на комментарий упражнения
        assertEquals("комментарий упражнения", records.single().comment)
    }

    @Test
    fun comment_null_when_both_absent() {
        // Given — комментариев нет ни у записи, ни у упражнения

        // When
        val records = computeExerciseRecords(listOf(entry(id = 1L, date = millis(monday))), RecordsMode.PER_SET)

        // Then — комментарий null, строка всё равно есть
        assertEquals(null, records.single().comment, "Нет комментария — null")
    }

    @Test
    fun records_sorted_by_date_desc() {
        // Given — отжимания (рекорд в пн) и подтягивания (рекорд в ср)
        val entries =
            listOf(
                entry(
                    id = 1L,
                    exerciseId = 1L,
                    exerciseName = "Подтягивания",
                    date = millis(wednesday),
                    reps = 12
                ),
                entry(
                    id = 2L,
                    exerciseId = 2L,
                    exerciseName = "Отжимания",
                    date = millis(monday),
                    sets = 4,
                    reps = 10
                )
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_SET)

        // Then — список по дате убыванием: свежий рекорд первым
        assertEquals(
            listOf("Подтягивания", "Отжимания"),
            records.map { it.exerciseName },
            "Свежие рекорды выше"
        )
    }

    @Test
    fun reps_and_time_records_in_one_list() {
        // Given — REPS-упражнение и TIME-упражнение
        val entries =
            listOf(
                entry(id = 1L, date = millis(monday)),
                entry(
                    id = 2L,
                    exerciseId = 2L,
                    exerciseName = "Планка",
                    measuredBy = ExerciseMeasuredBy.TIME,
                    date = millis(monday),
                    reps = null,
                    durationSec = 60
                )
            )

        // When
        val records = computeExerciseRecords(entries, RecordsMode.PER_SET)

        // Then — оба рекорда в одном списке
        assertEquals(2, records.size, "TIME и REPS вместе")
        assertEquals(
            setOf(ExerciseMeasuredBy.REPS, ExerciseMeasuredBy.TIME),
            records.map { it.measuredBy }.toSet()
        )
    }
}
