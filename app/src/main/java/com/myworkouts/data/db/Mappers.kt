package com.myworkouts.data.db

import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy

/**
 * Маппинг entity ↔ domain.
 *
 * Категория хранится и переносится строкой (`ExerciseEntity.category` —
 * имя категории, ссылка по имени без FK — custom-categories, design
 * Decision 1): passthrough без преобразований, кастомные имена равноправны
 * со стандартными ключами. Тип измерения `measuredBy` переводится
 * `valueOf`/`name`; сознательно без fallback на «любое» значение (design.md
 * Decision 2): если значение испорчено (ручной дамп БД), `valueOf` падает
 * с `IllegalArgumentException` — тихая подстановка хуже падения,
 * испорченные данные должны быть видны сразу.
 */
internal fun ExerciseEntity.toDomain(): Exercise =
    Exercise(
        id = id,
        name = name,
        category = category,
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
        category = category,
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
 * Строка `categories` → доменная модель [Category] (custom-categories,
 * spec «Домен-модель Category»). Все поля — прямое отображение, без
 * преобразований: имя — ключ как есть.
 */
internal fun CategoryEntity.toDomain(): Category =
    Category(
        id = id,
        name = name,
        colorArgb = colorArgb,
        isCustom = isCustom,
        isArchived = isArchived
    )

/**
 * Доменная модель [Category] → строка `categories`. `createdAt` — вне
 * доменной модели (в бэкапе поля нет): момент вставки передаёт вызывающий
 * (импорт — моментом импорта, prepopulate/пересев — моментом набора).
 */
internal fun Category.toEntity(createdAt: Long): CategoryEntity =
    CategoryEntity(
        id = id,
        name = name,
        colorArgb = colorArgb,
        isCustom = isCustom,
        isArchived = isArchived,
        createdAt = createdAt
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
 * (overview-screen, design Decision 1). Категория переносится строкой
 * как есть (custom-categories, design Decision 1); тип измерения
 * переводится `valueOf`-ом без fallback — тот же контракт, что в
 * [toDomain]: испорченные данные падают громко, тихая подстановка хуже.
 */
internal fun EntryListItemWithExerciseEntity.toEntryWithExercise(): EntryWithExercise =
    EntryWithExercise(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        iconNumber = iconNumber,
        category = category,
        measuredBy = ExerciseMeasuredBy.valueOf(measuredBy),
        exerciseComment = exerciseComment,
        sets = sets,
        reps = reps,
        durationSec = durationSec,
        weightKg = weightKg,
        comment = comment,
        date = date
    )
