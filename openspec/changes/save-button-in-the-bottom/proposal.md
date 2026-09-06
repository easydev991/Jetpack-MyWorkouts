## Why

На экране создания/редактирования записи кнопка «Сохранить» сейчас
лежит последним элементом внутри скроллящегося `Column.verticalScroll`.
Из-за этого при длинной форме кнопка уезжает за нижнюю границу экрана,
а при открытой экранной клавиатуре — рискует быть перекрытой ей,
поскольку `imePadding()` висит на том же `Column`, что и сама кнопка.
Нужно закрепить кнопку в нижней области экрана, чтобы она всегда была
видна и всегда располагалась над клавиатурой, вне зависимости от
длины формы и позиции скролла.

## What Changes

- Кнопка «Сохранить» выносится из скроллящегося `Column` в слот
  `Scaffold.bottomBar` экрана `EntryCreateScreen`.
- Внутри `bottomBar` — контейнер с ime/navigationBars padding
  (детали — `design.md`, Decision 1).
- Текущая композабла `SaveSection` разделяется на две:
  - `SaveBottomBar` — только кнопка (живёт в `Scaffold.bottomBar`);
  - `SaveHintsSection` — блок текстов-подсказок
    (`entry_hint_*`), остаётся в скроллящемся `Column`
    после поля комментария, перед `isSaveError`-блоком.
- Скроллящийся `Column.verticalScroll` теряет модификатор
  `imePadding()` — теперь его поглощает контейнер `bottomBar`;
  `padding(paddingValues)` от Scaffold по-прежнему применяется,
  поэтому скролл-контент не уходит под кнопку.
- В `EntryCreateScreenPreview.kt` добавляются обязательные превью
  новых composable (`SaveBottomBar`, `SaveHintsSection`),
  удаляется устаревший `SaveSectionPreview` и добавляется
  long-form-превью экрана (состав состояний и порядок работ —
  `tasks.md`: 2.2, 3.1).
- Бизнес-логика `EntryCreateViewModel` и `EntryCreateUiState`,
  поведение подсказок и логика `canSave` НЕ меняются.

## Capabilities

### New Capabilities

Нет.

### Modified Capabilities

- `workout-entry-screen`: два изменения в существующую capability:
  - `## MODIFIED Requirements` — изменён существующий Requirement
    «Состав экрана создания записи» и его три Scenario: «кнопка
    сохранения» удаляется из top-to-bottom перечислений полей
    скроллящейся части формы, и явно фиксируется, что она живёт
    в нижней области экрана (Scaffold.bottomBar), вне скролл-
    порядка.
  - `## ADDED Requirements` — новое требование о поведении кнопки
    «Сохранить» относительно экранной клавиатуры и скролла формы
    — кнопка всегда видна, всегда над клавиатурой, не скроллится
    вместе с формой; подсказки о невалидной форме остаются в
    скроллящейся части рядом с полями.

## Impact

- `app/src/main/java/com/myworkouts/ui/screens/entry/EntryCreateScreen.kt`
  — рефакторинг структуры: добавить `bottomBar`, вынести кнопку
  из `SaveSection`, разделить на кнопку + блок подсказок, убрать
  `imePadding()` с `Column`.
- `openspec/specs/workout-entry-screen/spec.md` — новая
  `Requirement`-секция и модификация существующего Requirement
  «Состав экрана создания записи» (детали изменений — раздел
  Capabilities выше).
- Никаких новых зависимостей, сторонних библиотек и кастомных
  `Modifier`-ов. Никакого `imeNestedScroll`.
