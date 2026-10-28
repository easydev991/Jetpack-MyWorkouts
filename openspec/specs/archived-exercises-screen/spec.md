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
`AppearanceScreen`). Экран SHALL содержать `Scaffold` с `TopAppBar`,
`SecondaryTabRow` с вкладками «Упражнения» / «Категории» (когда видны) и
`when`-свитч содержимого по выбранной вкладке.

#### Scenario: Экран рендерится с локализованным заголовком

- **WHEN** `ArchivedExercisesScreen` composed с активной русской локалью
- **THEN** `TopAppBar` отображает «Архив»

#### Scenario: English locale title

- **WHEN** `ArchivedExercisesScreen` composed с активной английской
  локалью
- **THEN** `TopAppBar` отображает "Archive"

#### Scenario: Тело экрана

- **WHEN** тело `Scaffold` composed
- **THEN** содержимое выбирается `when`-свитчем по выбранной вкладке:
  LazyColumn архивных упражнений / LazyColumn архивных категорий /
  заглушка пустого архива
- **AND** таб-роу виден по правилам требования «Табы "Упражнения" /
  "Категории" и видимость таб-бара»

#### Scenario: Content insets scaffold

- **WHEN** `Scaffold` экрана composed
- **THEN** он устанавливает
  `contentWindowInsets = WindowInsets.safeDrawing
  .only(WindowInsetsSides.Horizontal)` — симметрично `ExercisesScreen` и
  `AppearanceScreen`

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
`selectedTab: MutableStateFlow<ArchivedTab>`, и формировать
`StateFlow<ArchivedExercisesUiState>`. `ArchivedExercisesUiState` SHALL
быть sealed-иерархией без `Error`-ветки. При восстановлении упражнения
или категории реактивные Flow SHALL автоматически обновить списки без
ручного refresh.

#### Scenario: Loading до первой эмиссии

- **WHEN** `ArchivedExercisesViewModel` создан, но репозиторий ещё не
  эмитил
- **THEN** `uiState.value` is `ArchivedExercisesUiState.Loading`
- **AND** поток собран через `stateIn(viewModelScope,
  SharingStarted.WhileSubscribed(5000), ArchivedExercisesUiState.Loading)`

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

#### Scenario: Состав sealed-иерархии UiState

- **WHEN** `ArchivedExercisesUiState` объявлен
- **THEN** это sealed-иерархия `Loading` /
  `Success(exercises: List<Exercise>, categories: List<Category>,
  selectedTab: ArchivedTab)` без `Error`-ветки (единственный источник —
  офлайн Room-Flow)

#### Scenario: Выравнивание selectedTab при опустошении вкладки

- **WHEN** вкладка опустела после восстановления
- **THEN** ViewModel выравнивает `selectedTab` (требование «Табы
  "Упражнения" / "Категории" и видимость таб-бара»)

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
В отличие от `ExercisesScreen`, проверки `isCustom` нет — меню для
архивных строк показывается ВСЕГДА. Меню SHALL подсвечивать строку.
Дополнительно к жесту каждая строка архива SHALL предоставлять
локализованное accessibility-действие «Открыть меню» / "Open menu",
открывающее то же меню «Вернуть».

#### Scenario: Long-press архивного открывает меню с пунктом «Вернуть»

- **WHEN** пользователь делает долгое нажатие на строке архивного
  упражнения (стандартного или кастомного)
- **THEN** в точке нажатия открывается меню с единственным пунктом
  «Вернуть»
- **AND** строка подсвечена, пока меню открыто
- **AND** жест — `detectTapGestures(onLongPress)` по образцу в
  `ExercisesScreen` (дефолтный platform long-press ≈500 мс)
- **AND** меню (`DropdownMenu`) располагается в точке нажатия

#### Scenario: Accessibility-действие строки открывает меню «Вернуть»

- **WHEN** пользователь скринридера активирует на строке архивного
  упражнения accessibility-действие «Открыть меню»
- **THEN** открывается то же меню с единственным пунктом «Вернуть»
- **AND** выбор «Вернуть» работает как при открытии долгим нажатием
- **AND** при открытии accessibility-действием позиция меню по умолчанию
  (центр строки) допускается

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

Когда архив пуст целиком (нет ни архивных упражнений, ни архивных
категорий), экран SHALL показывать текст
`R.string.archived_exercises_empty` («В архиве пусто» / "Archive is
empty") по центру body. Таб-бар SHALL быть скрыт, `LazyColumn` SHALL NOT
содержать строк, экран НЕ закрывается автоматически. При пустом списке
одного типа и непустом другом заглушка SHALL NOT показываться.

#### Scenario: Архив пуст — заглушка по центру

- **WHEN** `uiState` is `Success` с пустыми списками упражнений и категорий
- **THEN** по центру экрана отображается «В архиве пусто» /
  "Archive is empty"
- **AND** `LazyColumn` не содержит строк AND экран не закрывается
- **AND** паттерн заглушки — как `exercises_list_empty` на
  `ExercisesScreen`
- **AND** состояние достижимо восстановлением последнего архивного объекта
  любого типа

#### Scenario: Восстановление последней категории — заглушка без закрытия

- **WHEN** на вкладке «Категории» восстановлена последняя архивная категория
  AND архивных упражнений нет
- **THEN** экран остаётся открытым
- **AND** по центру отображается «В архиве пусто» / "Archive is empty"

#### Scenario: Пустая вкладка при непустом архиве — просто пустой список

- **WHEN** на вкладке «Категории» нет категорий AND в архиве есть упражнения
- **THEN** таб-бар отображается, вкладка «Категории» показывает пустой
  список (заглушка «В архиве пусто» НЕ показывается — архив в целом непуст)
- **AND** при скрытом таб-баре отображается просто список непустого типа
  (по правилу «Табы "Упражнения" / "Категории" и видимость таб-бара»)

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

### Requirement: Табы «Упражнения» / «Категории» и видимость таб-бара

Экран архива SHALL отображать вкладки «Упражнения»
(`R.string.exercises_tab_exercises`) и «Категории»
(`R.string.exercises_tab_categories`) — общие ключи с экраном упражнений.
Таб-бар (`SecondaryTabRow`) SHALL рендериться только когда в архиве есть
И упражнения, И категории. Выбранная вкладка SHALL храниться в ViewModel и
переживать поворот экрана.

#### Scenario: В архиве и упражнения, и категории — таб-бар

- **WHEN** в архиве 2 упражнения AND 1 категория
- **THEN** под `TopAppBar` отображается `SecondaryTabRow` с вкладками
  «Упражнения» и «Категории»
- **AND** паттерн — «таб-бар при ≥ 2 видимых вкладках» из `OverviewScreen`
  (`archivedExercises` непуст AND `archivedCategories` непуст)

#### Scenario: Только упражнения — список без таб-бара

- **WHEN** в архиве 2 упражнения AND 0 категорий
- **THEN** таб-бар скрыт AND отображается список архивных упражнений

#### Scenario: Только категории — список без таб-бара

- **WHEN** в архиве 0 упражнений AND 3 категории
- **THEN** таб-бар скрыт AND отображается список архивных категорий

#### Scenario: Пустой архив — таб-бар скрыт, заглушка

- **WHEN** архив пуст целиком (обе коллекции пусты)
- **THEN** таб-бар скрыт
- **AND** отображается заглушка «В архиве пусто»

#### Scenario: Восстановление последнего упражнения переключает вкладку

- **WHEN** на вкладке «Упражнения» восстановлено последнее архивное
  упражнение AND в архиве остаются категории
- **THEN** экран НЕ закрывается AND автоматически выбирается вкладка
  «Категории»

#### Scenario: Восстановление последней категории переключает вкладку

- **WHEN** на вкладке «Категории» восстановлена последняя архивная
  категория AND в архиве остаются упражнения
- **THEN** экран НЕ закрывается
- **AND** автоматически выбирается оставшаяся непустая вкладка
  «Упражнения» (при смене видимости вкладок — последний объект типа
  восстановлен)

### Requirement: Вкладка «Категории» в архиве

Вкладка «Категории» в архиве SHALL отображать `LazyColumn` с `key = {
it.id }` архивных категорий (`archivedCategories()` репозитория). Строка
SHALL использовать тот же компонент `CategoryListItemRow` без дубликата.
Долгое нажатие на строке (и accessibility-действие «Открыть меню») SHALL
открывать контекстное меню с единственным пунктом «Вернуть» / "Restore".
Тап «Вернуть» SHALL вызывать `setCategoryArchived(id, false)`.

#### Scenario: Список архивных категорий

- **WHEN** в архиве 1 стандартная и 1 кастомная категория
- **THEN** отображаются 2 строки с цветовыми индикаторами, кастомная выше
- **AND** сортировка — `is_custom DESC, created_at DESC, name COLLATE
  NOCASE`

#### Scenario: Компонент строки категории

- **WHEN** создаётся строка архивной категории
- **THEN** используется `CategoryListItemRow`, создаваемый в пакете
  `exercises/` отдельным файлом по образцу `ExerciseListItemRow.kt`
  (конвенция «строки живут у своих экранов»)
- **AND** контракт строки — название + цветовой индикатор

#### Scenario: Long-press архивной категории открывает меню «Вернуть»

- **WHEN** пользователь делает долгое нажатие на строке архивной категории
  (стандартной или кастомной)
- **THEN** открывается меню с единственным пунктом «Вернуть»
- **AND** пункт — `exercise_list_menu_unarchive` с иконкой
  `Icons.Filled.Unarchive`, для стандартных и кастомных архивных категорий,
  по образцу архивных упражнений

#### Scenario: Тап «Вернуть» восстанавливает категорию

- **WHEN** открыто меню архивной категории с id = 4 AND пользователь тапает
  «Вернуть»
- **THEN** `setCategoryArchived(4, false)` вызывается
- **AND** категория исчезает из списка архива реактивно
- **AND** категория появляется на вкладке «Категории» экрана упражнений
