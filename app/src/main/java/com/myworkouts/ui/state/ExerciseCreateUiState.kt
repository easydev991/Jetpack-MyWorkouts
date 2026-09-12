package com.myworkouts.ui.state

import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy

/**
 * UI State экрана создания упражнения.
 *
 * Текстовые поля хранят сырой ввод пользователя; нормализация `trim()` —
 * единая точка в `ExerciseCreateViewModel.save` (spec «Сохранение —
 * create/addExercise и edit/updateExercise с popBackStack»).
 *
 * @property name Название упражнения — сырой ввод (`null` — не тронуто)
 * @property category Выбранная категория (`null` — не выбрана)
 * @property measuredBy Тип измерения; по умолчанию REPS — большинство
 *   упражнений на повторы
 * @property comment Комментарий — сырой ввод (`null` — не тронут)
 * @property isSaveError Признак провала последнего сохранения
 * @property isEditMode Истина, если экран открыт с `exerciseId != -1`
 *   (режим редактирования), — в том числе пока упражнение ещё не загружено
 *   или не найдено
 * @property hasChanges Есть отличия формы от загруженного упражнения
 *   (вычисляется ViewModel; всегда `false` в режиме создания)
 * @property isExerciseMissing При открытии edit-режима упражнение
 *   отсутствует в БД (`getExercise` → `success(null)` или `failure`) —
 *   показать подсказку под кнопкой сохранения. Не меняется после
 *   `init {}` — отражает результат однократной загрузки при открытии экрана
 */
data class ExerciseCreateUiState(
    val name: String? = null,
    val category: ExerciseCategory? = null,
    val measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS,
    val comment: String? = null,
    val isSaveError: Boolean = false,
    val isEditMode: Boolean = false,
    val hasChanges: Boolean = false,
    val isExerciseMissing: Boolean = false
) {
    /**
     * Вычисляемая доступность сохранения: название непустое (после `trim()`)
     * и категория выбрана; в edit-режиме дополнительно требуются изменения
     * относительно оригинала; `isExerciseMissing` — защитный guard: при
     * отсутствующем упражнении сохранение недоступно независимо от полей
     * формы (spec-сценарий «Упражнение не найдено с заполненной формой —
     * canSave всё равно false»).
     */
    val canSave: Boolean
        get() =
            !name.isNullOrBlank() &&
                category != null &&
                (!isEditMode || hasChanges) &&
                !isExerciseMissing

    /**
     * Вычисляемое «есть несохранённые изменения»: в режиме создания —
     * заполнено любое текстовое поле (`name`/`comment` с отсечением blank)
     * или выбрана категория, `measuredBy` в формулу не входит
     * (spec-сценарий «hasUnsavedChanges вычисляется по name/category/comment»);
     * в режиме редактирования — [hasChanges]: предзаполненные поля сами по
     * себе не блокируют закрытие экрана.
     */
    val hasUnsavedChanges: Boolean
        get() =
            if (isEditMode) {
                hasChanges
            } else {
                !name.isNullOrBlank() || category != null || !comment.isNullOrBlank()
            }
}
