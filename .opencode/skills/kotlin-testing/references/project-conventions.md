# Project Conventions

Соглашения Jetpack-MyWorkouts для unit-тестов. Часть из них —
требования AGENTS.md (обязательные), часть — устоявшаяся практика
(принятая, но гибкая).

## Обязательные (из AGENTS.md)

### Никакого `!!`

Даже в тестах. Используй `?.`, `?:`, `let`, `checkNotNull`:

```kotlin
// ПЛОХО
val workout = repository.getExercise(id)!!
val reminder = successState.reminder!!

// ХОРОШО
val result = repository.getExercise(id)
assertTrue(result.isSuccess, "getExercise вернул Result")
val workout = result.getOrThrow()

// Или с сообщением
val reminder = successState.reminder
assertNotNull(reminder, "Запись должна быть не null")
```

### `snake_case` для имён тестов, без обратных кавычек

```kotlin
// ПЛОХО — обратные кавычки
@Test
fun `valid entry returns success`() { }

// ХОРОШО
@Test
fun valid_entry_returns_success() { }
```

Разделители `_`, имя функции и сегменты `when`/`then` могут быть в
camelCase — главное, что разделители `_`. Подробности — в `SKILL.md` п.3.

### Файлы тестов в `app/src/test/`, зеркалят `app/src/main/`

```
app/src/main/java/com/myworkouts/data/repository/EntryValidator.kt
app/src/test/java/com/myworkouts/data/repository/EntryValidatorTest.kt
```

### Имя тест-класса = `<Class>Test`

```
EntryValidator → EntryValidatorTest
MainViewModel → MainViewModelTest
WorkoutDataRepository → WorkoutDataRepositoryTest
```

### Импорты только из JUnit 5

`org.junit.jupiter.api.*`, не `org.junit.*`.

## Устоявшиеся (следуй для единообразия)

### Given / When / Then в каждом тесте

Три маркера-комментария. Для тривиальных тестов (`assertThrows`,
одна строка действия) — допустим объединённый `// When + Then`
(см. `SKILL.md` п.2 и `references/fundamentals.md`).

### Русские сообщения в `assert`

```kotlin
assertEquals(2, items.size, "Должно быть 2 элемента")
```

Сообщения — на русском, как в существующих тестах. Без исключений:
проваленный тест читает разработчик, и ему проще на русском —
сообщения на английском увеличивают время локализации бага.

### Fake предпочтительнее Mock для репозиториев с Flow

См. `references/fakes.md`. `WorkoutDataRepository` — `Fake<Name>`
на `MutableStateFlow` (не `mockk() returns flowOf(...)`). Текущие
Fakes: `MainViewModelFakeRepository`, `ExercisesViewModelFakeRepository`,
`AppDataViewModelFakeRepository`, `FakeWorkoutDataRepository`,
`FakeExerciseRepository`.

### Private inner class для Fake

`MainViewModelFakeRepository`, `FakeWorkoutDataRepository`,
`FakeExerciseRepository` — объявляются как `private class` внутри
тест-класса. Это даёт полный контракт рядом с тестом. Порог и правила
выноса — в `references/fakes.md` → «Где размещать Fakes» (6+
дублирований).

### Имя Fake-класса — `<ViewModelName>FakeRepository` или `Fake<InterfaceName>`

```kotlin
private class MainViewModelFakeRepository : WorkoutDataRepository
private class ExercisesViewModelFakeRepository : WorkoutDataRepository
private class AppDataViewModelFakeRepository : WorkoutDataRepository
private class FakeWorkoutDataRepository(exercises: List<Exercise>) : WorkoutDataRepository
private class FakeExerciseRepository : WorkoutDataRepository
```

Префикс по тестируемой VM устраняет конфликты при общем компилируемом
test source set (`private` на top-level изолирует только в пределах
модуля).

### `lateinit var` для всего, что создаётся в `@BeforeEach`

```kotlin
private lateinit var repository: MainViewModelFakeRepository
private lateinit var viewModel: MainViewModel
private lateinit var testDispatcher: TestDispatcher
```

Immutable поля (например, `private val useCase = ...()`) — `val`.

### `@OptIn(ExperimentalCoroutinesApi::class)` на классе

Если используется `StandardTestDispatcher` / `TestScope` —
на уровне класса, не метода:

```kotlin
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    // ...
}
```

### `runTest(testDispatcher) { ... }` как выражение

JUnit 5 + coroutines-test поддерживают `runTest(testDispatcher)` как
тело функции. Передаём `testDispatcher` из `setMain`, иначе
корутины не подхватят тестовый планировщик:

```kotlin
@Test
fun state_when_subscribed_then_success_with_entries() =
    runTest(testDispatcher) {
        // Given
        val viewModel = MainViewModel(repository)

        // When — подписка активирует WhileSubscribed
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is MainUiState.Success)
    }
```

### `mockkStatic(Log::class)` для ViewModel с логированием

Если ViewModel использует `android.util.Log.e(...)`, добавь
`mockkStatic(Log::class)` в `@BeforeEach` и `unmockkStatic(Log::class)`
в `@AfterEach`. Касается `AppDataViewModel` и `ExercisesViewModel`.
См. `references/mocking-mockk.md`.

### Тестовые JSON-данные — строкой в коде, не из `app/src/test/resources/`

В проекте нет файлов `app/src/test/resources/*.json`. JSON подаётся
строкой через `"""…""".trimIndent()` (Export/ImportBackupUseCase).
Это держит тесты JVM-only, без Android-зависимостей. Формат файла
описан в `references/data-layer.md` → «JSON-сборка и парсинг».

### KDoc на тест-классе

```kotlin
/**
 * Unit-тесты для [EntryValidator].
 */
class EntryValidatorTest
```

```kotlin
/**
 * Fake [WorkoutDataRepository] на `MutableStateFlow` для JVM-тестов
 * [MainViewModel]. Каталог и записи реактивные; …
 */
private class MainViewModelFakeRepository : WorkoutDataRepository
```

## Запреты (из AGENTS.md)

Содержательные запреты — ниже. Повторяющиеся правила `!!` и обратных
кавычек описаны в разделе «Обязательные» выше.

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
- Никакого `Context.getString` — это integration; нужен
  `mockk<Context>()` или `mockk<ContentResolver>()`.

## Что гибко

- `@DisplayName` — опционально, не злоупотребляй. В проекте сейчас
  нигде не используется.
- `@Nested` — не используется в проекте, но JUnit 5 поддерживает.
- `ParameterizedTest` — **используется** (см. `ExerciseCategoryTest`,
  `AppearanceViewModelTest`, `EntryValidatorTest`,
  `ImportBackupUseCaseTest`). Для повторяющихся кейсов (валидация
  диапазонов, перебор enum-значений, таблица невалидных файлов) —
  предпочтителен. См. `references/assertions.md` →
  «`@ParameterizedTest`».
- Импорт `assertEquals` через `*` — не принят, но и не запрещён.