## 1. Формат веса (TDD)

- [x] 1.1 Добавить в `EntryValueFormatterTest` JVM-тесты `formatWeightKg`: целое 5.0 → «5», дробное 2.5 → «2.5», 12.25 → «12.25»; убедиться, что они падают (функции ещё нет)
- [x] 1.2 Реализовать `formatWeightKg(weightKg: Double): String` в `EntryValueFormatter.kt` (design Decision 3) и убедиться, что тесты 1.1 зелёные (`make test`)

## 2. Слой данных: weightKg в проекции (TDD)

- [x] 2.1 Добавить в `MappersTest` JVM-тесты `toListItem`: `weightKg = 5.0` и `weightKg = null` маппятся в `EntryListItem.weightKg`, `weightKg = 0.0` проходит без искажений (0 — данные, не `null`); убедиться, что тесты падают
- [x] 2.2 Добавить androidTest в `RoomWorkoutDataRepositoryTest`: записи с `weightKg = 5.0` и `weightKg = null` отдаются потоком `entries` с сохранением веса; убедиться, что тест падает
- [x] 2.3 Реализовать: `weightKg: Double?` без дефолта в `EntryListItemEntity` (Room заполняет из SELECT, design Decision 5), `e.weight_kg AS weightKg` в SELECT `observeEntries`, `weightKg: Double? = null` в `EntryListItem`, проброс в `toListItem()` (design Decisions 1, 5); убедиться, что тесты 2.1–2.2 зелёные (`make test`, `make android-test`)

## 3. Строковые ресурсы

- [x] 3.1 Добавить `entry_list_weight_label` («Дополнительный вес»/«Extra weight») и `entry_list_weight_value` («%1$s кг»/«%1$s kg») в `values/strings.xml` и `values-en/strings.xml`; проверить сборку `make lint`

## 4. UI: строка веса в `EntryListItemRow` (TDD)

- [x] 4.1 Добавить в `MainScreenTest` androidTest `row_when_weight_then_shows_weight_line` (spec «Дополнительный вес в строке записи»): запись с весом 5.0 показывает «Дополнительный вес» и «5 кг»; убедиться, что тест падает
- [x] 4.2 Добавить в `MainScreenTest` androidTest `row_when_no_weight_then_hides_weight_line`: записи с `weightKg = null` и `weightKg = 0.0` не показывают строку веса; убедиться, что тест падает
- [x] 4.3 Переделать `EntryListItemRow` в `Column` из двух `Row` (design Decision 4): фон/padding/semantics — на `Column`, тексты верхнего `Row` (название и значение) — `fontWeight = FontWeight.Medium`, второй `Row` с меткой и значением («5 кг» через `entry_list_weight_value` + `formatWeightKg`) только при `weightKg > 0.0`; убедиться, что тесты 4.1–4.2 зелёные

## 5. Превью и финальная проверка

- [x] 5.1 Создать новый файл `EntryListItemRowPreview.kt` рядом с `EntryListItemRow.kt` с превью компонента (конвенция: светлая + тёмная темы, `showBackground`, `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`): строки с целым весом (5.0 → «5 кг»), дробным (2.5 → «2.5 кг») и без веса; имена функций не конфликтуют с существующим `EntryListItemRowPreview` из `MainScreenPreview.kt`. `MainScreenPreview.kt` не трогать — существующие превью остаются как есть; проверить визуально в превью Android Studio
- [x] 5.2 Прогнать `make format && make lint && make test` и `make android-test` — всё зелёное; ручная проверка на эмуляторе: запись без веса выглядит как раньше
