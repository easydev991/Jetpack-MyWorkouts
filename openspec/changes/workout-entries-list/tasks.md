# Tasks: workout-entries-list

Порядок — TDD (AGENTS.md): тесты на логику раньше реализации.
Верификация каждой задачи — прогон соответствующего тест-класса.

## 1. Слой данных: реактивный список записей

- [ ] 1.1 Red (androidTest): `ExerciseEntryDaoTest` — наблюдение
  списка записей: пустая БД → пустой список; вставка двух записей с
  разными датами → обе в проекции с названием упражнения (JOIN) и
  порядком по дате убыванию. Прогнать — падает
- [ ] 1.2 Green (main): `ExerciseEntryDao.observeEntries()` — JOIN
  `exercise_entries`/`exercises`, projection `EntryListItemEntity`,
  `ORDER BY date DESC`; доменный `EntryListItem` и extension-маппер
  `EntryListItemEntity.toListItem()` в `data/db/Mappers.kt`
  (по образцу `toDomain`, design Decision 2). Прогнать 1.1 — зелёный
- [ ] 1.3 Red (androidTest): `RoomWorkoutDataRepositoryTest` —
  `entries()` возвращает доменные `EntryListItem` (поля, порядок,
  название упражнения). Прогнать — падает (метода нет)
- [ ] 1.4 Green (main): `WorkoutDataRepository.entries(): Flow
  <List<EntryListItem>>` + реализация в `RoomWorkoutDataRepository`:
  flow DAO → extension-маппер из 1.2 (design Decision 2).
  Прогнать 1.3 — зелёный
- [ ] 1.5 Red (JVM): `EntryValidatorTest` — по measuredBy (design
  Decision 7): REPS без повторов → failure `MISSING_REPS`; TIME без
  длительности → failure `MISSING_DURATION`; TIME с длительностью 0 →
  failure `INVALID_DURATION`; REPS с повторами = 0 → failure
  `INVALID_REPS` (существующее правило сохраняется); повторы и
  длительность заданы одновременно → failure
  `INCOMPATIBLE_REPS_AND_DURATION`; существующие вызовы в тестах
  перевести на новую сигнатуру `validate(entry, measuredBy)`.
  Прогнать — падает
- [ ] 1.6 Red (androidTest): `RoomWorkoutDataRepositoryTest` —
  `addEntry`/`updateEntry` записи TIME-упражнения без длительности →
  failure, записи в БД нет; REPS-упражнения без повторов → failure;
  две идентичные записи → обе сохраняются отдельными строками
  (дедупликации нет); сценарий «слой данных не навязывает
  соответствие» из старого теста заменить на противоположный.
  Прогнать — падает
- [ ] 1.7 Green (main): сигнатура `validate(entry, measuredBy)` +
  коды `MISSING_REPS`/`MISSING_DURATION`/`INVALID_DURATION`/
  `INCOMPATIBLE_REPS_AND_DURATION`;
  существующие `INVALID_SETS`/`INVALID_REPS`/`INVALID_WEIGHT`
  сохраняются как есть;
  `addEntry`/`updateEntry` грузят упражнение по `exerciseId`
  (`ExerciseDao.getById`, несуществующее → `Result.failure(
  IllegalStateException("Упражнение не найдено"))` по образцу
  `deleteExercise`) и передают `measuredBy` валидатору. Прогнать 1.5,
  1.6 и существующие тесты валидатора/репозитория — зелёные

## 2. Форматтер значения записи

- [ ] 2.1 Red (JVM): `EntryValueFormatterTest` — «4 х 10» при
  4×10; «10» при 1×10; «3 х 20» при 3×20 сек; «20» при 1×20 сек
  (spec «Формат значения записи»; сигнатура `formatEntryValue(sets,
  value: Int, separator)` — null-логика в caller'е, design
  Decision 3). Прогнать — падает
- [ ] 2.2 Green (main): чистая функция `formatEntryValue(sets,
  value, separator)` в `ui/screens/main/` (design Decision 3).
  Прогнать 2.1 — зелёный

## 3. ViewModel главного экрана

- [ ] 3.1 Red (JVM): `MainViewModelTest` на fake-репозитории —
  состояние Loading → Success(список); пустой список → флаг пустого
  состояния; `requestDelete` выставляет запись в `pendingDeleteId`;
  `confirmDelete` вызывает `deleteEntry` и очищает pending;
  `cancelDelete` очищает без удаления (design Decision 4). Прогнать —
  падает
- [ ] 3.2 Green (main): `MainViewModel` + `MainUiState` (sealed
  Loading/Success, пустота — флагом), `stateIn(WhileSubscribed(5000))`,
  factory по образцу `EntryCreateViewModel`. Прогнать 3.1 — зелёный

## 4. Строки ru/en

- [ ] 4.1 Добавить строки: значение-разделитель « х »/« x »,
  `entry_list_menu_delete` («Удалить»/«Delete»), заголовок и текст
  алерта удаления («Удалить запись?»/«Delete entry?», текст с
  названием), кнопки («Удалить»/«Отмена», «Delete»/«Cancel»),
  `entry_list_empty` («Добавьте первую запись»/«Add your first entry»).
  Сборка `./gradlew :app:compileDebugKotlin`

## 5. Экран: список, меню, алерт, пустое состояние

- [ ] 5.1 Red (androidTest): `MainScreenTest` — при записях отображаются
  строки (название и значение); при пустом списке — заглушка
  «Добавьте первую запись» и строк нет. Прогнать — падает
- [ ] 5.2 Green (main): переиспользуемый `EntryListItemRow` (слева
  название, справа значение (design Decision 5), `LazyColumn`
  с `key = { it.id }`, заглушка
  пустого состояния. Preview светл/тём. Прогнать 5.1 — зелёный
- [ ] 5.3 Red (androidTest): long-press по строке → меню с пунктом
  «Удалить» (в точке нажатия — проверка наличия меню и пункта);
  выбор «Удалить» → алерт с «Удалить?»/«Отмена»; отмена → алерт
  закрыт, запись на месте; подтверждение → алерт закрыт и в
  fake/VM-слое удаление вызвано (design Decision 5). Прогнать — падает
- [ ] 5.4 Green (main): `detectTapGestures.onLongPress` +
  `onGloballyPositioned` → `DropdownMenu(offset)` на уровне списка
  (один пункт), `AlertDialog` подтверждения (confirm в
  `colorScheme.error`), связка с `MainViewModel`
  (request/confirm/cancel). Preview. Прогнать 5.3 — зелёный
- [ ] 5.5 Интеграция: `MainScreen` получает `MainViewModel` параметром
  по образцу `EntryCreateScreen` (`RootScreen.kt`: `viewModel(factory =
  MainViewModel.factory(repository))`), `RootScreen` передаёт
  репозиторий. Обновить `MainScreenFabNavigationTest` под новую
  сигнатуру: передача `MainViewModel` через factory с in-memory
  репозиторием, FAB-сценарий сохраняется (red — не компилируется,
  green — тест зелёный). Ручная проверка на эмуляторе: сохранённая
  запись видна первой, long-press → меню в точке нажатия → удаление
  с алертом, пустое состояние при отсутствии записей

## 6. Полный прогон

- [ ] 6.1 `make format && make lint && make test && make android-test` —
  всё зелёное
- [ ] 6.2 Синхронизация документации: проверить соответствие спек
  (`workouts-main-screen`, `workout-data`) факту после реализации,
  `openspec validate workout-entries-list`
