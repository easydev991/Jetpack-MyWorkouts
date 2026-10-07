# exercises-screen Specification

## Purpose

Экран «Упражнения» — вкладка каталога упражнений (стандартных и
пользовательских) в нижней навигации: реактивный список только для чтения
с FAB, открывающим экран создания упражнения.

## Requirements

### Requirement: ExercisesScreen Scaffold с TopAppBar и FAB

`ExercisesScreen` — composable; полная сигнатура (обязательные колбэки
навигации — паттерн существующей) — канон требования «Сигнатура
ExercisesScreen принимает колбэк архива» ниже. Её `Scaffold` SHALL содержать
`topBar = { TopAppBar(title = {
Text(stringResource(R.string.exercises_title)) }) }` и
`floatingActionButton` с `Icon(Icons.Filled.Add, contentDescription` —
`exercises_add_exercise` на вкладке «Упражнения», `categories_add_category`
на вкладке «Категории»). `Scaffold` SHALL устанавливать
`contentWindowInsets = WindowInsets.safeDrawing.only(
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

#### Scenario: Content description FAB зависит от вкладки

- **WHEN** активна вкладка «Категории»
- **THEN** FAB имеет contentDescription «Добавить категорию» (ru) /
  "Add category" (en)

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
(реактивный `Flow` НЕ-архивных упражнений, НЕ разовый
`getAllExercises()` — тот для экспорта/импорта), на
`repository.categories()` (активные категории для вкладки «Категории»), на
внутренние `pendingDeleteId: MutableStateFlow<Long?>` (идентификатор
упражнения, для которого открыт алерт подтверждения удаления),
`pendingArchiveId: MutableStateFlow<Long?>` (алерт архивации упражнения),
`pendingCategoryDeleteId: MutableStateFlow<Long?>` (идентификатор
категории, для которой открыт алерт подтверждения удаления —
`deleteCategory(id)`); `pendingCategoryArchiveId:
MutableStateFlow<Long?>` (алерт архивации категории),
`selectedTab: MutableStateFlow<ExercisesTab>`
(выбранная вкладка), на `repository.hasArchived(): Flow<Boolean>`
(реактивный признак непустого архива — упражнений или категорий — для
кнопки в `TopAppBar`; репозиторий собирает его `combine`'ом двух
DAO-счётчиков, интерфейсу отдельные счётчики не нужны), объединяя потоки через
`combine` (при числе потоков больше 5 — группировкой в несколько
`combine` либо vararg-вариантом; способ группировки — на усмотрение
реализации) в единый `StateFlow<ExercisesUiState>` (`stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000), ExercisesUiState.Loading)`).
`ExercisesUiState.Success` SHALL содержать поля `exercises:
List<Exercise>` (только активные — фильтрация в SQL
`exercises(query)`), `categories: List<Category>` (активные),
`selectedTab: ExercisesTab`, `pendingDeleteId: Long?`, `deleteError:
DeleteExerciseError? = null`, `pendingArchiveId: Long?`,
`pendingCategoryDeleteId: Long?`, `categoryDeleteError:
DeleteCategoryError? = null`, `pendingCategoryArchiveId: Long?`,
`hasArchived: Boolean = false`. Sealed-иерархия —
`Loading` / `Success(...)` без `Error`-ветки (единственный источник —
офлайн Room-Flow; ошибки доводятся до пользователя снекбаром или тихо).
Сортировка SHALL быть на стороне SQL (DAO `observeExercises` / DAO-запрос
категорий); критерии и порядок определены требованием «Сортировка каталога —
кастомные сверху, новые первые» в главной спеке `exercises-screen`. Благодаря реактивности
создание, удаление, редактирование, архивация и восстановление упражнений
и категорий обновляют списки без действий экрана.

#### Scenario: Loading до первой эмиссии

- **WHEN** `ExercisesViewModel` создан, но репозиторий ещё не эмитил
- **THEN** `uiState.value` is `ExercisesUiState.Loading`

#### Scenario: Success со списком после первой эмиссии

- **WHEN** репозиторий эмитит список упражнений
- **THEN** `uiState.value` is `ExercisesUiState.Success` с тем же списком
  и `pendingDeleteId == null`

#### Scenario: Изменение Flow обновляет список

- **WHEN** в репозитории добавили/удалили/изменили/заархивировали
  упражнение или категорию (Flow эмитит новый список) при активной подписке
- **THEN** `uiState` эмитит обновлённый `Success` без перезапуска
  ViewModel

#### Scenario: pending-идентификатор отражается в Success

- **WHEN** вызван `requestDelete` для упражнения с id = 5
- **THEN** `uiState.value` is `Success` с `pendingDeleteId == 5`
- **WHEN** вызван `cancelDelete()`
- **THEN** `uiState.value` is `Success` с `pendingDeleteId == null`

#### Scenario: Выбор вкладки отражается в Success

- **WHEN** вызван `selectTab(ExercisesTab.CATEGORIES)`
- **THEN** `uiState.value` is `Success` с `selectedTab == CATEGORIES`

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

### Requirement: Иконка упражнения в строке списка

Строка списка упражнений SHALL показывать иконку упражнения слева от
названия в верхнем ряду строки, если у упражнения назначен известный
номер иконки. Иконка SHALL иметь размер 24dp и SHALL NOT окрашиваться
(tint отсутствует). Вариант иконки SHALL выбираться по теме системы:
тёмный вариант — при тёмной теме, светлый — при светлой. Иконка SHALL
NOT иметь собственное описание доступности
(`contentDescription = null`): название упражнения уже озвучивается
строкой. Если у упражнения `iconNumber = null` или номер неизвестен
таблице иконок, иконка SHALL NOT отображаться — строка остаётся как до
изменения. Лейбл типа измерения сохраняет позицию у правого края.

#### Scenario: Упражнение с назначенной иконкой

- **WHEN** список отображает упражнение с известным номером иконки
  (например, приседания)
- **THEN** слева от названия упражнения в верхнем ряду отображается
  иконка 24dp
- **AND** лейбл типа измерения остаётся у правого края

#### Scenario: Упражнение без иконки

- **WHEN** список отображает упражнение с `iconNumber = null`
- **THEN** иконка не отображается
- **AND** строка остаётся как до изменения — название слева,
  лейбл типа измерения справа

#### Scenario: Тёмная тема использует тёмный вариант

- **WHEN** список отображается при тёмной теме системы
- **THEN** упражнения с назначенной иконкой показывают тёмный вариант
  ресурса

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

### Requirement: FAB открывает ExerciseCreateScreen

FAB экрана упражнений SHALL действовать по активной вкладке: на вкладке
«Упражнения» тап по FAB SHALL инициировать навигацию на
`Screen.ExerciseCreate.route` через колбэк `onAddExerciseClick`;
на вкладке «Категории» — навигацию на `Screen.CategoryCreate.createRoute()`
через колбэк `onAddCategoryClick` (без параметра — create-режим). Колбэки
прокидываются из `RootScreen` (паттерн `onAddExerciseClick`). Экран создания
упражнения определён в capability `exercise-create-screen`, экран создания
категории — в capability `category-create-screen`.

#### Scenario: Тап по FAB открывает ExerciseCreateScreen

- **WHEN** активна вкладка «Упражнения» AND пользователь нажимает FAB
- **THEN** `onAddExerciseClick` вызывается ровно один раз
- **AND** активным становится `Screen.ExerciseCreate.route`

#### Scenario: Тап по FAB на вкладке категорий открывает CategoryCreateScreen

- **WHEN** активна вкладка «Категории» AND пользователь нажимает FAB
- **THEN** `onAddCategoryClick` вызывается ровно один раз
- **AND** активным становится `Screen.CategoryCreate.route` (create-режим)

### Requirement: Сортировка каталога — кастомные сверху, новые первые

Каталог упражнений SHALL отображаться в порядке, определяемом SQL-запросом
репозитория: `ORDER BY is_custom DESC, created_at DESC, name COLLATE
NOCASE`. Кастомные упражнения (`isCustom = true`) SHALL находиться выше
стандартных (`isCustom = false`); внутри каждой группы — по убыванию
`createdAt` (новые сверху); при равных `createdAt` — по `name` без учёта
регистра (COLLATE NOCASE, по возрастанию) как финальный tie-break
(действует в обеих группах — кастомных и стандартных).

#### Scenario: Кастомное упражнение выше стандартного

- **WHEN** в каталоге есть упражнение «Жим» (`isCustom = true`) и
  стандартное «Приседания» (`isCustom = false`)
- **THEN** в списке «Жим» отображается выше «Приседаний»

#### Scenario: Несколько кастомных — новые сверху

- **WHEN** в каталоге есть два кастомных упражнения: «A» с `createdAt =
  T1` и «B» с `createdAt = T2 > T1`
- **THEN** в списке «B» отображается выше «A»

#### Scenario: Равные createdAt — сортировка по имени (COLLATE NOCASE)

- **WHEN** в каталоге есть два кастомных упражнения «Бег» и «Жим» с
  одинаковым `createdAt`
- **THEN** в списке «Бег» отображается выше «Жим» (по возрастанию
  `name` с `COLLATE NOCASE`)

#### Scenario: Стандартные сортированы по имени без учёта регистра (ASCII-выборка)

- **WHEN** в каталоге только стандартные упражнения на ASCII-выборке
  (паттерн — `ExerciseDaoTest.search_results_areSortedByNameCaseInsensitive`,
  комментарий L94–95: «SQLite NOCASE-коллация покрывает только ASCII»)
  с одинаковым `createdAt`: «alpha», «Beta», «CHARLIE», «delta»
- **THEN** порядок: `alpha`, `Beta`, `CHARLIE`, `delta`

### Requirement: Контекстное меню по долгому нажатию — с учётом типа упражнения

Долгое нажатие на строке `ExerciseListItemRow` SHALL открывать
контекстное меню с учётом типа упражнения. Для пользовательского
упражнения (`isCustom = true`) меню SHALL содержать три пункта в
порядке: «Изменить» / "Edit" (`exercise_list_menu_edit`, иконка Edit),
«Архивировать» / "Archive" (`exercise_list_menu_archive`, иконка Archive),
«Удалить» / "Delete" (`exercise_list_menu_delete`, иконка Delete). Для
стандартного упражнения (`isCustom = false`) меню SHALL содержать
ровно один пункт «Архивировать» / "Archive"
(`exercise_list_menu_archive`, иконка Archive) — пункты «Изменить» и
«Удалить» для стандартных
НЕ показываются (стандартные нельзя редактировать/удалять по
дизайну — контракт архивного `edit-delete-exercise` Decision 4
фиксирует отсутствие меню для удаления/редактирования стандартных;
для архивации делается исключение осознанно — стандартные можно
архивировать). Жест `detectTapGestures(onLongPress)` — по образцу
в `MainScreen` (дефолтный platform long-press ≈500 мс, без
кастомного timeout). Меню (`DropdownMenu`) SHALL располагаться в
точке нажатия (офсет внутри корня списка — паттерн `MainScreen`).
Пока меню открыто, строка SHALL подсвечиваться (цвет через
`animateColorAsState`, семантика `selected` — паттерн
`EntryListItemRow`); закрытие меню (тап вне меню, выбор пункта,
системный back) снимает подсветку.

Дополнительно к жесту каждая строка каталога SHALL предоставлять
локализованное accessibility-действие «Открыть меню» / "Open menu":
активация действия из меню действий скринридера SHALL открывать то
же контекстное меню (состав пунктов — по типу упражнения, как при
долгом нажатии). При открытии accessibility-действием позиция меню
по умолчанию (центр строки) SHALL допускаться.

Тап «Изменить» (только для кастомных) SHALL инициировать навигацию
на `Screen.ExerciseCreate.createRoute(exerciseId)` (маршрут —
capability `non-tab-screen-navigation`, поведение edit-режима —
capability `exercise-create-screen`). Тап «Архивировать» SHALL
закрывать меню и открывать алерт подтверждения архивации
(требование «Алерт подтверждения архивации» ниже). Тап «Удалить»
(только для кастомных) SHALL закрывать меню и открывать алерт
подтверждения удаления (требование «Алерт подтверждения удаления
упражнения» — без изменений).

Если упражнение — единственное активное в каталоге (остальные в
архиве или удалены), пункты «Архивировать» и «Удалить» SHALL
скрываться из меню; пункт «Изменить» (кастомные) остаётся. Если
после скрытия в меню не остаётся ни одного пункта (последнее
активное — стандартное упражнение), долгое нажатие и
accessibility-действие «Открыть меню» SHALL NOT открывать меню —
строка не подсвечивается. Видимость пунктов вычисляется UI из
`ExercisesUiState.Success.exercises` (размер списка активных == 1
AND id совпадает с целью); список — полный нефильтрованный каталог
(сегодня `repository.exercises("")`): при появлении поиска/фильтра
формула SHALL учитывать весь каталог, а не выборку. Новые запросы в VM
не вводятся.

#### Scenario: Long-press кастомного открывает меню с тремя пунктами

- **WHEN** пользователь делает долгое нажатие на строке упражнения с
  `isCustom = true`, когда в каталоге есть и другие активные упражнения
- **THEN** в точке нажатия открывается меню с пунктами «Изменить»,
  «Архивировать» и «Удалить» в указанном порядке
- **AND** строка подсвечена, пока меню открыто

#### Scenario: Long-press стандартного открывает меню с одним пунктом

- **WHEN** пользователь делает долгое нажатие на строке упражнения с
  `isCustom = false`, когда в каталоге есть и другие активные упражнения
- **THEN** в точке нажатия открывается меню с единственным пунктом
  «Архивировать»
- **AND** пункты «Изменить» и «Удалить» НЕ показываются
- **AND** строка подсвечена, пока меню открыто

#### Scenario: Accessibility-действие строки открывает меню по типу упражнения

- **WHEN** пользователь скринридера активирует на строке упражнения
  accessibility-действие «Открыть меню», когда в каталоге есть и другие
  активные упражнения
- **THEN** открывается то же контекстное меню с составом пунктов по
  типу упражнения (три пункта у кастомного, «Архивировать» у
  стандартного)
- **AND** выбор пунктов меню работает как при открытии долгим
  нажатием

#### Scenario: Тап «Изменить» навигирует на edit-экран

- **WHEN** открыто меню кастомного упражнения с id = 7 AND пользователь
  тапает «Изменить»
- **THEN** меню закрывается
- **AND** выполняется навигация на
  `Screen.ExerciseCreate.createRoute(7)`

#### Scenario: Тап «Архивировать» открывает алерт архивации

- **WHEN** открыто меню упражнения «Жим» (кастомного или стандартного)
  AND пользователь тапает «Архивировать»
- **THEN** меню закрывается
- **AND** открывается алерт подтверждения архивации с заголовком
  «Архивировать упражнение?» и текстом с именем упражнения

#### Scenario: Закрытие меню снимает подсветку строки

- **WHEN** меню открыто AND пользователь тапает вне меню (или выбирает
  пункт, или нажимает системный back)
- **THEN** меню закрывается
- **AND** подсветка строки снята

#### Scenario: У последнего активного кастомного — только «Изменить»

- **WHEN** кастомное упражнение — единственное активное в каталоге AND
  пользователь делает долгое нажатие на его строке
- **THEN** меню открывается с единственным пунктом «Изменить»
- **AND** пункты «Архивировать» и «Удалить» НЕ показываются

#### Scenario: У последнего активного стандартного меню не открывается

- **WHEN** стандартное упражнение — единственное активное в каталоге
- **THEN** долгое нажатие на его строке не открывает меню
- **AND** accessibility-действие «Открыть меню» также не открывает меню
- **AND** подсветка строки не включается


## Requirement: Контекстное меню по долгому нажатию — с учётом типа упражнения

Долгое нажатие на строке `ExerciseListItemRow` SHALL открывать
контекстное меню с учётом типа упражнения. Для пользовательского
упражнения (`isCustom = true`) меню SHALL содержать три пункта в
порядке: «Изменить» / "Edit" (`exercise_list_menu_edit`, иконка Edit),
«Архивировать» / "Archive" (`exercise_list_menu_archive`, иконка Archive),
«Удалить» / "Delete" (`exercise_list_menu_delete`, иконка Delete). Для
стандартного упражнения (`isCustom = false`) меню SHALL содержать
ровно один пункт «Архивировать» / "Archive"
(`exercise_list_menu_archive`, иконка Archive) — пункты «Изменить» и
«Удалить» для стандартных
НЕ показываются (стандартные нельзя редактировать/удалять по
дизайну — контракт архивного `edit-delete-exercise` Decision 4
фиксирует отсутствие меню для удаления/редактирования стандартных;
для архивации делается исключение осознанно — стандартные можно
архивировать). Жест `detectTapGestures(onLongPress)` — по образцу
в `MainScreen` (дефолтный platform long-press ≈500 мс, без
кастомного timeout). Меню (`DropdownMenu`) SHALL располагаться в
точке нажатия (офсет внутри корня списка — паттерн `MainScreen`).
Пока меню открыто, строка SHALL подсвечиваться (цвет через
`animateColorAsState`, семантика `selected` — паттерн
`EntryListItemRow`); закрытие меню (тап вне меню, выбор пункта,
системный back) снимает подсветку.

Дополнительно к жесту каждая строка каталога SHALL предоставлять
локализованное accessibility-действие «Открыть меню» / "Open menu":
активация действия из меню действий скринридера SHALL открывать то
же контекстное меню (состав пунктов — по типу упражнения, как при
долгом нажатии). При открытии accessibility-действием позиция меню
по умолчанию (центр строки) SHALL допускаться.

Тап «Изменить» (только для кастомных) SHALL инициировать навигацию
на `Screen.ExerciseCreate.createRoute(exerciseId)` (маршрут —
capability `non-tab-screen-navigation`, поведение edit-режима —
capability `exercise-create-screen`). Тап «Архивировать» SHALL
закрывать меню и открывать алерт подтверждения архивации
(требование «Алерт подтверждения архивации» ниже). Тап «Удалить»
(только для кастомных) SHALL закрывать меню и открывать алерт
подтверждения удаления (требование «Алерт подтверждения удаления
упражнения» — без изменений).

#### Scenario: Long-press кастомного открывает меню с тремя пунктами

- **WHEN** пользователь делает долгое нажатие на строке упражнения с
  `isCustom = true`
- **THEN** в точке нажатия открывается меню с пунктами «Изменить»,
  «Архивировать» и «Удалить» в указанном порядке
- **AND** строка подсвечена, пока меню открыто

#### Scenario: Long-press стандартного открывает меню с одним пунктом

- **WHEN** пользователь делает долгое нажатие на строке упражнения с
  `isCustom = false`
- **THEN** в точке нажатия открывается меню с единственным пунктом
  «Архивировать»
- **AND** пункты «Изменить» и «Удалить» НЕ показываются
- **AND** строка подсвечена, пока меню открыто

#### Scenario: Accessibility-действие строки открывает меню по типу упражнения

- **WHEN** пользователь скринридера активирует на строке упражнения
  accessibility-действие «Открыть меню»
- **THEN** открывается то же контекстное меню с составом пунктов по
  типу упражнения (три пункта у кастомного, «Архивировать» у
  стандартного)
- **AND** выбор пунктов меню работает как при открытии долгим
  нажатием

#### Scenario: Тап «Изменить» навигирует на edit-экран

- **WHEN** открыто меню кастомного упражнения с id = 7 AND пользователь
  тапает «Изменить»
- **THEN** меню закрывается
- **AND** выполняется навигация на
  `Screen.ExerciseCreate.createRoute(7)`

#### Scenario: Тап «Архивировать» открывает алерт архивации

- **WHEN** открыто меню упражнения «Жим» (кастомного или стандартного)
  AND пользователь тапает «Архивировать»
- **THEN** меню закрывается
- **AND** открывается алерт подтверждения архивации с заголовком
  «Архивировать упражнение?» и текстом с именем упражнения

#### Scenario: Закрытие меню снимает подсветку строки

- **WHEN** меню открыто AND пользователь тапает вне меню (или выбирает
  пункт, или нажимает системный back)
- **THEN** меню закрывается
- **AND** подсветка строки снята

### Requirement: Алерт подтверждения удаления упражнения

Тап «Удалить» в контекстном меню SHALL открывать `AlertDialog` с
заголовком `exercise_delete_dialog_title` («Удалить упражнение?» /
"Delete exercise?"), текстом `exercise_delete_dialog_text`
(«Упражнение "%1$s" будет удалено» / "Exercise \"%1$s\" will be
deleted", подставляется `name` упражнения), кнопкой отмены
`exercise_delete_dialog_cancel` («Отмена» / "Cancel") и кнопкой
подтверждения `exercise_delete_dialog_confirm` («Удалить» / "Delete") в
`colorScheme.error` (паттерн `EntryDeleteDialog`).

Подтверждение SHALL вызывать `confirmDelete()`, который вызывает
`repository.deleteExercise(id)`. При `Result.success` pending
сбрасывается, алерт закрыт, список обновляется реактивно (Flow
каталога). Отмена (`cancelDelete()`) SHALL закрыть алерт, не изменяя
каталог.

#### Scenario: Тап «Удалить» открывает алерт с именем упражнения

- **WHEN** открыто меню кастомного упражнения «Жим» AND пользователь
  тапает «Удалить»
- **THEN** виден алерт с заголовком «Удалить упражнение?» и текстом
  «Упражнение "Жим" будет удалено»
- **AND** строки заголовка и кнопок локализованы (ru/en)

#### Scenario: Подтверждение удаляет упражнение и закрывает алерт

- **WHEN** алерт открыт AND `repository.deleteExercise` возвращает
  `Result.success` AND пользователь тапает «Удалить»
- **THEN** алерт закрыт, `pendingDeleteId == null`
- **AND** упражнение отсутствует в списке (реактивное обновление)

#### Scenario: Отмена не изменяет каталог

- **WHEN** алерт открыт AND пользователь тапает «Отмена»
- **THEN** алерт закрыт без вызова `deleteExercise`
- **AND** упражнение остаётся в списке

### Requirement: Ошибка удаления — типизированные причины в снекбаре

`ExercisesUiState.Success` SHALL содержать `deleteError:
DeleteExerciseError? = null` (sealed-тип из `domain/model/` с
вариантами `HasLinkedEntries`, `StandardExercise`,
`Other`). При `confirmDelete()` репозиторий может
вернуть:

- `Result.failure(IllegalStateException("Упражнение не найдено"))`
  (`getById(id) == null`, race между сбором списка и подтверждением
  алерта) → `deleteError = DeleteExerciseError.Other` плюс `Log.e`
  для диагностики. Конкретный
  текст «Упражнение не найдено» сохраняется в логе, пользователь
  видит generic снекбар «Не удалось удалить упражнение»;
- `Result.failure(LinkedEntriesDeletionException)` →
  `deleteError = DeleteExerciseError.HasLinkedEntries`;
- `Result.failure(StandardExerciseDeletionException)` →
  `deleteError = DeleteExerciseError.StandardExercise`;
- `Result.failure(прочее)` → `deleteError = DeleteExerciseError.Other`
  плюс `Log.e` для диагностики.

UI SHALL маппить `deleteError` в `Snackbar`-сообщение через `when`:
- `HasLinkedEntries` → `R.string.exercise_delete_error_used_in_workouts`
  («Это упражнение уже используется в тренировках» / "This exercise is
  used in workouts");
- `StandardExercise` → `R.string.exercise_delete_error_standard`
  («Нельзя удалить стандартное упражнение» / "Cannot delete a standard
  exercise");
- `Other` → `R.string.exercise_delete_error_generic`
  («Не удалось удалить упражнение» / "Failed to delete exercise").

`onErrorShown()` SHALL сбрасывать `deleteError = null` (one-shot —
паттерн «одноразовые события» AGENTS.md). Парсинг текстов исключений
в UI SHALL NOT выполняться (маппинг — в VM, на уровне логики, покрыт
unit-тестами).

#### Scenario: FK-ошибка — снекбар с указанием причины

- **WHEN** подтверждено удаление кастомного упражнения, у которого есть
  записи, AND `repository.deleteExercise` возвращает
  `Result.failure(LinkedEntriesDeletionException)`
- **THEN** алерт закрыт
- **AND** упражнение остаётся в списке
- **AND** показан снекбар «Это упражнение уже используется в
  тренировках»
- **AND** сообщение исключения записано в logcat через `Log.e`

#### Scenario: Стандартное упражнение — снекбар с указанием причины

- **WHEN** `repository.deleteExercise` возвращает
  `Result.failure(StandardExerciseDeletionException)` (защитный случай;
  из UI недостижимо — Decision 4 не показывает меню для стандартных)
- **THEN** алерт закрыт (если был открыт)
- **AND** упражнение остаётся в списке
- **AND** показан снекбар «Нельзя удалить стандартное упражнение»

#### Scenario: Прочие ошибки — generic снекбар

- **WHEN** `repository.deleteExercise` возвращает `Result.failure` с
  исключением, не относящимся к `LinkedEntriesDeletionException` или
  `StandardExerciseDeletionException` (например, SQLite I/O)
- **THEN** алерт закрыт
- **AND** упражнение остаётся в списке
- **AND** показан снекбар «Не удалось удалить упражнение»
- **AND** сообщение исключения записано в logcat через `Log.e`

#### Scenario: Успешное удаление не показывает снекбар

- **WHEN** `deleteExercise` возвращает `Result.success`
- **THEN** снекбар не показывается

#### Scenario: Снекбар показан — флаг сбрасывается через onErrorShown

- **WHEN** показан снекбар (после `Result.failure`) AND пользователь
  тапает на снекбаре или снекбар скрылся по таймауту
- **THEN** экран вызывает `viewModel.onErrorShown()`
- **AND** `UiState.deleteError == null` (one-shot-семантика — повторный
  запуск подтверждения удаления возможен без ложного снекбара)

### Requirement: Предварительная проверка FK — Snackbar без диалога

`ExercisesViewModel` SHALL подписываться на
`repository.exerciseIdsWithLinkedEntries(): Flow<Set<Long>>` —
реактивный запрос, определяющий какие упражнения имеют записи. В
`requestDelete(item: Exercise)`:
- если `item.id in exerciseIdsWithLinkedEntries.value` — VM SHALL
  устанавливать `deleteError = DeleteExerciseError.HasLinkedEntries`,
  диалог подтверждения НЕ показывается, `pendingDeleteId` остаётся
  `null`, снекбар показывается сразу;
- иначе — VM SHALL устанавливать `pendingDeleteId = item.id` (диалог
  показывается, требуется подтверждение).

UI `ExercisesScreen` SHALL при `state.uiState.pendingDeleteId == null`
AND `state.uiState.deleteError != null` отображать снекбар (через
`LaunchedEffect(uiState)`, перед показом закрывая текущий снекбар) с
текстом по `deleteError` (маппинг —
требование «Ошибка удаления — типизированные причины в снекбаре»).

Защита от race: между pre-check и реальным `confirmDelete → repository
.deleteExercise` пользователь мог добавить запись на другой
вкладке/сессии. В этом случае repository вернёт
`LinkedEntriesDeletionException` (safety net Decision 3) и Snackbar всё
равно покажется после confirm с тем же текстом.

#### Scenario: Тап «Удалить» на кастомном с записями — Snackbar без диалога

- **WHEN** открыто меню кастомного упражнения «Жим», на которое есть
  записи (`item.id in exerciseIdsWithLinkedEntries.value`), AND
  пользователь тапает «Удалить»
- **THEN** меню закрывается
- **AND** диалог подтверждения НЕ показывается (`pendingDeleteId` остаётся
  `null`)
- **AND** сразу показан снекбар «Это упражнение уже используется в
  тренировках»
- **AND** упражнение остаётся в списке

#### Scenario: Тап «Удалить» на кастомном без записей — диалог как раньше

- **WHEN** открыто меню кастомного упражнения «Жим», на которое НЕТ
  записей, AND пользователь тапает «Удалить»
- **THEN** меню закрывается
- **AND** `pendingDeleteId == item.id`
- **AND** показан диалог подтверждения «Удалить упражнение?» с именем
  упражнения
### Requirement: Алерт подтверждения архивации

Тап «Архивировать» в контекстном меню при `dontShowArchiveDialog =
false` SHALL открывать `AlertDialog` с
заголовком `exercise_archive_dialog_title` («Архивировать упражнение?» /
"Archive exercise?"), текстом `exercise_archive_dialog_text`
с формальной подстановкой имени упражнения
(«Чтобы снова использовать упражнение "%1$s" в тренировках, нужно
будет вернуть его из архива» / "To use \"%1$s\" again in workouts,
restore it from the archive"), кнопкой отмены
`exercise_archive_dialog_cancel` («Отмена» / "Cancel") и кнопкой
подтверждения `exercise_archive_dialog_confirm` («Архивировать» /
"Archive") в дефолтном цвете (`ButtonDefaults.textButtonColors()`,
БЕЗ `colorScheme.error` — в отличие от диалога удаления, который
использует тот же `ExerciseConfirmDialog` с
`confirmInError = true`).
Архивация — обратимая операция (есть восстановление через экран
архива), обычный цвет подтверждения. Bypass-ветка при
`dontShowArchiveDialog = true` описана в абзаце про `requestArchive`
ниже.

Подтверждение SHALL вызывать `confirmArchive(suppress: Boolean =
false)`, который вызывает `repository.setArchived(id, true)`. При
`Result.success` pending сбрасывается, алерт закрыт, упражнение
пропадает из списка (реактивный Flow каталога); при `Result.failure`
pending также сбрасывается (race: упражнение удалено/изменено между
открытием меню и подтверждением — см. сценарий ниже), каталог не
изменён. Отмена (`cancelArchive()`) SHALL закрыть алерт, не
изменяя каталог.

Перед открытием алерта `requestArchive(item: Exercise)` SHALL
прочитать `dataStore.dontShowArchiveDialog.first()`; если значение
равно `true`, VM SHALL немедленно вызвать
`repository.setArchived(item.id, true)`, НЕ устанавливая
`pendingArchiveId` и НЕ показывая алерт. В противном случае VM SHALL
установить `pendingArchiveId = item.id` (текущее поведение — алерт
показывается, требуется подтверждение).

Если в открытом алерте пользователь поставил галочку «Больше не
показывать» (`R.string.exercise_archive_dont_show_again`) и подтвердил архивацию,
`confirmArchive` SHALL дополнительно вызвать
`dataStore.setDontShowArchiveDialog(true)`. Чекбокс в диалоге
архивации SHALL отображаться между текстом диалога и кнопками;
изначально — снят. Лейбл «Больше не показывать» SHALL быть
кликабельным: тап по лейблу SHALL переключать состояние чекбокса так
же, как тап по самому чекбоксу (область клика строки — чекбокс и
лейбл вместе). Реализационные детали размещения `remember`
(scope, замыкание на `onConfirm`, неизменность сигнатуры
`onConfirm: () -> Unit`, отсутствие `suppress` в `ExercisesUiState`)
зафиксированы в `design.md` решения 4 change'а
`archive-alert-hide` (`openspec/changes/archive/2026-09-16-archive-alert-hide/design.md`) —
единый канон.

`ExerciseConfirmDialog` SHALL принимать опциональный параметр
`suppressContent: (@Composable () -> Unit)? = null`. Когда
`suppressContent == null` — диалог рендерится как сейчас (используется
для удаления). Когда `suppressContent != null` — слот отрисовывается
между текстом диалога и кнопками.

#### Scenario: Тап «Архивировать» открывает алерт с именем упражнения

- **WHEN** открыто меню упражнения «Жим» AND пользователь тапает
  «Архивировать»
- **THEN** виден алерт с заголовком «Архивировать упражнение?» и
  текстом «Чтобы снова использовать упражнение "Жим" в тренировках,
  нужно будет вернуть его из архива»
- **AND** строки заголовка, текста и кнопок локализованы (ru/en)

#### Scenario: В открытом алерте виден чекбокс «Больше не показывать»

- **WHEN** алерт архивации открыт
- **THEN** между текстом алерта и кнопками отображается `Checkbox`
  в `Row` с лейблом «Больше не показывать» / "Don't show again"
- **AND** изначально чекбокс снят (`checked = false`)

#### Scenario: Тап по лейблу переключает чекбокс

- **WHEN** алерт архивации открыт AND чекбокс снят AND пользователь
  тапает по лейблу «Больше не показывать»
- **THEN** чекбокс становится поставленным (`checked = true`)
- **AND** повторный тап по лейблу снимает чекбокс
- **AND** переключение лейблом эквивалентно переключению самим
  чекбоксом (подтверждение с поставленным лейблом пишет pref так же,
  как сценарий «confirmArchive с suppress=true»)

#### Scenario: Подтверждение архивирует упражнение и закрывает алерт

- **WHEN** алерт открыт AND `repository.setArchived(id, true)`
  возвращает `Result.success` AND пользователь тапает «Архивировать»
- **THEN** алерт закрыт, `pendingArchiveId == null`
- **AND** упражнение отсутствует в списке (реактивное обновление)
- **AND** упражнение появляется в списке на экране архива

#### Scenario: Отмена не изменяет каталог

- **WHEN** алерт открыт AND пользователь тапает «Отмена»
- **THEN** алерт закрыт без вызова `setArchived`
- **AND** упражнение остаётся в списке

#### Scenario: Подтверждение архивации стандартного упражнения

- **WHEN** алерт открыт для стандартного упражнения «Бег» AND
  пользователь тапает «Архивировать»
- **THEN** `setArchived(id, true)` возвращает `Result.success`
- **AND** упражнение пропадает из активного каталога и появляется
  в списке архива

#### Scenario: Failure при подтверждении — алерт закрыт, каталог без изменений

- **WHEN** алерт открыт AND `repository.setArchived(id, true)`
  возвращает `Result.failure(IllegalStateException("Упражнение не
  найдено"))` (race: упражнение удалено/изменено между открытием
  меню и подтверждением) AND пользователь тапает «Архивировать»
- **THEN** алерт закрыт, `pendingArchiveId == null`
- **AND** каталог не изменён (упражнение остаётся в активном
  списке — `setArchived` вернул `failure`, запись в БД не выполнена)
- **AND** повторный `requestArchive(item)` возможен (race
  разрешается на повторном подтверждении, когда упражнение снова
  доступно)
- **AND** пользователю НЕ показывается сообщение об ошибке
  (согласуется с «без Error-ветки в UiState» — `confirmArchive`
  молча сбрасывает pending; в отличие от `confirmDelete`, который
  при failure выставляет `deleteError` и показывает снекбар, здесь
  снекбар неуместен: операция обратима, failure — редкий race,
  повторный `requestArchive` достаточен)
- **AND** при failure `dontShowArchiveDialog` НЕ откатывается —
  решение «скрыть навсегда» сохраняется

#### Scenario: confirmArchive с suppress=true пишет pref

- **WHEN** алерт открыт AND чекбокс «Больше не показывать» поставлен
  AND пользователь тапает «Архивировать»
- **THEN** `confirmArchive(suppress = true)` вызывает
  `repository.setArchived(id, true)` И
  `dataStore.setDontShowArchiveDialog(true)` ровно по одному разу
- **AND** последующее чтение `dataStore.dontShowArchiveDialog.first()`
  возвращает `true`

#### Scenario: confirmArchive с suppress=false НЕ пишет pref

- **WHEN** алерт открыт AND чекбокс «Больше не показывать» снят
  AND пользователь тапает «Архивировать»
- **THEN** `confirmArchive(suppress = false)` вызывает
  `repository.setArchived(id, true)`
- **AND** `dataStore.setDontShowArchiveDialog` НЕ вызывается
- **AND** последующее чтение `dataStore.dontShowArchiveDialog.first()`
  возвращает прежнее значение (по умолчанию `false`)

#### Scenario: requestArchive при dontShowArchiveDialog=true архивирует без диалога

- **WHEN** `dataStore.dontShowArchiveDialog.first()` возвращает `true`
  AND пользователь тапает «Архивировать» в контекстном меню
- **THEN** `repository.setArchived(item.id, true)` вызывается
  немедленно
- **AND** `pendingArchiveId` остаётся `null` (алерт НЕ показывается)
- **AND** `confirmArchive` НЕ вызывается
- **AND** упражнение пропадает из активного списка и появляется
  в архиве

#### Scenario: requestArchive при dontShowArchiveDialog=false показывает диалог

- **WHEN** `dataStore.dontShowArchiveDialog.first()` возвращает `false`
  (по умолчанию или после opt-in «снова показывать»)
  AND пользователь тапает «Архивировать» в контекстном меню
- **THEN** VM устанавливает `pendingArchiveId = item.id`
- **AND** `repository.setArchived` НЕ вызывается до явного
  подтверждения пользователем

#### Scenario: Обратная совместимость — старая установка без pref

- **WHEN** пользователь обновил приложение с предыдущей версии
  AND pref `dontShowArchiveDialog` отсутствует в DataStore
- **THEN** `dataStore.dontShowArchiveDialog.first()` возвращает
  default `false`
- **AND** алерт архивации показывается как раньше (поведение
  полностью обратно совместимо)

### Requirement: Кнопка архива в TopAppBar

В `actions` слоте `TopAppBar` `ExercisesScreen` SHALL показываться
`IconButton` с иконкой `Icons.Filled.Archive` и
`contentDescription = R.string.archive_title`
(«Архив» / "Archive" — общий ключ с заголовком экрана архива; экран по
этой дельте объединяет архивы упражнений и категорий, отдельный ключ
`exercises_archive_button` («Архив упражнений») удаляется из строковых
ресурсов), ЕСЛИ в архиве есть хотя бы
один объект любого типа: `hasArchived == true` (реактивный признак из
репозитория; объединяет оба типа объектов). Если архив пуст
(`hasArchived == false`) — кнопка скрыта. Тап по кнопке SHALL инициировать навигацию на
`Screen.ArchivedExercises.route` через колбэк `onArchivedExercisesClick`,
прокидываемый из `RootScreen`. Видимость кнопки SHALL быть реактивной:
при архивации первого объекта кнопка появляется, при восстановлении
последнего — исчезает. Общий ключ `archive_title` в contentDescription —
намеренно: имя кнопки совпадает с заголовком экрана назначения (паттерн
«кнопка → экран» для TalkBack), отдельный accessibility-ключ не вводится.

#### Scenario: Архив пуст — кнопка скрыта

- **WHEN** `hasArchived == false`
- **THEN** `TopAppBar.actions` НЕ содержит `IconButton` с иконкой Archive

#### Scenario: Архив не пуст — кнопка отображается

- **WHEN** `hasArchived == true` (например, 1 заархивированное упражнение)
- **THEN** `TopAppBar.actions` содержит `IconButton` с иконкой
  `Icons.Filled.Archive` и `contentDescription = archive_title` («Архив»)

#### Scenario: Архивны только категории — кнопка отображается

- **WHEN** в архиве только 2 категории (упражнений нет) AND
  `hasArchived == true`
- **THEN** `TopAppBar.actions` содержит `IconButton` с иконкой Archive

#### Scenario: Тап кнопки архива навигирует на ArchivedExercisesScreen

- **WHEN** архив непуст AND пользователь тапает кнопку архива в `TopAppBar`
- **THEN** `onArchivedExercisesClick` вызывается ровно один раз
- **AND** активным становится `Screen.ArchivedExercises.route`

#### Scenario: Реактивное появление кнопки при архивации

- **WHEN** архив пуст AND пользователь архивирует первый объект
  (упражнение или категорию)
- **THEN** после закрытия алерта `hasArchived` становится `true`
- **AND** `IconButton` с иконкой Archive появляется в `TopAppBar.actions`

#### Scenario: Реактивное исчезновение кнопки при восстановлении

- **WHEN** восстановлен последний архивный объект AND `hasArchived`
  становится `false`
- **THEN** `IconButton` с иконкой Archive скрывается из `TopAppBar.actions`

### Requirement: Сигнатура ExercisesScreen принимает колбэк архива

`ExercisesScreen` SHALL принимать параметр
`onArchivedExercisesClick: () -> Unit` (без значения по умолчанию,
обязательный) и пробрасывать его в
`ExercisesScreenState.onArchivedExercisesClick`. Полная сигнатура SHALL
быть: `@Composable fun ExercisesScreen(viewModel: ExercisesViewModel,
onAddExerciseClick: () -> Unit, onAddCategoryClick: () -> Unit,
onArchivedExercisesClick: () -> Unit, analyticsProvider: AnalyticsProvider,
onEditExercise: (Long) -> Unit = {}, onEditCategory: (Long) -> Unit = {},
modifier: Modifier = Modifier)` — параметры без значений по умолчанию
(включая существующий `analyticsProvider` — 9.1 его не меняет) стоят до
параметров с ними, порядок соответствует конвенции экранов с
`modifier` в конце (`MoreScreen`, `OverviewScreen`,
`ArchivedExercisesScreen`).
Каждый колбэк SHALL быть единственным каналом своей навигации — экран не
знает про NavController: `onArchivedExercisesClick` →
`Screen.ArchivedExercises.route`, `onAddCategoryClick` →
`Screen.CategoryCreate.createRoute()` (create-режим), `onEditCategory` →
`Screen.CategoryCreate.createRoute(categoryId)` (edit-режим),
`onEditExercise` → `Screen.ExerciseCreate.createRoute(exerciseId)`
(существующий колбэк меню «Изменить» упражнения — без изменений). В
`RootScreen` колбэки SHALL передаваться через `navController.navigate(...)`
(паттерн — существующая прокидка `onAddExerciseClick`). Колбэки
`onAddCategoryClick` / `onEditCategory` / `onArchivedExercisesClick` SHALL
пробрасываться в соответствующие поля `ExercisesScreenState`.

#### Scenario: ExercisesScreen принимает onArchivedExercisesClick

- **WHEN** `ExercisesScreen` is referenced
- **THEN** его сигнатура включает обязательный параметр
  `onArchivedExercisesClick: () -> Unit`
- **AND** параметр пробрасывается в
  `ExercisesScreenState.onArchivedExercisesClick`

#### Scenario: Тап кнопки архива вызывает прокинутый колбэк

- **WHEN** архив непуст AND пользователь тапает `IconButton` с
  иконкой `Icons.Filled.Archive` в `TopAppBar`
- **THEN** вызывается `ExercisesScreenState.onArchivedExercisesClick`
  (а через него — `RootScreen` → `navController.navigate(
  Screen.ArchivedExercises.route)`)

### Requirement: ExercisesViewModel factory принимает AppSettingsDataStore

`ExercisesViewModel(repository: WorkoutDataRepository, dataStore:
AppSettingsDataStore) : ViewModel` SHALL принимать `dataStore` вторым
параметром конструктора и использовать его для чтения и записи
`dontShowArchiveDialog` (поведение — требование «Алерт подтверждения
архивации»). Companion SHALL предоставлять
`fun factory(repository: WorkoutDataRepository, dataStore:
AppSettingsDataStore): ViewModelProvider.Factory` через
`viewModelFactory { initializer { ExercisesViewModel(repository,
dataStore) } }`. `RootScreen` SHALL пробрасывать тот же `dataStore`
в фабрику `ExercisesViewModel` при композиции экрана упражнений.
Требование «ровно один экземпляр `AppSettingsDataStore` на
процесс, создаётся в `MyWorkoutsApplication` (lazy)» — канон в
capability `appearance` (MODIFIED-требование этого change в
`openspec/changes/archive-alert-hide/specs/appearance/spec.md`;
синхронизируется через `openspec sync-specs` при архивации).

#### Scenario: VM получает dataStore через factory

- **WHEN** `ExercisesViewModel.factory(repository, dataStore)` используется
  в `viewModel<ExercisesViewModel>(factory = ...)`
- **THEN** сконструированный `ExercisesViewModel` использует тот же
  `dataStore` для операций `requestArchive` / `confirmArchive`

### Requirement: Локализация строки «Больше не показывать»

Строки `exercise_archive_dont_show_again` SHALL существовать в
`res/values/strings.xml` («Больше не показывать») и
`res/values-en/strings.xml` ("Don't show again"). Лейбл используется
в слоте `suppressContent` диалога архивации (требование «Алерт
подтверждения архивации») и НЕ применяется в диалоге удаления
(`ExerciseConfirmDialog` для удаления вызывается с
`suppressContent = null`).

#### Scenario: Локализованный лейбл чекбокса в диалоге архивации (ru)

- **WHEN** открыт алерт архивации AND активна русская локаль
- **THEN** рядом с чекбоксом отображается текст «Больше не
  показывать»

#### Scenario: Локализованный лейбл чекбокса в диалоге архивации (en)

- **WHEN** открыт алерт архивации AND активна английская локаль
- **THEN** рядом с чекбоксом отображается текст "Don't show again"

### Requirement: Табы «Упражнения» / «Категории»

Экран упражнений SHALL отображать под `TopAppBar` компонент `SecondaryTabRow`
с двумя вкладками: «Упражнения» (`R.string.exercises_tab_exercises`) и
«Категории» (`R.string.exercises_tab_categories`), локализованными ru/en.
Переключение вкладки SHALL менять содержимое тела экрана без смены экрана
(`when`-свитч по выбранной вкладке — паттерн `OverviewScreen`). Выбранная
вкладка SHALL храниться в `ExercisesViewModel` (переживает поворот экрана,
не персистится между запусками приложения). Заголовок `TopAppBar` остаётся
«Упражнения» / "Exercises" независимо от вкладки.

#### Scenario: Переключение вкладок меняет содержимое

- **WHEN** пользователь выбирает вкладку «Категории»
- **THEN** тело экрана отображает список категорий вместо списка упражнений
- **AND** `TopAppBar` показывает «Упражнения», нижняя навигация не меняется

#### Scenario: Выбранная вкладка переживает поворот

- **WHEN** выбрана вкладка «Категории» AND устройство поворачивается
- **THEN** после поворота активна вкладка «Категории»

### Requirement: Вкладка «Категории» — список и пустое состояние

Вкладка «Категории» SHALL отображать `LazyColumn` с `key = { it.id }` всех
активных категорий (`categories()` репозитория — стандартные и кастомные,
сортировка `is_custom DESC, created_at DESC, name COLLATE NOCASE`).
При пустом списке вкладка SHALL показывать текст
`R.string.categories_list_empty` («Нет категорий» / "No categories") по
центру body — паттерн `exercises_list_empty` (достижимо, когда все
стандартные категории заархивированы и кастомных нет).

#### Scenario: Список категорий

- **WHEN** на вкладке «Категории» есть 5 стандартных и 1 кастомная категория
- **THEN** отображаются 6 строк, кастомная — выше стандартных

#### Scenario: Все категории заархивированы — заглушка

- **WHEN** список активных категорий пуст
- **THEN** по центру вкладки отображается «Нет категорий» / "No categories"

### Requirement: Строка списка категорий

Строка списка категорий SHALL отображать название категории (`bodyLarge`,
`FontWeight.Medium`) и цветовой индикатор — небольшой маркер формы круга
цветом `category.colorArgb` слева от названия (24dp, без описания
доступности — название строки уже озвучивается). Отображение имени SHALL резолвиться через file-level хелпер
`standardCategoryLabelResId(name: String): Int?` (`ui/util`, design
Decision 2): для стандартных категорий — строковые ресурсы
`exercise_category_*` по имени-ключу, для кастомных (`null`) — по
`name` как есть.

#### Scenario: Строка с цветовым индикатором

- **WHEN** список отображает категорию «Плечи» с `colorArgb = 0xFF4E6EF2`
- **THEN** слева от названия виден круглый маркер этого цвета

#### Scenario: Стандартная категория локализована

- **WHEN** список отображает стандартную категорию `PUSH` при русской локали
- **THEN** строка показывает «Толкающие» (через `exercise_category_push`)
- **AND** при английской локали — "Push"

### Requirement: Контекстное меню категории по типу

Долгое нажатие на строке категории SHALL открывать контекстное меню по
образцу контекстного меню упражнений (жест `detectTapGestures(onLongPress)`,
`DropdownMenu` в точке нажатия, подсветка строки на время открытия,
accessibility-действие «Открыть меню»). Для кастомной категории
(`isCustom = true`) меню SHALL содержать три пункта в порядке: «Изменить»
(`category_list_menu_edit`), «Архивировать» (`category_list_menu_archive`),
«Удалить» (`category_list_menu_delete`). Для стандартной категории
(`isCustom = false`) меню SHALL содержать ровно один пункт «Архивировать».
Тап «Изменить» (только кастомные) SHALL навигировать на
`Screen.CategoryCreate.createRoute(categoryId)`; тап «Архивировать» —
открывать алерт архивации категории (требование «Алерт архивации категории
с чекбоксом подавления»); тап «Удалить» (только кастомные) — проходить
пре-чек и открывать алерт удаления (требование «Алерт удаления категории
и пре-чек использования»).

Пункты «Архивировать» и «Удалить» SHALL скрываться, если категория —
единственная активная (остальные в архиве или удалены); пункт
«Изменить» (кастомные) остаётся. Если после скрытия пунктов не
остаётся (последняя активная — стандартная категория), долгое нажатие
и accessibility-действие SHALL NOT открывать меню. Видимость
вычисляется из `ExercisesUiState.Success.categories` — полном
нефильтрованном списке активных категорий (формула «размер == 1»
корректна только на полном каталоге).

#### Scenario: Long-press кастомной категории — три пункта

- **WHEN** пользователь делает долгое нажатие на строке кастомной
  категории, когда есть и другие активные категории
- **THEN** открывается меню с пунктами «Изменить», «Архивировать», «Удалить»
  в указанном порядке

#### Scenario: Long-press стандартной категории — один пункт

- **WHEN** пользователь делает долгое нажатие на строке стандартной
  категории, когда есть и другие активные категории
- **THEN** открывается меню с единственным пунктом «Архивировать»

#### Scenario: Accessibility-действие строки категории открывает меню по типу

- **WHEN** пользователь скринридера активирует на строке категории
  accessibility-действие «Открыть меню», когда есть и другие активные
  категории
- **THEN** открывается то же контекстное меню с составом пунктов по
  типу категории (три пункта у кастомной, «Архивировать» у стандартной)
- **AND** выбор пунктов меню работает как при открытии долгим нажатием

#### Scenario: Тап «Изменить» навигирует на edit-экран

- **WHEN** открыто меню кастомной категории с id = 3 AND пользователь тапает
  «Изменить»
- **THEN** меню закрывается AND выполняется навигация на
  `Screen.CategoryCreate.createRoute(3)`

#### Scenario: У последней активной кастомной категории — только «Изменить»

- **WHEN** кастомная категория — единственная активная AND пользователь
  делает долгое нажатие на её строке
- **THEN** меню открывается с единственным пунктом «Изменить»
- **AND** пункты «Архивировать» и «Удалить» НЕ показываются

#### Scenario: У последней активной стандартной категории меню не открывается

- **WHEN** стандартная категория — единственная активная
- **THEN** долгое нажатие на её строке не открывает меню
- **AND** accessibility-действие «Открыть меню» также не открывает меню


## Requirement: Контекстное меню категории по типу

Долгое нажатие на строке категории SHALL открывать контекстное меню по
образцу контекстного меню упражнений (жест `detectTapGestures(onLongPress)`,
`DropdownMenu` в точке нажатия, подсветка строки на время открытия,
accessibility-действие «Открыть меню»). Для кастомной категории
(`isCustom = true`) меню SHALL содержать три пункта в порядке: «Изменить»
(`category_list_menu_edit`), «Архивировать» (`category_list_menu_archive`),
«Удалить» (`category_list_menu_delete`). Для стандартной категории
(`isCustom = false`) меню SHALL содержать ровно один пункт «Архивировать».
Тап «Изменить» (только кастомные) SHALL навигировать на
`Screen.CategoryCreate.createRoute(categoryId)`; тап «Архивировать» —
открывать алерт архивации категории (требование «Алерт архивации категории
с чекбоксом подавления»); тап «Удалить» (только кастомные) — проходить
пре-чек и открывать алерт удаления (требование «Алерт удаления категории
и пре-чек использования»).

#### Scenario: Long-press кастомной категории — три пункта

- **WHEN** пользователь делает долгое нажатие на строке кастомной категории
- **THEN** открывается меню с пунктами «Изменить», «Архивировать», «Удалить»
  в указанном порядке

#### Scenario: Long-press стандартной категории — один пункт

- **WHEN** пользователь делает долгое нажатие на строке стандартной категории
- **THEN** открывается меню с единственным пунктом «Архивировать»

#### Scenario: Тап «Изменить» навигирует на edit-экран

- **WHEN** открыто меню кастомной категории с id = 3 AND пользователь тапает
  «Изменить»
- **THEN** меню закрывается AND выполняется навигация на
  `Screen.CategoryCreate.createRoute(3)`

### Requirement: Алерт удаления категории и пре-чек использования

Тап «Удалить» в меню категории SHALL проходить предварительную проверку:
если имя категории (`Category` уже несёт `name` из строки списка)
входит в `categoryNamesWithLinkedExercises()` (на неё ссылается хотя
бы одно упражнение — активное или архивное), алерт НЕ открывается,
сразу показывается снекбар
`R.string.category_delete_error_used_by_exercises` («Эта категория уже
используется в упражнении» / "This category is used by an exercise").
Иначе открывается алерт подтверждения — переиспользовать
`ExerciseConfirmDialog` с текстами для категорий: заголовок
`category_delete_dialog_title` («Удалить категорию?» / "Delete category?"),
текст `category_delete_dialog_text` («Категория "%1$s" будет удалена» /
"Category \"%1$s\" will be deleted"; имя берётся из списка по
`pendingCategoryDeleteId` — паттерн `ExercisesScreen.kt:348`), кнопки
«Отмена» / «Удалить» (`confirmInError = true`, паттерн алерта удаления
упражнения). Подтверждение SHALL вызывать `confirmCategoryDelete()` →
`deleteCategory(pendingCategoryDeleteId)`; при
`Result.success` pending сбрасывается, список обновляется реактивно; при
`Result.failure` устанавливается `categoryDeleteError` (снекбар по
требованию «Ошибки удаления категории — типизированные причины»).

#### Scenario: Используемая категория — снекбар без алерта

- **WHEN** открыто меню категории, на которую ссылается упражнение AND
  пользователь тапает «Удалить»
- **THEN** диалог НЕ показывается
- **AND** сразу показан снекбар «Эта категория уже используется в
  упражнении»
- **AND** категория остаётся в списке

#### Scenario: Неиспользуемая категория — алерт с именем

- **WHEN** категория «Плечи» не используется AND пользователь тапает
  «Удалить»
- **THEN** показан алерт с текстом «Категория "Плечи" будет удалена»

#### Scenario: Подтверждение удаляет категорию

- **WHEN** алерт открыт AND удаление возвращает `Result.success` AND
  пользователь тапает «Удалить»
- **THEN** алерт закрыт AND категория исчезает из списка реактивно

### Requirement: Ошибки удаления категории — типизированные причины в снекбаре

`ExercisesUiState.Success` SHALL содержать `categoryDeleteError:
DeleteCategoryError? = null` (sealed-тип из `domain/model/` с вариантами
`UsedByExercises`, `StandardCategory`, `Other` — контракт спеки
`category-data`). UI SHALL маппить ошибку в снекбар через `when`:
`UsedByExercises` → `category_delete_error_used_by_exercises`
(«Эта категория уже используется в упражнении»), `StandardCategory` →
`category_delete_error_standard` («Нельзя удалить стандартную категорию» /
"Cannot delete a standard category"), `Other` → `category_delete_error_generic`
(«Не удалось удалить категорию» / "Failed to delete category"). Прочие
ошибки репозитория дополнительно логируются через `Log.e`. Парсинг текстов
исключений в UI SHALL NOT выполняться (маппинг — в VM). `onCategoryErrorShown()`
SHALL сбрасывать `categoryDeleteError = null` (one-shot).

#### Scenario: Safety-net-ошибка — снекбар с указанием причины

- **WHEN** подтверждено удаление категории с упражнениями AND репозиторий
  возвращает `UsedByExercises` (safety net после пре-чека)
- **THEN** показан снекбар «Эта категория уже используется в упражнении»

#### Scenario: One-shot сброс ошибки

- **WHEN** снекбар показан AND экран вызывает `onCategoryErrorShown()`
- **THEN** `categoryDeleteError == null`

### Requirement: Алерт архивации категории с чекбоксом подавления

Тап «Архивировать» в меню категории при `dontShowCategoryArchiveDialog =
false` SHALL открывать алерт, целиком переиспользующий UI алерта архивации
упражнения (`ExerciseConfirmDialog` + `suppressContent`-слот с чекбоксом —
паттерн `ArchiveSuppressSlot`), с текстами для категорий: заголовок
`category_archive_dialog_title` («Архивировать категорию?» / "Archive
category?"), текст `category_archive_dialog_text` («Чтобы снова использовать
категорию "%1$s" в упражнениях, нужно будет вернуть её из архива» / "To use
\"%1$s\" again in exercises, restore it from the archive»), кнопки
«Отмена» / «Архивировать» (обычный цвет — операция обратима). Чекбокс
«Больше не показывать» (`exercise_archive_dont_show_again` — общий лейбл)
SHALL управлять ОТДЕЛЬНЫМ флагом `dont_show_category_archive_dialog` в
`AppSettingsDataStore` (НЕ общим с упражнениями `dont_show_archive_dialog`).
Канон хранения — дельта `appearance` этого change (требование
«AppSettingsDataStore persists archive-dialog preference»).
Тап «Архивировать» SHALL вызывать метод VM `requestCategoryArchive(id:
Long)` (паттерн `requestArchive`), который SHALL прочитать
`dataStore.dontShowCategoryArchiveDialog.first()`: при `true` — немедленно
вызвать `repository.setCategoryArchived(id, true)`, НЕ устанавливая
`pendingCategoryArchiveId` и НЕ показывая алерт; при `false` — установить
`pendingCategoryArchiveId = id`. При `dontShowCategoryArchiveDialog = true`
архивация SHALL выполняться сразу без алерта. Подтверждение SHALL вызывать
`setCategoryArchived(id, true)`;
при `Result.failure` pending сбрасывается молча (race — по образцу
архивации упражнения). Колбэки алерта SHALL прокидываться через поля
`ExercisesScreenState`: `onConfirmCategoryArchive: (Boolean) -> Unit = {}`
и `onCancelCategoryArchive: () -> Unit = {}` (симметрично
`onConfirmArchive`/`onCancelArchive`; значения по умолчанию `= {}` —
существующий паттерн data class, обязательность гарантируется на уровне
параметра `ExercisesScreen`). Кликабельность лейбла чекбокса — канон алерта
архивации упражнений.

#### Scenario: Тап «Архивировать» открывает алерт с именем категории

- **WHEN** открыто меню категории «Плечи» AND пользователь тапает
  «Архивировать»
- **THEN** виден алерт «Архивировать категорию?» с текстом про «Плечи»
  AND чекбокс «Больше не показывать» между текстом и кнопками

#### Scenario: Подтверждение с чекбоксом пишет отдельный pref

- **WHEN** чекбокс поставлен AND пользователь тапает «Архивировать»
- **THEN** `setDontShowCategoryArchiveDialog(true)` вызывается
- **AND** `dont_show_archive_dialog` (флаг упражнений) остаётся `false`

#### Scenario: Включённый флаг архивирует без алерта

- **WHEN** `dontShowCategoryArchiveDialog == true` AND пользователь тапает
  «Архивировать» в меню
- **THEN** категория архивируется немедленно AND алерт НЕ показывается

#### Scenario: Флаг упражнений не влияет на категории

- **WHEN** `dont_show_archive_dialog == true` (упражнения) AND
  `dont_show_category_archive_dialog == false`
- **THEN** архивация категории показывает алерт как обычно

### Requirement: Блокировка последнего активного — типизированные снекбары

Отклонение архивации или удаления последнего активного
упражнения/категории (`LastActiveExerciseException` /
`LastActiveCategoryException` из репозитория) SHALL показываться
одноразовым снекбаром. VM SHALL маппить guard-фейл в one-shot ошибку
на всех точках входа: `requestArchive` (прямой путь при suppress-флаге),
`confirmArchive`, `requestCategoryArchive`, `confirmCategoryArchive`,
`confirmDelete`, `confirmCategoryDelete`. Прочие failure архивации
(например, race «не найдено») остаются без сообщения. Сброс ошибок —
через `onErrorShown()` / `onCategoryErrorShown()`.

#### Scenario: Блокировка архивации упражнения — снекбар

- **WHEN** `repository.setArchived(id, true)` возвращает
  `Result.failure(LastActiveExerciseException)` в `confirmArchive`
  или в прямом пути `requestArchive`
- **THEN** показан снекбар «Нельзя архивировать единственное
  упражнение» / "Cannot archive the only exercise"
- **AND** упражнение остаётся в списке, pending сброшен

#### Scenario: Блокировка удаления упражнения — снекбар

- **WHEN** `repository.deleteExercise(id)` возвращает
  `Result.failure(LastActiveExerciseException)` в `confirmDelete`
- **THEN** показан снекбар «Нельзя удалить единственное упражнение» /
  "Cannot delete the only exercise"
- **AND** упражнение остаётся в списке

#### Scenario: Блокировка архивации и удаления категории — снекбары

- **WHEN** `setCategoryArchived`/`deleteCategory` возвращают
  `Result.failure(LastActiveCategoryException)`
- **THEN** показан снекбар «Нельзя архивировать единственную
  категорию» / "Cannot archive the only category" или «Нельзя удалить
  единственную категорию» / "Cannot delete the only category"
  соответственно
- **AND** категория остаётся в списке

#### Scenario: Race failure архивации остаётся без сообщения

- **WHEN** `setArchived` возвращает
  `Result.failure(IllegalStateException("Упражнение не найдено"))`
  (race, не guard)
- **THEN** снекбар не показывается — существующее поведение
  сохранено

#### Scenario: One-shot сброс ошибки

- **WHEN** снекбар показан AND экран вызывает `onErrorShown()` или
  `onCategoryErrorShown()`
- **THEN** обе one-shot ошибки своей сущности сброшены в `null`
  (`onErrorShown()` — `deleteError` и `archiveError`,
  `onCategoryErrorShown()` — `categoryDeleteError` и
  `categoryArchiveError`) — повторный показ возможен без ложного снекбара

