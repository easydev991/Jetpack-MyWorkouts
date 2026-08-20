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
