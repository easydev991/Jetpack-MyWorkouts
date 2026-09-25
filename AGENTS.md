# AGENTS.md — Jetpack-MyWorkouts

"My Workouts" — Android-приложение для трекинга тренировок. Полностью офлайн.
Kotlin + Compose + MVVM + Clean Architecture (manual DI, без Hilt).

- Локализация: ru — основной, en — дополнительный. Логи в коде — на русском.
- Реальные слои добавляй по мере фич, не плоди заранее.

---

## Stack

Jetpack Compose + Material3, Navigation Compose, ViewModel + StateFlow,
DataStore, Room. Manual DI — без Hilt.

Версии — `gradle/libs.versions.toml`, конфиг — `gradle.properties`,
`app/build.gradle.kts`. **JDK 17 обязателен** для сборки и detekt.

**Detekt**: лимит `maxIssues: 5` (`config/detekt/detekt.yml`). Повышать
лимит **без прямого согласования с владельцем репо запрещено**. Новые
detekt-предупреждения исправляются до завершения задачи. Исключение —
заранее известное предупреждение, запланированное к исправлению на
следующих этапах той же задачи: допустимо на промежуточном этапе,
злоупотреблять нельзя.

---

## Build

`make help` — список целей. Перед коммитом:
`make format && make lint && make test`.

Флейворы дистрибуции: `github` (по умолчанию) и `rustore` (заглушка),
выбор — `FLAVOR=`: `make build FLAVOR=rustore`. Детали —
`docs/plans/flavors-implementation.md`.

`make install` ставит debug-APK на подключённый adb-девайс
(`./gradlew install$(FLAVOR)Debug`).

`make apk` — подписанный release-APK в корне репо
(`myworkouts{VERSION_CODE}.apk`); при первом запуске сам подтягивает
секреты из приватного репозитория `android-secrets` (`_load_secrets`).

---

## Tests

Полные руководства — в навыках `kotlin-testing` (unit, JUnit 5) и
`kotlin-ui-testing` (androidTest, JUnit 4).

`make test` — JVM. `make android-test` — на подключённом устройстве.
Для ускорения androidTest: `make emulator-fast` — выключает анимации
эмулятора; применять после каждого старта эмулятора.

**Запуск тестов — один прогон → один отчёт**: `make test` / `make android-test`
уже вызывают `scripts/test_report.py` (статистика + список упавших).
Повторно гонять `make` или `find … -name 'TEST-*.xml'` ради чтения того
же отчёта — запрещено (минyты впустую). Перечитать отчёт без повторного
прогона: `python3 scripts/test_report.py <dir>`. Полные правила —
`.opencode/rules/test-execution.md`.

**TDD для логики**: use-cases, ViewModel, репозитории, валидация —
сначала падающий тест, потом реализация (red-green-refactor).
Не применяется к верстке/навигации — там Compose UI-тесты по факту.
При планировании задач (openspec `tasks.md`) тесты идут перед
реализацией: задача «тест на X» — раньше задачи «реализация X».

---

## Conventions

- **Никогда `!!`** — `checkNotNull` с сообщением, `?.let`, `?:`.
- UiState экрана — data class по умолчанию: поля-флаги +
  одноразовые события типом enum/sealed-kind (например,
  `AppDataResult`). Sealed-иерархия `Loading` / `Success(data)` /
  `Error(message)` — только когда экран имеет взаимоисключающие
  визуальные состояния (отдельные экраны Loading/Error).
- `Result<T>` с `try/catch` в use-cases.
- StateFlow + `SharingStarted.WhileSubscribed(5000)` в ViewModel.
- `LazyColumn` с `key = { it.id }`.
- **Preview обязательны** для всех composable-компонентов: светлая + тёмная
  (`UI_MODE_NIGHT_YES`), `showBackground = true, locale = "ru"`, обёртка в
  `JetpackMyWorkoutsTheme`, ключевые состояния компонента — отдельными
  `@Preview`-функциями (одно состояние — одно превью; собирать несколько
  состояний экрана в один `Column` не следует — неудобно инспектировать;
  «рядом» допустимо только для мелких компонентов вроде строки списка
  или контекстного меню).
  Превью могут жить в соседнем `*Preview.kt`-файле рядом с экраном.
- **Offline-only**: приложение не ходит в сеть за данными — никаких
  фича-сетевых библиотек (Retrofit/OkHttp/Ktor). Телеметрия Firebase
  (Crashlytics, Analytics) разрешена: SDK ставится как зависимость,
  провайдер выбирается в `MainActivity` по `BuildConfig.DEBUG`
  (Noop в debug, Firebase в release), пакеты `analytics/` и `crash/`
  живут рядом с `data/` и `domain/` и не считаются фича-сетью.

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

`app/src/main/java/com/myworkouts/`:
- `MainActivity.kt`
- `MyWorkoutsApplication.kt` — композиционный корень manual DI
  (process-singleton `AppSettingsDataStore`, `WorkoutDatabase`,
  `RoomWorkoutDataRepository`)
- `data/db/` — Room: entities, DAOs, `Mappers`, `WorkoutDatabase`,
  стандартные упражнения. Смена схемы — bump `version` + коммит
  экспортированного JSON из `app/schemas/`; до первого релиза
  `fallbackToDestructiveMigration(dropAllTables = false)` пересоздаёт БД с повторным prepopulate
  стандартного набора (тот же список используется и в `deleteAllData`)
- `data/preferences/AppSettingsDataStore.kt`
- `data/repository/` — `WorkoutDataRepository`, `RoomWorkoutDataRepository`,
  `EntryValidator`, `DeletionExceptions`
- `domain/model/` — `AppTheme`, `Exercise`, `ExerciseCategory`, `ExerciseEntry`,
  `EntryListItem`, `ExerciseMeasuredBy`, `BackupEntry`, `DeleteExerciseError`
- `domain/usecase/` — `ExportBackupUseCase`, `ImportBackupUseCase` (бэкап в JSON),
  `BackupException`
- `analytics/` — событийная модель и провайдеры: sealed interface
  `AnalyticsEvent` (`ScreenView`/`UserAction`/`AppError`), enum'ы
  `AppScreen`/`UserActionType`/`AppErrorOperation`, интерфейс
  `AnalyticsProvider` и реализации `FirebaseAnalyticsProvider`
  (релизный маппинг в `Bundle`) и `NoopAnalyticsProvider` (debug)
- `crash/` — `CrashlyticsHelper` (object, `logException` в `try/catch`,
  `setCustomKey` для контекста; используется из use-cases и VM)
- `navigation/Screen.kt` (только `route`) + `BottomTab` + `BottomTabs`
- `ui/screens/{main,entry,more,root,appearance,appdata,exercises}/`
- `ui/components/` — переиспользуемые composable-компоненты
  (`SettingToggleRow` — общий для экранов настроек)
- `ui/state/` — UiState-класс, по одному на экран
- `ui/theme/{Color,Theme,Type}.kt` ← `Type`, не `Typography`
- `ui/util/` — `DurationFormatter.kt` (общий для main и entry экранов)
- `ui/viewmodel/` — ViewModel, по одной на экран

`di/` и `util/` намеренно не создаются — manual DI без Hilt; `MainActivity`
получает зависимости через `application as MyWorkoutsApplication`, фабрики
VM собираются в `RootScreen`.

Тесты зеркалят source в `app/src/test/` и `app/src/androidTest/`.
