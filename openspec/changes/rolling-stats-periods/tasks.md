# Tasks

## 1. Скользящие окна периодов «Статистики» (TDD)

- [x] 1.1 Переписать в `ComputeCategoryStatsTest.kt` три теста границ периода
      под скользящие окна при `today = 2026-09-14`:
      `week_period_starts_monday_includes_today_excludes_sunday` →
      `week_period_is_last_seven_days_including_today` (d−7 вне, d−6 внутри,
      today внутри, завтра вне);
      `month_period_includes_first_day_excludes_last_of_previous` →
      `month_period_is_last_thirty_days_including_today` (15.08 вне, 16.08
      внутри = today − 29, today внутри);
      `year_period_includes_january_excludes_last_year` →
      `year_period_is_last_365_days_including_today` (14.09.2025 вне,
      15.09.2025 внутри = today − 364, today внутри). Проверка:
      `./gradlew testDebugUnitTest --tests "*ComputeCategoryStatsTest"` —
      три теста падают на текущей календарной логике (red)
- [x] 1.2 По design D1 добавить в `StatsPeriod.kt` поле `days`
      (DAY=1, WEEK=7, MONTH=30, YEAR=365) с `@Suppress("MagicNumber")` и
      KDoc про окна, заканчивающиеся сегодня; в `ComputeCategoryStats.kt`
      заменить тело `statsPeriodStart` на
      `today.minusDays((period.days - 1).toLong())` и переписать его KDoc.
      Проверка: те же три теста зелёные
- [x] 1.3 Прогнать `make test` — зелёный; если упали чужие ожидания
      календарных границ (`PreviewDemoDataTest`, `OverviewViewModelTest`,
      `VisiblePeriodsTest`) — поправить их под скользящие окна, не меняя
      порогов видимости

## 2. Документация и комментарии под скользящие окна

- [x] 2.1 Проверить комментарий сценария 4 в
      `screenshot-tests/src/main/java/com/myworkouts/screenshots/ScreenshotsTest.kt:190-191`:
      сценарий уже на «Неделе»
      (`overviewStats_whenWeekPeriodSelected_thenWritesWeekStatsPng`,
      `4-week-stats.png`), ISO-неделя не упоминается — при скользящих
      окнах комментарий точен. Проверка: правка не потребовалась (no-op)
- [x] 2.2 Проверить абзац про период «Неделя» в
      `docs/plans/screenshot-tests-infrastructure.md` (раздел «Сценарий 4»):
      ISO-неделя не упоминается — при скользящих окнах абзац точен.
      Проверка: правка не потребовалась (no-op)
- [x] 2.3 Прогнать `make format && make lint` — зелёный (ktlint + detekt + markdownlint)

## 3. Интеграционные проверки

- [x] 3.1 `make android-test` — зелёный; при падениях `StatsScreenTest` /
      `OverviewScreenTest`, вызванных старыми календарными границами,
      поправить только ожидания под скользящие окна
- [x] 3.2 `openspec validate rolling-stats-periods` (или
      `openspec validate --change rolling-stats-periods`) — без ошибок
