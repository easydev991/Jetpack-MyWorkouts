## MODIFIED Requirements

### Requirement: MainActivity creates DataStore and subscribes directly

`MyWorkoutsApplication` SHALL create exactly one
`AppSettingsDataStore(applicationContext)` instance, lazily (`val
dataStore: AppSettingsDataStore by lazy { ... }`), so that the
singleton scope is the **process** (`Application`), not per-Activity —
пересоздание Activity (смена конфигурации) более не плодит
инстансов DataStore и `InvalidationTracker` нотифицирует одну
подписку. `MainActivity.onCreate` SHALL получить этот инстанс через
`val dataStore = (application as MyWorkoutsApplication).dataStore`
BEFORE `setContent`. Внутри `setContent` активность подписывается на
`dataStore.theme.collectAsState(initial = AppTheme.SYSTEM)` и
`dataStore.useDynamicColors.collectAsState(initial = true)`,
вычисляет `val isDarkTheme = theme.isDark(isSystemInDarkTheme())` и
вызывает `LaunchedEffect(isDarkTheme) { enableEdgeToEdge(statusBarStyle
= if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else
SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)) }` для
перекраски статус-бара при смене темы. `RootScreen(dataStore =
dataStore)` SHALL компониться внутри `JetpackMyWorkoutsTheme(appTheme
= theme, dynamicColor = useDynamicColors)`.

#### Scenario: DataStore singleton lives on MyWorkoutsApplication

- **WHEN** the application process starts and
  `MyWorkoutsApplication.dataStore` is first accessed
- **THEN** exactly one `AppSettingsDataStore` is constructed with
  `applicationContext` (lazy — не создаётся в `onCreate` до первого
  обращения; повторные обращения возвращают тот же instance — второй
  instance на тот же файл `appearance_prefs` бросил бы
  `IllegalStateException`)

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

> Полностью заменяет канонное требование с тем же именем в
> `openspec/specs/appearance/spec.md` — ранее требовало создания
> `AppSettingsDataStore` в `MainActivity.onCreate` (записано
> `fix-entry-list-live-update` без синхронизации, этот change
> закрывает долг).

## ADDED Requirements

### Requirement: AppSettingsDataStore persists archive-dialog preference

`AppSettingsDataStore` SHALL expose
`val dontShowArchiveDialog: Flow<Boolean>` (default `false`) и
suspend-метод `setDontShowArchiveDialog(value: Boolean)`. Ключ SHALL
храниться в том же файле `appearance_prefs` (БЕЗ отдельного
`DataStore` и БЕЗ миграции — Preferences DataStore расширяет схему
автоматически при добавлении нового ключа; существующие установки
видят default `false` до явного opt-out пользователя).

#### Scenario: dontShowArchiveDialog returns false on first launch

- **WHEN** `AppSettingsDataStore.dontShowArchiveDialog` is collected
  with no prior writes
- **THEN** the first emitted value is `false`

#### Scenario: setDontShowArchiveDialog persists and Flow re-emits

- **WHEN** `setDontShowArchiveDialog(true)` is awaited
- **THEN** subsequent `dontShowArchiveDialog` Flow collection emits
  `true`
- **WHEN** `setDontShowArchiveDialog(false)` is awaited
- **THEN** subsequent `dontShowArchiveDialog` Flow collection emits
  `false`
