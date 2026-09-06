## Context

Главный экран вкладки «Тренировки» уже работает на реактивном потоке
`WorkoutDataRepository.entries()` (`MainViewModel.uiState`) и рендерит
плоский список `EntryListItem` через `LazyColumn` (`MainScreen` →
`EntryListItems`). Список уже отсортирован по дате убыванием на стороне
SQL. Доменная модель и слой данных сознательно остаются без изменений
(см. `proposal.md` — раздел «Impact / Что не меняется»).

Цель изменения — добавить визуальные секции по календарной дате и
сохранить существующее поведение (long-press меню, алерт удаления,
локализация ru/en, превью, edge-to-edge insets). Делаем это только
через уже подключённые Compose Foundation и `java.time`.

## Goals / Non-Goals

**Goals:**

- Разделить список на секции по `LocalDate` в системной зоне устройства.
- Сохранить порядок секций по убыванию даты и порядок записей внутри
  секции (готовый `entries` уже отсортирован убыванием по дате).
- Заголовки «Сегодня» / «Вчера» / дата в `dd.MM.yyyy`, локализация ru/en.
- Не сломать существующее поведение: long-press, удаление,
  `pendingDeleteId`-логика, превью, инсеты.
- Покрыть группировку и форматтер заголовков быстрыми JVM-тестами;
  регрессию long-press / удаления / sticky-header — androidTest.

**Non-Goals** (проектные ограничения — в `proposal.md`, здесь —
только технические решения):

- Не вводим use-case для группировки: функция чистая и достаточно мелкая,
  чтобы жить рядом с экраном (`ui/screens/main/`).
- Не используем сторонние библиотеки для секций —
  `LazyListScope.stickyHeader` из Compose Foundation достаточно.
- Не вводим новые состояния `MainUiState`: новое поле — производное
  от уже существующего `entries`, sealed-иерархия остаётся прежней.

## Decisions

### Decision 1. Секции считаются в ViewModel, а не в composable

Группировка `List<EntryListItem>` → `List<DateSection>` выполняется в
`MainViewModel.uiState` через `combine(pendingDeleteId, entries)`,
а не в composable-функции `EntryListItems`.

**Контракт группировки:** внутри каждой `DateSection` записи идут
по убыванию `date` (epoch millis); гарантия держится явной
сортировкой входа по `date` убыванием внутри `groupEntriesByDate`
перед `groupBy`. Контракт не зависит от порядка входного списка
и не накладывает требований на SQL — функция самодостаточна
и переиспользуема без оговорок.

**Почему:**

- `combine` уже паттерн для производных полей `MainUiState.Success`
  (`pendingDeleteId` подмешивается тем же способом). Добавляем
  `sections` третьим производным значением — никакой новой инфраструктуры.
- Сегодня (`LocalDate.now()`) фиксируется в момент эмиссии, а не в
  момент рекомпозиции. Без этого sticky-header заголовки могли бы
  переключаться с «Сегодня» на «Вчера» при долгом открытии экрана.
- Composables остаются чистой функцией состояния: один и тот же
  `MainUiState.Success` рендерится одинаково в проде и в превью.
- Тестируемость: pure-функция `groupEntriesByDate` покрывается
  JVM-тестами без Compose.

**Альтернативы:**

- Группировать внутри `EntryListItems` через `derivedStateOf`. Отказ:
  `LocalDate.now()` вызывался бы при каждой рекомпозиции/derivedStateOf
  и зависел бы от часов реального времени — sticky-заголовок мог бы
  «переехать» за полночь без смены состояния.
- Сделать use-case `GetEntriesByDateSectionUseCase`. Отказ: ради одной
  pure-функции добавлять слой use-case преждевременно (см. AGENTS.md —
  «реальные слои добавляй по мере фич, не плоди заранее»).

### Decision 2. `entries` оставлен рядом с `sections`

`MainUiState.Success` хранит оба поля: `entries: List<EntryListItem>`
(по-прежнему) и `sections: List<DateSection>` (новое). `entries`
используется для empty-check и для идентификации `pendingDeleteId`
по `id` записи (UI уже передаёт в `requestDelete(item: EntryListItem)`,
но вся логика «открыт ли алерт» живёт на `id`).

**Почему:**

- empty-check (`entries.isEmpty()`) — самый дешёвый путь к заглушке
  «Добавьте первую запись»; перебирать `sections` для этого не нужно.
- При удалении последней записи секции она автоматически исчезает
  (фильтрация в `groupEntriesByDate`): пустой список `entries` →
  пустой список `sections` → ветка empty-state.
- Никакой синхронизации «entries vs sections» не нужно: `sections`
  производное от `entries`, расхождение невозможно по построению.

**Альтернативы:**

- Хранить только `sections`, считать empty по `sections.isEmpty()`.
  Отказ: расчёт `sections` дороже, чем чтение уже отсортированного
  `entries.size`; в пустом списке это лишняя работа на каждый emit.
- Хранить только `entries`, считать `sections` в composable. Отказ —
  см. Decision 1.

### Decision 3. `ZoneId` инъектируется для тестов

`MainViewModel` принимает `clock: () -> LocalDate` и `zone: ZoneId`
с дефолтами (`LocalDate::now` и `ZoneId.systemDefault()`); сигнатура
`factory(repository)` остаётся прежней — defaults сохраняют
совместимость со всеми существующими call site, factory их не
принимает и не пробрасывает. В JVM-тестах — прямой конструктор
`MainViewModel(repository, clock = { ... }, zone = ...)` с
фиксированными значениями (например, `zone = ZoneId.of("UTC")`,
`clock = { LocalDate.of(2026, 9, 6) }`).

**Почему:**

- Граничные случаи группировки (запись за 23:59 вчера vs 00:01 сегодня
  в разных зонах) проверяются детерминированно, без заморозки системных
  часов.
- То же касается `sectionTitle`: тестируем с явным `today`, без
  обращения к системному времени.
- Существующий `MainViewModelTest` остаётся без изменений —
  контракт `sections` покрывается `EntryDateGrouperTest` в tasks.md
  §1 (чистая функция, не требующая `MainViewModel`); добавлять
  VM-уровневый тест на секции преждевременно (см. AGENTS.md —
  «реальные слои добавляй по мере фич, не плоди заранее»).
- `clock: () -> LocalDate` фиксирует «сегодня» в момент эмиссии
  `MainViewModel.uiState` (см. Decision 1), а не в момент рекомпозиции
  или каждого вызова `groupEntriesByDate` — без риска «переезда»
  sticky-заголовка через полночь в долгоживущем composable. Это и
  есть наш выбор: «сегодня» снаружи функции, время снимка — момент
  эмита `MainViewModel.uiState`.

**Альтернативы:**

- Использовать `Clock` из `java.time.Clock` и `Clock.fixed(...)`.
  Рассматривали, но `Clock` — более тяжёлая абстракция, чем
  `() -> LocalDate`: для нашего кейса нужна только дата, не время.
- Принимать в `groupEntriesByDate` уже конвертированный `Instant` +
  `ZoneId`, без `clock`-callable на уровне `MainViewModel`. Отказ:
  «сегодня» пришлось бы вычислять на стороне вызова и передавать
  заново при каждом изменении источника; плюс лишние
  `Instant.ofEpochMilli(...).atZone(zone).toLocalDate()` на каждую
  запись. `clock: () -> LocalDate` на уровне VM даёт «сегодня» один
  раз на эмиссию — проще для тестов и прод-сборки.

### Decision 4. Заголовок локализуется параметрами, а не строковым id

`sectionTitle(date, today, todayLabel, yesterdayLabel, formatter)`
принимает уже разрешённые строки `todayLabel` / `yesterdayLabel` и
форматтер `DateTimeFormatter`. Внутри сравнивает `date` с `today`
и решает, что вернуть: «Сегодня» / «Вчера» / отформатированную дату.

**Почему:**

- Чистая JVM-функция: можно покрыть тестами без Android-ресурсов,
  Robolectric и контекста.
- Локализация выполняется в composable один раз через `stringResource(...)`,
  итоговая строка уходит в форматтер — никаких `Context.getString(...)`
  внутри функции.
- `DateTimeFormatter` тоже инъектируется параметром: тесты используют
  фиксированный `DateTimeFormatter.ofPattern("dd.MM.yyyy")`, прод —
  тот же шаблон (бэкенд локали одинаковый).

**Альтернативы:**

- Передавать `Context` и `R.string.entry_section_today`. Отказ:
  `sectionTitle` становится нечистой (зависит от Android), тесты
  требуют Robolectric или androidTest, медленнее.
- Сделать enum `SectionTitleVariant { Today, Yesterday, Date }` и
  резолвить в composable. Отказ: лишний слой ради двух строк.

### Decision 5. `stickyHeader` из Compose Foundation, разные `contentType`

В `LazyColumn` используем
`androidx.compose.foundation.lazy.LazyListScope.stickyHeader { SectionHeader(...) }`
для заголовков секций, между ними — обычный `items(section.items, ...)`.

`stickyHeader` — нативный DSL из Compose Foundation, отдельная
зависимость не нужна (уже подключена через `compose.foundation`).

Заголовки и строки получают разные `contentType`:

- заголовок (`stickyHeader`): `contentType = "section"` — значение
- строка (`items`): `contentType = { "row" }` — лямбда-фабрика

**Почему:**

- Compose внутри одной `LazyColumn` поддерживает раздельные пулы
  переиспользования по `contentType`: заголовок не вытесняет строку
  и наоборот — это снимает риск визуального «моргания» при скролле.
- `key` остаётся `it.id` (лямбда) для строк; для заголовков `key` —
  `section.date.toEpochDay()` (значение, не лямбда — стабильный,
  уникальный в пределах списка).

**Альтернативы:**

- Не использовать `contentType`. Отказ: без него Compose относит
  `SectionHeader` и `EntryListItemRow` к одному пулу и переиспользует
  composables друг в друга — визуальный артефакт при скролле.
- Подключать стороннюю библиотеку sticky-headers. Отказ: лишняя
  зависимость, нативный DSL достаточен.

## Risks / Trade-offs

- [Заголовок «Сегодня» застывает на момент эмиссии, а не рекомпозиции]
  → Если пользователь оставит экран открытым через полночь, запись
    сменит секцию только после следующей эмиссии из репозитория
    (новая запись / удаление / перезапуск). Для типичного сценария
    использования это незаметно; явно устраивать таймер «тикать в
    полночь» избыточно (см. AGENTS.md — YAGNI).
- [Явная сортировка в `groupEntriesByDate` перед `groupBy`]
  → Списки тренировок в offline-only приложении — десятки/сотни
    записей, O(n log n) на список такого размера незаметен.
    Компромисс выбран сознательно (см. Decision 1 «Контракт
    группировки»): самодостаточность функции важнее микро-оптимизации.
    Если когда-либо понадобится, переход на single-pass с явной
    сортировкой внутри прохода локален.
- [Тесты `groupEntriesByDate` зависят от `ZoneId` — расхождение зон
  между продом и тестом]
  → Митигация: `ZoneId` явно инъектируется в `MainViewModel`, дефолт
    совпадает с продом (`ZoneId.systemDefault()`); тесты фиксируют
    обе стороны (`zone` и `today`) детерминированно.

## Migration Plan

Развёртывание — простой коммит, без миграций данных и без фичефлагов:

1. Добавить `EntryDateGrouper.kt`, `SectionTitleFormatter.kt` +
   сопутствующие JVM-тесты.
2. Добавить строки в `strings.xml` / `values-en/strings.xml`.
3. Расширить `MainUiState.Success` полем `sections`.
4. Подмешать `sections` в `MainViewModel.uiState` через `combine`.
5. Перевести `MainScreen` → `EntryListItems` на цикл по секциям с
   `stickyHeader` + `items`, разные `contentType`.
6. Обновить `MainScreenPreview`, добавить androidTest на регрессию.

Откат — стандартный `git revert`. Поскольку модель данных и SQL не
тронуты, откат не требует схемной миграции.
