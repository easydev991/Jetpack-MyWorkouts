# Spec Delta

## MODIFIED Requirements

### Requirement: Screen sealed class holds only route

`Screen` SHALL be `sealed class Screen(val route: String)` and SHALL
NOT contain `icon`, `titleResId`, or other UI metadata. Each subclass
is an `object` with a unique `route` string. `Screen` SHALL be the
single source of truth for navigation route strings; the navigation
graph in `RootScreen` SHALL use `Screen.Main.route`, `Screen.Exercises.route`,
`Screen.More.route`, `Screen.Appearance.route`, `Screen.AppData.route`,
`Screen.EntryCreate.route`, `Screen.ExerciseCreate.route`,
`Screen.ArchivedExercises.route`, `Screen.EntrySettings.route` as the
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

#### Scenario: ArchivedExercises object added to sealed Screen

- **WHEN** `Screen.ArchivedExercises` is referenced
- **THEN** `Screen.ArchivedExercises.route == "archived_exercises"`
- **AND** `Screen.ArchivedExercises` is an `object` instance of
  `Screen`
- **AND** `Screen.ArchivedExercises` НЕ входит в `BottomTabs` —
  паттерн `Screen.Appearance`

#### Scenario: EntrySettings object added to sealed Screen

- **WHEN** `Screen.EntrySettings` is referenced
- **THEN** `Screen.EntrySettings.route == "entry_settings"`
- **AND** `Screen.EntrySettings` is an `object` instance of `Screen`
- **AND** `Screen.EntrySettings` НЕ входит в `BottomTabs` — паттерн
  `Screen.Appearance` (системная панель навигации — см.
  `entry-settings-screen/spec.md`)
- **AND** нижняя навигация приложения скрыта при активном маршруте
  `entry_settings` — как на экране создания/изменения записи
