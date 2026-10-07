package com.myworkouts.domain.model

/**
 * Причина ошибки удаления категории для UI (custom-categories, design
 * Decision 4). Sealed-тип по образцу [DeleteExerciseError]: варианты —
 * конкретные причины для типизированных снекбаров и `Other` для всего
 * остального. Маппинг исключений репозитория в варианты — контракт
 * `ExercisesViewModel` (spec `category-data`, requirement «Удаление
 * категории»).
 */
sealed class DeleteCategoryError {
    /** На категорию ссылаются упражнения (активные или архивные). */
    object UsedByExercises : DeleteCategoryError()

    /** Удаление стандартной категории (safety net — UI не предлагает). */
    object StandardCategory : DeleteCategoryError()

    /**
     * Категория — последняя активная (guard репозитория,
     * prevent-from-archiving-all; safety net — UI скрывает пункт меню,
     * дойти можно через race или suppress-путь).
     */
    object LastActive : DeleteCategoryError()

    /** Прочее (включая «категория не найдена»): текст исключения в логе. */
    object Other : DeleteCategoryError()
}
