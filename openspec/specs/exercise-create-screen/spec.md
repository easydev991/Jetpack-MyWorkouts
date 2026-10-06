# exercise-create-screen Specification

## Purpose

Экран создания нового упражнения — форма с обязательным названием, выбором
категории, сегментированной кнопкой типа измерения и необязательным
комментарием; сохранение добавляет `Exercise` с `isCustom = true` в
каталог.

## Requirements

### Requirement: ExerciseCreateScreenContent — Scaffold с TopAppBar и bottomBar

`ExerciseCreateScreenContent` (а не обёртка `ExerciseCreateScreen`)
SHALL содержать `Scaffold` с `topBar = { TopAppBar(title = { Text(
stringResource(titleResId)) }, navigationIcon = {
IconButton(onClick = attemptClose) { Icon(Icons.AutoMirrored.Filled.
ArrowBack, contentDescription = stringResource(R.string.back)) } }) }`
и `bottomBar = { ExerciseCreateSaveBottomBar(state = state) }`. Заголовок
SHALL зависеть от режима: `R.string.exercise_create_title` («Новое
упражнение» / "New exercise") в create-режиме, `R.string.exercise_edit_title`
(«Изменить упражнение» / "Edit exercise") в edit-режиме
(`state.uiState.isEditMode == true`).
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
паттерн `EntryCreateScreen.SaveBottomBar(state = state)`. Строка `back` — существующий
ресурс («Назад» / "Back"), переиспользуется без нового ключа.

#### Scenario: Открытие экрана показывает пустую форму

- **WHEN** пользователь открывает `ExerciseCreateScreen` в create-режиме
- **THEN** в `TopAppBar` отображается «Новое упражнение» (ru) / "New
  exercise" (en)
- **AND** поле «Название» пустое
- **AND** в выпадающем меню «Категория» не выбрано значение
- **AND** сегмент «Повторы» активен (по умолчанию)
- **AND** поле «Комментарий» пустое
- **AND** кнопка «Сохранить» в bottomBar disabled

#### Scenario: Edit-режим показывает заголовок «Изменить упражнение» (ru)

- **WHEN** `ExerciseCreateScreen` открыт с `exerciseId` существующего
  упражнения
- **THEN** в `TopAppBar` отображается «Изменить упражнение»

#### Scenario: Edit-режим показывает заголовок «Изменить упражнение» (en)

- **WHEN** `ExerciseCreateScreen` открыт с `exerciseId` существующего
  упражнения, локаль en
- **THEN** в `TopAppBar` отображается "Edit exercise"

### Requirement: Поле «Название» — однострочное, обязательное

`ExerciseCreateScreen` SHALL содержать `OutlinedTextField` со строкой
`R.string.exercise_field_name` в качестве `label`, `singleLine = true`, и
`onValueChange`/`value`, привязанные к `state.name`. `state.name`
хранит сырой ввод пользователя (`String?`); нормализация
`String.trim()` выполняется в момент сохранения (см. требование
«Сохранение — create/addExercise и edit/updateExercise с popBackStack») — тот же
момент, что и для других текстовых полей формы. Пустое или blank-имя
блокирует сохранение.

#### Scenario: Ввод имени активирует валидацию

- **WHEN** пользователь вводит «Отжимания» в поле «Название»
- **THEN** в `UiState.name` сохраняется «Отжимания»
- **AND** кнопка «Сохранить» всё ещё disabled — `category` ещё не выбрана

#### Scenario: Пробелы не считаются валидным именем

- **WHEN** пользователь вводит только пробелы в поле «Название»
- **THEN** `canSave` остаётся `false`

### Requirement: Секция выбора иконки

`ExerciseCreateScreenContent` SHALL отображать секцию выбора иконки под
полем «Название» с заголовком `R.string.exercise_field_icon` («Иконка» /
"Icon") над сеткой ячеек. Секция SHALL быть видна всегда — экран
создания/редактирования доступен только для пользовательских упражнений,
отдельное условие по типу упражнения не вводится. Сетка SHALL содержать
первую ячейку «Без иконки» (выбирает отсутствие иконки;
contentDescription — `R.string.exercise_icon_picker_no_icon` («Без
иконки» / "No icon")) и далее все иконки каталога приложения со
стабильными номерами 1..19; ячейки иконок каталога SHALL иметь
`contentDescription = null` — именованные лейблы для иконок не
вводятся (как у отображения иконок в списках); вариант отрисовки иконки
SHALL выбираться по теме системы (тёмная/светлая). Выбор ячейки SHALL записываться в
`UiState.iconNumber` (`null` — «Без иконки», иначе — номер иконки
каталога). Выбранная ячейка SHALL выделяться обводкой цветом `primary`,
невыбранная — тонкой обводкой `outlineVariant`; обводка SHALL НЕ сдвигать
layout ячеек.

#### Scenario: Выбор иконки при создании

- **WHEN** пользователь открывает форму в create-режиме AND тапает ячейку
  с иконкой номер 7
- **THEN** `UiState.iconNumber == 7`
- **AND** ячейка 7 отображается выбранной (обводка `primary`), ячейка
  «Без иконки» — не выбранной

#### Scenario: Смена иконки при редактировании

- **WHEN** форма открыта в edit-режиме упражнения с `iconNumber == 5`
  AND пользователь тапает ячейку с иконкой номер 9
- **THEN** `UiState.iconNumber == 9`
- **AND** ячейка 9 отображается выбранной, ячейка 5 — не выбранной

#### Scenario: «Без иконки» — значение по умолчанию

- **WHEN** пользователь открывает форму в create-режиме
- **THEN** `UiState.iconNumber == null`
- **AND** ячейка «Без иконки» отображается выбранной

#### Scenario: Повторный выбор «Без иконки» снимает выбранную иконку

- **WHEN** пользователь выбрал иконку номер 7 AND тапает ячейку
  «Без иконки»
- **THEN** `UiState.iconNumber == null`
- **AND** ячейка «Без иконки» отображается выбранной, ячейка 7 — нет

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

`ExerciseCreateScreen` SHALL содержать `ExposedDropdownMenuBox`
(internal-компонент `CategoryDropdownField`) для выбора категории.
В `label` SHALL быть `R.string.exercise_field_category`. Placeholder
(non-selected) SHALL показывать `R.string.exercise_field_category_select`
(«Выберите категорию» / "Select category"). Пока категория не выбрана
сохранение заблокировано. Источник категорий — таблица БД (не enum):
`CategoryDropdownField` SHALL принимать список доступных категорий и
выбранное имя; в create-режиме список SHALL содержать активные категории
(`categories()` репозитория, архивные скрыты); в edit-режиме к текущей
категории упражнения SHALL добавляться его собственная архивная категория
(если она архивна) — прочие архивные остаются скрытыми. Локализованный
лейбл SHALL вычисляться по имени категории через file-level хелпер
`standardCategoryLabelResId(name: String): Int?` (`ui/util`, design
Decision 2): для пяти стандартных имен-ключей
(`PUSH`/`PULL`/`LEGS`/`CORE`/`CARDIO`) — существующие ресурсы
`exercise_category_*`, для кастомных (хелпер вернул `null`) — `name`
как есть. В `DropdownMenuItem` отображается
лейбл; в `UiState`/БД категория хранится как имя (`String`).
Список категорий SHALL обновляться реактивно (категория, созданная
в другой сессии, появляется без пересоздания экрана).

#### Scenario: Выбор категории разрешает сохранение

- **WHEN** `name` непустое (после `trim()`) AND пользователь выбрал
  категорию «Плечи» в выпадающем меню
- **THEN** `UiState.categoryName == "Плечи"`
- **AND** `canSave == true`

#### Scenario: Локализованный лейбл категории в выпадающем меню

- **WHEN** пользователь открывает выпадающее меню `CategoryDropdownField`
  в русской локали
- **THEN** для стандартных категорий видны локализованные лейблы «Толкающие»
  (`PUSH`), «Тянущие» (`PULL`), «Ноги» (`LEGS`), «Кор» (`CORE`),
  «Кардио» (`CARDIO`), для кастомных — их имена как есть

#### Scenario: Локализованный лейбл категории в английской локали

- **WHEN** пользователь открывает выпадающее меню `CategoryDropdownField`
  в английской локале
- **THEN** для стандартных категорий видны "Push", "Pull", "Legs", "Core",
  "Cardio"

#### Scenario: Кастомная категория в выпадающем меню

- **WHEN** в БД есть кастомная активная категория «Спина» AND пользователь
  открывает меню в create-режиме
- **THEN** в меню видна строка «Спина» AND выбор сохраняет
  `UiState.categoryName == "Спина"`

#### Scenario: Архивные категории скрыты при создании

- **WHEN** категория «Плечи» заархивирована AND открыт create-режим
- **THEN** «Плечи» отсутствует в выпадающем меню

#### Scenario: Текущая архивная категория видна при редактировании

- **WHEN** редактируется упражнение категории «Плечи» AND «Плечи»
  заархивирована AND другая категория «Кор» тоже заархивирована
- **THEN** в меню видны активные категории AND «Плечи» (текущая)
- **AND** «Кор» (прочая архивная) скрыта

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
без лямбд/колбэков) с полями: `name: String? = null`, `categoryName:
String? = null`, `availableCategories: List<Category> = emptyList()`,
`measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS`,
`comment: String? = null`, `iconNumber: Int? = null`,
`isSaveError: Boolean = false`,
`isEditMode: Boolean = false`, `hasChanges: Boolean = false`,
`isExerciseMissing: Boolean = false`.
`iconNumber == null` означает «Без иконки» — как стартовое состояние
create-формы, так и явный выбор ячейки «Без иконки».
`availableCategories` SHALL наполняться реактивной подпиской на активные
категории репозитория; в edit-режиме при архивной текущей категории
упражнения список для дропдауна SHALL включать её (в UiState допускается
computed-набор `dropdownCategories` = активные + текущая, если та архивна).
`canSave` SHALL быть computed-свойством `val canSave: Boolean get() =
!name.isNullOrBlank() && categoryName != null && (!isEditMode || hasChanges)
&& !isExerciseMissing`
в теле класса (не в primary constructor data class) — паттерн
`EntryCreateUiState`: в create-режиме достаточно валидной формы, в edit-режиме дополнительно требуются изменения относительно оригинала
(формула `hasChanges` — требование «Edit-режим: загрузка, предзаполнение
и hasChanges» ниже). Финальный терм `&& !isExerciseMissing` —
защитный guard: при `isExerciseMissing == true` (упражнение удалено в
другой сессии) `canSave` SHALL быть `false` независимо от полей формы.
`hasUnsavedChanges` — ещё одно computed-свойство в теле класса,
полная формула и назначение — **канон в требовании «BackHandler +
hasUnsavedChanges — подтверждение закрытия» главной спеки
exercise-create-screen**; это требование лишь
объявляет его как computed-свойство UiState рядом с `canSave`, чтобы не
дублировать формулу (в формуле термин `category != null` заменяется на
`categoryName != null`).
Колбэки (`onNameChange: (String) -> Unit` /
`onCategoryChange: (String) -> Unit` /
`onMeasuredByChange: (ExerciseMeasuredBy) -> Unit` /
`onCommentChange: (String) -> Unit` /
`onIconNumberChange: (Int?) -> Unit`) SHALL быть методами
`ExerciseCreateViewModel`, оборачивающими `mutableUiState.update { ... }`.
Каждый колбэк SHALL сбрасывать `isSaveError = false` (паттерн
`EntryCreateViewModel.onXChange`), чтобы ошибка сохранения исчезала
при первом же изменении ввода. `onIconNumberChange` принимает `Int?` —
в отличие от `onCategoryChange`, «снять выбор» здесь валидное действие
(повторный выбор «Без иконки»).
Сигнатура `onCategoryChange` НЕ принимает `null` —
`ExposedDropdownMenuBox` физически не позволяет «снять выбор»
(аналог `EntryCreateViewModel.onExerciseChange(exercise: Exercise)`);
`null` допустим только в `UiState.categoryName: String?` для стартового
состояния.
`save(onSaved: () -> Unit)` SHALL быть методом ViewModel и принимать
колбэк закрытия экрана параметром (как `EntryCreateViewModel.save`).

#### Scenario: Пустая форма — canSave = false

- **WHEN** `ExerciseCreateViewModel` создан в create-режиме, форма не
  заполнена
- **THEN** `uiState.value.canSave == false`

#### Scenario: Заполнены name и category — canSave = true

- **WHEN** `UiState.name == "Жим"` AND `UiState.categoryName == "PUSH"` AND
  `isEditMode == false`
- **THEN** `uiState.value.canSave == true`

#### Scenario: Edit без изменений — canSave = false

- **WHEN** `isEditMode == true` AND предзаполненная форма не менялась
  (`hasChanges == false`) AND форма валидна
- **THEN** `uiState.value.canSave == false`

#### Scenario: measuredBy переключается без влияния на canSave

- **WHEN** форма (create) содержит `name` и категорию, `measuredBy`
  переключён на TIME
- **THEN** `uiState.value.canSave` остаётся `true`

#### Scenario: Ввод после неуспешного сохранения скрывает ошибку

- **WHEN** `UiState.isSaveError == true` после неуспешного `save()` AND
  пользователь изменяет любое поле ввода (`onNameChange` /
  `onCategoryChange` / `onMeasuredByChange` / `onCommentChange` /
  `onIconNumberChange`)
- **THEN** `UiState.isSaveError == false` (сброс при каждом колбэке —
  паттерн `EntryCreateViewModel.onXChange`)

#### Scenario: Список категорий реактивен

- **WHEN** при открытой форме в другой сессии создана категория «Гантельная»
- **THEN** `availableCategories` обновляется без пересоздания ViewModel
  AND «Гантельная» появляется в выпадающем меню

### Requirement: Сохранение — create/addExercise и edit/updateExercise с popBackStack

`ExerciseCreateViewModel.save(onSaved: () -> Unit)` SHALL начинаться
с `val state = uiState.value` И `if (!state.canSave) return` — верхний
guard, симметрично `EntryCreateViewModel.save`. Сборка `Exercise` для
сохранения и для сравнения `hasChanges` SHALL идти через единый
внутренний хелпер-сборщик черновика (`draftOf(state, original)` в
`ExerciseCreateViewModel.kt` — паттерн «единая точка нормализации»,
design Decision 2): инварианты сборки — `name = state.name?.trim()` с
отсечением blank; `comment = state.comment?.trim()?.takeIf {
it.isNotEmpty() }`; `iconNumber = state.iconNumber`; ветвление по режиму:

- **create** (`isEditMode == false`): `id = 0L`, `isCustom = true`,
  `createdAt = System.currentTimeMillis()`, остальные поля из формы →
  `repository.addExercise(exercise)`;
- **edit** (`isEditMode == true`): `id`/`createdAt`/`isCustom` берутся
  из загруженного оригинала, остальные поля из формы →
  `repository.updateExercise(exercise)`.

Конструкция `Exercise` НЕ собирается инлайн в `save()` — любое
расхождение между сборкой для diff-а `hasChanges` и сборкой для записи
в репозиторий нарушает «единую точку нормализации». Запрещён оператор
`!!` (AGENTS.md): `null`-извлечения обрабатываются early-return внутри
`draftOf`. При `Result.success` SHALL вызваться `onSaved()` (закрытие
экрана `popBackStack()` — в обоих режимах). При `Result.failure` SHALL
устанавливаться `UiState.isSaveError = true`, экран НЕ закрывается.

#### Scenario: Успешное сохранение — экран закрывается

- **WHEN** пользователь нажимает «Сохранить» с заполненной формой в
  create-режиме AND `repository.addExercise` возвращает `Result.success`
- **THEN** `onSaved()` вызывается ровно один раз
- **AND** в репозиторий передан `Exercise` с `isCustom = true`,
  `createdAt ≈ System.currentTimeMillis()` (в пределах теста ±5s),
  `name = name.trim()`, `category`, `measuredBy`, `comment`, `iconNumber`
  — как описано

#### Scenario: Успешное редактирование — updateExercise вместо addExercise

- **WHEN** пользователь нажимает «Сохранить» в edit-режиме AND
  `repository.updateExercise` возвращает `Result.success`
- **THEN** `repository.addExercise` НЕ вызывается
- **AND** `onSaved()` вызывается ровно один раз (экран закрывается)
- **AND** переданный `Exercise` сохраняет `id` и `createdAt` оригинала,
  а `name`/`category`/`measuredBy`/`comment`/`iconNumber` взяты из формы
  (с trim для текстовых)

#### Scenario: Неуспешное сохранение — флаг ошибки

- **WHEN** пользователь нажимает «Сохранить» AND репозиторий возвращает
  `Result.failure`
- **THEN** `UiState.isSaveError == true`
- **AND** `onSaved()` НЕ вызывается

#### Scenario: Тап при canSave = false — no-op

- **WHEN** пользователь нажимает «Сохранить» при `canSave == false`
  (невалидная форма или edit без изменений)
- **THEN** репозиторий НЕ вызывается
- **AND** `onSaved()` НЕ вызывается

#### Scenario: Иконка сохраняется при создании

- **WHEN** пользователь заполняет create-форму, выбрав иконку номер 7,
  AND нажимает «Сохранить»
- **THEN** в репозиторий передан `Exercise` с `iconNumber == 7`

#### Scenario: Иконка сохраняется при редактировании

- **WHEN** в edit-режиме упражнения с `iconNumber == 5` пользователь
  выбрал иконку номер 9 AND нажимает «Сохранить»
- **THEN** в репозиторий передан `Exercise` с `id` оригинала и
  `iconNumber == 9`

### Requirement: BackHandler + hasUnsavedChanges — подтверждение закрытия

**Канон для `attemptClose`/`showConfirmDialog`/`BackHandler` в этом
файле и для формулы `hasUnsavedChanges` (UiState)** — этот Requirement
единственный нормативный источник формулы `hasUnsavedChanges` и связки
back-логики. Остальные места в `spec.md`, `design.md`, `tasks.md`
ссылаются на это требование, а не дублируют формулу.
Формула (единственная нормативная копия), с ветвлением по режиму:

- **create** (`isEditMode == false`):
  `val hasUnsavedChanges: Boolean get() = !name.isNullOrBlank() ||
  category != null || !comment.isNullOrBlank() || iconNumber != null` —
  любое нетривиальное текстовое поле (`name`/`comment` с отсечением
  blank), выбранная категория или выбранная иконка (отличная от «Без
  иконки») делает форму изменённой; `measuredBy` в формулу не входит
  (сценарий «hasUnsavedChanges вычисляется по name/category/comment»);
- **edit** (`isEditMode == true`):
  `val hasUnsavedChanges: Boolean get() = hasChanges` — форма считается
  изменённой, только если текущий черновик отличается от загруженного
  оригинала (предзаполненные поля сами по себе не блокируют back).

`ExerciseCreateScreenContent` SHALL использовать `BackHandler` и
диалог подтверждения при несохранённых изменениях — паттерн
`EntryCreateScreen.Content`. Конкретно: SHALL быть локальная `var
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
`R.string.cancel` («Отмена» / "Cancel") — диалог SHALL жить в
internal `@Composable fun ExerciseCreateCloseConfirmDialog(onConfirm:
() -> Unit, onDismiss: () -> Unit)` в файле `ExerciseCreateScreen.kt`
(паттерн-близнец `EntryCloseConfirmDialog` из `EntryCreateScreen.kt`).
Кнопка подтверждения SHALL использовать
`ButtonDefaults.textButtonColors(contentColor = colorScheme.error)`
(как в `EntryCreateScreen.EntryCloseConfirmDialog.confirmButton`).
Строки `entry_confirm_close_*` переиспользуются без новых ключей —
текст универсален для любой формы.

#### Scenario: Системный back при hasUnsavedChanges — показ диалога

- **WHEN** `state.uiState.hasUnsavedChanges == true` AND пользователь
  нажимает системный back
- **THEN** `BackHandler` срабатывает AND `showConfirmDialog` становится
  `true`
- **AND** на экране виден `AlertDialog` с текстом «Закрыть?» и кнопками
  «Закрыть» (`colorScheme.error`) и «Отмена»

#### Scenario: Системный back при пустой форме — закрытие без диалога

- **WHEN** `state.uiState.hasUnsavedChanges == false` (create-режим,
  форма не тронута) AND пользователь нажимает системный back
- **THEN** `BackHandler` НЕ активен (`enabled = false`)
- **AND** вызывается `state.onBackClick()` без показа диалога

#### Scenario: Edit-режим без изменений — back закрывает без диалога

- **WHEN** `isEditMode == true` AND пользователь не менял предзаполненные
  поля (`hasChanges == false`) AND пользователь нажимает системный back
- **THEN** `BackHandler` НЕ активен
- **AND** вызывается `state.onBackClick()` без показа диалога

#### Scenario: Упражнение не найдено — attemptClose закрывает экран без диалога

- **WHEN** `isEditMode == true` AND `isExerciseMissing == true`
  (`getExercise` вернул `null` или `failure`, оригинал отсутствует,
  `hasChanges == false` по построению) AND пользователь нажимает
  системный back (или стрелку `TopAppBar.navigationIcon`)
- **THEN** оба источника сходятся в `attemptClose`, который видит
  `hasUnsavedChanges == false` и сразу вызывает `state.onBackClick()`
  без показа диалога: для системного back `BackHandler` НЕ активен
  (`enabled = false`), для стрелки `TopAppBar` `attemptClose` вызывается
  напрямую — единая точка входа, форма не редактируется, терять нечего

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

- **WHEN** `UiState` (create-режим) содержит `name == null`, `category == null`,
  `comment == null` (только что открытая create-форма)
- **THEN** `UiState.hasUnsavedChanges == false`
- **WHEN** пользователь вводит «Жим» (`name != null`, остальные поля
  пустые) — `hasUnsavedChanges == true`
- **AND** если пользователь стирает текст «Жим» — `hasUnsavedChanges`
  возвращается к `false` (после очистки `name` либо `null`, либо пустая
  строка `""` — оба варианта отсекаются `isNullOrBlank()` в computed-
  свойстве)
- **AND** выбор только `category` (например `PULL`) при пустых
  `name`/`comment` — `hasUnsavedChanges == true`
- **AND** ввод только `comment` (например «узкий хват») при пустых
  `name`/`category` — `hasUnsavedChanges == true`
- **AND** изменение только `measuredBy` (REPS ↔ TIME) при пустых
  `name`/`category`/`comment` НЕ делает форму «изменённой»
- **AND** формула дополнена термом `|| iconNumber != null` — случай
  «выбрана только иконка при пустых `name`/`category`/`comment`»
  покрыт отдельным сценарием «Изменение иконки учитывается в
  hasUnsavedChanges и hasChanges» ниже

#### Scenario: Изменение иконки учитывается в hasUnsavedChanges и hasChanges

- **WHEN** в create-режиме пользователь выбрал иконку (например номер 3),
  остальные поля формы пустые (`name`/`category`/`comment == null`)
- **THEN** `UiState.hasUnsavedChanges == true` — выбранная иконка
  теряется при отмене; закрытие формы по back или стрелке `TopAppBar`
  показывает диалог подтверждения
- **AND** повторный выбор «Без иконки» (`iconNumber == null`) возвращает
  `hasUnsavedChanges` к `false`
- **AND** сохранение в create-режиме разрешено без иконки:
  `iconNumber == null` — нормальное состояние формы, `canSave` зависит
  только от `name` и `category` (иконка — необязательное поле модели
  `Exercise.iconNumber: Int?`)
- **WHEN** в edit-режиме упражнения с `iconNumber == 5` пользователь
  выбрал иконку номер 9 (остальные поля формы не менялись)
- **THEN** `hasChanges == true` AND `canSave == true` — кнопка
  «Сохранить» становится активной (изменение только иконки достаточно
  для активации сохранения)
- **AND** возврат к исходной иконке 5 при неизменных остальных полях
  возвращает `hasChanges == false` и `canSave == false`

### Requirement: Edit-режим: загрузка, предзаполнение и hasChanges

`ExerciseCreateViewModel` SHALL принимать `exerciseId: Long =
Screen.ExerciseCreate.NO_EXERCISE_ID` (константа определена в capability
`non-tab-screen-navigation`). `isEditMode` SHALL быть `true` тогда и
только тогда, когда `exerciseId != NO_EXERCISE_ID`. В edit-режиме
ViewModel SHALL загрузить упражнение через
`repository.getExercise(exerciseId)`:

- при `Result.success(null)` или `Result.failure` (упражнение удалено в
  другой сессии либо ошибка чтения) SHALL устанавливаться флаг
  `isExerciseMissing = true` — экран показывает подсказку
  `exercise_hint_exercise_missing` («Упражнение не найдено» / "Exercise
  not found") под формой, кнопка «Сохранить» недостижимо disabled
  (паттерн `isEntryMissing` — см. `EntryCreateUiState.kt:31–34, 57` и
  `EntryCreateViewModel.kt` (init-блок с `getEntry`), а также архивный
  `openspec/changes/archive/2026-09-06-entry-edit-mode/design.md` Decision 8);
- при `Result.success(exercise != null)` SHALL заполнять поля формы
  значениями упражнения (name, category, measuredBy, comment,
  iconNumber) и запоминать оригинал.

`hasChanges` SHALL быть `false` в create-режиме; в edit-режиме —
результатом сравнения черновика (текущие поля формы, нормализованные как
при сохранении) с оригиналом: `hasChanges == true` тогда и только тогда,
когда хотя бы одно из `name` (после trim), `category`, `measuredBy`,
`comment` (нормализованного как при сохранении), `iconNumber`
отличается от оригинала.

#### Scenario: Поля предзаполнены из БД

- **WHEN** `ExerciseCreateScreen` открыт с `exerciseId` упражнения
  «Подтягивания» (PULL, REPS, comment = «широкий хват», iconNumber = 13)
- **THEN** поле «Название» содержит «Подтягивания»
- **AND** выбрана категория «Тянущие» (PULL)
- **AND** активен сегмент «повторы»
- **AND** поле «Комментарий» содержит «широкий хват»
- **AND** в секции иконки выбрана ячейка с иконкой номер 13
- **AND** кнопка «Сохранить» disabled (изменений нет)

#### Scenario: Изменение name активирует hasChanges

- **WHEN** в edit-режиме предзаполненной формы пользователь изменил
  `name` на другое значение
- **THEN** `hasChanges == true` AND `canSave == true` (форма валидна)

#### Scenario: Возврат к исходным значениям сбрасывает hasChanges

- **WHEN** пользователь изменил `name`, затем вернул ровно исходное
  значение (и остальные поля не менял)
- **THEN** `hasChanges == false` AND `canSave == false`

#### Scenario: Упражнение не найдено — подсказка и disabled Save

- **WHEN** `repository.getExercise(exerciseId)` возвращает
  `Result.success(null)` (упражнение удалено в другой сессии)
- **THEN** `UiState.isExerciseMissing == true`
- **AND** под формой отображается подсказка «Упражнение не найдено»
- **AND** кнопка «Сохранить» disabled и тап по ней не вызывает
  репозиторий

#### Scenario: Упражнение не найдено с заполненной формой — canSave всё равно false

- **WHEN** `isEditMode == true` AND `isExerciseMissing == true` AND
  `getExercise` сначала вернул `Result.success(exercise)` (форма
  предзаполнена, `originalExercise` сохранён), затем `isExerciseMissing`
  стал `true` (defensive guard: в текущем коде `ExerciseCreateViewModel`
  не переинициализируется и `getExercise` возвращает один `Result`, но
  guard `&& !isExerciseMissing` защищает от любого будущего источника
  перехода state в `true` — например, race при открытии edit-режима
  в параллельной сессии того же приложения), AND
  пользователь редактирует форму так, что `hasChanges == true`
- **THEN** `UiState.canSave == false` благодаря guard-терму
  `&& !isExerciseMissing` в формуле `canSave`
- **AND** кнопка «Сохранить» остаётся disabled
- **AND** тап по «Сохранить» не вызывает репозиторий
- **AND** guard покрыт существующим unit-тестом «`canSave == false` при
  `isExerciseMissing == true` независимо от полей формы» в
  `ExerciseCreateViewModelTest`

### Requirement: Превью edit-режима и новых компонентов каталога

Превью SHALL покрывать новые состояния: в `ExerciseCreateScreenPreview.kt`
добавляются состояния edit-режима — предзаполненная форма без изменений
(`isEditMode = true`, `hasChanges = false`, Save disabled) и изменённая
форма (`isEditMode = true`, `hasChanges = true`, Save enabled) — каждое в
светлой и тёмной теме (обёртка `JetpackMyWorkoutsTheme`,
`showBackground = true`, `locale = "ru"`). Для каталога — превью
контекстного меню упражнений (открытое меню) и алерта подтверждения
удаления рядом в Column.

#### Scenario: Edit-превью в обеих темах

- **WHEN** edit-превью отрисовано с `uiMode = UI_MODE_NIGHT_NO` и
  `UI_MODE_NIGHT_YES`
- **THEN** обе темы показывают предзаполненную форму с заголовком
  «Изменить упражнение»

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
`onIconNumberChange: (Int?) -> Unit`,
`onSaveClick: () -> Unit`, `onBackClick: () -> Unit`.
Выбранная иконка SHALL читаться Content из `state.uiState.iconNumber` —
отдельное поле/дубликат в `ExerciseCreateScreenState` не вводится
(черновые поля `selectedIconNumber`/`onIconNumberChange` с no-op
дефолтами удаляются).

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
