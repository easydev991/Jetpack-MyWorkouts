# Tasks

## 1. Чистые функции видимости периодов (TDD)

- [x] 1.1 Написать падающие JVM-тесты `visibleStatsPeriods` / `visibleActivityPeriods` (новый файл `VisiblePeriodsTest.kt` в `app/src/test/`, зеркало `VisiblePeriods.kt` из 1.2): пороги Stats — Неделя `earliest < today`, Месяц `earliest <= today−7`, Год `earliest <= today−30`; пороги Activity — Квартал `≤ today−30`, Полгода `≤ today−91`, Год `≤ today−182`; граничные даты (ровно порог ± 1 день), `earliest = null` → только базовый период (Stats — День, Activity — Месяц), порядок периодов не меняется. Канон значений порогов — дельта спеки. Проверка: `./gradlew testDebugUnitTest --tests "*PeriodsTest"` — тесты падают (классов ещё нет)
- [x] 1.2 Реализовать чистые функции в `domain/usecase/` рядом с `StatsPeriod`/`ActivityPeriod` — один файл `VisiblePeriods.kt` с обеими функциями (прецедент — `visibleTabs`); константы порогов — `private const val` (detekt MagicNumber пропускает по `ignoreConstantDeclaration`, suppress не нужен), `today` — параметр. Проверка: тесты из 1.1 зелёные

## 2. ViewModel: самая ранняя дата, видимые периоды, clamp (TDD)

- [x] 2.1 Написать падающие тесты в `OverviewViewModelTest`: `earliest` = min календарного дня записей из загруженного списка; `visiblePeriods` попадает в `StatsUiState`/`ActivityUiState`; clamp — при дефолте WEEK и первой тренировке сегодня выбирается DAY; для хитмапа — clamp с не-дефолтного выбора: выбран «Год», после удаления записей старше 182 дней выбирается «Полгода» (spec-сценарий «Clamp выбранного периода хитмапа»); при удалении записей старше порога выбранный период подменяется самым длинным видимым; подмена не отправляет `SELECT_STATS_PERIOD`/`SELECT_ACTIVITY_PERIOD`. Проверка: `./gradlew testDebugUnitTest --tests "*OverviewViewModelTest"` — новые тесты падают
- [x] 2.2 Реализовать в `OverviewViewModel` (в существующем `combine`): расчёт `earliest`, вызов функций видимости, clamp; добавить поля `visiblePeriods` в `StatsUiState`/`ActivityUiState` со значением по умолчанию «все периоды». Проверка: тесты из 2.1 и весь существующий `OverviewViewModelTest` зелёные

## 3. Проценты в легенде (TDD)

- [x] 3.1 Обновить `ComputeCategoryStatsTest`: новые кейсы расчёта процента строки от суммы `totalSets` показанных строк (уровень категорий и drill-down), округление до целого, сумма процентов ≠ 100 %. Проверка: `./gradlew testDebugUnitTest --tests "*ComputeCategoryStatsTest"` — новые тесты падают
- [x] 3.2 Реализовать расчёт процента (в `ComputeCategoryStats`, см. design D6) и собирать значение строки легенды в `legendItems()` (`StatsLegendRow.valueText`): процент по локали — ru «N %», en «N%» (строковый ресурс с плейсхолдером, парность ключей ru/en); `LegendItemContent` остаётся тупым рендером готовой строки. Формат `DrillEntryRow` («3 х 12» / «3 х 30 сек.») не менять. Проверка: тесты из 3.1 зелёные

## 4. Условный рендер экранов

- [x] 4.1 StatsScreen: показывать `StatsPeriodPicker` только при `visiblePeriods.size >= 2` (сегменты — только видимые) в обоих body — портретном и `StatsScreenLandscapeBody` (вызовы `StatsScreen.kt:170` и `:224`); пикер получает `visiblePeriods` и итерирует его (`itemShape(count = visiblePeriods.size)` вместо `entries.size`); при скрытии контент поднимается на место пикера. Проверка: компиляция, ручной осмотр превью; поведение покрывается androidTest 5.2
- [x] 4.2 ActivityScreen: при `visiblePeriods.size >= 2` — `SingleChoiceSegmentedButtonRow` с видимыми сегментами (итерация `visiblePeriods`, `itemShape(count = visiblePeriods.size)` вместо `entries.size`); иначе вместо кнопки — жирный заголовок с месяцами данных через « – » (standalone-названия месяцев ICU по локали, первая буква каждого названия — заглавная, актуально только для ru (ICU en уже с заглавной); строковый ресурс — только разделитель, ru/en 1:1; см. design D5). Проверка: компиляция; поведение покрывается androidTest 5.3

## 5. Превью и androidTest

- [x] 5.1 Новые превью по конвенциям (светлая + тёмная `UI_MODE_NIGHT_YES`, `showBackground = true`, `locale = "ru"`, одно состояние — одно превью): StatsScreen со скрытым пикером — портрет и ландшафт (в стиле существующих `StatsLandscape*Preview`), ActivityScreen с заголовком месяцев; существующие превью собираются с дефолтными `visiblePeriods` без правок. Проверка: компиляция, осмотр превью в IDE
- [x] 5.2 Обновить `StatsScreenTest`: проценты в легенде вместо `overview_unit_reps`-текста (ожидаемые строки собирать из строковых ресурсов через `context.getString`, как в существующих ассертах), скрытие пикера при одном видимом периоде, отображение видимых сегментов (ландшафт — визуально в превью 5.1: ландшафтных androidTest в проекте нет); убрать из тестовых данных использование `stats_duration_unit_hours`, `totalReps`/`totalDurationSec` (удаляются в §6). Проверка: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.overview.StatsScreenTest'` (после `make emulator-fast`) — зелёный
- [x] 5.3 Обновить `ActivityScreenTest`: заголовок месяцев вместо кнопки при одном видимом периоде, видимость порогов пикера (clamp не дублируется — покрыт в 2.1); проценты — из строковых ресурсов, заголовок месяцев сверять с текстом, собранным в тесте тем же ICU-путём, либо только факт наличия. Проверка: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.overview.ActivityScreenTest'` (после `make emulator-fast`) — зелёный
- [x] 5.4 Обновить `OverviewScreenTest` под проценты/видимость; ожидаемые строки — из ресурсов. Проверка: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.overview.OverviewScreenTest'` (после `make emulator-fast`) — зелёный

## 6. Чистка мёртвого груза (после §5 — тесты уже не ссылаются на удаляемые символы)

- [x] 6.1 Удалить `legendValueText`, `legendValueTextResource`, `formatStatsDurationCompact`, `formatStatsDurationCompactResource` из `DurationFormatter.kt` и легендные кейсы из `DurationFormatterTest`; поправить KDoc-ссылки на удаляемые символы (`DurationFormatter.kt`, `StatsScreen.kt`). Проверка: `make format && make lint && make test` — зелёные, без неиспользуемых членов (detekt в лимите)
- [x] 6.2 Удалить ресурсы `stats_duration_unit_seconds/minutes/hours/days`, `stats_value_separator` из `strings.xml` (ru и en); ресурс `overview_unit_reps` оставить. Проверка: `make lint` зелёный, `make test` зелёный
- [x] 6.3 Удалить `totalReps`/`totalDurationSec` у `CategoryTotal`/`ExerciseTotal` и хелперы `repsVolume`/`durationVolume` в `ComputeCategoryStats.kt`; убрать ассерты на эти поля из `ComputeCategoryStatsTest` и `OverviewViewModelTest` и обновить их упоминания в KDoc `ComputeCategoryStats.kt`; обновить демо-данные превью и `PreviewDemoDataTest`. Проверка: `make test` зелёный

## 7. Финальная валидация

- [x] 7.1 `make format && make lint && make test` — зелёные, detekt в лимите `maxIssues: 5` (не повышать). Проверка: вывод команд
- [x] 7.2 `make emulator-fast` (после старта эмулятора) и `make android-test` — все androidTest зелёные. Проверка: отчёт `scripts/test_report.py` в выводе
