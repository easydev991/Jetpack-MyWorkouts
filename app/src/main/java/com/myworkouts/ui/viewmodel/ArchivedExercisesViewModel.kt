package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.ui.state.ArchivedExercisesUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Таймаут подписки StateFlow для `WhileSubscribed` — 5 секунд. */
private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

/**
 * ViewModel экрана «Архив упражнений» (exercises-archive,
 * tasks §4).
 *
 * Список приходит реактивно из [WorkoutDataRepository.archivedExercises]
 * (НЕ разовый `getAllExercises()` — тот возвращает все включая
 * активные). Благодаря реактивности восстановление упражнения
 * автоматически убирает его из списка без ручного refresh.
 *
 * `archivedCount` отдельно не подписывается — длина списка архива
 * это и есть счётчик (design Decision 8).
 *
 * @property repository Репозиторий данных тренировок
 */
class ArchivedExercisesViewModel(
    private val repository: WorkoutDataRepository
) : ViewModel() {
    /**
     * UI State экрана: `Loading` до первой эмиссии репозитория, затем
     * `Success` со списком архивных упражнений. `WhileSubscribed(
     * SUBSCRIPTION_TIMEOUT_MS)` — прецедент [MainViewModel] /
     * [ExercisesViewModel]: подписка живёт 5 секунд после отписки,
     * повторные открытия экрана не дёргают SQL.
     */
    val uiState: StateFlow<ArchivedExercisesUiState> =
        repository
            .archivedExercises()
            .map { exercises -> ArchivedExercisesUiState.Success(exercises) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = ArchivedExercisesUiState.Loading
            )

    /**
     * Восстанавливает упражнение из архива: вызывает
     * [WorkoutDataRepository.setArchived] с `isArchived = false`.
     * Список обновляется сам реактивно (Flow архива эмитит обновление);
     * ручного refresh не нужно. `unarchive` молча игнорирует
     * `Result.failure` от репозитория — спека «Failure при подтверждении»
     * на этом экране не описана, восстановление инициируется одиночным
     * тапом без алерта, ошибка маловероятна (offline Room).
     *
     * @param item Архивное упражнение
     */
    fun unarchive(item: Exercise) {
        viewModelScope.launch { repository.setArchived(item.id, false) }
    }

    companion object {
        /**
         * Factory для создания [ArchivedExercisesViewModel]. Ручной DI
         * вместо Hilt, по образцу [ExercisesViewModel.factory] /
         * [MainViewModel.factory].
         *
         * @param repository Репозиторий данных тренировок
         * @return Factory для создания ViewModel
         */
        fun factory(repository: WorkoutDataRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ArchivedExercisesViewModel(repository) }
            }
    }
}
