package com.myworkouts.ui.viewmodel

import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
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
 * [EntryCreateViewModel]: каталог — реактивный, `addEntry`/`updateEntry`
 * записывают переданную запись и возвращают настраиваемый результат,
 * `getEntry` отвечает настраиваемой записью (`null` — «не найдена»),
 * остальные операции — заглушки.
 */
private class FakeWorkoutDataRepository(
    exercises: List<Exercise>
) : WorkoutDataRepository {
    private val catalog = MutableStateFlow(exercises)

    var addedEntry: ExerciseEntry? = null
        private set

    var addEntryResult: Result<Long> = Result.success(1L)

    /** Ответ `getEntry`; `null` — запись не найдена (режим Decision 8). */
    var entryById: ExerciseEntry? = null

    var updatedEntry: ExerciseEntry? = null
        private set

    override fun exercises(query: String): Flow<List<Exercise>> = catalog

    override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow<List<EntryListItem>>(emptyList())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> {
        addedEntry = entry
        return addEntryResult
    }

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> {
        updatedEntry = entry
        return Result.success(Unit)
    }

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(entryById)

    override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

    // Новые методы контракта (app-data-screen, задача 1.4): заглушки на
    // состоянии Flow для компиляции — EntryCreateViewModel их не вызывает.

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalog.value)

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = Result.success(Unit)
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

    /** Второе REPS-упражнение — для проверки same-type смены упражнения. */
    private val repsExercise2 =
        Exercise(
            id = 3L,
            name = "Отжимания",
            category = ExerciseCategory.PUSH,
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val repository =
        FakeWorkoutDataRepository(listOf(repsExercise, timeExercise, repsExercise2))

    /** 2024-07-15T12:30 UTC — фиксированный «сейчас» для тестов. */
    private val fixedNowMillis = 1_721_046_600_000L

    /** Дата/время записи-оригинала для режима редактирования: 2024-07-10 14:30. */
    private val originalEntryDateMillis: Long =
        LocalDate
            .of(2024, 7, 10)
            .atTime(14, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    /** REPS-запись для редактирования (упражнение — repsExercise из каталога). */
    private val originalEntry =
        ExerciseEntry(
            id = 42L,
            exerciseId = 1L,
            workoutId = null,
            date = originalEntryDateMillis,
            sets = 3,
            reps = 10,
            weightKg = 12.5,
            durationSec = null,
            comment = "разминочный подход легче",
            createdAt = 1_700_000_000_000L,
            modifiedAt = 1_700_000_000_000L
        )

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        entryId: Long = -1L,
        nowProvider: () -> Long = { fixedNowMillis }
    ): EntryCreateViewModel =
        EntryCreateViewModel(
            repository = repository,
            nowProvider = nowProvider,
            entryId = entryId
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
                listOf(repsExercise, timeExercise, repsExercise2),
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
    fun on_exercise_change_same_type_then_keeps_entered_reps() =
        runTest(testDispatcher) {
            // Given — REPS-упражнение с заполненными повторами
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onExerciseChange(repsExercise)
            viewModel.onRepsChange("10")
            advanceUntilIdle()

            // When — смена на другое упражнение того же типа REPS
            viewModel.onExerciseChange(repsExercise2)
            advanceUntilIdle()

            // Then — введённое значение сохранено, форма валидна
            val state = viewModel.uiState.value
            assertEquals(repsExercise2, state.selectedExercise, "Выбрано новое упражнение")
            assertEquals("10", state.repsText, "Повторы не стираются при смене упражнения того же типа")
            assertTrue(state.canSave, "Форма остаётся валидной — canSave = true")
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

    @Test
    fun edit_mode_when_entry_loaded_then_prefills_form_and_disables_save() =
        runTest(testDispatcher) {
            // Given — getEntry возвращает REPS-запись
            repository.entryById = originalEntry

            // When — экран открыт в режиме редактирования
            val viewModel = createViewModel(entryId = originalEntry.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — форма предзаполнена, упражнение подставлено из каталога
            val state = viewModel.uiState.value
            assertEquals(repsExercise, state.selectedExercise, "Упражнение подставлено из каталога по exerciseId")
            assertEquals("3", state.setsText, "Подходы предзаполнены")
            assertEquals("10", state.repsText, "Повторы предзаполнены")
            assertEquals("12.5", state.weightText, "Доп. вес предзаполнен")
            assertEquals(LocalDate.of(2024, 7, 10), state.date, "Дата — из записи")
            assertEquals(14, state.hour, "Час — из записи")
            assertEquals(30, state.minute, "Минута — из записи")
            assertEquals("разминочный подход легче", state.comment, "Комментарий предзаполнен")
            assertTrue(state.isEditMode, "isEditMode — из entryId")
            assertFalse(state.hasChanges, "Сразу после загрузки изменений нет")
            assertFalse(state.canSave, "Кнопка заблокирована без изменений при валидной форме")
            assertFalse(state.isEntryMissing, "Запись найдена")
        }

    @Test
    fun edit_mode_when_field_changed_then_has_changes_then_revert_resets() =
        runTest(testDispatcher) {
            // Given — загруженная запись без изменений
            repository.entryById = originalEntry
            val viewModel = createViewModel(entryId = originalEntry.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — изменены повторы
            viewModel.onRepsChange("12")
            advanceUntilIdle()

            // Then
            assertTrue(viewModel.uiState.value.hasChanges, "Изменение поля — hasChanges = true")
            assertTrue(viewModel.uiState.value.canSave, "Валидная форма с изменениями — canSave = true")

            // When — значение возвращено к исходному
            viewModel.onRepsChange("10")
            advanceUntilIdle()

            // Then — вычисляемый diff: форма снова совпадает с записью
            assertFalse(viewModel.uiState.value.hasChanges, "Возврат к исходному — изменений нет")
            assertFalse(viewModel.uiState.value.canSave, "Кнопка снова заблокирована")
        }

    @Test
    fun edit_mode_when_invalid_change_then_can_save_false() =
        runTest(testDispatcher) {
            // Given — загруженная валидная запись
            repository.entryById = originalEntry
            val viewModel = createViewModel(entryId = originalEntry.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — повторы очищены: изменение есть, форма невалидна
            viewModel.onRepsChange("")
            advanceUntilIdle()

            // Then — валидность сильнее наличия изменений
            assertTrue(viewModel.uiState.value.hasChanges, "Изменение зафиксировано")
            assertFalse(
                viewModel.uiState.value.canSave,
                "Невалидная форма блокирует кнопку независимо от изменений"
            )
        }

    @Test
    fun edit_mode_when_same_type_exercise_changed_then_save_enabled() =
        runTest(testDispatcher) {
            // Given — загруженная REPS-запись без изменений
            repository.entryById = originalEntry
            val viewModel = createViewModel(entryId = originalEntry.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            assertFalse(
                viewModel.uiState.value.canSave,
                "Пре-условие: без изменений кнопка заблокирована"
            )
            assertEquals("10", viewModel.uiState.value.repsText, "Пре-условие: повторы предзаполнены")

            // When — смена упражнения на другое того же типа REPS
            viewModel.onExerciseChange(repsExercise2)
            advanceUntilIdle()

            // Then — типозависимое поле сохранено, кнопка активна
            val state = viewModel.uiState.value
            assertEquals(repsExercise2, state.selectedExercise, "Выбрано новое упражнение")
            assertEquals("10", state.repsText, "Повторы не стираются при смене упражнения того же типа")
            assertTrue(state.hasChanges, "Смена упражнения — hasChanges = true")
            assertTrue(state.canSave, "Валидная форма с изменениями — canSave = true")
        }

    @Test
    fun edit_mode_when_type_switched_then_field_cleared_until_duration_picked() =
        runTest(testDispatcher) {
            // Given — загруженная REPS-запись без изменений
            repository.entryById = originalEntry
            val viewModel = createViewModel(entryId = originalEntry.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // When — смена на упражнение другого типа (TIME)
            viewModel.onExerciseChange(timeExercise)
            advanceUntilIdle()

            // Then — типозависимое поле стёрто, кнопка ждёт длительность
            val state = viewModel.uiState.value
            assertEquals("", state.repsText, "Повторы стёрты при смене типа измерения")
            assertNull(state.durationSec, "Длительность не выбрана")
            assertTrue(state.hasChanges, "Смена упражнения — hasChanges = true")
            assertFalse(state.canSave, "Без длительности кнопка заблокирована")

            // When — выбрана длительность
            viewModel.onDurationChange(60)
            advanceUntilIdle()

            // Then — форма валидна, кнопка активна
            assertTrue(viewModel.uiState.value.canSave, "С длительностью canSave = true")
        }

    @Test
    fun save_in_edit_mode_then_calls_update_entry_and_keeps_id_and_created_at() =
        runTest(testDispatcher) {
            // Given — загруженная запись с изменёнными повторами
            repository.entryById = originalEntry
            val viewModel = createViewModel(entryId = originalEntry.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.onRepsChange("12")
            advanceUntilIdle()
            var onSavedCalled = false

            // When
            viewModel.save { onSavedCalled = true }
            advanceUntilIdle()

            // Then — обновление, а не создание
            val updated = repository.updatedEntry
            assertNotNull(updated, "updateEntry должен быть вызван")
            assertNull(repository.addedEntry, "addEntry не вызывается в режиме редактирования")
            assertEquals(originalEntry.id, updated?.id, "id записи сохранён")
            assertEquals(originalEntry.createdAt, updated?.createdAt, "createdAt записи сохранён")
            assertEquals(12, updated?.reps, "Изменённые повторы записаны")
            assertEquals(3, updated?.sets, "Непроизводные поля — из записи")
            assertEquals("разминочный подход легче", updated?.comment, "Комментарий — из записи")
            assertTrue(onSavedCalled, "onSaved вызван после успешного обновления")
            assertFalse(viewModel.uiState.value.isSaveError, "Ошибки нет")
        }

    @Test
    fun edit_mode_when_entry_missing_then_defaults_edit_mode_and_hint() =
        runTest(testDispatcher) {
            // Given — getEntry отвечает null (запись удалена извне)
            repository.entryById = null

            // When
            val viewModel = createViewModel(entryId = originalEntry.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then — пустая форма, режим редактирования, подсказка (Decision 8)
            val state = viewModel.uiState.value
            assertTrue(state.isEditMode, "isEditMode остаётся true (из entryId)")
            assertTrue(state.isEntryMissing, "isEntryMissing = true — подсказка «Запись была удалена»")
            assertFalse(state.hasChanges, "Изменений нет")
            assertFalse(state.canSave, "Кнопка неактивна")
            assertNull(state.selectedExercise, "Форма — дефолты, без предзаполнения")
            assertEquals("1", state.setsText, "Дефолт режима создания")
        }

    /* Ограничение даты/времени моментом открытия экрана
     * (change date-time-limit-for-entry, задачи 1.1–1.6). */

    @Test
    fun on_time_change_when_date_is_open_day_then_clamps_later_time_to_open_moment() =
        runTest(testDispatcher) {
            // Given — экран открыт: момент открытия = fixedNow, дата = день открытия
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            val open = Instant.ofEpochMilli(fixedNowMillis).atZone(ZoneId.systemDefault())

            // When — на дне открытия выбрано время позже момента открытия
            viewModel.onTimeChange(23, 59)
            advanceUntilIdle()

            // Then — время клэмпится на момент открытия без сообщения об ошибке
            val state = viewModel.uiState.value
            assertEquals(open.hour, state.hour, "Час на дне открытия клэмпится на момент открытия")
            assertEquals(open.minute, state.minute, "Минута на дне открытия клэмпится на момент открытия")
        }

    @Test
    fun on_date_change_when_return_to_open_day_with_late_time_then_clamps_to_open_moment() =
        runTest(testDispatcher) {
            // Given — прошедшая дата с поздним временем (там оно допустимо)
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            val open = Instant.ofEpochMilli(fixedNowMillis).atZone(ZoneId.systemDefault())
            val openDate = open.toLocalDate()
            viewModel.onDateChange(openDate.minusDays(1))
            viewModel.onTimeChange(23, 59)
            advanceUntilIdle()
            assertEquals(23, viewModel.uiState.value.hour, "Пре-условие: на прошлой дате время любое")

            // When — дата возвращена на день открытия экрана
            viewModel.onDateChange(openDate)
            advanceUntilIdle()

            // Then — позднее время клэмпится на момент открытия (design Decision 4)
            val state = viewModel.uiState.value
            assertEquals(openDate, state.date, "Дата — день открытия")
            assertEquals(open.hour, state.hour, "Час клэмпится при возврате на день открытия")
            assertEquals(open.minute, state.minute, "Минута клэмпится при возврате на день открытия")
        }

    @Test
    fun on_time_change_when_date_in_past_then_keeps_any_time() =
        runTest(testDispatcher) {
            // Given — выбрана дата раньше дня открытия экрана
            val viewModel = createViewModel()
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            val openDate = Instant.ofEpochMilli(fixedNowMillis).atZone(ZoneId.systemDefault()).toLocalDate()
            viewModel.onDateChange(openDate.minusDays(1))
            advanceUntilIdle()

            // When — выбрано позднее время
            viewModel.onTimeChange(23, 59)
            advanceUntilIdle()

            // Then — на прошлой дате время любое (spec «Прошлая дата допускает любое время»)
            val state = viewModel.uiState.value
            assertEquals(23, state.hour, "Час на прошлой дате сохраняется")
            assertEquals(59, state.minute, "Минута на прошлой дате сохраняется")
        }

    @Test
    fun open_moment_when_real_time_passed_then_limit_is_open_moment_not_now() =
        runTest(testDispatcher) {
            // Given — nowProvider при повторных вызовах возвращает «протекшее»
            // время: момент открытия зафиксирован только первым вызовом
            var calls = 0
            val viewModel =
                createViewModel(
                    nowProvider = {
                        val current = fixedNowMillis + calls * 3_600_000L
                        calls++
                        current
                    }
                )
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            val open = Instant.ofEpochMilli(fixedNowMillis).atZone(ZoneId.systemDefault())
            assertEquals(1, calls, "Пре-условие: момент открытия взят одним вызовом nowProvider")

            // When — на дне открытия выбрано время позже момента открытия
            // (но раньше «протекшего» now)
            viewModel.onTimeChange(23, 59)
            advanceUntilIdle()

            // Then — ограничение по моменту открытия, а не по текущему now
            // (spec «Момент открытия не пересчитывается»)
            val state = viewModel.uiState.value
            assertEquals(open.hour, state.hour, "Час клэмпится на момент открытия, не на протекший now")
            assertEquals(open.minute, state.minute, "Минута клэмпится на момент открытия, не на протекший now")
        }

    @Test
    fun edit_mode_when_past_entry_loaded_then_kept_and_limits_still_apply() =
        runTest(testDispatcher) {
            // Given — запись из прошлого (2024-07-10 14:30)
            repository.entryById = originalEntry
            val viewModel = createViewModel(entryId = originalEntry.id)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            val open = Instant.ofEpochMilli(fixedNowMillis).atZone(ZoneId.systemDefault())
            val openDate = open.toLocalDate()

            // Then — предзаполненные дата и время записи не меняются (без клэмпа
            // prefill, design Decision 5)
            assertEquals(
                LocalDate.of(2024, 7, 10),
                viewModel.uiState.value.date,
                "Дата записи из прошлого сохранена без клэмпа"
            )
            assertEquals(14, viewModel.uiState.value.hour, "Час записи из прошлого сохранён без клэмпа")
            assertEquals(30, viewModel.uiState.value.minute, "Минута записи из прошлого сохранена без клэмпа")

            // Then — ограничения действуют и в режиме редактирования:
            // на дне открытия позднее время клэмпится
            viewModel.onDateChange(openDate)
            viewModel.onTimeChange(23, 59)
            advanceUntilIdle()
            assertEquals(
                open.hour,
                viewModel.uiState.value.hour,
                "В режиме редактирования время на дне открытия клэмпится"
            )
            assertEquals(open.minute, viewModel.uiState.value.minute, "Минута клэмпится и в режиме редактирования")
        }

    @Test
    fun clamp_when_fixed_non_utc_zone_then_day_boundary_respects_local_date() =
        runTest(testDispatcher) {
            // Given — фиксированная не-UTC зона: страхует UTC-ловушку пикера
            // (design Risks): день открытия — локальная дата момента открытия
            val zone = ZoneId.of("GMT+05:00")
            val originalZone = java.util.TimeZone.getDefault()
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(zone))
            try {
                // Случай 1: открытие 2024-07-15 23:59 GMT+5 — 23:59 того же дня
                // не клэмпится (граница суток)
                val lateOpenMillis =
                    LocalDate
                        .of(2024, 7, 15)
                        .atTime(23, 59)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                val lateViewModel = createViewModel(nowProvider = { lateOpenMillis })
                backgroundScope.launch { lateViewModel.uiState.collect { } }
                advanceUntilIdle()
                lateViewModel.onTimeChange(23, 59)
                advanceUntilIdle()
                assertEquals(
                    23,
                    lateViewModel.uiState.value.hour,
                    "23:59 в день открытия 23:59 не клэмпится (граница суток)"
                )
                assertEquals(59, lateViewModel.uiState.value.minute, "Минута 23:59 сохраняется на границе суток")

                // Случай 2: открытие 2024-07-16 00:01 GMT+5 (в UTC это ещё
                // 15 июля!) — локальный день открытия 16-е, 00:02 клэмпится
                val earlyOpenMillis =
                    LocalDate
                        .of(2024, 7, 16)
                        .atTime(0, 1)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli()
                val earlyViewModel = createViewModel(nowProvider = { earlyOpenMillis })
                backgroundScope.launch { earlyViewModel.uiState.collect { } }
                advanceUntilIdle()
                earlyViewModel.onTimeChange(0, 2)
                advanceUntilIdle()
                assertEquals(
                    0,
                    earlyViewModel.uiState.value.hour,
                    "00:02 на дне открытия (00:01) клэмпится на час открытия"
                )
                assertEquals(1, earlyViewModel.uiState.value.minute, "00:02 клэмпится на минуту открытия")

                // Случай 3: прошедшая дата (15-е) допускает любое время
                earlyViewModel.onDateChange(LocalDate.of(2024, 7, 15))
                earlyViewModel.onTimeChange(23, 59)
                advanceUntilIdle()
                assertEquals(
                    23,
                    earlyViewModel.uiState.value.hour,
                    "На прошлой дате (15-е) 23:59 остаётся: день открытия — 16-е"
                )
            } finally {
                java.util.TimeZone.setDefault(originalZone)
            }
        }
}
