## 1. Перенос `formatDuration` в общее место

- [x] 1.1 Создать `ui/util/DurationFormatter.kt`: перенести без изменений
  логики `formatDuration` + `SECONDS_PER_HOUR`/`SECONDS_PER_MINUTE` из
  `ui/screens/entry/EntryCreateFormatters.kt` и `formatDurationResource`
  из `ui/screens/entry/EntryCreateFields.kt` (design Decision 2);
  в `entry`-файлах оставить импорты; дата-функции не трогать. Перенести
  duration-тесты из `EntryCreateFormattersTest` в новый
  `app/src/test/java/com/myworkouts/ui/util/DurationFormatterTest.kt`
  (тесты зеркалируют source). Перенос — первым шагом: строка списка будет
  дергать `formatDurationResource`. Проверка: `make test` — зелёные,
  `EntryCreateScreen`-тесты не сломаны.

## 2. TDD: одна точка склейки значения

- [x] 2.1 В `EntryValueFormatterTest` обновить тесты на новую сигнатуру
  `formatEntryValue(sets, value: String, separator)` (design Decision 1):
  существующие тесты повторов — со строковым значением («4 x 10», «10»);
  новые падающие тесты склейки длительности: 3 × «20 сек» → «3 х 20 сек»;
  4 × «2 мин 30 сек» → «4 х 2 мин 30 сек»; 1 × «1 час» → «1 час»
  (готовый текст длительности передаётся строкой — склейка тестируется
  отдельно от `formatDuration`; JVM-разделитель в тестах — ASCII « x »,
  locale-neutral, как в существующих тестах; пользовательский формат —
  « х », см. spec). Проверка: `make test` — тесты красные (сигнатуры
  со `String` ещё нет).
- [x] 2.2 Реализовать `formatEntryValue(sets, value: String, separator)` в
  `ui/screens/main/EntryValueFormatter.kt`, обновить KDoc. Проверка:
  `make test` — все зелёные, формат повторов не изменился.

## 3. Строка списка главного экрана

- [x] 3.1 В `EntryListItemRow` ветвление по `item.reps != null`: повторы —
  `formatEntryValue(item.sets, item.reps.toString(), separator)`;
  длительность — `formatEntryValue(item.sets,
  formatDurationResource(item.durationSec ?: 0), separator)`;
  fallback `?: 0` сохранить (design Decision 1). Обновить KDoc компонента.
  Проверка: `make test` — зелёные.
- [x] 3.2 Обновить превью: `MainScreenPreview` — `previewTimeItem`
  (1×20 → «20 сек»); запись `durationSec = 60` (2 подхода) в
  `sectionsPreviewEntries` → «2 х 1 мин» (нулевые секунды опускаются —
  зафиксировать ожидание в комментарии); добавить составной пример
  «4 х 2 мин 30 сек».
  `EntryListItemRowPreview` — то же. Светлая + тёмная
  (`UI_MODE_NIGHT_YES`), `showBackground = true`, `locale = "ru"`.
  Проверка: проект компилируется, превью рендерятся без ошибок.

## 4. androidTest

- [x] 4.1 В `MainScreenTest.content_when_entries_then_shows_each_row_with_name_and_value`
  заменить захардкоженное `"${timeItem.durationSec}"` на сборку «20 сек»
  из строковых ресурсов (`entry_duration_seconds_short`) — design
  Decision 4. Запускать после 3.1 — тест зелёный только с новой
  реализацией строки. Проверка: `make android-test` — `MainScreenTest`
  зелёный.
- [x] 4.2 Убедиться, что `MainScreenEditNavigationTest` и остальные
  main-тесты не зависят от формата длительности (EditNavigationTest
  использует только REPS-запись — проверено при планировании). Проверка:
  `make android-test` — все main-тесты зелёные.

## 5. Финальная проверка

- [x] 5.1 Ручная проверка на эмуляторе (`make install`): записи
  4×10 → «4 х 10», 3×20 → «3 х 20 сек», 1×120 → «2 мин», 4×150 →
  «4 х 2 мин 30 сек», 1×3600 → «1 час» (spec «Формат значения записи»).
  Первые три случая проверены вручную на эмуляторе; сценарии «4 х 2 мин
  30 сек» и «1 час» — автоматизированы UI-тестом
  `MainScreenTest.content_when_duration_entries_then_shows_minutes_composite_and_hour_values`
  (реальные ресурсы на устройстве).
- [x] 5.2 Перед коммитом: `make format && make lint && make test`.
