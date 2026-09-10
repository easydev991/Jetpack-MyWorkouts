## Purpose

Экран «Упражнения» — вкладка каталога упражнений (стандартных и
пользовательских) в нижней навигации: реактивный список только для чтения с
FAB-заглушкой, из которого позже вырастут создание/редактирование/архивация.

## ADDED Requirements

### Requirement: ExercisesScreen Scaffold с TopAppBar и FAB

`ExercisesScreen` SHALL быть `@Composable fun ExercisesScreen(viewModel:
ExercisesViewModel, modifier: Modifier = Modifier)` (аналог `MainScreen`).
Её `Scaffold` SHALL содержать `topBar = { TopAppBar(title = {
Text(stringResource(R.string.exercises_title)) }) }` и
`floatingActionButton` с `Icon(Icons.Filled.Add, contentDescription =
stringResource(R.string.exercises_add_exercise))`. `Scaffold` SHALL
устанавливать `contentWindowInsets = WindowInsets.safeDrawing.only(
WindowInsetsSides.Horizontal)` — симметрично `MainScreen` и `MoreScreen`
(per-screen горизонтальные insets — см. `more-screen/spec.md`; rationale
обнуления insets внешнего `Scaffold` — `tab-navigation/spec.md`; без
двойного вычитания).

#### Scenario: ExercisesScreen renders with localized title

- **WHEN** `ExercisesScreen` composed с активной русской локалью
- **THEN** `TopAppBar` отображает «Упражнения»

#### Scenario: English locale title

- **WHEN** `ExercisesScreen` composed с активной английской локалью
- **THEN** `TopAppBar` отображает "Exercises"

### Requirement: Экран разделён на ViewModel-обёртку и internal Content

`ExercisesScreen` SHALL быть тонкой обёрткой: собирает `viewModel.uiState`
через `collectAsState()` и делегирует в `internal fun
ExercisesScreenContent(state: ExercisesScreenState, modifier)`. Data class
`ExercisesScreenState` (Test/Preview-state, аналог `MainScreenState`) SHALL
содержать `uiState: ExercisesUiState` и колбэк `onAddExerciseClick: () ->
Unit` — для UI-тестов и превью без ViewModel.

#### Scenario: Content доступен без ViewModel

- **WHEN** пишется UI-тест или превью
- **THEN** `ExercisesScreenContent` composed напрямую с
  `ExercisesScreenState(...)` — без `ExercisesViewModel`

### Requirement: Список упражнений реактивен

`ExercisesViewModel` SHALL подписываться на `repository.exercises("")`
(реактивный `Flow`, НЕ разовый `getAllExercises()`) и преобразовывать его в
`StateFlow<ExercisesUiState>` через `stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000), ExercisesUiState.Loading)`.
`ExercisesUiState` SHALL быть sealed-иерархией `Loading` /
`Success(exercises: List<Exercise>)` без `Error`-ветки (единственный источник
— офлайн Room-Flow, прецедент задокументирован в `MainUiState.kt`).
Сортировка по названию — на стороне SQL (`observeExercises`). Благодаря
реактивности будущие создание/удаление упражнений обновляют список без
действий экрана.

#### Scenario: Loading до первой эмиссии

- **WHEN** `ExercisesViewModel` создан, но репозиторий ещё не эмитил
- **THEN** `uiState.value` is `ExercisesUiState.Loading`

#### Scenario: Success со списком после первой эмиссии

- **WHEN** репозиторий эмитит список упражнений
- **THEN** `uiState.value` is `ExercisesUiState.Success` с тем же списком

#### Scenario: Изменение Flow обновляет список

- **WHEN** в репозитории добавили/удалили упражнение (Flow эмитит новый
  список) при активной подписке
- **THEN** `uiState` эмитит обновлённый `Success` без перезапуска ViewModel

### Requirement: Список отрисовывается LazyColumn с key

Каталог SHALL отрисовываться `LazyColumn` с `items(exercises, key = {
it.id })` — стабильные ключи для анимаций и переиспользования строк
(конвенция AGENTS.md). Каждая строка — `ExerciseListItemRow`.

#### Scenario: Строки идентифицируются по id упражнения

- **WHEN** код списка читается
- **THEN** `LazyColumn` передаёт `key = { it.id }`

### Requirement: Строка списка ExerciseListItemRow

`ExerciseListItemRow` SHALL быть `Column(fillMaxWidth, padding(horizontal
16.dp, vertical 16.dp), verticalArrangement spacedBy(4.dp))` (аналог
`EntryListItemRow` главного экрана). Внутри:

- `Row(fillMaxWidth, Arrangement.SpaceBetween, Alignment.CenterVertically)`:
  слева — `name` (`bodyLarge`, `FontWeight.Medium`, `Modifier.weight(1f)`),
  справа у края — локализованный лейбл `measuredBy` (`bodyMedium`,
  `colorScheme.onSurfaceVariant`, `TextAlign.End`).
- Ниже при `comment != null` — `comment` (`bodyMedium`,
  `colorScheme.onSurfaceVariant`), по аналогии с `WeightRow` главного экрана.

#### Scenario: Строка с комментарием

- **WHEN** в списке упражнение `name = "Подтягивания"`, `measuredBy = REPS`,
  `comment = "широкий хват"`
- **THEN** строка показывает «Подтягивания» слева и «повторы» справа у края
- **AND** вторая строка показывает «широкий хват»

#### Scenario: Строка без комментария

- **WHEN** у упражнения `comment = null`
- **THEN** строка содержит только название и лейбл типа измерения —
  пустая вторая строка не резервируется

### Requirement: Лейбл типа измерения локализован ru/en

Маппинг `ExerciseMeasuredBy` в строковый ресурс SHALL быть private
`@Composable`-функцией с тотальным `when` (без `else`-ветки — компилятор
следит за полнотой при добавлении значений enum): `REPS` →
`R.string.exercise_measured_by_reps`, `TIME` →
`R.string.exercise_measured_by_time`. Строки SHALL существовать в
`res/values/strings.xml` («повторы», «длительность») и
`res/values-en/strings.xml` ("reps", "duration").

#### Scenario: Russian locale labels

- **WHEN** список composed с активной русской локалью
- **THEN** упражнения с `measuredBy = REPS` показывают «повторы»
- **AND** упражнения с `measuredBy = TIME` показывают «длительность»

#### Scenario: English locale labels

- **WHEN** список composed с активной английской локалью
- **THEN** упражнения с `measuredBy = REPS` показывают "reps"
- **AND** упражнения с `measuredBy = TIME` показывают "duration"

### Requirement: Пустое состояние списка

При `Success(exercises = [])` экран SHALL показывать текст
`R.string.exercises_list_empty` («Нет упражнений» / "No exercises") по центру
body — аналогично `entry_list_empty` главного экрана. На практике каталог не
пуст (стандартные упражнения восстанавливаются в `deleteAllData`), но ветка
консистентна с главным экраном.

#### Scenario: Empty catalog

- **WHEN** `uiState` is `Success` с пустым списком
- **THEN** по центру экрана отображается «Нет упражнений» / "No exercises"
- **AND** `LazyColumn` не содержит строк

### Requirement: FAB — заглушка без навигации

FAB SHALL быть заглушкой: `onClick` логирует через `Log.d` с русским
сообщением (экран создания упражнения появится в будущем change) и SHALL NOT
выполнять навигацию или менять состояние экрана. Тег логирования SHALL быть
вынесен в `private const`.

#### Scenario: Тап по FAB остаётся на экране

- **WHEN** пользователь taps FAB
- **THEN** ничего не происходит кроме записи в лог
- **AND** активным остаётся `Screen.Exercises.route`

### Requirement: Заголовок вкладки и экрана локализован ru/en

Строка `exercises_title` SHALL существовать в `res/values/strings.xml`
(«Упражнения») и `res/values-en/strings.xml` ("Exercises") и использоваться
и как `NavigationBarItem.label`, и как `TopAppBar.title`. Состав, порядок и
метаданные вкладок (иконка, позиция Exercises между Main и More) — канон
`non-tab-screen-navigation/spec.md`; условная видимость вкладки и тоггл —
каноны `tab-navigation/spec.md` и `more-screen/spec.md` (там и только там).

#### Scenario: Russian locale tab label

- **WHEN** вкладка видна (настройка включена) и локаль устройства `ru`
- **THEN** лейбл вкладки — «Упражнения»

#### Scenario: English locale tab label

- **WHEN** вкладка видна (настройка включена) и локаль устройства `en`
- **THEN** лейбл вкладки — "Exercises"
