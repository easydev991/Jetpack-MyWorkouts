## 1. Репро-тесты (red)

- [x] 1.1 Создать `app/src/androidTest/java/com/myworkouts/EntryListLiveUpdateIntegrationTest.kt` по образцу `ExerciseCreateLiveUpdateIntegrationTest`: сценарий B «список актуален после пересоздания Activity» — подготовка записи напрямую в БД, `scenario.recreate()`, быстрое изменение упражнения у записи, сохранение, на главном экране строка показывает новое название. Проверка: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.EntryListLiveUpdateIntegrationTest'` — тест ПАДАЕТ (stale-список), что фиксирует дефект
- [x] 1.2 Добавить в тот же класс сценарий A «изменённая запись отображается при быстром возврате»: та же подготовка, правка и сохранение БЕЗ recreate и БЕЗ паузы (возврат в окне < 5 с, `waitForIdle` между шагами). Проверка: тест зелёный на чистом процессе (пересоздания не было) — документирует окно; падает вместе с B после воспроизведения пересоздания
- [x] 1.3 Добавить сценарий C «новая запись отображается при быстром возврате»: вставка упражнения напрямую в БД → создание записи через UI → сохранение → возврат → запись в списке. Проверка: green на чистом процессе (в сценарии нет recreate — red не гарантирован, см. design Decision 3); падает вместе с B после воспроизведения пересоздания

## 2. Фикс: один инстанс БД на процесс (green)

- [x] 2.1 Создать `app/src/main/java/com/myworkouts/MyWorkoutsApplication.kt` с lazy-свойствами `dataStore` (`AppSettingsDataStore`), `database` (`WorkoutDatabase.create` + `standardExercises`), `repository` (`RoomWorkoutDataRepository`); зарегистрировать `android:name=".MyWorkoutsApplication"` в `AndroidManifest.xml`. Проверка: `make build` проходит, приложение стартует
- [x] 2.2 Перевести `MainActivity` на зависимости из `(application as MyWorkoutsApplication)`: убрать создание `AppSettingsDataStore`/`database`/`repository` из `onCreate`, цепочка `RootScreen(dataStore, repository)` сохраняется. Проверка: `make test` зелёный; репро-тесты 1.1–1.3 зелёные (`make android-test`)
- [x] 2.3 Убедиться, что БД создаётся один раз на процесс: временный лог `identityHashCode(database)` при создании, прогон сценария B — в logcat ровно одно создание, включая после `recreate()`; лог убрать. Проверка: logcat-наблюдение, шаг фиксируется в описании PR/коммита

## 3. Регрессия и качество

- [x] 3.1 Прогнать полный JVM-набор: `make test` — статистика без новых фейлов
- [x] 3.2 Прогнать полный androidTest-набор: `make android-test` — без новых фейлов, включая существующие `ExerciseCreateLiveUpdateIntegrationTest` и `ExercisesLiveUpdateTest`
- [x] 3.3 `make format && make lint` — без замечаний
- [x] 3.4 `openspec validate --change fix-entry-list-live-update --strict` — валидно; ручная проверка обоих пользовательских сценариев из баг-репорта на устройстве (редактирование с быстрым возвратом; создание записи с новым упражнением)
