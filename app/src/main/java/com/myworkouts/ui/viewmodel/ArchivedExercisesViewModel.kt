package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.ui.state.ArchivedExercisesUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
 * @property analyticsProvider Провайдер аналитики
 */
class ArchivedExercisesViewModel(
    private val repository: WorkoutDataRepository,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    /**
     * UI State экрана: `Loading` до первой эмиссии репозитория, затем
     * `Success` со списком архивных упражнений. `WhileSubscribed(
     * defaultSubscriptionTimeoutMs())` — прецедент [MainViewModel] /
     * [ExercisesViewModel]: подписка живёт 5 секунд после отписки,
     * повторные открытия экрана не дёргают SQL.
     */
    val uiState: StateFlow<ArchivedExercisesUiState> =
        repository
            .archivedExercises()
            .map { exercises -> ArchivedExercisesUiState.Success(exercises) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(defaultSubscriptionTimeoutMs()),
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
        // Действие на вызове операции, до/независимо от исхода (design D10)
        analyticsProvider.log(AnalyticsEvent.UserAction(UserActionType.UNARCHIVE))
        viewModelScope.launch { repository.setArchived(item.id, false) }
    }

    companion object {
        /**
         * Factory для создания [ArchivedExercisesViewModel]. Ручной DI
         * вместо Hilt, по образцу [ExercisesViewModel.factory] /
         * [MainViewModel.factory].
         *
         * @param repository Репозиторий данных тренировок
         * @param analyticsProvider Провайдер аналитики
         * @return Factory для создания ViewModel
         */
        fun factory(
            repository: WorkoutDataRepository,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ArchivedExercisesViewModel(repository, analyticsProvider) }
            }
    }
}
