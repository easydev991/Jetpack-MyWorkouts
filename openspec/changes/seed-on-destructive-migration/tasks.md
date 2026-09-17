## 1. Тест (TDD — красный)

- [ ] 1.1 В `app/src/androidTest/java/com/myworkouts/data/db/WorkoutDatabaseTest.kt` добавить тест: файловая БД (изолированный файл через `context.getDatabasePath`, удаляется в `@After`) создаётся без prepopulate, `PRAGMA user_version = 2`, переоткрытие через `WorkoutDatabase.create(...)` (schema v3) → после деструктивной миграции `SELECT count(*) FROM exercises WHERE is_custom = 0` возвращает ровно 13. Запуск: `./gradlew :app:connectedDebugAndroidTest --console=plain -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.data.db.WorkoutDatabaseTest'` — тест падает (0 строк)

## 2. Реализация

- [ ] 2.1 В `WorkoutDatabase.kt` извлечь тело `onCreate` в private-метод `seedStandardExercises(db: SupportSQLiteDatabase)` внутри `seedCallback`; оба колбэка (`onCreate`, `onDestructiveMigration` с вызовом `super`) вызывают его. Русские комментарии, без `!!`. Запуск: тест из 1.1 зелёный

## 3. Проверка и релизная подготовка

- [ ] 3.1 `make test && make android-test` — один прогон, все зелёные (отчёт `scripts/test_report.py` в выводе)
- [ ] 3.2 Поднять `VERSION_CODE` до 4 в `gradle.properties` (установка поверх сборки 3)
- [ ] 3.3 Ручная верификация пути апгрейда на эмуляторе: установить debug-сборку, `adb shell "run-as com.myworkouts sqlite3 databases/workout_database 'PRAGMA user_version = 2'"`, перезапуск приложения → каталог показывает 13 стандартных упражнений (скриншот). Если `sqlite3: not found` под run-as — запасной вариант: `adb root && adb shell "sqlite3 /data/data/com.myworkouts/databases/workout_database 'PRAGMA user_version = 2'" && adb unroot`
