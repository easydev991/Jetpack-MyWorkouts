# Fundamentals — анатомия instrumented-теста

Базовые правила для всех тестов в `app/src/androidTest/`.
Конвенции проектные: без обратных кавычек и `!!`, русские
сообщения, обязательные `when_` / `then_` в имени теста, регистр
сегментов не нормируется (подробнее — ниже), зеркалирование
структуры `app/src/main/`.

## Стек и аннотации

Инструментированные тесты — JUnit 4. **Все** классы в `androidTest/`
аннотированы `@RunWith(...)` — `@RunWith(AndroidJUnit4::class)` для
обычных, `@RunWith(Parameterized::class)` для параметризованных
(`AppearanceScreenTest`, `ExercisesDeleteFlowTest`). v2 API
(`androidx.compose.ui.test.junit4.v2`) `createComposeRule()` это
допускает, но в проекте ни один компонентный тест не пишется без
`@RunWith` — единообразие важнее минимализма аннотации.

```kotlin
// Компонентный Compose-тест (без Activity):
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SomeComponentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun subject_whenCondition_thenResult() {
        // Given
        // When
        // Then
    }
}
```

```kotlin
// Интеграционный тест (с MainActivity):
@RunWith(AndroidJUnit4::class)
class SomeIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() { ... }

    @After
    fun tearDown() { ... }

    @Test
    fun subject_whenCondition_thenResult() { ... }
}
```

- `@Test`, `@Before`, `@After`, `@get:Rule` — из `org.junit.*`
- JUnit 5 (`org.junit.jupiter.api.*`) в androidTest не используется —
  это фреймворк unit-тестов `app/src/test/`
- Compose-правила — из `androidx.compose.ui.test.junit4.v2` (v2 API)

## Именование тестов

Без обратных кавычек, обязательные маркеры `when_` / `then_`.
Префикс-subject опционален, регистр сегментов не нормируется
(в проекте встречается и snake_case, и смешанный camelCase —
конвенция мягкая). Примеры из реальных тестов:

```kotlin
// snake_case:
fun exercises_screen_when_empty_then_shows_empty_state()
fun sections_when_scrolled_then_date_header_sticks_above_rows()

// Префикс-subject с camelCase-сегментами:
fun app_whenReturnedToCatalogAfterInsert_thenShowsNewExercise()
fun row_long_press_when_standard_then_no_context_menu()
fun sections_when_long_press_in_section_then_context_menu_with_edit_and_delete()
```

Главное — смысловые маркеры `when_<условие>_then_<результат>`.
Префикс-subject (`exercises_screen`, `row_long_press`,
`sections_when_long_press…`) ставится, когда без него тест
неоднозначен (двусмысленно, что «subject» — экран, строка, секция).
Обратные кавычки в существующих тестах не используются — пиши без них.

## Given / When / Then

В тестах со сценарием (insert / navigate / perform action) тело
разделяется тремя комментариями-маркерами `// Given`, `// When`,
`// Then`. В чисто рендер-тестах (готовый `*ScreenState`, нет
действий) маркер `// When` опускается — остаются `// Given` и
`// Then` (см. SKILL.md правило 4 и Verification checklist):

```kotlin
@Test
fun exercises_screen_when_empty_then_shows_empty_state() {
    // Given
    setContentWith(ExercisesUiState.Success(exercises = emptyList()))

    // Then
    composeTestRule
        .onNodeWithText(context.getString(R.string.exercises_list_empty))
        .assertIsDisplayed()
}
```

Полный код теста — в `references/EXAMPLE.md` (раздел
«Компонентный UI-тест (без Activity)»).

## Получение контекста

```kotlin
// Для строк из ресурсов (R.string.xxx):
private val context = InstrumentationRegistry.getInstrumentation().targetContext

// Для Application-контекста (Room, провайдеры):
val context = ApplicationProvider.getApplicationContext<Context>()
```

## Правила

- **Нет `!!`** — `?.`, `?:`, `let`, `checkNotNull`, `assertNotNull(...)`
- **Сообщения assert — на русском**:

```kotlin
assertTrue(
    "Sticky-заголовок должен остаться у верхнего края над строкой секции",
    headerTop < rowTop
)
```

- **Строки UI — через ресурсы**, не хардкод:

```kotlin
composeTestRule
    .onNodeWithText(context.getString(R.string.save))
    .assertIsNotEnabled()
```

- **Файлы зеркалят структуру `app/src/main/`**:
  `ui/screens/exercises/`, `ui/screens/main/`, `data/db/`,
  `data/repository/` и т.д.
- **Имя класса** оканчивается на `Test`, `UiTest`, `IntegrationTest`
  или `InstrumentedTest` (`ExerciseDaoTest`, `MainScreenTest`,
  `RoomWorkoutDataRepositoryTest`, `ExerciseCreateLiveUpdateIntegrationTest`).

## Два типа тестов — когда что использовать

| Ситуация | Тип теста | Правило |
|---|---|---|
| Отдельный Compose-компонент (экран с готовым `*ScreenState`, диалог, кнопка) | Компонентный | `createComposeRule()`, готовый `*ScreenState`, `JetpackMyWorkoutsTheme` (по необходимости) |
| Экран с реальной ViewModel поверх Room in-memory | Компонентный | `createComposeRule()` + реальная VM + Room in-memory + `NavHost`-harness |
| DAO / база / репозиторий | Интеграционный | Room in-memory, `runBlocking`, `.first()` |
| Сквозной сценарий с реальной Activity | Интеграционный | `createAndroidComposeRule<MainActivity>()` — единственный реальный случай в проекте (`ExerciseCreateLiveUpdateIntegrationTest`) |
