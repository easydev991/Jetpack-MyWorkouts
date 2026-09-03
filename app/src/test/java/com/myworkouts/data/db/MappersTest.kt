package com.myworkouts.data.db

import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit-тесты маппинга `Exercise` ↔ `ExerciseEntity` (Mappers.kt).
 *
 * Покрывают двусторонний перенос `measuredBy` ↔ `measured_by` по образцу
 * `category`: enum в домене, строка в entity.
 */
class MappersTest {
    private fun sampleExercise(measuredBy: ExerciseMeasuredBy) =
        Exercise(
            id = 1L,
            name = "Планка",
            category = ExerciseCategory.CORE,
            isCustom = false,
            comment = null,
            createdAt = 100L,
            measuredBy = measuredBy
        )

    @Test
    fun exercise_measuredBy_time_roundtrip_preserved() {
        // Given
        val exercise = sampleExercise(measuredBy = ExerciseMeasuredBy.TIME)

        // When
        val entity = exercise.toEntity()
        val restored = entity.toDomain()

        // Then — TIME сохраняется в обе стороны, entity хранит имя значения
        assertEquals("TIME", entity.measuredBy)
        assertEquals(ExerciseMeasuredBy.TIME, restored.measuredBy)
    }
}
