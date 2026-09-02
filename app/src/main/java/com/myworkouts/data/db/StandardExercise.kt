package com.myworkouts.data.db

import com.myworkouts.domain.model.ExerciseCategory

/**
 * Пара «название + категория» для prepopulate стандартного упражнения.
 *
 * Название уже разрешено из строкового ресурса — callback создания БД
 * не имеет доступа к `Context` (design.md Decision 4), поэтому
 * [WorkoutDatabase.create] принимает готовый список пар и замыкает его
 * в callback. Без отдельного «resolved»-варианта: локализация фиксируется
 * строкой, прочитанной из ресурсов текущей локали приложения в момент
 * первого запуска (см. Risks в design.md).
 */
data class StandardExercise(
    val name: String,
    val category: ExerciseCategory
)
