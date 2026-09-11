package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.state.ExerciseCreateUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Таймаут подписки StateFlow — как в [AppearanceViewModel]. */
private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

/**
 * ViewModel экрана создания упражнения: ввод названия, категории, типа
 * измерения и комментария; сохранение через
 * [WorkoutDataRepository.addExercise].
 *
 * Логика ввода и нормализация (`trim()` для `name`/`comment` — единая
 * точка здесь, при сохранении) — здесь; UI — «глупый» рендер
 * [ExerciseCreateUiState].
 *
 * @property repository Репозиторий данных тренировок
 */
class ExerciseCreateViewModel(
    private val repository: WorkoutDataRepository
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(ExerciseCreateUiState())

    /**
     * UI State экрана: состояние ввода формы. Каждый колбэк ввода сбрасывает
     * `isSaveError` (spec-сценарий «Ввод после неуспешного сохранения скрывает
     * ошибку»), [save] читает [uiState] — экран и сохранение видят одно
     * состояние.
     */
    val uiState: StateFlow<ExerciseCreateUiState> =
        mutableUiState
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
                initialValue = ExerciseCreateUiState()
            )

    /** Ввод названия; сбрасывает ошибку сохранения. */
    fun onNameChange(text: String) =
        mutableUiState.update {
            it.copy(name = text, isSaveError = false)
        }

    /** Выбор категории; сбрасывает ошибку сохранения. */
    fun onCategoryChange(category: ExerciseCategory) =
        mutableUiState.update {
            it.copy(category = category, isSaveError = false)
        }

    /** Выбор типа измерения; сбрасывает ошибку сохранения. */
    fun onMeasuredByChange(measuredBy: ExerciseMeasuredBy) =
        mutableUiState.update {
            it.copy(measuredBy = measuredBy, isSaveError = false)
        }

    /** Ввод комментария; сбрасывает ошибку сохранения. */
    fun onCommentChange(text: String) =
        mutableUiState.update {
            it.copy(comment = text, isSaveError = false)
        }

    /**
     * Сохраняет новое упражнение как пользовательское
     * (`isCustom = true`, `createdAt` — момент сохранения, design
     * Decision 7). Успех → [onSaved] (навигация назад), провал →
     * [ExerciseCreateUiState.isSaveError], экран открыт.
     */
    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        val name = state.name
        val category = state.category
        // Один guard вместо трёх return: canSave уже требует заполненные
        // name/category, null-проверки — для smart-cast к non-null.
        if (!state.canSave || name == null || category == null) return
        val exercise =
            Exercise(
                id = 0L,
                name = name.trim(),
                category = category,
                isCustom = true,
                comment = state.comment?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = System.currentTimeMillis(),
                measuredBy = state.measuredBy
            )
        viewModelScope.launch {
            repository
                .addExercise(exercise)
                .onSuccess { onSaved() }
                .onFailure { mutableUiState.update { it.copy(isSaveError = true) } }
        }
    }

    companion object {
        /**
         * Фабрика для manual DI (design Decision 4): создаётся в
         * `composable`-блоке `RootScreen.AppNavHost`, сам экран фабрику
         * не строит.
         *
         * @param repository Репозиторий данных тренировок
         * @return Factory для создания ViewModel
         */
        fun factory(repository: WorkoutDataRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ExerciseCreateViewModel(repository)
                }
            }
    }
}
