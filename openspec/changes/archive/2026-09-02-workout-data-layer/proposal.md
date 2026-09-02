## Why

FAB-флоу «создать запись о тренировке» (MVP) заблокирован отсутствием слоя
хранения: в проекте нет ни базы данных, ни доменных моделей упражнений.
Черновик модели (`docs/exercise-model-analysis.md`) зафиксировал решения —
этот change переводит их в работающий Room-слой, на который ляжет следующий
change (экран создания/редактирования записи с одним упражнением).

## What Changes

- Добавляем зависимости Room (runtime + KSP-компилятор) в
  `gradle/libs.versions.toml` и `app/build.gradle.kts`; включаем schema
  export в `app/schemas/`. Никаких сетевых — offline-only сохраняется.
- Добавляем доменные модели: `ExerciseCategory` (enum `PUSH`/`PULL`/`LEGS`/
  `CORE`/`CARDIO`), `Exercise`, `ExerciseEntry`.
- Добавляем Room-слой: две таблицы (`exercises`, `exercise_entries`) по
  черновику — один каталог, плоские поля записи, `workoutId = null` в MVP,
  индексы `exerciseId` и `date`, FK записи → упражнение.
- Добавляем prepopulate: 13 стандартных упражнений при первом создании БД —
  силовые, кор и кардио (полный состав — design.md, Decision 4). Названия —
  из локализованных строковых ресурсов (ru/en).
- Добавляем репозиторий с API под создание/редактирование записи
  (поиск и сортировка каталога, создание/редактирование упражнения,
  вставка/редактирование записи с валидацией и `Result`).
- UI (экран, ViewModel, интеграция FAB) — вне scope, отдельный change.
- Покрываем unit-тестами (домен, валидация) и instrumented-тестами
  (DAO на Room in-memory: prepopulate, FK, поиск, редактирование).

## Capabilities

### New Capabilities

- `workout-data`: персистентный слой каталога упражнений и записей о
  выполнении. Включает доменные модели, Room-схему v1 (2 таблицы),
  prepopulate стандартных упражнений, репозиторий с валидацией и
  `Result`, локализацию названий стандартных упражнений ru/en,
  unit- и instrumented-покрытие.

### Modified Capabilities

<!-- Нет: существующие capabilities (навигация, экраны) требования не меняют.
     Интеграция БД в UI — следующий change. -->

## Impact

- `gradle/libs.versions.toml`, `app/build.gradle.kts`: + room, + KSP-плагин
  (версия, согласованная с Kotlin 2.4.10), + room-testing (androidTest).
- `app/src/main/java/com/myworkouts/`:
  - `domain/model/ExerciseCategory.kt`, `domain/model/Exercise.kt`,
    `domain/model/ExerciseEntry.kt` — новые.
  - `data/db/` — новый пакет: сущности, DAO, `WorkoutDatabase`, мапперы,
    prepopulate-провайдер.
  - `data/repository/` — новый пакет: репозиторий (concrete-класс,
    интерфейс — следующий change) + валидатор.
- `res/values/strings.xml` + `res/values-en/strings.xml`: +13 названий
  стандартных упражнений.
- `app/schemas/` — экспортированная схема v1 (в git, база будущих миграций).
- `MainActivity` и все существующие экраны НЕ затрагиваются: БД создаётся
  фабрикой, интеграция в UI — следующий change.
- Тесты: JVM (категории, валидация) + androidTest (DAO, Room in-memory).
