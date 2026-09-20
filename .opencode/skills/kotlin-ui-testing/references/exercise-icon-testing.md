# Поиск иконки упражнения в UI-тестах (exercise-icon-display)

`ExerciseIcon` рисует `Image` с `contentDescription = null` (требование
спеки: название упражнения уже озвучивается строкой) — стандартные
матчеры (`onNodeWithText`, `onNodeWithContentDescription`) его не
находят. Проектный тестовый хук — тег `EXERCISE_ICON_TEST_TAG`.

## Тег и поиск

Тег объявлен рядом с композаблом: `ui/icons/ExerciseIcon.kt`.

```kotlin
// Поиск — ТОЛЬКО по unmerged-дереву: иконка лежит внутри строки
// с semantics(mergeDescendants = true), в merged-дереве её нода
// сливается со строкой и тег не находится (0 нод).
composeTestRule
    .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)
    .assertCountEquals(1)
```

Импорт: `com.myworkouts.ui.icons.EXERCISE_ICON_TEST_TAG` (internal —
доступен из androidTest того же модуля).

## Позиционные проверки («иконка левее названия»)

В merged-дереве `onNodeWithText` возвращает слитую строку целиком —
её `boundsInRoot` совпадают со всей строкой (left = 0), сравнение
врёт. Оба сравниваемых узла берём из unmerged-дерева:

```kotlin
val iconLeft =
    composeTestRule
        .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)[0]
        .fetchSemanticsNode()
        .boundsInRoot.left
val nameLeft =
    composeTestRule
        .onNodeWithText(name, useUnmergedTree = true)
        .fetchSemanticsNode()
        .boundsInRoot.left
assertTrue("Иконка должна отображаться слева от названия", iconLeft < nameLeft)
```

## Тёмный/светлый вариант ресурса — по яркости пикселей

Какой именно drawable нарисован, семантика не раскрывает. Варианты
пары инвертированы по яркости (светлый ~ `#434343`, тёмный ~
`#DCDCDC`), поэтому вариант определяется по средней яркости «чернил»
— пикселей, отличных от фона по яркости больше порога: в светлой
теме чернила тёмные (< 0.5), в тёмной — светлые (>= 0.5).

```kotlin
// Захват ноды иконки в битмап (v2: androidx.compose.ui.test.captureToImage)
val bitmap =
    composeTestRule
        .onAllNodesWithTag(EXERCISE_ICON_TEST_TAG, useUnmergedTree = true)[0]
        .captureToImage()
```

Полный хелпер `capturedIconGlyphLuminance()` и оба теста-канона —
`MainScreenTest.row_when_dark_theme_then_icon_uses_dark_variant` /
`row_when_light_theme_then_icon_uses_light_variant` (каждый со своим
`setContent` — v2 запрещает второй `setContent` в тесте, см. правило
14 SKILL.md; тема задаётся через `CompositionLocalProvider(
LocalConfiguration provides ...)`).

## Где используется

- `MainScreenTest` — наличие/отсутствие/неизвестный номер, тёмный и
  светлый вариант (эталонный экран, design Decision 6);
- `ExercisesScreenTest`, `ArchivedExercisesScreenTest` — строка
  списка и архива (наследование через общий `ExerciseListItemRow`);
- `EntryCreateScreenTest` — опции dropdown (`leadingIcon`) и
  архивное исходное упражнение в edit-mode.
