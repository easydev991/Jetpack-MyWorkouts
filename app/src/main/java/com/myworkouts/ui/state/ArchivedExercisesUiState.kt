package com.myworkouts.ui.state

import com.myworkouts.domain.model.Exercise

/**
 * UI State экрана «Архив упражнений» (exercises-archive,
 * spec «Список архивных упражнений реактивен»).
 *
 * Sealed-иерархия `Loading` / `Success(exercises)` БЕЗ `Error`-ветки —
 * единственный источник — офлайн Room-Flow, который в штатной работе
 * не завершается ошибкой (прецедент `MainUiState`,
 * `ExercisesUiState`). Пустое состояние вычисляется из
 * `exercises.isEmpty()` на use site, без отдельного sealed-подкласса
 * (exercises-archive design Decision 8: «длина списка архива — это и
 * есть количество архивных»).
 *
 * @see com.myworkouts.ui.viewmodel.ArchivedExercisesViewModel
 */
sealed class ArchivedExercisesUiState {
    /** Начальное состояние до первой эмиссии репозитория. */
    data object Loading : ArchivedExercisesUiState()

    /**
     * Загруженное состояние: список архивных упражнений (отсортирован
     * на стороне SQL — `is_custom DESC, created_at DESC, name COLLATE
     * NOCASE`, паттерн [ExercisesUiState]).
     *
     * @property exercises Список архивных упражнений (может быть пустым)
     */
    data class Success(
        val exercises: List<Exercise>
    ) : ArchivedExercisesUiState()
}
