# Spec Delta

## MODIFIED Requirements

### Requirement: Screen sealed class holds only route

`Screen` SHALL be `sealed class Screen(val route: String)` and SHALL
NOT contain `icon`, `titleResId`, or other UI metadata. Each subclass
is an `object` with a unique `route` string. `Screen` SHALL be the
single source of truth for navigation route strings.

#### Scenario: Registered destinations use Screen routes

- **WHEN** the navigation graph in `RootScreen` is composed
- **THEN** it uses `Screen.Main.route`, `Screen.Exercises.route`,
  `Screen.More.route`, `Screen.Appearance.route`, `Screen.AppData.route`,
  `Screen.EntryCreate.route`, `Screen.ExerciseCreate.route`,
  `Screen.ArchivedExercises.route`, `Screen.EntrySettings.route`,
  `Screen.CategoryCreate.route`, `Screen.Overview.route`,
  `Screen.OverviewSettings.route`, `Screen.EntryDetail.route` as the
  registered destinations

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

## ADDED Requirements

### Requirement: EntryDetail object added to sealed Screen

`Screen.EntryDetail` SHALL быть
`object EntryDetail : Screen(route = "entry_detail/{entryId}")` с
обязательным path-аргументом `entryId` (`NavType.LongType`, без
`defaultValue`) — паттерн JetpackDays `Screen.ItemDetail`, в отличие от
query-аргументов `EntryCreate`/`ExerciseCreate` (у деталей нет
create-режима). Объект SHALL содержать `ARG_ENTRY_ID` и
`createRoute(entryId: Long)`; маршрут без id SHALL NOT матчиться;
`Screen` остаётся единственным источником истины маршрутов.

#### Scenario: Константы и фабрика маршрута

- **WHEN** объявляется `Screen.EntryDetail`
- **THEN** он содержит `const val ARG_ENTRY_ID: String = "entryId"`,
  фабрику маршрута
  `fun createRoute(entryId: Long): String = "entry_detail/$entryId"` и
  `val arguments = listOf(navArgument(ARG_ENTRY_ID) { type =
  NavType.LongType })` — без `defaultValue`
- **AND** `Screen.EntryDetail` declared as `object` inside sealed
  `Screen`

#### Scenario: Фабрика маршрута с entryId

- **WHEN** `Screen.EntryDetail.createRoute(7)` вычислен
- **THEN** результат `"entry_detail/7"`

#### Scenario: Маршрут без аргумента не матчится

- **WHEN** выполняется попытка навигации на `"entry_detail"` без аргумента
- **THEN** маршрут не матчится: `entryId` — обязательный path-аргумент без
  `defaultValue`

#### Scenario: NavHost registers EntryDetail destination

- **WHEN** `RootScreen` is composed
- **THEN** `NavHost` has a
  `composable(Screen.EntryDetail.route, arguments = ...) { ... }`
  destination рядом с `EntryCreate`
- **AND** `entryId` из аргументов backStackEntry передаётся в фабрику
  `EntryDetailViewModel`
- **AND** экран открывается переходом с главного экрана (тап по строке
  или пункт «Открыть» меню), системный Back возвращает на главный экран

#### Scenario: Аналитика маршрута EntryDetail

- **WHEN** `screenForRoute` вычисляется для `entry_detail/{entryId}`
- **THEN** маршрут отображается на значение
  `AppScreen.EntryDetailScreen` для аналитики просмотра экранов

#### Scenario: Bottom navigation скрыта на EntryDetail

- **WHEN** активный route is `Screen.EntryDetail.route`
- **THEN** нижний бар навигации скрыт (контракт — `BottomTab`-бар не
  отображается; реализация в `RootScreen` оставлена на усмотрение
  разработчика — конкретное имя локального флага/переменной не фиксируется)
- **AND** активный экран — детальный экран записи (см. capability
  `workout-entry-detail-screen`)
- **AND** контракт — `shouldShowBottomBar = currentRoute in tabs.map {
  it.route }`: `entry_detail` не входит в список, правок `RootScreen`
  для скрытия бара не требуется
