## MODIFIED Requirements

### Requirement: MoreScreen Scaffold with TopAppBar

`MoreScreen` SHALL be a `@Composable fun MoreScreen(navController:
NavHostController, modifier: Modifier = Modifier)` annotated with
`@OptIn(ExperimentalMaterial3Api::class)`. Its `Scaffold` SHALL contain
`topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) }`.
The `Scaffold` body SHALL contain a `Box` with a `Row(fillMaxWidth,
Arrangement.Center) { FilledTonalButton(...) { Text(stringResource(R.string.appearance_title)) } }`
near the top, and a `Text` with the app version at the bottom, aligned
via `Alignment.BottomCenter`.

#### Scenario: MoreScreen renders with localized title

- **WHEN** `MoreScreen(navController)` is composed with the Russian
  locale active
- **THEN** the `TopAppBar` displays "Ещё"
- **AND** the `Text` with the version string is displayed at the bottom
  of the body

#### Scenario: MoreScreen has appearance button

- **WHEN** `MoreScreen(navController)` is composed
- **THEN** a `FilledTonalButton` labeled "Оформление приложения" /
  "Appearance" is visible and horizontally centered

## ADDED Requirements

### Requirement: MoreScreen accepts a required NavController

`MoreScreen` SHALL accept `navController: NavHostController` as a
required first parameter (it was previously `modifier: Modifier =
Modifier` only). The `navController` is used to navigate to
`Screen.Appearance.route` when the appearance button is tapped. There
is no fallback or `LocalNavController` lookup — the parameter is
explicit and required.

#### Scenario: NavController is passed from RootScreen

- **WHEN** `RootScreen` composes the `More` destination
- **THEN** it invokes `MoreScreen(navController = navController)` with
  the `RootScreen`'s `rememberNavController()`

### Requirement: Appearance button navigates to Screen.Appearance

The "Оформление приложения" / "Appearance" `FilledTonalButton` SHALL have
`onClick = { navController.navigate(Screen.Appearance.route) {
popUpTo(Screen.More.route) } }`. The button SHALL use
`contentPadding = PaddingValues(horizontal =
dimensionResource(R.dimen.button_horizontal_padding))` where
`button_horizontal_padding` is `16dp` in `res/values/dimens.xml`.

#### Scenario: Russian locale button text

- **WHEN** `MoreScreen` is composed with the Russian locale active
- **THEN** the button text is "Оформление приложения"

#### Scenario: English locale button text

- **WHEN** `MoreScreen` is composed with the English locale active
- **THEN** the button text is "Appearance"

#### Scenario: Clicking the button navigates to appearance

- **WHEN** user taps the "Оформление приложения" / "Appearance" button
- **THEN** `navController.navigate(Screen.Appearance.route)` is called
  with `popUpTo(Screen.More.route)`
- **AND** the active destination becomes `Screen.Appearance.route`

#### Scenario: Button is horizontally centered

- **WHEN** `MoreScreen` is composed
- **THEN** the button's left margin (distance from `root.left` to
  `button.left`) equals the right margin (distance from `button.right`
  to `root.right`) within ±1px (regression test for the
  `Row(fillMaxWidth, Arrangement.Center)` wrapper)
