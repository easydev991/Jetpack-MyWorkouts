## Why

Приложение полностью офлайн, и о падениях в проде мы узнаём только от пользователей.
Нет ни стек-трейсов, ни breadcrumbs, ни метрик по ключевым операциям (бэкап,
удаление данных). Подключение Firebase Crashlytics и базовой аналитики даст
видимость ошибок в release-сборках без изменения пользовательского опыта.

## What Changes

- Gradle: подключение Firebase BOM, библиотек crashlytics + analytics, плагинов
  google-services и firebase-crashlytics (root — `apply false`, app — применить).
- Сборки: телеметрия выключена в debug (`manifestPlaceholders["crashlyticsCollectionEnabled"] = false`)
  и включена в release; оба флага (`firebase_crashlytics_collection_enabled`,
  `firebase_analytics_collection_enabled`) читают один placeholder в AndroidManifest.
- Секреты: `app/google-services.json` доставляется из приватного репозитория
  `android-secrets` через существующий механизм `_load_secrets` / `_ensure_secrets`;
  все gradle-цели Makefile получают prerequisite `_ensure_secrets`.
- Код:
  - `com.myworkouts.crash.CrashlyticsHelper` — безопасное логирование исключений
    (try/catch против бесконечного цикла, кастомный ключ `error_message`).
  - `com.myworkouts.analytics`: `AnalyticsEvent` (sealed interface: ScreenView /
    UserAction / AppError), enum'ы `AppScreen` (8 маршрутов), `UserActionType`,
    `AppErrorOperation`; интерфейс `AnalyticsProvider` с реализациями
    `FirebaseAnalyticsProvider` и `NoopAnalyticsProvider`.
  - Выбор провайдера в `MainActivity` по `BuildConfig.DEBUG` (Noop в debug),
    проброс в ViewModel параметрами — как остальные зависимости manual DI.
  - Breadcrumbs навигации: `ScreenView`-событие на каждую смену дестинации
    через слушатель `NavController` в `RootScreen`.
  - UserAction-события на нажатия кнопок действий и навигации к созданию:
    сохранение/удаление/архивация записей и упражнений, бэкап-операции, FAB,
    настройки оформления, тоггл вкладки упражнений, feedback (канон значений —
    design D10).
  - Ошибки: `CrashlyticsHelper.logException` + `AnalyticsEvent.AppError` в
    catch-блоках use-cases бэкапа (строгий rethrow `CancellationException` без записи)
    и failure-ветках `AppDataViewModel` / `ExercisesViewModel`.
- Тесты (JVM): Fake-провайдер перехватывает события в VM-тестах (проверка
    `AppError`- и `UserAction`-событий) и Noop-провайдер; wire-значения
    фиксируются assert'ами на enum'ы (дизайн D9) — маппинг
    `FirebaseAnalyticsProvider` на JVM не тестируется (Bundle/Firebase —
    Android API).
- Документация: уточнение правила Offline-only в AGENTS.md — запрет касается
  фича-сетевых библиотек (Retrofit/OkHttp/Ktor), Firebase-телеметрия разрешена.

## Capabilities

### New Capabilities

- `crash-reporting`: телеметрия приложения — сбор крашей и breadcrumbs через
  Crashlytics, screen_view / user_action / app_error события аналитики,
  поведение в debug- и release-сборках, безопасное логирование исключений
  (включая rethrow `CancellationException` без записи).

### Modified Capabilities

<!-- Изменений требований существующих capabilities нет: интеграция телеметрии
     в экраны и use-cases не меняет их специфицированное поведение. -->

## Impact

- Зависимости: `gradle/libs.versions.toml` (+firebase-bom, +2 библиотеки без
  версий из BOM, +2 плагина), root и app `build.gradle.kts`, AndroidManifest.xml
  (2 meta-data), `app/proguard-rules.pro` (keep-правила уже подготовлены).
- Сборочная инфраструктура: Makefile (`_load_secrets` копирует google-services.json,
  prerequisite `_ensure_secrets` у gradle-целей); `configuration-cache=true`
  сохраняется — выбраны версии плагинов, совместимые с ним.
- Код: новые пакеты `crash/`, `analytics/`; меняются сигнатуры конструкторов и
  `factory()` у ViewModel семи экранов (+1 параметр-провайдер), `MoreScreen`
  получает провайдер параметром, catch-блоки `ExportBackupUseCase` /
  `ImportBackupUseCase`, `MainActivity`,
  `RootScreen` (регистрация слушателя навигации, проброс провайдера).
- Тесты: новые JVM-тесты analytics; существующие VM-тесты обновляются на новый
  конструктор (Fake-провайдер вместо статики).
- Вне области: opt-out аналитики в UI, Timber, изменения
  `MyWorkoutsApplication` (инициализация/выбор провайдера туда не
  переносим — Firebase инициализируется автоматически провайдером
  google-services), разделение Firebase по флейворам (подключается в
  оба безусловно), Remote Config (удалённая конфигурация Firebase).
- Ручной шаг пользователя: положить `google-services.json` в приватный репозиторий
  `android-secrets` (каталог `myworkouts/`) — фиксируется задачей-заметкой в tasks.md.
