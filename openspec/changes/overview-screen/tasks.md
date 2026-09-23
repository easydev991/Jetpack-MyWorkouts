# Tasks

## 1. Доменные типы и доступ к данным

- [ ] 1.1 Добавить доменную модель `EntryWithExercise` (`domain/model/`):
  поля `EntryListItem` + `exerciseId`, `category: ExerciseCategory`,
  `measuredBy: ExerciseMeasuredBy` и `exerciseComment: String?`
  (комментарий упражнения из каталога; `createdAt` из проекции не
  переносится — по Decision 1). Сначала — падающий JVM-тест в
  существующем `MappersTest` на маппинг
  `EntryListItemWithExerciseEntity → EntryWithExercise` (все поля, включая
  nullable), затем DAO-проекция `EntryListItemWithExerciseEntity` и запрос
  `observeEntriesWithExercise(): Flow<List<...>>` (JOIN по образцу
  `observeEntries`, `ORDER BY e.date DESC`), маппер в `Mappers.kt`, метод
  `fun entriesWithExercise(): Flow<List<EntryWithExercise>>` в
  `WorkoutDataRepository` и `RoomWorkoutDataRepository`. Обновить
  фейк-репозитории в существующих JVM-тестах (тривиальная реализация на
  `MutableStateFlow`). SQL-сторона DAO покрывается androidTest 7.1 —
  Room на JVM не поднимается.
  Проверка: `make test` — тест маппера красный до реализации (red) и
  зелёный после (green); схема БД (`app/schemas/`) не изменилась
- [ ] 1.2 Добавить enum `OverviewTab` в `domain/model/` по образцу
  `EntryField` (`@StringRes labelResId`: `overview_tab_stats`,
  `overview_tab_records`, `overview_tab_activity`, ru/en) и enums
  `StatsPeriod` (DAY/WEEK/MONTH/YEAR), `RecordsMode` (PER_SET/PER_DAY),
  `ActivityPeriod` (MONTH/QUARTER/HALFYEAR/YEAR, поле `days`) в
  `domain/usecase/` рядом с калькуляторами.
  Проверка: сборка `make lint` зелёная; строки существуют в values и
  values-en

## 2. Калькуляторы обзора — TDD (red → green)

Все JVM-тесты этого раздела — по навыку `kotlin-testing` (JUnit 5,
чистые функции без моков).

- [ ] 2.1 Написать падающие JVM-тесты `ComputeCategoryStatsTest`:
  фильтрация записей календарного периода (день/неделя с понедельника/
  месяц/год, системная зона), суммы повторов только REPS-записей, исключение
  категорий с нулевой суммой, доли упражнений drill-down, группировка записей
  категории по датам (убывание, включая TIME-записи), пустой период.
  Проверка: `make test` — новые тесты падают (red)
- [ ] 2.2 Реализовать `ComputeCategoryStats` в `domain/usecase/`
  (чистая функция, `today` — параметром).
  Проверка: `make test` — тесты 2.1 зелёные (green)
- [ ] 2.3 Написать падающие JVM-тесты `ComputeExerciseRecordsTest`:
  «за подход» = max(reps ?: durationSec ?: 0), «за день» = max суточных сумм
  `sets × value` (записи одного упражнения группируются по дню), значение > 0,
  отсутствие рекорда → нет строки, сортировка по дате убыванием, равные
  максимумы → наиболее ранняя дата, комментарий строки — по приоритету
  (запись-рекордсмен → упражнение → null), TIME и REPS
  вместе в одном списке.
  Проверка: `make test` — красные (red)
- [ ] 2.4 Реализовать `ComputeExerciseRecords`.
  Проверка: `make test` — зелёные (green)
- [ ] 2.5 Написать падающие JVM-тесты `ComputeStreaksTest`: сегодня в серии,
  серия жива при последней вчерашней тренировке, 0 при пропуске ≥2 дней,
  рекорд = max(завершённые, текущая), пустые данные → 0/0.
  Проверка: `make test` — красные (red)
- [ ] 2.6 Реализовать `ComputeStreaks`.
  Проверка: `make test` — зелёные (green)
- [ ] 2.7 Написать падающие JVM-тесты `ComputeHeatmapLevelsTest`: пороги
  `HEATMAP_THRESHOLDS = [1, 2, 3, 5]` (0/1/2/3–4/≥5 записей → 0/1/2/3/4),
  окно периода заканчивается сегодня, дни без записей → 0, выравнивание —
  день начала окна попадает в строку своего дня недели (ведущие пустые
  ячейки до понедельника в первой колонке).
  Проверка: `make test` — красные (red)
- [ ] 2.8 Реализовать `ComputeHeatmapLevels` (пороги — одна правляемая
  константа).
  Проверка: `make test` — зелёные (green)

## 3. Preferences — TDD

Тесты этого раздела — по навыку `kotlin-testing` (JUnit 5,
`PreferenceDataStoreFactory`, runTest).

- [ ] 3.1 Написать падающие JVM-тесты новых методов `AppSettingsDataStore`
  (паттерн существующих тестов на `PreferenceDataStoreFactory`):
  `overviewTabVisibility` (дефолт — все видимы), `setOverviewTabVisible`,
  `setAllOverviewTabsVisible` (один edit), `lastOverviewTab` (дефолт STATS,
  fallback на мусорное значение), `setLastOverviewTab`.
  Проверка: `make test` — красные (red)
- [ ] 3.2 Реализовать ключи и методы в `AppSettingsDataStore`
  (`show_overview_<tab>_tab` деривированные из enum, `last_overview_tab`;
  файл `appearance_prefs` не переименовывается).
  Проверка: `make test` — зелёные (green)
- [ ] 3.3 Написать падающие JVM-тесты `ExportBackupUseCaseTest` /
  `ImportBackupUseCaseTest` (после 3.2 — применяют его методы): JSON
  `"version": 2` с блоком `settings` (видимость вкладок +
  `lastOverviewTab`); обратная совместимость — бэкап версии 1 без
  `settings` импортируется и не меняет DataStore; при наличии `settings`
  значения применяются после восстановления.
  Проверка: `make test` — красные (red)
- [ ] 3.4 Расширить `ExportBackupUseCase`/`ImportBackupUseCase` (версия 2,
  опциональный блок `settings`, применение в DataStore после merge записей).
  Проверка: `make test` — зелёные (green)

## 4. ViewModel'ы — TDD

Тесты этого раздела — по навыку `kotlin-testing` (JUnit 5, MockK,
Fake-репозитории на `MutableStateFlow`, kotlinx-coroutines-test).

- [ ] 4.1 Написать падающие JVM-тесты `OverviewSettingsViewModelTest`
  (Fake-DataStore на `MutableStateFlow`, `FakeAnalyticsProvider`): uiState
  из visibility, guard — выключение последней вкладки игнорируется (нет
  записи в DataStore, нет события), `enableAll` — один вызов
  `setAllOverviewTabsVisible` и одно событие
  `ENABLE_ALL_OVERVIEW_TABS`, тоггл шлёт
  `TOGGLE_OVERVIEW_TAB_VISIBILITY` со значением `<tab>_on|off`.
  Проверка: `make test` — красные (red)
- [ ] 4.2 Реализовать `OverviewSettingsViewModel` по образцу
  `EntrySettingsViewModel` + `ui/state/OverviewSettingsUiState`.
  Проверка: `make test` — зелёные (green)
- [ ] 4.3 Написать падающие JVM-тесты `OverviewViewModelTest`: восстановление
  вкладки из preferences, дефолт STATS, fallback на первую видимую при
  скрытой сохранённой, `selectTab` пишет в DataStore + событие
  `SELECT_OVERVIEW_TAB`, смена периода сбрасывает drill-down +
  `SELECT_STATS_PERIOD`, выбор категории `SELECT_STATS_CATEGORY`,
  режимы рекордов `SELECT_RECORDS_MODE`, период хитмапа
  `SELECT_ACTIVITY_PERIOD`, пересчёт состояний при новой эмиссии записей
  (Fake-репозиторий).
  Проверка: `make test` — красные (red)
- [ ] 4.4 Реализовать `OverviewViewModel` (внешний combine четырёх потоков
  через `OverviewUserState` — группировка обходит detekt
  `LongParameterList`; `stateIn` с
  `SharingStarted.WhileSubscribed(5000)`, uiState — data class с вложенными
  состояниями) +
  `ui/state/OverviewUiState.kt` (+ StatsUiState/RecordsUiState/
  ActivityUiState, DrillEntry с `id`).
  Проверка: `make test` — зелёные (green)

## 5. Навигация и тема

- [ ] 5.1 Добавить `Screen.Overview("overview")`, `Screen.OverviewSettings`
  (`"overview_settings"`), значения `AppScreen.OverviewScreen` /
  `AppScreen.OverviewSettingsScreen`, маппинг в `screenForRoute`,
  регистрацию в `AppNavHost` (composable внутри `RootScreen.kt`) с
  фабриками VM (private `*NavDestination`
  composable). Нижнюю панель не трогать — скрытие существующим контрактом.
  Проверка: сборка; маршрут overview не входит в `tabs.map { it.route }`
- [ ] 5.2 Вынести цвета в `ui/theme/Color.kt`: `CategoryPush/Pull/Legs/Core/
  Cardio`, `HeatmapLevel1..4`, `StreakOrangeStart/End` + extension
  `ExerciseCategory.color`.
  Проверка: сборка; единственное место объявления hex-значений

## 6. Перенос вёрстки из драфтов и новая вёрстка (по слоям, с удалением DRAFT)

Все превью этого раздела — по Decision 12: обе темы (светлая + тёмная),
`locale = "ru"`, `showBackground = true`.

- [ ] 6.1 Вынести общий форматтер дат-заголовков секций (сегодня без
  заголовка — на стороне вызова, «Вчера», `EEE, d MMM` — только для дат
  текущего года, `EEE, dd.MM.yyyy` — для прошлых лет; правило выбора как
  на главном экране) в `ui/util/` рядом с `DurationFormatter`; JVM-тест
  `SectionDateFormatterTest` на чистую функцию (сначала red, затем green).
  Использовать в RecordsScreen
  и drill-down.
  Проверка: `make test`
- [ ] 6.2 Перенести `OverviewScreen` в чистовой файл: DRAFT-раннер
  `OverviewScreen.kt` переименовать в `OverviewScreenDraft.kt` (живёт до
  6.10, чтобы освободить имя), перенести `OverviewTopBar`
  (заголовок «Обзор» + подзаголовок при одной вкладке + кнопка настроек
  `Icons.Filled.Settings` из драфта),
  `SecondaryTabRow` со скрытием при одной видимой, подписка на
  `OverviewViewModel`, навигация на `OverviewSettingsScreen`. Строки
  `overview_title`, `a11y_overview_settings`. Превью светлой/тёмной
  (`locale = "ru"`).
  Проверка: ручной запуск — вкладки переключаются, состояние из VM
- [ ] 6.3 Перенести `StatsScreen` (портрет): пикер периода
  (SegmentedButton), донат Canvas, легенда, пустая подсказка
  «Нет тренировок за выбранный период»; строки `overview_period_*`,
  `overview_unit_reps`, `overview_stats_empty`. Превью обе темы.
  Проверка: превью и ручной запуск на реальных данных
- [ ] 6.4 Перенести drill-down статистики: донат оттенков категории
  (`shade` из драфта), легенда упражнений, кнопка «← Категории»
  (`overview_back_to_categories`), список записей по датам
  (заголовки — 6.1, LazyColumn с ключами `id`, `LazyColumn`-корень
  портрета); заголовки секций дат — семантика заголовка
  (`semantics { heading() }`, в драфте её нет), декоративные иконки —
  `contentDescription = null` (требование «Локализация и доступность
  обзора»). Превью.
  Проверка: ручной запуск — drill-down входит/выходит, смена периода
  сбрасывает
- [ ] 6.5 Перенести ландшафтную раскладку StatsScreen (iter5): строка
  [донат 160dp | легенда-сетка 2 колонки | записи], записи скрыты вне
  drill-down. Превью `widthDp = 891, heightDp = 411`.
  Проверка: поворот эмулятора — раскладка по макету, контент не за вырез
- [ ] 6.6 Перенести `RecordsScreen`: переключатель «За подход»/«За день»,
  список рекордов (значение `overview_unit_reps` /
  `formatDurationResource`, дата — 6.1; для даты today — краткий формат
  текущего года: форматтер 6.1 для today возвращает null — подстановка
  на стороне вызова, сортировка из UiState), пустое
  «Нет тренировок» (`records_empty`). `RecordRow` — Row из двух Column:
  слева название и комментарий при наличии (приоритет: комментарий
  записи-рекордсмена → комментарий упражнения → не показывать; стиль по
  образцу `ExerciseListItemRow` — `bodyMedium`, `onSurfaceVariant`,
  зазор 4.dp), справа значение и дата. Строки `records_mode_per_set`,
  `records_mode_per_day`. Превью: строка с комментарием записи,
  строка с комментарием упражнения, строка без комментария,
  пустой список (`records_empty`).
  Проверка: ручной запуск — оба режима корректны на REPS и TIME
- [ ] 6.7 Перенести `ActivityScreen`: карточка серии (градиент, plurals
  `overview_streak_days`, подпись `overview_streak_record` — видна только
  когда текущая серия меньше рекордной), пикер
  периода (`overview_period_month` / `_quarter` / `_halfyear` / `_year`),
  хитмап Canvas + `WeekdayLabels` (подписи «пн»…«вс» из драфта,
  `activity_weekday_*`; остаются на месте при прокрутке сетки) +
  `LegendRow` (`activity_less`, `activity_more`). Превью.
  Проверка: ручной запуск — уровни соответствуют числу записей, подписи
  дней выровнены с ячейками
- [ ] 6.8 Перенести `OverviewSettingsScreen` по образцу EntrySettings:
  тогглы трёх вкладок, «Включить все» при выключенной хотя бы одной,
  подпись `overview_settings_subtitle`, заголовок — общий ресурс с
  EntrySettings, a11y кнопки назад — существующий `R.string.back` (как на
  остальных non-tab экранах). Превью.
  Проверка: guard последней вкладки — тоггл не двигается
- [ ] 6.9 Добавить кнопку обзора в `MainScreen`: `IconButton` с
  `Icons.Filled.DonutLarge` в `TopAppBar` (действия), видимость
  `success.entries.isNotEmpty()`,   `a11y_open_overview` ru/en, клик —
  `onOverviewClick` + событие `OPEN_OVERVIEW` (как `OPEN_ENTRY_CREATE`),
  параметр `onOverviewClick` прокинут в `RootScreen` →
  `Screen.Overview.route`. `MainViewModel`
  не меняется.
  Проверка: ручной запуск — кнопка появляется/исчезает при удалении
  последней записи
- [ ] 6.10 Удалить все DRAFT-файлы `ui/screens/overview/` (6 файлов:
  переименованный в 6.2 DRAFT-раннер `OverviewScreenDraft.kt` и все
  `*Preview.kt` с демо-моделями) после полного переноса.
  Проверка: `grep -r "DRAFT" app/src/main/java/com/myworkouts/ui/screens/overview/`
  пуст — перенесённый код без драфт-комментариев; `make lint` и `make test`
  зелёные — сборка без ссылок на удалённые символы

## 7. androidTest (Compose, JUnit 4)

Все тесты этого раздела — по навыку `kotlin-ui-testing` (Compose Testing,
Room in-memory, реальные `WorkoutDatabase`/`RoomWorkoutDataRepository`).

- [ ] 7.1 `WorkoutDatabaseTest`: DAO `observeEntriesWithExercise` возвращает
  записи с категорией и `measuredBy` (Room in-memory).
  Проверка: `make android-test` зелёный
- [ ] 7.2 `MainScreenTest`: кнопка обзора видна при непустом списке, скрыта
  при пустом; клик вызывает `onOverviewClick`.
  Проверка: `make android-test`
- [ ] 7.3 `OverviewScreenTest`: вкладки переключают контент; при одной
  видимой вкладке `SecondaryTabRow` скрыт и подзаголовок показывает её
  название; навигация в настройки и обратно (createComposeRule с
  TestNavigator или NavHost-тест как в существующих).
  Проверка: `make android-test`
- [ ] 7.4 `OverviewSettingsScreenTest`: тогглы вызывают VM-колбэки, guard —
  выключение последней игнорируется, «Включить все» скрывается когда всё
  включено.
  Проверка: `make android-test`
- [ ] 7.5 `StatsScreenTest`: клик по категории открывает drill-down (легенда
  упражнений + записи), «← Категории» возвращает, смена периода сбрасывает
  drill-down, пустой период — подсказка.
  Проверка: `make android-test`
- [ ] 7.6 Дополнить `RootScreenTest` (NavHost-уровень `RootScreen`, сетап
  уже есть): навигация main → overview → overview_settings; на обоих
  маршрутах нижняя панель навигации скрыта (`assertDoesNotExist`).
  Проверка: `make android-test`

## 8. Аналитика и локализация — финальный аудит

- [ ] 8.1 Проверить полноту аналитики: все новые `UserActionType`
  отправляются из соответствующих точек (кнопка главного, вкладки, периоды,
  drill-down, режимы, тогглы, «включить все»); ScreenView нового экрана
  приходит через `screenForRoute`.
  Проверка: код-ревью маппинга + JVM-тесты секции 4 покрывают события
- [ ] 8.2 Аудит строк: все новые ключи есть в `values/strings.xml` и
  `values-en/strings.xml` 1:1, plurals `overview_streak_days`,
  «повт.» — `overview_unit_reps`, даты без ветвления по локали; логи на
  русском; a11y-аудит новых экранов — описания кнопок, декоративные
  иконки без описаний, heading у заголовков секций (требование
  «Локализация и доступность обзора»).
  Проверка: `make lint` + ручная сверка ключей ru/en

## 9. Финальная проверка

- [ ] 9.1 Прогнать `make format && make lint && make test` — всё зелёное,
  detekt без новых предупреждений (лимит 5 не поднимается).
  Проверка: вывод команд
- [ ] 9.2 Прогнать `make android-test` на эмуляторе (один флейвор github) —
  все тесты зелёные.
  Проверка: вывод команды
- [ ] 9.3 Ручной сквозной сценарий на эмуляторе: главная (кнопка появляется
  при записи) → обзор (вкладки, период, drill-down, поворот) → настройки
  (guard, «включить все») → back-стек; ландшафт с вырезом.
  Проверка: скриншоты сценариев
