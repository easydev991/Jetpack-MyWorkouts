package com.myworkouts.ui.state

import com.myworkouts.domain.model.EntryListItem

/**
 * UI State экрана деталей записи (workout-entry-detail-screen, design
 * Decision 3): взаимоисключающие визуальные состояния — отдельный
 * sealed-иерархией по конвенции проекта.
 */
sealed interface EntryDetailUiState {
    /** До первой эмиссии репозитория — пустой контент. */
    data object Loading : EntryDetailUiState

    /** Запись найдена — детали записи на экране. */
    data class Success(
        val data: EntryListItem
    ) : EntryDetailUiState

    /** Запись отсутствует (`null` от `entryById`) — заглушка «не найдена». */
    data object Error : EntryDetailUiState
}
