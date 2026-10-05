package com.myworkouts.ui.state

import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.ExerciseMeasuredBy

/**
 * UI State экрана создания упражнения.
 *
 * Текстовые поля хранят сырой ввод пользователя; нормализация `trim()` —
 * единая точка в `ExerciseCreateViewModel.save` (spec «Сохранение —
 * create/addExercise и edit/updateExercise с popBackStack»).
 *
 * @property name Название упражнения — сырой ввод (`null` — не тронуто)
 * @property categoryName Имя выбранной категории (`null` — не выбрана;
 *   ссылка по имени — custom-categories, design Decision 1)
 * @property availableCategories Активные категории из БД — реактивная
 *   подписка VM на `categories()` репозитория (custom-categories, этап 8)
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
    val categoryName: String? = null,
    val availableCategories: List<Category> = emptyList(),
    val measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS,
    val comment: String? = null,
    val iconNumber: Int? = null,
    val isSaveError: Boolean = false,
    val isEditMode: Boolean = false,
    val hasChanges: Boolean = false,
    val isExerciseMissing: Boolean = false
) {
    /**
     * Список категорий для дропдауна: активные из БД; в edit-режиме
     * архивная текущая категория упражнения добавляется в конец (spec
     * «Текущая архивная категория видна при редактировании»), прочие
     * архивные остаются скрытыми — их нет в [availableCategories].
     * Активная текущая категория не дублируется.
     */
    val dropdownCategories: List<Category>
        get() =
            categoryName
                ?.takeIf { current -> availableCategories.none { it.name == current } }
                ?.let { current -> availableCategories + Category(name = current, colorArgb = 0, isCustom = false) }
                ?: availableCategories

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
                categoryName != null &&
                (!isEditMode || hasChanges) &&
                !isExerciseMissing

    /**
     * Вычисляемое «есть несохранённые изменения»: в режиме создания —
     * заполнено любое текстовое поле (`name`/`comment` с отсечением blank)
     * или выбрана категория или выбрана иконка (`iconNumber != null`),
     * `measuredBy` в формулу не входит (spec-сценарии
     * «hasUnsavedChanges вычисляется по name/category/comment» и
     * «Изменение иконки учитывается в hasUnsavedChanges и hasChanges»);
     * в режиме редактирования — [hasChanges]: предзаполненные поля сами по
     * себе не блокируют закрытие экрана.
     */
    val hasUnsavedChanges: Boolean
        get() =
            if (isEditMode) {
                hasChanges
            } else {
                !name.isNullOrBlank() ||
                    categoryName != null ||
                    !comment.isNullOrBlank() ||
                    iconNumber != null
            }
}
