package com.myworkouts.data.repository

import com.myworkouts.domain.model.ExerciseEntry

/**
 * Коды ошибок валидации записи о выполнении.
 *
 * Нужны для диагностики и для будущего маппинга на пользовательское
 * сообщение в UI. Сейчас репозиторий просто заворачивает ошибку в
 * [Result.failure] — коды читабельны в логах.
 */
enum class EntryValidationError {
    /** Подходов меньше 1. */
    INVALID_SETS,

    /** Повторы заданы и меньше 1. */
    INVALID_REPS,

    /** Вес задан и меньше 0. */
    INVALID_WEIGHT,

    /** `workoutId != null` — тренировки появятся в будущем, MVP отклоняет. */
    WORKOUT_NOT_SUPPORTED
}

/**
 * Pure-валидатор полей записи о выполнении.
 *
 * Единая точка валидации — design.md Decision 6:
 * - `sets >= 1`
 * - `reps` либо `null`, либо `>= 1`
 * - `weightKg` либо `null` (собственный вес), либо `>= 0` (0.0 — нулевой
 *   вес, это отдельное валидное состояние, см. spec-сценарий «Отсутствие
 *   веса и нулевой вес различимы»)
 * - `workoutId == null` (MVP — одиночные записи, без привязки к тренировке)
 *
 * Альтернативы (CHECK-констрейнты в БД, валидация в UI) отклонены:
 * дублируют логику / не защищают от немодальных вызовов.
 */
object EntryValidator {
    // ponytail: валидатор — серия независимых проверок, ранний return на
    // каждой короче и понятнее вложенного when. 4 правила → 5 returns
    // (включая Result.success в конце) — это не god-function, это и есть
    // дизайн валидатора.
    @Suppress("ReturnCount")
    fun validate(entry: ExerciseEntry): Result<Unit> {
        if (entry.sets < 1) {
            return Result.failure(EntryValidationException(EntryValidationError.INVALID_SETS))
        }
        if (entry.reps != null && entry.reps < 1) {
            return Result.failure(EntryValidationException(EntryValidationError.INVALID_REPS))
        }
        if (entry.weightKg != null && entry.weightKg < 0.0) {
            return Result.failure(EntryValidationException(EntryValidationError.INVALID_WEIGHT))
        }
        if (entry.workoutId != null) {
            return Result.failure(EntryValidationException(EntryValidationError.WORKOUT_NOT_SUPPORTED))
        }
        return Result.success(Unit)
    }
}

/** Исключение валидации — содержит код ошибки для диагностики. */
class EntryValidationException(
    val error: EntryValidationError
) : IllegalArgumentException("Entry validation failed: $error")
