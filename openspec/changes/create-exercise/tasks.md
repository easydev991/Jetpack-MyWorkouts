## 1. UiState + ViewModel (JVM, TDD)

- [ ] 1.1 Red: `app/src/test/java/com/myworkouts/ui/viewmodel/ExerciseCreateViewModelTest.kt` —
  тесты на `ExerciseCreateViewModel` с fake-репозиторием на
  `MutableStateFlow`/`MutableList`. Кейсы: дефолт `measuredBy == REPS`
  при создании VM (без вызовов `onMeasuredByChange`); пустая форма →
  `canSave = false`; `onNameChange("Жим")` без `onCategoryChange` →
  `canSave = false` (промежуточное состояние name без category);
  `onNameChange("Жим")` + `onCategoryChange(PUSH)` → `canSave = true`;
  `onMeasuredByChange(TIME)` на пустой форме (только что созданный VM,
  `name == null`, `category == null`) → `state.measuredBy == TIME`,
  `canSave == false` (measuredBy не влияет на canSave — заполненность
  name/category та же); `onMeasuredByChange(TIME)` после `onNameChange
  ("Жим")` + `onCategoryChange(PUSH)` → `state.measuredBy == TIME`,
  `canSave == true` (соответствует спеке scenario «measuredBy
  переключается без влияния на canSave»: переключение из REPS в TIME
  НЕ меняет значение `canSave`); `onCommentChange("узкий хват")` →
  `comment == "узкий хват"` (сырое значение, без `trim()` на входе);
  кейсы для `hasUnsavedChanges` (требование спек «BackHandler +
  hasUnsavedChanges — подтверждение закрытия»): только что созданный
  VM (все поля null) → `hasUnsavedChanges == false`;
  `onNameChange("Жим")` → `hasUnsavedChanges == true`;
  `onCategoryChange(PULL)` без `onNameChange` →
  `hasUnsavedChanges == true`;
  `onCommentChange("коммент")` без `onNameChange`/`onCategoryChange` →
  `hasUnsavedChanges == true`;
  `onMeasuredByChange(TIME)` на пустой форме (только что созданный
  VM) → `hasUnsavedChanges == false` (изменение `measuredBy` при
  пустых `name`/`category`/`comment` НЕ считается изменением формы);
  `onNameChange("Жим")` → `onNameChange("")` → `hasUnsavedChanges ==
  false` (пустое имя после `trim()` не считается изменением);
  `save()` при пустой форме — `repository.addExercise` НЕ
  вызывается AND `onSaved` НЕ вызывается (симметрия с success-кейсом);
  `save()` с заполненной формой — `repository.addExercise` вызывается
  ровно один раз с `Exercise(id = 0L, name = "Жим", category = PUSH,
  isCustom = true, comment = null, createdAt ≈ System.currentTimeMillis()
  (±5s), measuredBy = REPS)`; `save()` с заполненной формой и
  `name = "  Жим  "` (после `onNameChange`) → в репозиторий передан
  `Exercise.name == "Жим"` (нормализация `trim()` при сохранении);
  `save()` с заполненной формой и `comment = "  узкий хват  "` → в
  репозиторий передан `Exercise.comment == "узкий хват"` (нормализация
  `trim()` при сохранении); `save()` с заполненной формой и
  `comment = "   "` →
  в репозиторий передан `Exercise.comment == null` (`trim()` +
  `takeIf { isNotEmpty() }`); `save()` при `Result.failure` →
  `isSaveError == true`, `hasUnsavedChanges` остаётся `true`,
  `onSaved` не вызван; после `Result.failure` любой колбэк ввода (`onNameChange`/`onCategoryChange`/`onMeasuredByChange`/`onCommentChange`) сбрасывает `isSaveError == false` (паттерн `EntryCreateViewModel.onXChange` — канон: спека, Requirement «ViewModel и UiState формы», Scenario «Ввод после неуспешного сохранения скрывает ошибку»); `save()` при
  `Result.success` → `onSaved` вызван ровно раз, `isSaveError` остаётся
  `false`. Verification: `./gradlew testDebugUnitTest --tests
  "com.myworkouts.ui.viewmodel.ExerciseCreateViewModelTest"` — тесты
  красные (класс/функции ещё не существуют).

- [ ] 1.2 Green: `app/src/main/java/com/myworkouts/ui/state/ExerciseCreateUiState.kt`
  — `data class ExerciseCreateUiState(val name: String? = null, val
  category: ExerciseCategory? = null, val measuredBy: ExerciseMeasuredBy =
  ExerciseMeasuredBy.REPS, val comment: String? = null, val isSaveError:
  Boolean = false)` с двумя computed-свойствами (только данные, без
  лямбд — паттерн `EntryCreateUiState` и AGENTS.md): `val canSave:
  Boolean get() = !name.isNullOrBlank() && category != null` и
  `val hasUnsavedChanges` (**полная формула — канон в требовании спеки
  «BackHandler + hasUnsavedChanges — подтверждение закрытия», эта задача
  ссылается на канон, а не дублирует формулу**). Файл
  `app/src/main/java/com/myworkouts/ui/viewmodel/ExerciseCreateViewModel.kt`:
  `MutableStateFlow<ExerciseCreateUiState>` + `val uiState: StateFlow<…>`
  через `stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
  ExerciseCreateUiState())`; методы `onNameChange/onCategoryChange/
   onMeasuredByChange/onCommentChange` через `mutableUiState.update { ... }`
   (каждый колбэк сбрасывает `isSaveError = false` — канон: спека,
   Requirement «ViewModel и UiState формы»);
  `save(onSaved: () -> Unit)` с формированием `Exercise(isCustom = true,
  createdAt = System.currentTimeMillis())` и вызовом
  `repository.addExercise(...)`. Verification: та же test-команда, что в
  1.1, — теперь зелёный.

## 2. Сортировка в ExerciseDao

- [ ] 2.1 Red: добавить новые сценарии в существующий
  `app/src/androidTest/java/com/myworkouts/data/db/ExerciseDaoTest.kt`
  (рядом с тестом `search_results_areSortedByNameCaseInsensitive` —
  переиспользуем ту же инфраструктуру `setUp`/`tearDown`/in-memory Room;
  НЕ создаём отдельный файл `Test.java`/`ExerciseDaoSortingTest.kt` —
  это раздует пакет дублёрами).   Кейсы разделены на **red-** и
  **регрессионные**: red-кейсы дискриминируют старый `ORDER BY name
  COLLATE NOCASE` от нового `ORDER BY is_custom DESC, created_at DESC,
  name COLLATE NOCASE`; регрессионные — фиксируют порядок, общий для
  обеих реализаций (зелёные и до, и после 2.2).

  Red-кейсы (имена подобраны так, чтобы алфавит противоречил
  кастомности / `createdAt` — на старом ORDER BY будет алфавитный
  порядок, на новом — кастомное-выше-стандартного / более-новое-выше):
  (1) кастомное упражнение «Я» (`isCustom = true`) и стандартное «А»
  (`isCustom = false`) → первая эмиссия `observeExercises("")`
  возвращает список с «Я» первым (`is_custom DESC` → кастомное выше);
  на старом ORDER BY первым будет «А» по алфавиту, red гарантирован;
  (2) два кастомных: «А» (`createdAt = T1`) и «Б» (`createdAt = T2 > T1`)
  → «Б» первым (`created_at DESC` → более новый выше; на старом ORDER
  BY первым будет «А» по алфавиту, red гарантирован).

  Регрессионные кейсы (порядок совпадает на старом и новом ORDER BY,
  цель — зафиксировать контракт, не red): (3) два кастомных «Бег» и
  «Жим» с одинаковым `createdAt` → «Бег» выше «Жим» (по возрастанию
  `name COLLATE NOCASE` после `is_custom DESC`/`created_at DESC`
  эквивалентны — обе ветви дают «Бег» выше); (4) четыре стандартных
  на ASCII-выборке (паттерн — существующий
  `search_results_areSortedByNameCaseInsensitive` в этом же файле,
  комментарий в теле функции про «SQLite NOCASE-коллация покрывает
  только ASCII»): «alpha», «Beta», «CHARLIE», «delta» → порядок
  COLLATE NOCASE (`alpha`, `Beta`, `CHARLIE`, `delta`); для
  регрессионного кейса 4 у фикстур должен быть одинаковый `createdAt`,
  иначе `created_at DESC` перебьёт name-порядок — это явное предусловие
  фикстуры, не контракт на порядок.

  Verification: `./gradlew connectedDebugAndroidTest --tests
  "com.myworkouts.data.db.ExerciseDaoTest"` — red-кейсы (1, 2) красные
  на старом `ORDER BY` без `is_custom DESC`; регрессионные (3, 4)
  зелёные и до, и после 2.2 (дополнительная страховка от регресса
  по name-коллации после перестановки ORDER BY).

- [ ] 2.2 Green: в `app/src/main/java/com/myworkouts/data/db/ExerciseDao.kt`
  изменить `@Query("SELECT * FROM exercises WHERE name LIKE '%' || :query
  || '%' COLLATE NOCASE ORDER BY name COLLATE NOCASE")` на
  `@Query("SELECT * FROM exercises WHERE name LIKE '%' || :query || '%'
  COLLATE NOCASE ORDER BY is_custom DESC, created_at DESC, name COLLATE
  NOCASE")`. Заодно обновить kdoc двух интерфейсов/реализаций
  репозитория (цитируется в design Decision 2 как контракт): в
  `app/src/main/java/com/myworkouts/data/repository/WorkoutDataRepository.kt`
  L23–27 (фраза «отсортированный по имени» на L25) и в
  `app/src/main/java/com/myworkouts/data/repository/RoomWorkoutDataRepository.kt`
  L48–52 (та же фраза на L50) — kdoc должен отражать новый порядок
  «отсортированный по убыванию кастомности, затем по убыванию
  `createdAt`, затем по `name` без учёта регистра». Verification: та же
  test-команда, что в 2.1, — зелёный (новые сценарии в `ExerciseDaoTest`
  проходят).

## 3. Строковые ресурсы (ru/en)

- [ ] 3.1 Добавить русские строки в
  `app/src/main/res/values/strings.xml`:
  `exercise_create_title` = «Новое упражнение», `exercise_field_name` =
  «Название», `exercise_field_category` = «Категория»,
  `exercise_field_category_select` = «Выберите категорию»,
  `exercise_field_comment` = «Комментарий», `exercise_save_error` =
  «Не удалось сохранить упражнение», плюс 5 локализованных лейблов
  категорий для `CategoryDropdownField`:
  `exercise_category_push` = «Толкающие»,
  `exercise_category_pull` = «Тянущие»,
  `exercise_category_legs` = «Ноги»,
  `exercise_category_core` = «Кор»,
  `exercise_category_cardio` = «Кардио». Лейблы соответствуют
  kdoc-описаниям в `ExerciseCategory.kt` (Толкающие/Тянущие/Ноги/Кор/
  Кардио), чтобы локализация была семантически согласована с KDoc.
  Строки для сегментов REPS/TIME НЕ добавляются — переиспользуются
  существующие `exercise_measured_by_reps` (уже «повторы») и
  `exercise_measured_by_time` (уже «длительность»), чтобы избежать
  дублирования и рассинхрона UX между каталогом и формой создания.
  Строка для `navigationIcon.contentDescription` тоже НЕ
  добавляется — переиспользуется `R.string.back`
  («Назад» / "Back"), как в `EntryCreateScreen.TopAppBar.navigationIcon`,
  `AppearanceScreen.TopAppBar.navigationIcon`,
  `AppDataScreen.TopAppBar.navigationIcon`. Текст кнопки «Сохранить» —
  переиспользуется существующий `R.string.entry_save` («Сохранить» /
  "Save"), новая строка НЕ нужна (паттерн —
  `EntryCreateScreen.SaveBottomBar.Button.Text`). Диалог подтверждения
  закрытия (`ExerciseCreateCloseConfirmDialog`) переиспользует
  существующие `R.string.entry_confirm_close_dialog_title`,
  `R.string.entry_confirm_close_dialog_message`, `R.string.entry_confirm_close`,
  `R.string.cancel` — обоснование переиспользования и набор строк —
  канон в спеке `specs/exercise-create-screen/spec.md`, требование
  «BackHandler + hasUnsavedChanges — подтверждение закрытия».
  Verification:
  `aapt2 dump strings` или запуск приложения — ресурсы видны.

- [ ] 3.2 Добавить английские варианты в
  `app/src/main/res/values-en/strings.xml` — те же 11 ключей
  (3 поля формы: name, category, comment + плейсхолдер
  `category_select` + заголовок `exercise_create_title` + текст ошибки
  `exercise_save_error` = 6 «форм-строк»; плюс 5 лейблов категорий),
  что в 3.1:
  `exercise_create_title` = "New exercise", `exercise_field_name` =
  "Name", `exercise_field_category` = "Category",
  `exercise_field_category_select` = "Select category",
  `exercise_field_comment` = "Comment", `exercise_save_error` =
  "Failed to save exercise"; лейблы категорий — "Push", "Pull",
  "Legs", "Core", "Cardio". Английские лейблы категорий переводят
  русские недословно, а выбирают стандартные фитнес-термины (`Push`/
  `Pull`), чтобы избежать неестественного дословного перевода
  русских kdoc-лейблов. Verification: запуск приложения в
  `en`-локали — все строки переведены.

## 4. Навигация (route + RootScreen)

- [ ] 4.1 В `app/src/main/java/com/myworkouts/navigation/Screen.kt` добавить
  `object ExerciseCreate : Screen(route = "exercise_create")` после
  `EntryCreate`. Verification: компиляция `./gradlew compileDebugKotlin`.

- [ ] 4.2 В `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt`
  сделать три правки: (a) в `AppNavHost` зарегистрировать новый
  destination `composable(Screen.ExerciseCreate.route) { ExerciseCreateScreen(
  viewModel = viewModel(factory = ExerciseCreateViewModel.factory(repository)),
  onBackClick = { navController.popBackStack() }) }` рядом с блоком
  `composable(Screen.EntryCreate.route, arguments = ..., ...) { ... }`
  — фабрика `ExerciseCreateViewModel.factory(repository)` строится
  по образцу `EntryCreateViewModel.factory(repository, entryId)` в
  существующем блоке (design Decision 4: фабрика создаётся внутри
  `composable`-блока, `viewModel(factory = ...)` передаётся в
  `ExerciseCreateScreen`; сам экран фабрику не строит); (b) в
  существующем блоке `composable(Screen.Exercises.route) { ... }`
  прокинуть `onAddExerciseClick = { navController.navigate(
  Screen.ExerciseCreate.route) }` в вызов `ExercisesScreen` —
  это требует, чтобы `ExercisesScreen` принял новый параметр
  (см. 4.3); (c) `shouldShowBottomBar` уже работает для не-tab
  маршрутов через `currentRoute in tabs.map { it.route }`
  (`RootScreen.kt` L71) — никакой отдельной ветки для
  `Screen.ExerciseCreate.route` в `RootScreen` НЕ требуется, бар
  скроется автоматически при навигации на новый экран
  (соответствует спеке `non-tab-screen-navigation/spec.md` Scenario
  «Bottom navigation скрыта на ExerciseCreate»). Verification:
  компиляция `./gradlew compileDebugKotlin` + ручной запуск
  (`make install`) — тап FAB открывает новый экран, кнопка назад в
  TopAppBar возвращает на `ExercisesScreen`, нижний бар скрыт.

- [ ] 4.3 В `app/src/main/java/com/myworkouts/ui/screens/exercises/ExercisesScreen.kt`
  сделать две правки: (a) добавить обязательный параметр
  `onAddExerciseClick: () -> Unit` в сигнатуру `fun ExercisesScreen(
  viewModel: ExercisesViewModel, onAddExerciseClick: () -> Unit, modifier:
  Modifier = Modifier)` (без значения по умолчанию — спека
  `specs/exercises-screen/spec.md` ADDED «FAB открывает
  ExerciseCreateScreen» явно требует, чтобы колбэк прокидывался из
  `RootScreen`, как `MainScreen → EntryCreateScreen`) и пробросить
  его в `state.copy(onAddExerciseClick = onAddExerciseClick)` при
  сборке `ExercisesScreenState` внутри тела обёртки; (b) удалить
  дефолт `= {}` у поля `ExercisesScreenState.onAddExerciseClick` —
  поле становится обязательным параметром. Существующие тесты в
  `ExercisesScreenTest.kt` (в функции `setContentWith` и
  `ExercisesScreenWithNavHost`) уже передают `onAddExerciseClick = {}`
  именованным аргументом — продолжат компилироваться. **Файл
  `ExercisesScreenTest.kt` и его harness `ExercisesScreenWithNavHost`
  в этом шаге НЕ модифицируются** — они остаются в текущем виде до
  первой расширяющей задачи (5.1b — добавит `composable(
  Screen.ExerciseCreate.route) { }`-заглушку в NavHost-harness + FAB→route
  сценарий; 7.2 — регресс-прогон существующих сценариев, нового
  не добавляет). Сама 5.1b НЕ добавляет форму в заглушку (форма —
  пустая), см. задачу 5.1b. Verification: `./gradlew compileDebugAndroidTest`
  — зелёный.

## 5. UI экрана (androidTest, TDD)

- [ ] 5.1a Red: `app/src/androidTest/java/com/myworkouts/ui/screens/exercises/ExerciseCreateScreenTest.kt`
  — инструментальный Compose-тест на рендер `ExerciseCreateScreenContent`
  с `createComposeRule()` (паттерн устоявшийся в проекте:
  `EntryCreateScreenTest` / `ExercisesScreenTest` / `MoreScreenTest` /
  `MainScreenTest`; `createAndroidComposeRule<MainActivity>` НЕ
  используется — manual DI создаёт `WorkoutDatabase` /
  `RoomWorkoutDataRepository` / `AppSettingsDataStore` в
  `MainActivity.onCreate`, и `createAndroidComposeRule<MainActivity>`
  потребовал бы отдельной тестовой инфраструктуры для воспроизведения
  цепочки DI; `createComposeRule()` обходит это, тестируя
  `ExerciseCreateScreenContent` с готовым `ExerciseCreateScreenState`).
  Кейсы: рендер пустой формы — видны 4 поля (`OutlinedTextField` name,
  `SingleChoiceSegmentedButtonRow` REPS/TIME, `CategoryDropdownField`,
  `OutlinedTextField` comment) и disabled Save; ввод name — Save ещё
  disabled; выбор category — Save enabled; `measuredBy` по умолчанию
  REPS активен; переключение на TIME работает; пустой комментарий —
  Save enabled (комментарий необязательный). Verification:
  `./gradlew connectedDebugAndroidTest --tests
  "com.myworkouts.ui.screens.exercises.ExerciseCreateScreenTest"` —
  красный (только рендер-кейсы, NavHost-сценарий идёт в 5.1b,
  isSaveError — в 5.1c).

- [ ] 5.1b Регрессионный тест по факту (НЕ TDD — навигация/верстка не
  покрывается red-green циклом, см. AGENTS.md L39–43). Добавить в файл
  `ExercisesScreenTest.kt` (НЕ в новый файл и НЕ через отдельный
  `ExerciseCreateScreenWithNavHost` — это согласуется с 7.2 и
  AGENTS.md «не плоди заранее») ещё одну `composable`-заглушку
  `composable(Screen.ExerciseCreate.route) { }` в существующем
  harness-функционале `ExercisesScreenWithNavHost` — существующий
  harness расширяется до трёх destinations (добавляется
  `Screen.ExerciseCreate.route`; `"stub"` и `Screen.Exercises.route`
  уже есть). Без заглушки `composable(
  Screen.ExerciseCreate.route)` `navController.navigate(
  Screen.ExerciseCreate.route)` бросил бы
  `IllegalArgumentException: destination not found`. **Сценарий
  ограничен только навигацией**: открыть `ExercisesScreen` через
  NavHost-harness, тапнуть FAB → активный route становится
  `Screen.ExerciseCreate.route` (заглушка пустая, форма в неё НЕ
  добавлена — fill/save-flow не тестируется здесь; на пустой заглушке
  fill-flow неработоспособен в принципе — `onNodeWithText` не найдёт
  полей; полный flow покрывается ручной проверкой 8.4). К моменту
  этого шага 4.2/4.3 уже выполнены (FAB прокинут, route зарегистрирован
  в `RootScreen`, `onAddExerciseClick` стал обязательным параметром) —
  тест пишется и проходит «по факту», red-фазы нет.
  Verification: тот же `--tests` файл — зелёный (новый NavHost-сценарий
  проходит; никаких red-кейсов для навигации).

- [ ] 5.1c Red: добавить в тот же файл `ExerciseCreateScreenTest.kt`
  отдельный тест-кейс на рендер блока ошибки сохранения. Тест
  рендерит `ExerciseCreateScreenContent` с готовым
  `ExerciseCreateScreenState` (паттерн — `EntryCreateScreenTest`,
  рендер через `createComposeRule()` без NavHost) и проверяет:
  при `state.uiState.isSaveError = true` в под-дереве экрана
  присутствует `Text(R.string.exercise_save_error)` (текст
  рендерится внутри `ExerciseCreateSaveBottomBar` под кнопкой
  «Сохранить» в стиле `MaterialTheme.colorScheme.error` +
  `typography.bodySmall`, согласно требованию спек «Кнопка «Сохранить» и
  блок ошибки в bottomBar» L171–184 — блок ошибки L177–184); при
  `state.uiState.isSaveError = false` этот `Text` НЕ
  отображается (поиск через `onNodeWithText(...)` →
  `assertDoesNotExist()`). Сценарий верифицирует только
  видимость/отсутствие текста при двух значениях флага и НЕ
  зависит от NavHost-harness. Verification: тот же `--tests`
  файл — красный (isSaveError-кейс).

- [ ] 5.1d Red: добавить в тот же файл `ExerciseCreateScreenTest.kt`
  отдельные тест-кейсы на `BackHandler` + диалог подтверждения
  закрытия (требование спек «BackHandler + hasUnsavedChanges —
  подтверждение закрытия», паттерн `EntryCreateScreenTest` —
  проверки `BackHandler`/`AlertDialog`). Сценарии: (a) при
  `state.uiState.hasUnsavedChanges = true` (например, `name = "Жим"`)
  тап стрелки `TopAppBar.navigationIcon` показывает `AlertDialog`
  с текстом «Закрыть?» и кнопками «Закрыть» (`colorScheme.error`)
  + «Отмена»; тап «Закрыть» в диалоге вызывает `state.onBackClick()`
  ровно один раз; тап «Отмена» НЕ вызывает `state.onBackClick()`
  (диалог скрывается, экран остаётся); (b) при
  `state.uiState.hasUnsavedChanges = false` (только что открытая
  форма, все поля null) тап стрелки `TopAppBar.navigationIcon` НЕ
  показывает диалог И сразу вызывает `state.onBackClick()`. Тесты
  используют `createComposeRule()` (НЕ NavHost — диалог изолирован
  от навигации), `androidx.test.espresso.Espresso.pressBack()` для
  симуляции системного back. Verification: тот же `--tests` файл —
  красный (BackHandler-кейс).

- [ ] 5.2 Green: `app/src/main/java/com/myworkouts/ui/screens/exercises/ExerciseCreateScreen.kt`
  — `@Composable fun ExerciseCreateScreen(viewModel:
  ExerciseCreateViewModel, onBackClick: () -> Unit, modifier: Modifier =
  Modifier)` (обёртка; `modifier` прокидывается в `Scaffold`),
  `internal data class ExerciseCreateScreenState(...)` с колбэками,
  `internal @Composable fun ExerciseCreateScreenContent(state:
  ExerciseCreateScreenState, modifier: Modifier = Modifier)` со
  `Scaffold(topBar = TopAppBar(...), bottomBar = { ... })`,
  BackHandler и диалогом подтверждения закрытия (см. ниже),
  `internal @Composable fun ExerciseCreateForm(state:
  ExerciseCreateScreenState, paddingValues: PaddingValues)` с
  `verticalScroll(rememberScrollState())` + 4 поля (см. ниже),
  `internal @Composable fun ExerciseCreateSaveBottomBar(state:
  ExerciseCreateScreenState)` с указанными модификаторами
  (`.fillMaxWidth().navigationBarsPadding().imePadding().padding(...)`),
  `internal @Composable fun ExerciseCreateCloseConfirmDialog(onConfirm:
  () -> Unit, onDismiss: () -> Unit)` — паттерн-близнец
  `EntryCloseConfirmDialog` из `EntryCreateScreen.kt` L170–195,
  использует существующие строки `R.string.entry_confirm_close_dialog_title`,
  `R.string.entry_confirm_close_dialog_message`,
  `R.string.entry_confirm_close`, `R.string.cancel` — переиспользование
  обосновано в спеке (канон, см. требование «BackHandler +
  hasUnsavedChanges — подтверждение закрытия» в
  `specs/exercise-create-screen/spec.md`).
  `ExerciseCreateScreenContent` SHALL содержать `var showConfirmDialog by
  remember { mutableStateOf(false) }`, локальную `val attemptClose = ...`,
  `BackHandler(...)` и рендер `ExerciseCreateCloseConfirmDialog` —
  **полные формулы и связки — канон в требовании спеки «BackHandler +
  hasUnsavedChanges — подтверждение закрытия»** (эта задача ссылается
  на канон, а не дублирует формулу). Паттерн — `EntryCreateScreenContent`
  L148–156.
  Сигнатура `ExerciseCreateSaveBottomBar` принимает `state` целиком
  (а не отдельные `canSave`/`isSaveError`) — это паттерн
  `EntryCreateScreen.SaveBottomBar(state = state)`, который позволяет
  читать `state.uiState.isSaveError` для условного рендера блока
  ошибки под кнопкой (без отдельного параметра `isSaveError: Boolean`).
  Поля формы: `OutlinedTextField` для name (`singleLine = true`),
  `SingleChoiceSegmentedButtonRow` с двумя `SegmentedButton` для
  `measuredBy` (использует существующие `exercise_measured_by_reps` /
  `_time`), `internal @Composable fun CategoryDropdownField(...)` —
  живёт внутри `ExerciseCreateScreen.kt` (тот же файл), паттерн-близнец
  `DurationDropdownField` из `EntryCreateFields.kt` (см. design
  Decision 6 — извлечение в общий компонент отложено);
  `CategoryDropdownField` SHALL резолвить локализованный лейбл через
  приватный file-level хелпер
  `private fun ExerciseCategory.labelResId(): Int @StringRes get() =
  when (this) { ... }` (полная сигнатура `when`-выражения и список
  ресурсов зафиксированы в спеке `specs/exercise-create-screen/spec.md`,
  Requirement «Категория через ExposedDropdownMenuBox — обязательная» —
  это единственный нормативный источник сигнатуры, чтобы правки не
  расползались между design / spec / tasks) и передавать его в
  `Text(stringResource(labelResId()))` внутри `DropdownMenuItem`
  (см. design Decision 6). Способ хранения `ExerciseCategory` в БД —
  канон в спеке (см. требование «Категория через ExposedDropdownMenuBox —
  обязательная» в `specs/exercise-create-screen/spec.md`).
  `OutlinedTextField` для comment (`singleLine = false, minLines = 2`).
  Verification: та же test-команда, что в 5.1, — зелёный.

## 6. Превью

- [ ] 6.1 Создать
  `app/src/main/java/com/myworkouts/ui/screens/exercises/ExerciseCreateScreenPreview.kt`
  — файл с минимум 8 `@Preview`-функциями (4 состояния × 2 темы:
  светлая `UI_MODE_NIGHT_NO` + тёмная `UI_MODE_NIGHT_YES`), обёрнутыми
  в `JetpackMyWorkoutsTheme`, `showBackground = true`, `locale = "ru"`.
  Четыре состояния рядом в `Column`: пустая форма (Save disabled), форма
  с name + category (Save enabled, без комментария), форма с заполненным
  комментарием,
  форма с `measuredBy = TIME`. Verification: ручная проверка —
  Android Studio Preview panel показывает оба варианта (светлый/тёмный)
  без ошибок компиляции.

## 7. Регрессия существующих тестов

- [ ] 7.1 Запустить `app/src/test/java/com/myworkouts/ui/viewmodel/ExercisesViewModelTest.kt`
  и убедиться, что после изменения `ORDER BY` тесты остаются зелёными.
  Файл `ExercisesViewModelTest.kt` существует — это контрактный регресс
  (TDD-цикл требует, чтобы шаг был обязательным, не условным). Если
  какой-то тест проверяет конкретный порядок, ослабить/переписать под
  новый порядок (кастомные сверху → стандартные снизу по имени).
  Verification: `./gradlew testDebugUnitTest --tests
  "com.myworkouts.ui.viewmodel.ExercisesViewModelTest"` — зелёный.

- [ ] 7.2 Регресс-прогон `app/src/androidTest/java/com/myworkouts/ui/screens/exercises/ExercisesScreenTest.kt`
  после изменения 4.3 (`onAddExerciseClick` стал обязательным
  параметром; дефолт `= {}` удалён). Существующие тесты в этом файле
  уже передают `onAddExerciseClick = {}` именованным аргументом
  (см. tasks.md 4.3) — продолжат компилироваться; запустить файл и
  убедиться, что рендер-сценарии остаются зелёными. Если какой-то
  сценарий упал — ослабить/переписать (TDD-цикл требует, чтобы шаг
  был обязательным, не условным). **Эта задача НЕ добавляет новый
  сценарий и НЕ модифицирует harness** — канон FAB-навигации уже
  закреплён в 5.1b (заглушка `composable(Screen.ExerciseCreate.route)
  { }` + FAB→route сценарий). Verification:
  `./gradlew connectedDebugAndroidTest --tests
  "com.myworkouts.ui.screens.exercises.ExercisesScreenTest"` — зелёный;
  ручная проверка из 8.4 покрывает полный flow включая этот шаг.

## 8. Финальные проверки

- [ ] 8.1 `make format && make lint && make test && make android-test` —
  все цели зелёные. Verification: вывод команд без ошибок и
  предупреждений detekt.

- [ ] 8.2 `openspec validate create-exercise` — без ошибок.
  Verification: stdout без `ERROR`/`FAIL`.

- [ ] 8.3 Обновить `AGENTS.md` Project Structure — добавить `exercises/`
  И `appdata/` к перечислению `ui/screens/...` (L97). Реальная
  структура `app/src/main/java/com/myworkouts/ui/screens/` уже
  содержит обе папки (`appdata/`, `appearance/`, `entry/`,
  `exercises/`, `main/`, `more/`, `root/`); `AGENTS.md` L97
  перечисляет только `{main,entry,more,root,appearance}/` и
  рассинхронизирован. Без этого документация лжёт после
  архивации change. Verification: `git diff AGENTS.md` — добавлены
  `exercises/` и `appdata/` в список.

- [ ] 8.4 Ручная проверка на эмуляторе (через MCP `adb` или Android
  Studio): открыть `ExercisesScreen`, тапнуть FAB, заполнить форму
  (имя + категория), нажать «Сохранить» — экран закрывается, новое
  упражнение первое в списке. Verification: скриншот через
  `android-debug-bridge_capture_screenshot` после сохранения — новое
  упражнение сверху списка.