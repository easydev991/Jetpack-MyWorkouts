## 1. Тесты (red — TDD)

- [x] 1.1 Обновить `WorkoutDatabaseTest`: новый тест
  `createDatabase_thenMeasuredByAssignedToExpectedExercises` — после
  первого создания БД планка и скакалка читаются с `measuredBy = TIME`,
  остальные 11 — с `measuredBy = REPS`. Запустить
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myworkouts.data.db.WorkoutDatabaseTest`
  — тест падает (поля ещё нет)
- [x] 1.2 Обновить `StandardExercisesLocalizationTest`: все 13 пар
  `standardExercises(context)` содержат `measuredBy`; `TIME` — ровно
  у планки и скакалки, `REPS` — у остальных. Запустить
  `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.myworkouts.data.db.StandardExercisesLocalizationTest`
  — тест падает до реализации
- [x] 1.3 Добавить JVM-тест `MappersTest` (пакет `data.db`):
  round-trip `Exercise(measuredBy = TIME)` → entity → domain сохраняет
  `TIME`, дефолт `REPS` не искажается. Запустить
  `./gradlew testDebugUnitTest --tests "com.myworkouts.data.db.MappersTest"`
  — тест падает до реализации

## 2. Реализация (green)

- [x] 2.1 Создать `domain/model/ExerciseMeasuredBy.kt` (`REPS`, `TIME`),
  добавить в `Exercise` поле
  `measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS`. Проверка:
  проект компилируется, существующие вызовы `Exercise(...)` не менялись
- [x] 2.2 Добавить `ExerciseEntity` колонку `measured_by` (строка;
  дефолт `"REPS"` — Kotlin-дефолт в конструкторе entity, не SQL
  DEFAULT, по образцу `comment: String? = null`; иначе именованные
  вызовы `ExerciseEntity(...)` в setUp существующих тестов перестанут
  компилироваться). Проверка: `make lint` без ошибок detekt
- [x] 2.3 Разметить `StandardExercise.measuredBy` и все 13 записей
  `standardExercises(...)`: планка и скакалка — `TIME`, остальные —
  `REPS`. Проверка: тест 1.2 зелёный
- [x] 2.4 Маппинг в `Mappers.kt`: `measuredBy` ↔ `measured_by` в обе
  стороны (по образцу `category`). Проверка: тест 1.3 зелёный
- [x] 2.5 Prepopulate в `WorkoutDatabase.create`: SQL-вставка пишет
  `measured_by` из `StandardExercise`. Проверка: тест 1.1 зелёный

## 3. Схема, репозиторий и полная проверка

- [x] 3.1 Тест в `RoomWorkoutDataRepositoryTest`:
  `addExercise` без явного `measuredBy` сохраняет и возвращает `REPS`
  (дефолт); `addExercise` c `TIME` читается обратно как `TIME`.
  Если правки репозитория не потребовались — тест зелёный после
  пп. 2.1–2.4. Без 2.1 не компилируется сам тест (named-аргумент
  `measuredBy`), без 2.2 — маппер из 2.4 (поля entity нет).
  Существующий API репозитория менять не нужно — поле проезжает через
  `addExercise(Exercise)` прозрачно
- [x] 3.2 Собрать проект (`make build` или `./gradlew assembleDebug`) —
  ksp перегенерирует `app/schemas/.../1.json` с колонкой `measured_by`;
  файл закоммитить
- [x] 3.3 Полный прогон перед коммитом: `make format && make lint &&
  make test`, затем `make android-test` на девайсе/эмуляторе
  (APK переустановить — старая БД несовместима, см. design.md Decision 3)
