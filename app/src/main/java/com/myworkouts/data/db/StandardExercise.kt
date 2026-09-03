package com.myworkouts.data.db

import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy

/**
 * Тройка «название + категория + тип измерения» для prepopulate
 * стандартного упражнения.
 *
 * Название уже разрешено из строкового ресурса — callback создания БД
 * не имеет доступа к `Context` (design.md Decision 4), поэтому
 * [WorkoutDatabase.create] принимает готовый список и замыкает его
 * в callback. Без отдельного «resolved»-варианта: локализация фиксируется
 * строкой, прочитанной из ресурсов текущей локали приложения в момент
 * первого запуска (см. Risks в design.md).
 *
 * @property name Локализованное название упражнения
 * @property category Категория упражнения
 * @property measuredBy Тип измерения; дефолт `REPS` — большинство
 *   стандартных упражнений на повторы, `TIME` задают явно
 *   (планка, скакалка)
 */
data class StandardExercise(
    val name: String,
    val category: ExerciseCategory,
    val measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS
)
