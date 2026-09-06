# Compose: закреплённая кнопка внизу, корректно поднимающаяся над клавиатурой

Проверенный паттерн (change `save-button-in-the-bottom`, экран `EntryCreateScreen`;
сверен с референсом Jetpack-WorkoutApp — экран «Изменить пароль»,
`ChangePasswordScreen` + `ui/ds/KeyboardAwareBottomBar.kt`).

## Схема

```kotlin
Scaffold(
    topBar = { /* ... */ },
    bottomBar = { SaveBottomBar(state) }          // кнопка живёт здесь, не скроллится
) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)               // ВАЖНО: ДО verticalScroll (внешний отступ)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) { /* поля формы + подсказки (без кнопки) */ }
}
```

```kotlin
@Composable
fun SaveBottomBar(state: ...) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()              // отступ системной навигации
            .imePadding()                          // поднимает бар над клавиатурой
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Button(onClick = ..., modifier = Modifier.fillMaxWidth(), enabled = canSave) { ... }
    }
}
```

## Почему это работает

- `imePadding()` на контейнере `bottomBar` → бар растёт вверх вместе с IME.
- Scaffold измеряет `bottomBar` УЖЕ с учётом его `imePadding`, поэтому
  `paddingValues.bottom` автоматически растёт при открытой клавиатуре.
- `padding(paddingValues)` ДО `verticalScroll` → viewport скролла заканчивается
  ровно на верхней границе бара: поле в фокусе всегда выше кнопки и клавиатуры
  (BringIntoView работает внутри сжатого viewport), контент никогда не заходит
  за бар → бар может быть прозрачным `Box`, `Surface`/подложка не нужны.
- Порядок `navigationBarsPadding()` → `imePadding()` даёт максимум инсетов,
  а не сумму (consume-семантика Compose).

## Анти-паттерны (проверено — дают баги)

1. `paddingValues` ПОСЛЕ `verticalScroll` (контентный отступ внутри скролла) —
   viewport продолжается за баром: кнопка перекрывает сфокусированное поле,
   за прозрачной кнопкой просвечивает контент формы.
2. `imePadding()` на скролл-`Column` при баре с `imePadding` — конфликт/двойной
   подъём, поле может остаться за клавиатурой.
3. Кнопка внутри скроллящегося `Column` — уезжает за экран на длинной форме.
4. На экранах с глобальным `BottomNavigation` (RootScreen) его нужно скрывать,
   иначе двойной нижний отступ (замечание из референса).

## Разделение композабл

Кнопку выносить отдельно (`SaveBottomBar`), подсказки валидации оставлять в
скроллящейся форме рядом с полями (`SaveHintsSection`) — подсказки описывают
поля, а не кнопку. Инвариант spec: «кнопка неактивна И подсказка».

## Верификация

- UI-тест: длинная форма (многострочный комментарий) →
  `onNodeWithText(...entry_save).assertIsDisplayed()` без `performScrollTo()`.
- Ручная проверка на эмуляторе: фокус в последнее поле → IME открывается →
  поле выше кнопки, кнопка выше клавиатуры, под баром ничего не просвечивает.
