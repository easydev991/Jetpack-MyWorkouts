# Examples — канонические примеры

Полные образцы тестов из реального кода Jetpack-MyWorkouts. Используй как
референс при написании новых.

> Источник правды — сами тесты в `app/src/test/`. Здесь — характерные
> фрагменты, которые удобно скопировать. Имена классов, импорты и
> паттерны соответствуют тому, что реально есть в проекте.

---

## 1. Pure-валидатор (object + sealed-error)

`EntryValidatorTest` — pure-функция с `Result<Unit>`-ошибками, без
корутин и без зависимостей.

```kotlin
package com.myworkouts.data.repository

import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class EntryValidatorTest {
    private fun baseEntry(
        sets: Int = 1,
        reps: Int? = 10,
        weightKg: Double? = null,
        workoutId: Long? = null,
        durationSec: Int? = null
    ): ExerciseEntry =
        ExerciseEntry(
            id = 0L,
            exerciseId = 1L,
            workoutId = workoutId,
            date = 1_700_000_000_000L,
            sets = sets,
            reps = reps,
            weightKg = weightKg,
            durationSec = durationSec,
            comment = null,
            createdAt = 1L,
            modifiedAt = 1L
        )

    @Test
    fun valid_entry_returns_success() {
        // Given
        val entry = baseEntry()

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isSuccess, "Корректная REPS-запись валидна")
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1, -100])
    fun sets_below_one_rejected(invalidSets: Int) {
        // Given
        val entry = baseEntry(sets = invalidSets)

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isFailure)
        assertEquals(
            EntryValidationError.INVALID_SETS,
            (result.exceptionOrNull() as? EntryValidationException)?.error
        )
    }
}
```

Особенности:
- `EntryValidator` — `object`, не требует DI.
- Ошибка типизирована через `EntryValidationException(error: EntryValidationError)`.
- `@ParameterizedTest` + `@ValueSource(ints = [...])` для повторяющихся
  граничных значений (см. `kotlin-testing` → `project-conventions.md`
  → «ParameterizedTest»).

---

## 2. Mapper (entity ↔ domain)

`MappersTest` — round-trip для enum-строк в entity.

```kotlin
package com.myworkouts.data.db

import com.myworkouts.domain.model.ExerciseCategory
import com.myworkouts.domain.model.ExerciseMeasuredBy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MappersTest {
    private fun sampleExercise(measuredBy: ExerciseMeasuredBy) =
        Exercise(
            id = 1L,
            name = "Планка",
            category = ExerciseCategory.CORE,
            isCustom = false,
            comment = null,
            createdAt = 100L,
            measuredBy = measuredBy
        )

    @Test
    fun exercise_measuredBy_time_roundtrip_preserved() {
        // Given
        val exercise = sampleExercise(measuredBy = ExerciseMeasuredBy.TIME)

        // When
        val entity = exercise.toEntity()
        val restored = entity.toDomain()

        // Then — TIME сохраняется в обе стороны, entity хранит имя значения
        assertEquals("TIME", entity.measuredBy)
        assertEquals(ExerciseMeasuredBy.TIME, restored.measuredBy)
    }

    @Test
    fun toListItem_when_weightKg_zero_then_keeps_zero() {
        // Given — 0.0 в БД: это данные, а не «нет веса» (design Decision 5)

        // When
        val item = EntryListItemEntity(
            id = 1L,
            exerciseName = "Подтягивания",
            sets = 4,
            reps = 10,
            durationSec = null,
            date = 1_700_000_000_000L,
            weightKg = 0.0
        ).toListItem()

        // Then — 0 проходит без искажений, решение о скрытии — на стороне UI
        assertEquals(0.0, item.weightKg)
    }
}
```

Особенности:
- `toDomain`/`toEntity` — `internal` extension-функции в `data/db/Mappers.kt`.
- enum `category`/`measuredBy` хранится строкой `name` (design Decision 2):
  `ExerciseCategory.valueOf(category)`, `category.name` в обратную сторону.
- Разные «нулевые» значения (`null` vs `0.0`) сохраняются раздельно — это
  семантика данных, не округление.

---

## 3. Domain enum с контрактом на полноту набора

`ExerciseCategoryTest` — `@ParameterizedTest` + `@EnumSource` для проверки
полного набора enum-значений.

```kotlin
package com.myworkouts.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ExerciseCategoryTest {
    @Test
    fun all_categories_contain_exactly_five_values() {
        // Given — enum ExerciseCategory
        // When
        val values = ExerciseCategory.values()

        // Then
        assertEquals(5, values.size)
    }

    @ParameterizedTest
    @EnumSource(ExerciseCategory::class)
    fun valueOf_roundtrips_for_known_name(category: ExerciseCategory) {
        // Given
        val name = category.name

        // When
        val parsed = ExerciseCategory.valueOf(name)

        // Then
        assertEquals(category, parsed)
    }

    @Test
    fun all_expected_categories_present() {
        // Given — ожидаемый контрактный набор
        val expected = setOf("PUSH", "PULL", "LEGS", "CORE", "CARDIO")

        // When
        val actual = ExerciseCategory.values().map { it.name }.toSet()

        // Then
        assertEquals(expected, actual)
    }

    @Test
    fun valueOf_throws_for_unknown_name() {
        // Given
        val unknown = "MADE_UP"

        // When + Then
        // enum.valueOf бросает IllegalArgumentException — это документированное
        // поведение, на которое опирается Mappers.kt.
        assertTrue(
            runCatching { ExerciseCategory.valueOf(unknown) }.isFailure
        )
    }
}
```

Особенности:
- Контракт «ровно N значений, набор фиксирован» — `prepopulate`
  стандартных упражнений в БД опирается на эти 5 значений.
- `@EnumSource` — основной источник для перебора enum-значений.

---

## 4. Use Case с `Result<T>` и I/O (mockk на ContentResolver)

`ExportBackupUseCaseTest` — I/O через `Context` + `ContentResolver`,
репозиторий мокается. Канонический пример — `app/src/test/.../ExportBackupUseCaseTest.kt`.

Полный файл длинный (тесты формата JSON и репозиторных ошибок), здесь
характерные куски.

### 4.1. Чистая функция `buildBackupJson` (без I/O)

```kotlin
@Test
fun buildBackupJson_when_data_present_then_version_and_arrays() {
    // Given
    val exercises = listOf(pullUps, plank)
    val entries = listOf(repsEntry, timeEntry)

    // When
    val json = JSONObject(buildBackupJson(exercises, entries))

    // Then
    assertEquals(1, json.getInt("version"), "Версия формата — всегда 1")
    assertEquals(2, json.getJSONArray("exercises").length(), "Все упражнения каталога в файле")
    assertEquals(2, json.getJSONArray("entries").length(), "Все записи в файле")
}
```

### 4.2. `invoke` — ошибка репозитория, поток не открывается

```kotlin
@Test
fun invoke_when_repository_fails_then_failure_and_no_stream() =
    runTest {
        // Given
        val dbError = RuntimeException("Сбой базы")
        val repository = mockk<WorkoutDataRepository>(relaxed = true)
        coEvery { repository.getAllExercises() } returns Result.failure(dbError)
        coEvery { repository.getAllEntries() } returns Result.failure(dbError)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { context.contentResolver } returns contentResolver
        val uri = mockk<Uri>()
        val useCase = ExportBackupUseCase(repository, context)

        // When
        val result = useCase(uri)

        // Then
        assertTrue(result.isFailure, "Ошибка репозитория — Result.failure")
        assertTrue(
            result.exceptionOrNull() === dbError,
            "Ошибка БД проходит в Result как есть, не оборачивается"
        )
        verify(exactly = 0) { contentResolver.openOutputStream(any()) }
    }
```

Особенности:
- `runTest { … }` без `viewModelScope` — для use-case'ов без диспетчеризации.
- `mockk<…>(relaxed = true)` для всего, что не влияет на тест.
- `coEvery` — только для `suspend`-методов (`getAllExercises()`, `getAllEntries()`).
- `every` — для `ContentResolver.openOutputStream()` и для геттеров.
- `verify(exactly = 0) { … }` — негативная проверка: поток НЕ открыт при провале снимка.

---

## 5. ViewModel + `FakeWorkoutDataRepository` (MutableStateFlow)

`MainViewModelTest` — реактивный список из `WorkoutDataRepository.entries()`,
удаление через `confirmDelete()`. Полный файл — в
`app/src/test/java/com/myworkouts/ui/viewmodel/MainViewModelTest.kt`.

```kotlin
package com.myworkouts.ui.viewmodel

import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.ui.state.MainUiState
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private class MainViewModelFakeRepository(
    initialEntries: List<EntryListItem> = emptyList()
) : WorkoutDataRepository {
    private val entriesFlow = MutableStateFlow(initialEntries)

    val deletedIds: MutableList<Long> = mutableListOf()

    override fun entries(): Flow<List<EntryListItem>> = entriesFlow

    override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override suspend fun deleteEntry(id: Long): Result<Unit> {
        deletedIds += id
        entriesFlow.value = entriesFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

    // ... остальные методы WorkoutDataRepository — заглушки
    // (addEntry, updateEntry, getEntry, deleteExercise, getExercise,
    // exerciseIdsWithLinkedEntries, getAllExercises, getAllEntries,
    // importData, deleteAllData, addExercise, updateExercise).
    // Для них `override suspend fun … = Result.success(… / Unit)` —
    // достаточно для большинства тестов; тонкая настройка — на use site.
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var repository: MainViewModelFakeRepository

    private val item1 = EntryListItem(
        id = 1L,
        exerciseName = "Подтягивания",
        sets = 4,
        reps = 10,
        durationSec = null,
        date = 1_721_000_000_000L
    )

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        repository = MainViewModelFakeRepository(initialEntries = listOf(item1))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_when_subscribed_then_success_with_entries() =
        runTest(testDispatcher) {
            // Given
            val viewModel = MainViewModel(repository)

            // When — подписка активирует WhileSubscribed, список доезжает
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Success, "После эмиссии — Success")
            val success = state as MainUiState.Success
            assertEquals(listOf(item1), success.entries, "Записи приходят из реактивного источника")
            assertNull(success.pendingDeleteId, "Без действия pendingDeleteId = null")
        }

    @Test
    fun confirmDelete_calls_repository_and_clears_pending() =
        runTest(testDispatcher) {
            // Given
            val viewModel = MainViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()
            viewModel.requestDelete(item1)
            advanceUntilIdle()

            // When
            viewModel.confirmDelete()
            advanceUntilIdle()

            // Then
            assertEquals(listOf(1L), repository.deletedIds, "deleteEntry вызван с id item1")
            assertNull(viewModel.uiState.value.pendingDeleteId, "pending сброшен после удаления")
        }
}
```

Особенности:
- Fake делает минимум: реактивный `entries()` + записывает `deleteEntry`.
- `MutableStateFlow.value = …` сразу триггерит эмиссию — list обновляется
  реактивно без `delay`/корутин.
- Подписка через `backgroundScope.launch { viewModel.uiState.collect { } }` —
  для запуска `WhileSubscribed` (stateIn) перед чтением `.value`.
- `advanceUntilIdle()` — после каждого действия ViewModel.

> Если нужен ещё один fake для той же ViewModel в другом тест-классе —
  именуй `MyViewModelFakeRepository` (см. комментарий в исходнике:
  test source set — одна единица компиляции, `private` на top-level
  изолирует только в пределах модуля).

---

## 6. ViewModel с use-case'ами (`AppDataViewModelTest`)

`AppDataViewModel` — комбинация репозитория (Fake) и use-case'ов (mockk).
Use-case'ы замоканы, репозиторий — Fake для `entries()`/`exercises()`.

```kotlin
package com.myworkouts.ui.viewmodel

import android.net.Uri
import android.util.Log
import com.myworkouts.data.repository.WorkoutDataRepository
import com.myworkouts.domain.model.BackupEntry
import com.myworkouts.domain.model.EntryListItem
import com.myworkouts.domain.model.Exercise
import com.myworkouts.domain.model.ExerciseEntry
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private class AppDataViewModelFakeRepository(
    entries: List<EntryListItem> = emptyList(),
    private val exercises: List<Exercise> = emptyList()
) : WorkoutDataRepository {
    private val entriesFlow = MutableStateFlow(entries)
    private val catalogFlow = MutableStateFlow(exercises)

    var deleteAllDataResult: Result<Unit> = Result.success(Unit)

    override fun entries(): Flow<List<EntryListItem>> = entriesFlow
    override fun exercises(query: String): Flow<List<Exercise>> = catalogFlow

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalogFlow.value)
    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())
    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = Result.success(Unit)
    override suspend fun deleteAllData(): Result<Unit> = deleteAllDataResult

    // ... остальные методы — заглушки Result.success
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppDataViewModelTest {
    private lateinit var testDispatcher: TestDispatcher
    private lateinit var uri: Uri
    private lateinit var exportUseCase: ExportBackupUseCase
    private lateinit var importUseCase: ImportBackupUseCase

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        // android.util.Log в JVM — stub, роняет короутину; мокаем статически
        mockkStatic(Log::class)
        every { Log.e(any(), any<String>()) } returns 0
        uri = mockk()
        exportUseCase = mockk()
        importUseCase = mockk()
        coEvery { exportUseCase.invoke(any()) } returns Result.success(Unit)
        coEvery { importUseCase.invoke(any()) } returns Result.success(Unit)
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: AppDataViewModelFakeRepository) =
        AppDataViewModel(repository, exportUseCase, importUseCase)

    @Test
    fun exportBackup_whenUseCaseFails_thenErrorExport() =
        runTest(testDispatcher) {
            // Given
            coEvery { exportUseCase.invoke(any()) } returns
                Result.failure(BackupException("SAF не отдал поток", BackupError.EXPORT))
            val viewModel = viewModel(AppDataViewModelFakeRepository())
            backgroundScope.launch { viewModel.uiState.collect {} }
            advanceUntilIdle()

            // When
            viewModel.exportBackup(uri)
            advanceUntilIdle()

            // Then
            assertEquals(AppDataResult.ERROR_EXPORT, viewModel.uiState.value.result)
            assertFalse(viewModel.uiState.value.isBusy)
        }
}
```

Особенности:
- `mockkStatic(Log::class)` + `unmockkStatic` в `@AfterEach` — стандартный
  приём для ViewModel, которые логируют ошибки (`AppDataViewModel`,
  `ExercisesViewModel`).
- `runTest(testDispatcher) { … }` — передаём тот же dispatcher, что в
  `setMain`, иначе корутины не подхватят тестовый планировщик.

---

## Где искать ещё

- `app/src/test/java/com/myworkouts/ui/viewmodel/MainViewModelTest.kt` — Fake-репозиторий на MutableStateFlow, реактивный список, удаление через `confirmDelete`.
- `app/src/test/java/com/myworkouts/ui/viewmodel/EntryCreateViewModelTest.kt` — Fake-репозиторий с настраиваемым результатом `addEntry`, санитайзинг, фиксированный `nowProvider`, `nowProvider`-параметр.
- `app/src/test/java/com/myworkouts/ui/viewmodel/AppearanceViewModelTest.kt` — `mockk<AppSettingsDataStore>(relaxed = true)`, `@ParameterizedTest` + `@EnumSource(AppTheme::class)`, `@MethodSource` для пар.
- `app/src/test/java/com/myworkouts/ui/viewmodel/ExerciseCreateViewModelTest.kt` — Fake с `addedExercises` (отслеживание аргументов).
- `app/src/test/java/com/myworkouts/ui/viewmodel/ExercisesViewModelTest.kt` — Fake с настраиваемыми `deleteExerciseResult`/`linkedEntries`, маппинг `DeleteExerciseError`.
- `app/src/test/java/com/myworkouts/ui/viewmodel/AppDataViewModelTest.kt` — Fake + замоканные use-case'ы, `mockkStatic(Log::class)`, маппинг `BackupError` → `AppDataResult`.
- `app/src/test/java/com/myworkouts/data/db/MappersTest.kt` — `toDomain`/`toEntity`, round-trip enum ↔ string.
- `app/src/test/java/com/myworkouts/data/repository/EntryValidatorTest.kt` — `@ParameterizedTest` + `@ValueSource(ints/doubles)` для граничных значений.
- `app/src/test/java/com/myworkouts/domain/model/ExerciseCategoryTest.kt` — `@ParameterizedTest` + `@EnumSource` для enum-контракта.
- `app/src/test/java/com/myworkouts/domain/usecase/ExportBackupUseCaseTest.kt` — `mockk<Context>` + `coEvery`/`every` для suspend-Flow-методов, верификация `openOutputStream` НЕ вызван при ошибке.
- `app/src/test/java/com/myworkouts/domain/usecase/ImportBackupUseCaseTest.kt` — `ByteArrayInputStream` для подачи JSON-строки, `slot` для захвата аргументов `importData`, `@ParameterizedTest` + `@MethodSource` для негативных кейсов.
- `app/src/test/java/com/myworkouts/ui/state/EntryCreateUiStateTest.kt` — sealed-state равенство через конструктор (без `is` + cast).
- `app/src/test/java/com/myworkouts/ui/util/DurationFormatterTest.kt` — чистая функция-форматтер.
- `app/src/test/java/com/myworkouts/ui/screens/entry/EntryCreateFormattersTest.kt` — `LocalDate` ↔ epoch millis с фиксированной зоной.
- `app/src/test/java/com/myworkouts/navigation/VisibleTabsTest.kt` — pure-функция с `Screen.*.route`, проверка обеих веток.