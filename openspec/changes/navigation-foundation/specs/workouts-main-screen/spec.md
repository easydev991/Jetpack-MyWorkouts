## ADDED Requirements

### Requirement: MainScreen Scaffold with TopAppBar

`MainScreen` SHALL be a `@Composable fun MainScreen(modifier: Modifier = Modifier)` annotated with `@OptIn(ExperimentalMaterial3Api::class)`. Its `Scaffold` SHALL contain `topBar = { TopAppBar(title = { Text(stringResource(R.string.workouts_title)) }) }`. The `Scaffold` body SHALL be empty.

#### Scenario: MainScreen renders with localized title

- **WHEN** `MainScreen` is composed with the Russian locale active
- **THEN** the `TopAppBar` displays "Тренировки"
- **AND** the `Scaffold` body is empty

### Requirement: MainScreen FAB is a placeholder

`MainScreen`'s `Scaffold` SHALL include `floatingActionButton = { FloatingActionButton(onClick = { /* placeholder */ }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_workout)) } }`. The `onClick` body SHALL be a placeholder — real "add workout" logic belongs to a separate change.

#### Scenario: FAB is visible and pressable

- **WHEN** the user is on the `Main` tab
- **THEN** a `FloatingActionButton` with `Icons.Filled.Add` is rendered above the `NavigationBar`
- **AND** tapping the FAB does not crash the app (placeholder no-op)

### Requirement: MainScreen Scaffold uses horizontal-only safeDrawing insets

`MainScreen`'s `Scaffold` SHALL set `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)`. The vertical insets are already consumed by `NavigationBar` in the outer `RootScreen`; re-applying them here would shift the FAB upward.

#### Scenario: Insets do not double-subtract

- **WHEN** `MainScreen` is rendered inside `RootScreen`
- **THEN** the FAB sits at the same vertical offset as in the `JetpackDays` reference
- **AND** content body sits flush with the `NavigationBar` top edge via `paddingValues`

### Requirement: Workouts tab labels are localized ru/en

The strings used by the workouts tab — `workouts_title` (for both `NavigationBarItem.label` and `TopAppBar.title`) and `add_workout` (for the FAB `contentDescription`) — SHALL exist in both `res/values/strings.xml` (Russian: "Тренировки" / "Добавить тренировку") and `res/values-en/strings.xml` (English: "Workouts" / "Add workout").

#### Scenario: Russian locale

- **WHEN** the device locale is `ru`
- **THEN** the workouts tab label is "Тренировки"
- **AND** the FAB `contentDescription` is "Добавить тренировку"

#### Scenario: English locale

- **WHEN** the device locale is `en`
- **THEN** the workouts tab label is "Workouts"
- **AND** the FAB `contentDescription` is "Add workout"
