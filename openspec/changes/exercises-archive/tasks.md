# Tasks: exercises-archive

Порядок: TDD — тесты на поведение раньше реализации. Разделы 1–4
(domain/data/repository/VM) идут снизу вверх по слоям; разделы 5–8 —
ресурсы (локализация) и UI; разделы 9–10 — превью и регрессия.

## 1. Domain и Data: isArchived в Exercise и ExerciseEntity (TDD)

- [ ] 1.1 Тест `MappersTest`: `ExerciseEntity.toDomain()` пробрасывает
  `isArchived` (true/false); `Exercise.toEntity()` — зеркально. Запуск:
  `make test` — тест красный (поля ещё нет в data class).
- [ ] 1.2 Реализация: добавить `val isArchived: Boolean = false` в
  `Exercise` (`domain/model/Exercise.kt`) и
  `@ColumnInfo(name = "is_archived", defaultValue = "0") val isArchived:
  Boolean = false` в `ExerciseEntity` (`data/db/ExerciseEntity.kt`);
  пробросить в обе стороны в `data/db/Mappers.kt`. Запуск: тест 1.1
  зелёный.

## 2. Repository: setArchived, archivedCount, archivedExercises,
фильтрация в exercises() (TDD)

- [ ] 2.1 Тесты `RoomWorkoutDataRepositoryTest` (androidTest с
  in-memory Room по образцу соседних DAO-тестов, либо расширить
  существующий класс, если есть): `setArchived(id, true)` для активного
  упражнения → `Result.success`, упражнение пропадает из
  `observeExercises("")`; `setArchived(id, false)` для архивного →
  `Result.success`, упражнение появляется в `observeExercises("")`;
  `setArchived(999, true)` для несуществующего → `Result.failure(
  IllegalStateException("Упражнение не найдено"))`. Запуск:
  `make android-test` — красный.
- [ ] 2.2 Тест: `archivedExercises()` эмитит только упражнения с
  `is_archived = 1`, отсортированные `is_custom DESC, created_at DESC,
  name COLLATE NOCASE`; `archivedCount()` эмитит количество. Запуск:
  красный.
- [ ] 2.3 Тест: `exercises("")` НЕ возвращает архивные упражнения
  (фильтрация `WHERE is_archived = 0` в SQL); поиск по подстроке
  тоже фильтрует архивные. Запуск: красный.
- [ ] 2.4 Реализация `ExerciseDao`: добавить `WHERE is_archived = 0`
  в существующий `observeExercises`; новые методы
  `observeArchivedExercises(): Flow<List<ExerciseEntity>>` с
  `WHERE is_archived = 1` + сортировка как в `exercises()`;
  `observeArchivedCount(): Flow<Int>` с
  `SELECT COUNT(*) FROM exercises WHERE is_archived = 1`.
  Запуск: тесты 2.1–2.3 зелёные.
- [ ] 2.5 Реализация `RoomWorkoutDataRepository`: `setArchived(id,
  isArchived)` через
  `runCatching { val entity = exerciseDao.getById(id) ?: throw
  IllegalStateException("Упражнение не найдено"); exerciseDao.update(
  entity.copy(isArchived = isArchived)) }` (отсутствующий id →
  `Result.failure(IllegalStateException("Упражнение не найдено"))` —
  паттерн `RoomWorkoutDataRepository.deleteExercise` для null-check);
  `archivedExercises()` делегирует в
  `observeArchivedExercises().map { ... toDomain() }`;
  `archivedCount()` — `observeArchivedCount()`; `exercises(query)`
  уже делегирует в `observeExercises(query)` — фильтрация в SQL
  сделана в §2.4. Запуск: тесты 2.1–2.3 зелёные.
- [ ] 2.6 Обновить интерфейс `WorkoutDataRepository`: добавить
  `setArchived`, `archivedCount`, `archivedExercises`. Запуск:
  компиляция JVM + androidTest — красная до §2.7.
- [ ] 2.7 Override новых методов во ВСЕХ существующих fake-репозиториях
  (иначе §2.6 не компилируется): 5 JVM —
  `FakeExerciseRepository` (`ExerciseCreateViewModelTest.kt`),
  `ExercisesViewModelFakeRepository` (`ExercisesViewModelTest.kt`),
  `FakeWorkoutDataRepository` (`EntryCreateViewModelTest.kt`),
  `MainViewModelFakeRepository` (`MainViewModelTest.kt`),
  `AppDataViewModelFakeRepository` (`AppDataViewModelTest.kt`);
  7 androidTest fake-классов в 5 файлах —
  `MainScreenEditNavigationTest` (1, анонимный),
  `MainScreenFabNavigationTest` (1, анонимный),
  `MainScreenSectionsTest.SectionsFakeRepository` (1),
  `ExerciseCreateScreenTest.EditFakeRepository` (1),
  `ExercisesDeleteFlowTest.FakeRepository` (3 — по одному на вложенный
  тестовый класс).
  Заглушки: `setArchived` → `Result.success(Unit)` (с публичным
  счётчиком вызовов для тестов архивации);
  `archivedCount` → `MutableStateFlow(0)` (тесты §3 управляют через
  публичный сеттер); `archivedExercises` →
  `MutableStateFlow(emptyList())`. Запуск:
  `./gradlew :app:compileDebugUnitTestKotlin compileDebugAndroidTestKotlin`
  зелёная.

## 3. VM ExercisesViewModel: pending-архивация + archivedCount (JVM,
TDD)

- [ ] 3.1 Тесты `ExercisesViewModelTest` (расширить): `requestArchive(
  item)` ставит `pendingArchiveId == item.id` в `Success`;
  `cancelArchive()` сбрасывает в `null`. Запуск: `make test` —
  красный.
- [ ] 3.2 Тест: `confirmArchive()` вызывает
  `repository.setArchived(id, true)`; при `Result.success` pending
  сброшен. Запуск: красный.
- [ ] 3.3 Тест: `confirmArchive()` при `Result.failure(
  IllegalStateException("Упражнение не найдено"))` — pending сброшен,
  повторный `requestArchive` возможен (race). Запуск: красный.
- [ ] 3.4 Тест: `archivedCount` из репозитория реактивно попадает в
  `Success.archivedCount` (fake-репозиторий управляет значением
  через сеттер, добавленный в §2.7). Запуск: красный.
- [ ] 3.5 Реализация: `pendingArchiveId: MutableStateFlow<Long?>`,
  `archivedCount` через `repository.archivedCount().stateIn(
  viewModelScope, WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS), 0)` —
  приватный `stateIn`-Flow в VM; значение попадает в
  `Success.archivedCount` через `combine` (паттерн —
  `MainViewModel`/`MainUiState.Success(entries, pendingDeleteId)`);
  `uiState = combine(repository.exercises(""), pendingDeleteId,
  deleteError, pendingArchiveId, archivedCount) → Success(exercises,
  pendingDeleteId, deleteError, pendingArchiveId, archivedCount)`;
  `requestArchive(item)` → `pendingArchiveId.value = item.id`;
  `cancelArchive()` → `pendingArchiveId.value = null`;
  `confirmArchive()` → `viewModelScope.launch {
  repository.setArchived(id, true); pendingArchiveId.value = null }`.
  Расширение `ExercisesUiState.Success` полями `pendingArchiveId:
  Long? = null` и `archivedCount: Int = 0`. Запуск: тесты 3.1–3.4
  зелёные.

## 4. VM ArchivedExercisesViewModel (новый, JVM, TDD)

- [ ] 4.1 Тест `ArchivedExercisesViewModelTest`: VM подписывается на
  `repository.archivedExercises()`; до первой эмиссии — `Loading`;
  после эмиссии — `Success` со списком; новая эмиссия — обновление
  без перезапуска VM. Запуск: `make test` — красный.
- [ ] 4.2 Тест: `unarchive(item)` вызывает
  `repository.setArchived(item.id, false)`. Запуск: красный.
- [ ] 4.3 Реализация: `ArchivedExercisesViewModel(repository)` с
  `uiState = repository.archivedExercises().map { Success(it) }
  .stateIn(viewModelScope, WhileSubscribed(SUBSCRIPTION_TIMEOUT_MS),
  Loading)`; `fun unarchive(item: Exercise) = viewModelScope.launch {
  repository.setArchived(item.id, false) }`. Создать
  `ArchivedExercisesUiState` (sealed: `Loading` / `Success(exercises:
  List<Exercise>)`). Запуск: тесты 4.1–4.2 зелёные.
- [ ] 4.4 Создать `ArchivedExercisesViewModel.Companion.factory(
  repository): ViewModelProvider.Factory` по образцу
  `ExercisesViewModel.factory`.

## 5. Локализация (ru/en)

- [ ] 5.1 Добавить 9 новых строк в `res/values/strings.xml` (ru,
  default) и `res/values-en/strings.xml` (en override): ключи и их
  ru/en-значения — единый нормативный источник в спек-е
  `exercises-screen` (alert-сценарий, кнопка архива, новое меню) и
  `archived-exercises-screen` (заголовок, empty, меню «Вернуть»);
  добавлять строки в `strings.xml` строго по спекам. Проверка:
  `make lint` (MissingTranslation) зелёный.

## 6. ExercisesScreen UI: меню архивации, алерт, кнопка в TopAppBar
(androidTest, TDD)

- [ ] 6.0 `Screen.ArchivedExercises : Screen(route = "archived_exercises")`
  — добавить в `navigation/Screen.kt`. Не в `BottomTabs` (контракт —
  паттерн `Screen.Appearance`). Проверка: компиляция
  `./gradlew :app:compileDebugKotlin` зелёная. Выполняется ДО §6.1,
  чтобы §6.7.a мог компилироваться.
- [ ] 6.1 AndroidTest `ExercisesScreenTest` (расширить): long-press на
  кастомном упражнении — меню из трёх пунктов
  («Изменить»/«Архивировать»/«Удалить») в указанном порядке, строка
  подсвечена. Запуск: `make android-test` — красный.
- [ ] 6.2 AndroidTest: long-press на стандартном упражнении — меню
  из одного пункта «Архивировать»; пункты «Изменить» и «Удалить» НЕ
  показываются. Существующий тест §4.1
  `ExercisesScreenTest.row_long_press_when_standard_then_no_context_menu:268`
  ПЕРЕИМЕНОВАТЬ в `row_long_press_when_standard_then_menu_with_archive_only`
  и переписать ассерты: `Edit`/`Delete` отсутствуют, `Archive`
  присутствует, меню открыто в точке нажатия, строка подсвечена.
  Запуск: красный.
- [ ] 6.3 AndroidTest: тап «Архивировать» в меню кастомного →
  алерт «Архивировать упражнение?» с именем упражнения; тап
  «Отмена» — алерт закрыт, упражнение в списке. Запуск: красный.
- [ ] 6.4 AndroidTest: тап «Архивировать» в алерте подтверждения —
  сработал колбэк подтверждения архивации; после обновления
  `ExercisesUiState` (исключающего заархивированное упражнение
  из `success.exercises`) строка исчезает из списка. Content-based
  инфраструктура текущего `ExercisesScreenTest` (см. §6.6 — этот
  тест-класс в список 12 override-сайтов не входит): вызов
  репозитория проверить нельзя, проверяется колбэк подтверждения
  + перерисовка state. Запуск: красный.
- [ ] 6.5 AndroidTest: `archivedCount == 0` → кнопка архива в
  TopAppBar скрыта; `archivedCount == 1` → кнопка отображается с
  иконкой `Icons.Filled.Archive` и
  `contentDescription = exercises_archive_button`; тап кнопки —
  сработал колбэк `onArchivedExercisesClick`. Запуск: красный.
- [ ] 6.6 AndroidTest: реактивное появление/скрытие кнопки — после
  архивации `archivedCount` переходит с 0 на 1 (тест управляет
  `mutableStateOf<ExercisesUiState>` в `setContent` — Content-based
  инфраструктура текущего `ExercisesScreenTest` без отдельного
  FakeRepository, см. §2.7 — этот тест-класс в список 12 override-сайтов
  не входит) кнопка появляется; после восстановления (1 → 0) —
  скрывается. Запуск: красный.
- [ ] 6.7.a Реализация UI: обёртка `ExercisesScreen` принимает
  параметр `onArchivedExercisesClick: () -> Unit` и пробрасывает
  в `ExercisesScreenState.onArchivedExercisesClick`. В `RootScreen`
  пробрасывается
  `navController.navigate(Screen.ArchivedExercises.route)`.
- [ ] 6.7.b `ExerciseListItemRow` остаётся без изменений (переиспользуется
  на обоих экранах); существующий `applyLongPress` в `ExercisesScreen.kt`
  параметризуется — добавляется флаг `enabled: Boolean` (по умолчанию
  `true` — поведение не меняется: `ExercisesScreen` вызывает с
  `enabled = item.isCustom`, `ArchivedExercisesScreen` — с
  `enabled = true`). Параметр «меню доступно» поднимается до
  `internal` (видимость для повторного использования в
  `ArchivedExercisesScreen` и тестах). Никакого второго
  `applyLongPressForArchive` — одна функция, два вызывающих сайта
  (см. §7.5). Пункты «Архивировать» добавляются через расширенный
  `ExerciseContextMenu`, см. §6.7.c.
- [ ] 6.7.c `ExerciseContextMenu` получает параметры `isCustom: Boolean`,
  `onEditClick: () -> Unit`, `onArchiveClick: () -> Unit`,
  `onDeleteClick: () -> Unit`. Рендерит 3 пункта (Edit / Archive /
  Delete) для `isCustom == true` или 1 пункт «Архивировать» для
  `isCustom == false`. Экран архива использует отдельный composable
  `ExerciseUnarchiveContextMenu(onUnarchive: () -> Unit)` (см. §7.5) —
  не расширять `ExerciseContextMenu` флагом режима. Запуск: тесты
  6.1–6.2 зелёные.
- [ ] 6.7.d `ExerciseArchiveDialog` — `AlertDialog` с заголовком
  `exercise_archive_dialog_title`, текстом
  `exercise_archive_dialog_text` (с `%1$s`), кнопкой отмены
  `exercise_archive_dialog_cancel`, кнопкой подтверждения
  `exercise_archive_dialog_confirm` в дефолтном цвете
  (`ButtonDefaults.textButtonColors()` без `colorScheme.error`).
  Запуск: тесты 6.3–6.4 зелёные.
- [ ] 6.7.e `SuccessBody` — рендер алерта архивации при
  `pendingArchiveId != null` (поиск упражнения в `success.exercises`
  через `firstOrNull { it.id == pendingArchiveId }`); новый пункт
  меню «Архивировать» (`onArchiveClick`) вызывает `viewModel.requestArchive(item)`.
- [ ] 6.7.f `TopAppBar` в `ExercisesScreenContent` получает
  `actions = { if (success.archivedCount > 0)
  IconButton(onClick = state.onArchivedExercisesClick) { Icon(
  Icons.Filled.Archive, contentDescription = stringResource(
  R.string.exercises_archive_button)) } }`. Запуск: тесты 6.5–6.6
  зелёные.

## 7. RootScreen NavHost + ArchivedExercisesScreen (androidTest,
TDD)

- [ ] 7.1 AndroidTest `ArchivedExercisesScreenTest` (новый):
  VM-обёртка с пустым архивом — заглушка «В архиве пусто» по центру.
  Запуск: `make android-test` — красный.
- [ ] 7.2 AndroidTest: VM с одним архивным упражнением — список из
  одной строки; long-press → меню с пунктом «Вернуть». Запуск:
  красный.
- [ ] 7.3 AndroidTest: тап «Вернуть» — fake-репозиторий вызвал
  `setArchived(id, false)`; реактивный список обновился, упражнение
  пропало. Запуск: красный.
- [ ] 7.4 AndroidTest: тап стрелки «назад» в TopAppBar — сработал
  `onBackClick`. Запуск: красный.
- [ ] 7.5 Реализация `ArchivedExercisesScreen.kt` (новый файл) —
  паттерн `AppearanceScreen`: VM-обёртка + `ArchivedExercisesState`
  + `ArchivedExercisesScreenContent`. `TopAppBar` с заголовком
  `archived_exercises_title` и `navigationIcon` со стрелкой назад.
  Тело — `LazyColumn` (пустое состояние → заглушка, иначе список
  через `ExerciseListItemRow` + параметризованный `applyLongPress`
  из §6.7.b с `enabled = true`).
  Используется отдельный composable
  `ExerciseUnarchiveContextMenu(onUnarchive: () -> Unit)` с
  единственным пунктом «Вернуть» (`Icons.Filled.Unarchive`) —
  чтобы не плодить `showArchiveOnly: Boolean`-флаг в общем
  `ExerciseContextMenu`. Запуск: тесты 7.1–7.4 зелёные.
- [ ] 7.6 `RootScreen`: добавить `composable(Screen.ArchivedExercises
  .route) { ArchivedExercisesScreen(viewModel = viewModel(factory =
  ArchivedExercisesViewModel.factory(repository)), onBackClick = {
  navController.popBackStack() }) }` в `NavHost` (выполняется ПОСЛЕ
  §7.5, чтобы composable-ссылка на `ArchivedExercisesScreen`
  разрешалась на компиляции). Проверка: `./gradlew
  :app:compileDebugKotlin` зелёная; все androidTest зелёные.

## 8. EntryCreateScreen: фильтрация архивных (TDD)

- [ ] 8.1 JVM `EntryCreateViewModelTest` (расширить): в `initialState`
  задан `selectedExercise = X` и `fakeRepository.catalog` содержит
  `[X, Y, ...]`; после `fakeRepository.setArchived(X.id, true)`
  VM-`uiState.exercises` реактивно обновляется и НЕ содержит X,
  `uiState.selectedExercise` остаётся равным X (form-state не
  пересчитывается из каталога). Используется существующий
  `FakeWorkoutDataRepository:40` — добавить override `setArchived`
  и сеттер `archivedIds` для управления `catalog`. Запуск: `make
  test` — красный.
- [ ] 8.2 androidTest `EntryCreateScreenTest` (расширить):
  `mutableStateOf<EntryCreateUiState>` с `selectedExercise = X` и
  `exercises = [X, Y, ...]`; при перерисовке state заменить
  `exercises = [Y, ...]` (имитация реактивного обновления после
  архивации, без VM/repo) — X продолжает отображаться как текущий
  выбор упражнения формы (поле выбора показывает X, в выпадающем
  списке X отсутствует). Запуск: `make android-test` — красный.
- [ ] 8.3 Проверка: SQL-фильтрация `WHERE is_archived = 0` уже
  покрыта `RoomWorkoutDataRepositoryTest` §2.3; никаких правок в
  `EntryCreateViewModel`/`EntryCreateScreen`/`EntryCreateUiState` не
  нужно — фильтрация приходит через `repository.exercises(query)`
  (раздел 2).

## 9. Превью

- [ ] 9.1 Превью `ExerciseArchiveDialog` — в соседнем
  `ExercisesScreenPreview.kt`, светлая + тёмная
  (`UI_MODE_NIGHT_YES`), `showBackground = true, locale = "ru"`,
  обёртка `JetpackMyWorkoutsTheme`. Проверка: компиляция зелёная.
- [ ] 9.2 Превью `ArchivedExercisesScreenContent` — состояния:
  пустое (заглушка), со списком (одна/две строки, в т.ч. подсвеченная).
  Светлая + тёмная, `showBackground = true, locale = "ru"`,
  `JetpackMyWorkoutsTheme`. Проверка: компиляция зелёная.
- [ ] 9.3 Обновить `ExerciseContextMenuPreview` в
  `ExercisesScreenPreview.kt` — два состояния рядом в `Column`:
  3 пункта (Edit / Archive / Delete, `isCustom = true`) и
  1 пункт «Архивировать» (`isCustom = false`). Светлая + тёмная,
  `showBackground = true, locale = "ru"`, `JetpackMyWorkoutsTheme`.
  Проверка: компиляция зелёная, превью видны в Android Studio.
- [ ] 9.4 Превью `ExerciseUnarchiveContextMenu` — в соседнем
  `ArchivedExercisesScreenPreview.kt` (или
  `ExercisesScreenPreview.kt`), один пункт «Вернуть»
  (`Icons.Filled.Unarchive`). Светлая + тёмная,
  `showBackground = true, locale = "ru"`, `JetpackMyWorkoutsTheme`.
  Проверка: компиляция зелёная.
- [ ] 9.5 В `ExercisesScreenPreview.kt` — превью
  `ExercisesScreenContent` с `archivedCount = 1` (кнопка архива в
  `TopAppBar` видна, `Icons.Filled.Archive`,
  `contentDescription = exercises_archive_button`) и
  `archivedCount = 0` (кнопка скрыта) — рядом в `Column`. Светлая +
  тёмная, `showBackground = true, locale = "ru"`,
  `JetpackMyWorkoutsTheme`. Ключевые состояния компонента — рядом
  (AGENTS.md, раздел Conventions, блок «Preview»). Проверка:
  компиляция зелёная, превью видны в Android Studio.

## 10. Регрессия и финальные проверки

- [ ] 10.1 `make format` — без диффа после форматирования.
- [ ] 10.2 `make lint` (detekt) — зелёный.
- [ ] 10.3 `make test` — все JVM-тесты зелёные (включая новые
  `ArchivedExercisesViewModelTest` и расширенные
  `ExercisesViewModelTest`).
- [ ] 10.4 `make android-test` — все инструментальные тесты
  зелёные (включая существующие `ExercisesScreenTest`/`EntryCreateScreenTest`/
  `ExerciseCreateScreenTest` регрессионные сценарии, новый
  `ArchivedExercisesScreenTest`).
- [ ] 10.5 Ручная проверка на эмуляторе (опционально, `make install`):
  long-press кастомного/стандартного, алерт архивации, кнопка в
  TopAppBar, экран архива, возврат упражнения, фильтрация архивных
  в picker `EntryCreateScreen`.
