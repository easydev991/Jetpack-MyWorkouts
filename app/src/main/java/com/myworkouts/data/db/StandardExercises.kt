package com.myworkouts.data.db

import android.content.Context
import com.myworkouts.R
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy

/**
 * Список из 13 стандартных упражнений для prepopulate каталога.
 *
 * Названия резолвятся из строковых ресурсов текущей локали приложения —
 * локализация фиксируется строкой, прочитанной из ресурсов в момент первого
 * запуска (design.md Decision 4, Risks). Полнота и корректность набора —
 * контракт, проверяемый [com.myworkouts.androidTest.data.db.WorkoutDatabaseTest]
 * (spec-сценарий «Первый запуск приложения»). Тип измерения: планка
 * и скакалка — `TIME`, остальные — `REPS` (дефолт).
 *
 * @return Список из ровно 13 записей «название + категория + тип
 *   измерения», без дублей
 */
fun standardExercises(context: Context): List<StandardExercise> =
    listOf(
        StandardExercise(
            name = context.getString(R.string.exercise_pullups),
            category = ExerciseCategory.PULL
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_australian_pullups),
            category = ExerciseCategory.PULL
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_squats),
            category = ExerciseCategory.LEGS
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_pushups),
            category = ExerciseCategory.PUSH
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_pushups_knees),
            category = ExerciseCategory.PUSH
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_lunges),
            category = ExerciseCategory.LEGS
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_bars),
            category = ExerciseCategory.PUSH
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_plank),
            category = ExerciseCategory.CORE,
            measuredBy = ExerciseMeasuredBy.TIME
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_crunches),
            category = ExerciseCategory.CORE
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_hanging_leg_raises),
            category = ExerciseCategory.CORE
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_pistol_squats),
            category = ExerciseCategory.LEGS
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_running),
            category = ExerciseCategory.CARDIO
        ),
        StandardExercise(
            name = context.getString(R.string.exercise_jump_rope),
            category = ExerciseCategory.CARDIO,
            measuredBy = ExerciseMeasuredBy.TIME
        )
    )
