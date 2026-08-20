# Examples — канонические примеры

Полные образцы тестов из реального кода Jetpack-MyWorkouts. Используй как
референс при написании новых.

---

## 1. Use Case без зависимостей + `Result<T>` + Clock

`BuildWorkoutReminderUseCaseTest` — clock-зависимый use case с валидацией.

```kotlin
package com.myworkouts.domain.usecase

import com.myworkouts.domain.model.WorkoutReminderIntervalUnit
import com.myworkouts.domain.model.WorkoutReminderMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class BuildWorkoutReminderUseCaseTest {
    private val fixedInstant = Instant.parse("2026-04-27T10:15:30Z")
    private val zoneId = ZoneId.of("Europe/Moscow")
    private val clock: Clock = Clock.fixed(fixedInstant, zoneId)

    @Test
    fun invoke_whenAfterIntervalDays_thenKeepsCurrentTimeOfDayAndAddsDays() {
        // Given
        val useCase = BuildWorkoutReminderUseCase(clock = clock)
        val request = WorkoutReminderRequest(
            workoutId = 42L,
            mode = WorkoutReminderMode.AFTER_INTERVAL,
            afterAmount = 3,
            afterUnit = WorkoutReminderIntervalUnit.DAY
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isSuccess, "Результат должен быть успешным")
        val reminder = result.getOrThrow()
        val expected = ZonedDateTime.ofInstant(fixedInstant, zoneId).plusDays(3)
        assertEquals(expected.toInstant().toEpochMilli(), reminder.targetEpochMillis)
        assertEquals(3, reminder.intervalAmount)
        assertEquals(WorkoutReminderIntervalUnit.DAY, reminder.intervalUnit)
    }

    @Test
    fun invoke_whenAfterIntervalAmountIsZero_thenReturnsFailure() {
        // Given
        val useCase = BuildWorkoutReminderUseCase(clock = clock)
        val request = WorkoutReminderRequest(
            workoutId = 42L,
            mode = WorkoutReminderMode.AFTER_INTERVAL,
            afterAmount = 0,
            afterUnit = WorkoutReminderIntervalUnit.WEEK
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isFailure, "Должна быть ошибка валидации")
    }
}
```

---

## 2. ViewModel с FakeWorkoutRepository + DataStore

`WorkoutsScreenViewModelTest` (фрагмент). Полный файл — в
`app/src/test/java/com/myworkouts/ui/viewmodel/WorkoutsScreenViewModelTest.kt`.

```kotlin
package com.myworkouts.ui.viewmodel

import com.myworkouts.data.preferences.UserPreferencesDataStore
import com.myworkouts.domain.model.DisplayOption
import com.myworkouts.domain.model.Workout
import com.myworkouts.domain.model.SortOrder
import com.myworkouts.domain.repository.WorkoutRepository
import com.myworkouts.util.Logger
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutsScreenViewModelTest {
    private lateinit var repository: FakeWorkoutRepository
    private lateinit var sortOrderFlow: MutableStateFlow<SortOrder>
    private lateinit var dataStore: UserPreferencesDataStore
    private lateinit var logger: Logger
    private lateinit var viewModel: WorkoutsScreenViewModel
    private lateinit var testDispatcher: TestDispatcher
    private val sortOrderSlot = slot<SortOrder>()

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        repository = FakeWorkoutRepository()
        sortOrderFlow = MutableStateFlow(SortOrder.DESCENDING)
        dataStore = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        every { dataStore.sortOrder } returns sortOrderFlow
        coEvery { dataStore.setSortOrder(capture(sortOrderSlot)) } answers {
            sortOrderFlow.value = sortOrderSlot.captured
        }

        viewModel = WorkoutsScreenViewModel(repository, dataStore, logger)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun when_sort_order_changed_then_sorts_workouts() = runTest {
        // Given
        val items = listOf(
            Workout(id = 1L, title = "Событие 1", details = "Детали 1",
                 timestamp = System.currentTimeMillis() - 86400000,
                 colorTag = null, displayOption = DisplayOption.DAY),
            Workout(id = 2L, title = "Событие 2", details = "Детали 2",
                 timestamp = System.currentTimeMillis(),
                 colorTag = null, displayOption = DisplayOption.DAY)
        )
        repository.setWorkouts(items)

        // When
        viewModel.updateSortOrder(SortOrder.ASCENDING)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertTrue(uiState is WorkoutsScreenState.Success, "Должно быть состояние Success")
        val successState = uiState as WorkoutsScreenState.Success
        assertEquals(2, successState.items.size)
        assertEquals(1L, successState.items[0].id, "Первый элемент должен быть старым")
        assertEquals(SortOrder.ASCENDING, viewModel.sortOrder.value)
        coVerify { dataStore.setSortOrder(SortOrder.ASCENDING) }
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        private val _items = MutableStateFlow<List<Workout>>(emptyList())

        fun setWorkouts(items: List<Workout>) { _items.value = items }

        override fun getAllWorkouts(): Flow<List<Workout>> = _items

        override fun getAllWorkouts(sortOrder: SortOrder): Flow<List<Workout>> =
            _items.map { items ->
                when (sortOrder) {
                    SortOrder.ASCENDING -> items.sortedWith(compareBy({ it.timestamp }, { it.id }))
                    SortOrder.DESCENDING ->
                        items.sortedWith(compareByDescending<Workout> { it.timestamp }.thenByDescending { it.id })
                }
            }

        override suspend fun getWorkoutById(id: Long): Workout? = _items.value.find { it.id == id }
        override fun getWorkoutFlow(id: Long): Flow<Workout?> = _items.map { it.find { workout -> workout.id == id } }
        override fun searchWorkouts(query: String): Flow<List<Workout>> = _items

        override suspend fun insertWorkout(workout: Workout): Long {
            val newId = (_items.value.maxOfOrNull { it.id } ?: 0) + 1
            _items.value = _items.value + workout.copy(id = newId)
            return newId
        }
        override suspend fun updateWorkout(workout: Workout) {
            _items.value = _items.value.map { if (it.id == workout.id) item else it }
        }
        override suspend fun deleteWorkout(workout: Workout) {
            _items.value = _items.value.filterNot { it.id == workout.id }
        }
        override suspend fun deleteAllWorkouts() { _items.value = emptyList() }
        override suspend fun getWorkoutsCount(): Int = _items.value.size
    }
}
```

---

## 3. Mapper (entity ↔ domain)

```kotlin
package com.myworkouts.data.database

import com.myworkouts.data.database.entity.WorkoutEntity
import com.myworkouts.domain.model.DisplayOption
import com.myworkouts.domain.model.Workout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WorkoutMapperTest {
    @Test
    fun roundTripConversion_thenPreservesAllFields() {
        // Given
        val original = Workout(
            id = 5L,
            title = "Оригинальное событие",
            details = "Детали",
            timestamp = 9876543210000L,
            colorTag = 0xFF00FF00.toInt(),
            displayOption = DisplayOption.YEAR_MONTH_DAY
        )

        // When
        val entity = original.toEntity()
        val converted = entity.toDomain()

        // Then
        assertEquals(original.id, converted.id)
        assertEquals(original.title, converted.title)
        assertEquals(original.details, converted.details)
        assertEquals(original.timestamp, converted.timestamp)
        assertEquals(original.colorTag, converted.colorTag)
        assertEquals(original.displayOption, converted.displayOption)
    }
}
```

---

## 4. JSON-парсинг с реальными файлами

`BackupImportRealFilesTest` (фрагмент):

```kotlin
package com.myworkouts.domain.usecase

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.InputStream

class BackupImportRealFilesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parse_old_backup_sample_json_as_list_of_backupitem() {
        // Given
        val jsonString = loadResource("/old-backup-sample.json")

        // When
        val items = json.decodeFromString<List<WorkoutBackupItem>>(jsonString)

        // Then
        assertEquals(8, items.size)

        val firstBackupWorkout = items[0]
        assertEquals("День рождения", firstBackupWorkout.title)
        assertEquals("#FF5722", firstBackupWorkout.colorTag)
    }

    private fun loadResource(path: String): String {
        val stream: InputStream? = javaClass.getResourceAsStream(path)
        assertNotNull(stream, "Resource not found: $path")
        return checkNotNull(stream, "Resource stream is null").bufferedReader().use { it.readText() }
    }
}
```

---

## Где искать ещё

- `app/src/test/java/com/myworkouts/domain/usecase/CalculateWorkoutDurationUseCaseTest.kt` — pure use case, временные кейсы, явный `currentDate`.
- `app/src/test/java/com/myworkouts/domain/usecase/FormatWorkoutDurationUseCaseTest.kt` — MockK для одного интерфейса + `StubResourceProvider`.
- `app/src/test/java/com/myworkouts/domain/model/WorkoutDurationTest.kt` — sealed-class проверки.
- `app/src/test/java/com/myworkouts/ui/viewmodel/WorkoutDetailsScreenViewModelTest.kt` — `SavedStateHandle`, `currentTimeMillisProvider`, `FakeWorkoutReminderManager`.
- `app/src/test/java/com/myworkouts/data/repository/WorkoutReminderRepositoryImplTest.kt` — `FakeWorkoutReminderDao` для repository.
- `app/src/test/java/com/myworkouts/reminder/DefaultWorkoutReminderManagerTest.kt` — несколько Fake'ов одновременно.
- `app/src/test/java/com/myworkouts/data/database/DisplayOptionConverterTest.kt` — converter + round-trip.
- `app/src/test/java/com/myworkouts/ui/state/WorkoutDetailsStateTest.kt` — sealed-state equality.