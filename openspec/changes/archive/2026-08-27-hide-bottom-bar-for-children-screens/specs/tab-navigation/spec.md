## MODIFIED Requirements

### Requirement: NavigationBar highlights the active tab via currentBackStackEntryAsState

`RootScreen` SHALL derive the active tab from `navController.currentBackStackEntryAsState()?.destination?.route` (NOT from a separate `ViewModel`). A `NavigationBarItem` SHALL be `selected = true` when the current route equals its `screen.route`.

#### Scenario: Active tab indicator follows navigation

- **WHEN** user is on the `More` tab
- **THEN** the `NavigationBarItem` for `More` shows the `selected` styling
- **AND** the `Main` tab does not show `selected` styling

#### Scenario: NavigationBar iterates BottomTabs

- **WHEN** `RootScreen` is composed
- **THEN** its `NavigationBar` iterates `BottomTabs.forEach { tab -> ... }`
- **AND** the number of `NavigationBarItem`s equals `BottomTabs.size` (2)

#### Scenario: Appearance destination has no NavigationBarItem

- **WHEN** user is on `Screen.Appearance.route`
- **THEN** no `NavigationBarItem` is `selected`

## ADDED Requirements

### Requirement: NavigationBar visibility follows current route

`RootScreen`'s `Scaffold.bottomBar` SHALL render the `NavigationBar`
only when `currentRoute` (from
`navController.currentBackStackEntryAsState()?.destination?.route`)
equals one of the routes in `BottomTabs.map { it.route }`. For any
other route (currently `Screen.Appearance.route`), the `bottomBar`
slot SHALL render nothing — the `NavigationBar` MUST NOT be present
in the composition tree. The list of visible-tab routes SHALL be
derived from `BottomTabs` (single source of truth), not hardcoded —
when a new tab is added to `BottomTabs`, its route automatically
becomes visible-with-bar; any other destination is automatically
hidden.

#### Scenario: NavigationBar visible on Main

- **WHEN** the active route is `Screen.Main.route`
- **THEN** the `NavigationBar` is visible
- **AND** the `Main` `NavigationBarItem` shows the `selected` styling

#### Scenario: NavigationBar visible on More

- **WHEN** the active route is `Screen.More.route`
- **THEN** the `NavigationBar` is visible
- **AND** the `More` `NavigationBarItem` shows the `selected` styling

#### Scenario: NavigationBar hidden on Appearance

- **WHEN** the active route is `Screen.Appearance.route`
- **THEN** the `NavigationBar` is NOT present in the composition tree
- **AND** zero `NavigationBarItem`s are rendered
- **AND** no vertical space is reserved for the bottom bar
  (the screen body fills the area down to the system navigation bar)

#### Scenario: NavigationBar reappears when returning to a tab

- **WHEN** user is on `Screen.Appearance.route` and navigates back to
  `Screen.More.route` (via the back arrow or system back gesture)
- **THEN** the `NavigationBar` becomes visible again
- **AND** the `More` `NavigationBarItem` shows the `selected` styling

#### Scenario: Visibility is derived from BottomTabs

- **WHEN** the code is read
- **THEN** the visibility check is `currentRoute in
  BottomTabs.map { it.route }` (no hardcoded list of tab routes)

#### Scenario: Adding a new tab automatically shows the bar on it

- **WHEN** a developer adds a new `BottomTab` entry to `BottomTabs`
- **THEN** no other code change is required for the `NavigationBar` to
  appear on that route — the visibility predicate is computed from
  `BottomTabs.map { it.route }`
