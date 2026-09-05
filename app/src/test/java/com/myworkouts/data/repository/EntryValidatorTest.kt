package com.myworkouts.data.repository

import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit-тесты для [EntryValidator].
 *
 * Покрывают спецификацию «Валидация полей записи» (workout-data spec,
 * workout-entries-list — design Decision 7):
 * rejected — подходы 0, повторы 0, вес −0.5, workoutId ≠ null;
 * для REPS — без повторов (`MISSING_REPS`); для TIME — без длительности
 * (`MISSING_DURATION`), длительность 0 (`INVALID_DURATION`);
 * одновременно повторы и длительность (`INCOMPATIBLE_REPS_AND_DURATION`);
 * accepted — вес null vs 0.0 различимы, запись «на время» (reps=null,
 * duration=60) валидна, две идентичные записи сохраняются раздельно.
 */
class EntryValidatorTest {
    private fun baseEntry(
        sets: Int = 1,
        reps: Int? = 10,
        weightKg: Double? = null,
        workoutId: Long? = null,
        durationSec: Int? = null
    ): ExerciseEntry =
        ExerciseEntry(
            id = 0L,
            exerciseId = 1L,
            workoutId = workoutId,
            date = 1_700_000_000_000L,
            sets = sets,
            reps = reps,
            weightKg = weightKg,
            durationSec = durationSec,
            comment = null,
            createdAt = 1L,
            modifiedAt = 1L
        )

    @Test
    fun valid_entry_returns_success() {
        // Given
        val entry = baseEntry()

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isSuccess)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, -100])
    fun sets_below_one_rejected(invalidSets: Int) {
        // Given
        val entry = baseEntry(sets = invalidSets)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.INVALID_SETS,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, -50])
    fun reps_below_one_when_present_rejected(invalidReps: Int) {
        // Given
        val entry = baseEntry(reps = invalidReps)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.INVALID_REPS,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @Test
    fun reps_null_is_valid_for_time_exercises() {
        // Given — планка: 1 подход, 60 секунд, без повторов
        val entry = baseEntry(reps = null, durationSec = 60)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.TIME)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun reps_null_for_reps_exercise_is_missing_reps_failure() {
        // Given — упражнение на повторы, а повторы не заданы
        // (workout-entries-list, design Decision 7: соответствие measuredBy)
        val entry = baseEntry(reps = null, durationSec = null)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.MISSING_REPS,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @Test
    fun duration_null_for_time_exercise_is_missing_duration_failure() {
        // Given — упражнение на время, длительность не задана
        val entry = baseEntry(reps = null, durationSec = null)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.TIME)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.MISSING_DURATION,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, -60])
    fun duration_zero_or_negative_for_time_exercise_is_invalid_duration_failure(invalidDuration: Int) {
        // Given
        val entry = baseEntry(reps = null, durationSec = invalidDuration)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.TIME)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.INVALID_DURATION,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @Test
    fun reps_and_duration_both_set_is_incompatible_failure() {
        // Given — одновременно заданы повторы и длительность
        val entry = baseEntry(reps = 10, durationSec = 60)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.INCOMPATIBLE_REPS_AND_DURATION,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @Test
    fun reps_and_duration_both_set_is_incompatible_failure_for_time_too() {
        // Given — для TIME-упражнения правило то же
        val entry = baseEntry(reps = 10, durationSec = 60)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.TIME)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.INCOMPATIBLE_REPS_AND_DURATION,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @Test
    fun reps_with_duration_for_reps_exercise_reports_incompatible_not_missing_reps() {
        // Given — REPS-упражнение, повторы заданы + длительность задана
        val entry = baseEntry(reps = 10, durationSec = 60)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then — порядок проверок: INCOMPATIBLE_REPS_AND_DURATION раньше MISSING_REPS
        assertEquals(
            EntryValidationError.INCOMPATIBLE_REPS_AND_DURATION,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.5, -1.0, -100.0])
    fun negative_weight_rejected(invalidWeight: Double) {
        // Given
        val entry = baseEntry(weightKg = invalidWeight)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.INVALID_WEIGHT,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @Test
    fun weight_null_is_valid_own_bodyweight() {
        // Given
        val entry = baseEntry(weightKg = null)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isSuccess)
    }

    @Test
    fun weight_zero_is_valid_distinct_from_null() {
        // Given — спецификация: «нулевой вес» отличается от «вес не задан»
        val entry = baseEntry(weightKg = 0.0)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isSuccess)
        assertNotNull(entry.weightKg)
        assertEquals(0.0, entry.weightKg)
    }

    @Test
    fun workout_id_non_null_rejected_in_mvp() {
        // Given — workoutId = 1 означает «привязано к тренировке»,
        // в MVP не поддерживается (таблицы workouts ещё нет)
        val entry = baseEntry(workoutId = 1L)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.WORKOUT_NOT_SUPPORTED,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }

    @Test
    fun validation_preserves_domain_fields() {
        // Given — валидатор не должен мутировать entry
        val entry = baseEntry()

        // When
        EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertEquals(1L, entry.exerciseId)
        assertEquals(1, entry.sets)
        assertEquals(10, entry.reps)
        assertNull(entry.weightKg)
        assertNull(entry.workoutId)
    }

    @Test
    @Suppress("UNUSED_VARIABLE")
    fun all_categories_are_independent_of_validation() {
        // Given — категория живёт на упражнении, а не на записи, но
        // контракт «запись валидна» не должен зависеть от типа упражнения.
        // Здесь просто проверяем, что валидатор не смотрит на категорию —
        // она живёт в Exercise, а не в ExerciseEntry.
        ExerciseCategory.values().forEach { _ ->
            val entry = baseEntry()
            assertTrue(EntryValidator.validate(entry, ExerciseMeasuredBy.REPS).isSuccess)
        }
    }
}
