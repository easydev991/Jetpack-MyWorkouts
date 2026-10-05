package com.myworkouts.ui.state

import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.Exercise

/**
 * Вкладка экрана «Архив» (custom-categories, tasks 7.2). Только
 * UI-состояние: хранится в [com.myworkouts.ui.viewmodel.ArchivedExercisesViewModel]
 * (переживает поворот экрана), НЕ персистится между запусками.
 */
enum class ArchivedTab {
    /** Список архивных упражнений. */
    EXERCISES,

    /** Список архивных категорий. */
    CATEGORIES
}

/**
 * UI State экрана «Архив» (exercises-archive +
 * custom-categories, spec «Список архивных упражнений реактивен»).
 *
 * Sealed-иерархия `Loading` / `Success(exercises, categories, selectedTab)`
 * БЕЗ `Error`-ветки — единственный источник — офлайн Room-Flow, который
 * в штатной работе не завершается ошибкой (прецедент [com.myworkouts.ui.state.MainUiState],
 * [com.myworkouts.ui.state.ExercisesUiState]). Пустое состояние вычисляется
 * из `exercises.isEmpty() && categories.isEmpty()` на use site, без
 * отдельного sealed-подкласса (exercises-archive design Decision 8:
 * «длина списка архива — это и есть количество архивных»).
 *
 * @see com.myworkouts.ui.viewmodel.ArchivedExercisesViewModel
 */
sealed class ArchivedExercisesUiState {
    /** Начальное состояние до первой эмиссии репозитория. */
    data object Loading : ArchivedExercisesUiState()

    /**
     * Загруженное состояние: архив упражнений + архив категорий +
     * выбранная вкладка (списки отсортированы на стороне SQL —
     * `is_custom DESC, created_at DESC, name COLLATE NOCASE`, паттерн
     * [ExercisesUiState.Success]).
     *
     * @property exercises Список архивных упражнений (может быть пустым)
     * @property categories Список архивных категорий (может быть пустым;
     *   custom-categories, spec «Табы „Упражнения“ / „Категории“»)
     * @property selectedTab Выбранная вкладка (custom-categories,
     *   spec «Табы „Упражнения“ / „Категории“»; переживает поворот,
     *   не персистится)
     */
    data class Success(
        val exercises: List<Exercise>,
        val categories: List<Category> = emptyList(),
        val selectedTab: ArchivedTab = ArchivedTab.EXERCISES
    ) : ArchivedExercisesUiState()
}
