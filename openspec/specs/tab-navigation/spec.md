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

`Screen` SHALL be a `sealed class` with `val route: String`, `val icon: ImageVector`, `val titleResId: Int` — all three parameters non-null. Subclasses SHALL be `object Main` (`route = "main"`, icon = `Icons.Filled.Home`, title = `R.string.workouts_title`) and `object More` (`route = "more"`, icon = `Icons.Filled.Menu`, title = `R.string.more_title`). `Screen` SHALL expose `val AllTabs = listOf(Main, More)` as the single source of truth for tab order.

#### Scenario: AllTabs list matches the visible tabs

- **WHEN** `RootScreen` renders its `NavigationBar`
- **THEN** it iterates `Screen.AllTabs`
- **AND** the order of `NavigationBarItem`s matches the order in `AllTabs`

#### Scenario: Screen fields are non-null

- **WHEN** a destination is rendered in `NavigationBarItem`
- **THEN** `screen.icon` and `screen.titleResId` are accessed without `!!` or `?.let`

### Requirement: RootScreen hosts NavHost with the two destinations

`RootScreen` SHALL use `rememberNavController()` and a `NavHost` containing exactly two `composable` entries: one for `Screen.Main.route` calling `MainScreen()`, one for `Screen.More.route` calling `MoreScreen()`. The start destination SHALL be `Screen.Main.route`.

#### Scenario: Navigation to the More tab

- **WHEN** user taps the "Ещё" tab in the `NavigationBar`
- **THEN** `NavController` navigates to `Screen.More.route`
- **AND** `MoreScreen` is composed

### Requirement: NavigationBar highlights the active tab via currentBackStackEntryAsState

`RootScreen` SHALL derive the active tab from `navController.currentBackStackEntryAsState()?.destination?.route` (NOT from a separate `ViewModel`). A `NavigationBarItem` SHALL be `selected = true` when the current route equals its `screen.route`.

#### Scenario: Active tab indicator follows navigation

- **WHEN** user is on the `More` tab
- **THEN** the `NavigationBarItem` for `More` shows the `selected` styling
- **AND** the `Main` tab does not show `selected` styling

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

