## MODIFIED Requirements

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
показывать» (`R.string.dont_show_again`) и подтвердил архивацию,
`confirmArchive` SHALL дополнительно вызвать
`dataStore.setDontShowArchiveDialog(true)`. Чекбокс в диалоге
архивации SHALL отображаться между текстом диалога и кнопками;
изначально — снят. Реализационные детали размещения `remember`
(scope, замыкание на `onConfirm`, неизменность сигнатуры
`onConfirm: () -> Unit`, отсутствие `suppress` в `ExercisesUiState`)
зафиксированы в `design.md` Decision 4 — единый канон.

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

## ADDED Requirements

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

Строки `dont_show_again` SHALL существовать в
`res/values/strings.xml` («Больше не показывать») и
`res/values-en/strings.xml` ("Don't show again"). Лейбл используется
в слоте `suppressContent` диалога архивации (требование «Алерт
подтверждения архивации») и НЕ применяется в диалоге удаления
(`ExerciseConfirmDialog` для удаления вызывается с
`suppressContent = null`).

#### Scenario: Локализованный лейбл чекбокса в диалоге архивации

- **WHEN** открыт алерт архивации AND активна русская локаль
- **THEN** рядом с чекбоксом отображается текст «Больше не
  показывать»
- **WHEN** открыт алерт архивации AND активна английская локаль
- **THEN** рядом с чекбоксом отображается текст "Don't show again"
