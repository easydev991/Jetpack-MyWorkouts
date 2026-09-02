## 1. Зависимости

- [x] 1.1 В `gradle/libs.versions.toml` добавить: версию `room` (последняя
  стабильная 2.8.x), плагин `ksp` (`com.google.devtools.ksp`, версия,
  согласованная с Kotlin 2.4.10), библиотеки `androidx-room-runtime`,
  `androidx-room-ktx`, `androidx-room-compiler`, `androidx-room-testing`
  (androidTest). Верификация: файл правится, `./gradlew help` проходит.
- [x] 1.2 В `app/build.gradle.kts` подключить плагин `ksp`, добавить
  `implementation(room-runtime, room-ktx)`, `ksp(room-compiler)`,
  `androidTestImplementation(room-testing)`, включить schema export:
  `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Верификация:
  `./gradlew :app:assembleDebug` зелёный.

## 2. Домен

- [x] 2.1 Создать `domain/model/ExerciseCategory.kt`:
  `enum class ExerciseCategory { PUSH, PULL, LEGS, CORE, CARDIO }`.
- [x] 2.2 Создать `domain/model/Exercise.kt`:
  `data class Exercise(id: Long, name: String, category: ExerciseCategory,
  isCustom: Boolean, comment: String?, createdAt: Long)`.
- [x] 2.3 Создать `domain/model/ExerciseEntry.kt`:
  `data class ExerciseEntry(id: Long, exerciseId: Long, workoutId: Long?,
  date: Long, sets: Int, reps: Int?, weightKg: Double?, durationSec: Int?,
  comment: String?, createdAt: Long, modifiedAt: Long)`.
- [x] 2.4 Создать `app/src/test/.../domain/model/ExerciseCategoryTest.kt`:
  тест полного набора категорий (`PUSH, PULL, LEGS, CORE, CARDIO`) —
  контракт prepopulate и БД. Верификация: `make test` зелёный.

## 3. Room: сущности, DAO, БД

- [x] 3.1 Создать `data/db/ExerciseEntity.kt` и
  `data/db/ExerciseEntryEntity.kt` по design.md (2 таблицы; у записи —
  FK на `exercises.id` с NO ACTION, индексы `exerciseId` и `date`,
  nullable-поля `reps`/`weightKg`/`durationSec`/`comment`/`workoutId`).
- [x] 3.2 Создать `data/db/ExerciseDao.kt`: `observeExercises(query)`
  (LIKE + `ORDER BY name COLLATE NOCASE`), `insert`, `update`, `getById`;
  `data/db/ExerciseEntryDao.kt`: `insert`, `update` (`@Update` над entity
  как есть — `modifiedAt` DAO не вычисляет; репозиторий выставляет его
  через `copy` до вызова, см. 5.3), `getById`.
- [x] 3.3 Создать `data/db/WorkoutDatabase.kt`: `@Database(version = 1,
  entities = [...])`, companion-фабрика
  `create(context, standardExercises: List<StandardExercise>)` —
  готовые строки передаются параметром и замыкаются в callback
  (design.md Decision 4).
- [x] 3.4 Создать `data/db/StandardExercise.kt` (пара
  `name: String, category: ExerciseCategory`) и
  `data/db/StandardExercises.kt` — функция
  `standardExercises(context): List<StandardExercise>`, резолвящая 13
  названий из строковых ресурсов (design.md Decision 4).
- [x] 3.5 Создать `data/db/Mappers.kt`: entity ↔ domain (категория —
  `ExerciseCategory.valueOf`/`.name`). Верификация:
  `./gradlew :app:assembleDebug` зелёный.

## 4. Строки + prepopulate

- [x] 4.1 В `res/values/strings.xml` (ru) добавить 13 названий
  стандартных упражнений (exercise_pullups, exercise_australian_pullups,
  exercise_squats, exercise_pushups, exercise_pushups_knees,
  exercise_lunges, exercise_bars, exercise_plank, exercise_crunches,
  exercise_hanging_leg_raises, exercise_pistol_squats, exercise_running,
  exercise_jump_rope). Верификация: сборка ресурсов зелёная.
- [x] 4.2 В `res/values-en/strings.xml` (en) добавить те же 13 ключей.
  Верификация: `./gradlew :app:lintDebug` зелёный — нет MissingTranslation.
- [x] 4.3 Реализовать prepopulate в `WorkoutDatabase.create`:
  `RoomDatabase.Callback.onCreate` вставляет 13 упражнений
  (`isCustom = false`, `createdAt = now`) через транзакцию. Верификация:
  тест 6.1 зелёный.

## 5. Репозиторий + валидация

- [x] 5.1 Создать `data/repository/RoomWorkoutDataRepository.kt` —
  concrete-класс (без интерфейса: он появится в следующем change вместе
  с fake для VM-тестов, YAGNI): делегирует DAO, маппит entity ↔ domain.
- [x] 5.2 Создать `data/repository/EntryValidator.kt`: pure-функция
  (подходы ≥ 1; повторы — null или ≥ 1; вес — null или ≥ 0;
  workoutId — только null) → `Result`/ошибка. Верификация:
  тест 5.4 зелёный.
- [x] 5.3 В `RoomWorkoutDataRepository`: `addEntry`/`updateEntry`
  прогоняют валидатор и FK-ошибки через try/catch в `Result`,
  `updateEntry` ставит `modifiedAt = now` (createdAt не трогает).
- [x] 5.4 Создать `app/src/test/.../data/repository/EntryValidatorTest.kt`:
  rejected — подходы 0, повторы 0, вес −0.5, workoutId ≠ null; accepted —
  вес null vs 0.0 различимы, запись «на время» (reps=null, duration=60).
  Верификация: `make test` зелёный.

## 6. androidTest: Room in-memory

- [x] 6.1 `WorkoutDatabaseTest`: после создания БД каталог соответствует
  spec-сценарию «Первый запуск приложения» (ровно 13 стандартных,
  0 пользовательских; состав и категории — design.md Decision 4).
- [x] 6.2 `ExerciseEntryDaoTest`: вставка записи с существующим
  `exerciseId` сохраняется; с несуществующим — FK-ошибка, записи нет.
- [x] 6.3 `ExerciseDaoTest`: поиск по подстроке «под» без учёта регистра
  возвращает только совпадения; пустой запрос — весь каталог; сортировка
  по имени без учёта регистра.
- [x] 6.4 `ExerciseEntryUpdateTest`: update записи меняет поля,
  `modifiedAt` обновляется, `createdAt` не меняется; вес null vs 0.0
  различимы при чтении; «планка 60 сек» сохраняется с reps = null.
  Верификация всех: `make android-test` зелёный (новые классы).
- [x] 6.5 `RoomWorkoutDataRepositoryTest` (in-memory): `addEntry` с
  несуществующим `exerciseId` → `Result.isFailure`, в таблице записей
  0 строк — spec-сценарий «Ссылка на несуществующее упражнение»,
  покрытый через репозиторий, а не только DAO. Верификация:
  `make android-test` зелёный.
- [x] 6.6 `StandardExercisesLocalizationTest`: `standardExercises(context)`
  под контекстами локалей `ru` и `en` возвращает названия, равные строкам
  из `values/strings.xml` и `values-en/strings.xml` — spec-сценарий
  «Локализация при prepopulate». Верификация: `make android-test` зелёный.

## 7. Финальная верификация

- [x] 7.1 `make format && make lint && make test` — все зелёные.
- [x] 7.2 `make build` — зелёный.
- [x] 7.3 `make android-test` — новые DAO-тесты зелёные, старые не сломаны.
- [x] 7.4 `app/schemas/com.myworkouts.data.db.WorkoutDatabase/1.json`
  появился и закоммичен; `MainActivity` и существующие экраны не изменены
  (git diff не содержит UI-файлов).
