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
`Screen.More.route`, `Screen.Appearance.route`, `Screen.AppData.route` as the
registered destinations.

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
Main and More. `Screen.Appearance` and `Screen.AppData` SHALL NOT be in
`BottomTabs` (they are not tab destinations). `BottomTabs` SHALL remain
the full static list (order and membership — single source of truth);
runtime visibility of individual tabs is NOT filtered here — it is
governed by `tab-navigation/spec.md` (the Exercises entry is hidden when
the `show_exercises_tab` setting is off).

#### Scenario: BottomTabs contains exactly Main and More

- **WHEN** `BottomTabs` is iterated
- **THEN** the routes are `["main", "exercises", "more"]` in that order
- **AND** no entry has route `"appearance"` or `"app_data"`

#### Scenario: NavigationBar iterates BottomTabs

- **WHEN** `RootScreen` is composed
- **THEN** its `NavigationBar` iterates `BottomTabs` (not a screen-level
  list) to render `NavigationBarItem`s
