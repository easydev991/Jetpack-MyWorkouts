package com.myworkouts.data.db

import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.icons.ExerciseIcons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit-тесты маппинга `Exercise` ↔ `ExerciseEntity` (Mappers.kt).
 *
 * Покрывают двусторонний перенос `measuredBy` ↔ `measured_by` по образцу
 * `category`: enum в домене, строка в entity. А также двусторонний перенос
 * флага архивации `isArchived` ↔ `is_archived` и опционального номера
 * иконки `iconNumber` ↔ `icon_number` (exercise-icon-mvp).
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

    @Test
    fun exercise_isArchived_true_roundtrip_preserved() {
        // Given — заархивированное упражнение
        val exercise =
            sampleExercise(measuredBy = ExerciseMeasuredBy.REPS)
                .copy(isArchived = true)

        // When
        val entity = exercise.toEntity()
        val restored = entity.toDomain()

        // Then — true проброшен в обе стороны
        assertEquals(true, entity.isArchived)
        assertEquals(true, restored.isArchived)
    }

    @Test
    fun exercise_isArchived_false_roundtrip_preserved() {
        // Given — активное упражнение (false по умолчанию)
        val exercise = sampleExercise(measuredBy = ExerciseMeasuredBy.REPS)

        // When
        val entity = exercise.toEntity()
        val restored = entity.toDomain()

        // Then — false проброшен в обе стороны
        assertEquals(false, entity.isArchived)
        assertEquals(false, restored.isArchived)
    }

    @Test
    fun exercise_iconNumber_value_roundtrip_preserved() {
        // Given — упражнение с назначенной иконкой 5
        val exercise =
            sampleExercise(measuredBy = ExerciseMeasuredBy.REPS)
                .copy(iconNumber = 5)

        // When
        val entity = exercise.toEntity()
        val restored = entity.toDomain()

        // Then — 5 проброшен в обе стороны: и в колонку entity, и обратно в домен
        assertEquals(5, entity.iconNumber)
        assertEquals(5, restored.iconNumber)
    }

    @Test
    fun exercise_iconNumber_null_roundtrip_preserved() {
        // Given — упражнение без иконки (null по умолчанию)
        val exercise = sampleExercise(measuredBy = ExerciseMeasuredBy.REPS)

        // When
        val entity = exercise.toEntity()
        val restored = entity.toDomain()

        // Then — null доезжает без искажений (отсутствие иконки не теряется)
        assertEquals(null, entity.iconNumber)
        assertEquals(null, restored.iconNumber)
    }

    private fun sampleListItemEntity(
        weightKg: Double?,
        comment: String? = null,
        iconNumber: Int? = null
    ) = EntryListItemWithExerciseEntity(
        id = 1L,
        exerciseId = 2L,
        exerciseName = "Подтягивания",
        iconNumber = iconNumber,
        category = "PULL",
        measuredBy = "REPS",
        exerciseComment = null,
        sets = 4,
        reps = 10,
        durationSec = null,
        date = 1_700_000_000_000L,
        weightKg = weightKg,
        comment = comment
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

    @Test
    fun toListItem_when_comment_present_then_maps_to_domain() {
        // Given — проекция с непустым комментарием

        // When
        val item =
            sampleListItemEntity(weightKg = null, comment = "сразу после сна")
                .toListItem()

        // Then — комментарий проброшен в доменную модель как есть
        assertEquals("сразу после сна", item.comment)
    }

    @Test
    fun toListItem_when_comment_null_then_maps_null() {
        // Given — проекция без комментария

        // When
        val item = sampleListItemEntity(weightKg = null, comment = null).toListItem()

        // Then
        assertEquals(null, item.comment)
    }

    @Test
    fun toListItem_when_iconNumber_present_then_maps_to_domain() {
        // Given — проекция строки записи с иконкой подтягиваний
        // (exercise-icon-display: JOIN icon_number доезжает в домен)

        // When
        val item =
            sampleListItemEntity(weightKg = null, iconNumber = ExerciseIcons.PULLUPS)
                .toListItem()

        // Then — номер иконки проброшен в доменную модель
        assertEquals(ExerciseIcons.PULLUPS, item.iconNumber)
    }

    @Test
    fun toListItem_when_iconNumber_null_then_maps_null() {
        // Given — проекция строки записи упражнения без иконки

        // When
        val item =
            sampleListItemEntity(weightKg = null, iconNumber = null).toListItem()

        // Then — отсутствие иконки не теряется
        assertEquals(null, item.iconNumber)
    }

    // ---------- overview-screen: проекция записи с данными упражнения ----------

    private fun sampleWithExerciseEntity(
        exerciseComment: String?,
        comment: String?,
        weightKg: Double?
    ) = EntryListItemWithExerciseEntity(
        id = 3L,
        exerciseId = 7L,
        exerciseName = "Подтягивания",
        iconNumber = ExerciseIcons.PULLUPS,
        category = "PULL",
        measuredBy = "REPS",
        exerciseComment = exerciseComment,
        sets = 4,
        reps = 10,
        durationSec = null,
        weightKg = weightKg,
        comment = comment,
        date = 1_700_000_000_000L
    )

    @Test
    fun toEntryWithExercise_when_all_fields_then_maps_to_domain() {
        // Given — проекция обзора с полными данными каталога

        // When
        val item =
            sampleWithExerciseEntity(
                exerciseComment = "Хват шире плеч",
                comment = "сразу после сна",
                weightKg = 5.0
            ).toEntryWithExercise()

        // Then — все поля доезжают, категория и тип измерения переводятся в enum
        assertEquals(3L, item.id)
        assertEquals(7L, item.exerciseId)
        assertEquals("Подтягивания", item.exerciseName)
        assertEquals(ExerciseIcons.PULLUPS, item.iconNumber)
        assertEquals(ExerciseCategory.PULL, item.category)
        assertEquals(ExerciseMeasuredBy.REPS, item.measuredBy)
        assertEquals("Хват шире плеч", item.exerciseComment)
        assertEquals(4, item.sets)
        assertEquals(10, item.reps)
        assertEquals(5.0, item.weightKg)
        assertEquals("сразу после сна", item.comment)
        assertEquals(1_700_000_000_000L, item.date)
    }

    @Test
    fun toEntryWithExercise_when_optional_fields_null_then_maps_null() {
        // Given — проекция без комментариев и веса (nullable-ветка)

        // When
        val item =
            sampleWithExerciseEntity(
                exerciseComment = null,
                comment = null,
                weightKg = null
            ).toEntryWithExercise()

        // Then — null доезжает без искажений во всех nullable-полях
        assertEquals(null, item.exerciseComment)
        assertEquals(null, item.comment)
        assertEquals(null, item.weightKg)
    }
}
