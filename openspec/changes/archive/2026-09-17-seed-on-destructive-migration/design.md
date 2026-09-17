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

### 1. Обработка деструктивной миграции в существующем `seedCallback`

Room 2.8 (`RoomOpenHelper.onUpgrade`) в деструктивном пути вызывает
пользовательский `Callback.onDestructiveMigration` **после**
`dropAllTables`, но **до** `createAllTables` — прямой seed в момент
вызова падает с «no such table» (подтверждено байткодом room-runtime
2.8.5 и красным прогоном теста). Поэтому переопределение
`onDestructiveMigration` только выставляет флаг, а seed откладывается в
`onOpen`, который Room вызывает уже после создания таблиц:

```kotlin
override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
    super.onDestructiveMigration(db)
    needsSeed = true
}

override fun onOpen(db: SupportSQLiteDatabase) {
    super.onOpen(db)
    if (needsSeed) {
        needsSeed = false
        seedStandardExercises(db)
    }
}
```

Флаг — plain `var`: колбэки одного `Callback`-объекта сериализованы
lock'ом открытия БД (androidx.sqlite `ProcessLock` + монитор
`SQLiteOpenHelper`), атомарность не нужна.

Альтернативы: отдельный `Callback` для миграций — отвергнуто: два
объекта на одну заботу, порядок вызова не гарантирован; seed «если
каталог пуст» в `onOpen` без флага — отвергнуто: воскресил бы удалённые
пользователем стандартные упражнения; другой механизм (например,
`addMigrations` с пустой миграцией) — отвергнуто: меняет контракт
деструктивного фоллбека, зафиксированный спекой до релиза.

### 2. Извлечение общего `seedStandardExercises(db)`

Тело `onCreate` (транзакция + INSERT-цикл по `standardExercises`)
выносится в private-метод `seedStandardExercises(db: SupportSQLiteDatabase)`
внутри `seedCallback`; его вызывают `onCreate` и `onOpen` (под флагом из
Decision 1). Замыкание на
`standardExercises` сохраняется — имена по-прежнему резолвятся до
регистрации callback'а, поскольку у callback нет доступа к `Context`
(решение зафиксировано в KDoc `WorkoutDatabase`/`StandardExercises`).

### 3. Тест — файловая БД с заниженным `user_version` в `WorkoutDatabaseTest`

Инструментальный тест (JUnit 4, `WorkoutDatabaseTest`): создать файловую
БД на production-имени `DATABASE_NAME` (простой `Room.databaseBuilder`
без prepopulate — `WorkoutDatabase.create(...)` не принимает имени,
изолированный файл недоступен),
открыть/закрыть, выставить `PRAGMA user_version = 2` через
`SupportSQLiteOpenHelper` или raw `SQLiteDatabase`, затем переоткрыть
через `WorkoutDatabase.create(...)` (schema v3) и посчитать строки
`exercises` с `is_custom = 0` — ожидается ровно 13. В фикстуру до
занижения версии вставляется одна пользовательская строка
(`is_custom = 1`); после переоткрытия она SHALL отсутствовать
(`count(*) WHERE is_custom = 1` — 0), фиксируя AND-условие сценария
«Деструктивная миграция восстанавливает стандартный набор»: миграция
остаётся деструктивной. До фикса тест красный
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
- **`onDestructiveMigration` вызывается вне пользовательской транзакции и
  до создания таблиц** → seed оборачивается в
  `db.beginTransaction()/setTransactionSuccessful/`
  `endTransaction` (переносится без изменений из `onCreate`) и
  откладывается до `onOpen` (Decision 1); сбой вставки
  откатит seed, но не миграцию — таблицы останутся пустыми, как до фикса,
  регресса нет.
- **Тест с raw-файлом может конфликтовать с in-memory-тестами в классе** →
  тест работает на production-имени `DATABASE_NAME` (`create(...)` не
  принимает имени): файл удаляется перед созданием фикстуры и в
  `@After` — изменённый файл не остаётся после прогона.

## Migration Plan

Изменение обратно совместимо: новое поведение включается только на пути
`onDestructiveMigration`, котором раньше соответствовал пустой каталог.
Откат — revert `WorkoutDatabase.kt` и теста. `VERSION_CODE=4` откатывается
синхронно.

## Open Questions

Нет.
