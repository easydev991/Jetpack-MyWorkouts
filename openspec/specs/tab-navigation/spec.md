# tab-navigation Specification

## Purpose
TBD - created by archiving change navigation-foundation. Update Purpose after archive.
## Requirements
### Requirement: App entry point delegates to RootScreen

`MainActivity.setContent` SHALL render `JetpackMyWorkoutsTheme { RootScreen() }` and SHALL NOT contain the Android Studio template `Greeting`/`GreetingPreview` composables.

#### Scenario: Application starts on the workouts tab

- **WHEN** user launches the application
- **THEN** `MainActivity` invokes `RootScreen()`
- **AND** the start destination in `NavHost` is `Screen.Main.route`

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

### Requirement: NavigationBarItem click preserves tab state across switches

`onClick` of each `NavigationBarItem` SHALL navigate using `popUpTo(navController.graph.findStartDestination().id) { saveState = true }`, `launchSingleTop = true`, `restoreState = true`. This SHALL preserve the per-tab state (scroll position, text fields) across switches without a separate `ViewModel`.

#### Scenario: Returning to a tab restores its state

- **WHEN** user switches from `Main` to `More` and back to `Main`
- **THEN** the `Main` tab composable state is the same as before the switch
- **AND** no `ViewModel` is used to store the state

### Requirement: Outer Scaffold zeroes contentWindowInsets

`RootScreen`'s outer `Scaffold` SHALL set `contentWindowInsets = WindowInsets(0, 0, 0, 0)`. Rationale: `Material3.NavigationBar` already accounts for the system navigation bar inset via its own `windowInsets` parameter, so the outer `Scaffold` MUST NOT subtract it again — otherwise the inner `MainScreen` applies the same subtraction and the `FloatingActionButton` floats twice the system bar height above the `NavigationBar`.

#### Scenario: FAB sits at the correct vertical offset

- **WHEN** the app is rendered on a device with a gesture navigation bar
- **THEN** the `FloatingActionButton` in `MainScreen` visually sits 16 dp above the `NavigationBar` top edge
- **AND** NOT 16 dp above the system navigation bar (which would mean the double-subtract bug)

### Requirement: Navigation logic is implemented without a ViewModel

`RootScreen` SHALL NOT depend on `androidx.lifecycle.ViewModel`. Active tab state SHALL come from `navController.currentBackStackEntryAsState()`. `MutableStateFlow<Screen>` SHALL NOT be used for tab tracking.

#### Scenario: No ViewModel in RootScreen

- **WHEN** the code is read
- **THEN** `RootScreen.kt` has no `viewModel()` call
- **AND** no class named `RootScreenViewModel` exists in the project
