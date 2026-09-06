## Why

Запись нельзя исправить: опечатка, смена даты или веса заставляет удалять запись
и вводить её заново. Слой данных (`updateEntry`, `getEntry`) уже готов, но UI
не даёт до него добраться.

## What Changes

- Контекстное меню long-press на главном экране получает иконки (карандаш у
  «Изменить», корзина у «Удалить») и новый пункт «Изменить» выше «Удалить».
  Образец — `ContextMenu` в JetpackDays.
- Пункт «Изменить» закрывает меню и открывает экран записи в режиме
  редактирования: маршрут `entry_create` получает опциональный аргумент
  `entryId` (default −1 — режим создания).
- Режим редактирования: заголовок «Изменить запись», форма предзаполняется
  из `getEntry(id)` (упражнение подставляется из каталога по `exerciseId`),
  кнопка «Сохранить» активна только при валидной форме И наличии изменений,
  сохранение через `updateEntry` с сохранением `id`/`createdAt`.
- Режим создания (FAB) не меняется: `entryId = −1`, поведение прежнее.

## Capabilities

### New Capabilities

(нет — расширение существующих экранов)

### Modified Capabilities

- `workouts-main-screen`: контекстное меню long-press — два пункта («Изменить»
  выше «Удалить») с leadingIcon; пункт «Изменить» ведёт к экрану редактирования
  записи; требование «Удаление записи длинным нажатием» обновляется (меню уже
  не с единственным пунктом).
- `workout-entry-screen`: экран записи получает режим редактирования —
  заголовок, предзаполнение полей из существующей записи, условие активности
  кнопки сохранения (валидность + наличие изменений), сохранение через
  обновление существующей записи.

## Impact

- `navigation/Screen.kt` — аргумент `entryId` маршрута `EntryCreate` + helper
  `createRoute(entryId)`; обновление KDoc (решение об опциональном аргументе
  вместо упомянутого ранее `entry_edit/{id}`).
- `ui/screens/root/RootScreen.kt` — прокидывание `entryId` в экран и factory.
- `ui/screens/main/MainScreen.kt` — меню (`EntryContextMenu`), колбэк
  `onRequestEdit`.
- `ui/screens/entry/EntryCreateScreen.kt` (+ Preview) — заголовок, превью
  режима редактирования.
- `ui/state/EntryCreateUiState.kt` — исходная запись / признак `hasChanges`.
- `ui/viewmodel/EntryCreateViewModel.kt` — загрузка `getEntry`, ветка
  `updateEntry` в `save()`, factory с `entryId`.
- `res/values{,-en}/strings.xml` — `entry_edit_title`, `entry_list_menu_edit`.
- Тесты: unit `EntryCreateViewModelTest` (редактирование), androidTest
  `MainScreenTest` (меню), новый/расширенный UI-тест навигации к
  редактированию.
- `data/`, `domain/` — без изменений; миграций нет.
