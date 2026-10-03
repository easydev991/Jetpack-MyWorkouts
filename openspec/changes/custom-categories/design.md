# Design

## Context

Категория сегодня — enum `ExerciseCategory` (5 значений с `@StringRes`),
хранится enum-именем в `ExerciseEntity.category` (String в Room), цвета —
статический `when` в `ui/theme/Color.kt`, статистика группирует по enum,
импорт бэкапа валидирует категорию `requiredEnum` и отвергает весь файл.
Room v3, миграций ещё не было (правило: первая рукописная `Migration` —
в паре с `MigrationTest` + `room-testing`). Экран упражнений — список + FAB
+ long-press меню; архив упражнений — отдельный экран; паттерн табов —
`OverviewScreen` (`SecondaryTabRow` + `when`, таб-бар при ≥ 2 видимых).

## Goals / Non-Goals

**Goals:**

- Категория — строка в данных: таблица `categories`, домен-модель `Category`,
  ссылки упражнений по имени; enum `ExerciseCategory` удаляется.
- Переиспользование существующих паттернов: `ExerciseCreate` (экран
  категории), `DeleteExerciseError` (ошибки удаления), `ArchiveSuppressSlot`
  (подавление алерта архивации), вкладки/архив — по канонам `OverviewScreen`
  и архивного экрана.
- Обратно совместимый бэкап v1 (секция `categories[]` — расширение).

**Non-Goals:**

- Ручной выбор/изменение цвета (только хранение `colorArgb` как задел).
- Изменение/удаление/переименование стандартных категорий.
- FK-связь упражнений с категориями (см. Decision 1).
- Изменение поведения архивации упражнений и записей.

## Decisions

### Decision 1: Упражнения ссылаются на категорию по имени, без FK

`ExerciseEntity.category` остаётся `TEXT` с именем категории; новая таблица
`categories` не связана FK. Переименование — одна транзакция:
`UPDATE categories` + `UPDATE exercises SET category = :new WHERE category = :old`.

*Почему не FK (`category_id`)*: бэкап v1 уже переносит категорию именем
(`exercises[].category` → `categories[].name`) — FK потребовал бы
backfill id в миграции и изменения формата; ссылка по имени сохраняет
текущую модель `Exercise` и миграцию тривиальной (CREATE TABLE + INSERT).
Цена — целостность поддерживается приложением (pre-check удаления,
транзакционное переименование), что уже принято для связи «запись →
упражнение по имени» в импорте.

### Decision 2: Константы стандартных категорий — один источник

Новый файл `data/db/StandardCategories.kt`: список пяти категорий
(`name = "PUSH"/"PULL"/"LEGS"/"CORE"/"CARDIO"`, `colorArgb` — текущие
значения из `Color.kt`, `isCustom = false`). Его используют: prepopulate
(callback создания БД), `deleteAllData`, `Migration(3, 4)` (SQL-INSERT
значений), `MigrationTest` (asserts). Отображение: file-level хелпер
`standardCategoryLabelResId(name: String): Int?` (`when` по пяти
ключам, `else -> null` → существующие `exercise_category_*`), живёт в
`ui/util` (создаётся задачей 1.1 — нужен контракт-тесту label-ресурсов;
потребители — 3.4/5.4/8.3); `null` = кастомная —
показываем `name`. Enum `ExerciseCategory` и `when` в `Color.kt` удаляются;
`Exercise.category: ExerciseCategory` → `category: String` (имя).

`ExerciseCategoryTest` перерабатывается в контракт-тест набора стандартных
ключей + соответствия labelResId (те же инварианты, источник —
`StandardCategories`).

### Decision 3: Генерация цвета — чистая функция, отступ hue 15°

Private-функция репозитория (один потребитель `addCategory`; отдельный
use-case не вводится): вход —
`List<Int>` существующих `colorArgb`; существующие конвертируются в
hue через `android.graphics.Color.colorToHSV` (единая модель HSV во всех
расчётах); S/V фиксированные (например, S=0.55, V=0.55 — визуально
согласовано с текущей палитрой). Минимальный отступ hue — константа
`MIN_HUE_OFFSET = 15°`. Алгоритм: найти наибольший зазор hue-кольца
`gap`; если `gap ≥ 2·MIN_HUE_OFFSET` (30°), новый hue выбирается
случайно внутри центрального участка зазора —
`gapStart + 15° + random·(gap − 30°)` — так что до обоих соседних hue
остаётся ≥ 15°; если все зазоры < 30°, берётся центр наибольшего
зазора (отступ может нарушаться — повторы допустимы, зафиксировано
в спеке). Гарантия для ≤ 12 категорий: максимальный зазор ≥ среднего
`360/n` ≥ 30° при `n ≤ 12` — условие `gap ≥ 30°` выполнимо. Результат —
ARGB Int. Чистая функция → JVM-тест без моков (функция принимает
`random: Random` для детерминированных тестов).

### Decision 4: Ошибки удаления — по образцу `DeleteExerciseError`

Репозиторийный метод `deleteCategory(id)` внутри транзакции: standard →
`StandardCategoryDeletionException`; есть упражнения (активные/архивные) →
`CategoryInUseDeletionException`; успех → удаление. VM маппит в sealed
`DeleteCategoryError { UsedByExercises, StandardCategory, Other }` —
структура и снекбар-маппинг зеркалят упражнения. Реактивный пре-чек —
`categoryNamesWithLinkedExercises(): Flow<Set<String>>`
(`SELECT DISTINCT category FROM exercises`), VM держит `stateIn`-подписку и
открывает алерт только для свободного имени (safety net — исключение из
репозитория после подтверждения).

### Decision 5: Миграция v3 → v4, `room-testing`, тесты

- `WorkoutDatabase` version 4; `Migration(3, 4)`: `CREATE TABLE categories`
  (+ unique-индекс `name`, `created_at`) и INSERT пяти стандартных из
  `StandardCategories` (единый `created_at` → стабильная сортировка по
  `name COLLATE NOCASE`). Рукописная, а не `@AutoMigration`: требуется
  `INSERT` данных стандартных категорий — AutoMigration переносит только
  схему.
- `androidx.room:room-testing` — в `androidTest`-зависимости;
  `MigrationTest` (androidTest) через `MigrationTestHelper` (create v3
  с данными → migrate → verify). Мок-тесты SQL миграций запрещены (AGENTS).
- Экспортированный `app/schemas/…4.json` коммитится вместе с миграцией.
- DAO-контракты (unique-индекс, сортировка, счётчики) — `CategoryDaoTest`
  в androidTest по образцу `ExerciseDaoTest`; логика репозитория
  (маппинг исключений, порядок вызовов) — JVM c MockK/fake DAO.

### Decision 6: Табы и UiState экрана упражнений

`ExercisesTab { EXERCISES, CATEGORIES }`; `selectedTab:
MutableStateFlow<ExercisesTab>` в `ExercisesViewModel` (не персистится).
`combine` уже занят 5 потоками — новые (categories,
archivedCategoriesCount, pendingCategory*, selectedTab) группируются во
второй `combine` или vararg-вариант (детали — на усмотрение реализации,
главное один `stateIn`). `ExercisesScreen` получает колбэки
`onAddCategoryClick` / `onEditCategory` (навигация на
`Screen.CategoryCreate`) по паттерну `onAddExerciseClick`. Строка
категории — новый `CategoryListItemRow` у экрана упражнений (конвенция
«строки живут у своих экранов»), переиспользуется архивом.

### Decision 7: Экран категории — уменьшенная копия `ExerciseCreate`

`CategoryCreateScreen` (обёртка) + `CategoryCreateScreenContent` +
`CategoryCreateScreenState` + `CategoryCreateViewModel` (фабрика — в
`RootScreen`, `categoryId = NO_CATEGORY_ID` для create). Отличия от
`ExerciseCreate`: одно поле, нет иконок/measuredBy; валидация дубликата
имени (снимок `getAllCategories`/Flow, `isDuplicateName` в UiState);
edit-режим → `renameCategory` (транзакция Decision 1); BackHandler —
канон `exercise-create-screen` (строки `entry_confirm_close_*`
переиспользуются).

### Decision 8: DataStore-флаг подавления алерта архивации категории

`dont_show_category_archive_dialog: Boolean = false` в
`AppSettingsDataStore` — отдельный ключ, API зеркалит
`dontShowArchiveDialog`. `ExercisesViewModel.requestCategoryArchive` читает
флаг перед алертом (паттерн `requestArchive`). UI алерта — тот же
`ExerciseConfirmDialog` + `ArchiveSuppressSlot`, тексты категории.

### Decision 9: Бэкап — категории в v1, резолв упражнения по имени

Канон версии формата (устраняет дрейф спека/код): экспорт пишет
`"version": 1`; `settings` и `categories` — обратно совместимые расширения
без подъёма версии (старые релизы читают новый файл: неизвестные поля
игнорируются). Код пишет `1`, импорт принимает `1` и `2`;
существующие файлы v2 остаются читаемыми. Сужать условие импорта до
только `1` не нужно и нельзя: файлы с меткой `2` создаёт код текущего
сторового релиза — приём `2` сохраняется и после change.

Экспорт: `getAllCategories()` → массив `categories[]` (`name`, `colorArgb`,
`isArchived`; `isCustom` не пишется). Блок `settings` пишется в новые
бэкапы всегда (канон дельты `app-data-management`); старые файлы без него
остаются валидными при импорте. Импорт: parse `categories[]`
(отсутствие массива — ok), дедуп по `name.trim().lowercase()` против БД и
внутри файла; отсутствующие вставляются (`isCustom = true`, цвет/архив из
файла); односторонняя архивация существующих — по правилу упражнений;
затем упражнения: категория резолвится по (БД ∪ файл), иначе упражнение
(и его записи) пропускается; `requiredEnum` для категории заменяется на
`requiredString` (задача 4.4, после перевода `Exercise.category` на
`String` в 3.2 — раньше правка не компилируется). Репозиторийный `importData` расширен до
`importData(categories, exercises, entries)` — одна транзакция.

### Decision 10: Статистика и цвета — включая drill-down

`EntryWithExercise.category: ExerciseCategory` → `String` (имя).
`computeCategoryStats` группирует по имени (математика не меняется):
`CategoryTotal.category` → имя-строка, `onCategoryClick` принимает имя
(`String?` — `null` — выход из drill-down). `OverviewViewModel`
дополнительно подписывается на объединение `categories()` и
`archivedCategories()` — архивная категория остаётся в статистике (её
упражнения не трогаются), цвет нужен и ей — и резолвит `colorArgb` +
отображаемое имя для легенды/доната И drill-down — оттенки drill-down
считаются от `colorArgb` выбранной категории (после удаления
`Color.kt`-`when` — Decision 2 — это единственный источник цвета
категории); unknown-имя — fallback:
имя как есть, нейтральный цвет темы. UI
красит через `Color(category.colorArgb)`.

### Decision 11: Аналитика

`AppScreen.CategoryCreateScreen`; `UserActionType` — создание/сохранение/
удаление/архивация/восстановление категории, выбор вкладки категорий;
`AppErrorOperation.CATEGORY_DELETE`. Минимальные правки по существующей
модели (`analytics/`), провайдеры не меняются.

## Risks / Trade-offs

- [Миграция на живых установках] → рукописная `Migration(3, 4)` +
  `MigrationTestHelper`-тест (create v3 с данными → verify v4), коммит
  schema JSON; деструктивный фоллбек запрещён каноном `workout-data`.
- [Рассинхрон имени упражнения и категории без FK] → переименование только
  в транзакции; удаление — пре-чек + safety net; экспорт/импорт толерантны
  (unknown-категория → пропуск упражнения, не файла).
- [Combine-арность в `ExercisesViewModel`] → группировка потоков; при
  росте сложности — не рефакторить превентивно, следить за читаемостью.
- [Hue-кольцо исчерпано при >12 категорий] → повторы цветов допустимы
  (зафиксировано в спеке `category-data`), ручная настройка цвета —
  задел на будущее (Non-Goal).
- [Detekt maxIssues: 5] → новые предупреждения гасятся в ходе задач;
  `make format && make lint && make test` — зелёные перед каждым коммитом
  задач (правило репо).
- [Старые бэкапы без `categories[]`] → импорт резолвит категории по БД;
  упражнение с отсутствующей категорией пропускается — поведение
  зафиксировано в спеке `app-data-management`.

## Migration Plan

1. Bump `WorkoutDatabase.version` 3 → 4, добавить `CategoryEntity`/
   `CategoryDao`, `MIGRATION_3_4` в `addMigrations(...)`.
2. Экспортировать schema JSON v4 (`room.schemaLocation` уже настроен),
   закоммитить вместе с миграцией.
3. `androidTestImplementation(androidx.room:room-testing)` +
  `MigrationTest` — в том же коммите, что и миграция (правило AGENTS).
4. Откат не предусмотрен (канон репо: миграции без деструктивного фоллбека);
  rollback = возврат предыдущего коммита до релиза.
