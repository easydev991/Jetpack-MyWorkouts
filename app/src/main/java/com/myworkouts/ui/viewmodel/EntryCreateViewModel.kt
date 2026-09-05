package com.myworkouts.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.ui.screens.entry.entryDateMillis
import com.myworkouts.ui.state.EntryCreateUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Таймаут подписки StateFlow — как в [AppearanceViewModel]. */
private const val SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L

/**
 * ViewModel экрана создания записи о выполнении.
 *
 * Логика ввода (санитайзинг, сброс типозависимого поля при смене
 * упражнения, сборка [ExerciseEntry]) — здесь; UI — «глупый» рендер
 * [EntryCreateUiState]. Дата/время при открытии — «сейчас» из
 * [nowProvider]; `createdAt`/`modifiedAt` — «сейчас» в момент сохранения.
 *
 * @property repository Репозиторий данных тренировок
 * @property nowProvider Провайдер текущего момента (epoch millis) —
 *   внедряется для детерминированных JVM-тестов
 */
class EntryCreateViewModel(
    private val repository: WorkoutDataRepository,
    private val nowProvider: () -> Long
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(initialState(nowProvider()))

    /**
     * UI State экрана: реактивный каталог из репозитория, объединённый
     * с изменяемым состоянием ввода.
     */
    val uiState: StateFlow<EntryCreateUiState> =
        combine(
            repository.exercises(query = ""),
            mutableUiState
        ) { exercises, state ->
            state.copy(exercises = exercises)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = mutableUiState.value
        )

    /** Выбирает упражнение и сбрасывает типозависимое поле (spec-сценарий «Смена упражнения переключает поле»). */
    fun onExerciseChange(exercise: Exercise) =
        mutableUiState.update {
            it.copy(
                selectedExercise = exercise,
                repsText = "",
                durationSec = null,
                isSaveError = false
            )
        }

    /** Ввод подходов: нецифровые символы и ведущие нули отбрасываются. */
    fun onSetsChange(text: String) =
        mutableUiState.update {
            it.copy(setsText = sanitizeNumber(text), isSaveError = false)
        }

    /** Ввод повторов: нецифровые символы и ведущие нули отбрасываются. */
    fun onRepsChange(text: String) =
        mutableUiState.update {
            it.copy(repsText = sanitizeNumber(text), isSaveError = false)
        }

    /** Ввод доп. веса: десятичный санитайзинг (design Decision 2). */
    fun onWeightChange(text: String) =
        mutableUiState.update {
            it.copy(weightText = sanitizeWeight(text), isSaveError = false)
        }

    /** Выбор длительности (`null` — сброс). */
    fun onDurationChange(durationSec: Int?) =
        mutableUiState.update {
            it.copy(durationSec = durationSec, isSaveError = false)
        }

    /** Смена даты выполнения. */
    fun onDateChange(date: LocalDate) =
        mutableUiState.update {
            it.copy(date = date, isSaveError = false)
        }

    /** Смена времени выполнения. */
    fun onTimeChange(
        hour: Int,
        minute: Int
    ) = mutableUiState.update {
        it.copy(hour = hour, minute = minute, isSaveError = false)
    }

    /** Ввод комментария (опциональное поле). */
    fun onCommentChange(text: String) =
        mutableUiState.update {
            it.copy(comment = text, isSaveError = false)
        }

    /**
     * Сохраняет запись: собирает [ExerciseEntry] (date — из выбранных
     * даты/времени, `createdAt`/`modifiedAt` — `nowProvider`), вызывает
     * [WorkoutDataRepository.addEntry]. Успех → [onSaved] (навигация
     * назад), провал → [EntryCreateUiState.isSaveError], экран открыт.
     */
    fun save(onSaved: () -> Unit) {
        val state = mutableUiState.value
        val exercise = state.selectedExercise ?: return
        if (!state.canSave) return
        viewModelScope.launch {
            val now = nowProvider()
            val entry =
                ExerciseEntry(
                    id = 0L,
                    exerciseId = exercise.id,
                    workoutId = null,
                    date = entryDateMillis(state.date, state.hour, state.minute),
                    sets = state.setsText.toIntOrNull() ?: 1,
                    reps =
                        if (exercise.measuredBy == ExerciseMeasuredBy.REPS) {
                            state.repsText.toIntOrNull()
                        } else {
                            null
                        },
                    weightKg = state.weightText.toDoubleOrNull()?.takeIf { it > 0.0 },
                    durationSec =
                        if (exercise.measuredBy == ExerciseMeasuredBy.TIME) {
                            state.durationSec
                        } else {
                            null
                        },
                    comment = state.comment.ifBlank { null },
                    createdAt = now,
                    modifiedAt = now
                )
            repository
                .addEntry(entry)
                .onSuccess { onSaved() }
                .onFailure { mutableUiState.update { it.copy(isSaveError = true) } }
        }
    }

    private fun initialState(nowMillis: Long): EntryCreateUiState {
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        return EntryCreateUiState(
            date = now.toLocalDate(),
            hour = now.hour,
            minute = now.minute
        )
    }

    companion object {
        /**
         * Factory для создания [EntryCreateViewModel]. Ручной DI вместо Hilt.
         *
         * @param repository Репозиторий данных тренировок
         * @return Factory для создания ViewModel
         */
        fun factory(repository: WorkoutDataRepository): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    EntryCreateViewModel(repository) { System.currentTimeMillis() }
                }
            }
    }
}

/** Оставляет только цифры и отбрасывает ведущие нули («0a1#2» → «12»). */
private fun sanitizeNumber(input: String): String = input.filter { it.isDigit() }.dropWhile { it == '0' }

/**
 * Санитайзинг десятичного ввода веса (design Decision 2): только цифры
 * и один десятичный разделитель; запятая нормализуется в точку; ведущие
 * нули отбрасываются, но одиночный ноль перед точкой сохраняется
 * («000.5» → «0.5»); пустой результат — одиночный «0» («00» → «0»).
 */
private fun sanitizeWeight(input: String): String {
    val normalized =
        input
            .filter { it.isDigit() || it == '.' || it == ',' }
            .replace(',', '.')
    // Не более одной точки — первой: «12.3.4» → «12.34»
    val parts = normalized.split('.', limit = 2)
    val singleDot = if (parts.size == 2) parts[0] + "." + parts[1].filter { it.isDigit() } else normalized
    val withoutLeadingZeros = singleDot.dropWhile { it == '0' }
    return when {
        withoutLeadingZeros.isEmpty() -> "0"
        withoutLeadingZeros.first() == '.' -> "0$withoutLeadingZeros"
        else -> withoutLeadingZeros
    }
}
