## Why

Текущий слой данных (`workout-data-layer`) умеет создавать и редактировать
упражнения и записи о выполнении, но не умеет их удалять. Когда появится
UI редактирования тренировок, пользователю понадобится возможность убрать
неудачное упражнение из каталога и стереть ошибочную запись о подходе —
без потери уже накопленной истории тренировок.

## What Changes

- Расширяем `ExerciseDao` и `ExerciseEntryDao` методами `deleteById` —
  жёсткое удаление по идентификатору через Room.
- Расширяем `RoomWorkoutDataRepository` методами
  `deleteExercise(id: Long): Result<Unit>` и
  `deleteEntry(id: Long): Result<Unit>`. Оба возвращают `Result.failure`
  с понятной ошибкой при нарушении бизнес-правила (см. ниже), а не
  пробрасывают низкоуровневое исключение Room/SQLite.
- Стандартные упражнения (`isCustom = false`) удалять **нельзя** —
  репозиторий отклоняет запрос. Стандартный каталог фиксируется
  prepopulate'ом при создании БД и восстановлению не подлежит.
- Удаление пользовательского упражнения, на которое ссылается хотя бы
  одна запись, **отклоняется** (FK `NO ACTION` гарантирует это в БД;
  репозиторий переводит `SQLiteConstraintException` в `Result.failure`
  с сообщением «у этого упражнения есть записи, удалите их сначала»).
- Удаление записи о выполнении **безусловно** — на неё никто не ссылается
  (это лист дерева зависимостей).
- Схема БД не меняется: `NO ACTION` уже заложен в `workout-data-layer`,
  никаких миграций. Файл `app/schemas/.../1.json` остаётся валидным.
- Покрываем androidTest'ом: удаление записи; удаление упражнения без
  записей (успех); удаление стандартного упражнения (отказ);
  удаление пользовательского упражнения с записями (отказ с осмысленной
  ошибкой через репозиторий).

## Capabilities

### New Capabilities

<!-- Эта change не вводит новую capability — она добавляет требования
     в существующую `workout-data` (заархивирована как
     workout-data-layer). Сценарии удаления появятся в той же
     `specs/workout-data/spec.md` через дельту этого change. -->

### Modified Capabilities

- `workout-data`: добавлены требования к удалению упражнений и записей
  с правилами запрета удаления стандартных упражнений и упражнений
  с историей.

## Impact

- `app/src/main/java/com/myworkouts/data/db/ExerciseDao.kt`:
  добавляется `suspend fun deleteById(id: Long)`.
- `app/src/main/java/com/myworkouts/data/db/ExerciseEntryDao.kt`:
  добавляется `suspend fun deleteById(id: Long)`.
- `app/src/main/java/com/myworkouts/data/repository/RoomWorkoutDataRepository.kt`:
  добавляются `deleteExercise(id): Result<Unit>` и
  `deleteEntry(id): Result<Unit>` с try/catch и маппингом
  `SQLiteConstraintException` → `Result.failure`.
- `app/src/androidTest/java/com/myworkouts/data/repository/RoomWorkoutDataRepositoryTest.kt`:
  +4 теста (запись удаляется; кастомное упражнение без записей
  удаляется; стандартное не удаляется; кастомное с записями не
  удаляется через репозиторий — FK-ошибка становится `Result.failure`).
- БД, схема, prepopulate, доменные модели, валидатор записей,
  существующие unit- и androidTest'ы — без изменений.
- `MainActivity` и UI — без изменений: API добавлен, потребителей пока
  нет (UI удаления — отдельный change).
