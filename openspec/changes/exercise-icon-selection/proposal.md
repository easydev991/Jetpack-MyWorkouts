# Proposal

## Why

Пользователь пока не может назначить упражнению иконку при создании и сменить её при редактировании: данные уже готовы (колонка `icon_number`, мапперы, репозиторий — из `exercise-icon-mvp`), иконки отображаются в списках (`exercise-icon-display`), а на форме выбора нет — секция `ExerciseIconPickerSection` существует только как черновик с хардкодом строк и no-op колбэком.

## What Changes

- Секция выбора иконки на `ExerciseCreateScreen` перестаёт быть черновиком:
  - хардкод заменён строковыми ресурсами ru/en (заголовок «Иконка»/«Icon», contentDescription «Без иконки»/«No icon»);
  - состояние и колбэк проводятся от ViewModel через `ExerciseCreateScreenState` вместо черновых полей с no-op дефолтами.
- `ExerciseCreateUiState`: новое поле `iconNumber: Int? = null` (по умолчанию — «Без иконки»).
- `ExerciseCreateViewModel`: обработчик `onIconNumberChange` (по образцу `onMeasuredByChange`, сбрасывает `isSaveError`); `draftOf` несёт `iconNumber` в `Exercise`; `prefillState` предзаполняет `iconNumber` в edit-режиме. Сохранение (create и edit) пишет выбранный `iconNumber`.
- Экран доступен только для пользовательских упражнений, поэтому секция видна всегда, без условия по `is_custom`.
- Тесты (TDD — до реализации): unit (`ExerciseCreateViewModelTest`, `ExerciseCreateUiStateTest`) и androidTest (`ExerciseCreateScreenTest`) — отрисовка секции, выбор ячейки, round-trip сохранения в Room in-memory.

## Capabilities

### New Capabilities

(нет)

### Modified Capabilities

- `exercise-create-screen`:
  - **ADDED** новый requirement «Секция выбора иконки» — выбор при создании, смена при редактировании, «Без иконки» как значение по умолчанию и его повторный выбор;
  - **MODIFIED** существующие requirements (правка нормативных текстов, требует `## MODIFIED Requirements` в дельте):
    - «ViewModel и UiState формы» — добавлены поле `iconNumber: Int? = null` в UiState и колбэк `onIconNumberChange: (Int?) -> Unit`;
    - «Сохранение — create/addExercise и edit/updateExercise с popBackStack» — тело требования приводится к сборке через `draftOf(state, original)` (канон кода: `ExerciseCreateViewModel.kt:127–144, 167–183` — единая точка нормализации уже существует, спека подтягивается к коду, чтобы оставаться нормативной); `iconNumber` в составе `Exercise` при create/edit как часть инвариантов сборки черновика;
    - «BackHandler + hasUnsavedChanges — подтверждение закрытия» — канон-формула `hasUnsavedChanges` create-режима дополнена термом `|| iconNumber != null`; добавлен отдельный scenario «Изменение иконки учитывается в hasUnsavedChanges и hasChanges»;
    - «Edit-режим: загрузка, предзаполнение и hasChanges» — `iconNumber` входит в предзаполнение и в diff для `hasChanges`;
    - «ExerciseCreateScreen — обёртка + Content + ScreenState» — `onIconNumberChange: (Int?) -> Unit` в перечне полей `ScreenState`; черновые `selectedIconNumber`/`onIconNumberChange` удалены.

## Impact

- `app/src/main/java/com/myworkouts/ui/screens/exercises/ExerciseCreateScreen.kt` (+ `ExerciseCreateScreenPreview.kt`) — доводка `ExerciseIconPickerSection`, проводка состояния.
- `app/src/main/java/com/myworkouts/ui/state/ExerciseCreateUiState.kt` — поле `iconNumber`, формула `hasUnsavedChanges` create-режима.
- `app/src/main/java/com/myworkouts/ui/viewmodel/ExerciseCreateViewModel.kt` — обработчик, `draftOf`, `prefillState`.
- `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` — две новые строки.
- Тесты: `app/src/test/.../ExerciseCreateViewModelTest.kt`, `ExerciseCreateUiStateTest.kt`; `app/src/androidTest/.../ExerciseCreateScreenTest.kt`.
- Data-слой не меняется: `ExerciseEntity.icon_number`, `Mappers`, репозиторий уже несут `iconNumber`.
