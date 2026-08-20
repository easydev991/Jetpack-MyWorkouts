# ViewModel integration — MainDispatcherRule, Turbine

Единственный интеграционный ViewModel-тест в проекте —
`WorkoutDetailsScreenViewModelIntegrationTest.kt`
(`app/src/androidTest/java/com/myworkouts/ui/viewmodel/`).
Он тестирует ViewModel с реальной in-memory Room БД и реальным
репозиторием, в отличие от unit-тестов с фейками.

> Новые интеграционные ViewModel-тесты не писать — только unit-тесты
> с Fake-репозиторием (см. навык `kotlin-testing`, правило 7).
> Существующий — обслуживать как есть.

## MainDispatcherRule

Общий хелпер — `app/src/androidTest/java/com/myworkouts/test/MainDispatcherRule.kt`:

```kotlin
@ExperimentalCoroutinesApi
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
```

Использование:

```kotlin
@get:Rule
val mainDispatcherRule = MainDispatcherRule()
```

Заменяет `Dispatchers.Main` на `StandardTestDispatcher` до теста
и восстанавливает после. Без него `viewModelScope.launch` использует
реальный Main, который не контролируется тестом.

## Скелет ViewModel-интеграции

Полный код — `references/EXAMPLE.md` (раздел «ViewModel-интеграция
(Turbine)»). Ключевые элементы:

```kotlin
@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutDetailsScreenViewModelIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Before
    fun setUp() {
        // in-memory Room + WorkoutRepositoryImpl + database.clearAllTables()
    }
}
```

`clearAllTables()` в `@Before` — обязателен: база in-memory живёт
весь процесс, между тестами данные остаются.

## Тест через Turbine

Паттерн (полный тест — в EXAMPLE.md):

```kotlin
viewModel.uiState.test {
    val loadingState = awaitItem()   // начальное состояние — Loading
    val successState = awaitItem()   // после загрузки — Success
}
```

Ключевое:
- `runTest { }` + `viewModel.uiState.test { awaitItem() }` (Turbine)
- Первый `awaitItem()` — начальное состояние (`Loading`)
- Второй `awaitItem()` — состояние после загрузки (`Success`)
- Доступ к полям — после `assertTrue(state is ...)`, без `!!`
- `SavedStateHandle(mapOf("workoutId" to insertedId))` — реальный handle

## Альтернативы Turbine

Когда `.test { }` не подходит (например, нужен `advanceUntilIdle`):

```kotlin
runTest {
    ...
    viewModel.uiState.test { ... }   // не используется
}

// Вариант 1: advanceUntilIdle + сбор через backgroundScope
backgroundScope.launch {
    viewModel.uiState.collect { collectedStates += it }
}
testDispatcher.scheduler.advanceUntilIdle()
```

Проверка реактивности — обновление БД между эмиссиями:

```kotlin
viewModel.uiState.test {
    val first = awaitItem()
    repository.updateWorkout(updatedItem)   // изменение в БД
    val second = awaitItem()             // ViewModel переэмитит
    assertEquals("Обновлённое событие", (second as WorkoutDetailsState.Success).workout.title)
}
```

## Различия unit vs integration для ViewModel

| Аспект | Unit-тест (`app/src/test/`) | Интеграционный (`androidTest/`) |
|---|---|---|
| Репозиторий | Fake на MutableStateFlow | Реальный `WorkoutRepositoryImpl` |
| База | — | Room in-memory |
| Диспетчер | `StandardTestDispatcher` + setMain/resetMain | `MainDispatcherRule` |
| State-проверки | `runTest` + collect | Turbine `.test { awaitItem() }` |
| SavedStateHandle | Фейковый | Реальный с `mapOf(...)` |
