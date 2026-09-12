# exercises-screen Specification

## Purpose

Экран «Упражнения» — вкладка каталога упражнений (стандартных и
пользовательских) в нижней навигации: реактивный список только для чтения
с FAB, открывающим экран создания упражнения.

## Requirements

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
(реактивный `Flow`, НЕ разовый `getAllExercises()`) и на внутренний
`pendingDeleteId: MutableStateFlow<Long?>` (идентификатор упражнения, для
которого открыт алерт подтверждения удаления), объединяя оба потока через
`combine` в `StateFlow<ExercisesUiState>` (`stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000), ExercisesUiState.Loading)`).
`ExercisesUiState` SHALL быть sealed-иерархией `Loading` /
`Success(exercises: List<Exercise>, pendingDeleteId: Long?,
deleteError: DeleteExerciseError? = null)` без
`Error`-ветки (единственный источник — офлайн Room-Flow, прецедент
задокументирован в `MainUiState.kt`; ошибки удаления доводятся до
пользователя снекбаром через one-shot-флаг `deleteError` — требование
«Ошибка удаления — типизированные причины в снекбаре» ниже). Сортировка SHALL быть на стороне SQL
(`observeExercises`); критерии и порядок определены требованием
«Сортировка каталога — кастомные сверху, новые первые» в этом же
capability. Благодаря реактивности создание, удаление и редактирование
упражнений обновляют список без действий экрана.

#### Scenario: Loading до первой эмиссии

- **WHEN** `ExercisesViewModel` создан, но репозиторий ещё не эмитил
- **THEN** `uiState.value` is `ExercisesUiState.Loading`

#### Scenario: Success со списком после первой эмиссии

- **WHEN** репозиторий эмитит список упражнений
- **THEN** `uiState.value` is `ExercisesUiState.Success` с тем же списком
  и `pendingDeleteId == null`

#### Scenario: Изменение Flow обновляет список

- **WHEN** в репозитории добавили/удалили/изменили упражнение (Flow эмитит
  новый список) при активной подписке
- **THEN** `uiState` эмитит обновлённый `Success` без перезапуска ViewModel

#### Scenario: pending-идентификатор отражается в Success

- **WHEN** вызван `requestDelete` для упражнения с id = 5
- **THEN** `uiState.value` is `Success` с `pendingDeleteId == 5`
- **WHEN** вызван `cancelDelete()`
- **THEN** `uiState.value` is `Success` с `pendingDeleteId == null`

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

`ExercisesScreen` SHALL принимать `onAddExerciseClick: () -> Unit` в
качестве обязательного параметра (без значения по умолчанию) и
пробрасывать его в `ExercisesScreenState.onAddExerciseClick`. `FAB`
(компонент `FloatingActionButton`, без `IconButton`-обёртки —
паттерн существующего кода, `ExercisesScreen.kt` L101–108) SHALL
принимать `onClick = state.onAddExerciseClick`. Тап по FAB SHALL
инициировать навигацию на `Screen.ExerciseCreate.route` через
колбэк `onAddExerciseClick`, прокидываемый из `RootScreen` (как
`MainScreen → EntryCreateScreen`). Поле `ExercisesScreenState.onAddExerciseClick:
() -> Unit` SHALL быть обязательным (без дефолта `= {}`); `ExercisesScreen`
пробрасывает колбэк из своего параметра в `ExercisesScreenState.onAddExerciseClick`
при создании state. Экран создания упражнения определён в capability
`exercise-create-screen`.

#### Scenario: Тап по FAB открывает ExerciseCreateScreen

- **WHEN** пользователь нажимает FAB на `ExercisesScreen`
- **THEN** `onAddExerciseClick` вызывается ровно один раз
- **AND** активным становится `Screen.ExerciseCreate.route`

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

### Requirement: Контекстное меню по долгому нажатию — только для кастомных упражнений

Долгое нажатие на строке `ExerciseListItemRow` SHALL открывать
контекстное меню только если упражнение пользовательское
(`isCustom = true`). Для стандартного упражнения (`isCustom = false`)
долгое нажатие SHALL NOT производить видимого эффекта: ни меню, ни
подсветки, ни алерта. Жест `detectTapGestures(onLongPress)` —
по образцу в `MainScreen` (дефолтный platform long-press ≈500 мс,
без кастомного timeout). Меню (`DropdownMenu`) SHALL располагаться в точке
нажатия (офсет внутри корня списка — паттерн `MainScreen`) и содержать
ровно два пункта: «Изменить» (`exercise_list_menu_edit`, иконка Edit) и
«Удалить» (`exercise_list_menu_delete`, иконка Delete). Пока меню
открыто, строка SHALL подсвечиваться (цвет через `animateColorAsState`,
семантика `selected` — паттерн `EntryListItemRow`); закрытие меню (тап
вне меню, выбор пункта, системный back) снимает подсветку.

Тап «Изменить» SHALL инициировать навигацию на
`Screen.ExerciseCreate.createRoute(exerciseId)` (маршрут — capability
`non-tab-screen-navigation`, поведение edit-режима — capability
`exercise-create-screen`). Тап «Удалить» SHALL закрывать меню и
открывать алерт подтверждения (требование «Алерт подтверждения удаления
упражнения» ниже).

#### Scenario: Long-press кастомного открывает меню с двумя пунктами

- **WHEN** пользователь делает долгое нажатие на строке упражнения с
  `isCustom = true`
- **THEN** в точке нажатия открывается меню с пунктами «Изменить» и
  «Удалить»
- **AND** строка подсвечена, пока меню открыто

#### Scenario: Long-press стандартного не показывает меню

- **WHEN** пользователь делает долгое нажатие на строке упражнения с
  `isCustom = false`
- **THEN** меню не открывается
- **AND** строка не подсвечивается

#### Scenario: Тап «Изменить» навигирует на edit-экран

- **WHEN** открыто меню кастомного упражнения с id = 7 AND пользователь
  тапает «Изменить»
- **THEN** меню закрывается
- **AND** выполняется навигация на
  `Screen.ExerciseCreate.createRoute(7)`

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
