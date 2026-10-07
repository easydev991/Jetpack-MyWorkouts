package com.myworkouts.domain.model

/**
 * Причина ошибки удаления упражнения для UI (edit-delete-exercise,
 * tasks §1.5, design Decision 3). Sealed-тип: варианты — конкретные
 * причины для типизированных снекбаров и `Other` для всего
 * остального (включая race «упражнение не найдено при
 * `confirmDelete`» — design Decision 3, ветка «не найдено»).
 *
 * Маппинг `DeleteExerciseError → R.string.exercise_delete_error_*` —
 * норматив в спек-е `exercises-screen`, requirement «Ошибка удаления —
 * типизированные причины в снекбаре».
 */
sealed class DeleteExerciseError {
    /** FK: на упражнение ссылаются записи (pre-check поймал race). */
    object HasLinkedEntries : DeleteExerciseError()

    /** Удаление стандартного упражнения (UI не должен дойти сюда, safety net). */
    object StandardExercise : DeleteExerciseError()

    /**
     * Упражнение — последнее активное (guard репозитория,
     * prevent-from-archiving-all; safety net — UI скрывает пункт меню,
     * дойти можно через race или suppress-путь).
     */
    object LastActive : DeleteExerciseError()

    /** Прочее (включая «упражнение не найдено»): текст исключения в логе. */
    object Other : DeleteExerciseError()
}
