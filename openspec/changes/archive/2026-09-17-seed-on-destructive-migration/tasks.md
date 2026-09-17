## 1. Тест (TDD — красный)

- [x] 1.1 В `app/src/androidTest/java/com/myworkouts/data/db/WorkoutDatabaseTest.kt` добавить тест: файловая БД (изолированный файл через `context.getDatabasePath`, удаляется в `@After`) создаётся без prepopulate, `PRAGMA user_version = 2`, переоткрытие через `WorkoutDatabase.create(...)` (schema v3) → после деструктивной миграции `SELECT count(*) FROM exercises WHERE is_custom = 0` возвращает ровно 13. Запуск: `./gradlew :app:connectedDebugAndroidTest --console=plain -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.data.db.WorkoutDatabaseTest'` — тест падает (0 строк) *(фактически: файл на production-имени `DATABASE_NAME` — `WorkoutDatabase.create()` не принимает имени, изолированный файл недоступен; гигиена — `deleteDatabase` перед созданием фикстуры и в `tearDown`; плюс ревью-дополнение — в фикстуру вставляется 1 пользовательская строка, после переоткрытия assert `count(*) WHERE is_custom = 1` = 0; см. design.md Decision 3)*

## 2. Реализация

- [x] 2.1 В `WorkoutDatabase.kt` извлечь тело `onCreate` в private-метод `seedStandardExercises(db: SupportSQLiteDatabase)` внутри `seedCallback`; оба колбэка (`onCreate`, `onDestructiveMigration` с вызовом `super`) вызывают его. Русские комментарии, без `!!`. Запуск: тест из 1.1 зелёный *(фактически: `onDestructiveMigration` выставляет флаг, seed выполняется в `onOpen` — Room 2.8 вызывает колбэк до создания таблиц; см. design.md Decision 1)*

## 3. Проверка и релизная подготовка

- [x] 3.1 `make test && make android-test` — один прогон, все зелёные (отчёт `scripts/test_report.py` в выводе)
- [x] 3.2 Поднять `VERSION_CODE` до 4 в `gradle.properties` (установка поверх сборки 3)
- [x] 3.3 Ручная верификация пути апгрейда на эмуляторе: установить debug-сборку, `adb shell "run-as com.myworkouts sqlite3 databases/workout_database 'PRAGMA user_version = 2'"`, перезапуск приложения → каталог показывает 13 стандартных упражнений (скриншот). Если `sqlite3: not found` под run-as — запасной вариант: `adb root && adb shell "sqlite3 /data/data/com.myworkouts/databases/workout_database 'PRAGMA user_version = 2'" && adb unroot`
