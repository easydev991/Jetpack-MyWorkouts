# Spec Delta

## ADDED Requirements

### Requirement: Маршруты экрана обзора и его настроек

`Screen.Overview : Screen(route = "overview")` и
`Screen.OverviewSettings : Screen(route = "overview_settings")` —
нетабовые маршруты по паттерну `Screen.Appearance`: без аргументов, без
UI-метаданных, `Screen` остаётся единственным источником строк маршрутов.
Оба маршрута SHALL быть зарегистрированы в `NavHost` `RootScreen`;
`Overview` открывается с главного экрана (кнопка обзора в `TopAppBar`),
`OverviewSettings` — с экрана обзора (кнопка настроек), системный Back
возвращает на предыдущий экран. `screenForRoute` SHALL отображать маршруты
на значения `AppScreen.OverviewScreen` и `AppScreen.OverviewSettingsScreen`
для аналитики просмотра экранов. Нижняя панель навигации SHALL быть скрыта
на обоих маршрутах — существующий контракт
`shouldShowBottomBar = currentRoute in tabs.map { it.route }`:
`overview` и `overview_settings` не входят в список табов, правок
`RootScreen` для скрытия не требуется.

#### Scenario: Регистрация и переход

- **WHEN** пользователь нажимает кнопку обзора на главном экране
- **THEN** `NavHost` навигирует на `Screen.Overview.route` и `screenForRoute` отдаёт `AppScreen.OverviewScreen`

#### Scenario: Bottom navigation скрыта на экранах обзора

- **WHEN** активен маршрут `overview` или `overview_settings`
- **THEN** нижняя панель навигации не отображается
- **AND** контент экрана использует освободившееся место без двойных отступов

#### Scenario: Переход к настройкам обзора и возврат

- **WHEN** на экране обзора нажимается кнопка настроек, затем системный Back
- **THEN** открывается `Screen.OverviewSettings.route`, затем Back возвращает на экран обзора
