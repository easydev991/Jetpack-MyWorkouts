# Proposal: edit-delete-exercise

## Why

Экран «Упражнения» сейчас — каталог только для чтения: пользовательское
упражнение нельзя исправить (опечатка в названии, другая категория) и нельзя
удалить (мусорное упражнение остаётся в списке навсегда). Записи на главном
экране уже умеют и то, и другое — каталог отстаёт по возможностям, хотя
слой данных (`updateExercise`, `deleteExercise`) готов с самого начала.

## What Changes

- Долгое нажатие на строке **кастомного** упражнения открывает контекстное
  меню «Изменить» / «Удалить» — точная копия паттерна главного экрана
  (`ContextMenuState` + `DropdownMenu` у точки нажатия + подсветка строки).
  Долгое нажатие на **стандартном** упражнении меню не показывает.
- «Удалить» открывает `AlertDialog` подтверждения («Удалить упражнение?»);
  при подтверждении вызывается `repository.deleteExercise`, при отмене —
  ничего не меняется.
- Ошибка удаления (FK — есть записи) пробрасывается пользователю: снекбар с
  сообщением об ошибке, pending-состояние сбрасывается.
- «Изменить» открывает `ExerciseCreateScreen` в режиме редактирования через
  новый query-параметр `exerciseId` маршрута — по образцу `Screen.EntryCreate`.
- `ExerciseCreateScreen` в edit-режиме: заголовок «Изменить упражнение»,
  поля предзаполнены из БД, «Сохранить» активна только при `canSave`
  (формула — норматив в спек-е `exercise-create-screen`, требование
  «ViewModel и UiState формы»), сохранение идёт через `updateExercise`
  (в create-режиме — через `addExercise`, как раньше).
- Новый метод репозитория `getExercise(id): Result<Exercise?>` — чтение
  по идентификатору для prefill-а формы.
- Новые локализованные строки ru/en: заголовок edit-режима, пункты меню,
  алерт удаления, конкретные снекбары для каждой причины ошибки
  удаления (FK, стандартное, прочее — design Decision 3).

## Capabilities

### New Capabilities

(нет — переиспользуем существующие capabilities, новые не вводим)

### Modified Capabilities

- `exercises-screen`: новые требования — контекстное меню по долгому
  нажатию только для кастомных упражнений, алерт подтверждения удаления,
  pending-флаг удаления в `ExercisesUiState.Success`, обработка ошибки
  удаления, навигация на edit-режим `ExerciseCreateScreen`.
- `exercise-create-screen`: расширение маршрута query-параметром
  `exerciseId`, edit-режим (prefill, заголовок, `hasChanges`/`canSave`,
  ветвление save create/update, `isExerciseMissing`), обновление формулы
  `hasUnsavedChanges` для edit-режима, новые превью edit-состояний.
- `non-tab-screen-navigation`: `Screen.ExerciseCreate` — маршрут с
  query-параметром `exerciseId`, константа `NO_EXERCISE_ID`, фабрика
  `createRoute(exerciseId)` и аргументы `composable`-назначения в
  `RootScreen` (по образцу `Screen.EntryCreate`).

## Impact

- `app/src/main/java/com/myworkouts/navigation/Screen.kt` —
  `Screen.ExerciseCreate` получает query-параметр `exerciseId` +
  `NO_EXERCISE_ID`.
- `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt` — чтение
  `exerciseId` из аргументов, проброс в фабрику VM.
- `app/src/main/java/com/myworkouts/ui/screens/exercises/ExercisesScreen.kt`
  — long-press, `ExerciseContextMenu`, `ExerciseDeleteDialog`, снекбар
  ошибки.
- `app/src/main/java/com/myworkouts/ui/state/ExercisesUiState.kt` —
  расширение `Success` полями `pendingDeleteId: Long?` и
  `deleteError: DeleteExerciseError?` (sealed-тип из
  `domain/model/DeleteExerciseError.kt`; паттерн
  `MainUiState.Success.pendingDeleteId`).
- `app/src/main/java/com/myworkouts/ui/viewmodel/ExercisesViewModel.kt` —
  pendingDeleteId + `requestDelete`/`confirmDelete`/`cancelDelete`.
- `app/src/main/java/com/myworkouts/ui/screens/exercises/ExerciseCreateScreen.kt`
  — edit-режим заголовка.
- `app/src/main/java/com/myworkouts/ui/viewmodel/ExerciseCreateViewModel.kt`
  — `originalExercise`, prefill, ветвление save.
- `app/src/main/java/com/myworkouts/ui/state/ExerciseCreateUiState.kt` —
  `isEditMode`, `hasChanges`, `isExerciseMissing`; `canSave`
  переписывается с учётом `isEditMode` (в edit дополнительно требуется
  `hasChanges`); `hasUnsavedChanges` получает ветвление по `isEditMode`.
- `app/src/main/java/com/myworkouts/data/repository/WorkoutDataRepository.kt`
  и `RoomWorkoutDataRepository.kt` — `getExercise(id)` (§1.3) И
  `exerciseIdsWithLinkedEntries(): Flow<Set<Long>>` (§1.5, pre-check FK);
  `deleteExercise` возвращает `Result<Unit>` с типизированными
  `StandardExerciseDeletionException` / `LinkedEntriesDeletionException`
  (design Decision 3).
- `app/src/main/java/com/myworkouts/data/db/ExerciseEntryDao.kt` —
  `countByExerciseId(id): Int` + `observeExerciseIdsWithLinkedEntries():
  Flow<List<Long>>` (для pre-check).
- `app/src/main/java/com/myworkouts/data/repository/DeletionExceptions.kt`
  (новый) — `class StandardExerciseDeletionException :
  IllegalStateException(...)` + `class LinkedEntriesDeletionException :
  IllegalStateException(...)`.
- `app/src/main/java/com/myworkouts/domain/model/DeleteExerciseError.kt`
  (новый) — sealed-тип `DeleteExerciseError { HasLinkedEntries;
  StandardExercise; Other(message) }`.
- `app/src/main/res/values/strings.xml` (ru, default) и
  `values-en/strings.xml` (en override) — 11 новых ключей; среди них
  `exercise_delete_error_used_in_workouts` и
  `exercise_delete_error_standard` для типизированных снекбаров
  (Decision 3).
- Тесты: `ExercisesViewModelTest`, `ExerciseCreateViewModelTest`,
  репозиторный тест `getExercise`; androidTest `ExercisesScreenTest`,
  `ExerciseCreateScreenTest`.
- Не затрагивается: схема БД и миграции, `MainScreen`, `EntryCreateScreen`,
  prepopulate стандартных упражнений, модель `Exercise`, новые зависимости.
