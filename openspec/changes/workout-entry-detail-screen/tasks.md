# Tasks

> Финальный gate каждой задачи — `make format && make lint && make test`
> (kotlin + markdown lint); новые detekt-предупреждения исправляются до
> завершения задачи, лимит `maxIssues: 5` не повышается. Для задач
> androidTest дополнительно указан точечный прогон (класс —
> runner-arguments, канон `.opencode/rules/test-execution.md`); полный
> `make android-test` — один раз в разделе 6.

## 1. Строки и порт данных (TDD)

- [ ] 1.1 Добавить строки ru/en в `values/strings.xml` и
  `values-en/strings.xml`: `entry_detail_title` («Детали записи» /
  "Entry details"), `entry_detail_not_found` («Запись не найдена» /
  "Entry not found"), `entry_list_menu_view` («Просмотр» / "View").
  Проверка: `make format && make lint && make test` — ресурсы компилируются
- [ ] 1.2 Написать падающий тест `entryById` в
  `RoomWorkoutDataRepositoryTest` (androidTest, Room in-memory):
  существующий id → поток эмитит `EntryListItem` с полями записи
  (название, иконка, значение, вес, комментарий, дата); несуществующий id →
  эмитит `null`; после `updateEntry` поток эмитит обновлённые данные
  (реактивность). Проверка: точечный прогон класса — red;
  `make format && make lint && make test` — JVM зелёные
- [ ] 1.3 Реализовать `entryById(id: Long): Flow<EntryListItem?>`:
  DAO-запрос `observeEntryRowById` (JOIN-проекция `observeEntryRows` с
  `WHERE id = :id`), маппинг `toListItem()` в
  `RoomWorkoutDataRepository`, объявление в порту
  `WorkoutDataRepository` (design Decision 2). Обновить ВСЕ Fake-реализации
  порта в тестах (компилятор перечислит; известные:
  `MainViewModelTest`, `MainScreenEditNavigationTest`,
  `MainScreenFabNavigationTest`, `MainScreenSectionsTest`,
  `ExerciseCreateScreenTest` (EditFakeRepository), `ExercisesDeleteFlowTest`
  (3 фейка), прочие по компилятору; `MainScreenTest`,
  `MainScreenDetailCardTest`, `MainScreenDetailCardRecreateTest` фейков
  порта не имеют — правятся в разделе 5 как переписывание). Проверка: точечный прогон `RoomWorkoutDataRepositoryTest`
  — green; `make format && make lint && make test` — всё зелёное

## 2. EntryDetailViewModel (TDD, JVM)

- [ ] 2.1 Написать падающие тесты `EntryDetailViewModelTest` (Fake-порт с
  hot-потоком записи): начальное состояние `Loading`; существующий id →
  `Success` с данными; несуществующий id → `Error` (не вечный Loading);
  изменение записи в потоке → `Success` с новыми данными;
  `requestDelete` выставляет pending, `cancelDelete` снимает;
  `confirmDelete` вызывает `deleteEntry`, логирует `UserAction DELETE` и
  вызывает `onDeleted`; `confirmDelete` при ошибке репозитория логирует
  exception через crashReporter и НЕ вызывает `onDeleted`. Проверка:
  `make test` — новые тесты падают (red)
- [ ] 2.2 Реализовать `EntryDetailViewModel` + `EntryDetailUiState`
  (`ui/state/`): sealed `Loading`/`Success(data)`/`Error` (data object);
  `uiState = entryById(entryId).map { … }.stateIn(viewModelScope,
  WhileSubscribed(5000), Loading)`; `pendingDelete: StateFlow<Boolean>`;
  `confirmDelete(onDeleted)` по design Decision 4; фабрика
  `factory(repository, analyticsProvider, crashReporter, entryId)` (паттерн
  `EntryCreateViewModel.factory`, без `SavedStateHandle`). Конвенции:
  никогда `!!`, `Result<T>` с onFailure-веткой. Проверка: `make test` —
  тесты раздела зелёные (green)

## 3. Экран деталей и общий алерт удаления

- [ ] 3.1 Вынести `EntryDeleteDialog` из `MainScreen.kt` в
  `ui/components/EntryDeleteDialog.kt` (internal; стиль и строки — без
  изменений, design Decision 5); `MainScreen` переключается на общий
  компонент. Проверка: `make format && make lint && make test` — зелёное
- [ ] 3.2 Переработать черновик A2 в боевой экран
  `ui/screens/entrydetail/EntryDetailScreen.kt`: `Scaffold` + `TopAppBar`
  («Детали записи», стрелка назад → `onBackClick`, actions
  «Изменить»/«Удалить» только в `Success` — design Decision 6); состояния —
  `Loading` (пустой контент), `Success` (контент по центру без Card:
  иконка, название, значение `headlineMedium` `primary`, дата/время,
  комментарий в тональном блоке ≤200dp со скроллом), `Error` (заглушка
  `entry_detail_not_found`); `entryDetailsValueText`/`DetailComment`
  переезжают из `EntryDetailsCard.kt`; форматы —
  `formatEntryDetailsValue`/`formatDetailDateTime`; строки из ресурсов;
  контент через `paddingValues`; сигнатура —
  `EntryDetailScreen(viewModel, onBackClick, onEditClick)` (паттерн
  `EntryCreateScreen`); тап «Удалить» — `viewModel.requestDelete()`,
  подтверждение алерта — `viewModel.confirmDelete(onDeleted = onBackClick)`
  (как `save(onSaved = onBackClick)` в `EntryCreateScreen`; design
  Decision 4 — отдельный навигационный колбэк удаления не нужен). Никаких
  `!!`; deprecated API не используется. Проверка: `make format && make
  lint && make test` — зелёное
- [ ] 3.3 Создать `EntryDetailPreview.kt` (`*Preview.kt` рядом с экраном):
  по одному состоянию на превью — Loading, Success (все поля), Success без
  комментария, Success (запись на время), Error; светлая + тёмная темы
  (`UI_MODE_NIGHT_YES`), `showBackground = true`, `locale = "ru"`,
  обёртка в `JetpackMyWorkoutsTheme`. Проверка: компиляция превью без
  ошибок
- [ ] 3.4 Удалить `EntryDetailScreenDrafts.kt` целиком (подрезан до
  утверждённого A2 до реализации — коммиты «Удалил тест-рендер
  черновиков» и «Подрезал черновики экрана»); удалить
  `EntryDetailsCard.kt` и `EntryDetailsCardPreview.kt` (хелперы переехали в
  3.2). Проверка: `make format && make lint && make test` — компиляция без
  ссылок на удалённые файлы

## 4. Навигация и главный экран

- [ ] 4.1 Написать падающий JVM-тест `Screen.EntryDetail`:
  `createRoute(7) == "entry_detail/7"`; `arguments` —
  `NavType.LongType` без `defaultValue`; `ARG_ENTRY_ID == "entryId"`;
  объект — экземпляр sealed `Screen` (только `route`). Проверка:
  `make test` — red
- [ ] 4.2 Реализовать `Screen.EntryDetail` в `navigation/Screen.kt`
  (design Decision 1); зарегистрировать дестинацию
  `EntryDetailNavDestination` в `AppNavHost` (паттерн
  `EntryCreateNavDestination`: entryId из `backStackEntry.arguments`,
  фабрика VM, `onBackClick = popBackStack`, `onEditClick =
  navigate(Screen.EntryCreate.createRoute(entryId))` + лог
  `UserAction EDIT`); добавить `AppScreen.EntryDetailScreen`,
  `UserActionType.OPEN_ENTRY_DETAIL`, маппинг `screenForRoute`
  (design Decision 9). Проверка: `make test` — тест 4.1 зелёный; сборка
- [ ] 4.3 Главный экран: удалить `detailsEntryId` (`rememberSaveable`),
  `BackHandler`, `onCloseDetails`, `MainScreenState.detailsEntryId`,
  `DetailsOverlay`; `isSelected` — только `contextMenu?.item?.id == it.id`;
  тап по строке → лог `OPEN_ENTRY_DETAIL` +
  `onOpenEntryClick(item.id)` → `navigate(Screen.EntryDetail.createRoute(id))`
  (проводка через `NavDestinations.kt`); тап при открытом меню гасит только
  меню (design Decision 7). Проверка: `make format && make lint && make
  test` — JVM зелёные (androidTest попап-тесты переписываются в разделе 5 —
  до этого красные, ожидаемо)
- [ ] 4.4 Контекстное меню: пункт «Просмотр» выше «Изменить»
  (`leadingIcon` с `contentDescription = null`, паттерн пунктов меню) →
  тот же канал открытия деталей; `EntryListItemRow` — a11y-действие
  «Показать детали» перецеливается на открытие экрана (решение владельца).
  Проверка: `make format && make lint && make test` — зелёное

## 5. androidTest — переписывание существующих и новые UI-тесты

> До завершения раздела androidTest-набор красный/некомпилируемый —
> ожидаемо (снос попапа в разделе 3–4); Compose UI-тесты — по факту
> (не TDD), системный Back — канон
> `ExerciseCreateScreenTest.edit_when_no_changes_and_system_back_then_closed_without_dialog`.

- [ ] 5.1 Переписать `MainScreenDetailCardTest.kt` →
  `MainScreenEntryNavigationTest.kt`, поимённая судьба 12 тестов:
  `card_when_row_tapped_…` → тап открывает экран деталей (без
  `Selected`-проверки — осознанно: выделение от деталей устранено на
  уровне кода задачей 4.3, `isSelected` — только от меню, design
  Decision 7); `card_when_close_button_tapped_…` и
  `card_when_scrim_tapped_…` → удаляются (крестика/скрима нет);
  `card_when_back_pressed_…` → `pressBack` с деталей → главный экран;
  `card_when_long_comment_…` и `card_when_no_comment_…` → переезжают в
  `EntryDetailScreenTest` (5.4); `long_press_when_invoked_…` → остаётся
  (меню, не экран); `card_when_a11y_show_details_invoked_…` → действие
  открывает экран деталей; `card_when_entry_deleted_then_closes_…` →
  удаление с экрана деталей (алерт) → главный экран;
  `card_when_entry_updated_…` → актуализация данных экрана (без
  выделения строки); `menu_when_tap_other_row_…` → тап при открытом меню
  закрывает только меню; `menu_when_a11y_show_details_other_row_…` →
  a11y-действие закрывает меню и открывает экран. Проверка: точечный прогон
  класса — green
- [ ] 5.2 Переписать `MainScreenDetailCardRecreateTest.kt`:
  `card_when_activity_recreated_…` → пересоздание Activity/поворот на
  детальном экране — данные той же записи, маршрут
  `entry_detail/{entryId}` сохраняется. Проверка: точечный прогон класса —
  green
- [ ] 5.3 Правки `MainScreenTest.kt`:
  `content_long_press_then_menu_shows_edit_item_above_delete` → три пункта
  («Просмотр» выше «Изменить» выше «Удалить»);
  `content_long_press_open_menu_then_delete_…` → адаптировать под
  трёхпунктовое меню (сценарий удаления с главного экрана — как раньше);
  `content_long_press_then_row_selected_semantic_is_true_…` и
  `row_when_a11y_action_invoked_…` → проверить актуальность
  (`Selected` — только от меню; «Открыть меню» — без изменений). Проверка:
  точечный прогон класса — green
- [ ] 5.4 Новый `EntryDetailScreenTest.kt`: содержимое `Success` (значение с
  подходами и весом, один подход без веса, длительность, дата/время,
  комментарий целиком, отсутствие блока без комментария — перенятые из 5.1);
  несуществующий id → заглушка «Запись не найдена», actions скрыты;
  нижняя навигация отсутствует в дереве композиции; insets — контент не
  заходит под статус-бар и панель навигации (позиционные ассерты).
  Проверка: точечный прогон класса — green
- [ ] 5.5 Новые навигационные сценарии (`MainScreenEntryNavigationTest` /
  `EntryDetailScreenTest`): Detail → Edit («Изменить») → форма
  предзаполнена → сохранение → возврат на Detail с обновлёнными данными
  (parity-тест актуализации); Detail → Edit → отмена (стрелка) → Detail
  без изменений; Detail → Edit → `pressBack` без изменений → Detail без
  диалога; удаление: алерт → «Отмена» → остались на Detail; алерт →
  «Удалить» → запись удалена, открыт главный экран; нижний бар скрыт на
  всём пути Main → Detail → Edit; `pressBack` из Detail → Main.
  Проверка: точечный прогон классов — green

## 6. Финальная проверка

- [ ] 6.1 Прогнать `make format && make lint && make test` — всё зелёное,
  новых detekt-предупреждений нет (лимит `maxIssues: 5` не поднимать)
- [ ] 6.2 Прогнать `make android-test` на подключённом эмуляторе — один
  прогон, отчёт `scripts/test_report.py` в выводе; все тесты зелёные
- [ ] 6.3 Ручная проверка на устройстве/эмуляторе с жестовой навигацией и
  display cutout: контент детального экрана не заходит под системные
  панели; светлые/тёмные темы рендерятся по превью; скриншот-прогон не
  требуется (маркетплейс-сценарии не меняются)
