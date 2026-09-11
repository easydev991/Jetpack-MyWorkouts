# Proposal: create-exercise

## Why

Сейчас каталог упражнений на вкладке «Упражнения» реактивен, но FAB-заглушка
никуда не ведёт: пользователь не может пополнить каталог собственными
упражнениями. Экран создания упражнения отсутствует, равно как и явный
порядок сортировки каталога (кастомные vs стандартные, новые vs старые).
Этот change добавляет полноценный экран создания упражнения, подключает
FAB к навигации на него и фиксирует порядок каталога через `ORDER BY`
в Room-запросе.

## What Changes

- В `ExercisesScreen` FAB из заглушки превращается в точку входа на новый
  экран `ExerciseCreateScreen` (через колбэк `onAddExerciseClick` к
  навигации, как `MainScreen → EntryCreateScreen`).
- Добавляется экран создания упражнения по образцу `EntryCreateScreen`:
  обёртка + internal Content + data class ScreenState + `Scaffold` с
  `topBar`/`bottomBar`, форма с полями «Название», сегментированная кнопка
  `ExerciseMeasuredBy` (REPS/TIME, по умолчанию REPS), выпадающее меню
  `ExerciseCategory`, многострочный комментарий; кнопка «Сохранить» в
  `bottomBar` поверх клавиатуры.
- Валидация формы: `canSave = !name.isNullOrBlank() && category != null`.
- Подтверждение закрытия при несохранённых изменениях: `BackHandler(enabled =
  hasUnsavedChanges)` + `AlertDialog` с переиспользованными
  `entry_confirm_close_*`-строками (`R.string.entry_confirm_close_dialog_title`
  и т.д.) — симметрия UX с `EntryCreateScreen`, защита от случайной
  потери ввода при системном back или тапе стрелки `TopAppBar`.
- Сохранение: создаётся `Exercise(isCustom = true, createdAt =
  System.currentTimeMillis())` и вызывается `repository.addExercise(...)`;
  при успехе — `popBackStack()`, при ошибке — флаг `isSaveError` в
  `UiState` (тот же паттерн, что в `EntryCreateViewModel`). Нормализация
  текстовых полей — единая точка в `ExerciseCreateViewModel.save(...)`:
  `name.trim()` и `comment?.trim()?.takeIf { it.isNotEmpty() }`.
- В `Screen` появляется новый route `Screen.ExerciseCreate`
  (`"exercise_create"`, без аргументов), регистрируется в `NavHost`
  `RootScreen` рядом с `EntryCreate`; нижний бар скрывается через
  `shouldShowBottomBar` (как у не-tab экранов).
- Сортировка каталога в `ExerciseDao.observeExercises` переключается на
  `ORDER BY is_custom DESC, created_at DESC, name COLLATE NOCASE` —
  внутри каждой группы по убыванию `createdAt` (новые первые), при
  равных `createdAt` — по `name` без учёта регистра как финальный
  tie-break (действует в обеих группах; подробно — канон в спеке
  `specs/exercises-screen/spec.md`).
- Добавляются локализованные строки (ru/en) для заголовка экрана и всех
  полей формы, плюс строка ошибки сохранения.
- Добавляются `Preview` для экрана (светлая + тёмная темы, ключевые
  состояния формы).

## Capabilities

### New Capabilities

<!-- Capabilities being introduced. Use kebab-case for path segments you introduce
     (e.g., user-auth or identity/user-auth) that follow the project's existing
     spec organization. Each creates specs/<capability-path>/spec.md. -->

- `exercise-create-screen`: Экран создания нового упражнения (поля,
  валидация, поведение сохранения, формат создаваемого `Exercise`,
  взаимодействие с репозиторием, `BackHandler` + подтверждение закрытия
  при несохранённых изменениях).

### Modified Capabilities

<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use the exact existing path under openspec/specs/. Leave empty if no requirement
     changes. A change with no capabilities at all (pure refactor, tooling, docs)
     must set `skip_specs: true` in its .openspec.yaml - openspec validate rejects
     a zero-delta change without that marker. Do not invent a requirement just to
     satisfy validation. -->

- `exercises-screen`: FAB больше не заглушка — открывает
  `ExerciseCreateScreen`. Каталог сортируется на SQL-стороне согласно
  требованию спеки «Сортировка каталога — кастомные сверху, новые
  первые»: кастомные выше стандартных; внутри каждой группы — по
  убыванию `createdAt`; при равных `createdAt` — по `name` без учёта
  регистра (COLLATE NOCASE).
- `non-tab-screen-navigation`: В `Screen` добавляется
  `Screen.ExerciseCreate` (route `"exercise_create"`, без аргументов),
  регистрируется в `NavHost` `RootScreen`; нижний бар на этом экране
  скрыт (не-tab screen).

## Impact

- Новые/изменённые файлы:
  - `AGENTS.md` — Project Structure: добавить `exercises/` И `appdata/`
    к перечислению `ui/screens/...` (L97). `appdata/` уже существует в
    репозитории и отсутствует в AGENTS.md — рассинхрон фиксируется
    этим change попутно (без этого документация рассинхронизируется
    с реальной структурой после архивации change).
  - `app/src/main/java/com/myworkouts/navigation/Screen.kt` —
    `object ExerciseCreate` в `sealed class Screen`.
  - `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt` —
    `composable("exercise_create") { ... }` в `NavHost`; прокидывание
    `onAddExerciseClick` в `ExercisesScreen`.
  - `app/src/main/java/com/myworkouts/ui/screens/exercises/ExercisesScreen.kt`:
    - `ExercisesScreen(...)` принимает параметр `onAddExerciseClick:
      () -> Unit` без значения по умолчанию (требование спеки
      «FAB открывает ExerciseCreateScreen»).
    - Удалить дефолт `= {}` у поля `ExercisesScreenState.onAddExerciseClick`
      — поле становится обязательным.
  - `app/src/main/java/com/myworkouts/ui/screens/exercises/ExerciseCreateScreen.kt`
    (внутри: `ExerciseCreateScreen` — публичная обёртка,
    `internal ExerciseCreateScreenState`, `internal
    ExerciseCreateScreenContent`, `internal ExerciseCreateForm`,
    `internal ExerciseCreateSaveBottomBar`, `internal
    CategoryDropdownField`, `internal ExerciseCreateCloseConfirmDialog`
    — диалог подтверждения закрытия при несохранённых изменениях,
    см. design Decision 8 и задачу tasks.md 5.2).
  - `app/src/main/java/com/myworkouts/ui/screens/exercises/ExerciseCreateScreenPreview.kt`
    — `@Preview-функции` (светлая + тёмная тема).
  - `app/src/main/java/com/myworkouts/ui/state/ExerciseCreateUiState.kt`.
  - `app/src/main/java/com/myworkouts/ui/viewmodel/ExerciseCreateViewModel.kt`.
  - `app/src/main/java/com/myworkouts/data/db/ExerciseDao.kt` —
    изменение `ORDER BY` в `observeExercises`.
  - `app/src/main/java/com/myworkouts/data/repository/WorkoutDataRepository.kt`
    и
    `app/src/main/java/com/myworkouts/data/repository/RoomWorkoutDataRepository.kt`
    — обновление kdoc метода `exercises(query)` (после правки `ORDER BY`
    фраза «отсортированный по имени» станет ложной; см. tasks.md 2.2).
  - `app/src/main/res/values/strings.xml` и
    `app/src/main/res/values-en/strings.xml` — новые строки
    (`exercise_field_*`, `exercise_create_title`, `exercise_save_error`).
  - `app/src/test/java/com/myworkouts/ui/viewmodel/ExerciseCreateViewModelTest.kt`
    — новый (JVM, TDD).
  - `app/src/androidTest/java/com/myworkouts/data/db/ExerciseDaoTest.kt`
    — добавляются сценарии сортировки кастомных/стандартных/по дате
    (рядом с существующим `search_results_areSortedByNameCaseInsensitive`).
  - `app/src/androidTest/java/com/myworkouts/ui/screens/exercises/ExercisesScreenTest.kt`
    — добавляется сценарий FAB-навигации на `Screen.ExerciseCreate`.
  - `app/src/androidTest/java/com/myworkouts/ui/screens/exercises/ExerciseCreateScreenTest.kt`
    — новый (UI TDD).
- API/контракты: контракт `WorkoutDataRepository.addExercise(...)` не
  меняется (метод уже возвращает `Result<Long>`).
- Никаких новых внешних зависимостей; никаких изменений схемы Room
  (миграция не нужна — сущность `ExerciseEntity` уже содержит все
  поля).