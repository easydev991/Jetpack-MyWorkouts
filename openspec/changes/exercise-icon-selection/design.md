# Design

## Context

Секция выбора иконки уже существует в `ExerciseCreateScreen.kt` как
утверждённый черновик (`ExerciseIconPickerSection` + `ExerciseIconPickerCell`):
вёрстка зафиксирована и остаётся как есть — сетка `FlowRow` (форма
скроллится через `verticalScroll`), колонки и размер ячеек считаются из
фактической ширины через `BoxWithConstraints` (минимум 56dp на ячейку +
8dp промежуток, ячейки дорастягиваются до полного ряда), ячейка — квадрат
с `RoundedCornerShape(12.dp)`, обводка 0.5dp `outlineVariant` / 2dp
`primary` без сдвига layout. Черновое: хардкод строк («Иконка»,
«Без иконки») и черновые поля `selectedIconNumber`/`onIconNumberChange`
в `ExerciseCreateScreenState` с no-op дефолтами — ViewModel ещё не несёт
иконку. Data-слой готов с `exercise-icon-mvp`: `ExerciseEntity.icon_number`,
`Mappers`, репозиторий, `Exercise.iconNumber`. Каталог
`ExerciseIcons.byNumber` (стабильные номера 1..19, `Ref(light, dark)`) —
из `exercise-icon-display`. Ограничение: detekt-лимит не повышается.

## Goals / Non-Goals

**Goals:**
- Проводка `iconNumber` по слоям UI: UiState → ViewModel (обработчик,
  draftOf, prefillState, save) → ScreenState → секция.
- Локализация двух строк (ru/en) вместо хардкода.
- Фиксация формулы `hasUnsavedChanges` create-режима с учётом иконки.
- Тесты: unit (VM/UiState) + androidTest (секция, выбор, round-trip Room),
  TDD — тесты раньше реализации.

**Non-Goals:**
- Изменения data-слоя (entity/мапперы/репозиторий/схема БД) — уже готово.
- Изменение вёрстки секции (утверждённый черновик, доработка только по
  пунктам локализации и проводки состояния).
- Выбор иконки где-либо кроме `ExerciseCreateScreen` (стандартные
  упражнения не редактируются вовсе — их `iconNumber` уже назначен).
- Именованные лейблы для каждой из 19 иконок. Нормативная копия
  правила (`contentDescription = null` у ячеек иконок) — в требовании
  «Секция выбора иконки» (spec.md), здесь только явное исключение из
  цели (TalkBack читает выделение через обводку, не через имя иконки).

## Decisions

### Decision 1: `iconNumber` — поле UiState, черновые поля ScreenState удаляются

`ExerciseCreateUiState` получает `iconNumber: Int? = null` (тело
data class, рядом с `comment`). В `ExerciseCreateScreenState` черновые
`selectedIconNumber`/`onIconNumberChange` с no-op дефолтами удаляются;
взамен — обязательный (не nullable, без дефолта) параметр
`onIconNumberChange: (Int?) -> Unit`, а выбранное значение Content читает
из `state.uiState.iconNumber` — как и остальные поля формы
(`state.uiState.name` тоже читается из `uiState`). Альтернатива — оставить
зеркалящее поле в ScreenState — отклонена: дубликат источника истины,
все остальные поля формы уже живут в UiState.

`ExerciseCreateViewModel.onIconNumberChange(iconNumber: Int?)` — по
образцу `onMeasuredByChange`: `updateState { it.copy(iconNumber =
iconNumber, isSaveError = false) }`. Принимает `Int?` — повторный выбор
«Без иконки» валидное действие (в отличие от `onCategoryChange`).

Параметры секции и State-поля имеют разные имена — это сделано
намеренно, чтобы не путать API секции (имя колбэка секции —
`onIconSelect`) и канал `UiState → ScreenState → секция` (имя колбэка
state — `onIconNumberChange`). Mapping — на callsite в `ExerciseCreateForm`
(`onSelect = state.onIconNumberChange`). Фиксация имён ниже в edit points
защищает от рассинхронизации спек/тасков/дизайна при будущем рефакторинге.

**Edit points (для tasks.md §3.2; номера строк могут устаревать при
рефакторинге — обновлять здесь, не в tasks):**

- `ExerciseCreateScreenState` (`ExerciseCreateScreen.kt:103–114`):
  удалить черновые `selectedIconNumber`/`onIconNumberChange`,
  добавить обязательный `onIconNumberChange: (Int?) -> Unit`.
- Обёртка `ExerciseCreateScreen` (`ExerciseCreateScreen.kt:76–96`):
  пробросить `onIconNumberChange = viewModel::onIconNumberChange`.
- Callsite `ExerciseCreateForm` (`ExerciseCreateScreen.kt:251–254`):
  перевести на `ExerciseIconPickerSection(selectedIconNumber =
  state.uiState.iconNumber, onIconSelect = state.onIconNumberChange)`.
- Harness `stateFor` (`ExerciseCreateScreenTest.kt:65–74`):
  добавить явный `onIconNumberChange = {}` (ScreenState больше не
  имеет дефолта).
- Сигнатура `ExerciseIconPickerSection` (`ExerciseCreateScreen.kt:420–424`):
  `(selectedIconNumber: Int?, onIconSelect: (Int?) -> Unit, modifier: Modifier = Modifier)` —
  **verify / no-op**: правок не требует, приведена для контекста callsite в §3.2.

### Decision 2: `draftOf` несёт `iconNumber` — `hasChanges` пересчитывается бесплатно

В `draftOf` в конструктор `Exercise` добавляется
`iconNumber = state.iconNumber`. `updateState` уже пересчитывает
`hasChanges = draft != original` сравнением data class — отдельная
логика не нужна, смена иконки автоматически активирует `hasChanges` в
edit-режиме и `canSave`. Альтернатива — отдельный флаг «иконка менялась»
— отклонена: лишнее состояние, data-сравнение уже канон.

`prefillState` добавляет `iconNumber = exercise.iconNumber` рядом с
`comment ?: ""` — edit-предзаполнение симметрично остальным полям.

### Decision 3: иконка входит в `hasUnsavedChanges` create-режима (зафиксировано)

Канон-формула `hasUnsavedChanges` принадлежит требованию спеки
«BackHandler + hasUnsavedChanges — подтверждение закрытия» — здесь не
дублируется. Дополнение этого change к create-ветке формулы — добавить
терм `|| iconNumber != null` рядом с уже существующими
`!name.isNullOrBlank()`, `category != null`, `!comment.isNullOrBlank()`.
Edit-ветка не меняется (формула `hasChanges`, иконка уже участвует в
diff-е через `draftOf` — Decision 2). Перечисленные «уже существующие»
термы приведены только для контекста решения — канон и любые будущие
изменения формулы живут исключительно в требовании
«BackHandler + hasUnsavedChanges» (spec.md); при будущих правках формулы
этот список в design.md не обновляется.

Обоснование: выбранная иконка — пользовательский ввод, который теряется
при закрытии формы, ровно как name/category/comment; дефолт («Без
иконки», `null`) не считается изменением, поэтому открытие формы без
действий по-прежнему не вызывает диалог. Альтернатива — не включать
иконку в формулу — отклонена: несогласованно с остальными полями,
молчаливая потеря выбора. `measuredBy` остаётся вне формулы —
существующее решение (его сценарий в спеке не меняется).

### Decision 4: строковые ресурсы

- `exercise_field_icon` = «Иконка» / "Icon" — заголовок секции; ключ
  продолжает семейство `exercise_field_name`/`_category`/`_comment`.
- `exercise_icon_picker_no_icon` = «Без иконки» / "No icon" —
  contentDescription ячейки «Без иконки» (у остальных ячеек
  `contentDescription = null`, как у отображения иконок в списках).

Файлы: `app/src/main/res/values/strings.xml` (ru, default) +
`values-en/strings.xml`. Суффикс `_content_description` не используется —
в проекте он у единственного ключа-исключения, обычные ключи описаний
(`back`, `exercises_archive_button`) живут без него.

### Decision 5: тест-теги ячеек — по образцу `EXERCISE_ICON_TEST_TAG`

В `ExerciseCreateScreen.kt` вводится `internal const val
EXERCISE_ICON_PICKER_CELL_TEST_TAG = "exercise_icon_picker_cell_"`, ячейки
получают `Modifier.testTag(EXERCISE_ICON_PICKER_CELL_TEST_TAG + номер)`,
ячейка «Без иконки» — суффикс `"none"`
(`exercise_icon_picker_cell_none`). Так androidTest адресует конкретную
ячейку для тапа и проверки выбора. Альтернативы: contentDescription на
каждой ячейке (нет имён у 19 иконок, дублирует визуал для TalkBack);
единственный тег на секции + `onChildAt(index)` (хрупко к порядку).
Префикс-константа вместо 20 готовых строк — без магии.

### Decision 6: вёрстка секции не трогается, превью адаптируются под финальный API

`ExerciseIconPickerSection`/`ExerciseIconPickerCell` меняются только в
двух точках: строковые ресурсы вместо хардкода и testTag на ячейках.
Существующие экранные превью в `ExerciseCreateScreenPreview.kt`
(`ExerciseCreateScreenContentEmptyPreview`, `Filled`, `WithComment`,
`Time`, `EditUnchanged`, `EditChanged`, `WithIconSection`) адаптируются
под финальный API: `previewScreenState` теряет параметры
`selectedIconNumber`/`onIconNumberChange` и принимает выбранную иконку
через `ExerciseCreateUiState(iconNumber = …)`. Секционное превью
(`ExerciseIconPickerSectionPreview`, через helper `IconPickerFrame`) не
меняется — продолжает вызывать `ExerciseIconPickerSection` напрямую без
ScreenState, как описано в tasks.md §6.1. Новых превью не добавляется —
черновой набор уже покрывает вёрстку (детали адаптации — tasks.md §6.1).
Каждое превью — в светлой и тёмной теме (`UI_MODE_NIGHT_YES`,
`showBackground = true`, `locale = "ru"`), состояния — отдельными
`@Preview`-функциями по конвенции AGENTS.md.

### Decision 7: androidTest round-trip — Room in-memory через `WorkoutDatabase`

Round-trip (создать с иконкой → перечитать; отредактировать иконку →
перечитать) строится на реальных `WorkoutDatabase`/`RoomWorkoutDataRepository`
с `Room.inMemoryDatabaseBuilder` — паттерн `WorkoutDatabaseTest`/экранных
тестов с `ExerciseCreateScreenWithNavHost`. Схема БД не меняется —
миграции не требуются.

## Risks / Trade-offs

- [MODIFIED-дельта заменяет блоки целиком — риск потерять сценарии при
  архивации] → проверено `openspec validate` (валидатор требует полноту
  сценариев); валидация зелёная.
- [Смена формулы `hasUnsavedChanges` может сломать существующие unit/UI
  тесты, фиксирующие старую формулу] → TDD-порядок: сначала обновить/добавить
  тесты на новую формулу; прогон `make test` и `make android-test` до
  коммита.
- [detekt: растущий `ExerciseCreateScreen.kt` / длинные методы] → новые
  строки не увеличивают сложность методов (секция уже выделена);
  лимит `maxIssues: 5` не повышается, новые предупреждения исправляются
  на месте.
- [19 drawable-ресурсов в FlowRow без lazy — лишние композиции на форме]
  → черновик утверждён, 20 ячеек — незначительная цена для экрана
  редактирования; LazyVerticalGrid запрещён условием задачи (конфликт
  с `verticalScroll`).

## Migration Plan

Без миграций: схема БД не меняется, `icon_number` существует с
`exercise-icon-mvp`. Раскатка — обычный релиз; откат — revert коммита
(фича изолирована в UI-слое формы).

## Open Questions

(нет — решение по `hasUnsavedChanges` зафиксировано в Decision 3,
остальные пункты продиктованы утверждённым черновиком вёрстки.)
