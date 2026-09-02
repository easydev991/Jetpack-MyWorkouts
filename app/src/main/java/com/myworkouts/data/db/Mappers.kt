package com.myworkouts.data.db

import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry

/**
 * Маппинг entity ↔ domain.
 *
 * Категория хранится строкой (`ExerciseEntity.category`) и переводится
 * в [ExerciseCategory] через `valueOf`/`name`. Сознательно без fallback на
 * «любую» категорию (design.md Decision 2): если категория испорчена
 * (ручной дамп БД), `valueOf` падает с `IllegalArgumentException` —
 * тихая подстановка хуже падения, испорченные данные должны быть видны
 * сразу.
 */

internal fun ExerciseEntity.toDomain(): Exercise =
    Exercise(
        id = id,
        name = name,
        category = ExerciseCategory.valueOf(category),
        isCustom = isCustom,
        comment = comment,
        createdAt = createdAt
    )

internal fun Exercise.toEntity(): ExerciseEntity =
    ExerciseEntity(
        id = id,
        name = name,
        category = category.name,
        isCustom = isCustom,
        comment = comment,
        createdAt = createdAt
    )

internal fun ExerciseEntryEntity.toDomain(): ExerciseEntry =
    ExerciseEntry(
        id = id,
        exerciseId = exerciseId,
        workoutId = workoutId,
        date = date,
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        comment = comment,
        createdAt = createdAt,
        modifiedAt = modifiedAt
    )

internal fun ExerciseEntry.toEntity(): ExerciseEntryEntity =
    ExerciseEntryEntity(
        id = id,
        exerciseId = exerciseId,
        workoutId = workoutId,
        date = date,
        sets = sets,
        reps = reps,
        weightKg = weightKg,
        durationSec = durationSec,
        comment = comment,
        createdAt = createdAt,
        modifiedAt = modifiedAt
    )
