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

Контент задаётся в `setContent { }`, компонент оборачивается в тему:

```kotlin
composeTestRule.setContent {
    MyWorkoutsTheme {
        WorkoutDurationText(formattedText = testText)
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
.fetchSemanticsNode("name")    // получение семантики ноды (для bounds и т.п.)
```

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

// Действие на idle (например, обновить состояние):
composeTestRule.runOnIdle {
    reminderState.value = reminderState.value.copy(isEnabled = true)
}
```

`Thread.sleep` в проекте не используется — только `waitForIdle()`,
`waitUntil(...)` и `runOnIdle { }`.

## Проверка позиции нод (boundsInRoot)

Для проверки взаимного расположения элементов (сортировка, порядок):

```kotlin
val nodeA = composeTestRule.onNodeWithText(titleA)
val nodeB = composeTestRule.onNodeWithText(titleB)
nodeA.assertIsDisplayed()
nodeB.assertIsDisplayed()

val topA = nodeA.fetchSemanticsNode("nodeA").boundsInRoot.top
val topB = nodeB.fetchSemanticsNode("nodeB").boundsInRoot.top
assertTrue(
    "A (09:00) должен быть выше B (18:00) при сортировке «сначала старые», " +
        "но topA=$topA, topB=$topB",
    topA < topB
)
```

## Работа с несколькими нодами

Списки элементов с одинаковым contentDescription:

```kotlin
// 6 preset-цветов + 1 кастомный:
composeTestRule
    .onAllNodesWithContentDescription(colorDescription)
    .assertCountEquals(7)

// Выбор конкретной ноды из списка:
composeTestRule.onAllNodesWithContentDescription(colorDescription)[0].performClick()
```

## Проверка колбэков

Полный пример — `references/EXAMPLE.md` (раздел «Компонентный тест
с колбэками (диалог)»). Паттерн: флаг `var onApplyCalled = false`
в `setContent`, клик по кнопке, проверка флага на idle:

```kotlin
var onApplyCalled = false

composeTestRule.setContent {
    WorkoutTagFilterDialog(
        availableColors = availableColors,
        currentFilter = currentFilter,
        onApply = { onApplyCalled = true },
        onDismiss = {}
    )
}

composeTestRule.onNodeWithText(resetText).performClick()

composeTestRule.runOnIdle {
    org.junit.Assert.assertFalse(
        "При сбросе черновика без фильтра onApply не должен вызываться",
        onApplyCalled
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

// Доступная прошлым дням механика проверяется end-to-end: «ОК» без
// выбора дня не меняет поле (текст поля — через DateTimeFormatter,
// не хардкод локали).
```

Если нужна точная логика границы дат (какие millis доступны) — вынеси её
в pure-функцию без Compose-рантайма (internal top-level рядом с
форматтерами пикера) и покрывай JVM-тестами; UI-тест оставь для факта
«недоступные дни в пикере есть».
