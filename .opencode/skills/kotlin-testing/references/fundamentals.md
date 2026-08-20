# Fundamentals — анатомия unit-теста

Базовая структура: имя класса, имя метода, Given/When/Then, импорты,
`@BeforeEach` / `@AfterEach`.

## Расположение и зеркалирование

Unit-тесты лежат в `app/src/test/java/` и **зеркалят структуру main**:

```
app/src/main/java/com/myworkouts/domain/usecase/CalculateWorkoutDurationUseCase.kt
app/src/test/java/com/myworkouts/domain/usecase/CalculateWorkoutDurationUseCaseTest.kt
```

Правила:
- Пакет теста совпадает с пакетом тестируемого класса.
- Имя класса теста — `<ClassName>Test`.
- Файл — один публичный класс-тест (private fakes внутри — допустимо).

## Импорты

JUnit 5 — **только** из `org.junit.jupiter.api`:

```kotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
```

`org.junit.Assert.*` и `org.junit.Test` — это JUnit 4, не используй
для unit-тестов. Для androidTest допустимо.

## Именование методов

Формат: `functionName_whenCondition_thenExpectedResult` в `snake_case`.
Без обратных кавычек (запрещено AGENTS.md).

**Хорошо:**

```kotlin
@Test
fun calculate_when_same_day_then_returns_today() { ... }

@Test
fun invoke_when_today_and_showminus_false_then_returns_today_string() { ... }

@Test
fun whenViewModelCreated_thenLoadsAllItems() { ... }

@Test
fun when_color_filter_selected_then_shows_only_matching_items() { ... }
```

**Допустимо, но менее предпочтительно** (русские слова):

```kotlin
@Test
fun totaldays_в_calculated_содержит_общее_количество_дней() { ... }
```

Английский snake_case читается единообразнее.

## Анатомия теста: Given / When / Then

В каждом тесте — три маркера. Это и документация, и ритм:

```kotlin
@Test
fun calculate_when_1_day_difference_then_returns_1_day() {
    // Given
    val currentDate = LocalDate.now()
    val eventDate = currentDate.minusDays(1)
    val timestamp = eventDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    // When
    val result = useCase(eventTimestamp = timestamp)

    // Then
    assertTrue(result is WorkoutDuration.Calculated, "Результат должен быть Calculated")
    val calculated = result as WorkoutDuration.Calculated
    assertEquals(1, calculated.period.days, "Должен быть 1 день")
}
```

Простые тесты (одна проверка) могут опускать `// When`, если
очевидно — но маркеры должны быть видны.

## Базовый шаблон (pure, без зависимостей)

Когда тестируемый класс не имеет зависимостей и не работает с I/O,
шаблон минимален:

```kotlin
class CalculateWorkoutDurationUseCaseTest {
    private val useCase = CalculateWorkoutDurationUseCase()

    @Test
    fun calculate_when_same_day_then_returns_today() {
        // Given
        val today = LocalDate.now()
        val timestamp = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // When
        val result = useCase(eventTimestamp = timestamp)

        // Then
        assertTrue(result is WorkoutDuration.Today, "Результат должен быть Today")
    }
}
```

Объект `useCase` создаётся один раз как `val` — он immutable.

## Шаблон с `@BeforeEach` / `@AfterEach`

Когда нужна подготовка (диспетчер, fakes, mocks):

```kotlin
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutsScreenViewModelTest {

    private lateinit var repository: FakeWorkoutRepository
    private lateinit var viewModel: WorkoutsScreenViewModel
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)

        repository = FakeWorkoutRepository()
        viewModel = WorkoutsScreenViewModel(repository, mockk(relaxed = true), mockk(relaxed = true))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }
    // ... tests
}
```

## KDoc на тест-классе

Допустимо и принято:

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

## `@DisplayName` (опционально)

JUnit 5 поддерживает `@DisplayName("...")` для читаемого имени в
IDE/отчётах. В проекте используется редко (только в
`UserPreferencesDataStoreTest`), но допустимо:

```kotlin
@DisplayName("Тесты для моделей настроек")
class UserPreferencesDataStoreTest {
    @Test
    @DisplayName("AppTheme enum должен содержать все необходимые значения")
    fun appTheme_shouldContainAllValues() { ... }
}
```

Без `@DisplayName` имя теста — это имя метода (`snake_case`).
С `@DisplayName` — заголовок из аннотации. Выбирай одно из двух,
не дублируй.

## Что НЕ делать

- Не оборачивай `@Test` в try/catch «чтобы тест не падал» —
  это скрывает баги.
- Не делай `assertEquals(true, condition)` — пиши `assertTrue(condition)`.
- Не пиши `// Should be 1` над `assertEquals` — сообщение уже там.
- Не используй `!!` для распаковки результата — `as?` или
  `checkNotNull` + сообщение.
- Не создавай helper-методы «для красоты» — AAA-маркеры достаточны.