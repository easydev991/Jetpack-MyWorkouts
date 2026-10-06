# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
