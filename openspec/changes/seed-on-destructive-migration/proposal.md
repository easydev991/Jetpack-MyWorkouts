## Why

При апгрейде между версиями схемы БД (например, 2→3) Room деструктивно
пересоздаёт таблицы, но prepopulate зарегистрирован только в
`Callback.onCreate` — Room вызывает его лишь при первом создании файла БД.
После деструктивной миграции вызывается `onDestructiveMigration`, поэтому
пользователь получает пустой каталог упражнений (0 вместо 13 стандартных)
и потерю всех записей. Воспроизведено на эмуляторе 2026-09-17: после
`PRAGMA user_version = 2` и перезапуска `SELECT count(*) FROM exercises`
возвращает 0.

## What Changes

- `seedCallback` в `WorkoutDatabase.kt` дополнительно переопределяет
  `onDestructiveMigration` и восстанавливает стандартный набор после
  деструктивного пересоздания таблиц.
- INSERT-блок извлечён из `onCreate` в общий private-метод, вызываемый
  из обоих колбэков (дублирование исключено).
- Инструментальный тест: файловая БД с `user_version = 2` переоткрывается
  приложением → после деструктивной миграции в каталоге ровно 13
  стандартных упражнений с `is_custom = 0`. До фикса тест красный.
- `VERSION_CODE` поднимается до 4 (установка поверх сборки 3 на
  устройствах).
- Вне scope: валидация уникальности имён упражнений (отдельная задача),
  видимость вкладки «Упражнения» (`AppSettingsDataStore`, отдельное
  решение), переход на неразрушающие миграции (с первым релизом).

## Capabilities

### New Capabilities

(нет)

### Modified Capabilities

- `workout-data`: требование «Предзаполнение каталога стандартными
  упражнениями» дополняется сценарием «Деструктивная миграция
  восстанавливает стандартный набор» — после деструктивной миграции
  каталог SHALL содержать те же 13 стандартных упражнений, что и при
  первом создании БД.

## Impact

- `app/src/main/java/com/myworkouts/data/db/WorkoutDatabase.kt` —
  `seedCallback` (+`onDestructiveMigration`), извлечение общего
  seed-метода.
- `app/src/androidTest/java/com/myworkouts/data/db/WorkoutDatabaseTest.kt`
  — новый тест пути деструктивной миграции.
- `gradle.properties` — `VERSION_CODE=4`.
- Дельта: `openspec/changes/seed-on-destructive-migration/specs/workout-data/spec.md`
  → синхронизируется в `openspec/specs/workout-data/spec.md`.
- Decision 4 архивного change `2026-09-17-backup-is-archived-flag`
  не затрагивается — см. design.md Decision 4.
