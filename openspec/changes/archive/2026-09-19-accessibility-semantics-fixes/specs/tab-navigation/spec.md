# Spec Delta

## MODIFIED Requirements

### Requirement: NavigationBar highlights the active tab via currentBackStackEntryAsState

`RootScreen` SHALL derive the active tab from `navController.currentBackStackEntryAsState()?.destination?.route` (NOT from a separate `ViewModel`). A `NavigationBarItem` SHALL be `selected = true` when the current route equals its `tab.route`. Иконка `NavigationBarItem` SHALL NOT иметь собственного текстового описания (`contentDescription = null`): название вкладки читается скринридером один раз из видимого лейбла.

#### Scenario: Active tab indicator follows navigation

- **WHEN** user is on the `More` tab
- **THEN** the `NavigationBarItem` for `More` shows the `selected` styling
- **AND** the `Main` tab does not show `selected` styling

#### Scenario: NavigationBar iterates BottomTabs

- **WHEN** `RootScreen` is composed
- **THEN** its `NavigationBar` iterates the visible tabs
  (`BottomTabs` filtered by the `show_exercises_tab` setting) via
  `forEach { tab -> ... }`
- **AND** the number of `NavigationBarItem`s equals the number of visible
  tabs (3 with the setting on, 2 with it off)

#### Scenario: Tab name is announced once

- **WHEN** скринридер читает элемент нижней навигации
- **THEN** название вкладки озвучивается один раз — из видимого лейбла
- **AND** иконка вкладки не добавляет дублирующее текстовое описание

#### Scenario: Appearance destination has no NavigationBarItem

- **WHEN** user is on `Screen.Appearance.route`
- **THEN** no `NavigationBarItem` is `selected`
