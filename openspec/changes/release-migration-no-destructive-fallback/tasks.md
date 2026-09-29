# Tasks

## 1. Тест (TDD — красный)

- [ ] 1.1 В `app/src/androidTest/java/com/myworkouts/data/db/WorkoutDatabaseTest.kt` переписать существующий тест `destructiveMigration_whenUserVersionDowngraded_thenReseedsThirteenStandardExercises` на месте — переименовать в `noDestructiveFallback_whenUserVersionDowngraded_thenThrowsAndKeepsData` и инвертировать контракт: файловая БД на production-имени `DATABASE_NAME` (та же фикстура: plain `Room.databaseBuilder`; перед созданием закрыть in-memory `database` из setUp и выполнить `deleteDatabase`), вставка одной пользовательской строки, `PRAGMA user_version = 2`, `plain.close()`; `WorkoutDatabase.create(...)` присвоить полю `database` (закроется в tearDown) + первый доступ через `openHelper.writableDatabase` → `assertThrows(IllegalStateException)`; неудачный инстанс закрыть до raw-проверки, затем raw `SQLiteDatabase.openDatabase(...)` — `user_version` = 2 и пользовательская строка на месте. Запуск: `./gradlew :app:connectedGithubDebugAndroidTest --console=plain -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.data.db.WorkoutDatabaseTest'` (github — дефолтный флейвор; задачи без флейвора не существует) — тест красный (фоллбек пересоздаёт базу без исключения). См. design.md Decision 3

## 2. Реализация

- [ ] 2.1 В `WorkoutDatabase.kt` убрать `.fallbackToDestructiveMigration(dropAllTables = false)` из `create(...)`; упростить `seedCallback` — только `onCreate` → `seedStandardExercises(db)`, удалить `onDestructiveMigration`/`onOpen`/`needsSeed` и хак-комментарий про Room 2.8; обновить KDoc шапки, `create(...)` и `seedCallback` (убрать «после каждого деструктивного пересоздания таблиц» и ссылку на seed-on-destructive-migration); в `ExerciseEntity.kt` обновить KDoc `isArchived`/`iconNumber` (убрать обоснования через деструктивное пересоздание). `createInMemory(...)` не трогать. Запуск: тест 1.1 зелёный
- [ ] 2.2 В `WorkoutDatabaseTest.kt` удалить осиротевший хелпер `countExercisesByCustomFlag` (использовался только переписанным тестом); обновить KDoc класса теста и комментарий `tearDown` (исключение — теперь тест отсутствия деструктивного фоллбека; `deleteDatabase` в `tearDown` остаётся для файлового теста). Запуск: класс `WorkoutDatabaseTest` целиком зелёный

## 3. Спека и версия

- [ ] 3.1 Сверить дельту `specs/workout-data/spec.md` с реализацией (контракт «без миграции — `IllegalStateException`, данные целы»); выполнить `openspec validate release-migration-no-destructive-fallback` (синхронизация main-спеки — шаг 4.3: `openspec archive` обновляет main-спеки)
- [ ] 3.2 Поднять `VERSION_CODE` до 11 в `gradle.properties` (первая сборка с новым контрактом БД — должна отличаться от предыдущих на dev-устройствах; adb блокирует только понижение)
- [ ] 3.3 Обновить `AGENTS.md` (Project Structure → `data/db/`): предложение «до первого релиза `fallbackToDestructiveMigration(dropAllTables = false)` пересоздаёт БД…» заменить на контракт «изменения схемы — через Migration, prepopulate — при первом создании БД», хвост «(тот же список используется и в `deleteAllData`)» сохранить. Проверка: `grep fallbackToDestructiveMigration AGENTS.md` пуст, `grep deleteAllData AGENTS.md` не пуст

## 4. Проверка и архивация

- [ ] 4.1 `make format && make lint && make test` — один прогон, все зелёные (отчёт `scripts/test_report.py` в выводе)
- [ ] 4.2 `make emulator-fast`, затем `make android-test` — один прогон, все зелёные (отчёт `scripts/test_report.py` в выводе)
- [ ] 4.3 Заархивировать изменение: `openspec archive release-migration-no-destructive-fallback`
