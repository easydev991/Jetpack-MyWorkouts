## MODIFIED Requirements

### Requirement: MoreScreen Scaffold with TopAppBar

`MoreScreen` SHALL be a `@Composable fun MoreScreen(modifier: Modifier = Modifier)` annotated with `@OptIn(ExperimentalMaterial3Api::class)`. Its `Scaffold` SHALL contain `topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) }`. The `Scaffold` body SHALL contain a `Text` with the app version at the bottom, aligned via `Alignment.BottomCenter`.

#### Scenario: MoreScreen renders with localized title

- **WHEN** `MoreScreen` is composed with the Russian locale active
- **THEN** the `TopAppBar` displays "Ещё"
- **AND** the `Text` with the version string is displayed at the bottom of the body

## ADDED Requirements

### Requirement: MoreScreen version text shows BuildConfig.VERSION_NAME

`MoreScreen` SHALL display the value of `BuildConfig.VERSION_NAME` formatted via `stringResource(R.string.app_version, BuildConfig.VERSION_NAME)`. The `Text` SHALL use `MaterialTheme.typography.bodyMedium`, `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)`, `TextAlign.Center`, and `Modifier.fillMaxWidth()`. The string `app_version` SHALL exist in both `res/values/strings.xml` (Russian: "Версия приложения: %1$s") and `res/values-en/strings.xml` (English: "App version: %1$s") with a `%1$s` placeholder for the version value.

#### Scenario: Russian locale shows version

- **WHEN** `MoreScreen` is composed with the Russian locale active and `BuildConfig.VERSION_NAME = "1.0.0"`
- **THEN** the displayed text is "Версия приложения: 1.0.0"

#### Scenario: English locale shows version

- **WHEN** `MoreScreen` is composed with the English locale active and `BuildConfig.VERSION_NAME = "1.0.0"`
- **THEN** the displayed text is "App version: 1.0.0"
