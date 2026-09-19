# Proposal

## Why

Аудит доступности показал критичный пробел: контекстное меню строк (записи, упражнения, архив) открывается только долгим нажатием через `pointerInput`/`detectTapGestures`, который не создаёт accessibility-действий — через TalkBack действия «Изменить», «Удалить», «Архивировать», «Вернуть» недостижимы. Рядом с этим — три точечных дефекта семантики: заголовки секций не помечены как заголовки, названия вкладок озвучиваются дважды, тап по лейблу чекбокса «Больше не показывать» не переключает его.

## What Changes

- Строки списков получают accessibility-действие «Открыть меню»: то же контекстное меню, что по долгому нажатию, доступно из меню действий скринридера (главный список, каталог упражнений, архив).
- Заголовки секций главного экрана помечаются `heading()` — навигация по заголовкам в TalkBack работает.
- Иконка `NavigationBarItem` перестаёт дублировать название вкладки: `contentDescription = null`, название читается один раз из лейбла.
- Лейбл чекбокса «Больше не показывать» в алерте архивации кликабелен: тап по нему переключает чекбокс (`toggleable` с `Role.Checkbox`; тот же приём «единая кликабельная область + `onClick = null` на контроле», что у радио-строк внешности — те через `selectable`, см. design D7).

## Capabilities

### New Capabilities

_(нет)_

### Modified Capabilities

- `workouts-main-screen`: требование «Удаление записи длинным нажатием» — меню доступно также через accessibility-действие строки; требование «Список записей разделён на секции по дате» — заголовки секций помечены как заголовки (`heading`) для навигации скринридера.
- `exercises-screen`: требование «Контекстное меню по долгому нажатию — с учётом типа упражнения» — то же accessibility-действие у строк каталога; требование «Алерт подтверждения архивации» — тап по лейблу «Больше не показывать» переключает чекбокс.
- `archived-exercises-screen`: требование «Long-press меню «Вернуть» для архивных строк» — то же accessibility-действие у архивных строк.
- `tab-navigation`: требование «NavigationBar highlights the active tab via currentBackStackEntryAsState» — иконка вкладки без собственного текстового описания, название вкладки озвучивается один раз.

## Impact

- `app/src/main/java/com/myworkouts/ui/screens/main/MainScreen.kt` (`EntryListItems` — long-press-проводка), `ui/screens/main/EntryListItemRow.kt` (блок `semantics` строки — точка добавления accessibility-действия), `ui/screens/main/SectionHeader.kt` (`heading`).
- `ui/screens/exercises/ExerciseMenuHelpers.kt` (общий хелпер long-press — точка добавления accessibility-действия), `ExercisesScreen.kt` (вызовы long-press + лейбл чекбокса алерта архивации), `ArchivedExercisesScreen.kt` (вызовы).
- `ui/screens/root/RootScreen.kt` (`contentDescription` иконки вкладки).
- Ресурсы: одна новая строка `a11y_open_menu` (локализованный
  заголовок accessibility-действия) в `values/strings.xml` +
  `values-en/strings.xml`.
- Тесты: `app/src/androidTest/` — новые проверки accessibility-действий и семантики; регресс существующих long-press-тестов не ожидается.
