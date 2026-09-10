## ADDED Requirements

### Requirement: MoreScreen имеет кнопку видимости вкладки «Упражнения»

На экране «Ещё» сразу после навигационных кнопок и до feedback-кнопки
(`R.string.feedback_send`) SHALL быть кнопка (`FilledTonalButton`, в стиле
остальных кнопок экрана) переключения видимости вкладки «Упражнения».
`MoreScreen` SHALL принимать параметры `showExercisesTab: Boolean` и
`onShowExercisesTabChange: (Boolean) -> Unit` (состояние и запись — выше по
потоку, в `RootScreen`; у экрана нет ViewModel). Надпись кнопки SHALL
зависеть от состояния: `R.string.more_exercises_tab_show` («Показать вкладку
"Упражнения"» / "Show Exercises tab") при `showExercisesTab = false`,
`R.string.more_exercises_tab_hide` («Скрыть вкладку "Упражнения"» /
"Hide Exercises tab") при `showExercisesTab = true`; нажатие кнопки SHALL
вызывать `onShowExercisesTabChange` с новым значением.

#### Scenario: Russian locale button label when tab is hidden

- **WHEN** `MoreScreen` composed с активной русской локалью и
  `showExercisesTab = false`
- **THEN** на кнопке виден текст «Показать вкладку "Упражнения"»

#### Scenario: English locale button label when tab is hidden

- **WHEN** `MoreScreen` composed с активной английской локалью и
  `showExercisesTab = false`
- **THEN** на кнопке виден текст "Show Exercises tab"

#### Scenario: Надпись отражает состояние настройки

- **WHEN** `MoreScreen(navController, showExercisesTab = true, ...)`
- **THEN** на кнопке текст «Скрыть вкладку "Упражнения"» /
  "Hide Exercises tab"

#### Scenario: Нажатие кнопки вызывает колбэк

- **WHEN** пользователь нажимает кнопку при `showExercisesTab = false`
- **THEN** вызывается `onShowExercisesTabChange(true)`

### Requirement: Настройка видимости вкладки сохраняется в DataStore

Настройка SHALL храниться в `AppSettingsDataStore` (ключ
`show_exercises_tab`, файл `appearance_prefs` — правило хранилища «новые
ключи — точечно по фичам»): `Flow<Boolean>` с дефолтом `false` при первом
запуске + suspend-setter. `RootScreen` SHALL собирать настройку из
`dataStore` (тот же экземпляр, что передан из `MainActivity`) и при
переключении кнопки писать её в DataStore. Включение настройки SHALL
добавлять вкладку «Упражнения» в `NavigationBar`, выключение — убирать
(видимые вкладки — см. `tab-navigation/spec.md`).

#### Scenario: Первый запуск — вкладка скрыта

- **WHEN** приложение запускается впервые (ключа в DataStore нет)
- **THEN** `showExercisesTab` эмитит `false`
- **AND** в нижней навигации нет вкладки «Упражнения»

#### Scenario: Настройка переживает перезапуск

- **WHEN** пользователь включил настройку, перезапустил приложение
- **THEN** `showExercisesTab` эмитит `true`
- **AND** вкладка «Упражнения» видна в нижней навигации

#### Scenario: Переключение настройки меняет нижнюю навигацию

- **WHEN** настройка включена и пользователь выключает её кнопкой
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
без default-значений (design Decision 8): пара используется кнопкой
видимости вкладки из ADDED-требования выше.

#### Scenario: NavController is passed from RootScreen

- **WHEN** `RootScreen` composes the `More` destination
- **THEN** it invokes `MoreScreen(navController = navController,
  showExercisesTab = showExercisesTab, onShowExercisesTabChange =
  onShowExercisesTabChange)` with the `RootScreen`'s
  `rememberNavController()` and the setting collected from
  `AppSettingsDataStore`
