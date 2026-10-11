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

Флейворы дистрибуции: `github` (дефолт сборки) и `rustore` (RuStore —
основной канал релизов), выбор — `FLAVOR=`: `make build FLAVOR=rustore`.
Релизный процесс и детали флейворов — `docs/deployment.md`.
В Android Studio flavor выбирается в Run Configuration.

`make install` ставит debug-APK на подключённый adb-девайс
(`./gradlew install$(FLAVOR_TITLE)Debug`).

`make apk` — подписанный release-APK в корне репо
(`myworkouts{VERSION_CODE}.apk`); секреты из приватного репозитория
`android-secrets` подтягиваются автоматически (`_ensure_secrets` —
клонирует их, только если `.secrets/`/`google-services.json` отсутствуют).

---

## Tests

`make test` — JVM. `make android-test` — на подключённом устройстве.
`make memory-check` — точечный прогон LeakCanary-регрессии
(`android-test` её исключает — `notClass`); подробности —
`.opencode/rules/test-execution.md`.
Правила прогонов, отчёты и gotcha — `.opencode/rules/test-execution.md`
(один прогон → один отчёт); руководства — навыки `kotlin-testing` (unit)
и `kotlin-ui-testing` (androidTest).

`make screenshots{,-ru,-en}` — скриншоты для маркетплейса: модуль
`:screenshot-tests` пишет PNG в app-specific external storage, make
забирает их в `fastlane/metadata/android/<locale>/images/phoneScreenshots/`.
Нужны эмулятор без google_play-образа (`adb root`) и API 33+.

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
- Не использовать deprecated API — заменять на актуальные альтернативы.
- Импорт/бэкап JSON: неизвестные поля игнорируются (толерантный парсинг),
  фильтрация дубликатов при merge — импорт идемпотентен; стримы — через
  `use {}`; любая ошибка файла — `BackupException(INVALID_FILE)`.
- StateFlow + `SharingStarted.WhileSubscribed(5000)` в ViewModel — для
  холодных потоков из репозитория/DataStore. Для UiState на собственном
  `MutableStateFlow` это не применяется: `mutableUiState.asStateFlow()`
  без sharing-слоя (`save()` читает всегда свежий `.value`).
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
  фича-сетевых библиотек (конкретный запрет пакетов — detekt
  `ForbiddenImport`; текст правила шире: линтер не покрывает
  `HttpsURLConnection` и прочий голый `java.net`). Телеметрия Firebase
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

`:screenshot-tests/` — инструментальный модуль (`com.android.test`,
`targetProjectPath = ":app"`, без своих flavors) для генерации
скриншотов маркетплейса: `ScreenshotsTest` ставит demo-данные, снимает
6 сценариев (список тренировок, создание и сохранение записи,
статистика, рекорды, активность) и пишет PNG (`Bitmap.compress` в самом
тесте); имена PNG стабильны — README ссылается на них напрямую.
Запуск — `make screenshots{,-ru,-en}`.

`app/src/main/java/com/myworkouts/`:
- `MainActivity.kt`
- `MyWorkoutsApplication.kt` — композиционный корень manual DI
  (process-singleton `AppSettingsDataStore`, `WorkoutDatabase`,
  `RoomWorkoutDataRepository`)
- `data/db/` — Room: entities, DAOs, `Mappers`, `WorkoutDatabase`,
  стандартные упражнения. Смена схемы — bump `version` + коммит
  экспортированного JSON из `app/schemas/` + `Migration` в
  `addMigrations(...)` (без подходящей миграции Room при открытии
  бросает `IllegalStateException`); prepopulate стандартного набора —
  при первом создании БД (тот же список используется и в `deleteAllData`).
  Правила миграций: пока миграций не было (v1→v3 — bump вместо
  миграции), `androidx.room:room-testing` не подключён. Первая
  `Migration` коммитится в паре с `MigrationTest` (androidTest,
  `MigrationTestHelper` + добавление зависимости `room-testing`) —
  миграция без теста не принимается; простые изменения (колонка/
  таблица/индекс без перестройки данных) — предпочитать
  `@AutoMigration`; мок-юнит-тесты SQL миграций (`verify { execSQL }`
  с relaxed) запрещены — не ловят синтаксические ошибки, только
  интеграционный тест через `MigrationTestHelper`
- `data/preferences/AppSettingsDataStore.kt`
- `data/repository/` — `WorkoutDataRepository`, `RoomWorkoutDataRepository`,
  `EntryValidator`, `OperationalExceptions`
- `domain/model/` — domain-модели (упражнения, записи, настройки, периоды,
  `BackupEntry` и т.д.). Полный список — в каталоге.
- `domain/usecase/` — use-cases: бэкап в JSON, статистика (streaks, heatmap,
  records, category stats), вычисление периодов. Полный список — в каталоге.
- `analytics/` — событийная модель и провайдеры: sealed interface
  `AnalyticsEvent` (`ScreenView`/`UserAction`/`AppError`), enum'ы
  `AppScreen`/`UserActionType`/`AppErrorOperation`, интерфейс
  `AnalyticsProvider` и реализации `FirebaseAnalyticsProvider`
  (релизный маппинг в `Bundle`) и `NoopAnalyticsProvider` (debug)
- `crash/` — `CrashlyticsHelper` (object, `logException` в `try/catch`,
  `setCustomKey` для контекста; используется из use-cases и VM)
- `navigation/Screen.kt` — маршруты + `BottomTab`/`BottomTabs` (в одном
  файле, «только route» уже не про него)
- `ui/screens/{main,entry,more,root,appearance,appdata,exercises,overview}/`
- `ui/components/` — общий composable `SettingToggleRow` (экраны
  настроек); строки списков живут у своих экранов:
  `main/EntryListItemRow`, `exercises/ExerciseListItemRow`
- `ui/state/` — UiState-класс, по одному на экран
- `ui/theme/{Color,Theme,Type}.kt` ← `Type`, не `Typography`
- `ui/util/` — `DurationFormatter.kt` (main + entry), `SectionDateFormatter.kt`
- `ui/viewmodel/` — ViewModel, по одной на экран

`di/` и `util/` намеренно не создаются — manual DI без Hilt; `MainActivity`
получает зависимости через `application as MyWorkoutsApplication`, фабрики
VM собираются в `RootScreen`.

Тесты зеркалят source в `app/src/test/` и `app/src/androidTest/`.
