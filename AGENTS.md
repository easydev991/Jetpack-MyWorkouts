# AGENTS.md — Jetpack-MyWorkouts

"My Workouts" — Android-приложение для трекинга тренировок. Полностью офлайн.
Kotlin + Compose + MVVM + Clean Architecture (manual DI, без Hilt).
Шаблон — [JetpackDays](/Users/Oleg991/Documents/GitHub/JetpackDays).

- Локализация: ru — основной, en — дополнительный. Логи в коде — на русском.
- Реальные слои добавляй по мере фич, не плоди заранее.

---

## Stack

Jetpack Compose + Material3, Navigation Compose (planned), ViewModel + StateFlow,
Room (planned), DataStore (planned). Manual DI — factory methods, без Hilt.

Версии — `gradle/libs.versions.toml`, конфиг — `gradle.properties`,
`app/build.gradle.kts`. **JDK 17 обязателен** для сборки и detekt.

---

## Build

Сборка/тесты/линт — через `Makefile` (`make help` — список). Перед коммитом:
`make format && make lint && make test`.

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
- `navigation/Screen.kt` (sealed class `Screen` + `Screen.AllTabs`)
- `ui/theme/Theme.kt` + `Typography.kt` + `Color.kt`
- `ui/screens/main/MainScreen.kt`
- `ui/screens/more/MoreScreen.kt`
- `ui/screens/root/RootScreen.kt` (NavHost + NavigationBar)

Целевая структура (по образцу JetpackDays):

```
data/{database,preferences,repository}/      # Room/DataStore
domain/{exception,model,repository,usecase}/
ui/{ds,screens,state,theme,viewmodel}/
navigation/      # sealed class Screen + BottomNav
di/              # AppModule (manual)
util/            # Logger, AppConstants
MyWorkoutsApplication.kt
```

Тесты зеркалят source в `app/src/test/` и `app/src/androidTest/`.
