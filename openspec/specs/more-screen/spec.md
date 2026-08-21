# more-screen Specification

## Purpose
TBD - created by archiving change navigation-foundation. Update Purpose after archive.
## Requirements
### Requirement: MoreScreen Scaffold with TopAppBar

`MoreScreen` SHALL be a `@Composable fun MoreScreen(modifier: Modifier = Modifier)` annotated with `@OptIn(ExperimentalMaterial3Api::class)`. Its `Scaffold` SHALL contain `topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) }`. The `Scaffold` body SHALL be empty.

#### Scenario: MoreScreen renders with localized title

- **WHEN** `MoreScreen` is composed with the Russian locale active
- **THEN** the `TopAppBar` displays "Ещё"
- **AND** the `Scaffold` body is empty

### Requirement: MoreScreen has no FAB or BottomBar

`MoreScreen` SHALL NOT include a `FloatingActionButton` or any `BottomBar`. The `NavigationBar` belongs to the outer `RootScreen` only — `MoreScreen` SHALL NOT duplicate it.

#### Scenario: Only one NavigationBar exists

- **WHEN** the user navigates to the `More` tab
- **THEN** exactly one `NavigationBar` is visible (from `RootScreen`)
- **AND** no FAB is rendered

### Requirement: MoreScreen Scaffold uses horizontal-only safeDrawing insets

`MoreScreen`'s `Scaffold` SHALL set `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)` — symmetric with `MainScreen`. Rationale: see `specs/tab-navigation/spec.md` (insets contract). Without this, when the body becomes non-empty (list, `LazyColumn`, etc.), its content shifts upward by the system nav bar height.

#### Scenario: Insets do not double-subtract

- **WHEN** `MoreScreen` body contains content (list, etc.)
- **THEN** the content sits flush with the `NavigationBar` top edge via `paddingValues`
- **AND** does not shift upward by the system nav bar height

### Requirement: More tab label is localized ru/en

The string `more_title` (used for both `NavigationBarItem.label` and `TopAppBar.title`) SHALL exist in both `res/values/strings.xml` (Russian: "Ещё") and `res/values-en/strings.xml` (English: "More").

#### Scenario: Russian locale

- **WHEN** the device locale is `ru`
- **THEN** the more tab label is "Ещё"

#### Scenario: English locale

- **WHEN** the device locale is `en`
- **THEN** the more tab label is "More"

