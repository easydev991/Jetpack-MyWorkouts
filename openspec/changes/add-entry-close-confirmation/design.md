# Design — add-entry-close-confirmation

## Context

Экран записи (`EntryCreateScreen` + `EntryCreateViewModel` +
`EntryCreateUiState`) уже различает режимы создания/редактирования и имеет
производные `canSave` (`EntryCreateUiState`) и `hasChanges`
(`derivedState()` в ViewModel). Закрытие сегодня происходит сразу по
`onBackClick` — и из стрелки TopAppBar, и из успешного сохранения. Требования —
в `specs/workout-entry-screen/spec.md` (дельта), мотивация — в proposal.

## Goals / Non-Goals

- Перехват обоих путей закрытия (стрелка и системный back) с подтверждением
  только при наличии несохранённых изменений.
- Условие «есть что терять» — чистая функция состояния, тестируемая на JVM.
- Non-goals: автосохранение/черновики, подтверждение при ошибке сохранения,
  новые зависимости.

## Decisions

### Decision 1: `hasUnsavedChanges` — вычисляемое свойство `EntryCreateUiState`

Рядом с `canSave`, без новых полей и булевых флагов «трогал ли пользователь»:

```kotlin
val hasUnsavedChanges: Boolean
    get() = if (isEditMode) canSave else createModeChanges

private val createModeChanges: Boolean
    get() = selectedExercise != null ||
        setsText.toIntOrNull() != 1 ||
        repsText.isNotEmpty() ||
        durationSec != null ||
        (weightText.toDoubleOrNull() ?: 0.0) != 0.0 ||
        comment.isNotEmpty()
```

- Режим редактирования: `canSave` уже равен «валидная форма И есть
  отличия» (`formValid && hasChanges`) — ровно условие из спеки.
- Вес сравнивается численно: «0», «0.0», «0.00» — все равны дефолту
  «без веса».
- Дата/время не участвуют (спека: предзаполняются моментом открытия).
- Откат ввода до дефолтов автоматически гасит условие — сравнение значений,
  а не факта ввода.
- Чистая функция без ViewModel → юнит-тесты на состояние, без корутин и
  фейков.

Альтернатива — считать в `derivedState()` ViewModel: отклонена, там
`hasUnsavedChanges` тестируемо только через поднятие ViewModel с корутинами
и источником времени; свойство на `EntryCreateUiState` позволяет вести TDD
на чистом состоянии.

### Decision 2: Флаг диалога — `remember` в `EntryCreateScreenContent`

`var showConfirmDialog by remember { mutableStateOf(false) }`. Чисто UI-состояние
показа оверлея, ViewModel не расширяется. Один обработчик на оба источника:

```kotlin
val attemptClose = {
    if (state.uiState.hasUnsavedChanges) showConfirmDialog = true
    else state.onBackClick()
}
```

Стрелка TopAppBar и `BackHandler` вызывают `attemptClose`; confirm диалога —
`state.onBackClick()` (потеря изменений), dismiss/onDismissRequest — только
`showConfirmDialog = false`. Путь сохранения не меняется:
`viewModel.save(onSaved = onBackClick)` закрывает экран напрямую, минуя
перехват. Лямбда пересоздаётся при каждой рекомпозиции и читает
`state.uiState` в момент вызова — значения не «протухают», `remember`
для неё не нужен.

Альтернатива — `rememberSaveable` не требуется: выбран `remember`, и
исчезновение диалога при повороте некритично (экран остаётся открытым,
данные не теряются, пользователь повторит тап закрытия).

### Decision 3: `BackHandler(enabled = state.uiState.hasUnsavedChanges)`

`androidx.activity.compose.BackHandler` в `EntryCreateScreenContent` (внутри
`@Composable`, рядом с `Scaffold`). Когда изменений нет, обработчик отключён —
системный back работает дефолтно (навигация назад).

### Decision 4: Диалог — приватный composable в `EntryCreateScreen.kt`

`EntryCloseConfirmDialog(onConfirm, onDismiss)` поверх `MaterialTheme`
`AlertDialog`: заголовок/текст из новых строк, confirm-кнопка «Закрыть» c
`MaterialTheme.colorScheme.error`, dismiss — существующая строка `cancel`.
Preview — в `EntryCreateScreenPreview.kt` по конвенции проекта (светлая +
тёмная, `showBackground`, `locale = "ru"`).

### Decision 5: Строки

`entry_confirm_close_dialog_title` / `entry_confirm_close_dialog_message` /
`entry_confirm_close` (инфикс `_dialog_` — как `entry_list_delete_dialog_*`;
кнопка — строка действия, без инфикса) — ru основной, en дополнительный;
dismiss-кнопка — существующий `cancel`.

## Risks / Trade-offs

- [В режиме создания смена только даты/времени не защищена диалогом] →
  принято спекой: дата/время предзаполнены моментом открытия и почти никогда
  не являются единственной ценностью пользователя.
- [Диалог не переживает поворот экрана] → принято (Decision 2): экран и
  данные остаются, пользователь повторит тап закрытия.
- [`BackHandler` в режиме редактирования до загрузки записи] → безопасно по
  построению: пока запись не загружена, `hasChanges = false`, `canSave =
  false` → `hasUnsavedChanges = false` → back работает дефолтно.

## Migration Plan

Только прямой ход: свойство → строки → перехват/диалог → тесты. Откат —
revert коммита, миграций данных нет.

## Open Questions

Нет.
