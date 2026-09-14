# Design: exercises-archive

## Context

Каталог упражнений сейчас — реактивный список с long-press меню только для
кастомных упражнений (`edit-delete-exercise` Decision 4: «стандартные —
тишина, нет меню для удаления/редактирования»). Слой данных полностью
готов (`ExerciseDao` + `RoomWorkoutDataRepository`), но в нём нет поля
архивации. `EntryCreateScreen` использует
`repository.exercises(query = "")` напрямую (без фильтрации) для
выпадающего списка выбора упражнения. Навигация — Navigation Compose
через `RootScreen` с `BottomTabs` (Main / Exercises / More) и
non-tab-экранами (Appearance, AppData, EntryCreate, ExerciseCreate).
Экраны без нижней навигации — `Screen.Appearance` (паттерн:
`currentRoute NOT in tabs.map { it.route }`, контракт в
`tab-navigation/spec.md` и архивный
`2026-08-26-feature-appearance-screen/design.md`). Локализация ru/en уже
подключена (`locales_config.xml`), все строки живут в
`res/values/strings.xml` (ru, default) и `res/values-en/strings.xml` (en).
БД — Room с `fallbackToDestructiveMigration`, приложение ещё не в
релизе, добавление колонки с default value не требует миграции.

Этот change добавляет «мягкую» архивацию: поле `isArchived` на
`Exercise`/`ExerciseEntity`, фильтрация активных упражнений в
`exercises(query)`, новые методы репозитория, расширенное long-press
меню, новый экран архива, реактивная кнопка в `TopAppBar` каталога.

## Goals / Non-Goals

**Goals:**

- «Мягкое» скрытие упражнения из каталога без потери данных и с
  возможностью восстановления. Покрыть и кастомные, и стандартные
  упражнения.
- Реактивный список архива и счётчик архивных для UI без ручного
  refresh.
- Минимальные изменения: переиспользовать `ExerciseListItemRow`,
  контракты архивного `edit-delete-exercise` (long-press меню с
  аналогичной структурой), `AlertDialog`-паттерн из
  `EntryDeleteDialog`/`ExerciseDeleteDialog` (с другим цветом
  confirm-кнопки).
- Без миграции БД: `defaultValue = "0"` на `@ColumnInfo`,
  `fallbackToDestructiveMigration` уже в `WorkoutDatabase` —
  потеря локальных данных до релиза допустима.

**Non-Goals:**

- Фильтры по категории или другим атрибутам (только полный архив —
  `isArchived` boolean).
- Массовые операции: пакетная архивация/восстановление, фильтры в
  архивном экране.
- Отдельных доработок экспорта/импорта не требуется —
  `getAllExercises()` уже возвращает все упражнения включая архивные,
  `importData` сохраняет `isArchived` каждого упражнения как есть
  (норматив в `workout-data/spec.md`, требования «Снимки каталога и
  записей» / «Массовый импорт данных» / «Импорт сохраняет
  `isArchived`»).
- Изменения в `MainScreen`, `ExerciseCreateScreen` (только
  prefill/edit-режим, без архивации формы), `AppDataViewModel`
  (фильтрация в `exercises` приходит автоматически).
- Изменение схемы БД для уже выпущенной версии (приложение не в
  релизе, default value исключает миграцию; с первым релизом —
  миграции).
- Hard-delete с подтверждением через архив (архив — мягкая операция,
  физическое удаление остаётся через «Удалить» в long-press).

## Decisions

### Decision 1: `isArchived` — default value на `@ColumnInfo`, `version` поднимается до `2`

**Выбор:** `ExerciseEntity` получает `@ColumnInfo(name = "is_archived",
defaultValue = "0") val isArchived: Boolean = false`. В `Exercise`
(data class domain) — `val isArchived: Boolean = false`. `Mappers`
зеркально пробрасывают поле в обе стороны. `version` БД поднимается с
`1` до `2` (Room при `exportSchema = true` валидирует хэш схемы на
открытии — неизменный `version` при изменённой схеме даст
`IllegalStateException("Room cannot verify the data integrity...")`;
`fallbackToDestructiveMigration` покрывает только путь
"версия на устройстве < версии в коде", но не "та же версия, другой
хэш"). На случай upgrade без явной миграции уже включён
`fallbackToDestructiveMigration` в `WorkoutDatabase.create` — БД
пересоздаётся с новым prepopulate.

**Обоснование:** Приложение не в релизе — потеря локальных данных
пользователя (записей и кастомных упражнений) при деструктивном
фоллбеке допустима. `version = 2` — минимально необходимый bump для
обхода проверки хэша схемы; явная `Migration(1, 2)` для
pre-релизного состояния — лишний overhead.

**Альтернатива:** явная `Migration(1, 2) { db ->
db.execSQL("ALTER TABLE exercises ADD COLUMN is_archived INTEGER
NOT NULL DEFAULT 0") }` — отклонена для текущего состояния проекта:
overhead без выгоды, `fallbackToDestructiveMigration` уже решает задачу
без ручного SQL.

### Decision 2: Реактивный счётчик архивных через `archivedCount()
Flow<Int>`

**Выбор:** новый `ExerciseDao.observeArchivedCount(): Flow<Int>` с
`@Query("SELECT COUNT(*) FROM exercises WHERE is_archived = 1")`,
проброшенный в репозиторий как `archivedCount(): Flow<Int>`. Подписка в
`ExercisesViewModel` через `repository.archivedCount().stateIn(
viewModelScope, WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), 0)` —
значение попадает в `ExercisesUiState.Success.archivedCount: Int` и
используется UI для условной видимости кнопки в `TopAppBar`.

**Обоснование:** Реактивность обновляет кнопку сразу после
архивации/восстановления — пользователь видит появление/исчезновение
кнопки без ручного refresh. `WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS)`
(паттерн `MainViewModel`) — подписка живёт 5 секунд после
отписки, повторные открытия экрана не дёргают SQL. Запрос лёгкий
(`COUNT(*) WHERE is_archived = 1`): индекса на `is_archived` нет, но
таблица `exercises` пока маленькая — скан дешёвый; явный
`@Index(value = ["is_archived"])` добавляется, если профайлер
покажет рост.

**Альтернативы:**
- разовый `suspend fun archivedCount(): Int` + ручной refresh в VM —
  отклонена: теряется реактивность (пользователь должен выйти/зайти
  на экран, чтобы увидеть кнопку).
- использовать существующий
  `repository.exerciseIdsWithLinkedEntries()` как индикатор «есть
  архивные» — отклонена: семантически другая функция, имя вводит
  в заблуждение.

### Decision 3: Один метод репозитория `setArchived(id, isArchived)`
для обоих направлений

**Выбор:** `suspend fun setArchived(id: Long, isArchived: Boolean):
Result<Unit>` — реализация через `runCatching { val entity =
exerciseDao.getById(id) ?: throw IllegalStateException("Упражнение не
найдено"); exerciseDao.update(entity.copy(isArchived = isArchived)) }`.
`getById` уже возвращает `ExerciseEntity` (DAO), отдельный
`?.toEntity()`-маппинг не нужен. Семантика failure: отсутствующий
id → `Result.failure(IllegalStateException("Упражнение не
найдено"))` (race при архивации — редкий случай в офлайн-приложении);
прочие ошибки — generic `Result.failure`.

**Обоснование:** Один метод вместо двух (`archive(id)` / `restore(id)`)
— проще API и тесты. `setArchived(id, true)` для архивации и
`setArchived(id, false)` для восстановления. Параметр `isArchived:
Boolean` явно говорит о направлении, имя метода — нейтральное
«установить состояние». Race «между сбором списка и подтверждением
алерта» уже обрабатывается аналогично `deleteExercise` —
`getById(id) == null` → failure.

**Альтернативы:** `archive(id)` и `restore(id)` — отклонены: дублируют
логику, удваивают количество методов репозитория.

### Decision 4: Long-press меню для стандартных расширяется — есть
пункт «Архивировать»

**Выбор:** на `isCustom == false` long-press открывает меню с
единственным пунктом «Архивировать» (`Icons.Filled.Archive`).
Контракт архивного `edit-delete-exercise` Decision 4 («для стандартных
нет меню») был про «нет удаления и редактирования» — для архивации
делаем исключение осознанно. Удаление и редактирование стандартных
остаются запрещёнными (это не отменяется).

**Обоснование:** Стандартные упражнения — главный кейс для архивации:
пользователь хочет скрыть «Бег» или «Скакалку», если не использует, но
не может ни отредактировать, ни удалить. Расширение меню стандартных
с 0 пунктов до 1 («Архивировать») — минимальное изменение UX.
Пункт «Изменить» для стандартных остаётся скрытым (логика репозитория
`updateExercise` для стандартных тоже запрещена — паттерн не
меняется).

**Альтернативы:**
- запретить архивацию стандартных через guard в репозитории —
  отклонена: теряется главный кейс, ради которого change делается.
- помечать пункт «Архивировать» как disabled — отклонена:
  информативнее показать работающее действие с подтверждением
  (алерт), чем disabled-кнопку.

### Decision 5: Алерт архивации — confirm обычным цветом

**Выбор:** `ExerciseArchiveDialog` (внутренний composable в
`ExercisesScreen.kt`) — `AlertDialog` с заголовком
`exercise_archive_dialog_title`, текстом
`exercise_archive_dialog_text` (с `%1$s` подстановкой имени),
кнопкой отмены `exercise_archive_dialog_cancel`, кнопкой
подтверждения `exercise_archive_dialog_confirm` в дефолтном цвете
(`ButtonDefaults.textButtonColors()` без `colorScheme.error`).

**Обоснование:** Архивация — не необратимая операция (есть
восстановление через архивный экран). `colorScheme.error` в проекте
используется только для подтверждения необратимых действий
(`ExerciseDeleteDialog`, `EntryDeleteDialog`,
`AppDataScreen.deleteAllDialog`). Для архивации обычный цвет — корректная
семантика. Двухшаговое подтверждение (long-press → алерт) уже защищает
от случайного тапа.

**Альтернативы:** использовать `colorScheme.error` как в
`ExerciseDeleteDialog` — отклонена: вводит пользователя в заблуждение
(«удалил?»), действие обратимо.

### Decision 6: Сортировка не меняется — контракт зафиксирован

**Выбор:** `archivedExercises()` использует тот же `ORDER BY
is_custom DESC, created_at DESC, name COLLATE NOCASE`, что и
`observeExercises`. Меняется только предикат: `WHERE is_archived =
1` вместо `WHERE name LIKE '%' || :query || '%' COLLATE NOCASE AND
is_archived = 0` (для активного каталога).

**Обоснование:** Контракт сортировки зафиксирован в
`exercises-screen/spec.md` («Сортировка каталога — кастомные сверху,
новые первые») — единый источник истины для обоих экранов. SQL
`is_custom` и `created_at` остаются значимыми для архива
(пользователь хочет видеть свои кастомные сверху, новые сверху).

**Альтернативы:** сортировать архив только по `created_at DESC`
(«последний заархивированный сверху») — отклонена: ломает единый
контракт, в спек-е явно зафиксирован порядок
`is_custom DESC, created_at DESC`.

### Decision 7: `ExercisesUiState.Success` расширяется, не ветвится

**Выбор:** `Success` получает `pendingArchiveId: Long? = null` и
`archivedCount: Int = 0`. Не вводится отдельный sealed-вариант —
расширение копией data class по образцу добавления `pendingDeleteId`
и `deleteError` (edit-delete-exercise).

**Обоснование:** Паттерн уже прецедент: `Success` хранит все флаги
состояния как nullable-поля, UI маппит на видимость алерта/кнопки.
Отдельный sealed-вариант для «архивация в процессе» — overengineering:
`pendingArchiveId` — тот же паттерн, что `pendingDeleteId`.

### Decision 8: `ArchivedExercisesScreen` не имеет `archivedCount`
подписки

**Выбор:** VM экрана архива подписывается только на
`repository.archivedExercises()`. Счётчик `archivedCount` нужен
только каталогу (для условной кнопки в `TopAppBar`); на самом экране
архива длина списка = `exercises.size`, отдельный `Flow<Int>` не нужен.

**Обоснование:** Избегаем дублирующих подписок. Длина списка архива —
это и есть «количество архивных»; реактивность списка покрывает
обновление счётчика.

**Альтернатива:** подписаться на `archivedCount()` для отдельной
логики (например, заглушка «Архив пуст» через отдельный флаг) —
отклонена: `exercises.isEmpty()` решает задачу напрямую.

### Decision 9: Bottom-bar скрыт на `ArchivedExercises` — контракт
по образцу `Appearance`

**Выбор:** `Screen.ArchivedExercises : Screen(route =
"archived_exercises")` — НЕ в `BottomTabs`. `shouldShowBottomBar` в
`RootScreen` остаётся `currentRoute in tabs.map { it.route }` —
`archived_exercises` не в списке → бар скрыт автоматически. Никаких
правок в `RootScreen` для скрытия бара не нужно.

**Обоснование:** Контракт `non-tab-screen-navigation/spec.md` (Scenario
«Bottom navigation скрыта на ExerciseCreate» + архивный
`2026-08-26-feature-appearance-screen/design.md` Decision: «не-tab
экраны скрываются условием `currentRoute in tabs`»). Реализация уже
есть, новый экран попадает под тот же контракт без дополнительной
логики.

## Risks / Trade-offs

- [5 streams в `combine` — на пределе overload]
  `ExercisesViewModel.uiState` собирает `combine(repository.exercises(""),
  pendingDeleteId, deleteError, pendingArchiveId, archivedCount)` —
  ровно 5 потоков (`combine` имеет типизированные overload до 5
  аргументов, дальше — только vararg `combine(vararg flows: Flow<*>)
  с Array<*>`). Если добавится шестой поток (например, флаг
  «синхронизация с сервером» или ещё один pending-id), придётся
  переписывать на vararg-форму или каскад `combine`. На текущем scope
  5 потоков достаточно; при будущем расширении — пересмотреть структуру
  `ExercisesUiState.Success` (вынести редко-меняющиеся потоки в
  отдельный `derivedStateOf` внутри UI или разбить на два `combine`).
- [Race «упражнение вернули из архива между выбором в
  `EntryCreate` и сохранением»] → Mitigation: `EntryCreateViewModel`
  хранит `selectedExercise` как поле формы, не пересчитывает из
  каталога при сохранении. Если выбранное упражнение оказалось в
  архиве, оно остаётся в `uiState.exercises` (как поле
  `selectedExercise`) и подставляется в выпадающий список как
  текущий выбор через `derivedState` (функция `derivedState` в
  `EntryCreateViewModel`, паттерн `selected = state.selectedExercise
  ?: original?.let { orig -> exercises.firstOrNull { it.id ==
  orig.exerciseId } }`).
  Валидация при сохранении — `validateWithExercise` грузит упражнение
  по id (без фильтра архива, через `exerciseDao.getById(id)`),
  поэтому архивная запись пройдёт валидацию и сохранится (поведение
  документировано в требовании «Упражнение в архиве между открытием
  и сохранением» в `workout-entry-screen/spec.md`).
- [Иконка `Archive` vs `Unarchive` — разные Material-иконки] →
  Mitigation: `Icons.Filled.Archive` для архивации,
  `Icons.Filled.Unarchive` для восстановления (оба доступны в
  `androidx.compose.material.icons.filled`). Семантика очевидна:
  «коробка с крышкой вниз» — в архив, «коробка с крышкой вверх» —
  из архива.
- [Поле `isArchived` в каждой строке списка — лишний параметр для
  рендера] → Mitigation: `ExerciseListItemRow` принимает только
  `Exercise` + `isSelected`; `isArchived` не нужен в строке
  (нет специального визуала для архивных строк — они уже
  отфильтрованы с активного каталога и видны только на экране
  архива без отличий).
- [БД растёт неограниченно при архивации без очистки] → Mitigation:
  архив — пользовательская функция; «Удалить» из архива не
  предусмотрен (только возврат через «Вернуть» — это и есть ядро
  архивного экрана); hard-delete остаётся в long-press меню
  каталога (для кастомных — edit-delete-exercise).
- [Изменение схемы БД] → Mitigation: см. Decision 1. С первым
  релизом деструктивный фоллбек убирается; очередные изменения
  схемы — через явные `Migration` от актуальной версии.
- [Логика фильтрации в SQL `exercises()` ломает уже работающие
  потребители] → Mitigation: фильтрация `WHERE is_archived = 0`
  добавлена в существующий `observeExercises` запрос; все
  потребители (`ExercisesViewModel`, `EntryCreateViewModel`,
  `AppDataViewModel`) автоматически видят только не-архивные.
  Проверяется существующими androidTest-ами (`ExercisesScreenTest`,
  `EntryCreateScreenTest`) — при добавлении `is_archived = 0`
  поведение не меняется для существующих данных (где
  `is_archived = 0` по default).

## Migration Plan

Изменение схемы БД и обоснование выбора destructive fallback — см.
Decision 1 (канонический источник механики и trade-offs). Кратко:
`version` поднимается `1 → 2` уже в этом change, новый `is_archived`
имеет `@ColumnInfo(defaultValue = "0")`, `fallbackToDestructiveMigration`
срабатывает на upgrade и пересоздаёт БД с новым prepopulate. Потеря
локальных данных пользователя до первого релиза допустима.

С первым релизом `fallbackToDestructiveMigration` убирается;
очередные изменения схемы — через явные `Migration` от актуальной
версии (2 → 3 и далее).

Rollback — revert коммитов: новое поле `isArchived` удаляется,
новые методы репозитория и UI откатываются, `version`
возвращается к `1`. Существующий контракт `exercises(query)` после
rollback содержит `WHERE name LIKE ...` (без `is_archived = 0`) —
поведение возвращается.
