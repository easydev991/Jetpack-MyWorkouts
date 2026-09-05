package com.myworkouts.ui.viewmodel

import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fake [WorkoutDataRepository] на `MutableStateFlow` для JVM-тестов
 * [EntryCreateViewModel]: каталог — реактивный, `addEntry` записывает
 * переданную запись и возвращает настраиваемый результат, остальные
 * операции — заглушки (экран создания их не вызывает).
 */
private class FakeWorkoutDataRepository(
    exercises: List<Exercise>
) : WorkoutDataRepository {
    private val catalog = MutableStateFlow(exercises)

    var addedEntry: ExerciseEntry? = null
        private set

    var addEntryResult: Result<Long> = Result.success(1L)

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow<List<EntryListItem>>(emptyList())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> {
        addedEntry = entry
        return addEntryResult
    }

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)
}

/**
 * Unit-тесты для [EntryCreateViewModel].
 *
 * Каталог фиксирован (REPS- и TIME-упражнение), «сейчас» — константа:
 * начальное состояние пикеров даты/времени предсказуемо. Подписка на
 * `uiState` — через `backgroundScope` (запускает WhileSubscribed),
 * значения читаются синхронно через `.value` после `advanceUntilIdle`
 * (образец — `AppearanceViewModelTest`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EntryCreateViewModelTest {
    private lateinit var testDispatcher: TestDispatcher

    private val repsExercise =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val timeExercise =
        Exercise(
            id = 2L,
            name = "Планка",
            category = ExerciseCategory.CORE,
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.TIME
        )

    private val repository = FakeWorkoutDataRepository(listOf(repsExercise, timeExercise))

    /** 2024-07-15T12:30 UTC — фиксированный «сейчас» для тестов. */
    private val fixedNowMillis = 1_721_046_600_000L

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): EntryCreateViewModel =
        EntryCreateViewModel(
            repository = repository,
            nowProvider = { fixedNowMillis }
        )

    @Test
    fun initial_state_should_load_catalog_prefill_sets_and_disable_save() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            val expectedNow =
                Instant.ofEpochMilli(fixedNowMillis).atZone(ZoneId.systemDefault())

            // When — подписка активирует WhileSubscribed, каталог доезжает
            // из MutableStateFlow через combine
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertEquals(
                listOf(repsExercise, timeExercise),
                state.exercises,
                "Каталог должен быть загружен из репозитория"
            )
            assertEquals(
                expectedNow.toLocalDate(),
                state.date,
                "Дата при открытии — «сейчас» из nowProvider"
            )
            assertEquals(
                expectedNow.hour,
                state.hour,
                "Час при открытии — «сейчас» из nowProvider"
            )
            assertEquals(
                expectedNow.minute,
                state.minute,
                "Минута при открытии — «сейчас» из nowProvider"
            )
            assertEquals("1", state.setsText, "Поле подходов префиллено единицей")
            assertFalse(state.canSave, "Без выбранного упражнения canSave = false")
            assertNull(state.selectedExercise, "Упражнение изначально не выбрано")
        }

    @Test
    fun can_save_when_reps_exercise_without_reps_then_false() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — выбрано REPS-упражнение, повторы не заполнены
            viewModel.onExerciseChange(repsExercise)
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.canSave,
                "REPS без повторов — canSave = false"
            )
        }

    @Test
    fun can_save_when_reps_exercise_with_reps_then_true() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onExerciseChange(repsExercise)
            advanceUntilIdle()
            viewModel.onRepsChange("10")
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.canSave,
                "REPS с повторами и префиллом подходов — canSave = true"
            )
        }

    @Test
    fun can_save_when_time_exercise_without_duration_then_false() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — выбрано TIME-упражнение, длительность не выбрана
            viewModel.onExerciseChange(timeExercise)
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.canSave,
                "TIME без длительности — canSave = false"
            )
        }

    @Test
    fun can_save_when_time_exercise_with_duration_then_true() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onExerciseChange(timeExercise)
            advanceUntilIdle()
            viewModel.onDurationChange(60)
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.canSave,
                "TIME с длительностью — canSave = true"
            )
        }

    @Test
    fun on_exercise_change_when_switching_reps_to_time_then_resets_reps() =
        runTest(testDispatcher) {
            // Given — REPS-упражнение с заполненными повторами
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onExerciseChange(repsExercise)
            viewModel.onRepsChange("10")
            advanceUntilIdle()

            // When — смена на TIME-упражнение
            viewModel.onExerciseChange(timeExercise)
            advanceUntilIdle()

            // Then — типозависимое поле и его значение сброшены
            val state = viewModel.uiState.value
            assertEquals(timeExercise, state.selectedExercise, "Выбрано новое упражнение")
            assertEquals("", state.repsText, "Повторы сброшены при смене упражнения")
            assertNull(state.durationSec, "Длительность сброшена при смене упражнения")
            assertFalse(state.canSave, "После сброса типозависимого поля canSave = false")
        }

    @Test
    fun on_sets_change_should_drop_non_digits_and_leading_zeros() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onSetsChange("0a1#2")
            advanceUntilIdle()

            // Then — нецифровые символы и ведущие нули отброшены
            assertEquals("12", viewModel.uiState.value.setsText, "Санитайзинг подходов: «0a1#2» → «12»")
        }

    @Test
    fun on_reps_change_should_drop_non_digits_and_leading_zeros() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When
            viewModel.onRepsChange("007b")
            advanceUntilIdle()

            // Then
            assertEquals("7", viewModel.uiState.value.repsText, "Санитайзинг повторов: «007b» → «7»")
        }

    @Test
    fun on_weight_change_should_sanitize_decimal_input() =
        runTest(testDispatcher) {
            // Given
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Таблица случаев санитайзинга (design Decision 2)
            val cases =
                listOf(
                    "0" to "0",
                    "00" to "0",
                    "012" to "12",
                    "000,5" to "0.5",
                    "0,5" to "0.5",
                    "0." to "0.",
                    "0.0" to "0.0",
                    "12.5" to "12.5",
                    "5,5" to "5.5",
                    "-5" to "5",
                    "12.3.4" to "12.34",
                    "a1b2" to "12"
                )

            cases.forEach { (input, expected) ->
                // When
                viewModel.onWeightChange(input)
                advanceUntilIdle()

                // Then
                assertEquals(
                    expected,
                    viewModel.uiState.value.weightText,
                    "Санитайзинг веса: «$input» → «$expected»"
                )
            }
        }

    @Test
    fun can_save_when_sets_sanitized_to_empty_then_false() =
        runTest(testDispatcher) {
            // Given — REPS с заполненными повторами
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onExerciseChange(repsExercise)
            viewModel.onRepsChange("10")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canSave, "Пре-условие: canSave = true")

            // When — «00» санитайзится в пустую строку
            viewModel.onSetsChange("00")
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.canSave,
                "Очищенное поле подходов блокирует сохранение"
            )
        }

    @Test
    fun can_save_when_sets_zero_then_false() =
        runTest(testDispatcher) {
            // Given — REPS с заполненными повторами
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onExerciseChange(repsExercise)
            viewModel.onRepsChange("10")
            advanceUntilIdle()

            // When — нулевое поле подходов («0» не проходит правило >= 1)
            viewModel.onSetsChange("0")
            advanceUntilIdle()

            // Then
            assertFalse(
                viewModel.uiState.value.canSave,
                "Нулевое поле подходов блокирует сохранение"
            )
        }

    @Test
    fun save_when_valid_reps_entry_then_calls_add_entry_and_on_saved() =
        runTest(testDispatcher) {
            // Given — валидная REPS-запись, изменённая дата/время и комментарий
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onExerciseChange(repsExercise)
            advanceUntilIdle()
            viewModel.onRepsChange("10")
            viewModel.onSetsChange("3")
            viewModel.onDateChange(LocalDate.of(2024, 7, 10))
            viewModel.onTimeChange(14, 30)
            viewModel.onCommentChange("разминочный подход легче")
            advanceUntilIdle()
            val expectedDateMillis =
                LocalDate
                    .of(2024, 7, 10)
                    .atTime(14, 30)
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
            var onSavedCalled = false

            // When
            viewModel.save { onSavedCalled = true }
            advanceUntilIdle()

            // Then — addEntry вызван с собранной записью
            val entry = repository.addedEntry
            assertNotNull(entry, "addEntry должен быть вызван")
            assertEquals(repsExercise.id, entry?.exerciseId, "exerciseId выбранного упражнения")
            assertEquals(3, entry?.sets, "sets из поля ввода")
            assertEquals(10, entry?.reps, "reps для REPS-упражнения")
            assertNull(entry?.durationSec, "durationSec = null для REPS-упражнения")
            assertNull(entry?.weightKg, "weightKg = null при дефолте «0» (design Decision 1)")
            assertEquals(expectedDateMillis, entry?.date, "date собрана из LocalDate + час/минута")
            assertEquals("разминочный подход легче", entry?.comment, "Комментарий сохранён")
            assertEquals(fixedNowMillis, entry?.createdAt, "createdAt = nowProvider")
            assertEquals(fixedNowMillis, entry?.modifiedAt, "modifiedAt = nowProvider")
            assertTrue(onSavedCalled, "onSaved вызван после успешного сохранения")
            assertFalse(viewModel.uiState.value.isSaveError, "Ошибки нет")
        }

    @Test
    fun save_when_sets_not_touched_then_saves_prefilled_one_set() =
        runTest(testDispatcher) {
            // Given — пользователь не трогал подходы (префилл «1»)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onExerciseChange(timeExercise)
            advanceUntilIdle()
            viewModel.onDurationChange(300)
            advanceUntilIdle()

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then
            val entry = repository.addedEntry
            assertNotNull(entry, "addEntry должен быть вызван")
            assertEquals(1, entry?.sets, "sets = 1 из префилла")
            assertEquals(300, entry?.durationSec, "durationSec для TIME-упражнения")
            assertNull(entry?.reps, "reps = null для TIME-упражнения")
        }

    @Test
    fun save_when_positive_weight_entered_then_saves_it() =
        runTest(testDispatcher) {
            // Given — REPS-запись с введённым весом (запятая → точка)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onExerciseChange(repsExercise)
            viewModel.onRepsChange("10")
            viewModel.onWeightChange("12,5")
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canSave, "Вес не влияет на canSave")

            // When
            viewModel.save { }
            advanceUntilIdle()

            // Then
            assertEquals(
                12.5,
                repository.addedEntry?.weightKg,
                "Введённый вес сохраняется (design Decision 1)"
            )
        }

    @Test
    fun save_when_weight_zero_or_unparseable_then_saves_null() =
        runTest(testDispatcher) {
            // Таблица «нулевых» значений веса → weightKg = null (design Decision 1)
            listOf("0", "00", "0.", "0.0", "a").forEach { weightInput ->
                // Given — валидная запись, поле веса содержит «нулевой» ввод
                val viewModel = createViewModel()
                backgroundScope.launch { viewModel.uiState.collect { } }
                advanceUntilIdle()
                viewModel.onExerciseChange(repsExercise)
                viewModel.onRepsChange("10")
                viewModel.onWeightChange(weightInput)
                advanceUntilIdle()

                // When
                viewModel.save { }
                advanceUntilIdle()

                // Then
                assertNull(
                    repository.addedEntry?.weightKg,
                    "Вес «$weightInput» → weightKg = null («без доп. веса»)"
                )
            }
        }

    @Test
    fun save_when_repository_fails_then_no_on_saved_and_error_state() =
        runTest(testDispatcher) {
            // Given — репозиторий падает
            repository.addEntryResult = Result.failure(IllegalStateException("БД недоступна"))
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onExerciseChange(repsExercise)
            advanceUntilIdle()
            viewModel.onRepsChange("10")
            advanceUntilIdle()
            var onSavedCalled = false

            // When
            viewModel.save { onSavedCalled = true }
            advanceUntilIdle()

            // Then — onSaved не вызван, состояние содержит ошибку
            assertFalse(onSavedCalled, "onSaved не должен вызываться при провале")
            assertTrue(
                viewModel.uiState.value.isSaveError,
                "Состояние должно содержать ошибку сохранения"
            )
        }
}
