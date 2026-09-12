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
import com.myworkouts.navigation.Screen
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

/** Минут в часе — для сравнения времени суток при клэмпе на момент открытия. */
private const val MINUTES_PER_HOUR: Int = 60

/**
 * ViewModel экрана записи о выполнении: создание (FAB, `entryId == -1`) и
 * редактирование существующей записи (`entryId != -1`, entry-edit-mode).
 *
 * Логика ввода (санитайзинг, сброс типозависимого поля при смене типа
 * измерения упражнения, сборка [ExerciseEntry]) — здесь; UI — «глупый» рендер
 * [EntryCreateUiState]. Дата/время при открытии — «сейчас» из
 * [nowProvider]; `createdAt`/`modifiedAt` — «сейчас» в момент сохранения
 * (в режиме редактирования `createdAt` берётся из записи).
 *
 * @property repository Репозиторий данных тренировок
 * @property nowProvider Провайдер текущего момента (epoch millis) —
 *   внедряется для детерминированных JVM-тестов
 * @property entryId Id редактируемой записи; [Screen.EntryCreate.NO_ENTRY_ID] — режим создания
 */
class EntryCreateViewModel(
    private val repository: WorkoutDataRepository,
    private val nowProvider: () -> Long,
    private val entryId: Long = Screen.EntryCreate.NO_ENTRY_ID
) : ViewModel() {
    /**
     * Загруженная запись режима редактирования: `null` — режим создания
     * или запись не найдена (Decision 4 — в UiState не попадает).
     */
    private var originalEntry: ExerciseEntry? = null

    /**
     * Момент открытия экрана (epoch millis): фиксируется один раз и далее
     * не пересчитывается (spec-сценарий «Момент открытия не пересчитывается»).
     * Граница для ограничения даты/времени записи.
     */
    private val openMomentMillis: Long = nowProvider()

    private val mutableUiState =
        MutableStateFlow(
            initialState(nowMillis = openMomentMillis, isEditMode = entryId != Screen.EntryCreate.NO_ENTRY_ID)
        )

    init {
        if (entryId != Screen.EntryCreate.NO_ENTRY_ID) {
            viewModelScope.launch {
                repository
                    .getEntry(entryId)
                    .onSuccess { entry ->
                        originalEntry = entry
                        if (entry == null) {
                            // Decision 8: записи нет — пустая форма + подсказка
                            mutableUiState.update { it.copy(isEntryMissing = true) }
                        } else {
                            mutableUiState.update { prefillState(it, entry) }
                        }
                    }.onFailure {
                        // Ошибка загрузки приравнена к «запись недоступна»
                        mutableUiState.update { it.copy(isEntryMissing = true) }
                    }
            }
        }
    }

    /**
     * UI State экрана: реактивный каталог из репозитория, объединённый
     * с изменяемым состоянием ввода. Производные поля (подстановка
     * упражнения по `originalEntry.exerciseId`, `hasChanges`) считает
     * [derivedState]; [save] читает [uiState] — экран и сохранение
     * видят одно состояние.
     */
    val uiState: StateFlow<EntryCreateUiState> =
        combine(
            repository.exercises(query = ""),
            mutableUiState
        ) { exercises, state ->
            derivedState(state, exercises)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
            initialValue = mutableUiState.value
        )

    /**
     * Выбирает упражнение; типозависимое поле стирается только при смене
     * типа измерения (spec-сценарий «Смена упражнения переключает поле»),
     * при смене в пределах одного типа введённое значение сохраняется.
     */
    fun onExerciseChange(exercise: Exercise) =
        mutableUiState.update {
            it.copy(
                selectedExercise = exercise,
                repsText = if (exercise.measuredBy == ExerciseMeasuredBy.REPS) it.repsText else "",
                durationSec =
                    if (exercise.measuredBy == ExerciseMeasuredBy.TIME) it.durationSec else null,
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

    /** Смена даты выполнения; на дне открытия позднее время клэмпится (design Decision 4). */
    fun onDateChange(date: LocalDate) =
        mutableUiState.update {
            val (clampedHour, clampedMinute) = clampToOpenMoment(date, it.hour, it.minute)
            it.copy(date = date, hour = clampedHour, minute = clampedMinute, isSaveError = false)
        }

    /** Смена времени выполнения; на дне открытия время позже момента открытия клэмпится. */
    fun onTimeChange(
        hour: Int,
        minute: Int
    ) = mutableUiState.update {
        val (clampedHour, clampedMinute) = clampToOpenMoment(it.date, hour, minute)
        it.copy(hour = clampedHour, minute = clampedMinute, isSaveError = false)
    }

    /** Ввод комментария (опциональное поле). */
    fun onCommentChange(text: String) =
        mutableUiState.update {
            it.copy(comment = text, isSaveError = false)
        }

    /**
     * Сохраняет запись сборщиком [draftOf]: в режиме редактирования —
     * [WorkoutDataRepository.updateEntry] с `id`/`createdAt` оригинала
     * (`modifiedAt` ставит репозиторий), в режиме создания —
     * [WorkoutDataRepository.addEntry] с «сейчас» из [nowProvider].
     * Успех → [onSaved] (навигация назад), провал →
     * [EntryCreateUiState.isSaveError], экран открыт.
     */
    fun save(onSaved: () -> Unit) {
        val state = uiState.value
        if (!state.canSave) return
        val original = originalEntry
        val entry =
            draftOf(state, original)
                ?: return // selectedExercise == null — canSave уже отсёк
        viewModelScope.launch {
            val result =
                if (original != null) repository.updateEntry(entry) else repository.addEntry(entry)
            result
                .onSuccess { onSaved() }
                .onFailure { mutableUiState.update { it.copy(isSaveError = true) } }
        }
    }

    /**
     * Производные поля состояния (design Decision 3/4): подстановка
     * упражнения из каталога по `originalEntry.exerciseId` — пока не
     * выбрано вручную и есть оригинал (покрывает оба порядка эмита
     * каталога и `getEntry`); `hasChanges` — черновик != оригинал.
     */
    private fun derivedState(
        state: EntryCreateUiState,
        exercises: List<Exercise>
    ): EntryCreateUiState {
        val original = originalEntry
        val selected =
            state.selectedExercise
                ?: original?.let { orig -> exercises.firstOrNull { it.id == orig.exerciseId } }
        val withSelected =
            state.copy(exercises = exercises, selectedExercise = selected)
        val draft = draftOf(withSelected, original)
        return withSelected.copy(
            hasChanges = original != null && draft != null && draft != original
        )
    }

    /**
     * Сборка черновика [ExerciseEntry] из полей формы — одна точка
     * нормализации для diff-а `hasChanges` и [save] (design Decision 4/5).
     * `null` — упражнение не выбрано. В режиме редактирования
     * `id`/`createdAt`/`modifiedAt` берутся из оригинала: равенство
     * черновика с оригиналом означает «изменений нет».
     */
    private fun draftOf(
        state: EntryCreateUiState,
        original: ExerciseEntry?
    ): ExerciseEntry? {
        val exercise = state.selectedExercise ?: return null
        return ExerciseEntry(
            id = original?.id ?: 0L,
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
            createdAt = original?.createdAt ?: nowProvider(),
            modifiedAt = original?.modifiedAt ?: nowProvider()
        )
    }

    /** Предзаполнение полей формы загруженной записью (design Decision 2). */
    private fun prefillState(
        state: EntryCreateUiState,
        entry: ExerciseEntry
    ): EntryCreateUiState {
        val dateTime = Instant.ofEpochMilli(entry.date).atZone(ZoneId.systemDefault())
        return state.copy(
            setsText = entry.sets.toString(),
            repsText = entry.reps?.toString() ?: "",
            durationSec = entry.durationSec,
            weightText = entry.weightKg?.toString() ?: "0",
            date = dateTime.toLocalDate(),
            hour = dateTime.hour,
            minute = dateTime.minute,
            comment = entry.comment ?: ""
        )
    }

    /**
     * Клэмп времени на момент открытия экрана: на дне открытия время позже
     * момента открытия заменяется на момент открытия, без сообщения об
     * ошибке (spec-сценарии «Время на день открытия клэмпится…», «Возврат
     * на день открытия…»; design Decision 4). Для дат в прошлом время любое.
     *
     * @return Час и минута после клэмпа
     */
    private fun clampToOpenMoment(
        date: LocalDate,
        hour: Int,
        minute: Int
    ): Pair<Int, Int> {
        val openMoment = Instant.ofEpochMilli(openMomentMillis).atZone(ZoneId.systemDefault())
        if (date != openMoment.toLocalDate()) return hour to minute
        val selected = hour * MINUTES_PER_HOUR + minute
        val limit = openMoment.hour * MINUTES_PER_HOUR + openMoment.minute
        return if (selected > limit) openMoment.hour to openMoment.minute else hour to minute
    }

    private fun initialState(
        nowMillis: Long,
        isEditMode: Boolean
    ): EntryCreateUiState {
        val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())
        return EntryCreateUiState(
            date = now.toLocalDate(),
            hour = now.hour,
            minute = now.minute,
            openMomentMillis = nowMillis,
            isEditMode = isEditMode
        )
    }

    companion object {
        /**
         * Factory для создания [EntryCreateViewModel]. Ручной DI вместо Hilt.
         *
         * @param repository Репозиторий данных тренировок
         * @param entryId Id редактируемой записи; дефолт — режим создания
         * @return Factory для создания ViewModel
         */
        fun factory(
            repository: WorkoutDataRepository,
            entryId: Long = Screen.EntryCreate.NO_ENTRY_ID
        ): ViewModelProvider.Factory =
            viewModelFactory {
                initializer {
                    EntryCreateViewModel(repository, { System.currentTimeMillis() }, entryId)
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
