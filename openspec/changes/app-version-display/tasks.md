## 1. Gradle configuration

- [x] 1.1 Add `VERSION_NAME=1.0.0` and `VERSION_CODE=1` to `gradle.properties`
- [x] 1.2 Replace hardcoded `versionCode = 1` / `versionName = "1.0"` in `app/build.gradle.kts` `defaultConfig` with `project.findProperty(...)?.toString()?.toInt()` / `project.findProperty(...)?.toString()` reads
- [x] 1.3 Add `buildConfig = true` to `buildFeatures` in `app/build.gradle.kts`
- [x] 1.4 Verify with `make build` — Gradle config valid; `BuildConfig` generation confirmed in step 3.2

## 2. Localization

- [x] 2.1 Add `<string name="app_version">Версия приложения: %1$s</string>` to `app/src/main/res/values/strings.xml`
- [x] 2.2 Add `<string name="app_version">App version: %1$s</string>` to `app/src/main/res/values-en/strings.xml`
- [x] 2.3 Verify with `make build` — string resources compile

## 3. MoreScreen UI

- [x] 3.1 Add imports to `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt`: `Box`, `fillMaxSize`, `fillMaxWidth`, `padding`, `Alignment`, `MaterialTheme`, `Text`, `TextAlign`, `com.myworkouts.BuildConfig`, `androidx.compose.ui.unit.dp`
- [x] 3.2 Add to `Scaffold` body in `MoreScreen.kt`: `Box(Modifier.fillMaxSize().padding(paddingValues).padding(8.dp))` containing `Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), textAlign = TextAlign.Center)`
- [x] 3.3 Verify with `make build` — `BuildConfig.VERSION_NAME` import compiles (confirms `buildConfig = true` works)

## 4. OpenSpec spec synchronization

- [x] 4.1 In `openspec/specs/more-screen/spec.md`, modify `Requirement: MoreScreen Scaffold with TopAppBar`: replace "The `Scaffold` body SHALL be empty" with "The `Scaffold` body SHALL contain a `Text` with the app version at the bottom, aligned via `Alignment.BottomCenter`"
- [x] 4.2 In the same requirement, update the related `Scenario: MoreScreen renders with localized title`: replace "**AND** the `Scaffold` body is empty" with "**AND** the `Text` with the version string is displayed at the bottom of the body"
- [x] 4.3 Append new `Requirement: MoreScreen version text shows BuildConfig.VERSION_NAME` to the end of `openspec/specs/more-screen/spec.md` with two scenarios (Russian and English locale)

## 5. Final verification

- [x] 5.1 Run `make format && make lint && make test` — all green
- [ ] 5.2 Run `make install` and manually verify on device/emulator: tab "Ещё" shows "Версия приложения: 1.0.0" in Russian; switch device language to English — text becomes "App version: 1.0.0"
