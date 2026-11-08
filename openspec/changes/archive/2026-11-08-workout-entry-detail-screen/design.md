# Design

## Context

Мотивация — в `proposal.md`. Текущее состояние (факты сверены с файлами,
08.11.2026):

- Попап — `DetailsOverlay` (`ui/screens/main/MainScreen.kt:300–358`, скрим 32%
  - карточка по центру), визуал — `EntryDetailsCard.kt` (Card 28dp, ≤350dp).
  Состояние — compose-локальное `var detailsEntryId by rememberSaveable`
  (`MainScreen.kt:111`) + `BackHandler` (`:112`); данные —
  `success.entries.firstOrNull { it.id == detailsEntryId }` (`:276–283`).
  В `MainUiState` и навигации попап не участвует.
- Жесты: `EntryListItems` (`MainScreen.kt:510–582`) — `detectTapGestures`:
  `onTap` → `onEntryTap`, `onLongPress` → `EntryContextMenu`
  (`:591–619`, пункты «Изменить»/«Удалить»). A11y-действие «Показать детали»
  (`a11y_show_details`) — `EntryListItemRow.kt:128–138`; `Selected`-семантика
  строки (`:117–123`) сейчас OR-источник «меню ИЛИ детали»
  (`MainScreen.kt:468–471`).
- Редактирование: меню → `onEditEntryClick` → `NavDestinations.kt:50–52` →
  `navigate(Screen.EntryCreate.createRoute(entryId))`. Маршрут
  `Screen.EntryCreate` = `entry_create?entryId={entryId}`, `NO_ENTRY_ID = -1L`
  (`navigation/Screen.kt:81–103`); принимающая сторона —
  `EntryCreateNavDestination` (`RootScreen.kt:268–287`): `onBackClick =
  popBackStack`, сохранение закрывает экран через
  `viewModel.save(onSaved = onBackClick)` (`EntryCreateScreen.kt:72`) —
  стековые возвраты работают без правок.
- Удаление: `requestDelete` → `pendingDeleteId` в `MainUiState` → алерт
  `EntryDeleteDialog` (`MainScreen.kt:630–659`: заголовок
  `entry_list_delete_dialog_title`, текст с названием, confirm в
  `colorScheme.error`, dismiss «Отмена») → `confirmDelete` →
  `repository.deleteEntry(id)` (`WorkoutDataRepository.kt:104`); при ошибке —
  `crashReporter.logException`, pending остаётся. Use-case удаления нет.
- `MainViewModel`: `combine(pendingDeleteId, repository.entries())` →
  `stateIn(WhileSubscribed(...), Loading)`.
- Навигация: sealed `Screen` + `BottomTabs`; `shouldShowBottomBar =
  currentRoute in tabs.map { it.route }` (`RootScreen.kt:107`) — новый не-таб
  маршрут скрыт баром автоматически. `screenForRoute` (`:224–239`) маппит
  маршруты в `AppScreen` для ScreenView-логгера (`DisposableEffect`, `:81–95`).
- Edge-to-edge: `enableEdgeToEdge()` (`MainActivity.kt:57`); паттерн не-таб
  экранов — `Scaffold` + `TopAppBar`, контент через `paddingValues`
  (`EntryCreateScreen.kt:120–159`).
- Данные: все списки репозитория — реактивные `Flow` (`entries()`,
  `categories()`, …); `getEntry(id)` — разовый снимок; observe-by-id нет.
  `EntryListItem` — полная проекция для деталей (название, иконка, значение,
  вес, комментарий, дата); JOIN-запрос — `ExerciseEntryDao.observeEntryRows`.
- Референс JetpackDays (`com.dayscounter`): `Screen.ItemDetail` =
  `item_detail/{itemId}` + `createRoute(itemId: Long)` (`Screen.kt:34–39`);
  дестинация (`RootScreenComponents.kt:108–145`) — `navArgument("itemId")
  { NavType.LongType }`, `onBackClick = popBackStack`, `onEditClick =
  navigate(EditItem.createRoute(id))` + лог `EDIT`; `DetailScreen.kt` —
  Scaffold + `DetailTopAppBar` (назад → Edit → Delete, actions только в
  `Success` — `DetailAppBar.kt:26–85`), алерт (`:196–225`) с текстом названия,
  красной confirm и dismiss «Отмена»; `DetailScreenViewModel` —
  `savedStateHandle["itemId"]` checkNotNull, sealed
  `Loading`/`Success`/`Error`, НО `getItemFlow(id).filterNotNull()` при
  несуществующем id зависает в вечном Loading — осознанно не копируем.
- Черновики: `EntryDetailScreenDrafts.kt` — выбранный вариант A2
  `EntryDetailDraftCenterNoCard` (скриншоты
  `app/build/entry-detail-drafts/draft-A2-center-no-card-{light,dark}.png`);
  строки захардкожены; форматы значения/даты уже реализованы как чистые
  функции в `EntryValueFormatter.kt` (`formatEntryDetailsValue`,
  `formatDetailDateTime` — change workout-detail-view).

## Goals / Non-Goals

**Goals:**

- Детальный экран как навигационная дестинация `entry_detail/{entryId}` с
  реактивными данными и действиями «Изменить»/«Удалить».
- Полный снос попап-механизма (`DetailsOverlay`, `EntryDetailsCard`,
  `detailsEntryId`, его `BackHandler`) и переработка черновика A2 в боевой
  экран.
- Реюз существующих механизмов: алерт удаления, форматы значения/даты,
  стековые возвраты `EntryCreate`, breadcrumbs-логгер.

**Non-Goals:**

- Predictive back НЕ включается: `android:enableOnBackInvokedCallback`
  в манифесте нет и не добавляется в рамках задачи.
- Кастомный `BackHandler` на детальном экране (единственный легитимный
  BackHandler в связке — существующий на `EntryCreate` для
  несохранённых изменений, его не трогаем).
- Snackbar/уведомление после удаления (как в JetpackDays — без него).
- Изменения экрана записи (`EntryCreate`) и редактирования с главного экрана
  (отмена/сохранение → главный — как сейчас).
- Deep-link'и на `entry_detail` (офлайн-приложение, deep-link
  инфраструктуры нет).
- Изменения `BottomTabs`/таб-навигации, use-case удаления (прямая работа с
  портом — как в `MainViewModel`).

## Decisions

### Decision 1: Обязательный path-аргумент, не query-параметр

`Screen.EntryDetail` = `entry_detail/{entryId}` + `createRoute(entryId: Long)`
- `arguments = listOf(navArgument(ARG_ENTRY_ID) { type = NavType.LongType })`
— без `defaultValue`. Query-паттерн `EntryCreate` отвергнут: там
опциональность нужна для create-режима (`NO_ENTRY_ID = -1L`); у деталей id
существует всегда, путь без аргумента не должен матчиться (паттерн JetpackDays
`Screen.ItemDetail`). `entryId` читается дестинацией из
`backStackEntry.arguments` и передаётся в фабрику VM (паттерн
`EntryCreateNavDestination`); `SavedStateHandle` в VM не используется — id
неизменяем за жизнь дестинации, `rememberSaveable` не нужен.

### Decision 2: Реактивный observe-by-id в порту (не перечитывание)

Новый метод порта `WorkoutDataRepository.entryById(id: Long):
Flow<EntryListItem?>`: реализация — DAO-запрос `observeEntryRowById` (тот же
широкий JOIN-проекция, что `observeEntryRows`, с `WHERE id = :id`), маппинг
`toListItem()`; несуществующий id → поток эмитит `null`.

- `EntryDetailViewModel.uiState = entryById(entryId).map { it?.let(
  EntryDetailUiState::Success) ?: EntryDetailUiState.Error } .stateIn(
  viewModelScope, WhileSubscribed(5000), Loading)` — конвенция AGENTS для
  холодных потоков репозитория.
- Альтернатива «перечитывать `getEntry` при возврате» отвергнута: требует
  lifecycle-хука (ON_RESUME) на экране, не ловит удаление записи, пока экран
  открыт, и ломает реактивную конвенцию репозитория (все списки — Flow).
- Альтернатива «подписаться на `entries()` и искать `firstOrNull`»
  отвергнута: на экране одной записи тянет весь JOIN-список.
- `EntryListItem` переиспользуется как модель Success — проекция уже содержит
  всё, что нужно экрану; отдельная domain-модель не вводится.
- Влияние: новый метод порта требует дополнения всех Fake-реализаций
  `WorkoutDataRepository` в тестах — механический компиляторный фикс.

### Decision 3: Sealed Loading/Success/Error, Error — object

`EntryDetailUiState` — sealed `Loading` / `Success(data: EntryListItem)` /
`Error` (data object): взаимоисключающие визуальные состояния экрана (конвенция
AGENTS). `Error` без поля сообщения — заглушка с фиксированной строкой
«Запись не найдена» (`entry_detail_not_found`), отличной от вечного Loading
JetpackDays.

### Decision 4: Удаление — Boolean-пендинг в VM, навигация колбэком

`EntryDetailViewModel`: `requestDelete()` ставит `pendingDelete:
StateFlow<Boolean>` (запись на экране одна — id зафиксирован, Boolean вместо
id-пендинга `MainViewModel`); `confirmDelete(onDeleted: () -> Unit)` — лог
`DELETE` на вызове операции (канон — D10 «Точки эмиссии user_action»
архивного change firebase-crashlytics-mvp), `repository.deleteEntry(id)`,
onSuccess → `onDeleted()` (дестинация передаёт `popBackStack` — паттерн
`EntryCreateViewModel.save(onSaved)`), onFailure →
`crashReporter.logException("Удаление записи")`, алерт остаётся открыт
(паттерн `MainViewModel.confirmDelete` — провал не виден в UI, сбой доходит
до телеметрии); `cancelDelete()` снимает пендинг. Алерт показывается по
`uiState is Success` — из Error удалять нечего, actions скрыты (Decision 6).

### Decision 5: Алерт удаления — вынос в общий компонент

`EntryDeleteDialog` выносится из `MainScreen.kt` в
`ui/components/EntryDeleteDialog.kt` (internal, без изменений стиля и строк)
и используется обоими экранами: главный — как сейчас, детальный — при
подтверждении. Прямое дублирование composable в двух файлах отвергнуто:
владелец зафиксировал «переиспользовать стиль и строки текущего
EntryDeleteDialog» — вынос даёт буквальный реюз одного компонента.

### Decision 6: Экран по паттерну не-таб экранов

`EntryDetailScreen` — `Scaffold` + `TopAppBar` (паттерн `EntryCreateScreen.kt:120`),
контент через `paddingValues` — insets учитываются самим экраном (статус-бар
— `TopAppBar`, панель навигации/cutout — `Scaffold`); кастомные
inset-схемы главного экрана (`safeDrawing.only(Horizontal)`) не переносятся.
Actions «Изменить»/«Удалить» рендерятся только в `Success` (паттерн
JetpackDays `DetailAppBar`); стрелка назад — `popBackStack` из дестинации.
BackHandler на экране нет (Non-Goal). Компоновка контента — перенос варианта
A2 (`EntryDetailDraftCenterNoCard`) с правкой владельца после реализации:
колонка `fillMaxWidth` с горизонтальным паддингом 24dp по центру `Box`
(исходный cap 350dp снят владельцем вместе с капом высоты комментария),
иконка/название `titleLarge`/значение `headlineMedium` `primary`/дата
`bodySmall` `onSurfaceVariant`/комментарий в `surfaceVariant`-блоке (12dp, без фикс-капа:
растёт по содержимому, при переполнении доступного места — внутренний скролл; правка владельца после реализации). Форматирование значения — `formatEntryDetailsValue`,
даты — `formatDetailDateTime` (уже в `EntryValueFormatter.kt`);
`entryDetailsValueText` и `DetailComment` переезжают из удаляемого
`EntryDetailsCard.kt` в боевой экран.

### Decision 7: Главный экран — навигация вместо попапа

Из `MainScreen` удаляются `detailsEntryId`, `BackHandler`, `onCloseDetails`,
поле `MainScreenState.detailsEntryId`, overlay и параметр `detailsEntryId`
в `EntryList`; `isSelected` — только `contextMenu?.item?.id == it.id`
(выделение от деталей не имеет смысла: на детальном экране строка не видна —
решение владельца по a11y-вопросу). Единый канал «открыть детали»:
`onEntryTap` (тап), пункт меню «Открыть» и a11y-действие
«Показать детали» (`onEntryOpenDetails`) → лог `OPEN_ENTRY_DETAIL` +
`onOpenEntryClick(item.id)` → `navigate(Screen.EntryDetail.createRoute(id))`
из `NavDestinations.kt`. Сохраняется существующее различение тапа и a11y при
открытом меню (workout-detail-view design): тап гасит только меню
(Material3 DropdownMenu тап не поглощает), a11y идёт мимо popup и всегда
открывает экран. Пункт «Открыть» добавляется выше «Изменить» с ведущей
иконкой (`contentDescription = null`, паттерн пунктов меню; конкретная иконка
— на этапе реализации).

### Decision 8: Возвраты — только стандартный стек (зафиксировано владельцем)

Никаких кастомных прыжков (`navigate(Main) { popUpTo(...) }`) при возвратах:
удаление → `popBackStack()`; редактирование открывается поверх деталей,
отмена и сохранение закрывают его `popBackStack()` (механизм уже есть —
`EntryCreateNavDestination`), затем с деталей «назад» → главный;
возврат с деталей — стрелка/Back/жест = один `popBackStack()`.
Редактирование с главного экрана не меняется.

### Decision 9: Аналитика (решение владельца — ScreenView + UserActions)

- `AppScreen.EntryDetailScreen` + маппинг в `screenForRoute` — `ScreenView`
  логгируется автоматически существующим `DisposableEffect`-листенером
  `RootScreen` (единственный код аналитики экрана — маппинг).
- Новый `UserActionType.OPEN_ENTRY_DETAIL` (wire-значение UPPER_SNAKE_CASE,
  канон — D10 «Точки эмиссии user_action» архивного change
  firebase-crashlytics-mvp) — лог в точке нажатия в `MainScreen` (паттерн
  `OPEN_ENTRY_CREATE` на FAB).
- Реюз `EDIT` — лог при нажатии «Изменить» на детальном экране (паттерн
  JetpackDays `onEditClick`); реюз `DELETE` — в `confirmDelete`.

### Decision 10: Судьба файлов и черновиков

- `EntryDetailScreenDrafts.kt` перерабатывается в боевой экран:
  `ui/screens/entrydetail/EntryDetailScreen.kt` + `EntryDetailPreview.kt`;
  варианты A, B, C срезаны до реализации (остался только утверждённый A2
  как референс), демо-данные удаляются при переработке.
- `DetailsOverlay`, `EntryDetailsCard.kt`, `EntryDetailsCardPreview.kt`
  удаляются; требование превью «карточка поверх списка» исчезает вместе с
  компонентом.
- `EntryValueFormatter.kt` (форматы значения/даты) остаётся — переиспользуется
  детальным экраном.
- Тесты (поимённо): `MainScreenDetailCardTest` (12 тестов карточки) и
  `MainScreenDetailCardRecreateTest` переписываются под навигацию;
  `MainScreenTest` — правка меню (три пункта) и сценариев попапа
  (`menu_when_tap_other_row_*`, `menu_when_a11y_show_details_*` живут в
  DetailCardTest); `MainViewModelTest` — только фиксы Fake-порта;
  `EntryDetailDraftsScreenshotTest` (`:screenshot-tests`) удалён до
  реализации.

### Decision 11: Строки

Новые (ru/en): `entry_detail_title` («Детали записи» / "Entry details"),
`entry_detail_not_found` («Запись не найдена» / "Entry not found"),
`entry_list_menu_view` («Открыть» / "Open"). Реюз: `back` (стрелка),
`entry_list_menu_edit`, `entry_list_menu_delete`, `entry_list_delete_dialog_*`,
`cancel`, `a11y_show_details`, `entry_details_weight_suffix`,
`entry_value_separator`. Точные формулировки пунктов меню — финализируются на
этапе реализации (решение владельца), смысл зафиксирован спекой.

## Risks / Trade-offs

- [Новый метод порта ломает компиляцию 13+ Fake-репозиториев в тестах] →
  правка механическая, компилятор перечисляет все точки; включена отдельной
  задачей.
- [`WhileSubscribed(5000)`: экран под редактированием теряет подписку через
  5 c] → при возврате подписка восстанавливается, Room-Flow при
  переподписке отдаёт актуальные данные — актуализация не ломается
  (покрывается UI-тестом возврата после сохранения).
- [Запись удалена извне, пока экран открыт] → в офлайн-приложении с одним
  процессом недостижимо штатно; реактивный поток честно переводит экран в
  Error-заглушку (спека фиксирует).
- [Detekt `LongMethod: 60` на экране и дестинации] → дробление на private
  composable-функции (паттерн `MainScreen.kt`); лимит `maxIssues: 5` не
  повышается.
- [Тап-канал и a11y-канал при открытом меню ведут себя по-разному] →
  сохранение отлаженного поведения workout-detail-view (design Risks);
  поведение покрыто тестами.

## Migration Plan

БД не меняется (новый read-запрос DAO — без смены схемы и миграций). Чисто
UI/навигационный слой поверх существующего порта. Откат — revert коммита.

## Open Questions

Нет — все развилки закрыты решениями владельца и ответами на вопросы
(a11y-действие сохраняется и перецеливается; аналитика — ScreenView +
`OPEN_ENTRY_DETAIL`/реюз `EDIT`/`DELETE`; дельта состава меню — в требовании
«Удаление записи длинным нажатием»).
