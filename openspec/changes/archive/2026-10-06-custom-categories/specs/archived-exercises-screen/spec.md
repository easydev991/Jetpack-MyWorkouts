# Spec Delta

## ADDED Requirements

### Requirement: Табы «Упражнения» / «Категории» и видимость таб-бара

Экран архива SHALL отображать вкладки «Упражнения» (`R.string.exercises_tab_exercises`)
и «Категории» (`R.string.exercises_tab_categories`) — общие ключи с экраном
упражнений. Таб-бар (`SecondaryTabRow`) SHALL рендериться только когда в
архиве есть И упражнения, И категории (`archivedExercises` непуст AND
`archivedCategories` непуст) — паттерн «таб-бар при ≥ 2 видимых вкладках»
из `OverviewScreen`. Если в архиве только один тип объектов — таб-бар скрыт,
отображается просто список этого типа. Если архив пуст целиком (обе
коллекции пусты) — таб-бар скрыт, отображается заглушка «В архиве пусто».
Выбранная вкладка SHALL храниться в ViewModel и переживать поворот экрана;
при смене видимости вкладок (последний объект типа восстановлен) SHALL
автоматически выбираться оставшаяся непустая вкладка без закрытия экрана.

#### Scenario: В архиве и упражнения, и категории — таб-бар

- **WHEN** в архиве 2 упражнения AND 1 категория
- **THEN** под `TopAppBar` отображается `SecondaryTabRow` с вкладками
  «Упражнения» и «Категории»

#### Scenario: Только упражнения — список без таб-бара

- **WHEN** в архиве 2 упражнения AND 0 категорий
- **THEN** таб-бар скрыт AND отображается список архивных упражнений

#### Scenario: Только категории — список без таб-бара

- **WHEN** в архиве 0 упражнений AND 3 категории
- **THEN** таб-бар скрыт AND отображается список архивных категорий

#### Scenario: Восстановление последнего упражнения переключает вкладку

- **WHEN** на вкладке «Упражнения» восстановлено последнее архивное
  упражнение AND в архиве остаются категории
- **THEN** экран НЕ закрывается AND автоматически выбирается вкладка
  «Категории»

### Requirement: Вкладка «Категории» в архиве

Вкладка «Категории» в архиве SHALL отображать `LazyColumn` с `key = {
it.id }` архивных категорий (`archivedCategories()` репозитория, сортировка
`is_custom DESC, created_at DESC, name COLLATE NOCASE`). Строка SHALL
использовать тот же компонент `CategoryListItemRow` (создаётся в пакете
`exercises/` отдельным файлом по образцу `ExerciseListItemRow.kt` —
конвенция «строки живут у своих экранов»; контракт — название +
цветовой индикатор), без дубликата. Долгое
нажатие на строке (и accessibility-действие «Открыть меню») SHALL открывать
контекстное меню с единственным пунктом «Вернуть» / "Restore"
(`exercise_list_menu_unarchive`, иконка `Icons.Filled.Unarchive`) — для
стандартных и кастомных архивных категорий, по образцу архивных упражнений.
Тап «Вернуть» SHALL вызывать `setCategoryArchived(id, false)`; категория
реактивно исчезает из списка архива и появляется в активном списке на
вкладке «Категории» экрана упражнений.

#### Scenario: Список архивных категорий

- **WHEN** в архиве 1 стандартная и 1 кастомная категория
- **THEN** отображаются 2 строки с цветовыми индикаторами, кастомная выше

#### Scenario: Long-press архивной категории открывает меню «Вернуть»

- **WHEN** пользователь делает долгое нажатие на строке архивной категории
  (стандартной или кастомной)
- **THEN** открывается меню с единственным пунктом «Вернуть»

#### Scenario: Тап «Вернуть» восстанавливает категорию

- **WHEN** открыто меню архивной категории с id = 4 AND пользователь тапает
  «Вернуть»
- **THEN** `setCategoryArchived(4, false)` вызывается
- **AND** категория исчезает из списка архива реактивно
- **AND** категория появляется на вкладке «Категории» экрана упражнений

## MODIFIED Requirements

### Requirement: Пустое состояние списка

Когда архив пуст целиком (нет ни архивных упражнений, ни архивных
категорий), экран SHALL показывать текст `R.string.archived_exercises_empty`
(«В архиве пусто» / "Archive is empty") по центру body — паттерн заглушки
как `exercises_list_empty` на `ExercisesScreen`. Таб-бар SHALL быть скрыт,
`LazyColumn` SHALL NOT содержать строк, экран НЕ закрывается
автоматически. Это состояние достижимо восстановлением последнего
архивного объекта любого типа. При пустом списке одного типа и непустом
другом заглушка SHALL NOT показываться — отображается таб-бар и пустой
список выбранной вкладки (или просто список непустого типа, если таб-бар
скрыт по правилу «Табы "Упражнения" / "Категории" и видимость таб-бара»).

#### Scenario: Архив пуст — заглушка по центру

- **WHEN** `uiState` is `Success` с пустыми списками упражнений и категорий
- **THEN** по центру экрана отображается «В архиве пусто» /
  "Archive is empty"
- **AND** `LazyColumn` не содержит строк AND экран не закрывается

#### Scenario: Восстановление последней категории — заглушка без закрытия

- **WHEN** на вкладке «Категории» восстановлена последняя архивная категория
  AND архивных упражнений нет
- **THEN** экран остаётся открытым
- **AND** по центру отображается «В архиве пусто» / "Archive is empty"

#### Scenario: Пустая вкладка при непустом архиве — просто пустой список

- **WHEN** на вкладке «Категории» нет категорий AND в архиве есть упражнения
- **THEN** таб-бар отображается, вкладка «Категории» показывает пустой
  список (заглушка «В архиве пусто» НЕ показывается — архив в целом непуст)

### Requirement: Состав экрана

`ArchivedExercisesScreen` SHALL быть `@Composable fun
ArchivedExercisesScreen(viewModel: ArchivedExercisesViewModel,
onBackClick: () -> Unit, modifier: Modifier = Modifier)` (аналог
`AppearanceScreen`). Экран SHALL содержать `Scaffold` с `TopAppBar` и телом:
`SecondaryTabRow` с вкладками «Упражнения» / «Категории» (когда видны —
требование «Табы "Упражнения" / "Категории" и видимость таб-бара») и
`when`-свитч содержимого по выбранной вкладке (LazyColumn архивных
упражнений / LazyColumn архивных категорий / заглушка пустого архива).
`Scaffold` SHALL устанавливать `contentWindowInsets = WindowInsets.safeDrawing
.only(WindowInsetsSides.Horizontal)` — симметрично `ExercisesScreen` и
`AppearanceScreen`.

#### Scenario: Экран рендерится с локализованным заголовком

- **WHEN** `ArchivedExercisesScreen` composed с активной русской локалью
- **THEN** `TopAppBar` отображает «Архив»

#### Scenario: English locale title

- **WHEN** `ArchivedExercisesScreen` composed с активной английской
  локалью
- **THEN** `TopAppBar` отображает "Archive"

### Requirement: TopAppBar со стрелкой назад

`TopAppBar` SHALL содержать заголовок `R.string.archive_title`
(«Архив» / "Archive" — экран объединяет архивы упражнений и категорий;
ключ `archived_exercises_title` удаляется из строковых ресурсов) и
`navigationIcon` с `IconButton`, отображающим
`Icons.AutoMirrored.Filled.ArrowBack` с
`contentDescription = R.string.back` («Назад» / "Back") —
паттерн `AppearanceScreen`. Тап по `IconButton` SHALL вызывать `onBackClick`
(навигация обратно на `ExercisesScreen`).

#### Scenario: Стрелка назад в TopAppBar

- **WHEN** `ArchivedExercisesScreen` composed
- **THEN** `TopAppBar.navigationIcon` содержит `IconButton` со стрелкой
  `Icons.AutoMirrored.Filled.ArrowBack`

#### Scenario: Тап стрелки назад возвращает на ExercisesScreen

- **WHEN** пользователь тапает `IconButton` со стрелкой назад
- **THEN** `onBackClick` вызывается ровно один раз
- **AND** активным становится `Screen.Exercises.route`

### Requirement: Разделение на ViewModel-обёртку и internal Content

`ArchivedExercisesScreen` SHALL быть тонкой обёрткой: собирает
`viewModel.uiState` через `collectAsState()` и делегирует в
`internal fun ArchivedExercisesScreenContent(state:
ArchivedExercisesState, modifier)`. Data class
`ArchivedExercisesState` (Test/Preview-state, аналог
`ExercisesScreenState`) SHALL содержать `uiState:
ArchivedExercisesUiState` и колбэки `onUnarchiveExercise: (Long) -> Unit` /
`onUnarchiveCategory: (Long) -> Unit` / `onTabSelect` / `onBackClick`.

#### Scenario: Content доступен без ViewModel

- **WHEN** пишется UI-тест или превью
- **THEN** `ArchivedExercisesScreenContent` composed напрямую с
  `ArchivedExercisesState(...)` — без `ArchivedExercisesViewModel`

### Requirement: Список архивных упражнений реактивен

`ArchivedExercisesViewModel` SHALL подписываться на
`repository.archivedExercises()` и `repository.archivedCategories()`
(реактивные `Flow`, НЕ разовые снимки), на внутренний
`selectedTab: MutableStateFlow<ArchivedTab>` и формировать
`StateFlow<ArchivedExercisesUiState>` через `stateIn(
viewModelScope, SharingStarted.WhileSubscribed(5000),
ArchivedExercisesUiState.Loading)`. `ArchivedExercisesUiState` SHALL
быть sealed-иерархией `Loading` / `Success(exercises:
List<Exercise>, categories: List<Category>, selectedTab: ArchivedTab)` без
`Error`-ветки (единственный источник — офлайн Room-Flow). При
восстановлении упражнения или категории реактивные Flow SHALL автоматически
обновить списки без ручного refresh; ViewModel SHALL выравнивать
`selectedTab` при опустошении вкладки (требование «Табы "Упражнения" /
"Категории" и видимость таб-бара»).

#### Scenario: Loading до первой эмиссии

- **WHEN** `ArchivedExercisesViewModel` создан, но репозиторий ещё не
  эмитил
- **THEN** `uiState.value` is `ArchivedExercisesUiState.Loading`

#### Scenario: Success со списком после первой эмиссии

- **WHEN** репозиторий эмитит списки архивных упражнений и категорий
- **THEN** `uiState.value` is `ArchivedExercisesUiState.Success`
  с этими списками

#### Scenario: Восстановление упражнения обновляет список

- **WHEN** в `Success` есть упражнение X AND пользователь тапает
  «Вернуть» на строке X
- **THEN** `repository.setArchived(X.id, false)` вызывается
- **AND** реактивный Flow эмитит список без X
- **AND** `uiState.value` обновляется без перезапуска ViewModel

#### Scenario: Восстановление категории обновляет список

- **WHEN** пользователь тапает «Вернуть» на архивной категории Y
- **THEN** `repository.setCategoryArchived(Y.id, false)` вызывается
- **AND** `uiState.value` обновляется без перезапуска ViewModel
