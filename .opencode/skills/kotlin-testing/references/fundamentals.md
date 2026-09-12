# Fundamentals — анатомия unit-теста

Базовая структура: имя класса, имя метода, Given/When/Then, импорты,
`@BeforeEach` / `@AfterEach`.

## Расположение и зеркалирование

Unit-тесты лежат в `app/src/test/java/` и **зеркалят структуру main**:

```
app/src/main/java/com/myworkouts/data/repository/EntryValidator.kt
app/src/test/java/com/myworkouts/data/repository/EntryValidatorTest.kt
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

`MockK` импорты — из `io.mockk.*`:

```kotlin
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic   // для android.util.Log и подобных
import io.mockk.unmockkStatic
import io.mockk.slot
import io.mockk.verify
```

`kotlinx-coroutines-test` — `runTest`, `StandardTestDispatcher`,
`advanceUntilIdle`, `setMain` / `resetMain`. См. подробнее
`references/viewmodel-testing.md`.

## Именование методов

Формат: `functionName_whenCondition_thenExpectedResult` в `snake_case`.
Без обратных кавычек (запрещено AGENTS.md).

**Хорошо:**

```kotlin
@Test
fun calculate_when_same_day_then_returns_today() { ... }

@Test
fun invoke_whenAfterIntervalDays_thenKeepsCurrentTimeOfDayAndAddsDays() { ... }

@Test
fun roundTripConversion_thenPreservesAllFields() { ... }

@Test
fun save_when_sets_not_touched_then_saves_prefilled_one_set() { ... }

@Test
fun restoreBackup_whenInvalidFile_thenErrorInvalidFile() { ... }
```

Имя функции и сегменты `when`/`then` могут быть в camelCase —
главное, что разделители `_`. Подробности — в `SKILL.md` п.3.

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
fun valid_entry_returns_success() {
    // Given
    val entry = baseEntry()

    // When
    val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

    // Then
    assertTrue(result.isSuccess, "Корректная REPS-запись валидна")
}
```

Для тривиальных тестов (`assertThrows`, одна строка действия) — допустим
объединённый `// When + Then`:

```kotlin
@Test
fun valueOf_throws_for_unknown_name() {
    // Given
    val unknown = "MADE_UP"

    // When + Then
    assertTrue(
        runCatching { ExerciseCategory.valueOf(unknown) }.isFailure
    )
}
```

## Базовый шаблон (pure, без зависимостей)

Когда тестируемый класс не имеет зависимостей и не работает с I/O,
шаблон минимален:

```kotlin
class EntryValidatorTest {
    @Test
    fun valid_entry_returns_success() {
        // Given
        val entry = baseEntry()

        // When
        val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

        // Then
        assertTrue(result.isSuccess, "Корректная REPS-запись валидна")
    }
}
```

Для объектов с состоянием — поле создаётся один раз как `val`:

```kotlin
class DurationFormatterTest {
    @Test
    fun format_duration_when_seconds_only_then_seconds() {
        assertEquals("5 сек", formatDuration(5, "", "мин", "сек"))
    }
}
```

## Шаблон с `@BeforeEach` / `@AfterEach`

Когда нужна подготовка (диспетчер, fakes, mocks):

```kotlin
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private lateinit var repository: MainViewModelFakeRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        repository = MainViewModelFakeRepository(initialEntries = listOf(item1, item2))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }
    // ... tests
}
```

См. полный шаблон — `references/viewmodel-testing.md`.

## KDoc на тест-классе

Допустимо и принято:

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

## Что НЕ делать

- Не оборачивай `@Test` в try/catch «чтобы тест не падал» —
  это скрывает баги.
- Не делай `assertEquals(true, condition)` — пиши `assertTrue(condition)`.
- Не пиши `// Should be 1` над `assertEquals` — сообщение уже там.
- Не используй `!!` для распаковки результата — `as?` или
  `checkNotNull` + сообщение.
- Не создавай helper-методы «для красоты» — AAA-маркеры достаточны.
- Не дублируй канонические примеры (`FakeWorkoutDataRepository`,
  формат `buildBackupJson`) — оставляй ссылку на `references/EXAMPLE.md`
  или канонический файл.