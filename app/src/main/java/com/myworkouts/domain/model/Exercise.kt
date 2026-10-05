package com.myworkouts.domain.model

/**
 * Упражнение в каталоге тренировок.
 *
 * Каталог общий для стандартных (prepopulate при первом создании БД) и
 * пользовательских упражнений. Комментарий описывает само упражнение
 * («широкий хват», «техника: спина прямая»), а не конкретное выполнение —
 * комментарий о выполнении живёт в [ExerciseEntry.comment].
 *
 * @property id Идентификатор упражнения (`0` — для нового, БД присвоит свой)
 * @property name Название упражнения
 * @property category Имя категории (ссылка по имени без FK — custom-categories,
 *   design Decision 1; у стандартных — имя-ключ `PUSH`/`PULL`/`LEGS`/`CORE`/`CARDIO`)
 * @property isCustom `true` — создано пользователем; `false` — стандартное
 * @property comment Опциональный комментарий к упражнению
 * @property createdAt Момент создания записи в каталоге (epoch millis)
 * @property measuredBy Тип измерения нагрузки; по умолчанию `REPS` —
 *   большинство упражнений на повторы
 * @property isArchived `true` — скрыто из каталога (экран архива);
 *   `false` — активно (по умолчанию)
 * @property iconNumber Опциональный номер иконки в наборе ресурсов;
 *   `null` — иконка не назначена (по умолчанию; exercise-icon-mvp)
 */
data class Exercise(
    val id: Long,
    val name: String,
    val category: String,
    val isCustom: Boolean,
    val comment: String?,
    val createdAt: Long,
    val measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS,
    val isArchived: Boolean = false,
    val iconNumber: Int? = null
)
