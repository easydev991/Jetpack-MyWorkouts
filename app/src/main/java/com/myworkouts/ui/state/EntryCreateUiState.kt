package com.myworkouts.ui.state

import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseMeasuredBy
import java.time.LocalDate

/**
 * UI State экрана создания записи о выполнении.
 *
 * @property exercises Каталог упражнений для выпадающего списка
 * @property selectedExercise Выбранное упражнение (`null` — не выбрано)
 * @property setsText Текст поля «Подходы» (только цифры, префилл «1»)
 * @property repsText Текст поля «Повторы» (только для REPS-упражнений)
 * @property durationSec Выбранная длительность в секундах (только для
 *   TIME-упражнений, `null` — не выбрана)
 * @property date Дата выполнения
 * @property hour Час времени выполнения (0–23)
 * @property minute Минута времени выполнения (0–59)
 * @property comment Текст комментария (пустой — без комментария)
 * @property isSaveError Признак провала последнего сохранения
 */
data class EntryCreateUiState(
    val exercises: List<Exercise> = emptyList(),
    val selectedExercise: Exercise? = null,
    val setsText: String = "1",
    val repsText: String = "",
    val durationSec: Int? = null,
    val date: LocalDate = LocalDate.now(),
    val hour: Int = 0,
    val minute: Int = 0,
    val comment: String = "",
    val isSaveError: Boolean = false
) {
    /**
     * Вычисляемая доступность сохранения (spec: «запись не сохраняется,
     * пока не выбрано упражнение / не заполнены подходы ≥ 1 и
     * типозависимое поле»).
     */
    val canSave: Boolean
        get() =
            selectedExercise != null &&
                (setsText.toIntOrNull() ?: 0) >= 1 &&
                when (selectedExercise.measuredBy) {
                    ExerciseMeasuredBy.REPS -> repsText.toIntOrNull() != null
                    ExerciseMeasuredBy.TIME -> durationSec != null
                }
}
