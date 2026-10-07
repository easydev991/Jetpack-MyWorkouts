# Spec Delta

## ADDED Requirements

### Requirement: BackHandler + hasChanges

`CategoryCreateScreenContent` SHALL использовать `BackHandler` и диалог
подтверждения при несохранённых изменениях — канон требования «BackHandler +
hasUnsavedChanges — подтверждение закрытия» спеки `exercise-create-screen`
(локальный `showConfirmDialog`, `attemptClose`, переиспользование строк
`entry_confirm_close_dialog_title` / `entry_confirm_close_dialog_message` /
`entry_confirm_close` / `cancel` без новых ключей).

#### Scenario: Системный back с введённым именем — диалог

- **WHEN** `hasChanges == true` AND нажимается системный back
- **THEN** показывается диалог подтверждения закрытия

#### Scenario: Пустая форма закрывается без диалога

- **WHEN** create-форма не тронута AND нажимается системный back
- **THEN** экран закрывается без диалога

### Requirement: CategoryColorPicker — отображение и регенерация цвета

Форма SHALL содержать компонент `CategoryColorPicker` под полем
«Название»: `Row` с заголовком «Цвет» (`R.string.category_field_color` —
«Цвет» / "Color"), занимающим всю доступную ширину (`Modifier.weight(1f)`),
справа — вложенный `Row` из двух элементов: `IconButton` со стандартной
иконкой `Icons.Default.Refresh` и кружок текущего цвета (заливка
`colorArgb`, форма `CircleShape`). Компонент SHALL работать в обоих
режимах экрана (create и edit) и SHALL быть доступным: contentDescription
кнопки (`R.string.category_color_regenerate` — «Обновить цвет» /
"Regenerate color") и кружка (`R.string.category_color_current` —
«Текущий цвет» / "Current color"). `colorArgb == null` SHALL отображаться
нейтральным плейсхолдером (цвет темы), не пустотой. Тап по `IconButton`
SHALL вызывать колбэк регенерации цвета `onRegenerateColorClick`
(требование «ViewModel, UiState и canSave»).

#### Scenario: Отображение в create-режиме

- **WHEN** экран открыт в режиме создания
- **THEN** под полем «Название» виден заголовок «Цвет», кнопка refresh
  и кружок с сгенерированным цветом

#### Scenario: Отображение в edit-режиме

- **WHEN** экран открыт в режиме редактирования категории
- **THEN** кружок показывает исходный `colorArgb` категории до первого
  нажатия refresh

#### Scenario: Тап по refresh вызывает регенерацию

- **WHEN** нажимается `IconButton` refresh
- **THEN** вызывается `onRegenerateColorClick` AND кружок перерисовывается
  актуальным цветом (возможно, тем же по значению — спека `category-data`)

#### Scenario: Доступность

- **WHEN** компонент отрисован
- **THEN** кнопка имеет contentDescription «Обновить цвет» AND кружок —
  «Текущий цвет» (ru) / "Regenerate color" и "Current color" (en)

### Requirement: Сохранение — create и update с popBackStack

`save(onSaved: () -> Unit)` SHALL начинаться с guard `if (!canSave) return`.
В create-режиме SHALL вызываться `addCategory(name, colorArgb)` — имя
`trim()`-ится, цвет передаётся из пикера; если к моменту сохранения
цвет ещё не сгенерирован (`colorArgb == null` — имя введено раньше
первой эмиссии списка), VM SHALL сгенерировать его на месте теми же
правилами (правила генерации — спека `category-data`, требование
«Генерация и регенерация цвета категории»); в edit-режиме —
`updateCategory(id, name, colorArgb)`
(транзакция обновляет категорию и каскад ссылок упражнений по имени).
При `Result.success` SHALL вызываться `onSaved()` (`popBackStack()`); при
`Result.failure` экран SHALL оставаться открытым; текст ошибки SHALL
показываться под кнопкой «Сохранить» в `colorScheme.error`
(`R.string.category_save_error`) — паттерн `SaveBottomBar`
(`ui/components`).
Отказ update не различает причину — любой `Result.failure` показывает
общий `category_save_error` (типизированное исключение
`StandardCategoryDeletionException` — защита на уровне репозитория, UI
не открывает редактирование стандартной категории).

#### Scenario: Успешное создание закрывает экран

- **WHEN** форма валидна AND создание возвращает `Result.success`
- **THEN** `onSaved()` вызывается ровно один раз
- **AND** в репозиторий передано имя с `trim()` AND цвет из пикера

#### Scenario: Успешное редактирование передаёт оба поля

- **WHEN** в edit-режиме изменены имя и/или цвет AND `updateCategory`
  возвращает `Result.success`
- **THEN** `onSaved()` вызывается ровно один раз
- **AND** в репозиторий переданы id, имя с `trim()` и `colorArgb` из пикера

#### Scenario: Неуспешное сохранение показывает ошибку

- **WHEN** репозиторий возвращает `Result.failure`
- **THEN** экран остаётся открытым
- **AND** `isSaveError == true` AND под кнопкой «Сохранить» виден
  текст ошибки

#### Scenario: Ввод после неуспешного сохранения скрывает ошибку

- **WHEN** `isSaveError == true` после неуспешного `save()` AND
  пользователь вводит имя или регенерирует цвет
- **THEN** `isSaveError == false` (текст ошибки скрыт)

#### Scenario: Тап при canSave = false — no-op

- **WHEN** нажимается «Сохранить» при `canSave == false`
- **THEN** репозиторий НЕ вызывается AND `onSaved()` НЕ вызывается

## MODIFIED Requirements

### Requirement: CategoryCreateScreenContent — Scaffold с TopAppBar и bottomBar

`CategoryCreateScreenContent` SHALL содержать `Scaffold` с `topBar =
{ TopAppBar(title = { Text(stringResource(titleResId)) },
navigationIcon = { IconButton(onClick = attemptClose) {
Icon(Icons.AutoMirrored.Filled.ArrowBack,
contentDescription = stringResource(R.string.back)) } }) }` и
`bottomBar` с единственной кнопкой «Сохранить» (`R.string.entry_save`) —
паттерн `SaveBottomBar` (`ui/components`) с блоком ошибки под кнопкой при
`state.uiState.isSaveError == true` (см. требование «Сохранение — create
и update с popBackStack»). Заголовок SHALL зависеть от режима:
`R.string.category_create_title` («Новая категория» / "New category") в
create-режиме, `R.string.category_edit_title` («Изменить категорию» /
"Edit category") в edit-режиме (`state.uiState.isEditMode == true`).
Форма SHALL содержать поле «Название» и `CategoryColorPicker` под ним
(требование «CategoryColorPicker — отображение и регенерация цвета»).
`attemptClose` — единая точка входа для системного back и стрелки
`TopAppBar` (требование «BackHandler + hasChanges» — переименовано
в этой дельте, поведение не меняется).
Обёртка `CategoryCreateScreen` делегирует в Content через
`CategoryCreateScreenState` (паттерн `ExerciseCreateScreen — обёртка +
Content + ScreenState`: `collectAsState` + data class state с колбэками
`onNameChange` / `onRegenerateColorClick` / `onSaveClick` /
`onBackClick`).

#### Scenario: Открытие в create-режиме

- **WHEN** пользователь открывает `CategoryCreateScreen` без `categoryId`
- **THEN** `TopAppBar` отображает «Новая категория» (ru) / "New category" (en)
- **AND** поле «Название» пустое
- **AND** пикер показывает сгенерированный цвет (после первой генерации)
- **AND** кнопка «Сохранить» disabled

#### Scenario: Edit-режим показывает заголовок «Изменить категорию»

- **WHEN** `CategoryCreateScreen` открыт с `categoryId` существующей категории
- **THEN** в `TopAppBar` отображается «Изменить категорию» (ru) /
  "Edit category" (en)
- **AND** поле «Название» предзаполнено именем категории
- **AND** кружок пикера показывает текущий `colorArgb` категории

### Requirement: ViewModel, UiState и canSave

`CategoryCreateUiState` SHALL быть data class с полями `name: String? =
null`, `isEditMode: Boolean = false`, `hasChanges: Boolean = false`,
`isDuplicateName: Boolean = false`, `isCategoryMissing: Boolean = false`,
`isSaveError: Boolean = false`, `colorArgb: Int? = null` (текущий цвет
кружка; `null` — ещё не сгенерирован или не загружен) — по образцу
`ExerciseCreateUiState`. Отдельный флаг изменения цвета НЕ вводится:
стартовая линия (в create — первый сгенерированный цвет, в edit —
исходный `colorArgb` категории) хранится приватно в VM; сравнение
строго по значению `colorArgb`. `canSave` SHALL быть computed-свойством
`!name.isNullOrBlank() && !isDuplicateName && (!isEditMode || hasChanges)
&& !isCategoryMissing` (`isSaveError` НЕ входит в формулу — ошибка не
блокирует кнопку, только показывается и сбрасывается на вводе; в
create-режиме терм `(!isEditMode || hasChanges)` истинен по построению
— `canSave` от `hasChanges` не зависит). `hasChanges` — единый флаг
изменения относительно стартовой линии: имя после `trim()` ИЛИ цвет по
значению `colorArgb` (в обоих режимах). Колбэк `onNameChange`
SHALL сбрасывать `isDuplicateName` и `isSaveError = false` при каждом
вводе (перепроверка дубликата — по актуальному списку имён; паттерн
`ExerciseCreateViewModel.onXChange`); колбэк `onRegenerateColorClick`
SHALL сбрасывать `isSaveError = false`. В create-режиме VM SHALL
генерировать начальный цвет при
первой эмиссии списка категорий, регенерация SHALL выполняться по
правилам спеки `category-data` (требование «Генерация и регенерация
цвета категории») от цветов всех известных категорий (активных и
архивных).

#### Scenario: Валидная форма — canSave = true

- **WHEN** в create-режиме введено уникальное непустое имя
- **THEN** `canSave == true`

#### Scenario: Edit без изменений — canSave = false

- **WHEN** `isEditMode == true` AND форма не менялась
- **THEN** `canSave == false` AND `hasChanges == false`

#### Scenario: Изменённый только цвет включает Сохранить в edit

- **WHEN** в edit-режиме имя не менялось AND цвет регенерирован в другое
  значение
- **THEN** `hasChanges == true` AND `canSave == true`

#### Scenario: Регенерация тем же значением — не изменение

- **WHEN** регенерация вернула цвет, равный стартовому по значению
  `colorArgb`
- **THEN** `hasChanges == false`
  (в обоих режимах — совпадение по значению не считается изменением)

#### Scenario: Регенерированный цвет в create — несохранённое изменение

- **WHEN** в create-режиме имя пустое AND пользователь регенерировал цвет
  в другое значение
- **THEN** `hasChanges == true` (диалог подтверждения закрытия)

### Requirement: Edit-режим: загрузка и предзаполнение

`CategoryCreateViewModel` SHALL принимать `categoryId: Long` (`NO_CATEGORY_ID`
— create-режим). В edit-режиме VM SHALL загрузить категорию по id: при
отсутствии/ошибке — `isCategoryMissing = true` (кнопка «Сохранить»
недостижимо disabled, поведение по паттерну `isExerciseMissing`); при
успехе — предзаполнить поле имени, `colorArgb` (исходный цвет категории —
кружок до первой регенерации) и запомнить оригинал (имя и цвет).
`hasChanges` — результат сравнения черновика с оригиналом: имя после
`trim()` (без `lowercase()` — он применяется только при проверке
уникальности) ИЛИ цвет по значению `colorArgb`.

#### Scenario: Предзаполнение из БД

- **WHEN** экран открыт для категории «Плечи»
- **THEN** поле «Название» содержит «Плечи» AND кружок — цвет категории
  AND «Сохранить» disabled

#### Scenario: Категория не найдена

- **WHEN** категория удалена в другой сессии
- **THEN** `isCategoryMissing == true` AND «Сохранить» disabled

### Requirement: Превью в обеих темах

Превью SHALL существовать для ключевых состояний `CategoryCreateScreenContent`
(пустая create-форма, форма с введённым именем, edit-форма с предзаполнением,
ошибка дубликата, ошибка сохранения под кнопкой; кружок с цветом — в
create- и edit-состояниях) и для `CategoryColorPicker` (два ключевых
состояния: цвет задан и `colorArgb == null` — плейсхолдер) — каждое в
светлой и тёмной теме
(`JetpackMyWorkoutsTheme`, `showBackground = true`, `locale = "ru"`), в
соседнем `*Preview.kt`-файле.

#### Scenario: Превью ошибки дубликата

- **WHEN** отрисовано превью с `isDuplicateName = true`
- **THEN** под полем видна подсказка об ошибке в обеих темах

#### Scenario: Превью пикера

- **WHEN** отрисовано превью `CategoryColorPicker`
- **THEN** заголовок «Цвет», кнопка refresh и кружок видны в обеих темах

#### Scenario: Превью пикера без сгенерированного цвета

- **WHEN** отрисовано превью `CategoryColorPicker` с `colorArgb == null`
- **THEN** кружок отображается плейсхолдером (цвет темы) в обеих темах

## REMOVED Requirements

### Requirement: Сохранение — create и rename с popBackStack

**Reason**: rename заменён транзакционным обновлением имени и цвета
(`updateCategory`) — сохранение в edit-режиме пишет оба поля.
**Migration**: Требование «Сохранение — create и update с popBackStack»
(в секции ADDED этой дельты) — полное поведение с цветом.

### Requirement: BackHandler + hasUnsavedChanges

**Reason**: свойство `hasUnsavedChanges` удалено из `CategoryCreateUiState`
как чистый алиас `hasChanges` (ревью custom-categories-color-change).
**Migration**: Требование «BackHandler + hasChanges» (в секции ADDED этой
дельты) — переименованы требование и сценарная формула; механика диалога
та же, но семантика `hasChanges` расширена цветом: в create регенерация
цвета при пустом имени тоже даёт диалог (сценарий «Регенерированный цвет
в create — несохранённое изменение»), в edit — включает «Сохранить»
(формула в ADDED «ViewModel, UiState и canSave»).
