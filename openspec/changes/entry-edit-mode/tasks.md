## 1. Строки и маршрут

- [x] 1.1 Добавить строки `entry_edit_title` («Изменить запись» / "Edit
  entry"), `entry_list_menu_edit` («Изменить» / "Edit") и
  `entry_hint_entry_missing` («Запись была удалена» / "The entry has been
  deleted", design Decision 8) в `res/values/strings.xml` и
  `res/values-en/strings.xml`; проверить `make lint`
- [x] 1.2 `Screen.EntryCreate`: route-паттерн `entry_create?entryId={entryId}`,
  член `arguments = listOf(navArgument("entryId") { type =
  NavType.LongType; defaultValue = -1L })` (без него query-параметр обязателен
  и `navigate("entry_create")` не сматчится), helper
  `createRoute(entryId: Long = -1L)` (дефолт — чистый `entry_create` без
  query). Переписать устаревший KDoc `Screen.kt:32-34` — убрать «отдельный
  маршрут `entry_edit/{id}`» и ссылку «(design.md Decision 3)», описать
  опциональный аргумент `entryId` (design Decision 1). Проверка: `make lint`
- [x] 1.3 Подключить `arguments` в обоих местах регистрации:
  `composable(Screen.EntryCreate.route, arguments =
  Screen.EntryCreate.arguments)` — `RootScreen.kt:100` и
  `MainScreenFabNavigationTest.kt:92` (navArgument в проекте до этого не
  использовался). Там же в `MainScreenFabNavigationTest.kt:89` заменить
  `navigate(Screen.EntryCreate.route)` на
  `navigate(EntryCreate.createRoute())` — после смены route на шаблон старый
  вызов унесёт литеральный `{entryId}` в query, и LongType-аргумент не
  распарсится (краш теста). Проверка: `make android-test` —
  `main_screen_when_fab_clicked_then_navigates_to_entry_create_route`
  зелёный

## 2. Unit-тесты ViewModel (TDD — сначала красные)

- [x] 2.1 Подготовка сигнатуры (без изменения поведения): параметр
  `entryId: Long = -1L` в конструкторе `EntryCreateViewModel` и в `factory`.
  Дефолт сохраняет старые тесты зелёными — все 16 зовут конструктор
  named-аргументами через общий хелпер `createViewModel()`. Проверка:
  `make test` зелёный
- [x] 2.2 Донастроить фейк в `EntryCreateViewModelTest` (сейчас `getEntry`/
  `updateEntry` — заглушки `success(null)`/`success(Unit)`): `getEntry`
  возвращает настраиваемую запись, `updateEntry` записывает вызовы; хелпер
  `createViewModel()` принимает `entryId`
- [x] 2.3 `EntryCreateViewModelTest`: предзаполнение из `getEntry(id)` для
  REPS-записи (упражнение из каталога по `exerciseId`, подходы/повторы/вес/
  дата/время/комментарий) и `hasChanges == false` сразу после загрузки
- [x] 2.4 `EntryCreateViewModelTest` (hasChanges): `hasChanges == true` после
  изменения поля; кнопка заблокирована без изменений при валидной форме
- [x] 2.5 `EntryCreateViewModelTest` (canSave): невалидное изменение —
  очищенные повторы — деактивирует кнопку независимо от наличия изменений
- [x] 2.6 `EntryCreateViewModelTest`: `save` в режиме редактирования вызывает
  `updateEntry` (не `addEntry`) с сохранёнными `id`/`createdAt` записи;
  `getEntry → null`: форма в дефолтах, `isEditMode` остаётся `true`,
  кнопка неактивна, `isEntryMissing = true` (design Decision 8)
- [x] 2.7 Запустить `make test` — новые тесты (2.3–2.6) красные, старые
  зелёные

## 3. ViewModel и UiState режима редактирования

- [x] 3.1 `EntryCreateUiState`: булевы `isEditMode` (из `entryId`, не из
  `originalEntry` — Decision 4/8; KDoc: истина, если экран открыт с
  `entryId != -1`, в т.ч. пока запись не загружена или не найдена),
  `hasChanges` и `isEntryMissing` (Decision 8); `originalEntry` — приватное
  поле VM, доменная модель в UiState не попадает (Decision 4); приватный
  сборщик черновика `ExerciseEntry` из полей формы — один на `hasChanges`
  и `save()`; `hasChanges` вычисляется в combine-цепочке `uiState`
  (Decision 3); в режиме редактирования `canSave` дополняется `hasChanges`
- [x] 3.2 `EntryCreateViewModel`: `getEntry(entryId)` в `init` (design
  Decision 2); ветка подстановки `selectedExercise` по
  `originalEntry.exerciseId` — в combine-лямбде `uiState`, срабатывает при
  изменении любого источника, без отдельного поля (Decision 3); ветки
  `save()` — черновик с `id`/`createdAt` из `originalEntry` →
  `updateEntry`, иначе `addEntry` (Decision 5); при `getEntry → null` —
  `isEntryMissing` (Decision 8). Проверка: `make test` — все тесты зелёные;
  `make lint` — при детект-падении `LongMethod` выносить ветки, как уже
  сделано в `MainScreen`

## 4. Контекстное меню главного экрана

- [x] 4.1 `MainScreenTest`: long-press меню показывает пункты «Изменить» и
  «Удалить», «Изменить» выше (красный тест)
- [x] 4.2 `EntryContextMenu`: пункт «Изменить» (`Icons.Filled.Edit`) выше
  «Удалить» (`Icons.Filled.Delete`), у обоих `leadingIcon` с
  `contentDescription = null`; `onRequestEdit` прокинут через
  `EntryListCallbacks` → `MainScreenState` → `MainScreen(onEditEntryClick)`;
  обработчик закрывает меню. Регресс: существующие тесты удаления зелёные —
  оба long-press теста `MainScreenTest` (полный сценарий «Удалить» → алерт →
  отмена/  подтверждение и тест выделения строки; их матчеры — по тексту
  пункта «Удалить», состав меню они не проверяют) и тесты
  `request_delete_then_pending_delete_id_set`,
  `cancel_delete_clears_pending_without_calling_delete`,
  `confirm_delete_calls_delete_entry_and_clears_pending` в
  `MainViewModelTest` (работают с VM, меню не затрагивают); `make lint`
  (при детект-падении `LongMethod` — выносить, как в `MainScreen`)
- [x] 4.3 `RootScreen`: `MainScreen.onEditEntryClick = { id ->
  navController.navigate(EntryCreate.createRoute(id)) }`; FAB
  (`RootScreen.kt:87`) перевести с `Screen.EntryCreate.route` на
  `EntryCreate.createRoute()` → маршрут `entry_create` без query
  (design Decision 1). Проверка: `MainScreenFabNavigationTest` зелёный
  (тело теста не меняется, registration — в 1.3)

## 5. Экран записи в режиме редактирования

- [x] 5.1 Новый файл
  `app/src/androidTest/java/com/myworkouts/ui/screens/main/MainScreenEditNavigationTest.kt`:
  NavHost-обёртка по образцу
  `MainScreenFabNavigationTest` (его репозиторий — анонимный
  `WorkoutDataRepository` на `MutableStateFlow`, не Room) + фейк с
  настраиваемым `getEntry(id) → entry` и `entries() → [listItem]`: long-press
  → «Изменить» открывает `entry_create?entryId=N` с заголовком «Изменить
  запись» и предзаполненной формой (красный тест)
- [x] 5.2 `RootScreen` прокидывает `entryId` из `navArgument` в
  `EntryCreateViewModel.factory(repository, entryId)`; `EntryCreateScreen`:
  заголовок `entry_edit_title` при `isEditMode` (иначе `entry_create_title`,
  без изменений). Проверка: тест 5.1 зелёный, `EntryCreateScreenTest`
  зелёный
- [x] 5.3 Превью: режим редактирования в `EntryCreateScreenPreview`
  (предзаполненная форма, светлая + тёмная, `locale = "ru"`,
  `showBackground = true`)

## 6. Финализация

- [x] 6.1 Обновить Purpose основной спеки
  `openspec/specs/workout-entry-screen/spec.md` — убрать «в текущей
  редакции покрывает только создание записи» (теперь покрывает оба режима)
- [x] 6.2 `make format && make lint && make test`; при подключённом
  девайсе — `make android-test`. Ручная проверка на девайсе: long-press →
  «Изменить» → форма предзаполнена, кнопка неактивна без изменений,
  сохранение обновляет запись в списке
