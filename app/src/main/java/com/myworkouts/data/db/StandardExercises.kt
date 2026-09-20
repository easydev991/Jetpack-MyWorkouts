package com.myworkouts.data.db

import android.content.Context
import com.myworkouts.R
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.icons.ExerciseIcons

/**
 * Список из 13 стандартных упражнений для prepopulate каталога.
 *
 * Названия резолвятся из строковых ресурсов текущей локали приложения —
 * локализация фиксируется строкой, прочитанной из ресурсов в момент первого
 * запуска (design.md Decision 4, Risks). Полнота и корректность набора —
 * контракт, проверяемый [com.myworkouts.data.db.WorkoutDatabaseTest]
 * (spec-сценарий «Первый запуск приложения»). Тип измерения: бег, планка
 * и скакалка — `TIME`, остальные 10 — `REPS` (дефолт).
 *
 * Номера иконок — по фиксированной таблице exercise-icon-display
 * (design Decision 5); планка переиспользует иконку отжиманий — отдельной
 * иконки plank нет, при появлении — пересмотреть.
 *
 * @return Список из ровно 13 записей «название + категория + тип
 *   измерения + номер иконки», без дублей
 */
fun standardExercises(context: Context): List<StandardExercise> {
    fun exercise(
        nameRes: Int,
        category: ExerciseCategory,
        iconNumber: Int,
        measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS
    ) = StandardExercise(
        name = context.getString(nameRes),
        category = category,
        measuredBy = measuredBy,
        iconNumber = iconNumber
    )

    return listOf(
        exercise(R.string.exercise_pullups, ExerciseCategory.PULL, ExerciseIcons.PULLUPS),
        exercise(R.string.exercise_australian_pullups, ExerciseCategory.PULL, ExerciseIcons.PULLUPS_AUSTRALIAN),
        exercise(R.string.exercise_squats, ExerciseCategory.LEGS, ExerciseIcons.SQUATS),
        exercise(R.string.exercise_pushups, ExerciseCategory.PUSH, ExerciseIcons.PUSHUPS),
        exercise(R.string.exercise_pushups_knees, ExerciseCategory.PUSH, ExerciseIcons.PUSHUPS_KNEE),
        exercise(R.string.exercise_lunges, ExerciseCategory.LEGS, ExerciseIcons.LUNGES),
        exercise(R.string.exercise_bars, ExerciseCategory.PUSH, ExerciseIcons.BARS),
        // Планка: отдельной иконки plank нет, переиспользуем иконку отжиманий
        // (exercise-icon-display, design Decision 5) — при появлении иконки
        // plank пересмотреть
        exercise(
            R.string.exercise_plank,
            ExerciseCategory.CORE,
            ExerciseIcons.PUSHUPS,
            measuredBy = ExerciseMeasuredBy.TIME
        ),
        exercise(R.string.exercise_crunches, ExerciseCategory.CORE, ExerciseIcons.CRUNCHES),
        exercise(R.string.exercise_hanging_leg_raises, ExerciseCategory.CORE, ExerciseIcons.ABS_HANGING_LEG_RAISES),
        exercise(R.string.exercise_pistol_squats, ExerciseCategory.LEGS, ExerciseIcons.SQUATS_PISTOL),
        exercise(R.string.exercise_running, ExerciseCategory.CARDIO, ExerciseIcons.RUN, ExerciseMeasuredBy.TIME),
        exercise(
            R.string.exercise_jump_rope,
            ExerciseCategory.CARDIO,
            ExerciseIcons.JUMPING_ROPE,
            ExerciseMeasuredBy.TIME
        )
    )
}
