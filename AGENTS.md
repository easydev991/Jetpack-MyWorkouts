# AGENTS.md — Jetpack-MyWorkouts

## Project Overview

"My Workouts" — Android-приложение для трекинга тренировок. Полностью офлайн.
Kotlin + Jetpack Compose. Архитектура: **как в
[JetpackDays](/Users/Oleg991/Documents/GitHub/JetpackDays)** (Room, MVVM, Clean
Architecture, manual DI, без Hilt):

- Offline-only: никаких сетевых зависимостей (Retrofit/OkHttp/Ktor).
- Двухвкладочный `BottomNavigation`: «Тренировки» и «Ещё».
- Локализация: ru — основной, en — дополнительный (см. навык `localization`).
- Логи в коде — на русском независимо от локали.

> Репо сейчас — почти чистый шаблон Android Studio. Вся архитектура ниже —
> это целевое состояние, не текущее.

---

## Stack

| Component       | Technology                          |
|-----------------|-------------------------------------|
| UI              | Jetpack Compose + Material3         |
| Navigation      | Navigation Compose                  |
| State           | ViewModel + StateFlow               |
| Database        | Room (planned)                      |
| Preferences     | DataStore (planned)                 |
| Async           | Coroutines                          |
| DI              | Manual (factory methods, no Hilt)   |

Версии — в `gradle/libs.versions.toml` и `app/build.gradle.kts`.

---

## Build Commands

`Makefile`, ktlint и detekt **ещё не добавлены** — собираем через Gradle напрямую.

```bash
./gradlew assembleDebug                      # Debug APK
./gradlew assembleRelease                    # Release APK
./gradlew test                               # Unit tests (JUnit 4)
./gradlew connectedDebugAndroidTest          # Instrumentation tests (нужно устройство)
```

Single-test запуск:

```bash
./gradlew test --tests "com.example.jetpack_myworkouts.SomeTest"
./gradlew test --tests "*SomeTest"
./gradlew test --tests "com.example...SomeTest.methodName"
```

Полный набор утилит (`make format`, `make lint`, `make check`, `make android-test`)
планируется перенести из JetpackDays — пока не предполагай их существование.

---

## Code Style

### Kotlin
- Data classes для моделей.
- Sealed classes для состояний/результатов.
- Extension functions для читаемости.
- **Никогда `!!`** — используй `?`, `?:`, `let`, `checkNotNull`.

### Safe Unwrapping (обязательно)

```kotlin
// WRONG
val itemId = savedStateHandle["itemId"]!!

// CORRECT: checkNotNull с сообщением
private val itemId: Long = checkNotNull(savedStateHandle["itemId"]) {
    "ItemId parameter is required"
}

// CORRECT: let для null-safe
repository.getById(id)?.let { item -> /* ... */ }

// CORRECT: Elvis
val icon = screen.icon ?: defaultIcon
```

### Error Handling
- В use-cases — стандартный `Result<T>` с `try/catch`.
- Для UI-состояний — sealed classes: `Loading` / `Success(data)` / `Error(message)`.

### Compose
- `State`/`MutableState` для UI state.
- Однонаправленный data flow: state down, events up.
- `ViewModel` для управления состоянием экрана.
- `CompositionLocal` только для темы и глобальной конфигурации.

```kotlin
@Preview
@Composable
fun ComponentPreview() {
    JetpackMyWorkoutsTheme { Component() }
}
```

---

## Localization

Загрузи навык `.opencode/skills/localization/SKILL.md` **перед** добавлением
любых строк (там правила для ru/en, plurals, форматов и логирования).

---

## Project Structure (целевая)

Сейчас в `app/src/main/java/com/example/jetpack_myworkouts/` только
`MainActivity.kt` и `ui/theme/`. Целевая структура (по образцу JetpackDays):

```
app/src/main/java/com/example/jetpack_myworkouts/
├── data/
│   ├── database/         # Room: entities, DAO, DB, converters, mappers
│   ├── preferences/      # DataStore (настройки)
│   └── repository/       # Реализации репозиториев
├── domain/
│   ├── exception/        # WorkoutException и т. п.
│   ├── model/            # Workout, Exercise, Set (domain entities)
│   ├── repository/       # Интерфейсы репозиториев
│   └── usecase/          # Use cases
├── ui/
│   ├── ds/               # Переиспользуемые design-system компоненты
│   ├── screens/          # workouts/, more/, workoutdetail/, createedit/...
│   ├── state/            # UI state классы
│   ├── theme/            # Theme, Color, Type
│   └── viewmodel/        # ViewModels
├── navigation/           # Screen sealed class + BottomNav
├── di/                   # AppModule (manual DI, без Hilt)
├── util/                 # Logger, AppConstants
├── MyWorkoutsApplication.kt
└── MainActivity.kt
```

Тесты: `test/` (unit) и `androidTest/` (интеграционные/UI) — структура
зеркалит source.

---

## Architecture (target)

- **MVVM** + **Clean Architecture**: Presentation → Domain → Data.
- **Manual DI**: factory-методы в `AppModule` (без Hilt/Koin).
- **Offline-only**: без сетевых библиотек.
- **Room**: DAO возвращают `Flow<...>` для реактивных запросов.
- **StateFlow** с `SharingStarted.WhileSubscribed(5000)` в ViewModel.
- `viewModelScope.launch` для корутин с автоотменой.
- `LazyColumn` с `key = { it.id }` для стабильной идентификации элементов.

Реальные слои добавляй по мере фич — не плоди файлы заранее.

---

## Workflow

Навыки в `.opencode/skills/`:
- `localization` — обязателен перед добавлением строк.
- `openspec-*` — spec-driven workflow (`.opencode/commands/opsx-*`).

### Pre-commit checklist (planned, пока без автоматизации)
1. Все новые строки добавлены в `values/strings.xml` и `values-en/strings.xml`.
2. Никаких `!!` в новом коде.
3. KDoc на публичные API.
4. `./gradlew test` проходит локально.
