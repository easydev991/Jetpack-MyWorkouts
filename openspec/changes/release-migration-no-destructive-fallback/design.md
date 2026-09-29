# Design

## Context

`WorkoutDatabase.create(...)` регистрирует
`.fallbackToDestructiveMigration(dropAllTables = false)` — promise из
KDoc шапки и design.md архивной задачи
2026-09-17-seed-on-destructive-migration (Decision 1, Risks/Trade-offs):
«с первым релизом деструктивный фоллбек убирается и изменения схемы идут
через Migration». `seedCallback` существует в
расширенном виде именно ради деструктивного пути: `onDestructiveMigration`
ставит флаг, `onOpen` досеивает каталог после пересоздания таблиц (хак
под Room 2.8, который вызывает колбэк до `createAllTables`). В
`createInMemory(...)` фоллбека нет и не было. Мотивация — proposal.md (Why).

## Goals / Non-Goals

**Goals:**

- Расхождение версий БД без зарегистрированной миграции →
  `IllegalStateException` при открытии, файл не трогается (fail fast
  вместо тихой потери данных).
- `seedCallback` — только `onCreate`: prepopulate при первом создании БД
  сохраняется, мёртвый деструктивный код удаляется.
- Красный-зелёный цикл: инструментальный тест падает, пока фоллбек на
  месте, и зеленеет после его удаления.

**Non-Goals:**

- Сама миграция v3→v4 (таблица `workouts`) — отдельная задача; здесь
  только убирается фоллбек, `addMigrations(...)` не появляется.
- Контракт prepopulate (13 стандартных, имена, `measuredBy`, `iconNumber`)
  — не меняется.
- Схема БД: версия остаётся `3`, экспорт в `app/schemas/` не трогается.
- Устаревший KDoc `ExerciseEntryEntity` («отдельной миграцией v1→v2»)
  — правится в задаче v3→v4, где контекст этой строки меняется
  (решение по ревью).

## Decisions

### 1. Убрать фоллбек только из `create(...)`

`.fallbackToDestructiveMigration(dropAllTables = false)` удаляется из
цепочки `Room.databaseBuilder`; остальная конфигурация
(`addCallback(seedCallback(...))`) сохраняется. `createInMemory(...)`
не трогается — фоллбека там нет.

Альтернатива: оставить фоллбек до задачи с миграцией v3→v4 — отвергнуто:
обещание зафиксировано в KDoc и архивном design.md, а между этим
изменением и задачей `workouts` любой другой bump схемы (например,
смена default у колонки) молча стёр бы данные.

### 2. `seedCallback` — только `onCreate`

Остаются `onCreate` → `seedStandardExercises(db)` (тело без изменений:
транзакция + INSERT-цикл). Удаляются `onDestructiveMigration`, `onOpen`,
флаг `needsSeed`, хак-комментарий про Room 2.8 — весь этот механизм
существовал только ради деструктивного пути, который больше недостижим
(без фоллбека Room бросает исключение до вызова колбэков). KDoc шапки
класса, KDoc `create(...)` и KDoc `seedCallback` обновляются: ссылки
на деструктивное пересоздание и архивную задачу убираются. Дополнительно
KDoc `ExerciseEntity` (`isArchived`, `iconNumber`): обоснования «при
деструктивном пересоздании схемы…» тоже становятся ложными.

### 3. Тест отсутствия фоллбека взамен деструктивного — «фоллбека нет, данные целы»

Инструментальный тест в `WorkoutDatabaseTest` (файловая БД на
production-имени `DATABASE_NAME` — `create(...)` не принимает имени,
как в архивной задаче Decision 3):

- фикстура: закрыть in-memory `database` из setUp, затем
  `deleteDatabase(DATABASE_NAME)`; plain `Room.databaseBuilder`
  (создаёт файл в v3), вставка одной пользовательской строки,
  `PRAGMA user_version = 2`, `plain.close()`;
- акт: `WorkoutDatabase.create(...)`, инстанс присваивается полю
  `database` (закроется в tearDown, как в старом тесте) — важно:
  `build()` не открывает базу, исключение бросается при первом
  открытии, поэтому в assert входит `openHelper.writableDatabase`;
- THEN: `assertThrows(IllegalStateException)`; неудачный Room-инстанс
  закрыть до raw-проверки, далее raw
  `SQLiteDatabase.openDatabase(...)` (мимо Room, без валидации схемы):
  `user_version` по-прежнему 2 и пользовательская строка на месте —
  пересоздания не было.

Красный-зелёный: тест падает, пока фоллбек на месте (`create(...)`
проходит без исключения, а проверки «версия/данные не тронуты» валятся
на пересозданной базе); зеленеет после удаления фоллбека (Decision 1).
Запуск: `./gradlew :app:connectedGithubDebugAndroidTest --console=plain
-Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.data.db.WorkoutDatabaseTest'`
(задачи без флейвора не существует — флейворы github/rustore).

Альтернатива: проверять только исключение без raw-проверки файла —
отвергнуто: «файл не тронут» — прямое кодирование цели изменения
(защита данных), стоит четыре строки. `MigrationTestHelper` — не нужен:
нет миграций, которые он мог бы прогнать.

Существующий тест
`destructiveMigration_whenUserVersionDowngraded_thenReseedsThirteenStandardExercises`
переписывается на месте (переименование в
`noDestructiveFallback_whenUserVersionDowngraded_thenThrowsAndKeepsData`,
инверсия контракта) — фикстура та же, дифф меньше; его хелпер
`countExercisesByCustomFlag` (использовался только им) удаляется как
осиротевший. `tearDown` сохраняет
`deleteDatabase(DATABASE_NAME)` — теперь он нужен новому файловому
тесту; KDoc класса теста переформулируется («исключение — тест
отсутствия деструктивного фоллбека»).

### 4. `VERSION_CODE` → 11 в `gradle.properties`

`adb install` разрешает установку с тем же `versionCode` и блокирует
только понижение (строгое возрастание требует лишь Play). Бамп — по
образцу Decision 5 архивной задачи: это первая сборка с новым
контрактом БД, она должна отличаться от предыдущих на dev-устройствах.

### 5. Спека — REMOVED + ADDED требования и два MODIFIED

- «Предзаполнение каталога стандартными упражнениями»: требование
  удаляется (REMOVED с Reason/Migration) и заменяется ADDED-требованием
  «Предзаполнение каталога и миграции схемы БД» —
  контракт «до первого релиза — деструктивно» меняется на «изменения
  схемы — через миграцию, без миграции — исключение»; сценарии
  «Изменение схемы до релиза» и «Деструктивная миграция восстанавливает
  стандартный набор» заменяются одним «Изменение схемы без миграции
  отклоняется». Причина структурного решения — эмпирика валидатора
  (проверено при планировании на этой дельте): MODIFIED-вариант без двух
  деструктивных сценариев отклонён («omits scenario(s) the current spec
  still has … a MODIFIED requirement replaces the whole block, so
  archive refuses to drop them»), REMOVED + ADDED с прежним именем тоже
  («Requirement present in both ADDED and REMOVED»); принят только
  REMOVED + ADDED с новым именем требования.
- «Единый каталог…» и «Опциональный номер иконки…» (MODIFIED): удаляются
  устаревшие обоснования «default исключает миграцию при
  `fallbackToDestructiveMigration` (приложение ещё не в релизе)» и «при
  деструктивном пересоздании default не нужен» — после удаления
  фоллбека они ложны. Сами колонки (`DEFAULT 0`, nullable без default)
  не меняются. Попутно в блоке иконок выравнивается имя упражнения:
  «19 — приседания пистолетиком» (в main-спеке было «на одной ноге»;
  код — `R.string.exercise_pistol_squats` + `ExerciseIcons.SQUATS_PISTOL`).
  Расхождение досталось из main-спеки, а MODIFIED-блок переписывается
  целиком — поэтому правится тем же диффом и задекларировано в proposal.

Синхронизация main-спеки выполняется `openspec archive` на шаге
архивации (tasks.md 4.3 — archive обновляет main-спеки); из задач
остаётся только `openspec validate`.

## Risks / Trade-offs

- **Dev-устройства с файлом БД старой версии (v1/v2) после обновления
  упадут с `IllegalStateException` при старте** → pre-release,
  пользователей нет; лечение — переустановка или «Очистить данные».
  `VERSION_CODE` bump (Decision 4) обеспечивает установку поверх.
- **Любое будущее изменение схемы без миграции теперь крашит приложение
  при старте** → это и есть цель (fail fast); следующая задача (v3→v4,
  `workouts`) обязана зарегистрировать `Migration` в `addMigrations(...)`
  — контракт зафиксирован спекой.
- **Тест с raw-файлом на production-имени может задеть данные приложения
  на устройстве** → как в архивной задаче: `deleteDatabase` перед
  созданием фикстуры и в `tearDown`.

## Migration Plan

Для БД на текущей версии 3 изменение обратно совместимо: версии
совпадают, путь миграции не выполняется, поведение не меняется. Откат —
revert `WorkoutDatabase.kt` и теста; `VERSION_CODE` откатывается
синхронно. Спека синхронизируется после зелёных прогонов.

## Open Questions

Нет.
