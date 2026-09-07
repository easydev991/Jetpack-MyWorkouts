# Design: update-entry-list-duration-format

## Context

См. proposal.md — Why. Технически сейчас:

- `EntryValueFormatter.formatEntryValue(sets, value, separator)` — чистая
  функция, принимает числовое `value`; `EntryListItemRow` передаёт в неё
  `item.reps ?: item.durationSec ?: 0`, поэтому тип значения внутри функции
  неизвестен — длительность печатается как сырые секунды.
- `formatDuration(seconds, hoursWord, minutesShort, secondsShort)` — чистая
  функция в `ui/screens/entry/EntryCreateFormatters.kt`; `@Composable`
  обёртка `formatDurationResource(seconds)` — в `EntryCreateFields.kt`.
  Обе `internal` (модульная видимость), покрыты JVM-тестами
  (`EntryCreateFormattersTest`).
- Строковые ресурсы уже есть: `entry_duration_minutes_short`,
  `entry_duration_seconds_short`, plurals `entry_duration_hours` (ru/en).

## Goals / Non-Goals

**Goals:**

- Человекочитаемая длительность в строке главного экрана — тот же формат и
  тот же код, что на экране создания (переиспользование, не дублирование).
- Форматтер остаётся чистой JVM-тестируемой функцией (TDD).
- Формат повторов и схема «подходы × значение» не меняются.

**Non-Goals:**

- Никаких новых строковых ресурсов, изменений БД/слоя данных, навигации.
- Не трогаем сам форматтер `formatDuration` (логика уже верна) — только
  место его проживания.

## Decisions

### Decision 1: тип значения различается в `EntryListItemRow`; склейка — одна точка, `formatEntryValue` принимает готовую строку

В `EntryListItemRow` значение — одно выражение: тип значения определяется
наличием повторов у записи:

- `item.reps?.toString() ?: formatDurationResource(item.durationSec ?: 0)` —
  готовый текст значения (повторы или человекочитаемая длительность,
  design Decision 2), подставляемый в единственную функцию склейки
  `formatEntryValue(item.sets, value, separator)`.

Для этого `formatEntryValue` меняет тип `value` с `Int` на `String` — одна
логика склейки на оба типа значения, без параллельного форматтера и без
параметра-лямбды (`// ponytail: один форматтер склейки на оба типа значения`).
Случай `reps == null && durationSec == null` недостижим (`EntryValidator`);
fallback `?: 0` сохраняется, чтобы ветка была тотальной.

*Альтернативы (отклонены):* отдельный
`formatEntryDuration(sets, durationSec, separator, format: (Int) -> String)`
с лямбдой — вторая копия склейки «подходы × значение» и параметр-функция ради
одной строки; if-склейка прямо в caller — логика уходит в composable, не
покрываемый JVM-тестами; один форматтер с параметром-типом значения —
смешивает два формата в одной функции.

### Decision 2: `formatDuration` и обёртка переезжают в общее место

Новый файл `ui/util/DurationFormatter.kt` (пакет `com.myworkouts.ui.util`),
куда переносится без изменений логики:

- `formatDuration` + константы `SECONDS_PER_HOUR`, `SECONDS_PER_MINUTE`
  (из `EntryCreateFormatters.kt`);
- `formatDurationResource` (из `EntryCreateFields.kt`).

`entry`-экран импортирует оттуда; `EntryListItemRow` тоже. Дата-функции
остаются в `EntryCreateFormatters.kt` — они специфичны для экрана записи.

*Альтернатива:* импортировать `internal`-функцию из пакета `entry` напрямую
(Kotlin разрешает: internal — модульная видимость) — нулевой дифф, но создаёт
зависимость главного экрана от пакета чужого экрана; перенос одного файла
дешевле и не плодит скрытых связей.

`ui/util` — осознанное исключение из правила AGENTS.md «реальные слои
добавляй по мере фич, не плоди заранее»: второй потребитель (строка главного
экрана) уже существует. Это не слой, а файл с двумя функциями; `di/` и
`MyWorkoutsApplication.kt` при этом остаются несозданными.

### Decision 3: JVM-тесты — тесты зеркалируют source; склейка и текст длительности тестируются раздельно

Тесты зеркалируют source (AGENTS.md): duration-тесты из
`EntryCreateFormattersTest` переезжают в новый
`app/src/test/java/com/myworkouts/ui/util/DurationFormatterTest.kt`;
`EntryCreateFormattersTest` остаётся с дата-тестами.

Тесты `formatEntryValue` в `EntryValueFormatterTest` обновляются на
`value: String`; новые падающие тесты — комбинированные сценарии склейки:
3 × «20 сек» → «3 х 20 сек», 4 × «2 мин 30 сек» → «4 х 2 мин 30 сек»,
1 × «1 час» → «1 час» (готовый текст длительности передаётся строкой;
JVM-разделитель в тестах — ASCII « x », locale-neutral, как в существующих
тестах). Пишутся раньше реализации (TDD); сами тексты длительности
(«20 сек», «2 мин 30 сек», «1 час») уже покрыты тестами `formatDuration`.

### Decision 4: androidTest — без хардкода формата

В `MainScreenTest.content_when_entries_then_shows_each_row_with_name_and_value`
ожидание «20» (сырой `durationSec`) заменяется на сборку ожидаемой строки
из строковых ресурсов («20 сек») — как тест уже делает с разделителем через
`R.string.entry_value_separator`. `MainScreenEditNavigationTest` проверен:
использует только REPS-запись, не затронут.

## Risks / Trade-offs

- [Перенос `formatDuration` может изменить поведение] → чистый перенос:
  тело функции не правится, существующие JVM-тесты переезжают и проходят.
- [Регресс формата повторов] → ожидания существующих тестов
  `formatEntryValue` («4 x 10», «10») и androidTest-проверка «4 х 10»
  остаются без изменений; меняется только тип параметра.

## Migration Plan

Не требуется: UI-изменение без данных и совместимости. Откат — revert коммита.

## Open Questions

Нет.

## Post-Review Simplifications (после ревью, код финальный)

- `formatDuration`: параметр `hoursWord: (Int) -> String` заменён на
  готовый `hoursText: String` — production-обёртка передавала лямбду,
  игнорирующую аргумент, а JVM-тесты через неё проверяли собственный
  хелпер склонения, не код приложения. Алгоритм не менялся.
- Значение в `EntryListItemRow` — одно выражение через `?:` вместо
  дублирования `formatEntryValue` в двух ветках.
