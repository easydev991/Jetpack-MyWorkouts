## Purpose

Экран «Архив упражнений» — отдельный экран для просмотра и
восстановления архивных упражнений каталога. Доступен через навигацию
с `ExercisesScreen` (тап кнопки архива в `TopAppBar`); не участвует в
нижней навигации. Позволяет пользователю «вернуть» упражнение из
архива в активный каталог.

## Requirements

### Requirement: Состав экрана

`ArchivedExercisesScreen` SHALL быть `@Composable fun
ArchivedExercisesScreen(viewModel: ArchivedExercisesViewModel,
onBackClick: () -> Unit, modifier: Modifier = Modifier)` (аналог
`AppearanceScreen`). Экран SHALL содержать `Scaffold` с
`TopAppBar` и телом (LazyColumn со списком архивных упражнений или
заглушка пустого состояния). `Scaffold` SHALL устанавливать
`contentWindowInsets = WindowInsets.safeDrawing.only(
WindowInsetsSides.Horizontal)` — симметрично `ExercisesScreen` и
`AppearanceScreen`.

#### Scenario: Экран рендерится с локализованным заголовком

- **WHEN** `ArchivedExercisesScreen` composed с активной русской
  локалью
- **THEN** `TopAppBar` отображает «Архив упражнений»

#### Scenario: English locale title

- **WHEN** `ArchivedExercisesScreen` composed с активной английской
  локалью
- **THEN** `TopAppBar` отображает "Archived exercises"

### Requirement: TopAppBar со стрелкой назад

`TopAppBar` SHALL содержать заголовок
`R.string.archived_exercises_title` («Архив упражнений» /
"Archived exercises") и `navigationIcon` с `IconButton`,
отображающим `Icons.AutoMirrored.Filled.ArrowBack` с
`contentDescription = R.string.back` («Назад» / "Back") — паттерн
`AppearanceScreen`. Тап по `IconButton` SHALL вызывать `onBackClick`
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
ArchivedExercisesUiState` и колбэки `onUnarchive` / `onBackClick`.

#### Scenario: Content доступен без ViewModel

- **WHEN** пишется UI-тест или превью
- **THEN** `ArchivedExercisesScreenContent` composed напрямую с
  `ArchivedExercisesState(...)` — без `ArchivedExercisesViewModel`

### Requirement: Список архивных упражнений реактивен

`ArchivedExercisesViewModel` SHALL подписываться на
`repository.archivedExercises()` (реактивный `Flow<List<Exercise>>`,
НЕ разовый `getAllExercises()` — тот возвращает все включая активные)
и формировать `StateFlow<ArchivedExercisesUiState>` через `stateIn(
viewModelScope, SharingStarted.WhileSubscribed(5000),
ArchivedExercisesUiState.Loading)`. `ArchivedExercisesUiState` SHALL
быть sealed-иерархией `Loading` /
`Success(exercises: List<Exercise>)` без `Error`-ветки (единственный
источник — офлайн Room-Flow). При восстановлении упражнения
реактивный Flow SHALL автоматически обновить список без ручного
refresh.

#### Scenario: Loading до первой эмиссии

- **WHEN** `ArchivedExercisesViewModel` создан, но репозиторий ещё не
  эмитил
- **THEN** `uiState.value` is `ArchivedExercisesUiState.Loading`

#### Scenario: Success со списком после первой эмиссии

- **WHEN** репозиторий эмитит список архивных упражнений
- **THEN** `uiState.value` is `ArchivedExercisesUiState.Success` с
  этим списком

#### Scenario: Восстановление упражнения обновляет список

- **WHEN** в `Success` есть упражнение X AND пользователь тапает
  «Вернуть» на строке X
- **THEN** `repository.setArchived(X.id, false)` вызывается
- **AND** реактивный Flow эмитит список без X
- **AND** `uiState.value` обновляется без перезапуска ViewModel

### Requirement: Переиспользование ExerciseListItemRow

Каждая строка списка архивных упражнений SHALL использовать тот же
компонент `ExerciseListItemRow` из `ExercisesScreen.kt` без
дубликата (контракт «Строка списка ExerciseListItemRow» в
`exercises-screen/spec.md` — `name`, `measuredBy`, `comment`,
подсветка при `isSelected`). Строки SHALL отрисовываться
`LazyColumn` с `items(exercises, key = { it.id })` — стабильные
ключи для анимаций и переиспользования.

#### Scenario: Строка архива рендерится через ExerciseListItemRow

- **WHEN** в списке архива есть упражнение «Бег» с `measuredBy =
  TIME`
- **THEN** строка показывает «Бег» слева и «длительность» справа у
  края
- **AND** внешний вид строки идентичен строкам на `ExercisesScreen`

### Requirement: Long-press меню «Вернуть» для архивных строк

Долгое нажатие на строке архивного упражнения SHALL открывать
контекстное меню с единственным пунктом «Вернуть» / "Restore"
(`exercise_list_menu_unarchive`, иконка `Icons.Filled.Unarchive`).
В отличие от `ExercisesScreen`, проверки `isCustom` нет — для
архивных строк меню ВСЕГДА показывается (и для стандартных, и для
кастомных архивных упражнений доступен возврат). Жест
`detectTapGestures(onLongPress)` — по образцу в `ExercisesScreen`
(дефолтный platform long-press ≈500 мс). Меню (`DropdownMenu`) SHALL
располагаться в точке нажатия и подсвечивать строку.

Дополнительно к жесту каждая строка архива SHALL предоставлять
локализованное accessibility-действие «Открыть меню» / "Open menu":
активация действия из меню действий скринридера SHALL открывать то
же меню «Вернуть». При открытии accessibility-действием позиция меню
по умолчанию (центр строки) SHALL допускаться.

#### Scenario: Long-press архивного открывает меню с пунктом «Вернуть»

- **WHEN** пользователь делает долгое нажатие на строке архивного
  упражнения (стандартного или кастомного)
- **THEN** в точке нажатия открывается меню с единственным пунктом
  «Вернуть»
- **AND** строка подсвечена, пока меню открыто

#### Scenario: Accessibility-действие строки открывает меню «Вернуть»

- **WHEN** пользователь скринридера активирует на строке архивного
  упражнения accessibility-действие «Открыть меню»
- **THEN** открывается то же меню с единственным пунктом «Вернуть»
- **AND** выбор «Вернуть» работает как при открытии долгим нажатием

#### Scenario: Тап «Вернуть» восстанавливает упражнение

- **WHEN** открыто меню архивного упражнения с id = 7 AND пользователь
  тапает «Вернуть»
- **THEN** меню закрывается
- **AND** `repository.setArchived(7, false)` вызывается
- **AND** упражнение пропадает из списка архива (реактивное
  обновление)
- **AND** упражнение появляется в активном каталоге на
  `ExercisesScreen` в позиции согласно сортировке

#### Scenario: Закрытие меню снимает подсветку

- **WHEN** меню открыто AND пользователь тапает вне меню (или
  нажимает системный back)
- **THEN** меню закрывается
- **AND** подсветка строки снята

### Requirement: Пустое состояние списка

При `Success(exercises = [])` экран SHALL показывать текст
`R.string.archived_exercises_empty` («В архиве пусто» / "Archive is
empty") по центру body — паттерн заглушки как `exercises_list_empty`
на `ExercisesScreen`. `LazyColumn` SHALL NOT содержать строк.

#### Scenario: Архив пуст — заглушка по центру

- **WHEN** `uiState` is `Success` с пустым списком
- **THEN** по центру экрана отображается «В архиве пусто» /
  "Archive is empty"
- **AND** `LazyColumn` не содержит строк

### Requirement: Сортировка архива

Список архивных упражнений SHALL отрисовываться в порядке,
определяемом SQL-запросом `archivedExercises()`: `ORDER BY
is_custom DESC, created_at DESC, name COLLATE NOCASE`. Контракт
сортировки — тот же, что у активного каталога
(`exercises-screen/spec.md`, требование «Сортировка каталога —
кастомные сверху, новые первые»); меняется только предикат
`WHERE is_archived = 1`.

#### Scenario: Кастомное архивное упражнение выше стандартного

- **WHEN** в архиве есть кастомное упражнение «Жим» (`isCustom =
  true`) и стандартное «Приседания» (`isCustom = false`)
- **THEN** в списке «Жим» отображается выше «Приседаний»

#### Scenario: Несколько кастомных архивных — новые сверху

- **WHEN** в архиве есть два кастомных упражнения: «A» с `createdAt =
  T1` и «B» с `createdAt = T2 > T1`
- **THEN** в списке «B» отображается выше «A»

### Requirement: Нижняя навигация скрыта

`ArchivedExercisesScreen` SHALL NOT отображать нижнюю панель
навигации. Контракт — `non-tab-screen-navigation/spec.md`, требование
«ArchivedExercises не участвует в нижней навигации». Дублирование
формулы и имён локальных переменных здесь намеренно не приводится.

#### Scenario: Нижняя навигация скрыта

- **WHEN** активный route is `Screen.ArchivedExercises.route`
- **THEN** нижний бар навигации не отображается
