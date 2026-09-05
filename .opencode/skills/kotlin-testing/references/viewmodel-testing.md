# ViewModel Testing

Это самый частый кейс в проекте: тест ViewModel через
`FakeWorkoutRepository` + `StandardTestDispatcher`. Интеграционные
тесты ViewModel (с реальным Room) **запрещены** AGENTS.md.

## Полный шаблон

```kotlin
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MyViewModelTest {
    private lateinit var repository: FakeWorkoutRepository
    private lateinit var viewModel: MyViewModel
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        repository = FakeWorkoutRepository()
        viewModel = MyViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle()     // если нужен workoutId
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun whenAction_thenExpectedResult() = runTest {
        // Given
        repository.setWorkouts(listOf(testWorkout))

        // When
        viewModel.someAction()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val uiState = viewModel.uiState.value
        assertTrue(uiState is MyUiState.Success)
    }
}
```

## Шесть обязательных элементов

1. `@OptIn(ExperimentalCoroutinesApi::class)` на классе.
2. `testDispatcher = StandardTestDispatcher()` в `@BeforeEach`.
3. `Dispatchers.setMain(testDispatcher)` — иначе `viewModelScope`
   по умолчанию использует `Main`, который не контролируется тестом.
4. `Dispatchers.resetMain()` в `@AfterEach` — обязательно,
   иначе следующий тест в том же процессе JVM получит грязный
   диспетчер.
5. Простые интерфейсные зависимости (если появятся у ViewModel) —
   `mockk(relaxed = true)`; текущие ViewModel проекта без `Logger`,
   `NoOpLogger` в проекте нет.
6. `testDispatcher.scheduler.advanceUntilIdle()` после каждого
   действия ViewModel — корутины не запустятся сами.

## SavedStateHandle — для навигационных аргументов

`WorkoutDetailsScreenViewModel`, `WorkoutEditScreenViewModel` читают `workoutId`
из `SavedStateHandle`. В тесте передаём вручную:

```kotlin
val savedStateHandle = SavedStateHandle(mapOf("workoutId" to testItemId))
viewModel = WorkoutDetailsScreenViewModel(
    repository = repository,
    savedStateHandle = savedStateHandle,
    reminderManager = reminderManager
)
```

`mapOf("workoutId" to testItemId)` — это имитация аргументов навигации
(`"workout_detail/$testItemId"`), которые в проде Compose Navigation
кладёт в handle.

## `currentTimeMillisProvider` — для clock-зависимых ViewModel

`WorkoutDetailsScreenViewModel` принимает `currentTimeMillisProvider: () -> Long`
для тестирования "будущих" и "прошедших" напоминаний:

```kotlin
val nowMillis = 1_800_000_000_000L
viewModel = WorkoutDetailsScreenViewModel(
    repository = repository,
    savedStateHandle = SavedStateHandle(mapOf("workoutId" to testItemId)),
    reminderManager = reminderManager,
    currentTimeMillisProvider = { nowMillis }   // фиксируем время
)

// ... действие, advanceUntilIdle
nowMillis += 120_000L                            // "сдвиг" часов
viewModel.refreshReminder()
testDispatcher.scheduler.advanceUntilIdle()
// теперь проверяем, что прошедшее напоминание скрыто
```

## Полный пример: `WorkoutDetailsScreenViewModelTest`

```kotlin
@Test
fun when_workout_not_found_thenremainsinloadingstate() = runTest {
    // Given - Repository не содержит элемент
    val savedStateHandle = SavedStateHandle(mapOf("workoutId" to 999L))
    viewModel = WorkoutDetailsScreenViewModel(
        repository = repository,
        savedStateHandle = savedStateHandle,
        reminderManager = reminderManager
    )

    // When - Пытаемся загрузить несуществующий элемент
    testDispatcher.scheduler.advanceUntilIdle()

    // Then - Должно остаться в состоянии Loading
    val currentState = viewModel.uiState.value
    assertTrue(currentState is WorkoutDetailsState.Loading, "Должно остаться в состоянии Loading")
}
```

## Полный пример: `WorkoutsScreenViewModelTest` (с DataStore + slot)

```kotlin
private val sortOrderSlot = slot<SortOrder>()
private lateinit var sortOrderFlow: MutableStateFlow<SortOrder>

@BeforeEach
fun setUp() {
    testDispatcher = StandardTestDispatcher()
    Dispatchers.setMain(testDispatcher)

    repository = FakeWorkoutRepository()
    sortOrderFlow = MutableStateFlow(SortOrder.DESCENDING)
    val dataStore = mockk<UserPreferencesDataStore>(relaxed = true)

    every { dataStore.sortOrder } returns sortOrderFlow
    coEvery { dataStore.setSortOrder(capture(sortOrderSlot)) } answers {
        sortOrderFlow.value = sortOrderSlot.captured
    }

    viewModel = WorkoutsScreenViewModel(repository, dataStore, mockk(relaxed = true))
}

@Test
fun when_sort_order_changed_then_sorts_workouts() = runTest {
    // Given
    repository.setWorkouts(listOf(testWorkout1, testWorkout2))

    // When
    viewModel.updateSortOrder(SortOrder.ASCENDING)
    testDispatcher.scheduler.advanceUntilIdle()

    // Then
    val state = viewModel.uiState.value as WorkoutsScreenState.Success
    assertEquals(2, state.items.size)
    assertEquals(SortOrder.ASCENDING, viewModel.sortOrder.value)
    coVerify { dataStore.setSortOrder(SortOrder.ASCENDING) }
}
```

`MutableStateFlow` для `sortOrderFlow` имитирует persistence —
вызов `setSortOrder` обновляет `sortOrderFlow.value`, и ViewModel
получает эмиссию обратно через `dataStore.sortOrder`.

## Почему НЕ `runBlocking` для ViewModel

```kotlin
// НЕПРАВИЛЬНО — конфликтует с viewModelScope.launch
@Test
fun bad() = runBlocking {
    viewModel.someAction()   // запускает viewModelScope.launch на Main
    // viewModelScope.launch НЕ выполнится — диспетчер Main не под контролем
}
```

Симптомы: тест зависает, `UncompletedCoroutinesError` после, flaky
failures.

**Правильно** — `runTest` (он контролирует `TestScope`) +
`StandardTestDispatcher` + `advanceUntilIdle()`.

## Когда ViewModel использует Turbine

**Никогда в unit-тестах этого проекта.** Turbine подключён только в
`androidTest/` (см. `app/build.gradle.kts` — `androidTestImplementation(libs.turbine)`).
В unit-тестах Flow проверяется через `viewModel.uiState.value` после
`advanceUntilIdle()`. Turbine в unit-тестах не нужен и не используется.

## Проверка нескольких эмиссий без Turbine

```kotlin
@Test
fun when_workout_reemits_then_state_updates() = runTest {
    repository.setWorkout(testWorkout)
    testDispatcher.scheduler.advanceUntilIdle()
    val stateAfterFirstLoad = viewModel.uiState.value as WorkoutDetailsState.Success

    // Имитируем re-emit (например, после сохранения из Edit screen)
    repository.setWorkout(testWorkout.copy(timestamp = testWorkout.timestamp + 1))
    testDispatcher.scheduler.advanceUntilIdle()

    val stateAfterReemit = viewModel.uiState.value as WorkoutDetailsState.Success
    assertEquals(stateAfterFirstLoad.workout.id, stateAfterReemit.workout.id)
}
```

Каждый `setWorkout` + `advanceUntilIdle()` = один «снимок» Flow.
Промежуточные состояния смотри через `viewModel.uiState.value`.

## Типичные ошибки

| Ошибка | Симптом | Решение |
|---|---|---|
| Забыл `setMain` | `viewModelScope.launch` висит навечно | Добавь `Dispatchers.setMain(testDispatcher)` в `@BeforeEach` |
| Забыл `resetMain` | Следующий тест в JVM получает грязный Main, flaky failures | Добавь в `@AfterEach` |
| Забыл `advanceUntilIdle()` | `uiState.value` — `Loading`, хотя ждёшь `Success` | Вставь после действия ViewModel |
| `@OptIn` пропущен | Компилятор ругается на `StandardTestDispatcher` | Добавь `@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)` |
| `mockk()` вместо `Fake` для `WorkoutRepository` | `mockRepo.getAllWorkouts() returns flowOf(...)` не эмитится в `combine` | Замени на `FakeWorkoutRepository` (см. `references/fakes.md`) |