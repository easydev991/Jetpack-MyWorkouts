# Proposal: Запрет выбора будущей даты и времени на экране создания записи

## Why

Сейчас пикеры даты и времени на экране создания/редактирования записи не
ограничены: пользователь может выбрать любой момент в будущем, хотя запись
тренировки по смыслу фиксирует уже произошедшее событие. Будущие записи
искажают статистику и историю тренировок. Максимально допустимый момент —
момент открытия экрана.

## What Changes

- Момент открытия экрана фиксируется один раз при создании ViewModel
  (существующий `nowProvider()`) и далее не пересчитывается.
- В `DatePicker` даты позже дня открытия экрана становятся невыделяемыми
  (`DatePickerState.selectableDates`).
- Если выбранная дата совпадает с днём открытия экрана, время позже момента
  открытия клэмпится на момент открытия (часы/минуты) — молча, без ошибки;
  для прошлых дат время любое. Клэмп срабатывает и при выборе времени, и
  при последующей смене даты на день открытия.
- Режим редактирования подчиняется тем же ограничениям; предзаполненная
  запись (всегда в прошлом) сохраняется без изменений — без клэмпа prefill
  (обоснование — design Decision 5).

## Capabilities

### New Capabilities

(нет)

### Modified Capabilities

- `workout-entry-screen`: добавляется требование — дата и время записи не
  могут быть в будущем относительно момента открытия экрана (блокировка дат
  в `DatePicker`, клэмп времени в ViewModel).

## Impact

- `app/src/main/java/com/myworkouts/ui/viewmodel/EntryCreateViewModel.kt` —
  фиксация момента открытия, клэмп в `onTimeChange` и `onDateChange`
  (дата = день открытия).
- `app/src/main/java/com/myworkouts/ui/screens/entry/EntryCreateFields.kt` —
  `DateField`: ограничение в `DatePickerState.selectableDates`, локальная
  дата открытия вычисляется из `openMomentMillis` на месте.
- `app/src/main/java/com/myworkouts/ui/state/EntryCreateUiState.kt` —
  новое поле `openMomentMillis` со значением по умолчанию (прецедент —
  `date: LocalDate = LocalDate.now()`); обоснование дефолта — design
  Decision 3.
- `app/src/main/java/com/myworkouts/ui/screens/entry/EntryCreateScreen.kt` —
  прокидывание `openMomentMillis` из uiState в `DateField` (через приватную
  `EntryCreateForm` в `EntryCreateScreen.kt`; публичная сигнатура
  `EntryCreateScreenState` неизменна).
- `app/src/main/java/com/myworkouts/ui/screens/entry/EntryCreateScreenPreview.kt` —
  `DateFieldPreview`: новый аргумент `DateField`; остальные превью без
  изменений.
- Тесты: `EntryCreateViewModelTest` (unit), `EntryCreateScreenTest`
  (androidTest); `EntryCreateFormattersTest` не затронут — код форматтеров
  не меняется.
- Новых зависимостей и строковых ресурсов не требуется (клэмп без
  пользовательских сообщений).
