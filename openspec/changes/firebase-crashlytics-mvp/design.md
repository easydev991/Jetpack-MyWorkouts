## Context

Приложение офлайн, manual DI без Hilt: композиционный корень —
`MyWorkoutsApplication` (lazy `dataStore`, `WorkoutDatabase`,
`repository`); `MainActivity.onCreate` берёт их через
`application as MyWorkoutsApplication` и пробрасывает дальше.
ViewModel'ы получают зависимости через `companion factory()` в точке
`composable(...)`. Использование `CancellationException`
rethrow в use-cases бэкапа уже реализовано. `configuration-cache=true` включён.
Proguard-правила для Crashlytics и `.gitignore`-запись `app/google-services.json`
уже подготовлены заранее. В `Makefile` существует механизм `_load_secrets` /
`_ensure_secrets` (сейчас — только keystore), `SECRETS_DIR = myworkouts`.
Мотивация — в proposal.md (Why).

Существующие JVM-тесты VM: JUnit 5 + Fake-репозитории на MutableStateFlow + MockK
для use-cases, статика `android.util.Log.e` мокируется через `mockkStatic`.

## Goals / Non-Goals

**Goals:**

- Минимальный интеграционный слой Firebase: сборка, секреты, краши, breadcrumbs, события ошибок.
- Все существующие тесты остаются зелёными (с минимальной правкой конструкторов).
- Тестируемость аналитики на JVM через инъекцию провайдера.

**Non-Goals:**

- Остальное вне области не дублируем здесь — канон в proposal.md
  (Impact, «Вне области»): opt-out аналитики в UI, Timber, изменения
  `MyWorkoutsApplication` (выбор/инициализация провайдера туда не
  переносим — Firebase инициализируется автоматически провайдером
  google-services) и т.д.

## Decisions

### D1. Один провайдер вместо мульти-провайдерного сервиса

`AnalyticsProvider` (интерфейс) + `FirebaseAnalyticsProvider` + `NoopAnalyticsProvider`.
Сервис-обёртка со списком провайдеров не переносится: бэкенд один, лишний слой
не нужен. Добавим, если появится второй провайдер.

### D2. CrashlyticsHelper — object со статическими вызовами

`com.myworkouts.crash.CrashlyticsHelper.logException(throwable, message?)`:
внутри `try/catch` (против бесконечного цикла при ошибке самого Crashlytics),
`setCustomKey("error_message", message)` перед `recordException`. Вызывается
из use-cases и ViewModel напрямую, без инъекции — интерфейс ради одного вызова
не вводим. JVM-безопасность обеспечена самим helper'ом: неинициализированный
`Firebase.crashlytics` бросает исключение, которое глотается собственным catch —
существующие JVM-тесты use-cases не пострадают и без моков.

### D3. Breadcrumbs через addOnDestinationChangedListener в RootScreen

`navController.addOnDestinationChangedListener` в `DisposableEffect(navController)`
(в `onDispose` — снятие), маппинг `destination.route` → `AppScreen` через `when`
по 8 маршрутам. Одна точка вместо `LaunchedEffect` в каждом destination — меньше
правок и back покрывается автоматически. Для экранов с аргументами сравниваем с
полным шаблоном маршрута (`"entry_create?entryId={entryId}"`) — так `destination.route`
отдаёт Navigation.

### D4. Выбор провайдера в MainActivity по BuildConfig.DEBUG

`NoopAnalyticsProvider` в debug, `FirebaseAnalyticsProvider(applicationContext)`
в release. Проброс параметрами, как остальным зависимостям:
`MainActivity` → `RootScreen(dataStore, repository, analyticsProvider)` →
`factory()` ViewModel (+1 параметр `analyticsProvider` последним) и в слушатель
навигации. `Application`-класс (`MyWorkoutsApplication`) уже есть после
`fix-entry-list-live-update`; изменений в нём нет — Firebase инициализируется
автоматически провайдером `google-services`, в `MyWorkoutsApplication.onCreate`
ничего не добавляем. Сигнатуры VM с учётом запланированной ранее фичи
`archive-alert-hide` (которая добавляет `dataStore` вторым параметром
в `ExercisesViewModel`):
`AppDataViewModel(repository, exportUseCase, importUseCase, analyticsProvider)`,
`ExercisesViewModel(repository, dataStore, analyticsProvider)`.

### D5. Отключение debug двумя механизмами

`manifestPlaceholders["crashlyticsCollectionEnabled"] = false/true` по buildType —
обе meta-data записи (`firebase_crashlytics_collection_enabled`,
`firebase_analytics_collection_enabled`) читают один placeholder. Плюс Noop в
debug (D4). Двойная защита: события и сбор данных отключены независимо.

### D6. Версии зависимостей и безусловное подключение в оба флейвора

`firebase-bom = 34.19.0`, плагины `google-services = 4.5.0` и
`firebase-crashlytics-gradle = 3.0.8`; библиотеки `firebase-crashlytics` и
`firebase-analytics` — без версий (из BOM). Root `build.gradle.kts`: оба плагина
`apply false`; app — применить. Эти версии используются в проекте с включённым
`configuration-cache=true` — флаг не трогаем. Флейворы github/rustore — дистрибуция,
Firebase подключается в оба безусловно.

### D7. google-services.json через существующий механизм секретов

`_load_secrets` дополняется копированием `.secrets/google-services.json` →
`app/google-services.json`; условие `_ensure_secrets` расширяется проверкой этого
файла; prerequisite `_ensure_secrets` получают цели `build`, `install`, `apk`,
`test`, `android-test` (плагин google-services падает без файла). Сам файл в
приватный репозиторий `android-secrets` (каталог `myworkouts/`) пользователь
кладёт вручную — задача-заметка в tasks.md, не код.

### D8. Логирование ошибок в двух слоях, дубль принят

Use-case catch-блоки логируют первопричину с контекстом (стадия, uri);
failure-ветки ViewModel — итоговую ошибку (`BackupException` / ошибка удаления)
- `AnalyticsEvent.AppError`. Для операций бэкапа один инцидент может попасть в
Crashlytics дважды (первопричина и итог) — принято: Crashlytics группирует по
стек-трейсам, контекст стадии важнее уникальности записи. `CancellationException`
rethrow строго до любого логирования (уже так в use-cases — сохраняем).

### D9. Тесты: Fake-провайдер, FirebaseAnalyticsProvider на JVM не тестируем

`FakeAnalyticsProvider` (копит события в списке) в проектном стиле Fake —
инъекция в `AppDataViewModel` / `ExercisesViewModel`, проверка `AppError(operation)`
в failure-сценариях. `FirebaseAnalyticsProvider` на JVM не тестируется:
`FirebaseAnalytics` и `Bundle` — Android API; стабильность wire-значений
проверяем assert'ами на enum'ы (`screenName`, `value`). Канонические значения
`AppErrorOperation` (специфичные операции — для группировки ошибок):
`CREATE_BACKUP`, `RESTORE_BACKUP`, `DELETE_ALL_DATA`, `DELETE_EXERCISE`;
канон действий `UserActionType` — в D10. Существующий
`mockkStatic(Log::class)` в тестах не трогаем.

### D10. Точки эмиссии user_action: нажатие элемента, совершающего действие

`AnalyticsEvent.UserAction(action, value: String? = null)` эмитится в обработчике
нажатия — до/независимо от исхода (успех даст user_action, провал дополнительно
app_error — полный воронкой). Правила:

- Логируются кнопки действий: сохранение (запись, упражнение), удаление
  (запись, упражнение — confirm в диалоге), архивация/возврат, бэкап-операции
  (в VM-методе — после выбора файла в SAF, это фактическое действие), удаление
  всех данных (confirm), настройки (тема, динамические цвета, тоггл вкладки),
  отправка feedback, FAB (навигация к созданию — `OPEN_*`, различает создание
  и редактирование до сохранения).
- НЕ логируются (шум, навигация уже покрыта screen_view): переключение табов,
  back-стрелки и системный back, открытие диалогов/контекст-меню/пикеров,
  кнопки отмены и закрытия без сохранения, пункт «Изменить» (итоговый сейв
  различит CREATE/EDIT).

Канон `UserActionType`: `CREATE`, `EDIT`, `DELETE`, `ARCHIVE`, `UNARCHIVE`,
`CREATE_BACKUP`, `RESTORE_BACKUP`, `DELETE_ALL_DATA`, `OPEN_ENTRY_CREATE`,
`OPEN_EXERCISE_CREATE`, `SELECT_THEME`, `TOGGLE_DYNAMIC_COLORS`,
`TOGGLE_EXERCISES_TAB`, `SEND_FEEDBACK`; value: `SELECT_THEME` = light/dark/system,
`TOGGLE_DYNAMIC_COLORS` = on/off, `TOGGLE_EXERCISES_TAB` = show/hide.

Инъекция провайдера (сверх D4): параметром в `MainViewModel` (DELETE),
`EntryCreateViewModel` / `ExerciseCreateViewModel` (CREATE/EDIT),
`ArchivedExercisesViewModel` (UNARCHIVE), `AppearanceViewModel`
(SELECT_THEME/TOGGLE_DYNAMIC_COLORS); `MoreScreen` без VM — провайдер
параметром из `RootScreen` (SEND_FEEDBACK, TOGGLE_EXERCISES_TAB);
FAB-события — в точке нажатия в `MainScreen` / `ExercisesScreen`.

## Risks / Trade-offs

- [google-services.json отсутствует в android-secrets → сборка падает] → падение
  fail-fast осознанно: `_ensure_secrets` автозагружает файл при запуске make-целей;
  задача-заметка в tasks.md фиксирует ручной шаг.
- [Несовместимость плагинов с configuration-cache] → версии проверены в проекте
  с тем же флагом; после подключения контрольный `make build`; при проблемах —
  обновлять плагины, флаг не отключать.
- [Реконфигурация RootScreen → повторная регистрация слушателя навигации] →
  `DisposableEffect(navController)` со снятием в `onDispose`.
- [Новый параметр конструкторов 7 VM ломает существующие тесты] → правка тестов
  идёт задачей перед реализацией (TDD по AGENTS.md), Fake/Noop-провайдер
  передаётся явно.
- [Дубль записей бэкап-ошибок] → принято (D8), группировка Crashlytics.

## Migration Plan

Единый PR/коммит; откат — revert, данные пользователя не затрагиваются
(телеметрия только читает). После реализации: `make build` (debug, манифест-флаг
false) и `make apk` (release, флаг true) — проверить обе ветки; `make test` —
зелёные JVM-тесты.
