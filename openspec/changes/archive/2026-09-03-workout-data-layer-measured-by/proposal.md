## Why

Будущий экран создания записи должен показывать поле «повторы» или поле
«длительность» в зависимости от выбранного упражнения. Категория
(`PUSH`/`PULL`/`LEGS`/`CORE`/`CARDIO`) этого не определяет: планка и
скручивания — обе `CORE`, но планка выполняется на время, а скручивания —
на повторы. Типу измерения нужно быть атрибутом самого упражнения в слое
данных — до того, как UI-форма начнёт по нему ветвиться.

## What Changes

- Домен: новый enum `ExerciseMeasuredBy { REPS, TIME }`; модель `Exercise`
  получает поле `measuredBy` со значением по умолчанию `REPS`
  («большинство упражнений — на повторы»).
- БД: `ExerciseEntity` получает колонку `measured_by` (строка — имя
  значения enum, как уже сделано для `category`).
- Prepopulate: все 13 стандартных упражнений размечаются типом измерения —
  `TIME` для планки и скакалки, `REPS` для остальных 11; SQL-вставка
  prepopulate добавляет колонку.
- Пользовательские упражнения: тип измерения сохраняется вместе с
  упражнением; при создании по умолчанию — `REPS`.
- Миграция БД **не пишется**: приложение не в релизе, пользователей нет.
  Версия БД остаётся `1`, файл схемы `app/schemas/.../1.json`
  перегенерируется. Девайсы разработчика требуют переустановки/очистки
  данных приложения.
- `EntryValidator` и модель записи **не меняются**: `reps` и `durationSec`
  остаются опциональными; соответствие «measuredBy ↔ заполненные поля
  записи» — ответственность будущего UI, слой данных хранит, но не
  навязывает.
- Обновляемые тесты: `WorkoutDatabaseTest` (prepopulate размечает
  measuredBy), `StandardExercisesLocalizationTest`, тесты мапперов и
  `RoomWorkoutDataRepositoryTest`.

## Capabilities

### New Capabilities

<!-- Новая capability не вводится: поле касается существующего каталога
     упражнений в `workout-data`. -->

### Modified Capabilities

- `workout-data`: упражнение каталога получает обязательный тип измерения
  `measuredBy` (`REPS`/`TIME`) — в prepopulate стандартных упражнений,
  при создании пользовательских и при чтении каталога; слой данных
  хранит `measuredBy`, но не валидирует соответствие типа измерения
  заполненным полям записи (это будущий UI).

## Impact

- `app/src/main/java/com/myworkouts/domain/model/ExerciseMeasuredBy.kt`:
  новый enum `ExerciseMeasuredBy`.
- `app/src/main/java/com/myworkouts/domain/model/Exercise.kt`: поле
  `measuredBy: ExerciseMeasuredBy = ExerciseMeasuredBy.REPS`.
- `app/src/main/java/com/myworkouts/data/db/ExerciseEntity.kt`: колонка
  `measured_by` (строка).
- `app/src/main/java/com/myworkouts/data/db/StandardExercise.kt`,
  `StandardExercises.kt`: `measuredBy` в паре prepopulate и в 13
  стандартных упражнениях.
- `app/src/main/java/com/myworkouts/data/db/WorkoutDatabase.kt`:
  prepopulate SQL-вставка пишет `measured_by`.
- `app/src/main/java/com/myworkouts/data/db/Mappers.kt`: двусторонний
  маппинг `measuredBy` ↔ `measured_by`.
- `app/src/androidTest/...`: `WorkoutDatabaseTest`,
  `StandardExercisesLocalizationTest`, `RoomWorkoutDataRepositoryTest` —
  обновление под новое поле; новые проверки разметки планки и скакалки.
- `app/schemas/com.myworkouts.data.db.WorkoutDatabase/1.json`:
  перегенерация схем after build.
- `EntryValidator`, `ExerciseEntry`, DAO-интерфейсы, UI — без изменений.
