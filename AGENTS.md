# AGENTS.md — Jetpack-MyWorkouts

"My Workouts" — Android-приложение для трекинга тренировок. Полностью офлайн.
Kotlin + Compose + MVVM + Clean Architecture (manual DI, без Hilt).
Шаблон — [JetpackDays](/Users/Oleg991/Documents/GitHub/JetpackDays).

- Локализация: ru — основной, en — дополнительный. Логи в коде — на русском.
- Реальные слои добавляй по мере фич, не плоди заранее.

---

## Stack

Jetpack Compose + Material3, Navigation Compose, ViewModel + StateFlow,
DataStore. Room (planned). Manual DI — без Hilt.

Версии — `gradle/libs.versions.toml`, конфиг — `gradle.properties`,
`app/build.gradle.kts`. **JDK 17 обязателен** для сборки и detekt.

---

## Build

`make help` — список целей. Перед коммитом:
`make format && make lint && make test`.

`make install` ставит APK на подключённый adb-девайс
(`./gradlew installDebug`).

---

## Tests

Полные руководства — в навыках `kotlin-testing` (unit, JUnit 5) и
`kotlin-ui-testing` (androidTest, JUnit 4).

`make test` — JVM. `make android-test` — на подключённом устройстве.

---

## Conventions

- **Никогда `!!`** — `checkNotNull` с сообщением, `?.let`, `?:`.
- Sealed classes для UI-состояний: `Loading` / `Success(data)` / `Error(message)`.
- `Result<T>` с `try/catch` в use-cases.
- StateFlow + `SharingStarted.WhileSubscribed(5000)` в ViewModel.
- `LazyColumn` с `key = { it.id }`.
- **Offline-only**: никаких сетевых зависимостей (Retrofit/OkHttp/Ktor).

---

## Localization

`app_name` уже локализован (ru: «Мои тренировки», en: «My workouts»),
`app/src/main/res/xml/locales_config.xml` подключён. Остальные правила
(ru/en, plurals, форматы, логирование) — в навыке `localization`.

---

## Quirks

- **`gradle/gradle-daemon-jvm.properties`** — генерируется Android Studio при
  миграции на daemon toolchain. В `.gitignore`, миграция не нужна (`JAVA_HOME`
  уже JDK 17). Если IDE снова создал файл — игнорируй и удали.
- **Detekt 1.23.8 не работает на JDK 25** — через foojay/toolchain Gradle
  может тянуть JDK 25, после этого detekt падает на `--jvm-target=25`. Не
  трогай `toolchainVersion` без апгрейда detekt до 2.x.

---

## Project Structure

`app/src/main/java/com/myworkouts/` сейчас:
- `MainActivity.kt`
- `data/preferences/AppSettingsDataStore.kt`
- `domain/model/AppTheme.kt`
- `navigation/Screen.kt` (только `route`) + `BottomTab` + `BottomTabs`
- `ui/screens/{main,more,root,appearance}/`
- `ui/state/AppearanceUiState.kt`
- `ui/theme/{Color,Theme,Type}.kt` ← `Type`, не `Typography`
- `ui/viewmodel/AppearanceViewModel.kt`

`di/`, `util/`, `MyWorkoutsApplication.kt` ещё не созданы — manual DI в
пределах фичи, без центрального модуля.

Тесты зеркалят source в `app/src/test/` и `app/src/androidTest/`.