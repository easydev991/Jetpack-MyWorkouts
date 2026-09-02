# appearance Specification

## Purpose

Экран настройки внешнего вида приложения: выбор темы (системная, светлая,
тёмная) и динамических цветов Material You, с сохранением выбора в
DataStore и применением темы ко всему приложению.

## Requirements

### Requirement: Appearance screen renders theme selection

`AppearanceScreen` SHALL be a `@Composable fun AppearanceScreen(viewModel:
AppearanceViewModel, onBackClick: () -> Unit)`. The `Scaffold` SHALL contain
a `topBar` with `TopAppBar` showing localized title from
`R.string.appearance_title` and a navigation icon button (`Icons.AutoMirrored.Filled.ArrowBack`)
with `contentDescription` from `R.string.back` that calls `onBackClick`. The
body SHALL be a `Column(verticalArrangement = Arrangement.spacedBy(16.dp))`
with `modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
.padding(paddingValues).padding(16.dp)` (the modifier order matters:
`padding(paddingValues)` consumes the `Scaffold` content insets,
`padding(16.dp)` adds the screen-body margin; see
`AppearanceScreen.kt:117–125`), containing a `ThemeSection` and — when
dynamic colors are available on the device — a `DynamicColorsSection`,
separated by a `HorizontalDivider`.

#### Scenario: Russian locale shows localized title and back content description

- **WHEN** `AppearanceScreen` is composed with the Russian locale active
- **THEN** the `TopAppBar` displays "Оформление приложения"
- **AND** the navigation icon's `contentDescription` is "Назад"

#### Scenario: English locale shows localized title and back content description

- **WHEN** `AppearanceScreen` is composed with the English locale active
- **THEN** the `TopAppBar` displays "Appearance"
- **AND** the navigation icon's `contentDescription` is "Back"

#### Scenario: Tapping back calls onBackClick

- **WHEN** user taps the back navigation icon
- **THEN** `onBackClick` is invoked

### Requirement: ThemeSection exposes three radio options

`ThemeSection` SHALL display the localized section header from
`R.string.app_theme` and exactly three selectable rows — one per
`AppTheme` value (`LIGHT`, `DARK`, `SYSTEM`). Each row SHALL consist of
a `RadioButton` and a `Text` label localized via `R.string.light`,
`R.string.dark`, `R.string.system` respectively. The `Row` SHALL be
marked `Modifier.selectable(selected, onClick, enabled, role = Role.RadioButton)`
with `height(56.dp)`. The currently selected theme's row SHALL have
`enabled = false` (selected radio is not re-clickable).

#### Scenario: All three theme rows are displayed

- **WHEN** `AppearanceScreen` is composed
- **THEN** three rows are visible with labels "Светлая" / "Тёмная" /
  "Системная" (ru) or "Light" / "Dark" / "System" (en)

#### Scenario: Exactly one row is selected per AppTheme

- **WHEN** `AppearanceScreenContent` is rendered with `state.theme = LIGHT`
- **THEN** the row labeled "Светлая" / "Light" is selected
- **AND** the rows labeled "Тёмная"/"Dark" and "Системная"/"System" are
  not selected

#### Scenario: Clicking an unselected theme row invokes callback

- **WHEN** user clicks a non-selected theme row (e.g. "Тёмная"/"Dark"
  when current theme is `LIGHT`)
- **THEN** `onThemeChange(DARK)` is invoked

### Requirement: DynamicColorsSection is conditionally rendered

`AppearanceScreen` SHALL hide the entire `DynamicColorsSection` (and the
preceding `HorizontalDivider` MUST NOT become an orphan) when
`isDynamicColorsAvailable = false` (i.e. on devices with
`Build.VERSION.SDK_INT < S` / Android < 12). When
`isDynamicColorsAvailable = true`, the section SHALL contain a header
localized via `R.string.dynamic_colors`, a `Row` with localized label
`R.string.use_dynamic_colors` + a `Switch(checked = useDynamicColors,
onCheckedChange = onDynamicColorsChange)`, and a hint localized via
`R.string.dynamic_colors_hint` rendered as `Text(typography.bodySmall)`.

#### Scenario: Section visible when dynamic colors available

- **WHEN** `AppearanceScreenContent` is rendered with
  `state.isDynamicColorsAvailable = true`
- **THEN** the section header "Динамические цвета" / "Dynamic colors"
  is displayed
- **AND** the label "Использовать динамические цвета" / "Use dynamic
  colors" is displayed
- **AND** exactly one toggleable `Switch` is present in the tree

#### Scenario: Section hidden when dynamic colors unavailable

- **WHEN** `AppearanceScreenContent` is rendered with
  `state.isDynamicColorsAvailable = false`
- **THEN** the section header "Динамические цвета" / "Dynamic colors"
  does NOT exist in the tree
- **AND** the switch label does NOT exist
- **AND** zero toggleable nodes are present

#### Scenario: Toggling the switch invokes callback with inverted value

- **WHEN** user clicks the `Switch` while `useDynamicColors = true`
- **THEN** `onDynamicColorsChange(false)` is invoked
- **WHEN** user clicks the `Switch` while `useDynamicColors = false`
- **THEN** `onDynamicColorsChange(true)` is invoked

### Requirement: isDynamicColorsAvailable is computed outside Content

`AppearanceScreen` (outer wrapper) SHALL compute
`isDynamicColorsAvailable` once via
`remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }` and pass
it to `AppearanceScreenState.isDynamicColorsAvailable`. The internal
`AppearanceScreenContent` SHALL consume the value from state and SHALL
NOT call `Build.VERSION.SDK_INT` itself — this keeps `Content` a pure
function of `AppearanceScreenState` for deterministic UI tests.

#### Scenario: Content receives isDynamicColorsAvailable as state

- **WHEN** `AppearanceScreen(viewModel, onBackClick)` is composed on a
  device with Android 12+ (`SDK_INT >= S`)
- **THEN** `AppearanceScreenState.isDynamicColorsAvailable = true` is
  passed to `AppearanceScreenContent`

#### Scenario: Content receives false on Android < 12

- **WHEN** `AppearanceScreen(viewModel, onBackClick)` is composed on a
  device with Android 11 or older (`SDK_INT < S`)
- **THEN** `AppearanceScreenState.isDynamicColorsAvailable = false` is
  passed to `AppearanceScreenContent`

### Requirement: AppTheme domain enum with three values

`AppTheme` SHALL be a Kotlin `enum class` in
`com.myworkouts.domain.model` with exactly three entries named
`LIGHT`, `DARK`, `SYSTEM`. The names SHALL be the contract with
`AppSettingsDataStore` (which persists the choice as
`AppTheme.name`).

#### Scenario: AppTheme has exactly three entries

- **WHEN** `AppTheme.entries` is iterated
- **THEN** the set of names is `{"LIGHT", "DARK", "SYSTEM"}` (no more,
  no less)

### Requirement: AppTheme.isDark resolves to concrete dark/light

`AppTheme.isDark(systemDark: Boolean): Boolean` SHALL return `false` for
`AppTheme.LIGHT`, `true` for `AppTheme.DARK`, and `systemDark` for
`AppTheme.SYSTEM`.

#### Scenario: LIGHT resolves to false regardless of system

- **WHEN** `AppTheme.LIGHT.isDark(systemDark = true)` is called
- **THEN** the result is `false`
- **WHEN** `AppTheme.LIGHT.isDark(systemDark = false)` is called
- **THEN** the result is `false`

#### Scenario: DARK resolves to true regardless of system

- **WHEN** `AppTheme.DARK.isDark(systemDark = true)` is called
- **THEN** the result is `true`
- **WHEN** `AppTheme.DARK.isDark(systemDark = false)` is called
- **THEN** the result is `true`

#### Scenario: SYSTEM resolves to system value

- **WHEN** `AppTheme.SYSTEM.isDark(systemDark = true)` is called
- **THEN** the result is `true`
- **WHEN** `AppTheme.SYSTEM.isDark(systemDark = false)` is called
- **THEN** the result is `false`

### Requirement: AppSettingsDataStore persists theme and dynamic colors

`AppSettingsDataStore(context: Context)` SHALL expose `val theme:
Flow<AppTheme>` and `val useDynamicColors: Flow<Boolean>`, plus suspend
setters `setTheme(theme: AppTheme)` and `setUseDynamicColors(useDynamicColors:
Boolean)`. The underlying `DataStore<Preferences>` SHALL be named
`appearance_prefs`. Defaults SHALL be `AppTheme.SYSTEM` and `true`
respectively. An unrecognized stored theme name SHALL fall back to
`AppTheme.SYSTEM` (swallowed `IllegalArgumentException`).

#### Scenario: theme returns SYSTEM on first launch

- **WHEN** `AppSettingsDataStore.theme` is collected with no prior writes
- **THEN** the first emitted value is `AppTheme.SYSTEM`

#### Scenario: useDynamicColors returns true on first launch

- **WHEN** `AppSettingsDataStore.useDynamicColors` is collected with no
  prior writes
- **THEN** the first emitted value is `true`

#### Scenario: setTheme persists and theme Flow re-emits

- **WHEN** `setTheme(AppTheme.DARK)` is awaited
- **THEN** subsequent `theme` Flow collection emits `AppTheme.DARK`

#### Scenario: setUseDynamicColors persists and useDynamicColors Flow re-emits

- **WHEN** `setUseDynamicColors(false)` is awaited
- **THEN** subsequent `useDynamicColors` Flow collection emits `false`

#### Scenario: Unrecognized stored theme name falls back to SYSTEM

- **WHEN** the stored `theme` key has a value that is not a valid
  `AppTheme.name`
- **THEN** `theme` Flow emits `AppTheme.SYSTEM`

### Requirement: AppearanceViewModel exposes state and update methods

`AppearanceViewModel(dataStore: AppSettingsDataStore) : ViewModel` SHALL
expose `val uiState: StateFlow<AppearanceUiState>` and methods
`updateTheme(theme: AppTheme)` and `updateDynamicColors(useDynamicColors:
Boolean)`. `uiState` SHALL be derived from
`combine(dataStore.theme, dataStore.useDynamicColors).stateIn(viewModelScope,
SharingStarted.WhileSubscribed(5000L), AppearanceUiState())`.
`updateTheme` SHALL launch `dataStore.setTheme(theme)` in `viewModelScope`;
similarly for `updateDynamicColors`. The companion SHALL provide
`fun factory(dataStore: AppSettingsDataStore): ViewModelProvider.Factory`
via `viewModelFactory { initializer { AppearanceViewModel(dataStore) } }`.

#### Scenario: uiState combines theme and useDynamicColors

- **WHEN** the ViewModel is constructed with `dataStore.theme` emitting
  `DARK` and `dataStore.useDynamicColors` emitting `false`
- **THEN** `uiState.value` is `AppearanceUiState(theme = DARK, useDynamicColors = false)`

#### Scenario: updateTheme delegates to dataStore

- **WHEN** `updateTheme(AppTheme.LIGHT)` is called and the coroutine
  completes
- **THEN** `dataStore.setTheme(AppTheme.LIGHT)` has been invoked exactly once

#### Scenario: updateDynamicColors delegates to dataStore

- **WHEN** `updateDynamicColors(true)` is called and the coroutine
  completes
- **THEN** `dataStore.setUseDynamicColors(true)` has been invoked exactly once

#### Scenario: factory creates AppearanceViewModel with provided dataStore

- **WHEN** `AppearanceViewModel.factory(dataStore)` is used with
  `viewModel<T>(factory = ...)` and the ViewModel is resolved
- **THEN** the resulting `AppearanceViewModel`'s dataStore is the
  provided instance

### Requirement: AppearanceUiState is a plain data class

`AppearanceUiState` SHALL be a Kotlin `data class` with parameters
`theme: AppTheme = AppTheme.SYSTEM` and `useDynamicColors: Boolean = true`
(NOT sealed — the appearance screen has no async/Loading/Error scenarios;
it's a plain snapshot of two settings).

#### Scenario: Default state matches DataStore defaults

- **WHEN** `AppearanceUiState()` is constructed with no arguments
- **THEN** `theme = AppTheme.SYSTEM` and `useDynamicColors = true`

### Requirement: JetpackMyWorkoutsTheme applies AppTheme

`JetpackMyWorkoutsTheme` SHALL accept `appTheme: AppTheme =
AppTheme.SYSTEM`, `dynamicColor: Boolean = true`, and
`content: @Composable () -> Unit`. The composable SHALL compute
`val useDarkTheme = appTheme.isDark(isSystemInDarkTheme())`. When
`dynamicColor` is `true` AND `Build.VERSION.SDK_INT >=
Build.VERSION_CODES.S`, it SHALL use `dynamicLightColorScheme` /
`dynamicDarkColorScheme(context)` based on `useDarkTheme`; otherwise
it SHALL use the static `LightColorScheme` / `DarkColorScheme`.

#### Scenario: SYSTEM theme follows isSystemInDarkTheme

- **WHEN** `JetpackMyWorkoutsTheme(appTheme = AppTheme.SYSTEM,
  dynamicColor = false)` is composed AND `isSystemInDarkTheme()`
  returns `true`
- **THEN** `useDarkTheme = true` and `DarkColorScheme` is used

#### Scenario: LIGHT theme overrides system

- **WHEN** `JetpackMyWorkoutsTheme(appTheme = AppTheme.LIGHT,
  dynamicColor = false)` is composed
- **THEN** `useDarkTheme = false` and `LightColorScheme` is used
  regardless of `isSystemInDarkTheme()`

#### Scenario: DARK theme overrides system

- **WHEN** `JetpackMyWorkoutsTheme(appTheme = AppTheme.DARK,
  dynamicColor = false)` is composed
- **THEN** `useDarkTheme = true` and `DarkColorScheme` is used
  regardless of `isSystemInDarkTheme()`

### Requirement: MainActivity creates DataStore and subscribes directly

`MainActivity.onCreate` SHALL create exactly one
`AppSettingsDataStore(applicationContext)` instance BEFORE `setContent`
(passing the application context to avoid Activity leaks). Inside
`setContent`, the activity SHALL subscribe to
`dataStore.theme.collectAsState(initial = AppTheme.SYSTEM)` and
`dataStore.useDynamicColors.collectAsState(initial = true)`, compute
`val isDarkTheme = theme.isDark(isSystemInDarkTheme())`, and call
`LaunchedEffect(isDarkTheme) { enableEdgeToEdge(statusBarStyle = if
(isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else
SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)) }` to
recolor the status bar on theme change. `RootScreen(dataStore = dataStore)`
SHALL be composed inside `JetpackMyWorkoutsTheme(appTheme = theme,
dynamicColor = useDynamicColors)`.

#### Scenario: DataStore created exactly once per process

- **WHEN** `MainActivity.onCreate` is invoked
- **THEN** exactly one `AppSettingsDataStore` is constructed with
  `applicationContext` (no second instance may be created for the same
  `appearance_prefs` file — would throw `IllegalStateException`)

#### Scenario: Status bar style follows theme at runtime

- **WHEN** user is in the app and updates theme to `DARK` (via
  `AppearanceViewModel.updateTheme(DARK)`)
- **THEN** `LaunchedEffect(isDarkTheme = true)` re-runs and
  `enableEdgeToEdge` is called with `SystemBarStyle.dark(Color.TRANSPARENT)`
- **AND** the status bar icons become light (visible on dark background)

#### Scenario: Persistence across app restarts

- **WHEN** user selects `DARK` theme and force-stops the app
- **THEN** on next launch, `dataStore.theme` first emits `DARK`
- **AND** the entire UI (including status bar) renders in dark mode
  from the start
