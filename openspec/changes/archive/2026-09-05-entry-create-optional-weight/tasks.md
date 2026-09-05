## 1. ViewModel: вес (TDD)

- [x] 1.1 Добавить `weightText: String = "0"` в `EntryCreateUiState`
      (дефолт «0», `canSave` не меняется) — компилируемая база для
      красных тестов; `make test` без регрессий
- [x] 1.2 Красный JVM-тест санитайзинга веса в `EntryCreateViewModelTest`:
      `onWeightChange` таблицей случаев, результат — в
      `uiState.value.weightText` — буквы/минус отбрасываются, запятая
      нормализуется в точку, не более одной точки, ведущие нули кроме
      одиночного `«0»`/`«0.»` (`«0»→«0»`, `«00»→«0»`, `«012»→«12»`,
      `«000,5»→«0.5»`, `«0.»→«0.»`, `«0.0»→«0.0»`) — красная фаза:
      не компилируется, метода ещё нет
- [x] 1.3 Реализовать `onWeightChange` + private `sanitizeWeight` в
      `EntryCreateViewModel` (design Decision 2) — тест из 1.2 зелёный
- [x] 1.4 Красный JVM-тест сохранения веса (поле `weightText` — из 1.1,
      ввод — через `onWeightChange` из 1.3): дефолт `«0»`, `«0»` и
      нераспарсиваемый ввод → `weightKg = null`; `«12.5»` → `12.5`
      (design Decision 1); `canSave` не зависит от веса — ассерт падает
- [x] 1.5 Прокинуть вес в `save()` (`weightText.toDoubleOrNull()?.takeIf
      { it > 0.0 }`); в существующем тесте заменить сообщение ассерта на
      `"weightKg = null при дефолте «0» (design Decision 1)"` — тест
      из 1.4 зелёный, `make test` без регрессий

## 2. UI: поле веса и видимость «Подходов»

- [x] 2.1 Строковые ресурсы `entry_field_weight` (ru «Доп. вес (кг)»,
      en "Extra weight (kg)") — сборка
- [x] 2.2 `NumberEntryField` получает параметр `keyboardType` с дефолтом
      `Number`; поле веса вызывается с `KeyboardType.Decimal` (design
      Decision 3) — сборка, существующие вызовы не меняются
- [x] 2.3 `EntryCreateForm`: один блок `if (selectedExercise != null)` с
      «Подходами», `MeasuredByField` и «Доп. вес» (вес — сразу после
      типозависимого поля; design Decision 4, spec «Состав экрана
      создания записи»); `onWeightChange` в `EntryCreateScreenState`,
      `EntryCreateScreen` и механически `onWeightChange = {}` в
      `previewScreenState` (`EntryCreateScreenPreview`, main) и `stateFor`
      (`EntryCreateScreenTest`, androidTest) — `make test` без ошибок

## 3. UI-тесты и превью

- [x] 3.1 androidTest `EntryCreateScreenTest`: до выбора упражнения поля
      «Подходы»/«Повторы»/«Доп. вес» отсутствуют, после выбора — порядок
      REPS и TIME с «Доп. вес» (spec-сценарии) — `make android-test`
- [x] 3.2 androidTest поля веса: ввод десятичного значения обновляет
      state, дефолт «0» не блокирует кнопку — `make android-test`
- [x] 3.3 Обновить превью `EntryCreateScreenPreview` по правилам AGENTS.md:
      `showBackground = true, locale = "ru"`, обёртка
      `JetpackMyWorkoutsTheme`, светлая + тёмная темы
      (`UI_MODE_NIGHT_YES`), ключевые состояния рядом в Column —
      «упражнение не выбрано», «REPS без повторов» (существующее —
      сохранить, подсказка обязательности), «REPS с введённым весом»,
      «TIME с введённым весом» — сборка превью

## 4. Финальная проверка

- [x] 4.1 `make format && make lint && make test` — без ошибок и
      регрессий
- [x] 4.2 Ручная проверка на эмуляторе: Decimal-клавиатура с запятой,
      сохранение записи с весом и без (MCP-сценарий, скриншоты)
