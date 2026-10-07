package com.myworkouts.ui.state

import com.myworkouts.domain.model.Category
import com.myworkouts.domain.model.DeleteCategoryError
import com.myworkouts.domain.model.DeleteExerciseError
import com.myworkouts.domain.model.Exercise

/**
 * Вкладка экрана «Упражнения» (custom-categories, design Decision 6).
 * Только UI-состояние: хранится в [ExercisesViewModel] (переживает
 * поворот экрана), НЕ персистится между запусками приложения.
 */
enum class ExercisesTab {
    /** Список упражнений (каталог). */
    EXERCISES,

    /** Список активных категорий. */
    CATEGORIES
}

/**
 * UI State экрана «Упражнения» (вкладка каталога + вкладка категорий).
 *
 * Sealed-иерархия `Loading`/`Success` без отдельного `Error` —
 * копия прецедента [MainUiState] (exercises-tab, design Decision 2):
 * единственный источник — офлайн Room-Flow, который в штатной работе
 * не завершается ошибкой. Пустое состояние вычисляется из
 * `exercises.isEmpty()` / `categories.isEmpty()` на use site, без
 * отдельного sealed-подкласса.
 *
 * @see com.myworkouts.ui.viewmodel.ExercisesViewModel
 */
sealed class ExercisesUiState {
    /** Начальное состояние до первой эмиссии репозитория. */
    data object Loading : ExercisesUiState()

    /**
     * Загруженное состояние: каталог упражнений + активные категории +
     * выбранная вкладка + pending-состояния подтверждений (упражнения
     * и категории) + one-shot ошибки + признак непустого архива.
     *
     * @property exercises Список упражнений каталога (может быть пустым)
     * @property categories Активные категории (`categories()` —
     *   стандартные и кастомные, сортировка на стороне SQL;
     *   custom-categories, spec «Вкладка „Категории“ — список»)
     * @property selectedTab Выбранная вкладка (custom-categories,
     *   design Decision 6; переживает поворот, не персистится)
     * @property pendingDeleteId Идентификатор упражнения, для которого
     *   пользователь выбрал «Удалить» в long-press меню и открыт алерт
     *   подтверждения (`null` — алерт закрыт). Копия прецедента
     *   [MainUiState.Success.pendingDeleteId] (edit-delete-exercise
     *   design Decision 3).
     * @property deleteError Причина ошибки удаления упражнения для
     *   одноразового снекбара; `null` — нет ошибки. Сбрасывается
     *   в `null` через `onErrorShown()` после показа.
     * @property archiveError Флаг ошибки архивации упражнения для
     *   одноразового снекбара (guard последнего активного,
     *   prevent-from-archiving-all design Decision 4); `false` — нет
     *   ошибки. Сбрасывается через `onErrorShown()` вместе с
     *   `deleteError` (пара каналов на сущность).
     * @property pendingArchiveId Идентификатор упражнения, для которого
     *   открыт алерт подтверждения архивации (`null` — закрыт).
     *   Паттерн — `pendingDeleteId` (exercises-archive design Decision 7).
     * @property pendingCategoryDeleteId Идентификатор категории, для
     *   которой открыт алерт подтверждения удаления (`null` — закрыт;
     *   custom-categories, spec «Алерт удаления категории»).
     * @property categoryDeleteError Причина ошибки удаления категории
     *   для одноразового снекбара; `null` — нет ошибки. Sealed-тип
     *   [DeleteCategoryError] (custom-categories, design Decision 4);
     *   сбрасывается через `onCategoryErrorShown()` после показа.
     * @property categoryArchiveError Флаг ошибки архивации категории
     *   для одноразового снекбара (guard последней активной,
     *   prevent-from-archiving-all design Decision 4); `false` — нет
     *   ошибки. Сбрасывается через `onCategoryErrorShown()` вместе с
     *   `categoryDeleteError`.
     * @property pendingCategoryArchiveId Идентификатор категории, для
     *   которой открыт алерт подтверждения архивации (`null` — закрыт).
     * @property hasArchived Реактивный признак «в архиве есть
     *   упражнения или категории» — условная видимость кнопки архива
     *   в `TopAppBar` (exercises-archive design Decision 2;
     *   custom-categories, spec «Кнопка архива в TopAppBar»).
     */
    data class Success(
        val exercises: List<Exercise>,
        val categories: List<Category> = emptyList(),
        val selectedTab: ExercisesTab = ExercisesTab.EXERCISES,
        val pendingDeleteId: Long? = null,
        val deleteError: DeleteExerciseError? = null,
        val archiveError: Boolean = false,
        val pendingArchiveId: Long? = null,
        val pendingCategoryDeleteId: Long? = null,
        val categoryDeleteError: DeleteCategoryError? = null,
        val categoryArchiveError: Boolean = false,
        val pendingCategoryArchiveId: Long? = null,
        val hasArchived: Boolean = false
    ) : ExercisesUiState()
}
