# Tasks

## 1. Тесты слоя данных (TDD — сначала тесты)

- [x] 1.1 Новый JVM-тест `ExerciseIconsTest`: `resolve(1)`, `resolve(18)`, `resolve(19)` возвращают `Ref`; `resolve(null)`, `resolve(99)`, `resolve(0)` → `null`. Проверка: `./gradlew testDebugUnitTest --tests "*ExerciseIcons*"` — тесты зелёные (не TDD — покрытие существующего контракта)
- [x] 1.2 `MappersTest`: маппинг `toListItem` сохраняет `iconNumber` — `EntryListItemEntity(iconNumber = ExerciseIcons.PULLUPS).toListItem()` возвращает `EntryListItem` с `iconNumber = PULLUPS`; с `iconNumber = null` возвращает `null` — падает (поля ещё нет). Проверка: запуск теста красный по отсутствию поля
- [x] 1.3 androidTest `RoomWorkoutDataRepositoryTest`: сценарий `entries_exerciseIconNumber_reachesListItem` (имя отличать от существующего `addExercise_iconNumber_isPersistedAndDistinguishableFromNull` — тот проверяет round-trip через каталог, этот — проекцию) — иконка упражнения читается JOIN-ом и попадает в `EntryListItem`. Проверка: тест падает (иконка не приходит)
- [x] 1.4 androidTest `WorkoutDatabaseTest`: утверждение prepopulate переписано с «все 13 с `iconNumber = null`» на проверку назначенных номеров по таблице design Decision 5. Проверка: тест падает до реализации

## 2. Реализация слоя данных

- [x] 2.1 `EntryListItem.iconNumber: Int? = null`; поле в `EntryListItemEntity`; `icon_number` добавлен в SELECT проекции (`ExerciseEntryDao`, JOIN `exercises`); `Mappers.toListItem()` пробрасывает значение. Проверка: тест 1.2 зелёный (`make test`); тест 1.3 зелёный (`make android-test`)
- [x] 2.2 `StandardExercises.kt`: проставить 13 номеров по таблице design Decision 5. Проверка: тест 1.4 зелёный (`make android-test`)
- [x] 2.3 Полный прогон JVM: `make test` — статистика без падений

## 3. Общий рендер иконки

- [x] 3.1 `internal @Composable ExerciseIcon(iconNumber, modifier)` в `ui/icons/`: `resolve` → светлый/тёмный вариант по `isSystemInDarkTheme()`, `Image` 24dp без tint, `contentDescription = null`, `null` → ничего не рисует (design Decision 4). Превью светлое/тёмное для состояний «иконка есть» / «иконки нет». Проверка: компиляция `./gradlew compileDebugKotlin`, превью видны в IDE

## 4. Главный экран — строка записи

- [x] 4.1 `TitleValueRow` в `EntryListItemRow.kt`: `ExerciseIcon` перед `Text(exerciseName)`, `Text` сохраняет `weight(1f)`, значение у правого края; фикстуры `EntryListItemRowPreview`/`MainScreenPreview` дополнены `iconNumber` по смыслу. Проверка: превью с иконкой и без (ключевые состояния отдельными `@Preview`)
- [x] 4.2 androidTest главного экрана: строка записи упражнения с известным номером показывает иконку (тёмная тема → тёмный вариант), строка без `iconNumber` — без иконки, запись с номером вне таблицы — иконка не отображается без падения. Проверка: `make android-test` — тесты зелёные

## 5. Экран упражнений — строка списка

- [x] 5.1 `ExerciseListItemRow` в `ExercisesScreen.kt`: `ExerciseIcon` в верхнем Row перед названием; лейбл `measuredBy` остаётся у правого края; превью-фикстуры обновлены. Проверка: превью с иконкой и без
- [x] 5.2 androidTest экрана упражнений: иконка в строке списка по тем же правилам (тема-вариант, отсутствие при `null`). Проверка: `make android-test` — тесты зелёные
- [x] 5.3 androidTest `ArchivedExercisesScreen`: строка архивного упражнения с назначенной иконкой показывает её — наследование от `ExerciseListItemRow` без отдельной правки (design Decision 4). Проверка: `make android-test` — тесты зелёные

## 6. Экран записи — выпадающий список

- [x] 6.1 `ExerciseDropdownField` в `EntryCreateFields.kt`: `leadingIcon = { ExerciseIcon(...) }` в `DropdownMenuItem`; опция без `iconNumber` — только текст. Проверка: компиляция, превью dropdown с иконкой и без
- [x] 6.2 androidTest экрана записи: опция выпадающего списка показывает иконку; опция без `iconNumber` — только текст (тёмная тема → тёмный вариант); архивное исходное упражнение в режиме редактирования сохраняет иконку. Проверка: `make android-test` — тесты зелёные

## 7. Финальная верификация

- [x] 7.1 `make format && make lint && make test` — зелёные; detekt без новых предупреждений (лимит maxIssues: 5 не повышается)
- [x] 7.2 `make android-test` на подключённом эмуляторе — все инструментальные тесты зелёные (один прогон, один отчёт `scripts/test_report.py`)

## 8. Архивация

- [x] 8.1 При архивации change переименовать сценарий «Стандартный набор без иконок при prepopulate» в «Стандартный набор prepopulate с номерами иконок» в `openspec/specs/workout-data/spec.md` — переименовывать в дельте нельзя (валидатор требует сохранения имён сценариев MODIFIED-блока), правка основной спеки допустима только после архивации. Проверка: заголовок сценария в основной спеке обновлён
