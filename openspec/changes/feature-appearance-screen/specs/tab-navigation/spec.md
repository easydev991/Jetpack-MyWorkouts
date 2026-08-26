## MODIFIED Requirements

### Requirement: Sealed Screen class defines all navigation routes

`Screen` SHALL be a `sealed class` with a single `val route: String`
parameter and SHALL NOT contain `icon` or `titleResId` properties
(UI metadata for the `NavigationBar` is held in a separate `BottomTab`
data class — see `non-tab-screen-navigation/spec.md`). Subclasses SHALL
be `object Main` (`route = "main"`), `object More` (`route = "more"`),
and `object Appearance` (`route = "appearance"`, NOT a tab — no entry
in `BottomTabs`). `Screen` SHALL NOT expose `val AllTabs` (removed —
the source of truth for the bottom navigation is now `BottomTabs: List<BottomTab>`
in `non-tab-screen-navigation/spec.md`).

#### Scenario: Screen fields are non-null

- **WHEN** a `Screen` subclass is referenced (e.g. `Screen.Main`)
- **THEN** `screen.route` is accessible without `!!` or `?.let`
  (only one parameter on `Screen`)
- **AND** `icon` and `titleResId` are NOT properties of `Screen`
  (no `!!` / `?.let` needed at `NavigationBarItem` call sites —
  those reads moved to `BottomTab.icon` / `BottomTab.titleResId`)

#### Scenario: AllTabs list matches the visible tabs

- **WHEN** `RootScreen` renders its `NavigationBar`
- **THEN** it iterates `BottomTabs: List<BottomTab>` (the replacement
  for the removed `Screen.AllTabs`)
- **AND** the order of `NavigationBarItem`s matches the order in
  `BottomTabs`

#### Scenario: Appearance is a Screen object but not a tab

- **WHEN** `Screen.Appearance` is referenced
- **THEN** `Screen.Appearance.route == "appearance"`
- **AND** `Screen.Appearance` does NOT appear in `BottomTabs` (it has
  no `BottomTab` entry — it is a non-tab destination reached from
  `MoreScreen`)

### Requirement: RootScreen hosts NavHost with the three destinations

`RootScreen` SHALL use `rememberNavController()` and a `NavHost`
containing exactly three `composable` entries: one for
`Screen.Main.route` calling `MainScreen()`, one for `Screen.More.route`
calling `MoreScreen(navController = navController)`, and one for
`Screen.Appearance.route` calling
`AppearanceScreen(viewModel = viewModel(factory =
AppearanceViewModel.factory(dataStore)), onBackClick = { navController.popBackStack() })`.
The start destination SHALL be `Screen.Main.route`. `RootScreen` SHALL
accept `dataStore: AppSettingsDataStore` as a required parameter
(passed from `MainActivity`) — it SHALL NOT construct a second
`AppSettingsDataStore` for the same `appearance_prefs` file.

#### Scenario: Navigation to the More tab

- **WHEN** user taps the "Ещё" tab in the `NavigationBar`
- **THEN** `NavController` navigates to `Screen.More.route`
- **AND** `MoreScreen(navController = navController)` is composed

#### Scenario: Navigation to the Appearance destination from More

- **WHEN** `MoreScreen`'s "Оформление приложения" button is tapped
- **THEN** `NavController` navigates to `Screen.Appearance.route`
- **AND** `AppearanceScreen` is composed with `AppearanceViewModel.factory(dataStore)`

#### Scenario: Back from Appearance returns to More

- **WHEN** user is on `Screen.Appearance.route` and taps the back
  navigation icon in the `TopAppBar`
- **THEN** `onBackClick` invokes `navController.popBackStack()`
- **AND** the previous destination (`Screen.More.route`) is composed

#### Scenario: saveState preserves Appearance sub-destination across tab switches

- **WHEN** user navigates Main → More → Appearance → taps the Main tab
- **THEN** the `More` back-stack (with `Appearance` on top) is saved
  via `popUpTo(navController.graph.findStartDestination().id) {
  saveState = true }` in `NavigationBarItem.onClick`; the active
  back-stack becomes `[Screen.Main.route]`
- **AND** when user taps the More tab again, the active destination
  becomes `Screen.Appearance.route` (restored via `restoreState =
  true` — the saved `[Screen.Main.route, Screen.More.route,
  Screen.Appearance.route]` back-stack is restored with Appearance on
  top, NOT `[Screen.Main.route, Screen.More.route]`)

#### Scenario: Back from Appearance returns to More after tab switch

- **WHEN** user is on `Screen.Appearance.route`, taps the Main tab,
  taps the More tab, then taps the back navigation icon in
  Appearance's `TopAppBar`
- **THEN** `navController.popBackStack()` pops Appearance
- **AND** the active destination is `Screen.More.route` (the restored
  More tab state, NOT `Screen.Main.route`)
- **AND** `Screen.Main.route` remains at the bottom of the back-stack
  as the start destination (`popUpTo` uses
  `findStartDestination().id`, not `Screen.More.route`, so Main is
  never popped off)

### Requirement: NavigationBar highlights the active tab via currentBackStackEntryAsState

`RootScreen` SHALL derive the active tab from
`navController.currentBackStackEntryAsState()?.destination?.route`. The
`NavigationBar` SHALL iterate `BottomTabs` (NOT `Screen.AllTabs` — that
property no longer exists). A `NavigationBarItem` SHALL be
`selected = true` when the current route equals its `tab.route`.

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
- **AND** the `NavigationBar` is still visible (root `Scaffold`'s
  `bottomBar` is always rendered) — appearance is a sub-destination
  reached from `More`, not a tab

## REMOVED Requirements

### Requirement: Screen.icon and Screen.titleResId are non-null

**Reason**: replaced by the `BottomTab` data class. The `Screen` sealed
class now holds only `route`; UI metadata (icon, title) lives in
`BottomTab` so non-tab destinations like `Appearance` don't need
nullable UI fields. The bottom navigation is driven by
`BottomTabs: List<BottomTab>`, not by `Screen.AllTabs`.

**Migration**: code that referenced `screen.icon` / `screen.titleResId`
must read `tab.icon` / `tab.titleResId` from the matching `BottomTab`
entry. The mapping is one-to-one: each tab in `BottomTabs` has the
same `route` as a `Screen` object.

### Requirement: RootScreen has no ViewModel parameter

**Reason**: `RootScreen` does not depend on a `ViewModel`; it owns a
`rememberNavController()` and uses `currentBackStackEntryAsState()` for
the active tab. However, it now accepts `dataStore: AppSettingsDataStore`
(required) so the `Appearance` destination can construct its
`AppearanceViewModel` with the application-wide data store.

**Migration**: callers that previously invoked `RootScreen()` (no args)
must now invoke `RootScreen(dataStore = dataStore)` from `MainActivity`,
where `dataStore` is the single `AppSettingsDataStore(applicationContext)`
created in `onCreate`.
