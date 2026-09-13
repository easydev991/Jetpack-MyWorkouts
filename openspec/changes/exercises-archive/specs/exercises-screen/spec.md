## MODIFIED Requirements

### Requirement: Контекстное меню по долгому нажатию — с учётом типа упражнения

Долгое нажатие на строке `ExerciseListItemRow` SHALL открывать
контекстное меню с учётом типа упражнения. Для пользовательского
упражнения (`isCustom = true`) меню SHALL содержать три пункта в
порядке: «Изменить» / "Edit" (`exercise_list_menu_edit`, иконка Edit),
«Архивировать» / "Archive" (`exercise_list_menu_archive`, иконка Archive),
«Удалить» (`exercise_list_menu_delete`, иконка Delete). Для
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

### Requirement: Список упражнений реактивен

`ExercisesViewModel` SHALL подписываться на `repository.exercises("")`
(реактивный `Flow` НЕ-архивных упражнений, НЕ разовый
`getAllExercises()` — тот для экспорта/импорта), на внутренний
`pendingDeleteId: MutableStateFlow<Long?>` (идентификатор упражнения,
для которого открыт алерт подтверждения удаления), на внутренний
`pendingArchiveId: MutableStateFlow<Long?>` (идентификатор упражнения,
для которого открыт алерт подтверждения архивации) и на
`archivedCount: Flow<Int>` (реактивный счётчик архивных для кнопки в
`TopAppBar`), объединяя все потоки через `combine` в
`StateFlow<ExercisesUiState>` (`stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000), ExercisesUiState.Loading)`).
`ExercisesUiState.Success` SHALL содержать поля `exercises:
List<Exercise>` (только активные — фильтрация в SQL
`exercises(query)`), `pendingDeleteId: Long?`, `deleteError:
DeleteExerciseError? = null`, `pendingArchiveId: Long?`,
`archivedCount: Int`. Sealed-иерархия — `Loading` /
`Success(exercises, pendingDeleteId, deleteError, pendingArchiveId,
archivedCount)` без `Error`-ветки (единственный источник — офлайн
Room-Flow; ошибки доводятся до пользователя снекбаром или тихо).
Сортировка SHALL быть на стороне SQL (`observeExercises`); критерии и
порядок определены требованием «Сортировка каталога — кастомные
сверху, новые первые» в этом же capability. Благодаря реактивности
создание, удаление, редактирование, архивация и восстановление
упражнений обновляют список без действий экрана.

#### Scenario: Loading до первой эмиссии

- **WHEN** `ExercisesViewModel` создан, но репозиторий ещё не эмитил
- **THEN** `uiState.value` is `ExercisesUiState.Loading`

#### Scenario: Success со списком после первой эмиссии

- **WHEN** репозиторий эмитит список упражнений
- **THEN** `uiState.value` is `ExercisesUiState.Success` с тем же списком
  и `pendingDeleteId == null`

#### Scenario: Изменение Flow обновляет список

- **WHEN** в репозитории добавили/удалили/изменили/заархивировали
  упражнение (Flow эмитит новый список) при активной подписке
- **THEN** `uiState` эмитит обновлённый `Success` без перезапуска
  ViewModel

#### Scenario: pending-идентификатор отражается в Success

- **WHEN** вызван `requestDelete` для упражнения с id = 5
- **THEN** `uiState.value` is `Success` с `pendingDeleteId == 5`
- **WHEN** вызван `cancelDelete()`
- **THEN** `uiState.value` is `Success` с `pendingDeleteId == null`

## ADDED Requirements

### Requirement: Алерт подтверждения архивации

Тап «Архивировать» в контекстном меню SHALL открывать `AlertDialog` с
заголовком `exercise_archive_dialog_title` («Архивировать упражнение?» /
"Archive exercise?"), текстом `exercise_archive_dialog_text`
с формальной подстановкой имени упражнения
(«Чтобы снова использовать упражнение "%1$s" в тренировках, нужно
будет вернуть его из архива» / "To use \"%1$s\" again in workouts,
restore it from the archive"), кнопкой отмены
`exercise_archive_dialog_cancel` («Отмена» / "Cancel") и кнопкой
подтверждения `exercise_archive_dialog_confirm` («Архивировать» /
"Archive") в дефолтном цвете (`ButtonDefaults.textButtonColors()`,
БЕЗ `colorScheme.error` — в отличие от `ExerciseDeleteDialog`).
Архивация — обратимая операция (есть восстановление через экран
архива), обычный цвет подтверждения.

Подтверждение SHALL вызывать `confirmArchive()`, который вызывает
`repository.setArchived(id, true)`. При `Result.success` pending
сбрасывается, алерт закрыт, упражнение пропадает из списка
(реактивный Flow каталога). Отмена (`cancelArchive()`) SHALL
закрыть алерт, не изменяя каталог.

#### Scenario: Тап «Архивировать» открывает алерт с именем упражнения

- **WHEN** открыто меню упражнения «Жим» AND пользователь тапает
  «Архивировать»
- **THEN** виден алерт с заголовком «Архивировать упражнение?» и
  текстом «Чтобы снова использовать упражнение "Жим" в тренировках,
  нужно будет вернуть его из архива»
- **AND** строки заголовка, текста и кнопок локализованы (ru/en)

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

### Requirement: Кнопка архива в TopAppBar

В `actions` слоте `TopAppBar` `ExercisesScreen` SHALL показываться
`IconButton` с иконкой `Icons.Filled.Archive` и
`contentDescription = exercises_archive_button`
(«Архивированные упражнения» / "Archived exercises"), ЕСЛИ
`archivedCount > 0` (реактивный счётчик из репозитория). Если
архивных упражнений нет — кнопка скрыта. Тап по кнопке SHALL
инициировать навигацию на `Screen.ArchivedExercises.route` через
колбэк `onArchivedExercisesClick`, прокидываемый из `RootScreen`.
Видимость кнопки SHALL быть реактивной: при архивации первого
упражнения кнопка появляется, при восстановлении последнего —
исчезает.

#### Scenario: Архив пуст — кнопка скрыта

- **WHEN** `archivedCount == 0`
- **THEN** `TopAppBar.actions` НЕ содержит `IconButton` с иконкой
  Archive

#### Scenario: Архив не пуст — кнопка отображается

- **WHEN** `archivedCount > 0` (например, 1 заархивированное
  упражнение)
- **THEN** `TopAppBar.actions` содержит `IconButton` с иконкой
  `Icons.Filled.Archive` и `contentDescription = exercises_archive_button`

#### Scenario: Тап кнопки архива навигирует на ArchivedExercisesScreen

- **WHEN** `archivedCount > 0` AND пользователь тапает кнопку архива
  в `TopAppBar`
- **THEN** `onArchivedExercisesClick` вызывается ровно один раз
- **AND** активным становится `Screen.ArchivedExercises.route`

#### Scenario: Реактивное появление кнопки при архивации

- **WHEN** `archivedCount == 0` AND пользователь архивирует одно
  упражнение через long-press меню
- **THEN** после закрытия алерта `archivedCount` становится 1
- **AND** `IconButton` с иконкой Archive появляется в `TopAppBar.actions`

#### Scenario: Реактивное исчезновение кнопки при восстановлении

- **WHEN** `archivedCount == 1` AND пользователь восстанавливает
  последнее архивное упражнение через экран архива
- **THEN** после возврата на `ExercisesScreen` `archivedCount` становится 0
- **AND** `IconButton` с иконкой Archive скрывается из `TopAppBar.actions`

### Requirement: Сигнатура ExercisesScreen принимает колбэк архива

`ExercisesScreen` SHALL принимать параметр
`onArchivedExercisesClick: () -> Unit` (без значения по умолчанию,
обязательный) и пробрасывать его в
`ExercisesScreenState.onArchivedExercisesClick`. Полная сигнатура SHALL
быть: `@Composable fun ExercisesScreen(viewModel: ExercisesViewModel,
onAddExerciseClick: () -> Unit, onEditExercise: (Long) -> Unit = {},
onArchivedExercisesClick: () -> Unit, modifier: Modifier = Modifier)`.
Колбэк SHALL быть единственным каналом навигации на
`Screen.ArchivedExercises.route` — экран не знает про NavController.
В `RootScreen` SHALL передаваться
`navController.navigate(Screen.ArchivedExercises.route)` (паттерн —
существующая прокидка `onAddExerciseClick` для `Screen.ExerciseCreate`).

#### Scenario: ExercisesScreen принимает onArchivedExercisesClick

- **WHEN** `ExercisesScreen` is referenced
- **THEN** его сигнатура включает обязательный параметр
  `onArchivedExercisesClick: () -> Unit`
- **AND** параметр пробрасывается в
  `ExercisesScreenState.onArchivedExercisesClick`

#### Scenario: Тап кнопки архива вызывает прокинутый колбэк

- **WHEN** `archivedCount > 0` AND пользователь тапает `IconButton` с
  иконкой `Icons.Filled.Archive` в `TopAppBar`
- **THEN** вызывается `ExercisesScreenState.onArchivedExercisesClick`
  (а через него — `RootScreen` → `navController.navigate(
  Screen.ArchivedExercises.route)`)
