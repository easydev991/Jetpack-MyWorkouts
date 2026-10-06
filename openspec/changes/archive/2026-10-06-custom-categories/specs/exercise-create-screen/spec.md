# Spec Delta

## MODIFIED Requirements

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
