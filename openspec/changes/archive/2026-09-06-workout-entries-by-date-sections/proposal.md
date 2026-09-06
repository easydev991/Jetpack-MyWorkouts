## Why

Главный экран вкладки «Тренировки» показывает плоский список записей,
отсортированный по дате убыванием. С ростом числа записей (месяцы
трекинга) ориентироваться в списке становится трудно: пользователь
тратит время, выискивая «сегодняшние» или «вчерашние» записи среди
десятков строк. Привычный паттерн мобильных логов — визуальные
секции по календарной дате с заголовком «Вчера» / дата (сегодняшняя
секция — без заголовка: список и так открывается на сегодняшних
записях) — снижает когнитивную нагрузку и делает список читаемым
без скролла.

## What Changes

- Добавляем чистую функцию `groupEntriesByDate(entries, zone)`
  в новом файле `app/src/main/java/com/myworkouts/ui/screens/main/EntryDateGrouper.kt`,
  возвращающую `List<DateSection>` (data class `DateSection(date: LocalDate, items: List<EntryListItem>)`).
  Группировка по `LocalDate` в системной зоне устройства; секции
  упорядочены по дате убыванием; внутри секции записи идут по убыванию
  `date` (epoch millis) — гарантия держится явной сортировкой входа
  по `date` убыванием внутри `groupEntriesByDate`, контракт не зависит
  от порядка входного списка и от текущего `ORDER BY` в SQL.
- Заголовок секции резолвится на рендере инлайном в `EntryListItems`:
  «Вчера» / дата в формате `dd.MM.yyyy`; секция «Сегодня» заголовка
  не имеет — отдельная функция не заводится (выражение из одной
  строки, тестировать нечего — форматтер из stdlib).
- Добавляем строку `entry_section_yesterday` («Вчера» / «Yesterday»)
  в `res/values/strings.xml` и `res/values-en/strings.xml`.
- `MainUiState.Success` остаётся парой `(entries, pendingDeleteId)` —
  секции в state не храним: это производное от `entries`, рендерер
  считает сам через `remember(entries)` (`MainViewModel` не меняется).
- В `MainScreen` / `EntryListItems` заменяем плоский `items(entries, …)`
  на цикл по секциям: для всех секций, кроме сегодняшней,
  `stickyHeader { SectionHeader(…) }`, для всех —
  `items(section.items, …)` с разными `contentType` для заголовков
  и строк (`"section"` / `"row"`).
- Обновляем превью `MainScreenPreview`: добавляем сценарий с тремя
  секциями (сегодня / вчера / дата) — светлая + тёмная.
- Покрываем группировку JVM-тестами (JUnit 5), регрессию long-press /
  удаления / sticky-header / локали — androidTest'ами.

## Capabilities

### New Capabilities

Нет новых capabilities: вся функциональность относится к существующему
экрану «Тренировки» и описывается как новые REQUIREMENT-блоки внутри
его спека.

### Modified Capabilities

- `workouts-main-screen`: добавляется REQUIREMENT «Список записей
  разделён на секции по дате» с шестью сценариями (заголовки
  «Вчера» / дата — секция «Сегодня» без заголовка, упорядочение
  секций и записей внутри секции, работоспособность удаления
  из секции).

## Impact

- UI-слой:
  - `app/src/main/java/com/myworkouts/ui/screens/main/` — новые
    `EntryDateGrouper.kt`, `SectionHeader.kt`; правки `MainScreen.kt`
    (рендер-группировка через `remember`, инлайн-заголовок) и
    `MainScreenPreview.kt`.
  - `app/src/main/java/com/myworkouts/ui/state/MainUiState.kt` и
    `app/src/main/java/com/myworkouts/ui/viewmodel/MainViewModel.kt`
    — не меняются: секции — производное от `entries`, считаются на
    рендере.
- Ресурсы: `app/src/main/res/values/strings.xml`,
  `app/src/main/res/values-en/strings.xml` — одна новая строка.
- Тесты:
  - `app/src/test/.../ui/screens/main/EntryDateGrouperTest.kt`
    (JUnit 5).
  - Регрессионные androidTest на long-press / удаление / sticky-header /
    локализацию заголовка.

Что НЕ меняется (проектные ограничения; технические non-goals —
  в `design.md`):

- Доменная модель `EntryListItem` / `ExerciseEntry` остаётся как есть.
- Слой данных (SQL/DAO/репозиторий/мапперы) не трогается: новая
  сущность `DateSection` живёт только в UI; контракт упорядочения
  секций и записей держится явной сортировкой в `groupEntriesByDate`,
  а не SQL `ORDER BY`.
