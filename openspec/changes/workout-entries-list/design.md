# Design: workout-entries-list

## Context

См. `proposal.md` — Why/What. Отправные точки: записи хранят `exerciseId`
без названия (`ExerciseEntry`), `ExerciseEntryDao` не имеет реактивных
запросов, `MainScreen` — пустой Scaffold. Паттерны UI
(длинное нажатие → контекстное меню в точке нажатия → алерт
подтверждения; заглушка пустого состояния; строка списка с
названием слева и значением справа) — устоявшаяся практика
проекта, согласованы с пользователем.

## Goals / Non-Goals

- Goals: список записей на главном, формат значения, удаление через
  long-press меню + алерт, пустое состояние, реактивное чтение из БД.
- Non-Goals: поиск, фильтрация, редактирование записи, группировка по
  датам, отображение веса (`weightKg`) в строке, свайпы. Форматирование
  длительности в «мин/сек» в строке записи — вне рамок: в строке
  только число (  как в повторы: «Планка» слева, «20» справа).
  Отдельный Error-статус UI не вводится — сознательное отступление
  от конвенции AGENTS.md (`Loading`/`Success`/`Error`): единственный
  источник — офлайн Room-Flow, который в штатной работе не завершается
  ошибкой; решение соответствует фактической практике проекта
  (`AppearanceUiState`, `EntryCreateUiState` — data-классы без
  sealed-иерархии). Появится реальный источник ошибок чтения —
  добавить `Error` по конвенции AGENTS.md.

## Decisions

### Decision 1: название упражнения — JOIN в DAO, не combine в ViewModel

Запись не содержит название — только `exerciseId`. Два пути: JOIN
в SQL-запросе с projection-классом или `combine(exercises(), entries())`
в ViewModel.

Выбран **JOIN**: `@Query("SELECT e.id, x.name AS exerciseName, … FROM
exercise_entries e JOIN exercises x ON x.id = e.exercise_id ORDER BY
e.date DESC")` → data class `EntryListItemEntity` (projection, не @Entity,
схема не меняется — миграция не нужна). Одна точка сортировки и связки,
ViewModel не тянет лишний Flow каталога. Alternative (combine) — больше
кода и риск рассинхрона сортировок; отвергнут.

### Decision 2: проекция списка — отдельный доменный тип `EntryListItem`

DAO возвращает projection; маппинг — extension
`EntryListItemEntity.toListItem()` в `data/db/Mappers.kt` (по образцу
существующих `toDomain()`), репозиторий вызывает его в `entries()`;
доменный тип — `domain/model/
EntryListItem` (id, exerciseName, sets, reps?, durationSec?, date).
UI-модель не таскает лишние поля записи (weightKg, comment, createdAt) —
строке списка они не нужны. `WorkoutDataRepository.entries():
Flow<List<EntryListItem>>` — по аналогии с `exercises(query)`.

### Decision 3: форматтер значения — чистая функция с разделителем-параметром

`formatEntryValue(sets, value: Int, separator)` в
`ui/screens/main/`: логика одна для повторов и длительности —
`sets > 1 → "${sets}${sep}${value}"`, иначе `"$value"`. Значение
`value = reps ?: durationSec` вычисляет вызывающий код (composable
строки); при обоих `null` — фоллбек `0` (`reps ?: durationSec ?: 0`;
состояние недостижимо: обязательное значение гарантирует валидатор,
Decision 7 — отдельной ветки в форматтере нет). Разделитель — параметр:
ru « х », en « x »
(строковый ресурс), сама функция тестируется JVM-тестами без ресурсов.
Альтернатива (форматтер на строковых ресурсах) непроверяема на JVM.

### Decision 4: ViewModel главного экрана — `MainViewModel`

`StateFlow<MainUiState>` (sealed: Loading/Success; пустота вычисляется
из `entries.isEmpty()` на use site — см. Decision 6) из
`repository.entries()` через `stateIn(WhileSubscribed(5000))` — по
конвенции проекта. Удаление: `requestDelete(item)` выставляет
`pendingDeleteId` (для алерта), `confirmDelete()`/`cancelDelete()`;
после `deleteEntry(id)` список актуализируется сам реактивно (Flow),
ручного refresh не нужно.

### Decision 5: long-press меню и алерт подтверждения удаления

Строка: `Modifier.pointerInput { detectTapGestures(onLongPress =
{ offset → … }) }` (НЕ `combinedClickable` — `onTap` не нужен,
только long-press; кликов по строке нет, экрана деталей нет) +
`Modifier.onGloballyPositioned` (позиция строки в root через
`coordinates.positionInRoot()`); меню — один `DropdownMenu` на уровне
списка (НЕ внутри строки), `expanded = true` пока `contextMenuItem != null`.
Внутри строки нет собственного `DropdownMenu` — жест локальный, рендер
общий. Пункт один: «Удалить» (на главном экране MyWorkouts деталей
и редактирования пока нет — единственное доступное действие над
записью; при добавлении деталей/редактирования меню расширяется
по той же схеме). Алерт — `AlertDialog` с confirm-кнопкой в
`colorScheme.error` и текстом «Удалить запись?»/«Отмена».

**Позиция меню в точке касания.** Material3 1.4 `DropdownMenu`
использует стратегию `topToAnchorBottom` (`menu.top = anchor.bottom +
offset.y`); anchor — это Box, в котором лежат и `LazyColumn`, и
`DropdownMenu` (`EntryList`). Без компенсации `anchor.bottom ≈
screenHeight`, и меню уезжает за нижнюю границу, после чего M3
клампит его к середине экрана — баг «меню ниже точки касания».
Решение:
- `anchor-Box` сообщает свою позицию и высоту в root-координатах через
  `Modifier.onGloballyPositioned { coords → anchorOrigin =
  IntOffset(positionInRoot.x, positionInRoot.y); anchorHeight =
  coords.size.height }`. Хранится в двух `remember {
  mutableStateOf(...) }` (`IntOffset.Zero` и `0` соответственно) —
  `right`/`bottom` из исходного `IntRect` не нужны (menu использует
  только origin и высоту).
- touchPoint в root = `positionInRoot` строки + `localOffset` из
  `detectTapGestures` (row-local → root); это уже было.
- offset в dp:
  - `offset.x = (touchPoint.x − anchorOrigin.x).toDp()`
  - `offset.y = (touchPoint.y − anchorOrigin.y −
    anchorHeight).toDp()` (эквивалентно
    `touchPoint.y − (anchorOrigin.y + anchorHeight)`)
- `DropdownMenu` сам клампит offset в границах окна — ручной
  клампинг не пишется.

`offset.x` дополнительно вычитает `anchorOrigin.x` (для корректности
при горизонтальных insets от display cutouts в landscape — типично
`anchorOrigin.x == 0` в portrait). Жест приходит из строки внутри
`LazyColumn`, anchor — Box уровня списка, поэтому перевод
row-local → root → anchor-local обязателен.

**Выделение строки при показе меню.** Пока `contextMenuItem != null`,
соответствующая строка получает фон `colorScheme.surfaceVariant`
(вместо `colorScheme.surface`) — плавный переход через
`animateColorAsState`. Источник истины — уже существующее
`ContextMenuState.item`; параметр `isSelected` в строке =
`contextMenu?.item?.id == item.id`. Выделение снимается при любом
закрытии меню (выбор пункта, тап вне, системная отмена) —
`onDismissRequest` сбрасывает `contextMenu` в `null`. Покрытие —
задача 5.6 через семантическое свойство `SemanticsProperties.Selected`
(`Modifier.semantics(mergeDescendants = true) { selected = ... }` —
стандартный канал accessibility в Compose; `mergeDescendants = true`
нужен, чтобы `onNodeWithText` находил merged Row, а не отдельный
Text внутри).

**Тест-покрытие.** Задача 5.3 проверяет `performTouchInput.longClick()`
→ `onNodeWithText("Удалить").assertIsDisplayed()` (наличие меню
и пункта). Подсветка строки — задача 5.6: `SemanticsMatcher.expectValue(
SemanticsProperties.Selected, true|false)`. Позиция меню относительно
точки касания покрывается только ручной проверкой на эмуляторе (5.5,
5.8): `DropdownMenu`-offset не имеет семантики точной привязки в
Compose UI Test — проверка позиции требует `capture_ui_dump`/
uiautomator API (XML `<node ... bounds="[x,y][x,y]">` для popup-
контейнера) либо визуальной верификации по скриншоту.

### Decision 6: пустое состояние — заглушка вместо списка

При `Success` с пустым списком записей показывается `Box(fillMaxSize)`
с одним текстом «Добавьте первую запись», отцентрированным через
`Modifier.align(Alignment.Center)` (упрощённый паттерн проектного
шаблона — без второй строки-подзаголовка: она тут нечего добавляет).
Отдельный sealed-статус `Empty` не нужен — пустота
вычисляется из списка.

### Decision 7: обязательное значение по `measuredBy` — в EntryValidator

Решение пользователя: запись без указанных повторов/длительности
(по типу упражнения) сохранять нельзя. Отменяет прежнее правило
main-спеки `workout-data` («слой данных не навязывает соответствие
measuredBy») — дельта переписывает требования «Валидация полей записи»
и «Поддержка упражнений на время».

Реализация: `EntryValidator.validate(entry, measuredBy:
ExerciseMeasuredBy)` — для `REPS` повторы обязательны (`MISSING_REPS`),
для `TIME` длительность обязательна и ≥ 1 (`MISSING_DURATION`,
`INVALID_DURATION`); повторы и длительность не могут быть заданы
одновременно (`INCOMPATIBLE_REPS_AND_DURATION`) — запись содержит
только обязательное по `measuredBy` поле. Репозиторий в
`addEntry`/`updateEntry` перед валидацией грузит упражнение по
`exerciseId` (`ExerciseDao.getById`) и передаёт его `measuredBy`;
несуществующее упражнение → `Result.failure(IllegalStateException(
"Упражнение не найдено"))` — по образцу существующего пути удаления
упражнения, без нового кода в `EntryValidationError` (раньше
несуществующий id ловился только FK на insert). UI-путь уже
гарантирует поле (`EntryCreateUiState.canSave`) — правила закрывают
немодальные вызовы репозитория.

## Risks / Trade-offs

- [Меню у края экрана может выйти за границы] → `DropdownMenu`
  сам ограничивает offset границами окна (поведение M3); ручная
  клампинг-логика не пишется.
- [JOIN-запрос без теста на переименование/удаление упражнения] →
  удаление упражнения с записями уже запрещено FK-требованием
  `workout-data` («Удаление упражнения из каталога»), JOIN безопасен.
- [Деструктивный фоллбек БД] → схема не меняется (projection), риска
  пересоздания БД нет.
- [Ужесточение валидации меняет задокументированное поведение
  «не навязывает соответствие»] → дельта `workout-data` переписывает
  оба требования; существующие тесты сценария «не навязывает»
  переворачиваются в задачах 1.5–1.7.

## Migration Plan

Схема БД не меняется, новых зависимостей нет — выкатка атомарная.
Меняется поведение валидации (Decision 7): записей без обязательного
значения в БД нет и не было (UI не позволял), обратной совместимости
данных не требуется. Откат — revert коммита.

## Open Questions

(нет)
