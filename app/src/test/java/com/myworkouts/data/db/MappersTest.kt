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

    private fun sampleListItemEntity(weightKg: Double?) =
        EntryListItemEntity(
            id = 1L,
            exerciseName = "Подтягивания",
            sets = 4,
            reps = 10,
            durationSec = null,
            date = 1_700_000_000_000L,
            weightKg = weightKg
        )

    @Test
    fun toListItem_when_weightKg_present_then_maps_to_domain() {
        // Given — проекция с весом 5.0

        // When
        val item = sampleListItemEntity(weightKg = 5.0).toListItem()

        // Then — вес проброшен в доменную модель
        assertEquals(5.0, item.weightKg)
    }

    @Test
    fun toListItem_when_weightKg_null_then_maps_null() {
        // Given — проекция без веса

        // When
        val item = sampleListItemEntity(weightKg = null).toListItem()

        // Then
        assertEquals(null, item.weightKg)
    }

    @Test
    fun toListItem_when_weightKg_zero_then_keeps_zero() {
        // Given — 0.0 в БД: это данные, а не «нет веса» (design Decision 5)

        // When
        val item = sampleListItemEntity(weightKg = 0.0).toListItem()

        // Then — 0 проходит без искажений, решение о скрытии — на стороне UI
        assertEquals(0.0, item.weightKg)
    }
}
