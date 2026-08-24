## Why

На экране «Ещё» сейчас пустое тело `Scaffold` — пользователь не видит, какая
версия приложения установлена. Эталон `JetpackDays` показывает версию внизу
экрана `MoreScreen` через `BuildConfig.VERSION_NAME`. Переносим этот паттерн в
`Jetpack-MyWorkouts`, чтобы привести UI к единому стилю и дать пользователю
явный идентификатор сборки (полезно при обращении в поддержку).

## What Changes

- `gradle.properties`: добавляем `VERSION_NAME=1.0.0` и `VERSION_CODE=1`.
- `app/build.gradle.kts`: читаем `VERSION_NAME`/`VERSION_CODE` из property
  (паттерн JetpackDays) и включаем `buildFeatures.buildConfig = true` (в AGP 8+
  `BuildConfig` отключён по умолчанию, без него `import com.myworkouts.BuildConfig`
  не скомпилируется).
- `app/src/main/res/values/strings.xml`: добавляем строку `app_version`
  с плейсхолдером `%1$s` — русский вариант «Версия приложения: %1$s».
- `app/src/main/res/values-en/strings.xml`: английский вариант
  «App version: %1$s».
- `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt`: в тело
  `Scaffold` добавляем `Box` с `Alignment.BottomCenter` и `Text` со строкой
  `app_version`, подставленной через `BuildConfig.VERSION_NAME`. Стиль —
  `bodyMedium`, `onSurface.copy(alpha = 0.6f)`, `TextAlign.Center`,
  `fillMaxWidth`.
- `openspec/specs/more-screen/spec.md`: обновляем требование
  `MoreScreen Scaffold with TopAppBar` (тело больше не пустое) и добавляем
  новое требование про отображение версии через `BuildConfig.VERSION_NAME`.

## Capabilities

### New Capabilities

<!-- Capabilities being introduced. Replace <name> with kebab-case identifier (e.g., user-auth, data-export, api-rate-limiting). Each creates specs/<name>/spec.md -->
<!-- (none) -->

### Modified Capabilities

<!-- Existing capabilities whose REQUIREMENTS are changing (not just implementation).
     Only list here if spec-level behavior changes. Each needs a delta spec file.
     Use existing spec names from openspec/specs/. Leave empty if no requirement changes. -->
- `more-screen`: требование `MoreScreen Scaffold with TopAppBar` меняется —
  тело `Scaffold` больше не пустое, а содержит `Text` с версией приложения
  внизу (через `Alignment.BottomCenter`). В существующем
  `Scenario: MoreScreen renders with localized title` заменяется финальный
  AND-шаг («the `Scaffold` body is empty» → «the `Text` with the version
  string is displayed at the bottom of the body»), иначе Scenario
  противоречит обновлённому Requirement. Добавляется новое требование
  `MoreScreen version text shows BuildConfig.VERSION_NAME` про источник данных
  (`BuildConfig.VERSION_NAME`) и локализацию строки `app_version` в ru/en.

## Impact

- `gradle.properties` — добавить два property.
- `app/build.gradle.kts` — заменить захардкоженные `versionCode`/`versionName`
  на чтение из property, включить `buildConfig = true`.
- `app/src/main/res/values/strings.xml` — строка `app_version` (ru).
- `app/src/main/res/values-en/strings.xml` — строка `app_version` (en).
- `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt` — добавить
  `Box` с одним `Text`, прижатым к низу через `Alignment.BottomCenter`.
- `openspec/specs/more-screen/spec.md` — синхронизация требований под
  новый UI (Этап 4 из `docs/app-version-display.md`).

Без сетевых зависимостей, без новых сторонних библиотек, без изменений
в domain/data слоях. Только Gradle-конфигурация + UI + ресурсы.
