## 1. Секреты и Makefile

- [ ] 1.1 РУЧНОЙ ШАГ (пользователь, вне кода): положить `google-services.json` в приватный репозиторий `android-secrets` (каталог `myworkouts/`). Без него сборка после 2.2 будет падать — ожидаемо (fail fast)
- [ ] 1.2 Makefile: `_load_secrets` дополнить копированием `.secrets/google-services.json` → `app/google-services.json`; условие `_ensure_secrets` расширить проверкой `! -f app/google-services.json`; prerequisite `_ensure_secrets` добавить целям `build`, `install`, `apk`, `test`, `android-test`. Verify: `rm -f app/google-services.json .secrets/google-services.json && make build` — секреты подтягиваются автоматически, сборка проходит

## 2. Gradle и манифест

- [ ] 2.1 `gradle/libs.versions.toml`: версии `firebase-bom = "34.19.0"`, `google-services = "4.5.0"`, `firebase-crashlytics-gradle = "3.0.8"`; библиотеки `firebase-bom`, `firebase-crashlytics`, `firebase-analytics` (обе — без версий, из BOM) и плагины `google-services`, `firebase-crashlytics` — по образцу существующих алиасов. Verify: `./gradlew help` после 2.2 резолвит плагины без ошибок
- [ ] 2.2 Корневой `build.gradle.kts`: оба плагина `apply false`; `app/build.gradle.kts`: применить оба + `implementation(platform(libs.firebase.bom))` + `implementation(libs.firebase.crashlytics)` + `implementation(libs.firebase.analytics)`. Verify: `./gradlew help` (с google-services.json после 1.2)
- [ ] 2.3 `buildTypes`: debug — `manifestPlaceholders["crashlyticsCollectionEnabled"] = false`, release — `= true`; в `AndroidManifest.xml` две meta-data записи (`firebase_crashlytics_collection_enabled`, `firebase_analytics_collection_enabled`) со значением `${crashlyticsCollectionEnabled}`. Verify: контрольная сборка в 2.4
- [ ] 2.4 Контрольная сборка с configuration-cache: `make build` (debug) без проблем configuration-cache; `make apk` (release) собирается. Verify: обе команды зелёные, флаг `org.gradle.configuration-cache=true` не отключался

## 3. Модель аналитики (TDD: тесты раньше реализации)

- [ ] 3.1 Тест `AppScreen`/`UserActionType`/`AppErrorOperation`: значения всех enum'ов — имена экранов для 8 маршрутов `Screen.kt` в PascalCase (`MainScreen`, `ExercisesScreen`, `MoreScreen`, `AppearanceScreen`, `AppDataScreen`, `ArchivedExercisesScreen`, `EntryCreateScreen`, `ExerciseCreateScreen`), действия по канону design D10 (CREATE/EDIT/DELETE/ARCHIVE/UNARCHIVE, CREATE_BACKUP/RESTORE_BACKUP/DELETE_ALL_DATA, OPEN_ENTRY_CREATE/OPEN_EXERCISE_CREATE, SELECT_THEME/TOGGLE_DYNAMIC_COLORS/TOGGLE_EXERCISES_TAB/SEND_FEEDBACK), операции ошибок по канону D9. Verify: тест красный до 3.3, зелёный после
- [ ] 3.2 Тест `NoopAnalyticsProvider`: `log` не бросает для всех трёх типов событий. Verify: красный до 3.3, зелёный после
- [ ] 3.3 Реализация пакета `com.myworkouts.analytics`: `AnalyticsEvent` (sealed interface: `ScreenView(screen)`, `UserAction(action, value?)`, `AppError(operation, throwable)`) + enum'ы + интерфейс `AnalyticsProvider` + `NoopAnalyticsProvider`. Verify: `./gradlew testDebugUnitTest --tests "*analytics*"` зелёный
- [ ] 3.4 Реализация `FirebaseAnalyticsProvider`: маппинг событий в Bundle-параметры (`screen_view`, `user_action` с `action` и опциональным `value`, `app_error` с `operation`/`error_domain`). Verify: `make build` + `make lint` (JVM-теста нет — Android API, дизайн D9)

## 4. CrashlyticsHelper (TDD)

- [ ] 4.1 Тест `CrashlyticsHelperTest`: `logException` на JVM не падает (неинициализированный Firebase бросает исключение, helper его глотает). Verify: красный до 4.2, зелёный после
- [ ] 4.2 Реализация `com.myworkouts.crash.CrashlyticsHelper`: object, `logException(throwable, message?)` в `try/catch`, `setCustomKey("error_message", message)`; подавления детекта как в проекте (`@file:Suppress("TooGenericExceptionCaught", "SwallowedException")`). Verify: `make test` зелёный

## 5. Ошибки в use-cases бэкапа

- [ ] 5.1 Тест: `CancellationException` от репозитория/IO в `ExportBackupUseCase`/`ImportBackupUseCase` прокидывается (rethrow), не превращается в `Result.failure` — дополнить существующие тесты use-cases, если сценария нет. Verify: тест зелёный (rethrow уже реализован — тест фиксирует контракт)
- [ ] 5.2 Реализация: `CrashlyticsHelper.logException` в catch-блоках обоих use-cases (контекст операции в message), `CancellationException` — rethrow строго до логирования. Verify: `make test` — существующие JVM-тесты use-cases не сломались

## 6. Инъекция провайдера в ViewModel (TDD: тесты раньше реализации)

- [ ] 6.1 Обновить `AppDataViewModelTest`/`ExercisesViewModelTest`: создать `FakeAnalyticsProvider` в тестовых исходниках (копит события в списке) и передать параметром конструктора; новые проверки — сбои по полному списку операций из design D9 (экспорт/импорт → `AppError(CREATE_BACKUP/RESTORE_BACKUP)`, удаление упражнения → `AppError(DELETE_EXERCISE)`, удаление всех данных → `AppError(DELETE_ALL_DATA)`) и user_action на вызовах операций по D10 (`DELETE`/`ARCHIVE` в ExercisesViewModel, `CREATE_BACKUP`/`RESTORE_BACKUP`/`DELETE_ALL_DATA` в AppDataViewModel). Verify: красные до 6.2, зелёные после
- [ ] 6.2 Реализация: `analyticsProvider` параметром в `AppDataViewModel` и `ExercisesViewModel` + `factory()`; в failure-ветках (итоговая ошибка — `BackupException` у бэкапа / ошибка удаления, design D8) — `CrashlyticsHelper.logException` + `analyticsProvider.log(AppError(...))`; `UserAction` в confirm-методах и VM-методах бэкапа (D10). Verify: `make test` зелёный
- [ ] 6.3 `MainActivity`: выбор провайдера по `BuildConfig.DEBUG` (Noop в debug, Firebase в release), проброс в `RootScreen` → фабрики VM. Verify: `make build` + запуск debug-сборки на устройстве — телеметрия молчит, UI без изменений

## 7. UserAction в остальных экранах (TDD)

- [ ] 7.1 Тесты: `FakeAnalyticsProvider` параметром в `EntryCreateViewModel` (CREATE/EDIT на `save` — по наличию `entryId`), `ExerciseCreateViewModel` (CREATE/EDIT на `save`), `MainViewModel` (DELETE на `confirmDelete`), `ArchivedExercisesViewModel` (UNARCHIVE на `unarchive`), `AppearanceViewModel` (SELECT_THEME со value light/dark/system, TOGGLE_DYNAMIC_COLORS со value on/off). Verify: красные до 7.2, зелёные после
- [ ] 7.2 Реализация: `analyticsProvider` параметром в эти 5 ViewModel + их `factory()`; эмиссия `UserAction` в методах-обработчиках по правилу D10. Verify: `make test` зелёный
- [ ] 7.3 Кнопки без VM: FAB → `OPEN_ENTRY_CREATE` / `OPEN_EXERCISE_CREATE` (точка нажатия в `MainScreen` / `ExercisesScreen`); `MoreScreen` — провайдер параметром из `RootScreen`: `SEND_FEEDBACK`, `TOGGLE_EXERCISES_TAB` со value show/hide. Verify: `make build` + ручной прогон на эмуляторе с логом событий

## 8. Breadcrumbs навигации

- [ ] 8.1 Реализация в `RootScreen`: `DisposableEffect(navController)` с `addOnDestinationChangedListener` (снятие в `onDispose`), маппинг `destination.route` → `AppScreen` через `when` по 8 маршрутам (для `entry_create`/`exercise_create` — сравнение с полным шаблоном маршрута), событие `ScreenView`. Verify: `make build` + ручной прогон навигации на эмуляторе (переходы, back — без крашей)

## 9. Документация и финальная проверка

- [ ] 9.1 AGENTS.md: уточнить правило Offline-only — запрет касается фича-сетевых библиотек (Retrofit/OkHttp/Ktor), телеметрия Firebase разрешена; обновить раздел Project Structure — добавить описания пакетов `crash/` (CrashlyticsHelper) и `analytics/` (AnalyticsEvent, AnalyticsProvider, провайдеры). Verify: `make lint` (markdownlint) зелёный
- [ ] 9.2 Финальная проверка: `make format && make lint && make test`, `make build` (debug — сбор телеметрии off), `make apk` (release — on). Verify: все команды зелёные, `git status` не содержит `app/google-services.json`
