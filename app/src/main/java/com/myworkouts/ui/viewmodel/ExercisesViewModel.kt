package com.myworkouts.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.data.repository.LinkedEntriesDeletionException
import com.myworkouts.data.repository.StandardExerciseDeletionException
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.DeleteExerciseError
import com.myworkouts.domain.model.Exercise
import com.myworkouts.ui.state.ExercisesUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Таймаут подписки StateFlow — как в [MainViewModel] и [AppearanceViewModel]. */
private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

/** Тег для [Log.e] в failure-ветках [confirmDelete] (design Decision 3). */
private const val LOG_TAG: String = "ExercisesViewModel"

/**
 * ViewModel экрана «Упражнения» (вкладка каталога).
 *
 * Список приходит реактивно из [WorkoutDataRepository.exercises]
 * с пустым запросом — весь каталог (exercises-tab, design Decision 1;
 * НЕ разовый `getAllExercises()` — тот для экспорта/импорта). Благодаря
 * реактивности будущие создание/удаление упражнений обновляют список
 * без действий экрана и без ручного refresh.
 *
 * Удаление кастомного упражнения — двухшаговое (дизайн `MainScreen` +
 * edit-delete-exercise Decision 3, 8): `requestDelete(item)` →
 * pre-check FK → либо снекбар без диалога (`HasLinkedEntries`), либо
 * `pendingDeleteId = item.id` для алерта; `confirmDelete()` вызывает
 * репозиторий и сбрасывает pending; ошибки репозитория маппит в
 * [DeleteExerciseError]. Алерт не открывается для стандартных упражнений
 * (нет меню — Decision 4) — репозиторий всё равно вернёт
 * [StandardExerciseDeletionException] как safety net, и VM покажет
 * generic снекбар через [DeleteExerciseError.StandardExercise].
 *
 * @property repository Репозиторий данных тренировок
 */
class ExercisesViewModel(
    private val repository: WorkoutDataRepository
) : ViewModel() {
    /**
     * Id упражнения, для которого сейчас показан алерт подтверждения
     * удаления; `null` — алерт закрыт. Обновляется через [requestDelete]
     * / [confirmDelete] / [cancelDelete] (edit-delete-exercise,
     * tasks §2.4, design Decision 3).
     */
    private val pendingDeleteId: MutableStateFlow<Long?> = MutableStateFlow(null)

    /**
     * One-shot причина ошибки удаления для снекбара; `null` — нет ошибки.
     * Сбрасывается в `null` через [onErrorShown] после показа снекбара
     * (design Decision 6 — прецедент `MainViewModel`).
     */
    private val deleteError: MutableStateFlow<DeleteExerciseError?> = MutableStateFlow(null)

    /**
     * Реактивный список id упражнений с записями для pre-check в
     * [requestDelete] (design Decision 8). Приватное поле VM — в UiState
     * не нужно (UI его не рендерит напрямую, кнопки в меню нет, состояние
     * существует только на время тапа).
     *
     * `SharingStarted.Eagerly` — pre-check читает `value` синхронно на
     * тапе, нужен свежий кеш до подписки `uiState` (race в
     * `WhileSubscribed`: первая эмиссия upstream приходит только
     * после того, как `uiState.collectAsState()` подпишется на
     * `combine`). Upstream — одна SQL-выборка (`GROUP BY exercise_id`),
     * постоянная подписка дешёвая.
     */
    private val exerciseIdsWithLinkedEntries =
        repository
            .exerciseIdsWithLinkedEntries()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = emptySet()
            )

    /**
     * UI State экрана: `Loading` до первой эмиссии репозитория,
     * затем `Success` со списком каталога, состоянием подтверждения
     * удаления ([ExercisesUiState.Success.pendingDeleteId]) и one-shot
     * ошибкой ([ExercisesUiState.Success.deleteError]).
     */
    val uiState: StateFlow<ExercisesUiState> =
        combine(
            repository.exercises(""),
            pendingDeleteId,
            deleteError
        ) { exercises, pendingId, error ->
            ExercisesUiState.Success(
                exercises = exercises,
                pendingDeleteId = pendingId,
                deleteError = error
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = ExercisesUiState.Loading
        )

    /**
     * Запрашивает удаление упражнения: если на него ссылаются записи
     * (FK) — сразу снекбар [DeleteExerciseError.HasLinkedEntries] без
     * открытия алерта; иначе — открывает алерт через
     * [ExercisesUiState.Success.pendingDeleteId]. Решение синхронное по
     * кешу [exerciseIdsWithLinkedEntries] — без async-обвязки на тапе
     * (design Decision 8).
     *
     * @param item Упражнение из каталога
     */
    fun requestDelete(item: Exercise) {
        if (item.id in exerciseIdsWithLinkedEntries.value) {
            deleteError.value = DeleteExerciseError.HasLinkedEntries
            return
        }
        pendingDeleteId.value = item.id
    }

    /**
     * Закрывает алерт подтверждения удаления без удаления (кнопка
     * «Отмена» / системная «Назад»).
     */
    fun cancelDelete() {
        pendingDeleteId.value = null
    }

    /**
     * Подтверждает удаление из алерта: вызывает
     * [WorkoutDataRepository.deleteExercise]; при `success` сбрасывает
     * pending, при `failure` маппит исключение в [DeleteExerciseError]
     * (design Decision 3):
     * - [LinkedEntriesDeletionException] → `HasLinkedEntries` (race pre-check);
     * - [StandardExerciseDeletionException] → `StandardExercise`
     *   (safety net — UI не должен сюда дойти, Decision 4);
     * - прочее — `Other` (включая «упражнение не найдено» —
     *   race между pre-check и confirm).
     *
     * Каждая failure-ветка логирует текст исключения через [Log.e] для
     * диагностики (Decision 3).
     */
    fun confirmDelete() {
        val id = pendingDeleteId.value ?: return
        viewModelScope.launch {
            val result = repository.deleteExercise(id)
            pendingDeleteId.value = null
            result
                .onSuccess {
                    deleteError.value = null
                }.onFailure { error ->
                    val mapped: DeleteExerciseError =
                        when (error) {
                            is LinkedEntriesDeletionException -> DeleteExerciseError.HasLinkedEntries
                            is StandardExerciseDeletionException -> DeleteExerciseError.StandardExercise
                            else -> DeleteExerciseError.Other
                        }
                    Log.e(LOG_TAG, "deleteExercise failed for id=$id: ${error.message}", error)
                    deleteError.value = mapped
                }
        }
    }

    /**
     * Сбрасывает [ExercisesUiState.Success.deleteError] после показа
     * снекбара — one-shot семантика (design Decision 6).
     */
    fun onErrorShown() {
        deleteError.value = null
    }

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
