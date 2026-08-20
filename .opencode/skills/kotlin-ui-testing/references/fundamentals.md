# Fundamentals — анатомия instrumented-теста

Базовые правила для всех тестов в `app/src/androidTest/`.
Конвенции проектные: snake_case, без `!!`, русские сообщения,
зеркалирование структуры `app/src/main/`.

## Стек и аннотации

Инструментированные тесты — JUnit 4. Интеграционные тесты
(Activity, data/, reminder/) и большинство компонентных Compose-тестов
(7 из 10) аннотированы `@RunWith(AndroidJUnit4::class)`. Без него —
WorkoutDurationTextTest, ColorSelectorUiTest, WorkoutTagFilterDialogTest
(v2 API не требует):

```kotlin
// Компонентный Compose-тест — вариант без @RunWith
// (WorkoutDurationTextTest, ColorSelectorUiTest, WorkoutTagFilterDialogTest):
import androidx.compose.ui.test.junit4.v2.createComposeRule

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
// Интеграционный тест (с MainActivity или слоем данных):
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

snake_case без обратных кавычек. В проекте встречаются три формата:

```kotlin
// Формат 1: subject_whenCondition_thenResult
fun dialog_whenDraftSelectedAndNoCurrentFilter_thenResetButtonEnabled()
fun dialog_whenSameColorClicked_thenDeselects()
fun workoutDurationText_whenDisplayed_thenShowsCorrectText()

// Формат 2: when_condition_then_result (без префикса subject)
fun when_items_count_4_then_search_field_not_displayed()
fun when_user_typed_query_with_3_items_then_search_field_stays_visible()

// Формат 3: составной — subject_verb_suffix_thenResult (ThemeIconScreenTest)
fun themeIconScreen_displaysAppBarWithBackButton()
fun themeIconScreen_clicksLightTheme_callsOnThemeChange()
```

Обратные кавычки в существующих тестах не используются — пиши без них.

## Given / When / Then

Каждый тест разделяется тремя комментариями-маркерами:

```kotlin
@Test
fun workoutDurationText_whenDisplayed_thenShowsCorrectText() {
    // Given
    val testText = "5 дней"

    // When
    composeTestRule.setContent { WorkoutDurationText(formattedText = testText) }

    // Then
    composeTestRule.onNodeWithText(testText).assertExists()
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
    "A (09:00) должен быть выше B (18:00) при сортировке «сначала старые», " +
        "но topA=$topA, topB=$topB",
    topA < topB
)
```

- **Строки UI — через ресурсы**, не хардкод:

```kotlin
composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsNotEnabled()
```

- **Файлы зеркалят структуру `app/src/main/`**: `ui/screens/createedit/`,
  `ui/ds/`, `data/database/dao/`, `reminder/`, `ui/viewmodel/` и т.д.
- **Имя класса** оканчивается на `Test`, `UiTest`, `IntegrationTest`
  или `InstrumentedTest` (`ColorSelectorUiTest`, `WorkoutDaoTest`,
  `WorkoutDetailsScreenViewModelIntegrationTest`,
  `WorkoutReminderAlarmSchedulerInstrumentedTest`).

## Два типа тестов — когда что использовать

| Ситуация | Тип теста | Правило |
|---|---|---|
| Отдельный Compose-компонент (WorkoutDurationText, ColorSelector, диалог, кнопка) | Компонентный | `createComposeRule()`, изоляция фейками, `MyWorkoutsTheme` (по необходимости) |
| Реальный экран / ротация / навигация | Интеграционный | `createAndroidComposeRule<MainActivity>()`, реальный Room |
| DAO / база | Интеграционный | Room in-memory, `runBlocking` |
| ViewModel с БД | Интеграционный | `MainDispatcherRule` + Turbine |
| AlarmManager / Receiver | Интеграционный | `InstrumentationRegistry`, shell-пермишены |
