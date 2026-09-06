package com.myworkouts.ui.state

import com.myworkouts.domain.model.EntryListItem

/**
 * UI State главного экрана (вкладка «Тренировки»).
 *
 * Sealed-иерархия `Loading`/`Success` без отдельного `Error` —
 * сознательное отступление от конвенции AGENTS.md: единственный
 * источник — офлайн Room-Flow, который в штатной работе не
 * завершается ошибкой (workout-entries-list, design Decision 6).
 * Пустое состояние вычисляется из `entries.isEmpty()` на use site,
 * без отдельного sealed-подкласса и без хранимого флага — derived
 * state в UI-состоянии не нужно (design Decision 6).
 *
 * @see com.myworkouts.ui.viewmodel.MainViewModel
 */
sealed class MainUiState {
    /** Начальное состояние до первой эмиссии репозитория. */
    data object Loading : MainUiState()

    /**
     * Загруженное состояние: записи (может быть пустым) и опциональный
     * идентификатор записи, для которой открыт алерт подтверждения
     * удаления.
     *
     * @property entries Список записей (по дате убыванием — сортировка
     *   на стороне SQL, см. `ExerciseEntryDao.observeEntries`);
     *   остаётся для empty-check и `pendingDeleteId`-lookup
     * @property pendingDeleteId Идентификатор записи, для которой
     *   пользователь выбрал «Удалить» в long-press меню и ожидается
     *   подтверждение/отмена в алерте (`null` — алерт закрыт)
     */
    data class Success(
        val entries: List<EntryListItem>,
        val pendingDeleteId: Long? = null
    ) : MainUiState()
}
