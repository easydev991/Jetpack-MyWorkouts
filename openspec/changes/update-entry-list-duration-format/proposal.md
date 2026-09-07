# Proposal: update-entry-list-duration-format

## Why

На главном экране длительность записи отображается «сырым» числом секунд
(«3 х 20», «120»), тогда как на экране создания записи то же значение
показывается человекочитаемо («20 сек», «2 мин 30 сек», «1 час»). Формат
внутри приложения должен быть единым; пикер допускает значения до 3600 сек,
и «3600» в списке нечитаемо.

## What Changes

- Длительность в строке списка главного экрана форматируется как на экране
  создания: < 60 сек — «N сек», ≥ 60 сек — «M мин S сек» с пропуском нулевых
  частей, ≥ 1 часа — через plurals «час/часа/часов» (до 3600 сек = «1 час»).
- Формат повторов («4 х 10», «10») не меняется. Схема «подходы × значение»
  при нескольких подходах сохраняется: «3 х 20 сек», «4 х 2 мин 30 сек».
- `EntryListItemRow` перестаёт подставлять `reps ?: durationSec ?: 0` в один
  числовой параметр: тип значения различается, длительность форматируется
  через `formatDurationResource`.
- `formatDuration` (и её @Composable-обёртка `formatDurationResource`)
  переезжает из пакета `entry` в общее место — используется двумя экранами,
  дублирование не плодится.
- Превью (`EntryListItemRowPreview`, `MainScreenPreview`) и тесты обновлены
  под новый формат: JVM-тесты на форматтер — раньше реализации (TDD).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `workouts-main-screen`: требование «Формат значения записи» — сценарии с
  длительностью переходят с сырых секунд на человекочитаемый формат
  («3 х 20» → «3 х 20 сек», «20» → «20 сек»; новые сценарии «2 мин»,
  «4 х 2 мин 30 сек», «1 час»). Сценарии с повторами не меняются.

## Impact

- `app/src/main/java/com/myworkouts/ui/screens/main/EntryValueFormatter.kt` —
  `formatEntryValue` принимает уже отформатированное значение (`String`):
  одна точка склейки «подходы × значение» для повторов и длительности.
- `app/src/main/java/com/myworkouts/ui/screens/main/EntryListItemRow.kt` —
  различение reps/durationSec при формировании значения.
- `app/src/main/java/com/myworkouts/ui/screens/entry/EntryCreateFormatters.kt`
  и `EntryCreateFields.kt` — перенос `formatDuration`/`formatDurationResource`
  в общее место (`ui/util`), импорт в `entry`
  (обоснование — design Decision 2).
- Строковые ресурсы уже есть (`entry_duration_minutes_short`,
  `entry_duration_seconds_short`, plurals `entry_duration_hours` ru/en) —
  новых не требуется.
- Превью: `EntryListItemRowPreview.kt`, `MainScreenPreview.kt` (в т.ч.
  `previewTimeItem` → «20 сек» и запись с `durationSec = 60` (2 подхода) в
  `sectionsPreviewEntries` → «2 х 1 мин»).
- Тесты: `EntryValueFormatterTest` (JVM, сигнатура со `String` и сценарии
  склейки длительности), androidTest
  `MainScreenTest.content_when_entries_then_shows_each_row_with_name_and_value`
  (захардкожено «20» → «20 сек»). `MainScreenEditNavigationTest` использует
  только REPS-запись — не затронут.
