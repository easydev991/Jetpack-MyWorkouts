# Proposal

## Why

Перед первым релизом закрывается зафиксированное обещание (KDoc
`WorkoutDatabase`, design.md архивной задачи
2026-09-17-seed-on-destructive-migration — Decision 1 и Risks/Trade-offs):
«с первым релизом деструктивный фоллбек убирается и изменения схемы идут
через Migration». Пока `.fallbackToDestructiveMigration(dropAllTables =
false)` остаётся в `create(...)`, любой будущий bump версии схемы
(например, добавление таблицы `workouts` = v4, заготовка уже есть —
`workoutId` в `ExerciseEntryEntity`) молча сотрёт пользователям все
записи о тренировках.

## What Changes

- **BREAKING** (только для pre-release dev-устройств с файлом БД старой
  версии): из `WorkoutDatabase.create(...)` убирается
  `.fallbackToDestructiveMigration(dropAllTables = false)` — при
  расхождении версий без зарегистрированной миграции Room бросает
  `IllegalStateException` вместо молчаливого деструктивного пересоздания.
- `seedCallback` упрощается до одного `onCreate`: удаляются
  `onDestructiveMigration`, `onOpen`, флаг `needsSeed` и хак-комментарий
  под Room 2.8 (перестаёт быть нужным). `seedStandardExercises(db)`
  сохраняется — prepopulate при первом создании БД остаётся контрактом.
- KDoc `WorkoutDatabase` (шапка, `create(...)`, `seedCallback`) и
  KDoc `ExerciseEntity` (`isArchived`, `iconNumber`) обновляются:
  ссылки на деструктивный фоллбек/пересоздание убираются, контракт —
  «изменения схемы идут через `Migration`».
- Инструментальный тест
  `destructiveMigration_whenUserVersionDowngraded_...` переписывается
  на месте (переименование в `noDestructiveFallback_...`) на
  противоположный по контракту: переоткрытие файловой БД с заниженным
  `user_version` через `create(...)` → ожидается
  `IllegalStateException`, данные не тронуты. Осиротевший хелпер
  `countExercisesByCustomFlag` удаляется.
- Спека `workout-data` (через дельту; main-спека обновляется при
  архивации — tasks.md 4.3): сценарий «Изменение схемы до релиза»
  меняется на «Room бросает исключение при расхождении версий, требуется
  Migration в `addMigrations(...)`»; устаревший сценарий «Деструктивная
  миграция восстанавливает стандартный набор» удаляется; вычищаются
  устаревшие упоминания деструктивного фоллбека в требованиях про
  `is_archived` и `icon_number`; в таблице иконок попутно выравнивается
  имя упражнения: «19 — приседания пистолетиком» (в main-спеке было
  «на одной ноге», код — ресурс `exercise_pistol_squats`).
- `VERSION_CODE` в `gradle.properties` поднимается на 1 (10 → 11) —
  первая сборка с новым контрактом БД должна отличаться от предыдущих
  на dev-устройствах (по образцу Decision 5 архивной задачи).

## Capabilities

### New Capabilities

(нет)

### Modified Capabilities

- `workout-data`: контракт изменения схемы БД — вместо деструктивного
  пересоздания «до первого релиза»: отсутствие подходящей `Migration`
  приводит к `IllegalStateException` при открытии БД. Требование
  «Предзаполнение каталога стандартными упражнениями» заменяется
  (REMOVED + ADDED, новое имя «Предзаполнение каталога и миграции
  схемы БД»): сценарии деструктивного пути
  («Изменение схемы до релиза», «Деструктивная миграция восстанавливает
  стандартный набор») уступают место сценарию «Изменение схемы без
  миграции отклоняется»; контракт prepopulate сохраняется. В требованиях
  «Единый каталог для стандартных и пользовательских упражнений» и
  «Опциональный номер иконки упражнения» вычищаются устаревшие
  обоснования через `fallbackToDestructiveMigration`.

## Impact

- `app/src/main/java/com/myworkouts/data/db/WorkoutDatabase.kt` —
  `create(...)`, `seedCallback`, KDoc (шапка, `create(...)`,
  `seedCallback`).
- `app/src/main/java/com/myworkouts/data/db/ExerciseEntity.kt` — KDoc
  `isArchived`/`iconNumber` (упоминания деструктивного пересоздания).
- `app/src/main/java/com/myworkouts/data/db/ExerciseEntryEntity.kt` —
  не трогается: KDoc «отдельной миграцией v1→v2» правится в будущей
  задаче v3→v4 (change ещё не создан, см. design.md Non-Goals).
- `app/src/androidTest/java/com/myworkouts/data/db/WorkoutDatabaseTest.kt` —
  новый тест отсутствия деструктивного фоллбека, удаление устаревшего
  теста и хелпера.
- `gradle.properties` — `VERSION_CODE` 10 → 11.
- `AGENTS.md` — раздел Project Structure (`data/db/`): предложение про
  деструктивный фоллбек → «изменения схемы — через Migration».
- `openspec/specs/workout-data/spec.md` — обновляется при архивации
  (`openspec archive` обновляет main-спеки, tasks.md 4.3).
- Не меняется: контракт prepopulate (13 стандартных упражнений), имена
  стандартных упражнений, `createInMemory(...)` (фоллбека там и не было),
  схема БД (версия остаётся 3, экспорт в `app/schemas/` не трогается).
  Сама миграция v3→v4 (таблица `workouts`) — отдельная задача.
