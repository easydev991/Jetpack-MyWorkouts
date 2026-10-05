# Spec Delta

## ADDED Requirements

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

## MODIFIED Requirements

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
