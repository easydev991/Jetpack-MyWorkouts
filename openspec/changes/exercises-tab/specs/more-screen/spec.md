## ADDED Requirements

### Requirement: MoreScreen имеет тоггл «Показывать вкладку "Упражнения"»

На экране «Ещё» сразу после навигационных кнопок и до feedback-кнопки
(`R.string.feedback_send`) SHALL быть строка с `Switch`
и подписью `R.string.more_exercises_tab` («Показывать вкладку
"Упражнения"» / "Show Exercises tab"). `MoreScreen` SHALL принимать параметры
`showExercisesTab: Boolean` и `onShowExercisesTabChange: (Boolean) -> Unit`
(состояние и запись — выше по потоку, в `RootScreen`; у экрана нет
ViewModel). `Switch.checked` SHALL равняться `showExercisesTab`;
переключение SHALL вызывать `onShowExercisesTabChange` с новым значением.

#### Scenario: Russian locale toggle label

- **WHEN** `MoreScreen` composed с активной русской локалью
- **THEN** видна подпись «Показывать вкладку "Упражнения"» рядом со `Switch`

#### Scenario: English locale toggle label

- **WHEN** `MoreScreen` composed с активной английской локалью
- **THEN** видна подпись "Show Exercises tab" рядом со `Switch`

#### Scenario: Switch отражает состояние настройки

- **WHEN** `MoreScreen(navController, showExercisesTab = true, ...)`
- **THEN** `Switch` включён
- **WHEN** `MoreScreen(navController, showExercisesTab = false, ...)`
- **THEN** `Switch` выключен

#### Scenario: Переключение вызывает колбэк

- **WHEN** пользователь taps `Switch` при `showExercisesTab = false`
- **THEN** вызывается `onShowExercisesTabChange(true)`

### Requirement: Настройка видимости вкладки сохраняется в DataStore

Настройка SHALL храниться в `AppSettingsDataStore` (ключ
`show_exercises_tab`, файл `appearance_prefs` — правило хранилища «новые
ключи — точечно по фичам»): `Flow<Boolean>` с дефолтом `false` при первом
запуске + suspend-setter. `RootScreen` SHALL собирать настройку из
`dataStore` (тот же экземпляр, что передан из `MainActivity`) и при
переключении тоггла писать её в DataStore. Включение настройки SHALL
добавлять вкладку «Упражнения» в `NavigationBar`, выключение — убирать
(видимые вкладки — см. `tab-navigation/spec.md`).

#### Scenario: Первый запуск — вкладка скрыта

- **WHEN** приложение запускается впервые (ключа в DataStore нет)
- **THEN** `showExercisesTab` эмитит `false`
- **AND** в нижней навигации нет вкладки «Упражнения»

#### Scenario: Настройка переживает перезапуск

- **WHEN** пользователь включил тоггл, перезапустил приложение
- **THEN** `showExercisesTab` эмитит `true`
- **AND** вкладка «Упражнения» видна в нижней навигации

#### Scenario: Переключение тоггла меняет нижнюю навигацию

- **WHEN** тоггл включён и пользователь выключает его
- **THEN** вкладка «Упражнения» исчезает из `NavigationBar` без
  перезапуска приложения
- **AND** порядок оставшихся вкладок («Тренировки», «Ещё») не меняется

## MODIFIED Requirements

### Requirement: MoreScreen Scaffold with TopAppBar

`MoreScreen` SHALL be a `@Composable fun MoreScreen(navController:
NavHostController, showExercisesTab: Boolean, onShowExercisesTabChange:
(Boolean) -> Unit, modifier: Modifier = Modifier)` annotated with
`@OptIn(ExperimentalMaterial3Api::class)`. Новые параметры — без
default-значений: состояние и запись — выше по потоку, в `RootScreen`
(ADDED-требование про тоггл выше; design Decision 8). Its `Scaffold`
SHALL contain `topBar = { TopAppBar(title = {
Text(stringResource(R.string.more_title)) }) }`. The `Scaffold` body
SHALL contain a `Box` with a `Row(fillMaxWidth,
Arrangement.Center) { FilledTonalButton(...) { Text(stringResource(R.string.appearance_title)) } }`
near the top, and a `Text` with the app version at the bottom, aligned
via `Alignment.BottomCenter`.

#### Scenario: MoreScreen renders with localized title

- **WHEN** `MoreScreen(navController, showExercisesTab = false,
  onShowExercisesTabChange = {})` is composed with the Russian
  locale active
- **THEN** the `TopAppBar` displays "Ещё"
- **AND** the `Text` with the version string is displayed at the bottom
  of the body

#### Scenario: MoreScreen has appearance button

- **WHEN** `MoreScreen(navController, showExercisesTab = false,
  onShowExercisesTabChange = {})` is composed
- **THEN** a `FilledTonalButton` labeled "Оформление приложения" /
  "Appearance" is visible and horizontally centered

### Requirement: MoreScreen accepts a required NavController

`MoreScreen` SHALL accept `navController: NavHostController` as a
required first parameter. The `navController` is used to navigate to
`Screen.Appearance.route` when the appearance button is tapped. There
is no fallback or `LocalNavController` lookup — the parameter is
explicit and required. Параметры `showExercisesTab: Boolean` и
`onShowExercisesTabChange: (Boolean) -> Unit` — также обязательные,
без default-значений (design Decision 8): пара используется строкой
со `Switch` из ADDED-требования выше.

#### Scenario: NavController is passed from RootScreen

- **WHEN** `RootScreen` composes the `More` destination
- **THEN** it invokes `MoreScreen(navController = navController,
  showExercisesTab = showExercisesTab, onShowExercisesTabChange =
  onShowExercisesTabChange)` with the `RootScreen`'s
  `rememberNavController()` and the setting collected from
  `AppSettingsDataStore`
