# Spec Delta

## Purpose

Экран создания/редактирования категории — форма с единственным полем
«Название» и кнопкой «Сохранить» в bottomBar; один маршрут обслуживает
create- и edit-режимы по образцу `ExerciseCreateScreen`.

## ADDED Requirements

### Requirement: CategoryCreateScreenContent — Scaffold с TopAppBar и bottomBar

`CategoryCreateScreenContent` SHALL содержать `Scaffold` с `topBar =
{ TopAppBar(title = { Text(stringResource(titleResId)) },
navigationIcon = { IconButton(onClick = attemptClose) {
Icon(Icons.AutoMirrored.Filled.ArrowBack,
contentDescription = stringResource(R.string.back)) } }) }` и
`bottomBar` с единственной кнопкой «Сохранить» (`R.string.entry_save`) —
паттерн `ExerciseCreateSaveBottomBar` с блоком ошибки под кнопкой при
`state.uiState.isSaveError == true` (см. требование «Сохранение — create
и rename с popBackStack»). Заголовок SHALL зависеть от режима:
`R.string.category_create_title` («Новая категория» / "New category") в
create-режиме, `R.string.category_edit_title` («Изменить категорию» /
"Edit category") в edit-режиме (`state.uiState.isEditMode == true`).
Форма SHALL содержать единственное поле — «Название». `attemptClose` —
единая точка входа для системного back и стрелки `TopAppBar` (требование
«BackHandler + hasUnsavedChanges» ниже). Обёртка `CategoryCreateScreen`
делегирует в Content через `CategoryCreateScreenState` (паттерн
`ExerciseCreateScreen — обёртка + Content + ScreenState`: `collectAsState`
+ data class state с колбэками `onNameChange` / `onSaveClick` /
`onBackClick`).

#### Scenario: Открытие в create-режиме

- **WHEN** пользователь открывает `CategoryCreateScreen` без `categoryId`
- **THEN** `TopAppBar` отображает «Новая категория» (ru) / "New category" (en)
- **AND** поле «Название» пустое
- **AND** кнопка «Сохранить» disabled

#### Scenario: Edit-режим показывает заголовок «Изменить категорию»

- **WHEN** `CategoryCreateScreen` открыт с `categoryId` существующей категории
- **THEN** в `TopAppBar` отображается «Изменить категорию» (ru) /
  "Edit category" (en)
- **AND** поле «Название» предзаполнено именем категории

### Requirement: Поле «Название» — однострочное, обязательное

Форма SHALL содержать `OutlinedTextField` с `label =
R.string.category_field_name` («Название» / "Name"), `singleLine = true`,
привязанный к `state.uiState.name`. Пустое или blank-имя SHALL блокировать
сохранение (`canSave = false`). Нормализация `trim()` выполняется в момент
сохранения.

#### Scenario: Пробелы не считаются валидным именем

- **WHEN** пользователь вводит только пробелы в поле «Название»
- **THEN** `canSave` остаётся `false`

### Requirement: Валидация уникальности имени категории

`CategoryCreateViewModel` SHALL проверять уникальность имени по
`name.trim().lowercase()` против всех существующих категорий (активных и
архивных), кроме самой редактируемой в edit-режиме. При дубликате имени
`canSave` SHALL быть `false`, а форма SHALL показывать локализованную
подсказку об ошибке под полем (`R.string.category_error_duplicate` —
«Категория с таким названием уже существует» / "A category with this name
already exists") в `colorScheme.error`. Источник существующих имён SHALL
обновляться реактивно (снимок/Flow репозитория), чтобы категория, созданная
в другой сессии, учитывалась без пересоздания экрана.

#### Scenario: Дубликат блокирует сохранение с подсказкой

- **WHEN** в БД есть категория «Спина» AND пользователь вводит «СПИНА »
  в форме создания
- **THEN** `canSave == false`
- **AND** под полем отображается «Категория с таким названием уже существует»

#### Scenario: Собственное имя в edit-режиме не дубликат

- **WHEN** в edit-режиме категории «Спина» поле «Название» содержит «Спина»
- **THEN** дубликат НЕ определяется (само-сравнение исключено
  уникальностью), `hasChanges = false` (формула — «Edit-режим: загрузка и
  предзаполнение»: `trim()` без `lowercase()`)

### Requirement: ViewModel, UiState и canSave

`CategoryCreateUiState` SHALL быть data class с полями `name: String? =
null`, `isEditMode: Boolean = false`, `hasChanges: Boolean = false`,
`isDuplicateName: Boolean = false`, `isCategoryMissing: Boolean = false`,
`isSaveError: Boolean = false` (по образцу `ExerciseCreateUiState` —
флаг ошибки сохранения, показывается под кнопкой «Сохранить», на
доступность кнопки не влияет). `canSave` SHALL быть computed-свойством
`!name.isNullOrBlank() && !isDuplicateName && (!isEditMode || hasChanges)
&& !isCategoryMissing` — паттерн `ExerciseCreateUiState.canSave`
(`isSaveError` НЕ входит в формулу — ошибка не блокирует кнопку, только
показывается; сброс на вводе). `hasUnsavedChanges` — computed-
свойство: в create-режиме `!name.isNullOrBlank()`; в edit-режиме `hasChanges`.
Колбэк `onNameChange` SHALL сбрасывать `isDuplicateName` и
`isSaveError = false` при каждом вводе (паттерн
`ExerciseCreateViewModel.onXChange`; перепроверка дубликата выполняется
по актуальному списку имён).

#### Scenario: Валидная форма — canSave = true

- **WHEN** в create-режиме введено уникальное непустое имя
- **THEN** `canSave == true`

#### Scenario: Edit без изменений — canSave = false

- **WHEN** `isEditMode == true` AND форма не менялась
- **THEN** `canSave == false` AND `hasUnsavedChanges == false`

### Requirement: Сохранение — create и rename с popBackStack

`save(onSaved: () -> Unit)` SHALL начинаться с guard `if (!canSave) return`.
В create-режиме SHALL вызываться создание категории в репозитории (имя
`trim()`-ится, цвет генерируется репозиторием); в edit-режиме —
переименование (транзакция с обновлением упражнений). При `Result.success`
SHALL вызываться `onSaved()` (`popBackStack()`); при `Result.failure` экран
SHALL оставаться открытым; текст ошибки SHALL показываться под кнопкой
«Сохранить» в `colorScheme.error` (`R.string.category_save_error` —
«Не удалось сохранить категорию» / "Failed to save category") — паттерн
`ExerciseCreateSaveBottomBar`. Отказ rename не различает причину — любой
`Result.failure` показывает общий `category_save_error` (намеренное
упрощение: типизированное исключение `StandardCategoryDeletionException` —
защита на уровне репозитория, UI не открывает rename стандартной
категории).

#### Scenario: Успешное создание закрывает экран

- **WHEN** форма валидна AND создание возвращает `Result.success`
- **THEN** `onSaved()` вызывается ровно один раз
- **AND** в репозиторий передано имя с `trim()`

#### Scenario: Неуспешное сохранение показывает ошибку

- **WHEN** репозиторий возвращает `Result.failure`
- **THEN** экран остаётся открытым
- **AND** `isSaveError == true` AND под кнопкой «Сохранить» виден
  текст ошибки

#### Scenario: Ввод после неуспешного сохранения скрывает ошибку

- **WHEN** `isSaveError == true` после неуспешного `save()` AND
  пользователь вводит имя
- **THEN** `isSaveError == false` (текст ошибки скрыт)

#### Scenario: Тап при canSave = false — no-op

- **WHEN** нажимается «Сохранить» при `canSave == false`
- **THEN** репозиторий НЕ вызывается AND `onSaved()` НЕ вызывается

### Requirement: Edit-режим: загрузка и предзаполнение

`CategoryCreateViewModel` SHALL принимать `categoryId: Long` (`NO_CATEGORY_ID`
— create-режим). В edit-режиме VM SHALL загрузить категорию по id: при
отсутствии/ошибке — `isCategoryMissing = true` (кнопка «Сохранить»
недостижимо disabled, поведение по паттерну `isExerciseMissing`); при
успехе — предзаполнить поле имени и запомнить оригинал. `hasChanges` —
результат сравнения черновика с оригиналом после `trim()` (без
`lowercase()` — он применяется только при проверке уникальности).

#### Scenario: Предзаполнение из БД

- **WHEN** экран открыт для категории «Плечи»
- **THEN** поле «Название» содержит «Плечи» AND «Сохранить» disabled

#### Scenario: Категория не найдена

- **WHEN** категория удалена в другой сессии
- **THEN** `isCategoryMissing == true` AND «Сохранить» disabled

### Requirement: BackHandler + hasUnsavedChanges

`CategoryCreateScreenContent` SHALL использовать `BackHandler` и диалог
подтверждения при несохранённых изменениях — канон требования «BackHandler +
hasUnsavedChanges — подтверждение закрытия» спеки `exercise-create-screen`
(локальный `showConfirmDialog`, `attemptClose`, переиспользование строк
`entry_confirm_close_dialog_title` / `entry_confirm_close_dialog_message` /
`entry_confirm_close` / `cancel` без новых ключей).

#### Scenario: Системный back с введённым именем — диалог

- **WHEN** `hasUnsavedChanges == true` AND нажимается системный back
- **THEN** показывается диалог подтверждения закрытия

#### Scenario: Пустая форма закрывается без диалога

- **WHEN** create-форма не тронута AND нажимается системный back
- **THEN** экран закрывается без диалога

### Requirement: Превью в обеих темах

Превью SHALL существовать для ключевых состояний `CategoryCreateScreenContent`
(пустая create-форма, форма с введённым именем, edit-форма с предзаполнением,
ошибка дубликата, ошибка сохранения под кнопкой) — каждое в светлой и тёмной
теме (`JetpackMyWorkoutsTheme`, `showBackground = true`, `locale = "ru"`),
в соседнем `*Preview.kt`-файле.

#### Scenario: Превью ошибки дубликата

- **WHEN** отрисовано превью с `isDuplicateName = true`
- **THEN** под полем видна подсказка об ошибке в обеих темах
