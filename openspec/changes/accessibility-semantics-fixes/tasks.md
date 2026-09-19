# Tasks

## 1. Строки и главный список (TDD: тест → реализация)

- [ ] 1.1 Ресурсы: `a11y_open_menu` («Открыть меню» / "Open menu") в
  `app/src/main/res/values/strings.xml` и `values-en/strings.xml`
  (до теста — иначе красная фаза через ошибку компиляции, а не падение).
- [ ] 1.2 Тест: строка записи предоставляет accessibility-действие с
  лейблом `a11y_open_menu`; выполнение действия открывает контекстное
  меню с пунктами «Изменить»/«Удалить»
  (`app/src/androidTest/.../main/MainScreenTest.kt` — рядом с
  существующими long-press-тестами, семантика `CustomActions`).
- [ ] 1.3 Реализация: `EntryListItemRow` (`EntryListItemRow.kt`) — в
  существующий блок `semantics(mergeDescendants = true)` добавляется
  `customActions` с локализованным лейблом; колбэк long-press
  прокидывается из `EntryListItems` (`MainScreen.kt`) параметром;
  офсет — центр строки (design D2); тест 1.2 зелёный.

## 2. Каталог и архив упражнений

- [ ] 2.1 Тест: строка каталога и строка архива предоставляют
  accessibility-действие `a11y_open_menu`; выполнение открывает меню —
  состав по типу упражнения (каталог) / всегда «Вернуть» (архив)
  (`ExercisesScreenTest.kt`, `ArchivedExercisesScreenTest.kt`).
- [ ] 2.2 Реализация: `ExerciseMenuHelpers.applyLongPress` добавляет
  `customActions` (design D3) — ExercisesScreen и
  ArchivedExercisesScreen фиксируются одной правкой; тесты 2.1 зелёные.

## 3. Однострочники семантики

- [ ] 3.1 Тест: заголовок секции главного экрана имеет семантику
  `Heading` (`MainScreenSectionsTest.kt`).
- [ ] 3.2 Реализация: `SectionHeader.kt` — `Modifier.semantics { heading() }`.
- [ ] 3.3 Тест: элемент нижней навигации не имеет `ContentDescription`,
  название вкладки — единственный текст (новый
  `RootScreenTest.kt` — файла сейчас нет).
- [ ] 3.4 Реализация: `RootScreen.kt` — `contentDescription = null` у
  иконки `NavigationBarItem` (design D6).

## 4. Чекбокс «Больше не показывать»

- [ ] 4.1 Тест: тап по лейблу «Больше не показывать» переключает
  чекбокс алерта архивации; подтверждение с поставленным лейблом
  приводит к записи `dontShowArchiveDialog = true` (как в
  существующем сценарии «confirmArchive с suppress=true пишет pref»)
  (`ExercisesScreenTest.kt`).
- [ ] 4.2 Реализация: `Row` лейбла и чекбокса —
  `Modifier.toggleable(..., role = Role.Checkbox, ...)`,
  `Checkbox(onClick = null)` (design D7); тест 4.1 зелёный.

## 5. Финализация

- [ ] 5.1 `make format && make lint && make test` зелёные;
  `make android-test` (затронутые классы) зелёные; ручная проверка
  TalkBack-сценария на эмуляторе (меню из a11y-действия).
