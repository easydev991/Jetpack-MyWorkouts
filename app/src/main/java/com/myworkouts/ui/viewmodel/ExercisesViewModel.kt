package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.ui.state.ExercisesUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Таймаут подписки StateFlow — как в [MainViewModel] и [AppearanceViewModel]. */
private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

/**
 * ViewModel экрана «Упражнения» (вкладка каталога).
 *
 * Список приходит реактивно из [WorkoutDataRepository.exercises]
 * с пустым запросом — весь каталог (exercises-tab, design Decision 1;
 * НЕ разовый `getAllExercises()` — тот для экспорта/импорта). Благодаря
 * реактивности будущие создание/удаление упражнений обновляют список
 * без действий экрана и без ручного refresh.
 *
 * UI — «глупый» рендер [ExercisesUiState]; логики экрана пока нет.
 *
 * @property repository Репозиторий данных тренировок
 */
class ExercisesViewModel(
    repository: WorkoutDataRepository
) : ViewModel() {
    /**
     * UI State экрана: `Loading` до первой эмиссии репозитория,
     * затем `Success` со списком каталога.
     */
    val uiState: StateFlow<ExercisesUiState> =
        repository
            .exercises("")
            .map { exercises -> ExercisesUiState.Success(exercises = exercises) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = ExercisesUiState.Loading
            )

    companion object {
        /**
         * Factory для создания [ExercisesViewModel]. Ручной DI вместо Hilt,
         * по образцу [MainViewModel.factory].
         *
         * @param repository Репозиторий данных тренировок
         * @return Factory для создания ViewModel
         */
        fun factory(repository: WorkoutDataRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ExercisesViewModel(repository) }
            }
    }
}
