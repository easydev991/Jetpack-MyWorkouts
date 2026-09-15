## Why

Сейчас при попытке заархивировать упражнение всегда показывается
`AlertDialog` с подтверждением («Архивировать упражнение? — Чтобы
снова использовать… нужно будет вернуть из архива»). Это раздражает
пользователей, которые регулярно архивируют/разархивируют упражнения
(например, сезонные) и подтверждают одно и то же действие каждый
раз. Нужен одноразовый opt-out: галочка «Больше не показывать» в
диалоге архивации записывает глобальный флаг в `AppSettingsDataStore`,
после чего архивация выполняется сразу, без диалога.

## What Changes

- Расширение `AppSettingsDataStore` ключом `dontShowArchiveDialog`.
- Чтение pref в `requestArchive` с веткой немедленной архивации.
- Поддержка `suppress` в `confirmArchive(suppress: Boolean = false)`.
- Опциональный slot `suppressContent` в `ExerciseConfirmDialog`.
- Передача `Row { Checkbox + Text }` в слот из `SuccessBody`.
- Локализация `dont_show_again` (ru + en).
- Превью всех ключевых состояний диалога (светлая + тёмная темы).

Breaking changes: нет — при существующей установке pref
отсутствует → `first()` вернёт `false` → диалог показывается как
сейчас.

## Capabilities

### New Capabilities

Нет. Поведенческие изменения полностью укладываются в правки
существующих capabilities `appearance` и `exercises-screen`.

### Modified Capabilities

- `appearance`: расширение контракта `AppSettingsDataStore`
  (добавляются `dontShowArchiveDialog: Flow<Boolean>` со default
  `false` и `setDontShowArchiveDialog(value: Boolean)`). Файл
  `appearance_prefs` остаётся прежним, миграция не требуется
  (DataStore Preferences расширяет схему автоматически).
- `exercises-screen`: изменение требования «Алерт подтверждения
  архивации» — добавление глобального opt-out через чекбокс
  «Больше не показывать», чтение pref в `requestArchive` (ветка
  немедленной архивации), запись pref в `confirmArchive(suppress)`,
  расширение `ExerciseConfirmDialog` опциональным slot-параметром
  `suppressContent`. Требование «Алерт подтверждения удаления
  упражнения» остаётся без изменений (slot = `null`).

## Impact

- `app/src/main/java/com/myworkouts/data/preferences/AppSettingsDataStore.kt`
  — добавляются `dontShowArchiveDialog: Flow<Boolean>` и
  `setDontShowArchiveDialog(value)`.
- `app/src/main/java/com/myworkouts/ui/viewmodel/ExercisesViewModel.kt`
  — конструктор принимает `AppSettingsDataStore`; `requestArchive`
  читает pref через `.first()`; `confirmArchive(suppress: Boolean =
  false)` при `suppress == true` параллельно пишет pref; factory
  обновляется.
- `app/src/main/java/com/myworkouts/ui/screens/exercises/ExercisesScreen.kt`
  — `ExerciseConfirmDialog` принимает slot `suppressContent`; колбэк
  `ExercisesScreenState.onConfirmArchive` расширяется до
  `(Boolean) -> Unit` (детали и сводка по строкам — `design.md`
  Decision 4).
- `app/src/main/java/com/myworkouts/MainActivity.kt` — **без
  изменений**: `AppSettingsDataStore` создаётся в `MyWorkoutsApplication`
  как процесс-singleton, MainActivity берёт его через
  `application as MyWorkoutsApplication` (`MainActivity.kt:42–44`)
  и передаёт в `RootScreen` (`MainActivity.kt:69`) — DI-цепочка
  от `MyWorkoutsApplication` до `RootScreen` уже корректна
  (после `fix-entry-list-live-update`).
- `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt`
  — **правка в середине DI-цепочки**: `AppNavHost` (line 119)
  уже принимает `dataStore`, но `ExercisesNavDestination` (line
  210) сейчас имеет сигнатуру `(repository, navController)` и
  не получает `dataStore`. Нужно: (a) расширить
  `ExercisesNavDestination` параметром `dataStore: AppSettingsDataStore`;
  (b) в `AppNavHost.composable(Screen.Exercises.route) { ... }`
  передать `dataStore = dataStore` в `ExercisesNavDestination`;
  (c) внутри `ExercisesNavDestination` —
  `viewModel(factory = ExercisesViewModel.factory(repository, dataStore))`.
  Это единственное место, где `dataStore` «доходит» до VM
  фабрики.
- `app/src/main/res/values/strings.xml` + `values-en/strings.xml` —
  добавляется `dont_show_again`.
- `app/src/test/.../AppSettingsDataStoreTest.kt` — новый файл,
  три TDD-теста на `dontShowArchiveDialog` Flow/setter
  (см. `tasks.md` §1.0).
- `app/src/test/.../ExercisesViewModelTest.kt` — четыре новых
  теста (см. `tasks.md`).
- Превью-файлы `ExercisesScreenPreview.kt` (или аналогичный) —
  расширяются вариантами с активным архив-диалогом (чекбокс снят /
  поставлен) и общим `ExerciseConfirmDialog` со слотом/без слота,
  светлая + тёмная темы.

Out of scope:

- Счётчик «скрыть после N показов».
- Per-exercise пометка (только глобальный pref).
- Изменение диалога удаления (slot = `null`, рендерится как раньше).
- Миграция DataStore (новый ключ добавляется без миграции).
- `Result`-враппер для архивации (архив обратим — текущее поведение
  «проглотить ошибку» сохраняется).
- Откат pref при ошибке архивации (overengineering).
