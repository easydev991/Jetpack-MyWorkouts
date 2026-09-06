## 1. ViewModel: клэмп времени (unit, TDD)

- [ ] 1.1 Тест: клэмп при дате = день открытия экрана — время позже момента
  открытия заменяется на момент открытия: и в `onTimeChange` (spec-сценарий
  «Время на день открытия клэмпится на момент открытия»), и в
  `onDateChange` при возврате на день открытия с уже поздним временем
  (spec-сценарий «Возврат на день открытия с поздним временем клэмпится»;
  см. design Decision 4);
  запуск `./gradlew testDebugUnitTest` — тест падает (red). Файл:
  `app/src/test/java/com/myworkouts/ui/viewmodel/EntryCreateViewModelTest.kt`
- [ ] 1.2 Тест: `onTimeChange` при дате в прошлом сохраняет любое время
  (spec-сценарий «Прошлая дата допускает любое время») — red
- [ ] 1.3 Тест: момент открытия зафиксирован — после «протекания» времени
  `nowProvider` ограничение применяется к моменту открытия, а не к текущему
  `now` (spec-сценарий «Момент открытия не пересчитывается») — red
- [ ] 1.4 Тест: режим редактирования — `prefillState` не меняет дату/время
  записи из прошлого (spec-сценарий «Редактирование старой записи не меняет
  её дату и время») — red
- [ ] 1.5 Тест на границу дня в фиксированной не-UTC зоне (клэмп на 23:59
  и на 00:01 следующего дня открытия) — red; страхует UTC-ловушку из
  design Risks. Файл: `EntryCreateViewModelTest.kt`
- [ ] 1.6 Тест-страховка: `EntryCreateUiState()` собирается без ошибок с
  дефолтом `openMomentMillis` (страхует инвариант совместимости
  существующих вызовов конструктора, на который опираются Decision 5 и
  Impact) — red. Файл:
  `app/src/test/java/com/myworkouts/ui/viewmodel/EntryCreateViewModelTest.kt`
- [ ] 1.7 Реализация: новое поле `openMomentMillis` в
  `EntryCreateViewModel`, инициализируемое один раз в конструкторе из
  `nowProvider()`; клэмп в `onTimeChange` и `onDateChange` (дата = день
  открытия → min(выбранное, момент открытия)); одно поле `openMomentMillis`
  с дефолтом в `EntryCreateUiState` — локальная дата открытия вычисляется
  из него на месте (design Decision 3/5); запуск
  `./gradlew testDebugUnitTest` — тесты 1.1–1.6 зелёные (green)

## 2. UI: блокировка дат в пикере (TDD)

- [ ] 2.1 UI-тест (`EntryCreateScreenTest`, androidTest): даты позже дня
  открытия в `DatePicker` невыбираемы — тап по будущей дате не меняет поле
  (spec-сценарий «Даты позже дня открытия недоступны в пикере»); запуск
  `make android-test` — red. Файл: `app/src/androidTest/java/com/myworkouts/ui/screens/entry/EntryCreateScreenTest.kt`
- [ ] 2.2 Реализация: `DateField` получает `openMomentMillis` из
  `EntryCreateUiState` (через `EntryCreateForm`; сигнатура
  `EntryCreateScreenState` не меняется), локальную дату открытия вычисляет
  на месте, передаёт `selectableDates` в `DatePickerState` со сравнением
  через `localDateToPickerMillis(localDate)` (design Decision 2/3);
  запуск `make android-test` — green
- [ ] 2.3 Превью: обновить `DateFieldPreview` в
  `EntryCreateScreenPreview.kt` под новый аргумент `DateField`; остальные
  превью, хелпер `previewScreenState` и стаб `stateFor` в
  `EntryCreateScreenTest.kt` не меняются — сигнатура
  `EntryCreateScreenState` прежняя; проверить компиляцию двух source-set'ов
  (main и androidTest): `./gradlew compileDebugKotlin
  compileDebugAndroidTestKotlin` — unit-сет уже покрыт `testDebugUnitTest`
  в 1.7

## 3. Финализация

- [ ] 3.1 Прогнать `make format && make lint && make test` — без ошибок;
  при подключённом устройстве дополнительно `make android-test`
