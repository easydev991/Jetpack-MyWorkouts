# Tasks: exercise-icon-mvp

Порядок — TDD: тест на X раньше реализации X (правила проекта). JVM-тесты
запускаются `make test` (или точечно `./gradlew testDebugUnitTest --tests …`),
androidTest — `make android-test` (нужен эмулятор).

## 1. Модель и мапперы (workout-data)

- [x] 1.1 Тест (JVM): roundtrip `iconNumber` в Mappers — `toEntity(toDomain)` сохраняет значение и `null`; тест падает (красный) до реализации
- [x] 1.2 Добавить `iconNumber: Int? = null` в `Exercise` (domain/model) и `ExerciseEntity` (колонка `icon_number`, nullable, без SQL DEFAULT); прокинуть поле в `Mappers.toDomain`/`toEntity`; тест 1.1 зелёный
- [x] 1.3 Поднять версию `WorkoutDatabase` 2 → 3; убедиться, что экспортированная схема `3.json` появилась в `app/schemas/` и содержит колонку `icon_number` (проверка — содержимое файла после сборки)

## 2. Prepopulate стандартного набора

- [x] 2.1 Тест (androidTest, `WorkoutDatabaseTest`): все 13 стандартных упражнений после создания БД читаются с `iconNumber = null` (сценарий «Стандартный набор без иконок при prepopulate»); контрактный тест prepopulate — выполняется после раздела 1, зелёный по построению (колонка `icon_number` уже nullable, seed-SQL её ещё не пишет → SQLite даёт `NULL`); расхождение SQL со схемой поймает именно он
- [x] 2.2 Добавить `iconNumber: Int? = null` в `StandardExercise`; дополнить raw SQL INSERT в `seedCallback` колонкой `icon_number` (значение `NULL` — номера пока не проставляются, design Decision 3); тест 2.1 зелёный

## 3. Бэкап: экспорт (unit, TDD)

- [x] 3.1 Тесты (JVM, `ExportBackupUseCaseTest`): упражнение с `iconNumber = 5` попадает в файл как число; упражнение без иконки — как явный `null` (сценарий «Номер иконки в файле»); красный
- [x] 3.2 Реализовать в `buildBackupJson`: `.put("iconNumber", exercise.iconNumber ?: JSONObject.NULL)` (паттерн `comment`/`reps`, design Decision 4); тесты 3.1 зелёные

## 4. Бэкап: импорт и merge (unit, TDD)

- [x] 4.1 Тесты (JVM, `ImportBackupUseCaseTest`): файл v1 без `iconNumber` в упражнениях парсится, `iconNumber = null` (сценарий «Старый файл без номера иконки импортируется»); файл с `iconNumber` парсится со значением; `mergeBackup` вставляет упражнение с `iconNumber` из файла и не трогает `iconNumber` существующего при дедупликации (сценарии «Номер иконки вставленного упражнения сохраняется» / «Номер иконки существующего упражнения при дедупликации не меняется»); красный *(примечание после ревью: «парсится со значением» и «вставленное сохраняет из файла» — один путь, отдельный тест-дубль удалён, оба пункта покрывает `import_when_newExercise_then_iconNumberKeptFromBackup`)*
- [x] 4.2 Реализовать: `parseExercise` читает `iconNumber = obj.optionalInt("iconNumber")`; `mergeBackup` — без изменений кода (сохранение через существующий `copy`), убедиться тестами 4.1; зелёный

## 5. Репозиторий (androidTest)

- [x] 5.1 Тест (`RoomWorkoutDataRepositoryTest`): вставка упражнения с `iconNumber = 5` и чтение возвращает 5; вставка без `iconNumber` — `null` (сценарии «Сохранение и чтение номера иконки» / «Упражнение без иконки» / «Отсутствие иконки и её наличие различимы»)
- [x] 5.2 Убедиться, что тест 5.1 проходит без правок репозитория (поле проходит через entity/мапперы); при расхождении — чинить data-слой, не тест

## 6. Финализация

- [x] 6.1 `make format && make lint && make test` — один прогон, один отчёт; исправить упавшие, перечитывать отчёт через `scripts/test_report.py` без повторного прогона; полный прогон заодно покрывает сохранность существующих вызовов конструкторов `Exercise`/`ExerciseEntity`/`StandardExercise` (Kotlin-дефолты `null`) — отдельная задача не нужна
- [x] 6.2 `make android-test` (требуется эмулятор) — прогон androidTest, включая prepopulate- и репозиторий-тесты
