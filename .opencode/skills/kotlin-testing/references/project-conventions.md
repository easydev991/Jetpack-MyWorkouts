# Project Conventions

Соглашения Jetpack-MyWorkouts для unit-тестов. Часть из них —
требования AGENTS.md (обязательные), часть — устоявшаяся практика
(принятая, но гибкая).

## Обязательные (из AGENTS.md)

### Никакого `!!`

Даже в тестах. Используй `?.`, `?:`, `let`, `checkNotNull`:

```kotlin
// ПЛОХО
val workout = repository.getById(id)!!
val reminder = successState.reminder!!

// ХОРОШО
val workout = checkNotNull(repository.getById(id)) { "Workout должен существовать" }
val reminder = successState.reminder
assertNotNull(reminder, "WorkoutReminder должен быть не null")
```

### `snake_case` для имён тестов, без обратных кавычек

```kotlin
// ПЛОХО — обратные кавычки
@Test
fun `calculate when same day then returns today`() { }

// ХОРОШО
@Test
fun calculate_when_same_day_then_returns_today() { }
```

### Файлы тестов в `app/src/test/`, зеркалят `app/src/main/`

```
app/src/main/java/com/myworkouts/domain/usecase/CalculateWorkoutDurationUseCase.kt
app/src/test/java/com/myworkouts/domain/usecase/CalculateWorkoutDurationUseCaseTest.kt
```

### Имя тест-класса = `<Class>Test`

```
CalculateWorkoutDurationUseCase → CalculateWorkoutDurationUseCaseTest
WorkoutsScreenViewModel → WorkoutsScreenViewModelTest
```

### Импорты только из JUnit 5

`org.junit.jupiter.api.*`, не `org.junit.*`.

## Устоявшиеся (следуй для единообразия)

### Given / When / Then в каждом тесте

Три маркера-комментария. Даже если When и Then — одна строка.
Помогает глазу и как документация.

### Русские сообщения в `assert`

```kotlin
assertEquals(2, items.size, "Должно быть 2 элемента")
```

### `Logger` в тестах ViewModel

`com.myworkouts.util.NoOpLogger` — предпочтительный вариант:
реализация `Logger`, которая ничего не делает. Альтернативно —
`mockk(relaxed = true)` (relaxed-мок перехватывает любые вызовы
без падения; см. правило 5 SKILL.md и `EXAMPLE.md`). Оба подхода
живут в проекте: `WorkoutsScreenViewModelTest` использует
`mockk(relaxed = true)`, `WorkoutDetailsScreenViewModelTest` и
`WorkoutEditScreenViewModelTest` — `NoOpLogger()`. Смешивать
в одном тест-классе не нужно — выбери один.

```kotlin
// Предпочтительный вариант
viewModel = WorkoutsScreenViewModel(
    repository = repository,
    dataStore = dataStore,
    logger = NoOpLogger()
)

// Допустимая альтернатива
val logger: Logger = mockk(relaxed = true)
viewModel = WorkoutsScreenViewModel(
    repository = repository,
    dataStore = dataStore,
    logger = logger
)
```

### `StubResourceProvider` в тестах Use Case

`com.myworkouts.data.provider.StubResourceProvider` —
production-класс из `data/provider/`, специально сделанный
заглушкой для тестов. Не пиши свою:

```kotlin
private val resourceProvider: ResourceProvider = StubResourceProvider()
```

Он возвращает русские строки (`"Сегодня"`, `"осталось"`, `"прошло"`)
и сокращения (`"дн."`, `"мес."`, `"г."`).

### Fake предпочтительнее Mock для репозиториев с Flow

См. `references/fakes.md`. `FakeWorkoutRepository` на `MutableStateFlow`
вместо `mockk() returns flowOf(...)`.

### Private inner class для Fake

`FakeWorkoutRepository`, `FakeWorkoutReminderDao`, `FakeWorkoutReminderManager` —
объявляются как `private class` внутри тест-класса. Это даёт
полный контракт рядом с тестом. Выносить в общий файл только если
один Fake переиспользуется в 3+ тестах.

### Имя Fake-класса — `Fake<InterfaceName>` или
`Fake<InterfaceName>With<Modifier>`

```kotlin
private class FakeWorkoutRepository : WorkoutRepository
private class FakeWorkoutRepositoryWithLoggingDisabled : WorkoutRepository
private class FakeWorkoutReminderDao : WorkoutReminderDao
private class FakeWorkoutReminderManager : WorkoutReminderManager
private class FakeWorkoutReminderScheduler : WorkoutReminderScheduler
private class FakeResourceProvider : ResourceProvider
```

### `lateinit var` для всего, что создаётся в `@BeforeEach`

```kotlin
private lateinit var repository: FakeWorkoutRepository
private lateinit var viewModel: WorkoutsScreenViewModel
private lateinit var testDispatcher: TestDispatcher
```

Immutable поля (например, `private val useCase = ...()`) — `val`.

### `@OptIn(ExperimentalCoroutinesApi::class)` на классе

Если используется `StandardTestDispatcher` / `TestScope` —
на уровне класса, не метода:

```kotlin
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutsScreenViewModelTest {
    // ...
}
```

### `runTest { ... }` как выражение

JUnit 5 + coroutines-test поддерживают `runTest` как тело функции:

```kotlin
@Test
fun whenAction_thenExpected() = runTest {
    // Given
    // When
    // Then
}
```

Либо `runTest` внутри тела — оба варианта встречаются в проекте.
Форма как выражение — короче.

### Тестовые JSON-ресурсы в `app/src/test/resources/`

```
app/src/test/resources/old-backup-sample.json
app/src/test/resources/new-backup-sample.json
app/src/test/resources/old-ios-backup-sample.json
app/src/test/resources/new-ios-backup.json
```

Загрузка через `javaClass.getResourceAsStream("/<filename>")`.

### KDoc на тест-классе

```kotlin
/**
 * Тесты для [CalculateWorkoutDurationUseCase].
 */
class CalculateWorkoutDurationUseCaseTest
```

```kotlin
/**
 * Unit-тесты для WorkoutsScreenViewModel.
 */
class WorkoutsScreenViewModelTest
```

## Запреты (из AGENTS.md)

### Запрет `!!` (см. выше)

### Запрет обратных кавычек в именах тестов

### Запрет новых интеграционных тестов ViewModel

Причина (из AGENTS.md и опыта проекта):
- `runBlocking` конфликтует с `viewModelScope.launch`
- Flow репозитория не активируется в тестах
- Тесты зависают или падают
- Unit-тесты с MockK обеспечивают лучшее покрытие

В Jetpack-MyWorkouts часть интеграционных тестов ViewModel была отключена
(`@Ignore`) именно из-за этих сложностей.

Существующие интеграционные тесты ViewModel (если они есть в
`androidTest/`) обслуживай как есть, но **не создавай новых**.

### Запрет Mockito / Mockito-Kotlin

Только MockK (`io.mockk.*`). Mockito не подключён в проект.

### Не тестируй через реальные I/O в unit-тестах

- Никаких реальных Room database (это integration).
- Никакого реального DataStore (это integration).
- Никаких реальных сетевых вызовов (проект офлайн, но всё равно).
- Никакого `Context.getString` — это integration; используй
  `StubResourceProvider` или `mockk<ResourceProvider>()`.

## Что гибко

- `@DisplayName` — опционально, не злоупотребляй.
- `@Nested` — не используется в проекте, но JUnit 5 поддерживает.
- `ParameterizedTest` — не используется (хотя `@CsvSource` доступен).
  Если появится повторяющаяся логика — переходи на параметризацию.
- Импорт `assertEquals` через `*` — не принят, но и не запрещён.