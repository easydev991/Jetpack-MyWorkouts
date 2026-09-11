## MODIFIED Requirements

### Requirement: Screen sealed class holds only route

**Reason**: После добавления `Screen.ExerciseCreate` (ADDED ниже) список
зарегистрированных маршрутов в `RootScreen` расширяется — канон должен
отражать это изменение. Заодно фиксируется отсутствующий в каноне
`Screen.EntryCreate.route` (рассинхрон главной спеки с кодом —
`Screen.EntryCreate` существует в `Screen.kt`, но в существующей
главной спеке не упомянут; правка попутно возвращает спеку в
соответствие с реальным кодом).

**Migration**: Перечень маршрутов в существующем требовании
(`openspec/specs/non-tab-screen-navigation/spec.md`, L17–19) заменяется
на расширенный, включающий `Screen.ExerciseCreate.route`:

> the navigation graph in `RootScreen` SHALL use `Screen.Main.route`,
> `Screen.Exercises.route`, `Screen.More.route`, `Screen.Appearance.route`,
> `Screen.AppData.route`, `Screen.EntryCreate.route`,
> `Screen.ExerciseCreate.route` as the registered destinations.

Scenarios существующего требования (`Screen has only route parameter`,
`Appearance object added to sealed Screen`, `AppData object added to
sealed Screen`, `Exercises object added to sealed Screen`) переезжают
в MODIFIED-требование как есть (без изменений).

`Screen` SHALL be `sealed class Screen(val route: String)` and SHALL
NOT contain `icon`, `titleResId`, or other UI metadata. Each subclass
is an `object` with a unique `route` string. `Screen` SHALL be the
single source of truth for navigation route strings; the navigation
graph in `RootScreen` SHALL use `Screen.Main.route`, `Screen.Exercises.route`,
`Screen.More.route`, `Screen.Appearance.route`, `Screen.AppData.route`,
`Screen.EntryCreate.route`, `Screen.ExerciseCreate.route` as the
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

## ADDED Requirements

### Requirement: ExerciseCreate object added to sealed Screen

`Screen.ExerciseCreate` SHALL быть `object ExerciseCreate : Screen(route =
"exercise_create")`. Route SHALL быть стабильной строкой
`"exercise_create"` и SHALL регистрироваться в `NavHost` `RootScreen` как
отдельный `composable("exercise_create") { ... }` (без аргументов —
упражнение всегда создаётся, редактирования в этом change нет). `Screen`
остаётся единственным источником истины для строк навигационных
маршрутов.

#### Scenario: ExerciseCreate object added to sealed Screen

- **WHEN** `Screen.ExerciseCreate` is referenced
- **THEN** `Screen.ExerciseCreate.route == "exercise_create"`
- **AND** `Screen.ExerciseCreate` is an `object` instance of `Screen`

#### Scenario: NavHost registers ExerciseCreate destination

- **WHEN** `RootScreen` is composed
- **THEN** `NavHost` has a `composable("exercise_create") { ... }`
  destination рядом с `EntryCreate`

#### Scenario: Bottom navigation скрыта на ExerciseCreate

- **WHEN** активный route is `Screen.ExerciseCreate.route`
- **THEN** нижний бар навигации скрыт (контракт — `BottomTab`-бар не
  отображается; реализация в `RootScreen` оставлена на усмотрение
  разработчика — конкретное имя локального флага/переменной не
  фиксируется)
- **AND** активный экран — `ExerciseCreateScreen` (см. capability
  `exercise-create-screen`)