package com.myworkouts.data.db

import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy

/**
 * Маппинг entity ↔ domain.
 *
 * Категория хранится строкой (`ExerciseEntity.category`) и переводится
 * в [ExerciseCategory] через `valueOf`/`name`. Сознательно без fallback на
 * «любую» категорию (design.md Decision 2): если категория испорчена
 * (ручной дамп БД), `valueOf` падает с `IllegalArgumentException` —
 * тихая подстановка хуже падения, испорченные данные должны быть видны
 * сразу. Тип измерения `measuredBy` переводится тем же способом.
 */

internal fun ExerciseEntity.toDomain(): Exercise =
    Exercise(
        id = id,
        name = name,
        category = ExerciseCategory.valueOf(category),
        isCustom = isCustom,
        comment = comment,
        createdAt = createdAt,
        measuredBy = ExerciseMeasuredBy.valueOf(measuredBy)
    )

internal fun Exercise.toEntity(): ExerciseEntity =
    ExerciseEntity(
        id = id,
        name = name,
        category = category.name,
        isCustom = isCustom,
        comment = comment,
        createdAt = createdAt,
        measuredBy = measuredBy.name
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

/**
 * Проекция строки списка записей → доменная модель [EntryListItem]
 * (workout-entries-list, design Decision 2). JOIN-сторона даёт название
 * упражнения напрямую — отдельной подписки на каталог не нужно.
 */
internal fun EntryListItemEntity.toListItem(): EntryListItem =
    EntryListItem(
        id = id,
        exerciseName = exerciseName,
        sets = sets,
        reps = reps,
        durationSec = durationSec,
        weightKg = weightKg,
        date = date
    )
