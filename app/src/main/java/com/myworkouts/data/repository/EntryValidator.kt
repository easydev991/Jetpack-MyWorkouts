package com.myworkouts.data.repository

import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy

/**
 * Коды ошибок валидации записи о выполнении.
 *
 * Нужны для диагностики и для будущего маппинга на пользовательское
 * сообщение в UI. Сейчас репозиторий просто заворачивает ошибку в
 * [Result.failure] — коды читабельны в логах.
 *
 * `MISSING_REPS`/`MISSING_DURATION`/`INVALID_DURATION`/
 * `INCOMPATIBLE_REPS_AND_DURATION` добавлены в workout-entries-list
 * (design Decision 7) — слой данных теперь навязывает соответствие
 * `measuredBy` упражнения.
 */
enum class EntryValidationError {
    /** Подходов меньше 1. */
    INVALID_SETS,

    /** Повторы заданы и меньше 1. */
    INVALID_REPS,

    /** Вес задан и меньше 0. */
    INVALID_WEIGHT,

    /** `workoutId != null` — тренировки появятся в будущем, MVP отклоняет. */
    WORKOUT_NOT_SUPPORTED,

    /** REPS-упражнение: повторы не заданы. */
    MISSING_REPS,

    /** TIME-упражнение: длительность не задана. */
    MISSING_DURATION,

    /** TIME-упражнение: длительность задана и меньше 1. */
    INVALID_DURATION,

    /** Запись содержит и повторы, и длительность одновременно. */
    INCOMPATIBLE_REPS_AND_DURATION
}

/**
 * Pure-валидатор полей записи о выполнении.
 *
 * Единая точка валидации — design.md Decision 6 + workout-entries-list
 * design Decision 7 (соответствие `measuredBy`):
 * - `sets >= 1`
 * - `workoutId == null` (MVP — одиночные записи, без привязки к тренировке)
 * - повторы и длительность не заданы одновременно
 * - для `measuredBy = REPS`: `reps != null` и `reps >= 1`
 * - для `measuredBy = TIME`: `durationSec != null` и `durationSec >= 1`
 * - `weightKg` либо `null` (собственный вес), либо `>= 0` (0.0 — нулевой
 *   вес, это отдельное валидное состояние, см. spec-сценарий «Отсутствие
 *   веса и нулевой вес различимы»)
 *
 * Альтернативы (CHECK-констрейнты в БД, валидация в UI) отклонены:
 * дублируют логику / не защищают от немодальных вызовов.
 */
object EntryValidator {
    // ponytail: валидатор — серия независимых проверок, ранний return на
    // каждой короче и понятнее вложенного when. 7 правил → 8 returns
    // (включая Result.success в конце) — это не god-function, это и есть
    // дизайн валидатора.
    @Suppress("ReturnCount")
    fun validate(
        entry: ExerciseEntry,
        measuredBy: ExerciseMeasuredBy
    ): Result<Unit> {
        if (entry.sets < 1) {
            return Result.failure(EntryValidationException(EntryValidationError.INVALID_SETS))
        }
        if (entry.workoutId != null) {
            return Result.failure(EntryValidationException(EntryValidationError.WORKOUT_NOT_SUPPORTED))
        }
        if (entry.reps != null && entry.durationSec != null) {
            return Result.failure(
                EntryValidationException(EntryValidationError.INCOMPATIBLE_REPS_AND_DURATION)
            )
        }
        when (measuredBy) {
            ExerciseMeasuredBy.REPS -> {
                if (entry.reps == null) {
                    return Result.failure(
                        EntryValidationException(EntryValidationError.MISSING_REPS)
                    )
                }
                if (entry.reps < 1) {
                    return Result.failure(
                        EntryValidationException(EntryValidationError.INVALID_REPS)
                    )
                }
            }
            ExerciseMeasuredBy.TIME -> {
                if (entry.durationSec == null) {
                    return Result.failure(
                        EntryValidationException(EntryValidationError.MISSING_DURATION)
                    )
                }
                if (entry.durationSec < 1) {
                    return Result.failure(
                        EntryValidationException(EntryValidationError.INVALID_DURATION)
                    )
                }
            }
        }
        if (entry.weightKg != null && entry.weightKg < 0.0) {
            return Result.failure(EntryValidationException(EntryValidationError.INVALID_WEIGHT))
        }
        return Result.success(Unit)
    }
}

/** Исключение валидации — содержит код ошибки для диагностики. */
class EntryValidationException(
    val error: EntryValidationError
) : IllegalArgumentException("Entry validation failed: $error")
