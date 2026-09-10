package com.myworkouts.ui.state

import com.myworkouts.domain.model.Exercise

/**
 * UI State экрана «Упражнения» (вкладка каталога).
 *
 * Sealed-иерархия `Loading`/`Success` без отдельного `Error` —
 * копия прецедента [MainUiState] (exercises-tab, design Decision 2):
 * единственный источник — офлайн Room-Flow, который в штатной работе
 * не завершается ошибкой. Пустое состояние вычисляется из
 * `exercises.isEmpty()` на use site, без отдельного sealed-подкласса.
 *
 * @see com.myworkouts.ui.viewmodel.ExercisesViewModel
 */
sealed class ExercisesUiState {
    /** Начальное состояние до первой эмиссии репозитория. */
    data object Loading : ExercisesUiState()

    /**
     * Загруженное состояние: каталог упражнений (стандартные +
     * пользовательские, отсортирован по имени — сортировка на стороне
     * SQL, `ExerciseDao.observeExercises`).
     *
     * @property exercises Список упражнений каталога (может быть пустым)
     */
    data class Success(
        val exercises: List<Exercise>
    ) : ExercisesUiState()
}
