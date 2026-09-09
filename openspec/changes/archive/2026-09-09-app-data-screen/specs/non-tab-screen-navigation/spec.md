## MODIFIED Requirements

### Requirement: Screen sealed class holds only route

`Screen` SHALL be `sealed class Screen(val route: String)` and SHALL
NOT contain `icon`, `titleResId`, or other UI metadata. Each subclass
is an `object` with a unique `route` string. `Screen` SHALL be the
single source of truth for navigation route strings; the navigation
graph in `RootScreen` SHALL use `Screen.Main.route`, `Screen.More.route`,
`Screen.Appearance.route`, `Screen.AppData.route` as the registered
destinations.

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
