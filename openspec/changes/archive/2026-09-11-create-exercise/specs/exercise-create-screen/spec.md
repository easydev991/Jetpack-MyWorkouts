## Purpose

Экран создания нового упражнения — форма с обязательным названием, выбором
категории, сегментированной кнопкой типа измерения и необязательным
комментарием; сохранение добавляет `Exercise` с `isCustom = true` в
каталог.

## ADDED Requirements

### Requirement: ExerciseCreateScreenContent — Scaffold с TopAppBar и bottomBar

`ExerciseCreateScreenContent` (а не обёртка `ExerciseCreateScreen`)
SHALL содержать `Scaffold` с `topBar = { TopAppBar(title = { Text(
stringResource(R.string.exercise_create_title)) }, navigationIcon = {
IconButton(onClick = attemptClose) { Icon(Icons.AutoMirrored.Filled.
ArrowBack, contentDescription = stringResource(R.string.back)) } }) }`
и `bottomBar = { ExerciseCreateSaveBottomBar(state = state) }`.
`attemptClose` — единая точка входа для системного back и стрелки
`TopAppBar`; **канон поведения `attemptClose` (формула и связка с
`BackHandler`/`TopAppBar.navigationIcon`/`showConfirmDialog`) —
требование «BackHandler + hasUnsavedChanges — подтверждение закрытия»
ниже** (этот Requirement ссылается на канон, а не дублирует формулу). `state.onBackClick` пробрасывается из обёртки
`ExerciseCreateScreen` (обёртка только `collectAsState` + делегация в
Content, см. требование «ExerciseCreateScreen — обёртка + Content +
ScreenState»). Параметр `modifier` Content SHALL прокидываться в
`Scaffold`. Окно формы SHALL находиться в `Scaffold.content` и иметь
модификаторы `verticalScroll(rememberScrollState())` + `padding(
horizontal = 16.dp, vertical = 16.dp)`. `ExerciseCreateSaveBottomBar`
принимает `state` целиком (а не отдельные `canSave`/`onSaveClick`) —
паттерн `EntryCreateScreen.SaveBottomBar(state = state)` (см.
`EntryCreateScreen.kt` L296, L307–316). Строка `back` — существующий
ресурс («Назад» / "Back"), переиспользуется без нового ключа.

#### Scenario: Открытие экрана показывает пустую форму

- **WHEN** пользователь открывает `ExerciseCreateScreen`
- **THEN** в `TopAppBar` отображается «Новое упражнение» (ru) / "New
  exercise" (en)
- **AND** поле «Название» пустое
- **AND** в выпадающем меню «Категория» не выбрано значение
- **AND** сегмент «Повторы» активен (по умолчанию)
- **AND** поле «Комментарий» пустое
- **AND** кнопка «Сохранить» в bottomBar disabled

### Requirement: Поле «Название» — однострочное, обязательное

`ExerciseCreateScreen` SHALL содержать `OutlinedTextField` со строкой
`R.string.exercise_field_name` в качестве `label`, `singleLine = true`, и
`onValueChange`/`value`, привязанные к `state.name`. `state.name`
хранит сырой ввод пользователя (`String?`); нормализация
`String.trim()` выполняется в момент сохранения (см. требование
«Сохранение — вызов `repository.addExercise` и popBackStack») — тот же
момент, что и для других текстовых полей формы. Пустое или blank-имя
блокирует сохранение.

#### Scenario: Ввод имени активирует валидацию

- **WHEN** пользователь вводит «Отжимания» в поле «Название»
- **THEN** в `UiState.name` сохраняется «Отжимания»
- **AND** кнопка «Сохранить» всё ещё disabled — `category` ещё не выбрана

#### Scenario: Пробелы не считаются валидным именем

- **WHEN** пользователь вводит только пробелы в поле «Название»
- **THEN** `canSave` остаётся `false`

### Requirement: Сегментированная кнопка ExerciseMeasuredBy

`ExerciseCreateScreen` SHALL отображать сегментированную кнопку
(`SingleChoiceSegmentedButtonRow` из Material3) с двумя сегментами:
`R.string.exercise_measured_by_reps` («повторы» / "reps") и
`R.string.exercise_measured_by_time` («длительность» / "duration") — те
же ключи, что используются в каталоге `ExercisesScreen` (capability
`exercises-screen`, требование «Лейбл типа измерения локализован ru/en»).
По умолчанию выбран сегмент REPS. Выбор SHALL маппиться в
`ExerciseMeasuredBy.REPS` или `ExerciseMeasuredBy.TIME` в
`UiState.measuredBy`. Альтернативный UI (`ExposedDropdownMenuBox`) НЕ
используется — это явное требование. Новых строк ресурсов для этого
поля НЕ вводится (один домен — одна локализация).

#### Scenario: Сегмент REPS выбран по умолчанию

- **WHEN** `ExerciseCreateScreen` открыт
- **THEN** активен сегмент «повторы» / "reps"
- **AND** `UiState.measuredBy == ExerciseMeasuredBy.REPS`

#### Scenario: Переключение на TIME

- **WHEN** пользователь тапает сегмент «длительность» / "duration"
- **THEN** активным становится сегмент «длительность»
- **AND** `UiState.measuredBy == ExerciseMeasuredBy.TIME`

### Requirement: Категория через ExposedDropdownMenuBox — обязательная

`ExerciseCreateScreen` SHALL содержать `ExposedDropdownMenuBox` (новый
internal-компонент `CategoryDropdownField` — паттерн-близнец
`DurationDropdownField` из `EntryCreateFields.kt`, design Decision 6)
для выбора `ExerciseCategory`.
В `label` SHALL быть `R.string.exercise_field_category`. Placeholder
(non-selected) SHALL показывать `R.string.exercise_field_category_select`
(«Выберите категорию» / "Select category"). Пока `category == null`
сохранение заблокировано. Локализованный лейбл каждой категории SHALL
резолвиться через приватный file-level хелпер
`ExerciseCategory.labelResId(): Int @StringRes get() = when (this) {
ExerciseCategory.PUSH -> R.string.exercise_category_push;
ExerciseCategory.PULL -> R.string.exercise_category_pull;
ExerciseCategory.LEGS -> R.string.exercise_category_legs;
ExerciseCategory.CORE -> R.string.exercise_category_core;
ExerciseCategory.CARDIO -> R.string.exercise_category_cardio }`
и отображаться в `DropdownMenuItem(text = { Text(stringResource(labelResId())) })`.
Сам `ExerciseCategory` SHALL сохраняться в БД как enum, не как
локализованная строка — маппинг используется только для UI.

#### Scenario: Выбор категории разрешает сохранение

- **WHEN** `name` непустое (после `trim()`) AND пользователь выбрал
  категорию `PUSH` в выпадающем меню
- **THEN** `UiState.category == ExerciseCategory.PUSH`
- **AND** `canSave == true`

#### Scenario: Локализованный лейбл категории в выпадающем меню

- **WHEN** пользователь открывает выпадающее меню `CategoryDropdownField`
  в русской локали (default `values/`)
- **THEN** в `DropdownMenuItem` видны локализованные лейблы: «Толкающие»
  (для `PUSH`), «Тянущие» (для `PULL`), «Ноги» (для `LEGS`), «Кор»
  (для `CORE`), «Кардио» (для `CARDIO`) — через `stringResource(
  category.labelResId())`, а не `category.name`
- **AND** после выбора категории в БД сохраняется `Exercise.category
  == ExerciseCategory.PUSH` (или соответствующее значение), а НЕ
  локализованная строка

#### Scenario: Локализованный лейбл категории в английской локали

- **WHEN** пользователь открывает выпадающее меню `CategoryDropdownField`
  в английской локали (`values-en`)
- **THEN** в `DropdownMenuItem` видны локализованные лейблы: "Push",
  "Pull", "Legs", "Core", "Cardio"

### Requirement: Многострочное поле «Комментарий» — необязательное

`ExerciseCreateScreen` SHALL содержать `OutlinedTextField` со строкой
`R.string.exercise_field_comment` в качестве `label`, `singleLine = false`,
`minLines = 2`. Значение SHALL храниться в `UiState.comment` как
`String?`. При сохранении `comment` SHALL записываться только если
`comment?.trim()?.isNotEmpty() == true`; иначе сохраняется `null`.

#### Scenario: Пустой комментарий не сохраняется

- **WHEN** пользователь оставил поле «Комментарий» пустым
- **THEN** создаваемый `Exercise.comment == null`

#### Scenario: Комментарий с пробелами не сохраняется

- **WHEN** пользователь ввёл только пробелы в поле «Комментарий»
- **THEN** создаваемый `Exercise.comment == null`

#### Scenario: Непустой комментарий сохраняется с trim

- **WHEN** пользователь ввёл «  узкий хват  » в поле «Комментарий»
- **THEN** создаваемый `Exercise.comment == "узкий хват"`

#### Scenario: Имя с пробелами тримится при сохранении

- **WHEN** пользователь ввёл «  Жим  » в поле «Название» (остальные
  обязательные поля заполнены)
- **THEN** создаваемый `Exercise.name == "Жим"`

### Requirement: Кнопка «Сохранить» и блок ошибки в bottomBar

`ExerciseCreateSaveBottomBar(state: ExerciseCreateScreenState)` SHALL
быть `Surface` (или эквивалент) с модификаторами `.fillMaxWidth().
navigationBarsPadding().imePadding().padding(horizontal = 16.dp,
vertical = 8.dp)`, содержащим `Button(onClick = state.onSaveClick,
enabled = state.uiState.canSave, modifier = Modifier.fillMaxWidth())`
с текстом `R.string.entry_save` («Сохранить» / "Save"). Должна быть
единственной кнопкой в `bottomBar`. Под кнопкой SHALL располагаться
блок, который при `state.uiState.isSaveError == true` рендерит
`Text(stringResource(R.string.exercise_save_error))` в стиле
`MaterialTheme.colorScheme.error` + `typography.bodySmall` (паттерн —
`EntryCreateScreen.SaveBottomBar` — рендер `Text` под кнопкой при
`isSaveError == true`). Когда `isSaveError == false`, блок не рендерит
текст ошибки; допустимо оставлять блок-обёртку пустым — рендер-флаг
решает видимость.

#### Scenario: canSave = false — кнопка disabled

- **WHEN** `name` пустое или `category == null`
- **THEN** `Button(enabled = false)`
- **AND** `onClick` не срабатывает при тапе

#### Scenario: canSave = true — кнопка enabled

- **WHEN** `name` непустое (после `trim()`) AND `category` выбрана
- **THEN** `Button(enabled = true)`
- **AND** тап запускает `onSaveClick`

#### Scenario: Result.failure — пользователь видит ошибку

- **WHEN** `UiState.isSaveError == true` после неуспешного `save()`
- **THEN** под кнопкой «Сохранить» виден текст «Не удалось сохранить
  упражнение» в `colorScheme.error`

#### Scenario: Result.success — ошибка скрыта

- **WHEN** `UiState.isSaveError == false` (стартовое состояние или
  успешное сохранение)
- **THEN** под кнопкой «Сохранить» НЕТ текста ошибки

### Requirement: ViewModel и UiState формы

`ExerciseCreateViewModel` SHALL собирать `ExerciseCreateUiState` как
`StateFlow<ExerciseCreateUiState>` через `stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000), ExerciseCreateUiState())`. UiState
SHALL быть `data class` только из данных (паттерн `EntryCreateUiState` —
без лямбд/колбэков) с полями
data class: `name: String? = null`, `category: ExerciseCategory? = null`,
`measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS`,
`comment: String? = null`, `isSaveError: Boolean = false`.
`canSave` SHALL быть computed-свойством `val canSave: Boolean get() =
!name.isNullOrBlank() && category != null` в теле класса (не в primary
constructor data class) — паттерн `EntryCreateUiState`.
`hasUnsavedChanges` — ещё одно computed-свойство (полная формула и
назначение — **канон в требовании «BackHandler + hasUnsavedChanges —
подтверждение закрытия»** ниже; это требование лишь объявляет его как
поле UiState рядом с `canSave`, чтобы не дублировать формулу).
Колбэки (`onNameChange: (String) -> Unit` /
`onCategoryChange: (ExerciseCategory) -> Unit` /
`onMeasuredByChange: (ExerciseMeasuredBy) -> Unit` /
`onCommentChange: (String) -> Unit`) SHALL быть методами
`ExerciseCreateViewModel`, оборачивающими `mutableUiState.update { ... }`.
Каждый колбэк SHALL сбрасывать `isSaveError = false` (паттерн
`EntryCreateViewModel.onXChange` — все его колбэки включают
`isSaveError = false` в `copy(...)`), чтобы ошибка сохранения исчезала
при первом же изменении ввода.
Сигнатура `onCategoryChange` НЕ принимает `null` —
`ExposedDropdownMenuBox` физически не позволяет «снять выбор»
(аналог `EntryCreateViewModel.onExerciseChange(exercise: Exercise)` —
функция `onExerciseChange` в `EntryCreateViewModel.kt`); `null`
допустим только в
`UiState.category: ExerciseCategory?` для стартового состояния.
`save(onSaved: () -> Unit)` SHALL быть методом ViewModel и принимать
колбэк закрытия экрана параметром (как `EntryCreateViewModel.save`).

#### Scenario: Пустая форма — canSave = false

- **WHEN** `ExerciseCreateViewModel` создан, форма не заполнена
- **THEN** `uiState.value.canSave == false`

#### Scenario: Заполнены name и category — canSave = true

- **WHEN** `UiState.name == "Жим"` AND `UiState.category == PUSH`
- **THEN** `uiState.value.canSave == true`

#### Scenario: measuredBy переключается без влияния на canSave

- **WHEN** форма содержит `name` и `category`, `measuredBy` переключён
  на TIME
- **THEN** `uiState.value.canSave` остаётся `true`

#### Scenario: Ввод после неуспешного сохранения скрывает ошибку

- **WHEN** `UiState.isSaveError == true` после неуспешного `save()` AND
  пользователь изменяет любое поле ввода (`onNameChange` /
  `onCategoryChange` / `onMeasuredByChange` / `onCommentChange`)
- **THEN** `UiState.isSaveError == false` (сброс при каждом колбэке —
  паттерн `EntryCreateViewModel.onXChange`)

### Requirement: Сохранение — вызов `repository.addExercise` и popBackStack

`ExerciseCreateViewModel.save(onSaved: () -> Unit)` SHALL начинаться
с `val state = uiState.value` И `if (!state.canSave) return` — верхний
guard, симметрично `EntryCreateViewModel.save` (функция `save` в
`EntryCreateViewModel.kt` — `val state = uiState.value; if (!state.canSave)
return`). Затем SHALL локально извлечь `val category: ExerciseCategory =
state.category ?: return` (защитный early-return; инвариант `canSave`
уже гарантирует `category != null`, но избегаем `!!` — AGENTS.md
запрещает конструкцию), локально извлечь `val name: String = state.name
?: return` (симметричный guard; `canSave` гарантирует
`!name.isNullOrBlank()`, но явный ранний возврат вместо `name!!`),
сформировать `Exercise(id = 0L, name = name.trim(), category = category,
isCustom = true, comment = state.comment?.trim()?.takeIf {
it.isNotEmpty() }, createdAt = System.currentTimeMillis(), measuredBy =
state.measuredBy)` и вызвать `repository.addExercise(exercise)`.
Нормализация `trim()` для `name` и `comment` — единая точка, в момент
сохранения. При `Result.success` SHALL вызваться `onSaved()` (закрытие
экрана). При `Result.failure` SHALL устанавливаться
`UiState.isSaveError = true`, экран НЕ закрывается.

#### Scenario: Успешное сохранение — экран закрывается

- **WHEN** пользователь нажимает «Сохранить» с заполненной формой AND
  `repository.addExercise` возвращает `Result.success`
- **THEN** `onSaved()` вызывается ровно один раз
- **AND** в репозиторий передан `Exercise` с `isCustom = true`,
  `createdAt ≈ System.currentTimeMillis()` (в пределах теста ±5s),
  `name = name.trim()`, `category`, `measuredBy`, `comment` — как описано

#### Scenario: Неуспешное сохранение — флаг ошибки

- **WHEN** пользователь нажимает «Сохранить» AND `repository.addExercise`
  возвращает `Result.failure`
- **THEN** `UiState.isSaveError == true`
- **AND** `onSaved()` НЕ вызывается

#### Scenario: Тап при canSave = false — no-op

- **WHEN** пользователь нажимает «Сохранить» с пустой формой
- **THEN** `repository.addExercise` НЕ вызывается
- **AND** `onSaved()` НЕ вызывается

### Requirement: BackHandler + hasUnsavedChanges — подтверждение закрытия

**Канон для `attemptClose`/`showConfirmDialog`/`BackHandler` в этом
файле и для формулы `hasUnsavedChanges` (UiState)** — этот Requirement
единственный нормативный источник формулы `hasUnsavedChanges` и связки
back-логики. Остальные места в `spec.md`, `design.md`, `tasks.md`
ссылаются на это требование, а не дублируют формулу.
Формула (единственная нормативная копия):
`val hasUnsavedChanges: Boolean get() = !name.isNullOrBlank() ||
category != null || !comment.isNullOrBlank()` — любое нетривиальное
текстовое поле (`name`/`comment` с отсечением blank) или выбранная
категория делает форму изменённой; `measuredBy` в формулу не входит
(сценарий «hasUnsavedChanges вычисляется по name/category/comment»).

`ExerciseCreateScreenContent` SHALL использовать `BackHandler` и
диалог подтверждения при несохранённых изменениях — паттерн
`EntryCreateScreen.Content` (см. `EntryCreateScreen.kt` L107–115,
L148–156, L170–195). Конкретно: SHALL быть локальная `var
showConfirmDialog by remember { mutableStateOf(false) }`, локальная
`val attemptClose = { if (state.uiState.hasUnsavedChanges)
showConfirmDialog = true else state.onBackClick() }`, `BackHandler(
enabled = state.uiState.hasUnsavedChanges) { attemptClose() }`. Стрелка
`TopAppBar.navigationIcon` SHALL вызывать `attemptClose` (а не
`state.onBackClick` напрямую) — единая точка входа для back из обоих
источников (системный back и стрелка TopAppBar). При
`showConfirmDialog == true` SHALL рендериться `AlertDialog` с
существующими строками `R.string.entry_confirm_close_dialog_title`
(«Закрыть?» / "Close?"), `R.string.entry_confirm_close_dialog_message`
(«Несохранённые изменения будут потеряны» / "Unsaved changes will be
lost"), `R.string.entry_confirm_close` («Закрыть» / "Close"),
`R.string.cancel` («Отмена» / "Cancel") — диалог SHALL жить в новом
internal `@Composable fun ExerciseCreateCloseConfirmDialog(onConfirm:
() -> Unit, onDismiss: () -> Unit)` в файле `ExerciseCreateScreen.kt`
(паттерн-близнец `EntryCloseConfirmDialog` из `EntryCreateScreen.kt`,
L170–195). Кнопка подтверждения SHALL использовать
`ButtonDefaults.textButtonColors(contentColor = colorScheme.error)`
(как в `EntryCreateScreen.EntryCloseConfirmDialog.confirmButton`).
Строки `entry_confirm_close_*` переиспользуются без новых ключей —
текст универсален для любой формы (прецедент `R.string.back` для
`navigationIcon.contentDescription` и `R.string.entry_save` для
кнопки «Сохранить», см. tasks.md 3.1).

#### Scenario: Системный back при hasUnsavedChanges — показ диалога

- **WHEN** `state.uiState.hasUnsavedChanges == true` AND пользователь
  нажимает системный back
- **THEN** `BackHandler` срабатывает AND `showConfirmDialog` становится
  `true`
- **AND** на экране виден `AlertDialog` с текстом «Закрыть?» и кнопками
  «Закрыть» (`colorScheme.error`) и «Отмена»

#### Scenario: Системный back при пустой форме — закрытие без диалога

- **WHEN** `state.uiState.hasUnsavedChanges == false` (форма не
  тронута с момента открытия) AND пользователь нажимает системный back
- **THEN** `BackHandler` НЕ активен (`enabled = false`)
- **AND** вызывается `state.onBackClick()` без показа диалога

#### Scenario: Стрелка TopAppBar при hasUnsavedChanges — показ диалога

- **WHEN** `state.uiState.hasUnsavedChanges == true` AND пользователь
  тапает стрелку `TopAppBar.navigationIcon`
- **THEN** `showConfirmDialog` становится `true`
- **AND** на экране виден `AlertDialog` (тот же, что при системном back)

#### Scenario: Подтверждение закрытия в диалоге — выход из экрана

- **WHEN** `showConfirmDialog == true` AND пользователь тапает кнопку
  «Закрыть» в диалоге
- **THEN** `showConfirmDialog` становится `false`
- **AND** вызывается `state.onBackClick()` (экран закрывается)

#### Scenario: Отмена в диалоге — экран остаётся открытым

- **WHEN** `showConfirmDialog == true` AND пользователь тапает кнопку
  «Отмена» в диалоге (или тапает мимо диалога)
- **THEN** `showConfirmDialog` становится `false`
- **AND** `state.onBackClick()` НЕ вызывается (экран остаётся открытым,
  введённые данные сохранены в state)

#### Scenario: hasUnsavedChanges вычисляется по name/category/comment

- **WHEN** `UiState` содержит `name == null`, `category == null`,
  `comment == null` (только что открытая форма)
- **THEN** `UiState.hasUnsavedChanges == false`
- **WHEN** пользователь вводит «Жим» (`name != null`, остальные поля
  пустые) — `hasUnsavedChanges == true`
- **AND** если пользователь стирает текст «Жим» — `hasUnsavedChanges`
  возвращается к `false` (после очистки `name` либо `null`, либо пустая
  строка `""` — оба варианта отсекаются `isNullOrBlank()` в computed-
  свойстве — формула `canSave` в tasks.md 1.2 и кейсы в tasks.md 1.1)
- **AND** выбор только `category` (например `PULL`) при пустых
  `name`/`comment` — `hasUnsavedChanges == true` (формула
  `canSave` в tasks.md 1.2 и кейсы в tasks.md 1.1)
- **AND** ввод только `comment` (например «узкий хват») при пустых
  `name`/`category` — `hasUnsavedChanges == true` (формула
  `canSave` в tasks.md 1.2 и кейсы в tasks.md 1.1)
- **AND** изменение только `measuredBy` (REPS ↔ TIME) при пустых
  `name`/`category`/`comment` НЕ делает форму «изменённой»

### Requirement: ExerciseCreateScreen — обёртка + Content + ScreenState

`ExerciseCreateScreen` SHALL быть тонкой обёрткой (аналог `EntryCreateScreen`):
собирает `viewModel.uiState` через `collectAsState()`, делегирует в
`internal fun ExerciseCreateScreenContent(state: ExerciseCreateScreenState,
modifier: Modifier = Modifier)`. Единственный канал колбэка «назад» —
параметр `onBackClick: () -> Unit` обёртки; он пробрасывается в поле
`ExerciseCreateScreenState.onBackClick` (а не читается напрямую в
`Scaffold.navigationIcon`). Это сохраняет один источник истины —
`state.onBackClick` — который Content использует и для `navigationIcon`,
и при необходимости для `BackHandler`/диалога подтверждения. Data class
`ExerciseCreateScreenState` (для UI-тестов и Preview) SHALL содержать
`uiState: ExerciseCreateUiState`, `onNameChange: (String) -> Unit`,
`onCategoryChange: (ExerciseCategory) -> Unit` (не nullable —
см. требование «ViewModel и UiState формы»), `onMeasuredByChange:
(ExerciseMeasuredBy) -> Unit`, `onCommentChange: (String) -> Unit`,
`onSaveClick: () -> Unit`, `onBackClick: () -> Unit`.

#### Scenario: Content доступен без ViewModel

- **WHEN** пишется UI-тест или превью
- **THEN** `ExerciseCreateScreenContent` composed напрямую с
  `ExerciseCreateScreenState(...)` — без `ExerciseCreateViewModel`

### Requirement: Превью в обеих темах

`ExerciseCreateScreenPreview.kt` SHALL содержать минимум 8 `@Preview`
функций (4 состояния × 2 темы) для `ExerciseCreateScreenContent`,
обёрнутых в `JetpackMyWorkoutsTheme`, `showBackground = true`,
`locale = "ru"`. Четыре состояния рядом (в `Column`): пустая форма
(Save disabled), форма с name + category (Save enabled, без комментария),
форма с заполненным комментарием, форма с `measuredBy = TIME`. Те же
четыре состояния SHALL повторяться в `UI_MODE_NIGHT_YES` (через
`Configuration` или параметр `uiMode = ...` `@Preview`-аннотации).

#### Scenario: Превью пустой формы в светлой теме

- **WHEN** `ExerciseCreateScreenPreview` отрисован в `@Preview(
  uiMode = UI_MODE_NIGHT_NO, locale = "ru")`
- **THEN** видна форма с пустым «Название», «Выберите категорию»,
  активным «Повторы», пустым «Комментарий» и disabled «Сохранить»