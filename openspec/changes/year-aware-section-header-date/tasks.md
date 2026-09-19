# Tasks

## 1. androidTest: новые кейсы на оба формата (TDD — красные)

- [ ] 1.1 `MainScreenSectionsTest.kt`: добавить тест
  `sections_when_archive_date_then_header_includes_weekday_and_full_date`
  — фикстура с одной записью на фиксированной архивной дате
  (год ≠ текущий; например, `LocalDate.of(2024, 5, 15)`, час 12); ожидаемая строка
  заголовка строится через `LocalDate.of(2024, 5, 15).format(
  DateTimeFormatter.ofPattern("EEE, dd.MM.yyyy",
  Locale.getDefault()))`; `assertIsDisplayed` по этой строке.
  Проверка: до правки `MainScreen.kt` тест красный — текущий
  форматтер выдаёт только `15.05.2024`, без дня недели и без
  запятой; красная фаза здесь — расхождение строк, не падение
  компиляции.
- [ ] 1.2 `MainScreenSectionsTest.kt`: добавить тест
  `sections_when_current_year_date_then_header_has_weekday_without_year`
  — фикстура с одной записью на фиксированной дате текущего года
  (`LocalDate.of(LocalDate.now().year, 5, 15)`, час 12);
  ожидаемая строка — `LocalDate.of(...).format(DateTimeFormatter
  .ofPattern("EEE, d MMM", Locale.getDefault()))`; `assertIsDisplayed`
  по этой строке. В начале теста — `Assume.assumeFalse(LocalDate.now()
  == LocalDate.of(LocalDate.now().year, 5, 15))` (добавить
  `import org.junit.Assume`): в этот единственный день запись уйдёт
  в «Сегодня» без заголовка и ассерт упал бы — assumption
  пропускает тест (статический `@Ignore` не подходит — не умеет
  в условие). Проверка: до правки `MainScreen.kt` тест
  красный — текущий форматтер выдаёт `15.05.<текущий год>`,
  в ожидаемой строке года тоже нет, но есть день недели —
  расхождение.
- [ ] 1.3 Локализованный сценарий: добавить параметризацию или
  два отдельных теста (`sections_when_current_year_date_then_header_…_ru`
  / `_en`), повторяющих §1.2 в обеих локалях — это закрывает
  спека-сценарий «Сокращения дня недели и месяца соответствуют локали
  устройства» (локализация `MMM` проверяется только на формате
  «этот год», в архивном `EEE, dd.MM.yyyy` сокращения месяца нет).
  Фикстура: одна запись на `LocalDate.of(LocalDate.now().year, 5, 15)`,
  час 12; в начале каждого теста то же `Assume.assumeFalse(...)`,
  что в §1.2 (риск совпадения с «сегодня» — design D4). `setContentInLocale(
  "ru"/"en")` (через `Configuration.locale`) НЕ достаточно —
  `DateTimeFormatter.ofPattern(..., Locale.getDefault())` берёт
  JVM-локаль, которая не меняется через `Configuration`. Решение —
  перед `composeTestRule.setContent` явно поставить
  `Locale.setDefault(Locale("ru"))` или `Locale.setDefault(
  Locale.ENGLISH)`, сохранив исходное значение в `lateinit var
  initialLocale: Locale` — единственный выбранный паттерн (nullable-
  вариант с `?.let` и обнулением не используется, гибрид не
  разводить; `lateinit` безопасен — `@Before` выполняется перед
  каждым `@Test`, поэтому к моменту `@After` поле уже
  инициализировано). Ожидаемые строки
  строятся тем же `ofPattern(..., Locale.getDefault())` с шаблоном
  `"EEE, d MMM"` — в ru ожидается кириллический день недели и
  сокращённое название месяца по правилам JDK CLDR (точка у
  большинства, кроме «мая» — без), в en — латиница без точки
  после месяца (`May`). К тестовому файлу добавить
  `import java.util.Locale` и `import org.junit.After` (сейчас
  в тесте только `org.junit.Before`). В классе добавить
  `@After fun restoreLocale()` — `Locale.setDefault(initialLocale)`
  — без этого JVM-локаль утекает между
  тестами и ломает чужие androidTest в том же прогоне. Проверка:
  до правки `MainScreen.kt` оба локализованных теста красные по той
  же причине, что §1.2; после §2.1 (проверяется прогоном §2.2) —
  оба зелёные и строки реально различаются (ru ≠ en).

## 2. Реализация в MainScreen.kt (green)

- [ ] 2.1 `EntryListItems` (`MainScreen.kt:329`): вместо одного
  `DateTimeFormatter.ofPattern("dd.MM.yyyy")` ввести два
  `remember`-форматтера с `Locale.getDefault()` — `currentYearFormatter`
  (шаблон `"EEE, d MMM"`) и `archiveFormatter` (шаблон
  `"EEE, dd.MM.yyyy"`). Старый `formatter` удалить. В
  `if (section.date == yesterday) … else …` добавить вложенную
  ветку по году: `if (section.date.year == today.year)
  currentYearFormatter else archiveFormatter`. Проверка:
  компиляция.
- [ ] 2.2 Запустить тесты §1.1, §1.2, §1.3 — зелёные.
- [ ] 2.3 `MainScreenPreview.kt` — `sectionsPreviewEntries()`
  (строки 130–173): добавить одну запись на фиксированной
  прошлогодней дате (например, `LocalDate.of(
  LocalDate.now().year - 1, 3, 10)`, час 12), чтобы
  `MainScreenContentSectionsPreview` отрисовывал все три
  формата: «Вчера» / `EEE, d MMM` / `EEE, dd.MM.yyyy`.
  Обновить оба KDoc (design D5), заменив текст целиком.
  KDoc фикстуры (строка 127):
  «Записи для превью секций: сегодня, вчера и пять дней назад
  (фиксированная дата `dd.MM.yyyy`)» →
  «Записи для превью секций: сегодня, вчера, пять дней назад
  (текущий год) и прошлогодняя фиксированная дата».
  KDoc превью `MainScreenContentSectionsPreview` (строки 175–177,
  стоит перед `@Preview`-аннотациями):
  «Три секции: сегодня / вчера / дата `dd.MM.yyyy`. Превью задаёт
  только записи — секции рендерер считает сам через
  [groupEntriesByDate]» →
  «Четыре записи — четыре секции: сегодня (без заголовка) / вчера /
  дата текущего года / дата прошлого года; заголовки — „Вчера" /
  `EEE, d MMM` / `EEE, dd.MM.yyyy`. Превью задаёт только записи —
  секции рендерер считает сам через [groupEntriesByDate]».
  Проверка: рендер превью в IDE —
  видны три разных формата заголовков; компиляция.

## 3. Обновление существующих androidTest

Порядок важен: 3.1 → 3.2. Обратный ломает компиляцию между шагами —
после удаления val на строке 67 вызовы на строках 164/183
не скомпилируются.

- [ ] 3.1 `MainScreenSectionsTest.kt` — добавить `private fun
  formatSectionDate(date: LocalDate): String` с той же логикой
  выбора по году, что в `MainScreen.kt:329`, и заменить вызовы
  `dateFormatter.format(today.minusDays(5))` (строки 164, 183)
  на `formatSectionDate(today.minusDays(5))`. Ассерты и структура
  существующих тестов не меняются; тесты остаются зелёными при любом
  годе запуска (ожидаемая строка строится той же функцией), но
  в январе для `today.minusDays(5)` (дата уходит в прошлый год)
  фактически ассертится архивный формат — контраст с §1.2
  («без года») размывается. Проверка: компиляция (val
  `dateFormatter` на строке 67 становится неиспользуемым).
- [ ] 3.2 `MainScreenSectionsTest.kt:67` — удалить ставший
  неиспользуемым `val dateFormatter`. Проверка: компиляция;
  существующие тесты `sections_when_entries_across_dates_then_today_has_no_header_…`
  и `sections_when_scrolled_then_date_header_sticks_above_rows`
  после правки `MainScreen.kt` зелёные.
- [ ] 3.3 `MainScreenSectionsTest.kt:50–53` — обновить KDoc
  тест-класса: «заголовки „Вчера" / `dd.MM.yyyy`» → «заголовки
  „Вчера" / `EEE, d MMM` / `EEE, dd.MM.yyyy`», чтобы описание
  соответствовало трём форматам после §2.1. Проверка: текст KDoc
  совпадает с фактическим набором.

## 4. Верификация

- [ ] 4.1 `make android-test` — единый прогон: новые тесты
  §1.1–§1.3 зелёные, существующие тесты §3.2 зелёные, остальные
  androidTest не сломаны. Отчёт — `scripts/test_report.py` уже
  в make-цели.
- [ ] 4.2 `make format && make lint && make test` — без новых
  замечаний, JVM-тесты зелёные.
- [ ] 4.3 `MainScreenPreview` рендерится в IDE без ошибок —
  видны все три формата заголовков («Вчера», `EEE, d MMM`,
  `EEE, dd.MM.yyyy`); правка фикстуры — §2.3.