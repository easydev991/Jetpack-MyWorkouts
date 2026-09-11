package com.myworkouts.ui.state

import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy

/**
 * UI State экрана создания упражнения.
 *
 * Текстовые поля хранят сырой ввод пользователя; нормализация `trim()` —
 * единая точка в `ExerciseCreateViewModel.save` (spec «Сохранение — вызов
 * `repository.addExercise` и popBackStack»).
 *
 * @property name Название упражнения — сырой ввод (`null` — не тронуто)
 * @property category Выбранная категория (`null` — не выбрана)
 * @property measuredBy Тип измерения; по умолчанию REPS — большинство
 *   упражнений на повторы
 * @property comment Комментарий — сырой ввод (`null` — не тронут)
 * @property isSaveError Признак провала последнего сохранения
 */
data class ExerciseCreateUiState(
    val name: String? = null,
    val category: ExerciseCategory? = null,
    val measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS,
    val comment: String? = null,
    val isSaveError: Boolean = false
) {
    /**
     * Вычисляемая доступность сохранения: название непустое (после `trim()`)
     * и категория выбрана (spec-сценарии «Пустая форма — canSave = false»,
     * «Пробелы не считаются валидным именем»).
     */
    val canSave: Boolean
        get() = !name.isNullOrBlank() && category != null

    /**
     * Вычисляемое «есть несохранённые изменения»: заполнено любое текстовое
     * поле (`name`/`comment` с отсечением blank) или выбрана категория;
     * `measuredBy` в формулу не входит (spec-сценарий «hasUnsavedChanges
     * вычисляется по name/category/comment»).
     */
    val hasUnsavedChanges: Boolean
        get() = !name.isNullOrBlank() || category != null || !comment.isNullOrBlank()
}
