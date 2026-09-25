# Tasks

## 1. Домен: расчёт статистики по подходам (TDD)

- [ ] 1.1 Тесты `ComputeCategoryStatsTest`: TIME-категория попадает в
  `categoryTotals` и `exerciseTotals`; доли и сортировка по сумме
  подходов (REPS + TIME в одной категории); `totalReps` — сумма
  «подходы × повторы» только REPS; `totalDurationSec` — сумма
  «подходы × длительность» только TIME; смешанная категория несёт оба
  значения. Прогон: `make test` (новые тесты красные)
- [ ] 1.2 Реализация `ComputeCategoryStats`: `CategoryTotal`/`ExerciseTotal`
  получают `totalSets`/`totalReps`/`totalDurationSec`, расчёт по всем
  записям периода, сортировка по подходам убывание → имя. Прогон:
  `make test` (зелёный)

## 2. Пустое состояние (TDD)

- [ ] 2.1 Тест `OverviewViewModelTest`: только-TIME записи за период →
  `isEmpty = false`, категория в донате; нет записей любого типа →
  `isEmpty = true`. Прогон: `make test` (красный)
- [ ] 2.2 Реализация: `isEmpty` в `OverviewViewModel` по наличию записей
  периода (оба типа измерения). Прогон: `make test` (зелёный)

## 3. Формат значения легенды (TDD)

- [ ] 3.1 Тесты компактного форматтера (`ui/util`): пороги единиц —
  59 сек → «59 сек.», 60 сек → «1 мин.», 90 сек → «2 мин.», 59 мин →
  «59 мин.», 60 мин → «1 ч.», 23 ч 59 мин → «24 ч.», 24 ч → «1 дн.»
  (чистая функция с параметрами-строками). Прогон: `make test` (красный)
- [ ] 3.2 Реализация `formatStatsDurationCompact` в
  `ui/util/DurationFormatter.kt` + `@Composable`-обёртка; строковые
  ресурсы единиц (сек./мин./ч./дн. + разделитель « · ») в `values/` (ru) и
  `values-en/` (en). Прогон: `make test` (зелёный)
- [ ] 3.3 Тест сборки текста значения легенды (чистый хелпер выбора
  состава «N повт.» / «M <ед.>» / «N повт. · M <ед.>»). Прогон:
  `make test` (красный)
- [ ] 3.4 Реализация хелпера значения легенды. Прогон: `make test`
  (зелёный)

## 4. UI: донат и легенда

- [ ] 4.1 `StatsScreen`: `donutSlices` по `totalSets`, `StatsLegendRow`
  хранит готовый текст значения, `legendItems` использует хелпер
  значения и компактную длительность; превью `demoCategoryTotals`/
  `demoExerciseTotals` дополняются TIME- и смешанной категориями
  (демо-записи через `previewEntry`), светлые+тёмные превью по конвенциям.
  Проверка: сборка `make lint` и инспекция превью
- [ ] 4.2 UI-тесты `StatsScreenTest`: TIME-категория отображается в
  легенде с текстом длительности; тап по ней открывает drill-down со
  списком записей. Прогон точечно:
  `./gradlew :app:connectedGithubDebugAndroidTest --console=plain -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.overview.StatsScreenTest'`
  (нужен эмулятор; зелёный после 4.1 — UI-тесты по факту, без TDD)

## 5. Верификация

- [ ] 5.1 Полный прогон: `make format && make lint && make test`;
  вкладки «Рекорды» и «Активность» без изменений в коде и тестах
- [ ] 5.2 Полный androidTest на эмуляторе: `make android-test`
  (флейвор github по умолчанию) — регрессий нет
