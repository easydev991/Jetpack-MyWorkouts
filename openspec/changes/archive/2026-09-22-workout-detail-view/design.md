# Design

## Context

См. `proposal.md` — зачем. Текущее состояние главного экрана:

- Жесты строки живут в `EntryListItems` (`MainScreen.kt`):
  `pointerInput(item.id) { detectTapGestures(onLongPress = ...) }` — тап
  сейчас не обрабатывается вовсе, `detectTapGestures` уже умеет оба жеста
  одновременно.
- Выделение строки: `isSelected = { contextMenu?.item?.id == it.id }` в
  `EntryList`; строка (`EntryListItemRow`) уже несёт семантику `Selected`
  и custom a11y-действие «Открыть меню».
- Транзитное состояние меню — `var contextMenu by remember` в `MainScreen`,
  прокинуто через `MainScreenState`; VM-паттерн `pendingDeleteId` живёт
  в `MainUiState.Success` (удаление — доменная операция).
- Визуальный референс — `WorkoutDetailsPreview.kt`:
  `DetailsCompactDialogVariant` (карточка 28dp), `DetailsDemoScreen`
  (скрим 32% поверх экрана), хелперы `formatDetailDateTime`, `detailValueText`,
  `detailValueWithWeightText`, `DetailComment` — всё `private` в
  preview-файле.
- `BackHandler` — устоявшийся паттерн проекта (`EntryCreateScreen`,
  `ExerciseCreateScreen`).

## Goals / Non-Goals

**Goals:**

- Карточка деталей по тапу, визуально идентичная согласованному референсу.
- Выделение исходной строки без контекстного меню; снятие при закрытии.
- Ничего не сломать: long-press меню, a11y-действие «Открыть меню»,
  удаление, живое обновление списка.
- Минимальный дифф: VM и `MainUiState` не трогаем.

**Non-Goals:**

- Редактирование из карточки (кнопок действий в карточке нет).
- Аналитика события «открыты детали» (не запрошено; при необходимости —
  отдельный `UserActionType` позже).
- Диалог-подтверждений.

## Decisions

### Decision 1: Overlay поверх Scaffold, не dialog-destination

Карточка рендерится в `MainScreenContent` как overlay после `Scaffold`
(внешний `Box { Scaffold(...); if (...) Overlay }`) — как `DetailsDemoScreen`
в референсе.

Альтернатива — dialog destination в NavHost (`main/entry_details/{id}`):
Navigation Compose рендерит dialog-destination в **отдельном окне**
`Dialog` — список под ним пропадёт из виду, выделение строки и скрим
поверх списка станут невозможны. Это противоречит согласованному визуалу.
Плюс маршрут не нужен: детали не deep-linkable, Back закрывается
`BackHandler`.

Уровень «после Scaffold», а не «в `SuccessBody`»: скрим обязан накрывать
и FAB с `TopAppBar` (в референсе затемнён весь экран), иначе FAB остаётся
кликабельным при открытых деталях. `EntryDeleteDialog` при этом остаётся
в `SuccessBody` — он модальный `AlertDialog` и перекрывает overlay собой;
одновременно открыты быть не могут (скрим блокирует список, а алерт
открывается только из меню списка).

### Decision 2: Транзитное состояние в MainScreen, не в MainUiState

`var detailsEntryId by rememberSaveable { mutableStateOf<Long?>(null) }`
в `MainScreen`, прокидывается через `MainScreenState` (новые поля
`detailsEntryId`, `onEntryTap`, `onCloseDetails`) — паттерн `contextMenu`.

Альтернатива — поле в `MainUiState.Success` по образцу `pendingDeleteId`.
Отвергнуто: `pendingDeleteId` живёт в VM, потому что удаление — доменная
операция с подтверждением; просмотр деталей — чисто UI-состояние, VM
не участвует, данные не запрашиваются. Бонус `rememberSaveable`: переживает
поворот без единой строки в VM (у `contextMenu` — обычный
`remember`, меню после пересоздания гаснет; для деталей сохранение
желательно — «выделение корректно ведёт себя при реконфигурации»).

### Decision 3: Авто-закрытие и актуальность — lookup по id на рендере

Карточка ищется `success.entries.firstOrNull { it.id == detailsEntryId }`
— ровно паттерн `pendingDeleteId` в `SuccessBody`, только на UI-стороне:

- запись удалена, пока открыты детали → `firstOrNull` даёт `null` →
  карточка не рендерится (спека: «закрывается автоматически»); осиротевший
  `detailsEntryId` безвреден, при следующем открытии перезаписывается;
- запись изменена → Room-Flow эмитит свежий `entries` → карточка
  перерисовывается актуальными данными, выделение строки сохраняется
  (сравнение по id).

### Decision 4: onTap в существующий detectTapGestures + второе a11y-действие

В `pointerInput(item.id) { detectTapGestures(onLongPress = ..., onTap = ...) }`
— оба жеста в одном детекторе, long-press имеет приоритет, ничего
существующего не меняется. Тап прокидывается через `EntryListCallbacks`
(`onItemTap: (EntryListItem) -> Unit`), `isSelected`-лямбда расширяется:
`contextMenu?.item?.id == it.id || detailsEntryId == it.id`.

`Modifier.clickable` не используем: у него собственный long-press, конфликт
с существующим `pointerInput` и слом a11y-паттерна строки.

Accessibility: TalkBack-пользователь не может выполнить «обычный тап» по
строке без кликабельной семантики → добавляем второе
`CustomAccessibilityAction` «Показать детали» (`a11y_show_details`, ru/en)
рядом с существующим «Открыть меню» — тот же паттерн, что уже принят
в проекте (accessibility-semantics-fixes). `EntryListItemRow` получает
параметр `onOpenDetails`.

Имена колбэков — зеркала пар своего слоя: `onItemTap` ↔
`onItemLongPress`, `onOpenDetails` ↔ `onOpenMenu`, `onEntryTap`/
`onEntryOpenDetails`/`onCloseDetails` — сеттеры `detailsEntryId`.
Жест и a11y-действие идут через разные лямбды: Material3 DropdownMenu
тап по умолчанию **не** поглощает, поэтому сеттер `onEntryTap`
различает «меню было открыто» (гасит только меню, карточку открывает
повторный тап), а `onEntryOpenDetails` — a11y-канал мимо popup,
всегда закрывающий меню и открывающий карточку. Выделение у обоих
жестов — один OR-источник в `isSelected` (задача 3.4).

### Decision 5: Вынос компонента и форматтера

- `ui/screens/main/EntryDetailsCard.kt` — продакшн-компонент:
  `EntryDetailsCard(item, onClose)` (бывший `DetailsCompactDialogVariant`),
  плюс `DetailComment`. Скрим + позиционирование (центр; safe-insets —
  панель навигации/cutout по бокам, статус-бар/жест-навигация сверху и
  снизу — плюс 12dp вертикально и 24dp по бокам) —
  в overlay-обёртке `MainScreenContent` (вынесена отдельной функцией ради
  detekt `LongMethod: 60`). Ширина карточки ≤ 350dp (иначе
  `fillMaxWidth` контента растягивает её на весь экран в ландшафте);
  блок комментария ≤ 200dp, более длинный текст скроллится внутри.
- Формат «значение + вес» — чистая JVM-функция в `EntryValueFormatter.kt`
  (паттерн `formatEntryValue`: локализованные строки — параметрами):
  `formatEntryDetailsValue(sets, value, separator, weightSuffix: String?)` →
  «4 х 10 (+ 5 кг)»; composable-обёртка строит суффикс через `stringResource`
  при `weightKg > 0` (или передаёт `null`). `value` — пред-локализованная
  строка (паттерн `formatEntryValue`: повторы или «20 сек» — форматтер
  оперирует только склейкой).
  Аналогично `formatDetailDateTime(millis, locale, zoneId)` — чистая
  функция (`DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm")`); локаль и
  зона — необязательные параметры с дефолтом текущих (тот же паттерн
  «локализованное — параметром»: детерминированный JVM-тест без мутации
  process defaults). Формат значения — только JVM-тесты (TDD):
  androidTest формата не пишем — нерационально тратить UI-тесты
  на склейку строк.
- `WorkoutDetailsPreview.kt`: вариант-карточка и дублирующие хелперы
  удаляются (референс выполнил задачу — визуал согласован), превью
  «карточка поверх главного экрана» переезжает в `EntryDetailsCardPreview.kt`
  как превью настоящего компонента: светлая + тёмная тема, 3 превью по
  демо-моделям (все поля / без комментария / на время; «без веса» —
  частный случай «на время») + превью «карточка поверх списка со
  скримом» — см. tasks.md 2.2.

### Decision 6: Закрытие и строки

- Крестик — `IconButton`, `contentDescription` = существующий
  `entry_confirm_close` («Закрыть»/"Close") — реюз, новых строк нет.
- Скрим — `Modifier.clickable` (без ripple-индикации на затемнении)
  → `onCloseDetails`.
- Системный Back — `BackHandler(enabled = detailsEntryId != null)`
  (паттерн `EntryCreateScreen`).
- Строки — по канону задачи 1.1 (новый `a11y_show_details`, остальные
  ресурсы — реюз).

### Decision 7: Плавное появление/закрытие overlay

`DetailsOverlay` оборачивает скрим и карточку в `AnimatedVisibility`:
скрим — `fadeIn`/`fadeOut`, карточка —
`slideInVertically + fadeIn` / `slideOutVertically + fadeOut`.
В референсе анимаций нет — намеренное презентационное отступление:
без exit-анимации авто-закрытие из Decision 3 (запись удалена, пока
открыты детали) и ручное закрытие (крестик/скрим/Back) выглядят рывком.
Сценарии спеки анимация не затрагивает.

Два сопутствующих решения реализации:

- во время exit-анимации `item` уже `null`, а карточка ещё уезжает —
  контент берётся из последней непустой записи (`remember`-поле
  `lastItem`), иначе карточка гаснет рывком посреди анимации;
- `AnimatedVisibility` компонуется всегда, видимостью управляет
  `visible` — если создать его в момент, когда `visible` уже `true`
  (первое открытие после старта/поворота), enter-анимация не выполнится
  и карточка появится рывком.

## Risks / Trade-offs

- [Detekt `LongMethod` в `MainScreenContent` — overlay добавит строки] →
  overlay в отдельной private composable-функции, как уже сделано в файле
  (`SuccessBody`, `EntryList`).
- [Скрим перекрывает FAB/TopAppBar — FAB недоступен при открытых деталях] →
  это соответствует референсу; Back/крестик/скрим доступны.
- [`rememberSaveable` хранит id после «авто-закрытия» из Decision 3] →
  безвредно: карточка не рендерится, id перезаписывается следующим тапом.
  Явная очистка не добавляется (лишнее состояние).
- [Одновременное выделение из меню и деталей] → скрим блокирует список,
  меню открывается только из списка; тап по строке при открытом меню
  закрывает только меню, карточку открывает повторный тап — Material3
  DropdownMenu тап сам не поглощает (гасит меню лишь через
  `onDismissRequest`), поэтому сеттер `onEntryTap` проверяет «меню было
  открыто». A11y-действие идёт мимо popup — отдельный сеттер
  `onEntryOpenDetails` всегда гасит `contextMenu` и открывает карточку,
  иначе при открытых деталях обе строки выделены.

## Migration Plan

Чисто аддитивный UI-слой: БД, репозиторий, use-cases, VM не меняются.
Откат — revert одного коммита.
