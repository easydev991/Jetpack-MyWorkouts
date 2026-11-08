# Proposal

## Why

Детали записи сейчас показываются всплывающей карточкой (`DetailsOverlay` +
`EntryDetailsCard`) — кастомным overlay'ем вне навигации, с compose-локальным
состоянием и собственным `BackHandler`. Из карточки нельзя ничего сделать с
записью: «Изменить» и «Удалить» доступны только через long-press меню, просмотр
не попадает в breadcrumbs-аналитику (экрана в `AppScreen` нет). Тап по строке —
естественное место просмотра записи; отдельный детальный экран (по образцу
JetpackDays `item_detail/{itemId}`) даёт полноценную навигационную дестинацию
с `TopAppBar`-действиями «Изменить»/«Удалить». Компоновка выбрана владельцем —
вариант A2 из черновика `EntryDetailScreenDrafts.kt` (контент по центру без
Card-фона); черновик — временный файл и перерабатывается в боевой экран при
реализации.

## What Changes

- Обычный тап по строке и новый пункт «Открыть» контекстного меню (теперь
  три пункта: «Открыть», «Изменить», «Удалить») открывают отдельный детальный
  экран записи — навигационную дестинацию `entry_detail/{entryId}`
  (`Screen.EntryDetail.createRoute(entryId)`, обязательный path-аргумент).
- Состав экрана: `TopAppBar` «Детали записи» со стрелкой назад и actions
  «Изменить»/«Удалить»; контент по центру свободного места БЕЗ Card-фона —
  иконка упражнения, название (`titleLarge`), значение (`headlineMedium`,
  `primary`), дата/время (`bodySmall`, `onSurfaceVariant`), комментарий в
  тональном блоке со внутренним скроллом при переполнении. Нижняя навигация
  на экране скрыта.
- Состояния экрана — sealed `Loading`/`Success`/`Error`; запись с
  несуществующим id → заглушка «Запись не найдена» (осознанно не как в
  JetpackDays, где `filterNotNull()` зависает в вечном Loading).
- Данные — реактивный поток записи по id из слоя данных: экран актуализируется
  при изменении записи, в том числе после возврата с экрана редактирования
  с успешным сохранением (parity с нынешним попапом, тест
  `card_when_entry_updated_then_data_refreshed_and_selection_preserved`).
- «Удалить» → сначала `AlertDialog` подтверждения (стиль и строки текущего
  `EntryDeleteDialog`); удаление — только после подтверждения, затем
  `popBackStack()` на главный экран (без snackbar); отмена — остаёмся на
  экране.
- «Изменить» → существующий экран записи в режиме редактирования
  (`Screen.EntryCreate.createRoute(entryId)`) поверх детального экрана;
  все возвраты — стандартным стеком: отмена → детальный экран без изменений,
  успешное сохранение → детальный экран с актуальными данными. Редактирование
  с главного экрана не меняется.
- Возврат с детального экрана — стрелка `TopAppBar`, кнопка Back, жест свайпа
  — один и тот же `popBackStack()`; кастомный `BackHandler` на экране не
  добавляется.
- A11y-действие строки «Показать детали» сохраняется и перецеливается на
  открытие детального экрана; `Selected`-выделение строки остаётся только у
  контекстного меню.
- Аналитика: `ScreenView` экрана (`AppScreen.EntryDetailScreen` через
  `screenForRoute`), новое `UserActionType.OPEN_ENTRY_DETAIL` при открытии
  (тап, пункт меню или a11y-действие), реюз `EDIT` (переход на редактирование с деталей)
  и `DELETE` (подтверждение удаления).
- Удаляются `DetailsOverlay`, `EntryDetailsCard` (+ превью) и их требования
  в спеке главного экрана; черновик-файл перерабатывается в боевой экран
  (подготовка ветки уже выполнена: тест-рендер `EntryDetailDraftsScreenshotTest`
  удалён до реализации, невыбранные варианты A, B, C срезаны).
- Новые строки — локализация ru/en (ru — основной).

## Capabilities

### New Capabilities

- `workout-entry-detail-screen`: детальный экран записи — пути открытия (тап,
  пункт «Открыть», a11y-действие), состав и компоновка, состояния
  (`Loading`/`Success`/`Error`, несуществующий id → заглушка), актуализация
  данных, удаление с алертом и возвратом на главный экран, переход на
  редактирование и стек возвратов, скрытие нижней навигации, безопасные
  зоны, accessibility, аналитика.

### Modified Capabilities

- `workouts-main-screen`: REMOVED — «Детали записи по тапу на строку» и
  «Содержимое карточки деталей записи» (попап заменён отдельным экраном);
  MODIFIED — «Удаление записи длинным нажатием»: контекстное меню получает
  третий пункт «Открыть» (выше «Изменить»), открывающий детальный экран.
- `non-tab-screen-navigation`: MODIFIED — «Screen sealed class holds only
  route» (перечень зарегистрированных дестинаций дополняется
  `Screen.EntryDetail` и синхронизируется с уже требуемыми базовой спекой
  маршрутами обзора `Screen.Overview`/`Screen.OverviewSettings`, которые
  в сценарии «Registered destinations use Screen routes» не были
  перечислены); ADDED — требование для объекта `Screen.EntryDetail`
  (path-аргумент `entryId`, `createRoute`, регистрация в `NavHost`,
  скрытие нижнего бара, маппинг аналитики).

Проверены без изменений (не затрагиваются): `tab-navigation` (контракт
`shouldShowBottomBar`, скрывающий бар на не-таб маршрутах, живёт в спеке
`non-tab-screen-navigation` — сценарии «Bottom navigation скрыта на …»),
`workout-entry-screen`
(«возврат на предыдущий экран» уже описывает оба стека), `launcher-shortcut`
(шорткат не создаёт детальный экран в стеке).

## Impact

- `navigation/Screen.kt` — объект `Screen.EntryDetail` (`entry_detail/{entryId}`).
- `ui/screens/root/RootScreen.kt` — дестинация `EntryDetailNavDestination`,
  маппинг `screenForRoute`; `analytics/AnalyticsEvent.kt` —
  `AppScreen.EntryDetailScreen`, `UserActionType.OPEN_ENTRY_DETAIL`.
- `ui/screens/entrydetail/` — боевой экран (переработка черновика A2),
  `EntryDetailViewModel`, `EntryDetailUiState` (`ui/state/`).
- `domain/repository/WorkoutDataRepository.kt` + `data/repository/RoomWorkoutDataRepository.kt`
  - DAO — реактивный поток записи по id (`entryById(id): Flow<EntryListItem?>`);
  обновление всех Fake-реализаций порта в тестах (механический компиляторный фикс).
- `ui/screens/main/MainScreen.kt` — удаление `DetailsOverlay`, транзитного
  `detailsEntryId`, `BackHandler`; тап и «Открыть» → навигация; выделение
  строки — только от меню; `ui/screens/main/EntryDetailsCard.kt` (+ превью)
  удаляются; `EntryListItemRow` — перецеливание a11y-действия; алерт удаления
  выносится в общий компонент для реюза двумя экранами.
- Строковые ресурсы `values/strings.xml`, `values-en/strings.xml`.
- Тесты: переписываются `MainScreenDetailCardTest` (12 тестов карточки),
  `MainScreenDetailCardRecreateTest`, пункты меню в `MainScreenTest`; новый
  unit `EntryDetailViewModelTest` и новые UI-тесты экрана и навигации;
  `MainViewModelTest` — только фиксы Fake-порта.
