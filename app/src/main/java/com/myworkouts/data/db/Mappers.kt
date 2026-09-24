package com.myworkouts.data.db

import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
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
        measuredBy = ExerciseMeasuredBy.valueOf(measuredBy),
        isArchived = isArchived,
        iconNumber = iconNumber
    )

internal fun Exercise.toEntity(): ExerciseEntity =
    ExerciseEntity(
        id = id,
        name = name,
        category = category.name,
        isCustom = isCustom,
        comment = comment,
        createdAt = createdAt,
        measuredBy = measuredBy.name,
        isArchived = isArchived,
        iconNumber = iconNumber
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
 * Широкая строка JOIN-проекции → доменная модель [EntryListItem]
 * (workout-entries-list, design Decision 2). Колонки каталога
 * (`exerciseId`, `category`, `measuredBy`, `exerciseComment`) списку
 * записей не нужны — сужение широкой проекции до домена происходит здесь,
 * SQL при этом один (design Decision 1).
 */
internal fun EntryListItemWithExerciseEntity.toListItem(): EntryListItem =
    EntryListItem(
        id = id,
        exerciseName = exerciseName,
        iconNumber = iconNumber,
        sets = sets,
        reps = reps,
        durationSec = durationSec,
        weightKg = weightKg,
        comment = comment,
        date = date
    )

/**
 * Проекция строки обзора → доменная модель [EntryWithExercise]
 * (overview-screen, design Decision 1). Категория и тип измерения
 * переводятся `valueOf`-ом без fallback — тот же контракт, что в
 * [toDomain]: испорченные данные падают громко, тихая подстановка хуже.
 */
internal fun EntryListItemWithExerciseEntity.toEntryWithExercise(): EntryWithExercise =
    EntryWithExercise(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        iconNumber = iconNumber,
        category = ExerciseCategory.valueOf(category),
        measuredBy = ExerciseMeasuredBy.valueOf(measuredBy),
        exerciseComment = exerciseComment,
        sets = sets,
        reps = reps,
        durationSec = durationSec,
        weightKg = weightKg,
        comment = comment,
        date = date
    )
