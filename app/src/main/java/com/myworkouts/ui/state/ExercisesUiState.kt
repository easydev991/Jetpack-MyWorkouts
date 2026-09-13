package com.myworkouts.ui.state

import com.myworkouts.domain.model.DeleteExerciseError
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
     * SQL, `ExerciseDao.observeExercises`) + состояние подтверждения
     * удаления + one-shot ошибка удаления + состояние подтверждения
     * архивации + реактивный счётчик архивных.
     *
     * @property exercises Список упражнений каталога (может быть пустым)
     * @property pendingDeleteId Идентификатор упражнения, для которого
     *   пользователь выбрал «Удалить» в long-press меню и открыт алерт
     *   подтверждения (`null` — алерт закрыт). Копия прецедента
     *   [MainUiState.Success.pendingDeleteId] (edit-delete-exercise
     *   design Decision 3).
     * @property deleteError Причина ошибки удаления для одноразового
     *   снекбара; `null` — нет ошибки. Sealed-тип
     *   [DeleteExerciseError] различает FK / стандартное / прочее —
     *   снекбар показывает конкретный текст (Decision 3, 6). Сбрасывается
     *   в `null` через `onErrorShown()` после показа.
     * @property pendingArchiveId Идентификатор упражнения, для которого
     *   пользователь выбрал «Архивировать» в long-press меню и открыт
     *   алерт подтверждения архивации (`null` — алерт закрыт).
     *   Паттерн — `pendingDeleteId` (exercises-archive design Decision 7).
     * @property archivedCount Реактивный счётчик архивных упражнений
     *   из `WorkoutDataRepository.archivedCount()`; используется UI для
     *   условной видимости кнопки архива в `TopAppBar` (exercises-archive
     *   design Decision 2; tasks §6.7.f).
     */
    data class Success(
        val exercises: List<Exercise>,
        val pendingDeleteId: Long? = null,
        val deleteError: DeleteExerciseError? = null,
        val pendingArchiveId: Long? = null,
        val archivedCount: Int = 0
    ) : ExercisesUiState()
}
