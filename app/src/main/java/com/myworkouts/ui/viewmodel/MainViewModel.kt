package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.ui.state.MainUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Таймаут подписки StateFlow — как в [EntryCreateViewModel] и [AppearanceViewModel]. */
private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

/**
 * ViewModel главного экрана (вкладка «Тренировки»).
 *
 * Список записей приходит реактивно из [WorkoutDataRepository.entries]
 * (workout-entries-list, spec «Реактивный список записей»). Удаление
 * через long-press меню — три шага: [requestDelete] выставляет
 * `pendingDeleteId` (UI показывает алерт), [confirmDelete] вызывает
 * [WorkoutDataRepository.deleteEntry] (список обновляется сам через
 * реактивный источник — ручного refresh не нужно), [cancelDelete]
 * очищает pending без удаления.
 *
 * UI — «глупый» рендер [MainUiState]; вся логика выбора/удаления —
 * здесь. `pendingDeleteId` живёт в отдельном `MutableStateFlow` и
 * подмешивается в [MainUiState.Success] через `combine`, чтобы
 * обновление списка (новая запись, удаление) не сбрасывало
 * идентификатор открытого алерта до явного подтверждения/отмены.
 *
 * @property repository Репозиторий данных тренировок
 */
class MainViewModel(
    private val repository: WorkoutDataRepository
) : ViewModel() {
    /** Транзитное: id записи, для которой открыт алерт удаления. */
    private val pendingDeleteId = MutableStateFlow<Long?>(null)

    /**
     * UI State главного экрана: `Loading` до первой эмиссии
     * репозитория, затем `Success` со списком записей и `pendingDeleteId`.
     */
    val uiState: StateFlow<MainUiState> =
        combine(
            pendingDeleteId,
            repository.entries()
        ) { pendingId, entries ->
            MainUiState.Success(
                entries = entries,
                pendingDeleteId = pendingId
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = MainUiState.Loading
        )

    /**
     * Запрашивает удаление записи: выставляет её id в `pendingDeleteId`,
     * UI открывает алерт подтверждения (design Decision 4).
     */
    fun requestDelete(item: EntryListItem) {
        pendingDeleteId.update { item.id }
    }

    /**
     * Подтверждает удаление: вызывает [WorkoutDataRepository.deleteEntry]
     * для текущего `pendingDeleteId`. Список обновляется сам реактивно;
     * `pendingDeleteId` очищается после успеха — при провале репозитория
     * pending сохраняется, чтобы пользователь мог повторить попытку.
     */
    fun confirmDelete() {
        val id = pendingDeleteId.value ?: return
        viewModelScope.launch {
            repository.deleteEntry(id).onSuccess {
                pendingDeleteId.update { null }
            }
        }
    }

    /** Отменяет удаление: очищает `pendingDeleteId`, репозиторий не трогается. */
    fun cancelDelete() {
        pendingDeleteId.update { null }
    }

    companion object {
        /**
         * Factory для создания [MainViewModel]. Ручной DI вместо Hilt,
         * по образцу [EntryCreateViewModel.factory].
         *
         * @param repository Репозиторий данных тренировок
         * @return Factory для создания ViewModel
         */
        fun factory(repository: WorkoutDataRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { MainViewModel(repository) }
            }
    }
}
