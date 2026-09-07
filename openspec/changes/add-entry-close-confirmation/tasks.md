# Tasks — add-entry-close-confirmation

Спека — `specs/workout-entry-screen/spec.md`, решения — `design.md`.
TDD: тесты (1, 4) раньше реализации (2, 5).

## 1. Юнит-тесты условия подтверждения (red)

- [x] 1.1 Создать `app/src/test/java/com/myworkouts/ui/state/EntryCreateUiStateTest.kt`
  (JUnit 5): режим создания — дефолтное состояние → `hasUnsavedChanges == false`;
  выбор упражнения / непустые повторы / подходы ≠ 1 / длительность выбрана /
  доп. вес ≠ 0 / непустой комментарий → `true`; вес «0.0» → `false`;
  изменение только даты или времени → `false`; откат поля до дефолта → `false`.
  Запустить `make test` и убедиться, что тесты падают (нет свойства)

## 2. Свойство `hasUnsavedChanges`

- [x] 2.1 Добавить в `EntryCreateUiState` вычисляемое `hasUnsavedChanges`
  рядом с `canSave` (design Decision 1: создание — сравнение с дефолтами,
  редактирование — `canSave`). Юнит-тесты из 1.1 зелёные: `make test`
- [x] 2.2 Дополнить `EntryCreateUiStateTest`: редактирование — без изменений
  → `false`; отличия + валидная форма → `true`; отличия + невалидная форма
  (очищены повторы) → `false`. `make test` зелёный

## 3. Ресурсы

- [x] 3.1 Добавить `entry_confirm_close_dialog_title`,
  `entry_confirm_close_dialog_message`, `entry_confirm_close`
  в `values/strings.xml` (ru) и `values-en/strings.xml` (en). Проверка:
  сборка `./gradlew assembleDebug` без ошибок

## 4. UI-тесты диалога (red)

- [x] 4.1 Дописать в `app/src/androidTest/.../entry/EntryCreateScreenTest.kt`
  (JUnit 4, Compose): есть изменения → тап по стрелке «назад» → виден диалог
  (заголовок «Закрыть?»); «Отмена» → диалог скрыт, контент экрана виден;
  «Закрыть» → `onBackClick` вызван; есть изменения → системная кнопка
  «назад» (`Espresso.pressBack()`, espresso-core уже в зависимостях) → виден
  тот же диалог. Нет изменений → тап по стрелке → `onBackClick` без диалога.
  Редактирование: форма изменена и валидна → тап по стрелке → диалог;
  изменена и невалидна (очищены повторы) → тап по стрелке → `onBackClick`
  без диалога (edit-условие свойства дополнительно покрыто юнит-тестами 2.2).
  Сохранение → `onBackClick` без диалога. Проверить через `make android-test`,
  что новые тесты падают (red-фаза TDD: диалога и `BackHandler` ещё нет,
  реализация — в задаче 5.1, это не регрессия)

## 5. Перехват закрытия и диалог (green)

- [x] 5.1 В `EntryCreateScreenContent`: `showConfirmDialog` через `remember`,
  `attemptClose`, `BackHandler(enabled = hasUnsavedChanges)` и маршрут стрелки
  TopAppBar через `attemptClose`; приватный `EntryCloseConfirmDialog`
  (AlertDialog, confirm c `colorScheme.error`, dismiss — `cancel`);
  путь `onSaveClick` (= `viewModel.save(onSaved = onBackClick)`) не
  меняется — закрывает экран напрямую, минуя `attemptClose`
  (design Decisions 2–4). UI-тесты из 4.1
  зелёные: `make android-test`

## 6. Preview и финальная проверка

- [x] 6.1 Добавить preview `EntryCloseConfirmDialog` в
  `EntryCreateScreenPreview.kt`: светлая + тёмная темы, `showBackground`,
  `locale = "ru"`, обёртка в `JetpackMyWorkoutsTheme`. Проверка — сборка
  и рендер превью без ошибок
- [x] 6.2 Финал: `make format && make lint && make test` — без ошибок и
  предупреждений detekt
