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
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.navigation.Screen
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
 * ViewModel экрана упражнения: создание (FAB, `exerciseId == -1`) и
 * редактирование существующего упражнения (`exerciseId != -1`,
 * edit-delete-exercise).
 *
 * Логика ввода и нормализация (`trim()` для `name`/`comment` — единая
 * точка [draftOf], при сохранении) — здесь; UI — «глупый» рендер
 * [ExerciseCreateUiState]. В edit-режиме оригинал загружается в `init {}`,
 * `hasChanges` пересчитывается при каждом изменении формы сравнением
 * черновика с оригиналом.
 *
 * @property repository Репозиторий данных тренировок
 * @param exerciseId Id редактируемого упражнения;
 * [Screen.ExerciseCreate.NO_EXERCISE_ID] — режим создания
 * (edit-delete-exercise, design Decision 1)
 * @property analyticsProvider Провайдер аналитики
 */
class ExerciseCreateViewModel(
    private val repository: WorkoutDataRepository,
    exerciseId: Long = Screen.ExerciseCreate.NO_EXERCISE_ID,
    private val analyticsProvider: AnalyticsProvider
) : ViewModel() {
    /**
     * Загруженное упражнение edit-режима: `null` — режим создания
     * или упражнение не найдено (design Decision 2 — в UiState не попадает).
     */
    private var originalExercise: Exercise? = null

    private val mutableUiState =
        MutableStateFlow(
            ExerciseCreateUiState(isEditMode = exerciseId != Screen.ExerciseCreate.NO_EXERCISE_ID)
        )

    init {
        if (exerciseId != Screen.ExerciseCreate.NO_EXERCISE_ID) {
            viewModelScope.launch {
                repository
                    .getExercise(exerciseId)
                    .onSuccess { exercise ->
                        originalExercise = exercise
                        if (exercise == null) {
                            // Decision 5: упражнения нет — подсказка + disabled Save
                            mutableUiState.update { it.copy(isExerciseMissing = true) }
                        } else {
                            mutableUiState.update { prefillState(it, exercise) }
                        }
                    }.onFailure {
                        // Ошибка загрузки приравнена к «упражнение недоступно»
                        mutableUiState.update { it.copy(isExerciseMissing = true) }
                    }
            }
        }
    }

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
                initialValue = mutableUiState.value
            )

    /** Ввод названия; сбрасывает ошибку сохранения. */
    fun onNameChange(text: String) =
        updateState {
            it.copy(name = text, isSaveError = false)
        }

    /** Выбор категории; сбрасывает ошибку сохранения. */
    fun onCategoryChange(category: ExerciseCategory) =
        updateState {
            it.copy(category = category, isSaveError = false)
        }

    /** Выбор типа измерения; сбрасывает ошибку сохранения. */
    fun onMeasuredByChange(measuredBy: ExerciseMeasuredBy) =
        updateState {
            it.copy(measuredBy = measuredBy, isSaveError = false)
        }

    /** Ввод комментария; сбрасывает ошибку сохранения. */
    fun onCommentChange(text: String) =
        updateState {
            it.copy(comment = text, isSaveError = false)
        }

    /**
     * Сохраняет форму сборщиком [draftOf]: в режиме редактирования —
     * [WorkoutDataRepository.updateExercise] с `id`/`createdAt`/`isCustom`
     * оригинала, в режиме создания — [WorkoutDataRepository.addExercise]
     * как пользовательское (`isCustom = true`, `createdAt` — момент
     * сохранения, design Decision 7). Успех → [onSaved] (навигация назад),
     * провал → [ExerciseCreateUiState.isSaveError], экран открыт.
     */
    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        if (!state.canSave) return
        val original = originalExercise
        val exercise =
            draftOf(state, original)
                ?: return // name/category не заполнены — canSave уже отсёк
        // CREATE/EDIT — по наличию оригинала; на вызове операции (design D10)
        val action = if (original != null) UserActionType.EDIT else UserActionType.CREATE
        analyticsProvider.log(AnalyticsEvent.UserAction(action))
        viewModelScope.launch {
            val result =
                if (original != null) repository.updateExercise(exercise) else repository.addExercise(exercise)
            result
                .onSuccess { onSaved() }
                .onFailure { mutableUiState.update { it.copy(isSaveError = true) } }
        }
    }

    /**
     * Единая точка мутации состояния: после трансформации пересчитывает
     * `hasChanges` сравнением черновика с оригиналом (design Decision 2).
     * В режиме создания оригинала нет — `hasChanges` остаётся `false`.
     */
    private fun updateState(transform: (ExerciseCreateUiState) -> ExerciseCreateUiState) {
        mutableUiState.update { state ->
            val next = transform(state)
            val original = originalExercise
            val draft = draftOf(next, original)
            next.copy(hasChanges = original != null && draft != null && draft != original)
        }
    }

    /**
     * Сборка черновика [Exercise] из полей формы — одна точка нормализации
     * для diff-а `hasChanges` и [save] (design Decision 2). `null` — имя или
     * категория не заполнены (canSave уже отсёк этот случай для [save]).
     * В режиме редактирования `id`/`createdAt`/`isCustom` берутся из
     * оригинала: равенство черновика с оригиналом означает «изменений нет».
     */
    private fun draftOf(
        state: ExerciseCreateUiState,
        original: Exercise?
    ): Exercise? {
        val name = state.name?.trim()?.takeIf { it.isNotEmpty() }
        val category = state.category
        if (name == null || category == null) return null
        return Exercise(
            id = original?.id ?: 0L,
            name = name,
            category = category,
            isCustom = original?.isCustom ?: true,
            comment = state.comment?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = original?.createdAt ?: System.currentTimeMillis(),
            measuredBy = state.measuredBy
        )
    }

    /** Предзаполнение полей формы загруженным упражнением (design Decision 2). */
    private fun prefillState(
        state: ExerciseCreateUiState,
        exercise: Exercise
    ): ExerciseCreateUiState =
        state.copy(
            name = exercise.name,
            category = exercise.category,
            measuredBy = exercise.measuredBy,
            comment = exercise.comment ?: ""
        )

    companion object {
        /**
         * Фабрика для manual DI (design Decision 4): создаётся в
         * `composable`-блоке `RootScreen.AppNavHost`, сам экран фабрику
         * не строит.
         *
         * @param repository Репозиторий данных тренировок
         * @param exerciseId Id редактируемого упражнения;
         * [Screen.ExerciseCreate.NO_EXERCISE_ID] — режим создания
         * @param analyticsProvider Провайдер аналитики
         * @return Factory для создания ViewModel
         */
        fun factory(
            repository: WorkoutDataRepository,
            exerciseId: Long = Screen.ExerciseCreate.NO_EXERCISE_ID,
            analyticsProvider: AnalyticsProvider
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    ExerciseCreateViewModel(repository, exerciseId, analyticsProvider)
                }
            }
    }
}
