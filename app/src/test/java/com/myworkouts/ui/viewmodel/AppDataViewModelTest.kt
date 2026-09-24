package com.myworkouts.ui.viewmodel

import android.net.Uri
import android.util.Log
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AppErrorOperation
import com.myworkouts.analytics.FakeAnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.EntryWithExercise
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.ExerciseMeasuredBy
import com.myworkouts.domain.usecase.BackupError
import com.myworkouts.domain.usecase.BackupException
import com.myworkouts.domain.usecase.ExportBackupUseCase
import com.myworkouts.domain.usecase.ImportBackupUseCase
import com.myworkouts.ui.state.AppDataResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Fake [WorkoutDataRepository] на `MutableStateFlow` для JVM-тестов
 * [AppDataViewModel] (app-data-screen, design Decision 8): `entries`/
 * `exercises` — реактивные, `deleteAllData` — настраиваемый результат.
 * Use-case'ы к репозиторию не идут (они замоканы), поэтому снимки и
 * `importData` — тривиальные заглушки. Прочие операции — заглушки.
 */
private class AppDataViewModelFakeRepository(
    entries: List<EntryListItem> = emptyList(),
    private val exercises: List<Exercise> = emptyList()
) : WorkoutDataRepository {
    private val entriesFlow = MutableStateFlow(entries)
    private val catalogFlow = MutableStateFlow(exercises)

    var deleteAllDataResult: Result<Unit> = Result.success(Unit)

    override fun exercises(query: String): Flow<List<Exercise>> = catalogFlow

    override fun entries(): Flow<List<EntryListItem>> = entriesFlow

    override fun entriesWithExercise(): Flow<List<EntryWithExercise>> = MutableStateFlow(emptyList())

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalogFlow.value)

    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())

    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)

    override suspend fun deleteAllData(): Result<Unit> = deleteAllDataResult

    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)

    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)

    override suspend fun deleteEntry(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)

    override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)

    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

    // Новые методы контракта (exercises-archive, §2.7): заглушки —
    // AppDataViewModel их не вызывает (фильтрация архивных приходит
    // через `exercises`).

    override suspend fun setArchived(
        id: Long,
        isArchived: Boolean
    ): Result<Unit> = Result.success(Unit)

    override fun archivedExercises(): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override fun archivedCount(): Flow<Int> = MutableStateFlow(0)
}

/**
 * Unit-тесты для [AppDataViewModel] (app-data-screen, design Decision 8).
 *
 * Подписка на `uiState` — через `backgroundScope` (запускает
 * WhileSubscribed), значения читаются через `.value` после
 * `advanceUntilIdle` (образец — [EntryCreateViewModelTest]). Use-case'ы —
 * моки: их SAF/IO-обвязка покрыта собственными тестами, здесь важны
 * только переходы `isBusy`/`result` и маппинг [BackupError] в
 * [AppDataResult].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppDataViewModelTest {
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var uri: Uri
    private lateinit var exportUseCase: ExportBackupUseCase
    private lateinit var importUseCase: ImportBackupUseCase

    /** Перехватчик событий аналитики (design D9). */
    private val analyticsProvider = FakeAnalyticsProvider()

    private val standardExercise =
        Exercise(
            id = 1L,
            name = "Подтягивания",
            category = ExerciseCategory.PULL,
            isCustom = false,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val customExercise =
        Exercise(
            id = 2L,
            name = "Бёрпи",
            category = ExerciseCategory.PUSH,
            isCustom = true,
            comment = null,
            createdAt = 0L,
            measuredBy = ExerciseMeasuredBy.REPS
        )

    private val sampleListItem =
        EntryListItem(
            id = 1L,
            exerciseName = "Подтягивания",
            sets = 4,
            reps = 10,
            durationSec = null,
            weightKg = null,
            date = 1_700_000_000_000L
        )

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        // ViewModel логирует ошибки операций: android.util.Log в JVM не
        // замокан по умолчанию и роняет короутину операции
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
        uri = mockk()
        exportUseCase = mockk()
        importUseCase = mockk()
        coEvery { exportUseCase.invoke(any()) } returns Result.success(Unit)
        coEvery { importUseCase.invoke(any()) } returns
            Result.success(Unit)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: AppDataViewModelFakeRepository): AppDataViewModel =
        AppDataViewModel(repository, exportUseCase, importUseCase, analyticsProvider)

    @Test
    fun hasData_false_when_only_standard_exercises() =
        runTest(testDispatcher) {
            // Given — пустые записи, каталог только со стандартным набором
            val repository =
                AppDataViewModelFakeRepository(
                    entries = emptyList(),
                    exercises = listOf(standardExercise)
                )
            val viewModel = viewModel(repository)

            // When
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Then — стандартный набор данными пользователя не считается
            assertFalse(
                viewModel.uiState.value.hasData,
                "Стандартный набор не считается данными пользователя"
            )
        }

    @Test
    fun hasData_true_when_entries_exist() =
        runTest(testDispatcher) {
            // Given — есть хотя бы одна запись о выполнении
            val repository =
                AppDataViewModelFakeRepository(
                    entries = listOf(sampleListItem),
                    exercises = listOf(standardExercise)
                )
            val viewModel = viewModel(repository)

            // When
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.hasData,
                "Записи о выполнении — это данные пользователя"
            )
        }

    @Test
    fun hasData_true_when_only_custom_exercises() =
        runTest(testDispatcher) {
            // Given — записей нет, но есть пользовательское упражнение
            val repository =
                AppDataViewModelFakeRepository(
                    entries = emptyList(),
                    exercises = listOf(standardExercise, customExercise)
                )
            val viewModel = viewModel(repository)

            // When
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // Then
            assertTrue(
                viewModel.uiState.value.hasData,
                "Пользовательское упражнение — это данные пользователя"
            )
        }

    @Test
    fun exportBackup_setsBusy_thenResultExported_andBusyCleared() =
        runTest(testDispatcher) {
            // Given — use-case задержан гейтом: операция «в полёте»
            val repository = AppDataViewModelFakeRepository()
            val gate = CompletableDeferred<Unit>()
            coEvery { exportUseCase.invoke(any()) } coAnswers {
                gate.await()
                Result.success(Unit)
            }
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When — экспорт запущен, но ещё не завершился
            viewModel.exportBackup(uri)
            advanceUntilIdle()

            // Then — busy выставлен, результата ещё нет
            assertTrue(
                viewModel.uiState.value.isBusy,
                "isBusy должен подниматься на время операции"
            )
            assertNull(viewModel.uiState.value.result)

            // When — операция завершается
            gate.complete(Unit)
            advanceUntilIdle()

            // Then — busy сброшен, результат — успех экспорта
            assertFalse(
                viewModel.uiState.value.isBusy,
                "isBusy должен сбрасываться по завершении"
            )
            assertEquals(AppDataResult.EXPORTED, viewModel.uiState.value.result)
            coVerify { exportUseCase.invoke(uri) }
        }

    @Test
    fun exportBackup_whenUseCaseFails_thenErrorExport() =
        runTest(testDispatcher) {
            // Given — use-case вернул типизированную ошибку экспорта
            val failure = BackupException("SAF не отдал поток", BackupError.EXPORT)
            coEvery { exportUseCase.invoke(any()) } returns Result.failure(failure)
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.exportBackup(uri)
            advanceUntilIdle()

            // Then
            assertEquals(AppDataResult.ERROR_EXPORT, viewModel.uiState.value.result)
            assertFalse(viewModel.uiState.value.isBusy)
            val appError =
                analyticsProvider.events.filterIsInstance<AnalyticsEvent.AppError>().single()
            assertEquals(
                AppErrorOperation.CREATE_BACKUP,
                appError.operation,
                "Сбой экспорта — событие AppError(CREATE_BACKUP)"
            )
            assertSame(failure, appError.throwable, "В событии — итоговое исключение")
        }

    @Test
    fun restoreBackup_whenInvalidFile_thenErrorInvalidFile() =
        runTest(testDispatcher) {
            // Given — use-case отверг файл копии
            val failure = BackupException("Файл не является JSON", BackupError.INVALID_FILE)
            coEvery { importUseCase.invoke(any()) } returns Result.failure(failure)
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.restoreFromBackup(uri)
            advanceUntilIdle()

            // Then
            assertEquals(AppDataResult.ERROR_INVALID_FILE, viewModel.uiState.value.result)
            assertFalse(viewModel.uiState.value.isBusy)
            val appError =
                analyticsProvider.events.filterIsInstance<AnalyticsEvent.AppError>().single()
            assertEquals(
                AppErrorOperation.RESTORE_BACKUP,
                appError.operation,
                "Сбой восстановления — событие AppError(RESTORE_BACKUP)"
            )
            assertSame(failure, appError.throwable, "В событии — итоговое исключение")
        }

    @Test
    fun restoreBackup_whenRepositoryFails_thenErrorImport() =
        runTest(testDispatcher) {
            // Given — файл валиден, но вставка в БД падает ошибкой «как есть»
            val failure = IllegalStateException("БД недоступна")
            coEvery { importUseCase.invoke(any()) } returns Result.failure(failure)
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.restoreFromBackup(uri)
            advanceUntilIdle()

            // Then — нетипизированная ошибка мапится в ошибку импорта
            assertEquals(AppDataResult.ERROR_IMPORT, viewModel.uiState.value.result)
            assertFalse(viewModel.uiState.value.isBusy)
            val appError =
                analyticsProvider.events.filterIsInstance<AnalyticsEvent.AppError>().single()
            assertEquals(
                AppErrorOperation.RESTORE_BACKUP,
                appError.operation,
                "Сбой восстановления — событие AppError(RESTORE_BACKUP)"
            )
            assertSame(failure, appError.throwable, "В событии — итоговое исключение")
        }

    @Test
    fun restoreBackup_whenSuccess_thenRestored() =
        runTest(testDispatcher) {
            // Given — валидный файл и успешная вставка
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.restoreFromBackup(uri)
            advanceUntilIdle()

            // Then
            assertEquals(AppDataResult.RESTORED, viewModel.uiState.value.result)
            assertFalse(viewModel.uiState.value.isBusy)
            coVerify { importUseCase.invoke(uri) }
        }

    @Test
    fun deleteAllData_whenSuccess_thenDeleted() =
        runTest(testDispatcher) {
            // Given
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.deleteAllData()
            advanceUntilIdle()

            // Then
            assertEquals(AppDataResult.DELETED, viewModel.uiState.value.result)
            assertFalse(viewModel.uiState.value.isBusy)
        }

    @Test
    fun deleteAllData_whenRepositoryFails_thenErrorDelete() =
        runTest(testDispatcher) {
            // Given — репозиторий не смог выполнить массовый сброс
            val repository = AppDataViewModelFakeRepository()
            val failure = IllegalStateException("БД недоступна")
            repository.deleteAllDataResult = Result.failure(failure)
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.deleteAllData()
            advanceUntilIdle()

            // Then
            assertEquals(AppDataResult.ERROR_DELETE, viewModel.uiState.value.result)
            assertFalse(viewModel.uiState.value.isBusy)
            val appError =
                analyticsProvider.events.filterIsInstance<AnalyticsEvent.AppError>().single()
            assertEquals(
                AppErrorOperation.DELETE_ALL_DATA,
                appError.operation,
                "Сбой массового удаления — событие AppError(DELETE_ALL_DATA)"
            )
            assertSame(failure, appError.throwable, "В событии — итоговое исключение")
        }

    @Test
    fun clearResult_resetsResultToNull() =
        runTest(testDispatcher) {
            // Given — после успешного экспорта результат выставлен
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()
            viewModel.exportBackup(uri)
            advanceUntilIdle()
            assertEquals(AppDataResult.EXPORTED, viewModel.uiState.value.result)

            // When
            viewModel.clearResult()
            advanceUntilIdle()

            // Then — одноразовое событие сброшено
            assertNull(viewModel.uiState.value.result)
        }

    // ---------- Телеметрия (firebase-crashlytics-mvp, задачи 6.1/6.2) ----------

    /**
     * Экспорт копии эмитит `UserAction(CREATE_BACKUP)` независимо от
     * исхода — на вызове операции, после выбора файла в SAF
     * (design D10). На успехе других событий нет.
     */
    @Test
    fun exportBackup_emits_user_action_create_backup() =
        runTest(testDispatcher) {
            // Given
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.exportBackup(uri)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.CREATE_BACKUP)),
                analyticsProvider.events,
                "Экспорт копии — user_action CREATE_BACKUP"
            )
        }

    /** Восстановление из копии эмитит `UserAction(RESTORE_BACKUP)` (design D10). */
    @Test
    fun restoreBackup_emits_user_action_restore_backup() =
        runTest(testDispatcher) {
            // Given
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.restoreFromBackup(uri)
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.RESTORE_BACKUP)),
                analyticsProvider.events,
                "Восстановление копии — user_action RESTORE_BACKUP"
            )
        }

    /** Удаление всех данных эмитит `UserAction(DELETE_ALL_DATA)` (design D10). */
    @Test
    fun deleteAllData_emits_user_action_delete_all_data() =
        runTest(testDispatcher) {
            // Given
            val repository = AppDataViewModelFakeRepository()
            val viewModel = viewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.deleteAllData()
            advanceUntilIdle()

            // Then
            assertEquals(
                listOf(AnalyticsEvent.UserAction(UserActionType.DELETE_ALL_DATA)),
                analyticsProvider.events,
                "Удаление всех данных — user_action DELETE_ALL_DATA"
            )
        }
}
