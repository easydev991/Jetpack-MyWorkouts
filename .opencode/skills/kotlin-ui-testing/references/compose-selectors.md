# Compose selectors — поиск нод, действия, assertions

Работа с `ComposeTestRule` в UI-тестах Jetpack-MyWorkouts. Все примеры —
из реальных тестов `app/src/androidTest/java/com/myworkouts/ui/`.

## Создание правила

```kotlin
// Компонентный тест (без Activity):
@get:Rule
val composeTestRule = createComposeRule()

// Интеграционный тест (реальная MainActivity):
@get:Rule
val composeTestRule = createAndroidComposeRule<MainActivity>()
```

Контент задаётся в `setContent { }`, компонент при необходимости
оборачивается в тему:

```kotlin
composeTestRule.setContent {
    JetpackMyWorkoutsTheme {
        ExercisesScreenContent(
            state = ExercisesScreenState(
                uiState = ExercisesUiState.Success(exercises = listOf(pullups, plank)),
                onAddExerciseClick = {}
            )
        )
    }
}
```

## Селекторы

```kotlin
composeTestRule.onNodeWithText(text)                          // по тексту
composeTestRule.onNodeWithContentDescription(desc)            // по contentDescription
composeTestRule.onAllNodesWithContentDescription(desc)        // все ноды (для списков)
composeTestRule.onAllNodesWithText(text)                      // все ноды с текстом
```

## Действия

```kotlin
.performClick()                // клик
.performTextInput("text")      // ввод текста
.performScrollToNode(hasText("..."))  // скролл до ноды с заданным текстом
.fetchSemanticsNode()          // получение семантики ноды (для bounds и т.п.)
.performTouchInput { longClick() }  // long-press через touch injection
```

> `click`/`longClick`/`doubleClick` — extension-функции
> `TouchInjectionScope`, каждой нужен свой импорт:
> `import androidx.compose.ui.test.click`,
> `import androidx.compose.ui.test.longClick`.

## Assertions

```kotlin
.assertExists()                // нода существует
.assertDoesNotExist()          // нода отсутствует
.assertIsDisplayed()           // отображается
.assertIsEnabled()             // активна
.assertIsNotEnabled()          // неактивна
.assertCountEquals(n)          // количество нод = n
.assertTextEquals(text)        // текст ноды = text
```

> **Важно:** `.assert*()` — это **методы** `SemanticsNodeInteraction`,
> а не top-level функции. В Compose 1.12+ импорты вида
> `import androidx.compose.ui.test.assertExists` **не резолвятся**
> (`Unresolved reference 'assertExists'`). Просто вызывайте на receiver
> (`composeTestRule.onNodeWithText(...).assertExists()`) — Kotlin
> резолвит метод через тип receiver'а без импорта. То же для
> `assertIsDisplayed`, `assertCountEquals`, `assertTextEquals` и т.п.

## Ожидание idle-состояния

```kotlin
composeTestRule.waitForIdle()   // ожидание завершения рекомпозиций

// Ожидание условия с таймаутом (вместо Thread.sleep):
composeTestRule.waitUntil(timeoutMillis = 1000) {
    composeTestRule
        .onAllNodesWithContentDescription(searchDescription)
        .fetchSemanticsNodes()
        .isEmpty()
}

// Действие на idle (например, обновить состояние или навигация):
composeTestRule.runOnIdle {
    navController.popBackStack()
}
```

`Thread.sleep` в проекте не используется для ожидания UI — только
`waitForIdle()`, `waitUntil(...)` и `runOnIdle { }`. Единственное
исключение — `kotlinx.coroutines.delay(...)` для воспроизведения
реального таймаута сценария (например, `WhileSubscribed(5000)` в
`ExerciseCreateLiveUpdateIntegrationTest`).

## Проверка позиции нод (boundsInRoot)

Канонический паттерн проверки взаимного расположения элементов
(сортировка, sticky-заголовки, порядок секций):

```kotlin
val headerTop =
    composeTestRule
        .onNodeWithText(dateFormatter.format(today.minusDays(5)))
        .assertIsDisplayed()
        .fetchSemanticsNode()
        .boundsInRoot.top
val rowTop =
    composeTestRule
        .onNodeWithText("Планка")
        .assertIsDisplayed()
        .fetchSemanticsNode()
        .boundsInRoot.top
assertTrue(
    "Sticky-заголовок должен остаться у верхнего края над строкой секции",
    headerTop < rowTop
)
```

Полный контекст использования — `MainScreenSectionsTest`
(`app/src/androidTest/.../ui/screens/main/MainScreenSectionsTest.kt`).

## Работа с несколькими нодами

Списки элементов с одинаковым contentDescription:

```kotlin
// Несколько кастомных цветов:
composeTestRule
    .onAllNodesWithContentDescription(colorDescription)
    .assertCountEquals(7)

// Выбор конкретной ноды из списка:
composeTestRule.onAllNodesWithContentDescription(colorDescription)[0].performClick()
```

## Проверка колбэков

Паттерн: флаг `var onAddExerciseClickCalled = false` в `setContent`,
клик по кнопке, проверка флага на idle:

```kotlin
var onAddExerciseClickCalled = false

composeTestRule.setContent {
    ExercisesScreenContent(
        state = ExercisesScreenState(
            uiState = ExercisesUiState.Success(exercises = listOf(pullups)),
            onAddExerciseClick = { onAddExerciseClickCalled = true }
        )
    )
}

composeTestRule
    .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
    .performClick()

composeTestRule.runOnIdle {
    org.junit.Assert.assertTrue(
        "Клик по FAB должен вызвать колбэк onAddExerciseClick",
        onAddExerciseClickCalled
    )
}
```

## M3 DatePicker: семантика дней (material3 1.4+)

Номер дня **вычищен из семантики**: внутренний `Text("20")` обёрнут в
`clearAndSetSemantics {}`, ячейка несёт только локализованный description
даты (пример: «вт, 16 июля 2024 г.»). Поэтому:

- `onNodeWithText("20")` — **не найдёт** день, тест упадёт с
  «could not find any node»;
- `performClick()` на недоступной дате — **упадёт** (у disabled-ноды нет
  click-action), паттерн «тап по недоступному → ничего не произошло»
  не работает.

Факты о сетке, на которых строятся надёжные проверки:

- сетка месяца всегда 6×7 = 42 ячейки, но дни соседних месяцев — пустые
  `Spacer`-ы **без семантики**: day-ячейки есть только у дней текущего
  месяца → число disabled-нод детерминировано и **не зависит от локали**;
- `onAllNodes(...)` ищет во всём дереве, включая форму под диалогом:
  сделай состояние валидным (нет disabled-кнопок за спиной диалога) или
  учитывай лишние ноды в подсчёте.

Рабочий паттерн: дата 15.07.2024, момент открытия — тот же день →
недоступны 16–31 июля, ровно 16 disabled-ячеек:

```kotlin
// Импорты:
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click          // extension — нужен импорт!
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performTouchInput

// Недоступные дни считаются по признаку Disabled:
composeTestRule
    .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    .assertCountEquals(16)

// Тап по недоступной дате — только по координатам (физический жест
// по bounds ноды, click-action не требуется):
composeTestRule
    .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled))
    .onFirst()
    .performTouchInput { click(center) }
```

Точная логика границы доступных дат (какие millis доступны) выносится
в pure-функцию без Compose-рантайма (internal top-level рядом с
форматтерами пикера) и покрывается JVM-тестами; UI-тест оставляет
только проверку факта «недоступные дни в пикере есть» — кнопка
«ОК» без выбора дня не меняет поле ввода, его текст проверяется
через `DateTimeFormatter` без хардкода локали.
