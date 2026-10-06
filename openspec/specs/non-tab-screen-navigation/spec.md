# non-tab-screen-navigation Specification

## Purpose

Метаданные маршрутов навигации: sealed-класс `Screen` с маршрутами
внекладочных экранов и `BottomTab` с UI-метаданными вкладок нижней
навигации.

## Requirements

### Requirement: Screen sealed class holds only route

`Screen` SHALL be `sealed class Screen(val route: String)` and SHALL
NOT contain `icon`, `titleResId`, or other UI metadata. Each subclass
is an `object` with a unique `route` string. `Screen` SHALL be the
single source of truth for navigation route strings; the navigation
graph in `RootScreen` SHALL use `Screen.Main.route`, `Screen.Exercises.route`,
`Screen.More.route`, `Screen.Appearance.route`, `Screen.AppData.route`,
`Screen.EntryCreate.route`, `Screen.ExerciseCreate.route`,
`Screen.ArchivedExercises.route`, `Screen.EntrySettings.route`,
`Screen.CategoryCreate.route` as the registered destinations.

#### Scenario: Screen has only route parameter

- **WHEN** `Screen` is inspected
- **THEN** it exposes exactly one parameter: `val route: String`
- **AND** it does NOT expose `icon` or `titleResId` properties

#### Scenario: Appearance object added to sealed Screen

- **WHEN** `Screen.Appearance` is referenced
- **THEN** `Screen.Appearance.route == "appearance"`
- **AND** `Screen.Appearance` is an `object` instance of `Screen`

#### Scenario: AppData object added to sealed Screen

- **WHEN** `Screen.AppData` is referenced
- **THEN** `Screen.AppData.route == "app_data"`
- **AND** `Screen.AppData` is an `object` instance of `Screen`

#### Scenario: Exercises object added to sealed Screen

- **WHEN** `Screen.Exercises` is referenced
- **THEN** `Screen.Exercises.route == "exercises"`
- **AND** `Screen.Exercises` is an `object` instance of `Screen`

#### Scenario: ArchivedExercises object added to sealed Screen

- **WHEN** `Screen.ArchivedExercises` is referenced
- **THEN** `Screen.ArchivedExercises.route == "archived_exercises"`
- **AND** `Screen.ArchivedExercises` is an `object` instance of
  `Screen`
- **AND** `Screen.ArchivedExercises` НЕ входит в `BottomTabs` —
  паттерн `Screen.Appearance`

#### Scenario: EntrySettings object added to sealed Screen

- **WHEN** `Screen.EntrySettings` is referenced
- **THEN** `Screen.EntrySettings.route == "entry_settings"`
- **AND** `Screen.EntrySettings` is an `object` instance of `Screen`
- **AND** `Screen.EntrySettings` НЕ входит в `BottomTabs` — паттерн
  `Screen.Appearance` (системная панель навигации — см.
  `entry-settings-screen/spec.md`)
- **AND** нижняя навигация приложения скрыта при активном маршруте
  `entry_settings` — как на экране создания/изменения записи

### Requirement: BottomTab data class holds tab UI metadata

`BottomTab` SHALL be a `data class` with parameters `route: String`,
`icon: ImageVector`, and `titleResId: Int`, used to populate the
`NavigationBar` in `RootScreen`. `BottomTabs: List<BottomTab>` SHALL list
exactly the tabs that appear in the bottom navigation, in left-to-right
order. The list SHALL contain entries for `Screen.Main.route` (icon
`Icons.Filled.Home`, title `R.string.workouts_title`),
`Screen.Exercises.route` (icon `Icons.Filled.FitnessCenter`, title
`R.string.exercises_title`), and `Screen.More.route` (icon
`Icons.Filled.Menu`, title `R.string.more_title`) — Exercises between
Main and More. `Screen.Appearance`, `Screen.AppData` и
`Screen.ArchivedExercises` SHALL NOT be in `BottomTabs` (они не
являются tab-экранами). `BottomTabs` SHALL remain the full static list
(order and membership — single source of truth); runtime visibility of
individual tabs is NOT filtered here — it is governed by
`tab-navigation/spec.md` (the Exercises entry is hidden when the
`show_exercises_tab` setting is off).

#### Scenario: BottomTabs contains Main, Exercises and More

- **WHEN** `BottomTabs` is iterated
- **THEN** the routes are `["main", "exercises", "more"]` in that order
- **AND** no entry has route `"appearance"`, `"app_data"` or
  `"archived_exercises"`

#### Scenario: NavigationBar iterates BottomTabs

- **WHEN** `RootScreen` is composed
- **THEN** its `NavigationBar` iterates `BottomTabs` (not a screen-level
  list) to render `NavigationBarItem`s

### Requirement: ExerciseCreate object added to sealed Screen

`Screen.ExerciseCreate` SHALL быть `object ExerciseCreate : Screen(route =
"exercise_create?exerciseId={exerciseId}")` с query-параметром
`exerciseId` — паттерн `Screen.EntryCreate`. Объект SHALL содержать:
константу `const val ARG_EXERCISE_ID: String = "exerciseId"`,
`const val NO_EXERCISE_ID: Long = -1L`, фабрику маршрута
`fun createRoute(exerciseId: Long = NO_EXERCISE_ID) =
"exercise_create?$ARG_EXERCISE_ID=$exerciseId"` и
`val arguments = listOf(navArgument(ARG_EXERCISE_ID) { type =
NavType.LongType; defaultValue = NO_EXERCISE_ID })`. Регистрация в
`NavHost` `RootScreen`: `composable(Screen.ExerciseCreate.route,
arguments = Screen.ExerciseCreate.arguments) { ... }`; значение
`exerciseId` SHALL читаться из `backStackEntry.arguments?.getLong(
ARG_EXERCISE_ID, Screen.ExerciseCreate.NO_EXERCISE_ID)` и передаваться в
фабрику `ExerciseCreateViewModel` (по аналогии с `entryId` →
`EntryCreateViewModel`). Create-режим использует фабрику маршрута без
параметра: `Screen.ExerciseCreate.createRoute()` даёт
`"exercise_create?exerciseId=-1"`. `Screen`
остаётся единственным источником истины для строк навигационных
маршрутов.

#### Scenario: ExerciseCreate object added to sealed Screen

- **WHEN** `Screen.ExerciseCreate` is referenced
- **THEN** `Screen.ExerciseCreate.route ==
  "exercise_create?exerciseId={exerciseId}"`
- **AND** `Screen.ExerciseCreate` declared as `object` inside sealed
  `Screen`

#### Scenario: Фабрика маршрута с exerciseId

- **WHEN** `Screen.ExerciseCreate.createRoute(7)` вычислен
- **THEN** результат `"exercise_create?exerciseId=7"`
- **AND** `Screen.ExerciseCreate.NO_EXERCISE_ID == -1L`

#### Scenario: Create-режим без параметра

- **WHEN** `Screen.ExerciseCreate.createRoute()` вычислен без аргумента
- **THEN** результат `"exercise_create?exerciseId=-1"`
- **AND** `exerciseId == NO_EXERCISE_ID` соответствует create-режиму

#### Scenario: NavHost registers ExerciseCreate destination

- **WHEN** `RootScreen` is composed
- **THEN** `NavHost` has a
  `composable(Screen.ExerciseCreate.route, arguments = ...) { ... }`
  destination рядом с `EntryCreate`
- **AND** `exerciseId` из аргументов backStackEntry передаётся в фабрику
  `ExerciseCreateViewModel`

#### Scenario: Bottom navigation скрыта на ExerciseCreate

- **WHEN** активный route is `Screen.ExerciseCreate.route`
- **THEN** нижний бар навигации скрыт (контракт — `BottomTab`-бар не
  отображается; реализация в `RootScreen` оставлена на усмотрение
  разработчика — конкретное имя локального флага/переменной не
  фиксируется)
- **AND** активный экран — `ExerciseCreateScreen` (см. capability
  `exercise-create-screen`)
### Requirement: ArchivedExercises не участвует в нижней навигации

`Screen.ArchivedExercises : Screen(route = "archived_exercises")` —
паттерн `Screen.Appearance`: не-tab экран, доступный через
навигацию с `ExercisesScreen` (тап кнопки архива в `TopAppBar`).
Нижняя навигация SHALL быть скрыта при активном маршруте
`Screen.ArchivedExercises.route` — паттерн по образцу `Screen.Appearance`
(см. Scenario «Bottom navigation скрыта на ExerciseCreate» и
архивный `2026-08-26-feature-appearance-screen/design.md`). Контракт:
`shouldShowBottomBar = currentRoute in tabs.map { it.route }` —
`archived_exercises` НЕ в списке → бар скрыт. Никаких правок в
`RootScreen` для скрытия бара не требуется.

#### Scenario: Bottom navigation скрыта на ArchivedExercises

- **WHEN** активный route is `Screen.ArchivedExercises.route`
- **THEN** нижний бар навигации скрыт (контракт — `BottomTab`-бар не
  отображается; реализация в `RootScreen` оставлена на усмотрение
  разработчика — конкретное имя локального флага/переменной не
  фиксируется)
- **AND** активный экран — `ArchivedExercisesScreen` (см. capability
  `archived-exercises-screen`)

### Requirement: Маршруты экрана обзора и его настроек

`Screen.Overview : Screen(route = "overview")` и
`Screen.OverviewSettings : Screen(route = "overview_settings")` —
нетабовые маршруты по паттерну `Screen.Appearance`: без аргументов, без
UI-метаданных, `Screen` остаётся единственным источником строк маршрутов.
Оба маршрута SHALL быть зарегистрированы в `NavHost` `RootScreen`;
`Overview` открывается с главного экрана (кнопка обзора в `TopAppBar`),
`OverviewSettings` — с экрана обзора (кнопка настроек), системный Back
возвращает на предыдущий экран. `screenForRoute` SHALL отображать маршруты
на значения `AppScreen.OverviewScreen` и `AppScreen.OverviewSettingsScreen`
для аналитики просмотра экранов. Нижняя панель навигации SHALL быть скрыта
на обоих маршрутах — существующий контракт
`shouldShowBottomBar = currentRoute in tabs.map { it.route }`:
`overview` и `overview_settings` не входят в список табов, правок
`RootScreen` для скрытия не требуется.

#### Scenario: Регистрация и переход

- **WHEN** пользователь нажимает кнопку обзора на главном экране
- **THEN** `NavHost` навигирует на `Screen.Overview.route` и `screenForRoute` отдаёт `AppScreen.OverviewScreen`

#### Scenario: Bottom navigation скрыта на экранах обзора

- **WHEN** активен маршрут `overview` или `overview_settings`
- **THEN** нижняя панель навигации не отображается
- **AND** контент экрана использует освободившееся место без двойных отступов

#### Scenario: Переход к настройкам обзора и возврат

- **WHEN** на экране обзора нажимается кнопка настроек, затем системный Back
- **THEN** открывается `Screen.OverviewSettings.route`, затем Back возвращает на экран обзора

### Requirement: CategoryCreate object added to sealed Screen

`Screen.CategoryCreate` SHALL быть `object CategoryCreate : Screen(route =
"category_create?categoryId={categoryId}")` с query-параметром
`categoryId` — паттерн `Screen.ExerciseCreate`. Объект SHALL содержать:
константу `const val ARG_CATEGORY_ID: String = "categoryId"`, `const val
NO_CATEGORY_ID: Long = -1L`, фабрику маршрута `fun createRoute(categoryId:
Long = NO_CATEGORY_ID) = "category_create?$ARG_CATEGORY_ID=$categoryId"` и
`val arguments = listOf(navArgument(ARG_CATEGORY_ID) { type =
NavType.LongType; defaultValue = NO_CATEGORY_ID })`. Регистрация в
`NavHost` `RootScreen`: `composable(Screen.CategoryCreate.route, arguments
= Screen.CategoryCreate.arguments) { ... }`; значение `categoryId` SHALL
читаться из `backStackEntry.arguments?.getLong(ARG_CATEGORY_ID,
Screen.CategoryCreate.NO_CATEGORY_ID)` и передаваться в фабрику
`CategoryCreateViewModel`. Create-режим использует фабрику маршрута без
параметра. `Screen` остаётся единственным источником истины для строк
навигационных маршрутов. `screenForRoute` SHALL отображать маршрут на
значение `AppScreen.CategoryCreateScreen` для аналитики просмотра экранов.
Нижняя навигация SHALL быть скрыта при активном маршруте (контракт
`shouldShowBottomBar = currentRoute in tabs.map { it.route }` —
`category_create` не входит в список, правок `RootScreen` для скрытия бара
не требуется).

#### Scenario: CategoryCreate object added to sealed Screen

- **WHEN** `Screen.CategoryCreate` is referenced
- **THEN** `Screen.CategoryCreate.route ==
  "category_create?categoryId={categoryId}"`
- **AND** `Screen.CategoryCreate` declared as `object` inside sealed
  `Screen`

#### Scenario: Фабрика маршрута с categoryId

- **WHEN** `Screen.CategoryCreate.createRoute(3)` вычислен
- **THEN** результат `"category_create?categoryId=3"`
- **AND** `Screen.CategoryCreate.NO_CATEGORY_ID == -1L`

#### Scenario: NavHost registers CategoryCreate destination

- **WHEN** `RootScreen` is composed
- **THEN** `NavHost` has a `composable(Screen.CategoryCreate.route,
  arguments = ...) { ... }` destination рядом с `ExerciseCreate`
- **AND** `categoryId` из аргументов backStackEntry передаётся в фабрику
  `CategoryCreateViewModel`

#### Scenario: Bottom navigation скрыта на CategoryCreate

- **WHEN** активный route is `Screen.CategoryCreate.route`
- **THEN** нижний бар навигации скрыт
- **AND** активный экран — `CategoryCreateScreen` (см. capability
  `category-create-screen`)
