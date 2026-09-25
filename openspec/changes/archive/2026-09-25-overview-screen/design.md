# Design

## Context

См. proposal.md — зачем экран обзора. Текущее состояние:

- Готовые DRAFT-черновики в `ui/screens/overview/` (6 файлов, ~2300 строк):
  вёрстка финальна, состояние — `remember` + демо-данные; помечена `// DRAFT:`
  каждая захардкоженная строка/цвет. Макеты `progress-drafts/iter4_v1.html`
  (портрет) и `iter5_v1.html` (ландшафт) согласованы. И черновики, и
  HTML-макеты — временные источники переноса: после реализации оба набора
  удаляются (DRAFT — задачей 6.10, HTML-макеты — владельцем вне рамок
  задачи). Источник истины после переноса — реализованный код, его превью и
  спеки; все размеры и цвета из макетов, нужные после удаления, зафиксированы
  в долговечных местах (ландшафтные пропорции — задача 6.5, цвета —
  Decision 10, параметры хитмапа — в коде).
- `WorkoutDataRepository.entries()` даёт `EntryListItem` без категории и
  `measuredBy` — для расчётов нужен join с упражнениями.
- `AppSettingsDataStore` (`appearance_prefs`) уже хранит видимость полей
  entry-формы (`entryFieldVisibility`, ключи деривируются из enum `EntryField`)
  — образец для видимости вкладок.
- `RootScreen`: `screenForRoute`, `shouldShowBottomBar = currentRoute in tabs.map { it.route }`,
  фабрики VM по образцу `MainViewModel.factory(...)`.
- БД version 3; JOIN-проекция (не `@Entity`) не меняет схему — bump версии
  не нужен (прецедент: `EntryListItemEntity`).

## Goals / Non-Goals

**Goals:**

- Перенести вёрстку драфтов в чистовые Screen/UiState/VM/use-case слои без
  изменения UX; удалить весь DRAFT-код.
- Реактивные расчёты на Room-Flow: пересчёт при любом изменении записей.
- TDD для расчётов (доли, рекорды, серии, уровни хитмапа) и обеих VM.
- Одна точка правки для цветов категорий/хитмапа и порогов уровней.

**Non-Goals:**

- Подписи месяцев на хитмапе (нет в макете), аккордеон легенды (сознательно
  не портирован в драфте), режим «длительности» в донате (см. Decision 4 —
  upgrade path).
- Фильтры по конкретным упражнениям/датам, экспорт статистики.
- Отдельные VM на вкладки, отдельные экраны Loading/Error.

## Decisions

### Decision 1: Данные — новый JOIN-метод репозитория `entriesWithExercise()`

Один широкий SQL-запрос DAO `observeEntryRows()` (SQL JOIN
`exercise_entries e JOIN exercises x ON x.id = e.exercise_id`, проекция в
plain data class `EntryListItemWithExerciseEntity`, `ORDER BY e.date DESC`)
- метод интерфейса `fun entriesWithExercise(): Flow<List<EntryWithExercise>>`.

*Пост-ревью уточнение (ponytail-review): два копипастных SQL
(`observeEntries` и `observeEntriesWithExercise`) консолидированы в один
широкий запрос — ревью верно подсветило риск дрейфа двух копий при
эволюции схемы. Доменные модели и потребители не изменились: сужение
широкой проекции до нужного виду — в репозитории мапперами
`toListItem()` (список главного экрана; 4 доп. колонки игнорирует) и
`toEntryWithExercise()` (обзор). Отдельная узкая проекция
`EntryListItemEntity` удалена.*

`EntryWithExercise` — доменная модель обзора: поля `EntryListItem` плюс
`exerciseId`, `category: ExerciseCategory`, `measuredBy: ExerciseMeasuredBy`,
`exerciseComment: String?` (комментарий упражнения из каталога);
столбец `created_at` в проекцию не включается (прецедент — `EntryListItem`,
строке списка не нужен). Одна запись = одна строка; все три вкладки берут
данные из этого одного Flow.

- Отвергнуто: `combine(entries(), exercises())` в VM — join/сортировка в
  памяти при каждой эмиссии, VM знает структуру хранения, два триггера вместо
  одного. SQL-join каноничнее и уже есть прецедент в DAO.
- Отвергнуто: одна широкая доменная модель для всех потребителей —
  главный экран получал бы 4 неиспользуемых поля, правка затрагивала бы
  ~20 файлов и 13 тестовых фейков; типовая точность дешевле SQL-дубля,
  а дубль устраняется проекциями вниз без потери моделей.

Схема БД не меняется: проекция — не `@Entity`, `version = 3` остаётся,
модификация Room-схемы и JSON-экспорт не требуются. Фейк-репозитории в
JVM-тестах получают тривиальную реализацию нового метода на `MutableStateFlow`.

### Decision 2: Расчёты — чистые функции в `domain/usecase`, без Result

Четыре калькулятора (TDD, детерминированные, не ходят в данные — им не нужен
`Result<T>`/`try/catch`, в отличие от `ExportBackupUseCase`). Это
документированное исключение из конвенции AGENTS.md «`Result<T>` с `try/catch`
в use-cases»: правило про use-cases на IO-границе, у чистых функций без IO
failure mode нет:

- `ComputeCategoryStats.kt`: `(entries, StatsPeriod, today: LocalDate)` →
  доли категорий + данные drill-down (суммы по категориям и упражнениям,
  записи категории, сгруппированные по датам).
- `ComputeExerciseRecords.kt`: `(entries, RecordsMode)` → по одному лучшему
  результату на упражнение + дата.
- `ComputeStreaks.kt`: `(entries, today)` → `(current, record)`.
- `ComputeHeatmapLevels.kt`: `(entries, period, today)` → уровень 0–4 на день.

Единый helper фильтрации периода — внутри `ComputeCategoryStats`; все даты —
`Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())` (как
`groupEntriesByDate` главного экрана). `today` передаётся параметром —
тестируемость без часов.

Enums `StatsPeriod`, `RecordsMode`, `ActivityPeriod` живут рядом с
калькуляторами в `domain/usecase` (используются расчётами и VM; Compose их
не хранит).

### Decision 3: Периоды статистики — календарные

День = сегодня; неделя = ISO-неделя с понедельника по воскресенье; месяц =
календарный месяц; год = календарный год; системная зона. Границы периода —
`LocalDate` → `[start, today]` (будущие записи с кривой датой не рвут логику).
«Неделя с понедельника» совпадает с сеткой хитмапа (7 строк с понедельника).

- Отвергнуто: скользящие окна (7/30/365 дней назад) — «неделя» без привязки
  к календарю хуже соответствует ожиданию «текущая неделя».

Хитмап — окна фиксированной длины от сегодня назад (30/91/182/365), как в
макете и драфте: сетка всегда заканчивается сегодня.

### Decision 4: Донат — только повторы; `durationSec` — за один подход

Смешивать секунды и повторения в одной доле бессмысленно, макет
легенды — «N повт.». Решение:

- Донат и легенда статистики (категории и упражнения drill-down) — суммы
  `reps` записей упражнений с `measuredBy = REPS`. TIME-записи в доли и
  легенду не входят, но отображаются в списке записей drill-down своей
  категории (группировка по датам — все записи категории).
- Категория только с TIME-нагрузкой за период в донате не видна — при полном
  отсутствии REPS-записок период «пустой» (подсказка). Trade-off осознан:
  TIME-прогресс полностью виден во вкладках «Рекорды» и «Активность».
  Upgrade path (не в этой задаче): отдельный режим «длительность» с подписью
  «N мин».

Семантика `ExerciseEntry.durationSec` — **длительность одного подхода**:
формат главного экрана «3 х 45 сек» = 3 подхода по 45 секунд (значение в
строке — всегда за один подход, как `reps`). Отсюда формулы рекордов
(совпадают с драфтом):

- «За подход»: `max(reps ?: durationSec ?: 0)` по записям упражнения.
- «За день»: для каждого календарного дня сумма `sets × (reps ?: durationSec)`
  по записям, максимум по дням. Группировка по дню делаем честно (в драфте
  комментарий «двух записей одного упражнения за день не бывает» — не
  инвариант домена, полагаться на него нельзя).

При нескольких записях (или днях) с равным максимальным значением рекордом
считается наиболее ранняя по дате (в драфте — `maxByOrNull` по списку,
упорядоченному по дате: возвращает первый из равных максимумов).

Значение ≤ 0 не считается рекордом (без записей — нет строки).

`ExerciseRecord` несёт комментарий строки: комментарий записи-рекордсмена,
если задан; иначе комментарий упражнения; иначе `null` (вёрстка по образцу
`ExerciseListItemRow`: `bodyMedium`, `onSurfaceVariant`, зазор 4.dp).

### Decision 5: Streak — «сегодня» засчитывается, серия жива до полного пропуска дня

День тренировки = календарный день с ≥1 записью (системная зона). Текущая
серия: если сегодня есть запись — длина последовательности, заканчивающейся
сегодня; иначе если вчера есть — длина последовательности, заканчивающейся
вчера (серия жива: сегодня ещё не кончился); иначе 0. Рекордная серия —
максимум по всем последовательным прогонам дат (включая текущий). Пустые
данные → 0/0. Реализация — один проход по отсортированным distinct-датам.
Подпись `overview_streak_record` показывается только когда текущая серия
меньше рекордной (в непрерывной серии current = record — подпись избыточна).

**Пост-релизная доработка: три состояния карточки.**
`StreakResult` получил `workedOutToday: Boolean` (`today in daySet` —
множество дат уже построено, вычисление бесплатное). Режим отображения —
UI-понятие, поэтому enum `StreakCardState` (Active / Pending / Lost) живёт
в `ui/state/StreakCardState.kt`, а не в домене; маппинг в
`OverviewViewModel`: `currentStreak == 0` → Lost; `workedOutToday` →
Active; иначе → Pending. Отрисовка в `ActivityScreen`:

- Active — карточка без изменений (оранжевый градиент, огонёк);
- Pending — «сохраняем теплоту» серии: бледный градиент (альфа
  оригинального оранжевого), огонёк приглушён, текст —
  `secondary`/`onSurfaceVariant` (белый на бледном фоне не читается);
- Lost — карточки нет: при рекорде > 1 — `StreakRecordRow` («Рекорд серии:
  N дней», `top = 8.dp`, чтобы строка не прилипала к верхнему краю контента;
  общий `Spacer(16.dp)` снизу сохраняет вертикальный ритм экрана); при
  рекорде ≤ 1 место остаётся пустым — единственная «запись о рекорде»
  смысла не несёт, хитмап подтягивается ближе к верху.

Формулировка счётчика уточнена: plurals
`overview_streak_days_in_a_row` («N дней подряд») для серии больше одного
дня, прежний `overview_streak_days` — для единственного дня («1 день»).

### Decision 6: Уровни хитмапа — фиксированные пороги по числу записей

`0/1/2/3–4/≥5 записей → 0/1/2/3/4`. Пороги — один список
`HEATMAP_THRESHOLDS = listOf(1, 2, 3, 5)` рядом с функцией: уровень =
количество порогов, которые день проходит. Легко менять в одной точке.

- Отвергнуты квантили (стиль GitHub): при одной тренировке за квартал она
  получила бы максимальный уровень (все ненулевые дни «сильные») — вводит в
  заблуждение; плюс уровни дня дрожали бы при каждом добавлении записи.
- Счётчик — число записей за день (не повторы/минуты): универсален для REPS
  и TIME, совпадает с семантикой «день тренировки» серии (Decision 5).

### Decision 7: Год в хитмапе — оставить

Драфтовый `ActivityPeriod.YEAR(365)` уже написан и работает (53 колонки,
горизонтальный скролл). Стоимость нулевая, ценность годового обзора реальная.
Макет его не содержит, но и не запрещает; оставляем все четыре периода:
Месяц/Квартал/Полгода/Год.

### Decision 8: Настройки в DataStore — деривированные ключи по образцу `entryFieldVisibility`

Новый enum `OverviewTab` в `domain/model/` (образец `EntryField`: с
`@StringRes labelResId`, без Compose-зависимостей — DataStore его импортирует):

- Видимость: ключ `booleanPreferencesKey("show_overview_${tab.name.lowercase()}_tab")`,
  дефолт `true` (отсутствие ключа = видима); Flow
  `overviewTabVisibility: Flow<Map<OverviewTab, Boolean>>`;
  `setOverviewTabVisible(tab, visible)`; `setAllOverviewTabsVisible()` —
  один `edit` на «включить все» (одна эмиссия, одно аналитическое событие).
- Последняя вкладка: `stringPreferencesKey("last_overview_tab")` — имя
  enum-константы; `setLastOverviewTab(tab)`;
  `lastOverviewTab: Flow<OverviewTab>` c `valueOf` + fallback `STATS` на
  `IllegalArgumentException` (паттерн `theme`).
- Файл `appearance_prefs` не переименовывается; миграция не нужна — новые
  ключи с дефолтами.
- Настройки вкладок попадают в резервную копию: `buildBackupJson` пишет
  `"version": 2` и блок `settings` (`overviewTabVisibility` +
  `lastOverviewTab`); при импорте блок опционален — бэкапы версии 1
  валидны, при отсутствии `settings` DataStore не меняется, при наличии
  значения применяются сразу после восстановления (`lastOverviewTab` с тем
  же fallback на мусорное значение).

### Decision 9: Одна `OverviewViewModel` на экран; `OverviewSettingsViewModel` отдельно

`OverviewViewModel(repository, dataStore, analyticsProvider)`:

- Пользовательские состояния: `MutableStateFlow` для `statsPeriod = WEEK`,
  `selectedCategory: ExerciseCategory? = null`, `recordsMode = PER_SET`,
  `activityPeriod = MONTH` (не персистятся — как в драфте; переживают
  поворот через VM).
- `uiState: StateFlow<OverviewUiState>` = внешний `combine` из четырёх
  потоков: `overviewTabVisibility`, `lastOverviewTab`,
  `repository.entriesWithExercise()` и вложенный `combine` четырёх
  `MutableStateFlow` — пользовательские состояния группируются в data class
  `OverviewUserState` (группировка обходит detekt `LongParameterList`).
  Итог —
  `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
  OverviewUiState())` по конвенции AGENTS.md.
- Валидация: `selectedTab = сохранённая вкладка, если видима, иначе первая
  видимая` (спека «Сохранённая вкладка скрыта настройками»).
- `OverviewUiState` — data class: `selectedTab`, `visibleTabs`,
  `stats: StatsUiState`, `records: RecordsUiState`, `activity: ActivityUiState`
  (каждая — data class по конвенции; расчёты чистые и дешёвые — считаем все
  три на каждую эмиссию, дифинг Compose отсечёт лишние рекомпозиции).
- `StatsUiState`/`DrillEntry` — из драфта, но `DrillEntry` получает `id`
  (ключи LazyColumn), `category`-тип — доменный `ExerciseCategory`.
  **Как реализовано (отступление от буквы, intent сохранён):** отдельный
  `DrillEntry` в `ui/state` не заводится — drill-down переиспользует
   доменные `DrillDateGroup` + `EntryWithExercise` (пост-ревью:
  промежуточный `DrillEntryData` удалён; поле `id` уже есть в
  `EntryWithExercise`, дублирующий data class field-в-field не нужен).
  Аналогично `RecordsUiState` хранит
  `ExerciseRecordData`, `ActivityUiState` — `HeatmapDayData`.

Драфтовый enum `StatsCategory` **удаляется** — дублирует доменный
`ExerciseCategory` (PUSH/PULL/LEGS/CORE/CARDIO); подписи — существующие
`exercise_category_*`, цвета — Decision 10.

`OverviewSettingsViewModel(dataStore, analyticsProvider)` — копия
`EntrySettingsViewModel`: `uiState` из `overviewTabVisibility`, но
`setTabVisible` с guard: `if (!visible && включена ровно одна) return` —
до аналитики и записи (тоггл игнорируется целиком, спека). `enableAll()` —
одно событие `ENABLE_ALL_OVERVIEW_TABS`.

`MainViewModel` **не меняется**: видимость кнопки — `entries.isNotEmpty()`
прямо в `MainScreen` (как проверка пустого списка); отдельного поля UiState
не заводим.

### Decision 10: Цвета — именованные константы в `ui/theme/Color.kt`

Одна точка правки, одна версия на обе темы (в макете категории и зелёные
уровни одинаковы в dark/light; различается только уровень 0 — он берётся из
темы `MaterialTheme.colorScheme.surfaceContainerHighest`, как в драфте):

- Категории: `CategoryPush = Color(0xFFFF5252)`, `CategoryPull = 0xFF448AFF`,
  `CategoryLegs = 0xFF69F0AE`, `CategoryCore = 0xFFE040FB`,
  `CategoryCardio = 0xFFFFD740` + extension
  `ExerciseCategory.color: Color` (when по enum — единственное место связи).
- Хитмап: `HeatmapLevel1..4` (`0xFF81C784`, `0xFF4CAF50`, `0xFF2E7D32`,
  `0xFF1B5E20`) — от светлого к тёмному: больше записей — темнее ячейка,
  как в легенде «Меньше … Больше» (GitHub-конвенция; в драфте порядок
  инвертирован — при переносе перевернуть).
- Градиент серии: `StreakOrangeStart = 0xFFFF5722`,
  `StreakOrangeEnd = 0xFFFF9800`.

Оттенки drill-down (`shadeDeltas` + `ColorUtils`) переносятся из драфта как
есть — они параметризованы `baseColor` категории.

### Decision 11: Навигация и аналитика

`Screen.Overview("overview")` / `Screen.OverviewSettings("overview_settings")`
(паттерн `Screen.Appearance`), регистрация в `AppNavHost` (composable
внутри `RootScreen.kt`, отдельного файла нет) c фабриками
`OverviewViewModel.factory(repository, dataStore, analyticsProvider)` и
`OverviewSettingsViewModel.factory(dataStore, analyticsProvider)`;
`screenForRoute` += `AppScreen.OverviewScreen` / `AppScreen.OverviewSettingsScreen`.
Нижняя панель скрывается существующим контрактом — правок скрытия нет.

**Как реализовано (отступление):** «отдельного файла нет» не выдержало —
два новых registration-компоузабла сломали detekt `LongMethod` (75 →
порог 60) и подняли `RootScreen.kt` до порога `TooManyFunctions` (11).
Шесть destination-компоузаблов (`MainNavDestination`, `MoreNavDestination`,
`AppearanceNavDestination`, `AppDataNavDestination`, `OverviewNavDestination`,
`OverviewSettingsNavDestination`) вынесены в
`ui/screens/root/NavDestinations.kt` — механический перенос без логики;
`AppNavHost` в `RootScreen.kt` остаётся графом registrations.

Новые `UserActionType` (именование по консистентности с
`TOGGLE_ENTRY_FIELD_VISIBILITY` / `SELECT_THEME`):

- `OPEN_OVERVIEW` — тап кнопки на главном (логируется в `MainScreen`, как
  `OPEN_ENTRY_CREATE`).
- `SELECT_OVERVIEW_TAB` (value: `stats|records|activity`),
  `SELECT_STATS_PERIOD` (`day|week|month|year`),
  `SELECT_STATS_CATEGORY` (имя категории / `back`),
  `SELECT_RECORDS_MODE` (`per_set|per_day`),
  `SELECT_ACTIVITY_PERIOD` (`month|quarter|halfyear|year`),
  `TOGGLE_OVERVIEW_TAB_VISIBILITY` (`<tab>_on|off`),
  `ENABLE_ALL_OVERVIEW_TABS`.

ScreenView летит централизованно из `RootScreen` — новые значения
`AppScreen` подхватываются автоматически.

### Decision 12: Перенос вёрстки и безопасные зоны

Перенос блоками из `*Preview.kt` в чистовые файлы: `OverviewScreen.kt`
(каркас + `OverviewTopBar` + вкладки), `StatsScreen.kt`, `RecordsScreen.kt`,
`ActivityScreen.kt`, `OverviewSettingsScreen.kt` + `*Preview.kt` рядом
(обёртка `JetpackMyWorkoutsTheme`, светлая/тёмная, `locale = "ru"`,
`showBackground`). DRAFT-файлы удаляются
одной задачей (6.10) после полного переноса: весь каталог пересоздаётся
чистовыми файлами. Конфликт имён решается в 6.2: DRAFT-раннер
`OverviewScreen.kt` переименовывается в `OverviewScreenDraft.kt`, демо-
данные и переименованный раннер удаляются в 6.10 вместе с `*Preview.kt`.

Подписи дней недели слева от хитмапа (`WeekdayLabels`) и легенда
«Меньше … Больше» переносятся из драфта: в HTML-макетах их нет, решение
владельца — сохранить, они критичны для чтения сетки. Подписи месяцев
по-прежнему вне скоупа (их нет и в драфте).
*Пост-ревью уточнение: вместо ресурсов `activity_weekday_*`
(7 × 2 локали) подписи даёт платформа —
`DayOfWeek.getDisplayName(TextStyle.SHORT, locale)`; локаль берётся из
`LocalConfiguration` (per-app language совпадает с локалью ресурсов).
Для ru сокращения совпадают с прежними ресурсами «пн»…«вс» байт-в-байт,
для прочих локалей платформа даёт свои (en — «Mon»…). Ресурсы удалены.*

Безопасные зоны: каждый экран — собственный `Scaffold` с дефолтными
`contentWindowInsets` (образец `EntrySettingsScreen` для нетабовых
экранов): `TopAppBar` съедает статус-бар, тело — `safeDrawing` снизу и по
бокам (вырез в ландшафте). Вложенность в `RootScreen`-Scaffold уже
выставлена (`WindowInsets(0,0,0,0)` — отступы считает внутренний Scaffold).

Строки: переносятся в `strings.xml` по мере переноса каждого экрана —
полный реестр новых ключей:

- Каркас: `overview_title`, `a11y_overview_settings` (6.2),
  `a11y_open_overview` (6.9); лейблы вкладок `overview_tab_stats` /
  `overview_tab_records` / `overview_tab_activity` (1.2).
- Статистика: `overview_period_day` / `_week` / `_month` / `_year`,
  `overview_stats_empty`, `overview_unit_reps` = «%1$d повт.»,
  `overview_back_to_categories` (6.3–6.4).
- Рекорды: `records_mode_per_set`, `records_mode_per_day`,
  `records_empty` (6.6).
- Активность: `overview_period_month` / `_quarter` / `_halfyear` /
  `_year`, plurals `overview_streak_days` (день/дня/дней),
  plurals `overview_streak_days_in_a_row` (день/дня/дней подряд — 10.1),
  `overview_streak_record`, `activity_less`, `activity_more` (6.7);
  plurals `a11y_activity_heatmap` (a11y-аудит 8.2) читаются только через
  `getQuantityString` — `getString` на plurals падает
  («complex map type», поймано ручной проверкой на эмуляторе);
  `activity_weekday_*` удалены пост-ревью — подписи дней теперь ICU.
- Настройки: `overview_settings_subtitle` (заголовок — общий ресурс с
  EntrySettings) (6.8).

Форматтеры дат — переиспользуются
шаблоны главного экрана с тем же правилом выбора: «Вчера», короткий
`EEE, d MMM` — для дат текущего года, `EEE, dd.MM.yyyy` — для прошлых лет
(локаль по умолчанию — без ветвления ru/en); вынести дублирующийся код дат
драфтов в общий helper `ui/util` рядом с `DurationFormatter` (одна точка
правки для рекордов и drill-down; главный экран сохраняет свой
`EntryDateGrouper`).

## Risks / Trade-offs

- [Категория только с TIME-нагрузкой не видна в донате] → осознанный
  trade-off (Decision 4); вкладки «Рекорды»/«Активность» покрывают TIME;
  upgrade path — режим длительности.
- [1052-строчный `StatsScreenPreview` — риск потерять детали при переносе] →
  перенос по блокам (модели → донат → легенда → drill → ландшафт), после
  каждого блока превью светлой/тёмной темы; сверка с макетами iter4/iter5
  (они доступны весь период переноса — удаляются только после реализации).
- [detekt: `LongMethod`/`LongParameterList` на больших экранах] → структура
  приватных composable уже дробная в драфте; `combine` с группировкой в
  `OverviewUserState` (Decision 9); лимит `maxIssues: 5` не поднимается.
- [«Сегодня» в серии зависит от часового пояса устройства] → системная зона
  везде (`groupEntriesByDate`-паттерн); смена зоны корректно пересчитает на
  следующей эмиссии.
- [Тоггл последней вкладки игнорируется — можно подумать, что UI сломан] →
  поведение задано макетом и спекой; визуально тоггл не двигается.

## Migration Plan

Без миграций: схема БД не меняется (Decision 1), новые ключи DataStore имеют
дефолты (Decision 8). Развёртывание — обычный релиз; откат — предыдущая
версия (старые данные не затрагиваются). DRAFT-файлы удаляются в конце
задачи, после того как весь функциональный код перенесён.
