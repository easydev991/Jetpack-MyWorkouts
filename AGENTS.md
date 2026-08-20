# AGENTS.md — Jetpack-MyWorkouts

"My Workouts" — Android-приложение для трекинга тренировок. Полностью офлайн.
Kotlin + Jetpack Compose. Архитектура и настройки скопированы из
[JetpackDays](/Users/Oleg991/Documents/GitHub/JetpackDays) (MVVM, Clean
Architecture, manual DI — без Hilt).

- Локализация: ru — основной, en — дополнительный. Логи в коде — на русском.
- Реальные слои добавляй по мере фич, не плоди заранее.

---

## Stack

Jetpack Compose + Material3, Navigation Compose, ViewModel + StateFlow, Room
(planned), DataStore (planned). Manual DI — factory methods, без Hilt.

Версии — `gradle/libs.versions.toml`, конфиг сборки — `gradle.properties`,
`app/build.gradle.kts`. **JDK 17 обязателен** для сборки и для detekt.

---

## Build

Полный список — `make help`. Основные:

```bash
make format      # ktlintFormat + detekt autoCorrect + markdownlint
make lint        # ktlintCheck + detekt + markdownlint
make test        # JUnit unit tests
make check       # format + lint + test
```

Pre-commit: `make format && make lint && make test`.

Single-test: `./gradlew test --tests "<class>"`. Остальные gradle-команды —
`./gradlew help`.

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

Перед добавлением любых строк загрузи
`.opencode/skills/localization/SKILL.md` — там правила ru/en, plurals, форматы,
логирование. Логи в коде — на русском независимо от локали.

`app_name` уже локализован (ru: «Мои тренировки», en: «My workouts»),
`app/src/main/res/xml/locales_config.xml` подключен.

---

## Quirks

- **Namespace содержит underscore** (`com.example.jetpack_myworkouts`) — это
  дефолтный шаблон Android Studio. Правила ktlint `package-name` и detekt
  `PackageNaming` отключены в `.editorconfig` и `config/detekt/detekt.yml`.
  Переименование пакета — отдельная задача.
- **`gradle/gradle-daemon-jvm.properties`** — генерируется Android Studio при
  миграции на daemon toolchain. Файл в `.gitignore`, миграция на этом проекте
  не нужна (`JAVA_HOME` уже JDK 17). Если IDE снова создал файл — игнорируй
  и удали.
- **Detekt 1.23.8 не работает на JDK 25** — через foojay/toolchain Gradle
  может тянуть JDK 25, после этого detekt падает на `--jvm-target=25`. Не
  трогай `toolchainVersion` без апгрейда detekt до 2.x.

---

## Project Structure

Сейчас `app/src/main/java/com/example/jetpack_myworkouts/` содержит только
`MainActivity.kt` и `ui/theme/`. Целевая структура (по образцу JetpackDays):

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

---

## Workflow

Навыки в `.opencode/skills/`:
- `localization` — обязателен перед добавлением строк.
- `openspec-*` — spec-driven workflow (команды `.opencode/commands/opsx-*`).
