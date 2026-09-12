# Tasks: edit-delete-exercise

Порядок: TDD — тесты на поведение раньше реализации. Разделы 1–3
(JVM/ресурсы) не зависят друг от друга по данным, но идут от слоя данных
к UI; разделы 4–8 — UI-слои сверху вниз; 9–10 — превью и регрессия.

## 1. Репозиторий: getExercise (TDD)

- [x] 1.1 Тест: `getExercise(id)` возвращает `Result.success(Exercise)` с
  domain-моделью при существующем id — добавить в существующий тест-класс
  репозитория (тот же уровень, где тестируются `getEntry`/`deleteExercise`;
  если класса нет — androidTest с in-memory Room по образцу соседних
  DAO-тестов). Запуск: `make test` или `make android-test` — тест красный.
- [x] 1.2 Тест: `getExercise(id)` при несуществующем id возвращает
  `Result.success(null)` (не failure) — рядом с 1.1. Запуск: тест красный.
- [x] 1.3 Реализация: `suspend fun getExercise(id: Long): Result<Exercise?>`
  в `WorkoutDataRepository` + `runCatching { exerciseDao.getById(id)
  ?.toDomain() }` в `RoomWorkoutDataRepository` (образец — `getEntry`).
  Запуск: тесты 1.1–1.2 зелёные.
- [x] 1.4 Override `getExercise` во ВСЕХ существующих fake-репозиториях
  (иначе §1.3 не компилируется): 5 JVM —
  `FakeExerciseRepository` (`ExerciseCreateViewModelTest.kt:36`),
  `ExercisesViewModelFakeRepository` (`ExercisesViewModelTest.kt:36`),
  `FakeWorkoutDataRepository` (`EntryCreateViewModelTest.kt:40`),
  `MainViewModelFakeRepository` (`MainViewModelTest.kt:42`),
  `AppDataViewModelFakeRepository` (`AppDataViewModelTest.kt:50`);
  3 androidTest fake-репозитория — `MainScreenEditNavigationTest.kt:88`
  (anonymous), `MainScreenFabNavigationTest.kt:51` (anonymous) и
  `MainScreenSectionsTest.kt:277` (named `private class
  SectionsFakeRepository`).
  Заглушка `Result.success(null)` — упрощённо, соответствует поведению
  fake при отсутствии упражнения (см. requirement «Edit-режим: загрузка,
  предзаполнение и hasChanges», scenario «Упражнение не найдено —
  подсказка и disabled Save»). Проверка:
  `./gradlew :app:compileDebugUnitTestKotlin compileDebugAndroidTestKotlin`
  зелёная.
- [x] 1.5 Репозиторий: typed errors для `deleteExercise` +
  pre-check FK (design Decisions 3, 8). Создать
  `app/src/main/java/com/myworkouts/domain/model/DeleteExerciseError.kt`
  со sealed-классом `DeleteExerciseError { object HasLinkedEntries;
  object StandardExercise; object Other }`.
  В `ExerciseEntryDao.kt` добавить
  `@Query("SELECT COUNT(*) FROM exercise_entries WHERE exercise_id = :id")
  suspend fun countByExerciseId(id: Long): Int` И
  `@Query("SELECT exercise_id FROM exercise_entries GROUP BY exercise_id")
  fun observeExerciseIdsWithLinkedEntries(): Flow<List<Long>>`.
  В `WorkoutDataRepository` — `fun exerciseIdsWithLinkedEntries():
  Flow<Set<Long>>` (делегирует в DAO). В
  `RoomWorkoutDataRepository.deleteExercise` —
  `getById(id)` → если `null` → `Result.failure(
  IllegalStateException("Упражнение не найдено"))` (race — попадает
  в `Other` через VM-маппинг Decision 3); если
  `!isCustom` → `Result.failure(
  StandardExerciseDeletionException())`; если
  `exerciseEntryDao.countByExerciseId(id) > 0` → `Result.failure(
  LinkedEntriesDeletionException())`; затем `exerciseDao.delete(...)`.
  Исключения объявить в `data/repository/DeletionExceptions.kt`
  (`class StandardExerciseDeletionException : IllegalStateException(
  "Стандартное упражнение нельзя удалить")` и аналогично для FK).
  Override в 8 fake-репозиториях из §1.4: `deleteExercise` — теперь
  четыре исхода: `Result.success`, `Result.failure(
  LinkedEntriesDeletionException())`, `Result.failure(
  StandardExerciseDeletionException())`, `Result.failure(
  IllegalStateException("other"))`;
  `exerciseIdsWithLinkedEntries` — `MutableStateFlow(emptySet())`
  (тесты §2.5/§4.8 управляют через публичный сеттер). Запуск:
  `make test` + `compileDebugAndroidTestKotlin` зелёные; тесты §2.5/§4.8
  — красные до §2.4.

## 2. ExercisesViewModel: pending-удаление (JVM, TDD)

- [x] 2.1 Тесты `ExercisesViewModelTest` (fake-репозиторий на
  MutableStateFlow): `requestDelete(item)` для упражнения БЕЗ записей
  (нет в `exerciseIdsWithLinkedEntries`) ставит
  `pendingDeleteId == item.id` в `Success`; `cancelDelete()`
  сбрасывает в `null`. Запуск: `make test` — красный.
- [x] 2.2 Тест: `confirmDelete()` вызывает
  `repository.deleteExercise(id)`; при `Result.success` pending сброшен,
  `deleteError == null` (one-shot не сработал). Запуск: красный.
- [x] 2.3 Тест: `confirmDelete()` при `Result.failure(
  LinkedEntriesDeletionException())` — pending сброшен, `deleteError ==
  DeleteExerciseError.HasLinkedEntries`; повторный `requestDelete`
  возможен. Запуск: красный.
- [x] 2.4 Реализация: `pendingDeleteId: MutableStateFlow<Long?>`,
  `deleteError: MutableStateFlow<DeleteExerciseError?>`;
  `exerciseIdsWithLinkedEntries` через
  `repository.exerciseIdsWithLinkedEntries().stateIn(viewModelScope,
  WhileSubscribed(5000), emptySet())` — приватное поле VM (не в
  UiState, не нужно UI);
  `uiState = combine(repository.exercises(""), pendingDeleteId,
  deleteError) → Success(exercises, pendingDeleteId, deleteError)`;
  `requestDelete(item)`: если `item.id in exerciseIdsWithLinkedEntries
  .value` → `deleteError.value = HasLinkedEntries`, иначе
  `pendingDeleteId.value = item.id` (design Decision 8);
  `confirmDelete()`: при `success` → pending сброшен, deleteError=null;
  при `failure(LinkedEntriesDeletionException)` → pending сброшен,
  `deleteError = HasLinkedEntries`; при
  `failure(StandardExerciseDeletionException)` → `deleteError =
  StandardExercise`; при `failure(прочее)` →
  `deleteError = Other`. Каждая failure-ветка логирует текст
  исключения через `Log.e("ExercisesViewModel", message)` (design
  Decision 3 — спек Scenario «FK-ошибка» требует для FK, прочие
  ветки логируются по общему правилу Decision 3);
  `onErrorShown()` → `deleteError.value = null` (one-shot —
  Decision 6). Запуск: тесты 2.1–2.3 зелёные.

## 3. Строковые ресурсы (ru/en)

- [x] 3.1 Добавить 11 строк в `values/strings.xml` (ru, default) и
  `values-en/strings.xml` (en override): `exercise_edit_title`,
  `exercise_list_menu_edit`, `exercise_list_menu_delete`,
  `exercise_delete_dialog_title`, `exercise_delete_dialog_text`,
  `exercise_delete_dialog_confirm`, `exercise_delete_dialog_cancel`,
  `exercise_delete_error_used_in_workouts`,
  `exercise_delete_error_standard`,
  `exercise_delete_error_generic`,
  `exercise_hint_exercise_missing`. Каталог ключей и ru/en-значения —
  единый нормативный источник в спек-ах `exercises-screen` (alert-
  сценарий + снекбар + Decision 3 mapping) и `exercise-create-screen`
  (заголовок edit, подсказка `isExerciseMissing`); здесь не дублируем.
  Проверка: `make lint` (MissingTranslation) зелёный.

## 4. ExercisesScreen: long-press меню, алерт, снекбар (androidTest, TDD)

- [x] 4.1 AndroidTest `ExercisesScreenTest`: long-press на строке
  стандартного упражнения — меню не появляется (нет узлов «Изменить»/
  «Удалить»), строка не подсвечена. Запуск: `make android-test` — красный.
- [x] 4.2 AndroidTest: long-press на кастомном — меню из двух пунктов
  («Изменить», «Удалить») у точки нажатия, строка подсвечена. Запуск:
  красный.
- [x] 4.3 AndroidTest: тап «Удалить» → алерт «Удалить упражнение?» с
  именем упражнения; тап «Отмена» — алерт закрыт, упражнение в списке.
  Запуск: красный.
- [x] 4.4 AndroidTest: подтверждение «Удалить» → упражнение исчезло из
  списка (fake-репозиторий выполняет удаление). Запуск: красный.
- [x] 4.5 AndroidTest: тап «Изменить» — сработал колбэк навигации с
  exerciseId (счётчик в тестовом state/VM). Запуск: красный.
- [x] 4.6.a Реализация UI: обёртка `ExercisesScreen` принимает параметр
  `onEditExercise: (Long) -> Unit` и пробрасывает его в
  `ExercisesScreenState.onEditExercise`; обёртка хранит
  `var contextMenu by remember { mutableStateOf<ContextMenuState?>(null) }`
  и передаёт в `ExercisesScreenState.contextMenu: ContextMenuState? = null`
  (паттерн MainScreen.kt:74–87). В `RootScreen` пробрасывается
  `navigate(Screen.ExerciseCreate.createRoute(id))`. Запуск: тест 4.5
  зелёный.
- [x] 4.6.b `ExerciseListItemRow`: `onGloballyPositioned` +
  `detectTapGestures(onLongPress)` только при `isCustom` (образец
  `MainScreen.kt:343–351`, дословно, без кастомного timeout —
  дефолтный platform long-press ≈500ms), подсветка
  через `animateColorAsState` + `semantics { selected }`. Запуск:
  тесты 4.1–4.2 зелёные.
- [x] 4.6.c Internal `ExerciseContextMenu` (`DropdownMenu`: Edit/Delete,
  строки `exercise_list_menu_*`) и `ExerciseDeleteDialog` (AlertDialog
  по образцу `EntryDeleteDialog`, confirm в `colorScheme.error`).
  Запуск: тесты 4.3–4.4 зелёные.
- [x] 4.6.d `SuccessBody` — рендер меню у якоря, алерт при непустом
  `pendingDeleteId`, снекбар при ненулевом `deleteError` через
  `LaunchedEffect(uiState)` с маппингом
  `DeleteExerciseError → stringResource(...)` (канон маппинга — норматив
  в спек-е `exercises-screen`, requirement «Ошибка удаления —
  типизированные причины в снекбаре») + вызов
  `viewModel.onErrorShown()`. Маппинг находится на стороне UI, но
  решение о типе ошибки — в VM (`DeleteExerciseError` sealed-тип), UI НЕ
  парсит текст исключения (design Decision 3).
  `val snackbarHostState = remember { SnackbarHostState() }` в обёртке
  `ExercisesScreen`, передаётся параметром в `ExercisesScreenContent`,
  где живёт `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })`
  (Material3 default
  `SnackbarDuration.Short`, без явного `duration` — design Decision 6).
  Запуск: тесты 4.5, 4.7, 4.8 зелёные.
- [x] 4.7 AndroidTest (typed error → конкретный снекбар): параметризованный
  тест по трём причинам failure (FK / Standard / Other):
  fake-репозиторий возвращает `Result.failure(LinkedEntriesDeletionException())`
  → виден снекбар «Это упражнение уже используется в тренировках»;
  `Result.failure(StandardExerciseDeletionException())` → «Нельзя удалить
  стандартное упражнение»; `Result.failure(IllegalStateException("io"))` →
  «Не удалось удалить упражнение». После вызова
  `viewModel.onErrorShown()` — проверка: `state.deleteError == null`,
  повторный `requestDelete` + `confirmDelete` НЕ показывает снекбар.
  Запуск: красный.
- [x] 4.8 AndroidTest (pre-check UX): fake-репозиторий отдаёт
  `exerciseIdsWithLinkedEntries = setOf(item.id)` (через публичный сеттер
  fake из §1.5); long-press на этом кастомном → тап «Удалить» — меню
  закрывается, алерт НЕ появляется, сразу виден снекбар «Это упражнение
  уже используется в тренировках», упражнение в списке. Запуск: красный.

## 5. Маршрут ExerciseCreate с query-параметром

- [x] 5.1 `Screen.ExerciseCreate` →
  `route = "exercise_create?exerciseId={exerciseId}"`, константы
  `ARG_EXERCISE_ID: String = "exerciseId"` и
  `NO_EXERCISE_ID: Long = -1L`, `val arguments = listOf(navArgument(
  ARG_EXERCISE_ID) { type = NavType.LongType; defaultValue =
  NO_EXERCISE_ID })`, фабрика
  `createRoute(exerciseId: Long = NO_EXERCISE_ID)` (образец —
  `Screen.EntryCreate`). Проверка: компиляция `./gradlew :app:
  compileDebugKotlin` зелёная. После этого изменения FAB-навигация
  в `RootScreen.kt:143` пока не переключена — переключение в §5.3.
- [x] 5.2 `RootScreen`: `composable(Screen.ExerciseCreate.route,
  arguments = Screen.ExerciseCreate.arguments)` — читать
  `exerciseId = backStackEntry.arguments?.getLong(ARG_EXERCISE_ID,
  Screen.ExerciseCreate.NO_EXERCISE_ID) ?: NO_EXERCISE_ID`, передать в
  фабрику `ExerciseCreateViewModel.factory(repository, exerciseId)`
  (параметр добавлен в VM/фабрику уже здесь — транзитно, без логики,
  иначе §5.2 не компилируется; edit-логика — §7); подключить
  `onEditExercise` из раздела 4.6. Проверка:
  `make android-test` — существующие навигационные тесты зелёные.
- [x] 5.3 Переключить FAB-навигацию в `RootScreen.kt:143` с
  `navigate(Screen.ExerciseCreate.route)` на
  `navigate(Screen.ExerciseCreate.createRoute())` — иначе FAB упадёт на
  Long-парсинге placeholder'а `{exerciseId}` (риск-точка по образцу
  архивного `openspec/changes/archive/2026-09-06-entry-edit-mode/design.md:158–164`,
  где та же правка зафиксирована явно). Проверка: `make android-test` —
  навигация с каталога на форму создания работает; ручная проверка
  через эмулятор (опционально).
- [x] 5.4 Обновить навигационный androidTest
  `app/src/androidTest/java/com/myworkouts/ui/screens/exercises/
  ExercisesScreenTest.kt`: строка 101 — `navController.navigate(
  Screen.ExerciseCreate.route)` → `navController.navigate(
  Screen.ExerciseCreate.createRoute())` (иначе упадёт на Long-парсинге
  placeholder'а `{exerciseId}` в новом route-шаблоне); регистрация
  заглушки-destination — с `arguments = Screen.ExerciseCreate.arguments`.
  Ассерт `currentDestination?.route` оставлен на сравнении с
  `Screen.ExerciseCreate.route` (шаблоном, НЕ `createRoute()`):
  destination хранит route-шаблон, прецедент —
  `MainScreenFabNavigationTest:135–137`; подтверждено прогоном
  (10/10 зелёные; сравнение с `createRoute()` упало бы). Проверка:
  `make android-test` — `ExercisesScreenTest` зелёный.

## 6. ExerciseCreateUiState: hasChanges / canSave (JVM, TDD)

- [x] 6.1 Тесты UiState: `canSave == false` при `isEditMode == true`,
  валидной форме и `hasChanges == false`; `canSave == true` при
  `hasChanges == true`; `canSave == false` при `isExerciseMissing ==
  true` независимо от полей формы (guard `&& !isExerciseMissing`);
  `canSave` в create-режиме не изменился. Запуск: `make test` — красный.
- [x] 6.2 Тесты `hasUnsavedChanges`: create — прежняя формула
  (name/category/comment); edit — `hasChanges` (предзаполненные поля не
  считаются изменёнными). Запуск: красный.
- [x] 6.3 Реализация: поля `isEditMode = false`, `hasChanges = false`,
  `isExerciseMissing = false`; `canSave = !name.isNullOrBlank() &&
  category != null && (!isEditMode || hasChanges) && !isExerciseMissing`;
  `hasUnsavedChanges` с ветвлением по `isEditMode`. Запуск: тесты
  6.1–6.2 зелёные; VM (раздел 7) использует эти поля без рассинхрона.

## 7. ExerciseCreateViewModel: edit-режим (JVM, TDD)

- [x] 7.1 Тесты `ExerciseCreateViewModelTest` (расширить): VM с
  `exerciseId != NO_EXERCISE_ID` загружает упражнение — UiState содержит
  name/category/measuredBy/comment из БД, `isEditMode == true`.
  Запуск: `make test` — красный.
- [x] 7.2 Тесты `hasChanges`: изменение name → `true`; без изменений →
  `false`; изменение и возврат к исходному значению → `false`;
  изменение только measuredBy → `true` (влияет на hasChanges, в отличие
  от create-формулы hasUnsavedChanges). Запуск: красный.
- [x] 7.3 Тесты save: edit + `Result.success` → `updateExercise` вызван,
  `addExercise` НЕ вызван, `onSaved` вызван; create → `addExercise`
  (регрессия). Контракт 1:1 для неизменяемых полей edit-режима —
  переданный в `updateExercise` `Exercise` сохраняет `id`, `createdAt`
  И `isCustom` оригинала (защита от случайной мутации `isCustom`
  через цепочку save; UI-недостижимо из long-press только кастомных,
  но контракт покрывается тестом на всякий случай — design Decision 2
  явно фиксирует «id/createdAt/isCustom из оригинала»). Запуск:
  красный.
- [x] 7.4 Тесты: `getExercise` → `Result.success(null)` →
  `isExerciseMissing == true`, `save()` no-op; и `getExercise` →
  `Result.failure` → `isExerciseMissing == true`, `save()` no-op
  (покрывает обе ветки спека «Упражнение не найдено — подсказка и
  disabled Save»). Запуск: красный.
- [x] 7.5 Реализация: параметр `exerciseId: Long = NO_EXERCISE_ID` в VM и
  фабрике; `private var originalExercise: Exercise? = null`; init-блок —
  загрузка + `prefillState` (образец `EntryCreateViewModel`); приватный
  `draftOf(uiState)` — доменная модель с id/createdAt/isCustom из
  оригинала; `save(onSaved)` — ветвление update/add, всегда
  `onSaved = onBackClick`. Запуск: тесты 7.1–7.4 зелёные.

## 8. ExerciseCreateScreen: edit-режим UI (androidTest, TDD)

- [x] 8.1 AndroidTest `ExerciseCreateScreenTest` (расширить): открытие с
  `exerciseId` — заголовок «Изменить упражнение», поля предзаполнены,
  «Сохранить» disabled. Запуск: `make android-test` — красный.
- [x] 8.2 AndroidTest: изменение name → «Сохранить» enabled; возврат к
  исходному → снова disabled. Запуск: красный.
- [x] 8.3 AndroidTest: тап «Сохранить» (после изменения) — навигация назад
  (route вернулся на `exercises`), в fake-репозитории упражнение с новым
  name; системный back без изменений — закрылся без диалога. Запуск:
  красный.
- [x] 8.4 Реализация: заголовок `if (uiState.isEditMode)
  exercise_edit_title else exercise_create_title`; при
  `isExerciseMissing` — подсказка `exercise_hint_exercise_missing` под
  формой (паттерн `isEntryMissing` — см. `EntryCreateUiState.kt:31–32, 49`
  и `EntryCreateViewModel.kt:78–85`), Save
  недостижимо disabled;
  остальное уже работает через UiState (раздел 6). Запуск: 8.1–8.3 и
  8.5 зелёные.
- [x] 8.5 AndroidTest: открытие `ExerciseCreateScreen` с `exerciseId`
  упражнения, которое fake-репозиторий возвращает как `null` (или
  `Result.failure`) — под формой отображается текст
  «Упражнение не найдено», «Сохранить» disabled. Запуск: красный.

## 9. Превью

- [x] 9.1 Превью каталога: `ExerciseContextMenu` (открытое меню),
  `ExerciseDeleteDialog`, обновлённый `ExerciseListItemRow` (обычная и
  подсвеченная строка) — в соседнем `*Preview.kt`, светлая + тёмная
  (`UI_MODE_NIGHT_YES`), `showBackground = true, locale = "ru"`, обёртка
  `JetpackMyWorkoutsTheme`, состояния рядом в Column. Проверка: компиляция
  зелёная.
- [x] 9.2 Превью `ExerciseCreateScreenPreview.kt`: edit-состояния —
  предзаполненная форма без изменений (Save disabled) и изменённая
  (Save enabled), каждая в светлой и тёмной теме. Проверка: компиляция
  зелёная.

## 10. Регрессия и финальные проверки

- [x] 10.1 `make format` — без диффа после форматирования.
- [x] 10.2 `make lint` (detekt) — зелёный.
- [x] 10.3 `make test` — все JVM-тесты зелёные.
- [x] 10.4 `make android-test` — все инструментальные тесты зелёные
  (включая существующие ExercisesScreenTest/ExerciseCreateScreenTest
  create-сценарии и навигационные тесты).
- [x] 10.5 Ручная проверка на эмуляторе (опционально, `make install`):
  long-press на стандартном/кастомном, удаление с записями — снекбар,
  edit с сохранением и возвратом.
