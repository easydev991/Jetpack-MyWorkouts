# Design — entry-edit-mode

## Context

Слой данных готов целиком: `WorkoutDataRepository.getEntry(id)` возвращает
полную `ExerciseEntry`, `updateEntry(entry)` обновляет с валидацией
`EntryValidator` и сам ставит `modifiedAt`. `EntryListItem` в списке —
проекция без `comment`/`exerciseId`/`createdAt`, поэтому для
редактирования в навигацию передаётся только `id`, полная запись грузится
на экране. Экран записи (`EntryCreateScreen` + `EntryCreateViewModel`) уже
разложен на state (`EntryCreateScreenState` без ViewModel) — на нём
строится режим редактирования.

Образцы в JetpackDays: `ContextMenu`
(`app/src/main/java/com/dayscounter/ui/screens/events/MainScreen.kt:337`) —
пункты `DropdownMenuItem` с `leadingIcon` и `contentDescription = null`;
`CreateEditScreen` — единый экран создания/редактирования с
`checkHasChanges` и `SaveButton enabled = isSaveEnabled`.

## Goals / Non-Goals

**Goals:**

- Один экран записи для создания и редактирования; отличия — заголовок,
  предзаполнение, условие активности кнопки, ветка сохранения.
- Меню long-press: «Изменить» выше «Удалить», обоим — ведущие иконки;
  логика удаления не меняется.

**Non-Goals:**

- Слой данных, схема Room, миграции — без изменений.
- Пункт «Просмотр» (View) в меню — не запрашивался.
- Редактирование упражнения из каталога — отдельная тема.

## Decisions

1. **Навигация: опциональный аргумент на существующем маршруте.**
   `Screen.EntryCreate` получает route-паттерн
   `entry_create?entryId={entryId}` и, дополнительно к `route`, член
   `val arguments = listOf(navArgument("entryId") { type =
   NavType.LongType; defaultValue = -1L })` — без этого блока query-параметр
   `{entryId}` обязателен и `navigate("entry_create")` не сматчится с
   паттерном (navArgument в проекте до этого не использовался). Оба места
   регистрации — `RootScreen.kt:100` и `MainScreenFabNavigationTest.kt:92` —
   меняются на `composable(Screen.EntryCreate.route, arguments =
   Screen.EntryCreate.arguments)`. `entryId == -1L` — режим создания.
   Helper `EntryCreate.createRoute(entryId: Long = -1L)`: при дефолте
   эмитит чистый `entry_create` (без query — `defaultValue` подставит
   навигация), при явном id —    `entry_create?entryId=$entryId`. Альтернатива — отдельный маршрут
   `entry_edit/{id}` — отклонена: два composable на один экран,
   дублирование регистрации. Устаревший KDoc `Screen.kt:32-34` («Для
   будущего редактирования — отдельный маршрут `entry_edit/{id}` …
   (design.md Decision 3)») противоречит этому решению и переписывается под
   опциональный аргумент (tasks 1.2). Ассерт
   `MainScreenFabNavigationTest` (`:116-121`) уже сравнивает маршрут с
   `Screen.EntryCreate.route` и не меняется; в этом же тесте правятся
   registration (`:92`, arguments) и navigate-вызов (`:89` →
   `EntryCreate.createRoute()` — иначе литеральный `{entryId}` уйдёт в query
   и LongType-аргумент не распарсится).

2. **Загрузка записи в `init` ViewModel по id.**
   `EntryCreateViewModel` получает `entryId` параметром с дефолтом `-1L` —
   в конструкторе и в factory (дефолт сохраняет зелёность старых тестов:
   они зовут конструктор named-аргументами через общий хелпер). При
   `entryId != -1` в `init` вызывается `getEntry(id)`; успех →
   `originalEntry` в VM (приватное поле, Decision 4) и предзаполнение полей
   формы; `null` → Decision 8. Альтернатива — передать `EntryListItem` через
   навигацию — отклонена: проекция без `comment`/`exerciseId`/`createdAt`.

3. **Подстановка упражнения из каталога — в combine-цепочке `uiState`.**
   `uiState` уже строится как `combine(repository.exercises(),
   mutableUiState)` (`EntryCreateViewModel.kt:50-60`). Ветка подстановки
   добавляется в лямбду combine и срабатывает при изменении любого
   источника: если `selectedExercise == null && originalEntry != null` —
   взять из списка элемент с `exercise.id == originalEntry.exerciseId` и
   проставить в `selectedExercise`. Это покрывает оба порядка эмита:
   каталог раньше `getEntry` — завершение загрузки делает
   `mutableUiState.update { ... }` (предзаполнение полей формы), combine
   переэмитит и подстановка выполнится; запись раньше каталога — выполнится
   на первом эмите каталога. Ветка только в коллекторе каталога оставила бы
   экран без полей при порядке «каталог раньше». Отдельное поле
   `pendingExerciseId` не вводится: условие самогасится — после подстановки
   `selectedExercise != null`, а пути сбросить выбранное упражнение в UI
   нет. Это же покрывает предзаполнение типозависимого поля: поля
   выполнения, как и в режиме создания, видны только после выбора
   упражнения.

4. **`hasChanges` как вычисляемое сравнение, а не флаг на каждом onChange.**
   `originalEntry: ExerciseEntry?` — приватное поле `EntryCreateViewModel`;
   доменная запись не попадает в UiState — UI читает только булевы поля
   `isEditMode`, `hasChanges` и `isEntryMissing` (Decision 8). `isEditMode`
   в UiState — из `entryId != -1L`, а не из `originalEntry != null` (иначе
   при гонке из Decision 8 заголовок «моргнул» бы на «Новая запись»); KDoc
   поля оговаривает: истина, если экран открыт с `entryId != -1`, в том
   числе пока запись не загружена или не найдена. Нормализация полей формы
   в `ExerciseEntry` живёт в одном месте — приватный сборщик черновика в
   `EntryCreateViewModel`: `setsText.toIntOrNull() ?: 1`,
   `repsText.toIntOrNull()`/`durationSec` (по типу измерения),
   `weightText.toDoubleOrNull()?.takeIf { it > 0.0 }`,
   `comment.ifBlank { null }`, `entryDateMillis(date, hour, minute)`,
   `selectedExercise?.id`. `hasChanges` вычисляется в той же combine-цепочке
   `uiState` (Decision 3): «черновик с `id`/`createdAt`/`modifiedAt` из
   `originalEntry` != `originalEntry»`; `false`, пока `originalEntry ==
   null`. `canSave` в режиме редактирования — `canSave && hasChanges`
   (в режиме создания не меняется). Тот же сборщик использует `save()`
   (Decision 5) — одна точка правды для нормализации, diff-логика буквально
   «черновик == оригинал». Альтернатива — dirty-флаг в каждом `on*Change` —
   отклонена: 8 точек мутации, риск рассинхрона.

5. **Ветки `save()` на одном сборщике черновика.**
   `save()` строит запись сборщиком из Decision 4: в режиме редактирования —
   с `id` и `createdAt` из `originalEntry` и вызывает
   `repository.updateEntry` (`modifiedAt` репозиторий ставит сам); в режиме
   создания — `id = 0`, `createdAt = now`, вызов `addEntry` (ветка не
   меняется). Успех → `onSaved()` (назад), провал → `isSaveError` (экран
   открыт) — единая обработка.

6. **Меню: минимальный diff в `EntryContextMenu`.**
   `EntryContextMenu` получает колбэк `onRequestEdit: (EntryListItem) -> Unit`;
   пункты: «Изменить» (`Icons.Filled.Edit`) выше «Удалить»
   (`Icons.Filled.Delete`), у обоих `leadingIcon` c `contentDescription =
   null` — текст пункта сам читается службами доступности (образец —
   JetpackDays). Прокидывание — две ответственности с разным владельцем.
   Замыкание меню в обработчике обёртки — паттерн `onRequestDelete`
   (`MainScreen.kt:82-85`, `contextMenu = null`): колбэки
   `EntryListCallbacks.onRequestEdit` → `MainScreenState.onRequestEdit` —
   оба только сигнал из меню, без побочных эффектов; обёртка `MainScreen`
   в обработчике `MainScreenState.onRequestEdit` закрывает меню и
   делегирует своему параметру `onEditEntryClick: (Long) -> Unit` c
   `item.id`. Проброс `id` через параметр `MainScreen` и навигация —
   паттерн `onAddEntryClick` (`RootScreen.kt:87`): `RootScreen` делает
   только `navController.navigate(EntryCreate.createRoute(id))` и не знает
   про транзиентное состояние меню.

7. **Заголовок и строки.**
   `TopAppBar` берёт `if (uiState.isEditMode) R.string.entry_edit_title else
   R.string.entry_create_title`. Новые строки: `entry_edit_title`
   («Изменить запись» / "Edit entry"), `entry_list_menu_edit`
   («Изменить» / "Edit") и `entry_hint_entry_missing` («Запись была
   удалена» / "The entry has been deleted", Decision 8) — ru + en.

8. **`getEntry` вернул `null` → пустая форма + подсказка.**
   Записи нет (удалена, пока висела ссылка на экран). `originalEntry`
   остаётся `null`, но `isEditMode` (из `entryId`, Decision 4) — `true`:
   заголовок остаётся «Изменить запись», форма — дефолты без предзаполнения.
   VM выставляет в UiState `isEntryMissing = true`, и под кнопкой сохранения
   показывается подсказка `entry_hint_entry_missing` («Запись была
   удалена») — паттерн существующих `entry_hint_*`. Без подсказки кнопка
   выглядела бы сломанной: `canSave && hasChanges` недостижим (`hasChanges`
   false при `originalEntry == null`) — даже если пользователь заполнил
   форму вручную, сохранить он не может, и причины не видно. Тихое
   переключение заголовка на «Новая запись» выглядело бы багом; Snackbar
   или отдельный экран ошибки — оверкилл для случая, достижимого только
   гонкой извне.

## Risks / Trade-offs

- [Опциональный аргумент меняет строку маршрута] `currentRoute` для экрана
  записи станет `entry_create?entryId={entryId}`. В `MainScreenFabNavigationTest`
  ассерт (`:116-121`) уже сравнивает с `Screen.EntryCreate.route` и не
  меняется; меняются registration (`:92`, `arguments = ...`) и
  navigate-вызов (`:89` → `EntryCreate.createRoute()`, иначе краш на
  парсинге `{entryId}` в Long — tasks 1.3). Нижняя панель: маршрут
  не входит в `BottomTabs` — скрывается, как раньше.
- [Порядок эмита каталога и записи произвольный] каталог может прийти
  раньше завершения `getEntry` — ветка подстановки живёт в combine-цепочке
  `uiState` (Decision 3) и выполняется на переэмите после загрузки записи;
  ветка только в коллекторе каталога оставила бы экран без полей
  выполнения. Возможен миг одного кадра до прихода каталога.
- [Один экран — два режима] состояние шире на три булевых поля (`isEditMode`,
  `hasChanges`, `isEntryMissing`); риск смешения режимов ограничен
  ветвлением по `isEditMode` (из `entryId`, Decision 4).
- [Detekt `LongMethod: 60`] `EntryContextMenu` и `save()` растут → при
  необходимости выносить, как уже сделано в `MainScreen`.

## Migration Plan

Изменения только в UI-слое и строках; БД и миграции не затрагиваются.
Откат — реверт коммитов, состояние данных не меняется.

## Open Questions

Нет.
