## Why

Главный экран вкладки «Тренировки» показывает плоский список записей,
отсортированный по дате убыванием. С ростом числа записей (месяцы
трекинга) ориентироваться в списке становится трудно: пользователь
тратит время, выискивая «сегодняшние» или «вчерашние» записи среди
десятков строк. Привычный паттерн мобильных логов — визуальные
секции по календарной дате с заголовком «Сегодня» / «Вчера» / дата —
снижает когнитивную нагрузку и делает список читаемым без скролла.

## What Changes

- Добавляем чистую функцию `groupEntriesByDate(entries, today, zone)`
  в новом файле `app/src/main/java/com/myworkouts/ui/screens/main/EntryDateGrouper.kt`,
  возвращающую `List<DateSection>` (data class `DateSection(date: LocalDate, items: List<EntryListItem>)`).
  Группировка по `LocalDate` в системной зоне устройства; секции
  упорядочены по дате убыванием; внутри секции записи идут по убыванию
  `date` (epoch millis) — гарантия держится явной сортировкой входа
  по `date` убыванием внутри `groupEntriesByDate`, контракт не зависит
  от порядка входного списка и от текущего `ORDER BY` в SQL.
- Добавляем `sectionTitle(date, today, todayLabel, yesterdayLabel, formatter)`
  в новом файле `app/src/main/java/com/myworkouts/ui/screens/main/SectionTitleFormatter.kt`.
  Возвращает «Сегодня» / «Вчера» / дату в формате `dd.MM.yyyy`.
- Добавляем строки `entry_section_today` («Сегодня» / «Today») и
  `entry_section_yesterday` («Вчера» / «Yesterday») в `res/values/strings.xml`
  и `res/values-en/strings.xml`.
- Расширяем `MainUiState.Success` полем `sections: List<DateSection>`
  (`entries` остаётся для empty-check и для `pendingDeleteId`-lookup).
- В `MainViewModel` вычисляем `sections` как производное от `entries`
  через `combine` с инъектируемыми `today` и `zone` (для тестов —
  явные параметры; в рантайме — `LocalDate.now()` и `ZoneId.systemDefault()`).
- В `MainScreen` / `EntryListItems` заменяем плоский `items(entries, …)`
  на цикл по секциям: `stickyHeader { SectionHeader(…) }` + `items(section.items, …)`
  с разными `contentType` для заголовков и строк (`"section"` / `"row"`).
- Обновляем превью `MainScreenPreview`: добавляем сценарий с тремя
  секциями (сегодня / вчера / дата) — светлая + тёмная.
- Покрываем группировку и форматтер заголовков JVM-тестами (JUnit 5),
  регрессию long-press / удаления / sticky-header / локали —
  androidTest'ами.

## Capabilities

### New Capabilities

Нет новых capabilities: вся функциональность относится к существующему
экрану «Тренировки» и описывается как новые REQUIREMENT-блоки внутри
его спека.

### Modified Capabilities

- `workouts-main-screen`: добавляется REQUIREMENT «Список записей
  разделён на секции по дате» с шестью сценариями (заголовки
  «Сегодня» / «Вчера» / дата, упорядочение секций и записей внутри
  секции, работоспособность удаления из секции).

## Impact

- UI-слой:
  - `app/src/main/java/com/myworkouts/ui/screens/main/`
    — новые `EntryDateGrouper.kt`, `SectionTitleFormatter.kt`;
    правки `MainScreen.kt` и `MainScreenPreview.kt`.
  - `app/src/main/java/com/myworkouts/ui/state/MainUiState.kt` —
    новое поле `sections` в `Success`.
  - `app/src/main/java/com/myworkouts/ui/viewmodel/MainViewModel.kt` —
    производное `sections`; конструктор принимает clock/zone
    с дефолтами для тестов, сигнатура `factory(repository)`
    не меняется.
- Ресурсы: `app/src/main/res/values/strings.xml`,
  `app/src/main/res/values-en/strings.xml` — две новые строки.
- Тесты:
  - `app/src/test/.../ui/screens/main/EntryDateGrouperTest.kt`,
    `SectionTitleFormatterTest.kt` (JUnit 5).
  - Регрессионные androidTest на long-press / удаление / sticky-header /
    локализацию заголовка.

Что НЕ меняется (проектные ограничения; технические non-goals —
  в `design.md`):

- Доменная модель `EntryListItem` / `ExerciseEntry` остаётся как есть.
- Слой данных (SQL/DAO/репозиторий/мапперы) не трогается: новая
  сущность `DateSection` живёт только в UI; контракт упорядочения
  секций и записей держится явной сортировкой в `groupEntriesByDate`,
  а не SQL `ORDER BY`.
