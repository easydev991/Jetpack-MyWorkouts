package com.myworkouts.ui.state

/**
 * UI State экрана создания/редактирования категории (custom-categories,
 * design Decision 7).
 *
 * Текстовое поле хранит сырой ввод пользователя; нормализация `trim()` —
 * единая точка в [com.myworkouts.ui.viewmodel.CategoryCreateViewModel.save]
 * (спека «Поле «Название» — однострочное, обязательное»).
 *
 * @property name Название категории — сырой ввод (`null` — не тронуто)
 * @property isEditMode Истина, если экран открыт с `categoryId != -1`
 *   (режим переименования), — в том числе пока категория ещё не загружена
 *   или не найдена
 * @property hasChanges Есть отличия имени от загруженной категории
 *   (вычисляется ViewModel сравнением после `trim()`; всегда `false`
 *   в режиме создания)
 * @property isDuplicateName Имя совпадает (`name.trim().lowercase()`) с
 *   другой существующей категорией — активной или архивной; в edit-режиме
 *   сама редактируемая категория исключена. Сбрасывается и пересчитывается
 *   при каждом вводе (spec «Валидация уникальности имени категории»)
 * @property isCategoryMissing При открытии edit-режима категория
 *   отсутствует в БД — кнопка «Сохранить» недостижимо disabled
 *   (поведение по паттерну `isExerciseMissing`). Не меняется после
 *   `init {}` — отражает результат однократной загрузки при открытии
 * @property isSaveError Признак провала последнего сохранения
 */
data class CategoryCreateUiState(
    val name: String? = null,
    val isEditMode: Boolean = false,
    val hasChanges: Boolean = false,
    val isDuplicateName: Boolean = false,
    val isCategoryMissing: Boolean = false,
    val isSaveError: Boolean = false
) {
    /**
     * Вычисляемая доступность сохранения: имя непустое, не дубликат; в
     * edit-режиме дополнительно требуются изменения относительно оригинала;
     * `isCategoryMissing` — защитный guard. `isSaveError` НЕ входит в
     * формулу — ошибка не блокирует кнопку, только показывается
     * (spec «ViewModel, UiState и canSave»).
     */
    val canSave: Boolean
        get() =
            !name.isNullOrBlank() &&
                !isDuplicateName &&
                (!isEditMode || hasChanges) &&
                !isCategoryMissing

    /**
     * Вычисляемое «есть несохранённые изменения»: в режиме создания —
     * введено непустое имя; в режиме редактирования — [hasChanges]
     * (предзаполненное имя само по себе не блокирует закрытие экрана).
     */
    val hasUnsavedChanges: Boolean
        get() =
            if (isEditMode) {
                hasChanges
            } else {
                !name.isNullOrBlank()
            }
}
