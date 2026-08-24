# more-screen Specification

## Purpose
TBD - created by archiving change navigation-foundation. Update Purpose after archive.
## Requirements
### Requirement: MoreScreen Scaffold with TopAppBar

`MoreScreen` SHALL be a `@Composable fun MoreScreen(modifier: Modifier = Modifier)` annotated with `@OptIn(ExperimentalMaterial3Api::class)`. Its `Scaffold` SHALL contain `topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) }`. The `Scaffold` body SHALL contain a `Text` with the app version at the bottom, aligned via `Alignment.BottomCenter`.

#### Scenario: MoreScreen renders with localized title

- **WHEN** `MoreScreen` is composed with the Russian locale active
- **THEN** the `TopAppBar` displays "Ещё"
- **AND** the `Text` with the version string is displayed at the bottom of the body

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

### Requirement: MoreScreen version text shows BuildConfig.VERSION_NAME

`MoreScreen` SHALL display the value of `BuildConfig.VERSION_NAME` formatted via `stringResource(R.string.app_version, BuildConfig.VERSION_NAME)`. The `Text` SHALL use `MaterialTheme.typography.bodyMedium`, `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)`, `TextAlign.Center`, and `Modifier.fillMaxWidth()`. The string `app_version` SHALL exist in both `res/values/strings.xml` (Russian: "Версия приложения: %1$s") and `res/values-en/strings.xml` (English: "App version: %1$s") with a `%1$s` placeholder for the version value.

#### Scenario: Russian locale shows version

- **WHEN** `MoreScreen` is composed with the Russian locale active and `BuildConfig.VERSION_NAME = "1.0.0"`
- **THEN** the displayed text is "Версия приложения: 1.0.0"

#### Scenario: English locale shows version

- **WHEN** `MoreScreen` is composed with the English locale active and `BuildConfig.VERSION_NAME = "1.0.0"`
- **THEN** the displayed text is "App version: 1.0.0"
