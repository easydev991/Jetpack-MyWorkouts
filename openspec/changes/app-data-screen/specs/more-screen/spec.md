## ADDED Requirements

### Requirement: MoreScreen has app data button

На экране «Ещё» под кнопкой «Оформление приложения» SHALL быть кнопка
«Данные приложения» / "App data" (`FilledTonalButton`, центрирование и
отступы — по образцу существующих кнопок). Нажатие SHALL выполнять
переход на маршрут `app_data` с `popUpTo(Screen.More.route)`. Строка
`app_data` SHALL существовать в `res/values/strings.xml` («Данные
приложения») и `res/values-en/strings.xml` ("App data").

#### Scenario: Russian locale button text

- **WHEN** `MoreScreen` composed с активной русской локалью
- **THEN** текст кнопки — «Данные приложения»

#### Scenario: English locale button text

- **WHEN** `MoreScreen` composed с активной английской локалью
- **THEN** текст кнопки — "App data"

#### Scenario: Clicking the button navigates to app data screen

- **WHEN** пользователь нажимает кнопку «Данные приложения» / "App data"
- **THEN** выполняется `navController.navigate(Screen.AppData.route)` с
  `popUpTo(Screen.More.route)`
- **AND** активным становится экран «Данные приложения»
