## MODIFIED Requirements

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

## ADDED Requirements

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
