## Context

`WorkoutDatabase.seedCallback` (WorkoutDatabase.kt:90-118) переопределяет
только `Callback.onCreate` — Room вызывает его единожды при первом
создании файла БД. `fallbackToDestructiveMigration()` (строка 66) при
расхождении `user_version` с версией схемы деструктивно пересоздаёт
таблицы, но Room вызывает при этом `onDestructiveMigration`, который не
переопределён — каталог остаётся пустым. Мотивация и эмпирика — см.
proposal.md (Why). Существующая спека `workout-data` (сценарий
«Изменение схемы до релиза») уже декларирует «пересоздаётся деструктивно
с новым prepopulate» — реализация просто не выполняла обещанное.

## Goals / Non-Goals

**Goals:**
- После деструктивной миграции каталог содержит те же 13 стандартных
  упражнений, что и при первом создании БД.
- Ноль дублирования: INSERT-логика живёт в одном методе, оба колбэка
  (`onCreate`, `onDestructiveMigration`) его вызывают.
- Красный-зелёный цикл: инструментальный тест падает до фикса.

**Non-Goals:**
- Восстановление пользовательских данных после деструктивной миграции —
  она остаётся деструктивной (обоснование — в Risks / Trade-offs).
- Валидация уникальности имён упражнений, видимость вкладки
  «Упражнения», переход на неразрушающие миграции — вне scope,
  см. proposal.md (What Changes).

## Decisions

### 1. `onDestructiveMigration` в существующем `seedCallback`

Добавить в анонимный `Callback` переопределение:

```kotlin
override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
    super.onDestructiveMigration(db)
    seedStandardExercises(db)
}
```

Альтернативы: отдельный `Callback` для миграций — отвергнуто: два
объекта на одну заботу, порядок вызова не гарантирован; другой механизм
(например, `addMigrations` с пустой миграцией) —
отвергнуто: меняет контракт деструктивного фоллбека, зафиксированный
спекой до релиза.

### 2. Извлечение общего `seedStandardExercises(db)`

Тело `onCreate` (транзакция + INSERT-цикл по `standardExercises`)
выносится в private-метод `seedStandardExercises(db: SupportSQLiteDatabase)`
внутри `seedCallback`; оба колбэка вызывают его. Замыкание на
`standardExercises` сохраняется — имена по-прежнему резолвятся до
регистрации callback'а, поскольку у callback нет доступа к `Context`
(решение зафиксировано в KDoc `WorkoutDatabase`/`StandardExercises`).

### 3. Тест — файловая БД с заниженным `user_version` в `WorkoutDatabaseTest`

Инструментальный тест (JUnit 4, `WorkoutDatabaseTest`): создать файловую
БД (context.getDatabasePath + `Room.databaseBuilder` без prepopulate),
открыть/закрыть, выставить `PRAGMA user_version = 2` через
`SupportSQLiteOpenHelper` или raw `SQLiteDatabase`, затем переоткрыть
через `WorkoutDatabase.create(...)` (schema v3) и посчитать строки
`exercises` с `is_custom = 0` — ожидается ровно 13. До фикса тест красный
(0 строк). In-memory не подходит — у неё нет файла и деструктивный путь
по версии не срабатывает.

Альтернатива: эмуляция через `MigrationTestHelper` — отвергнуто: тянет
room-testing-харнесс ради одного сценария; PRAGMA-подход уже воспроизведён
вручную на эмуляторе (см. proposal.md).

### 4. Совместимость с Decision 4 (backup-is-archived-flag) — без изменений

`ImportBackupUseCase.mergeBackup` не трогается: вставленные из файла
упражнения по-прежнему всегда `isCustom = true`. После деструктивной
миграции каталог репопулируется стандартными (`isCustom = false`),
restore дедуплицирует по именам — записи привязываются к существующим
строкам, `isCustom = 0` стандартных сохраняется. Противоречий нет.

### 5. `VERSION_CODE` → 4 в `gradle.properties`

Чтобы сборка с фиксом установилась поверх сборки 3 на устройствах
пользователей (Android требует монотонный versionCode).

## Risks / Trade-offs

- **Потеря пользовательских данных при деструктивной миграции остаётся** →
  вне scope: фикс закрывает только пустой каталог; переход на миграции —
  перед первым релизом (зафиксировано в спеке и KDoc `WorkoutDatabase`).
- **`onDestructiveMigration` вызывается вне пользовательской транзакции** →
  seed сам оборачивается в `db.beginTransaction()/setTransactionSuccessful/`
  `endTransaction` (переносится без изменений из `onCreate`); сбой вставки
  откатит seed, но не миграцию — таблицы останутся пустыми, как до фикса,
  регресса нет.
- **Тест с raw-файлом может конфликтовать с in-memory-тестами в классе** →
  файл теста изолируется по `context.getDatabasePath("workout_database_test_…")`
  и удаляется в `@After`.

## Migration Plan

Изменение обратно совместимо: новое поведение включается только на пути
`onDestructiveMigration`, котором раньше соответствовал пустой каталог.
Откат — revert `WorkoutDatabase.kt` и теста. `VERSION_CODE=4` откатывается
синхронно.

## Open Questions

Нет.
