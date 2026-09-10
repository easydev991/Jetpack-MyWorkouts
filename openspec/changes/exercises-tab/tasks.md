# Tasks: exercises-tab

Порядок — TDD (AGENTS.md): тест на X раньше реализации X; задачи выстроены
так, чтобы каждая собиралась на своём месте (проводка RootScreen — после
того, как существуют MoreScreen-параметры и ExercisesScreen). Верификация
каждой задачи — прогон соответствующего теста. Data-слой каталога и домен
не меняются — потребляется `WorkoutDataRepository.exercises("")`; в
data-слое только новый ключ настроек в `AppSettingsDataStore`.

## 1. UiState + ViewModel (JVM, TDD)

- [ ] 1.1 Red (JVM): `ExercisesViewModelTest` (`app/src/test/`,
  JUnit 5, fake-репозиторий на `MutableStateFlow` по конвенциям
  kotlin-testing) — Loading до первой эмиссии; Success со списком после
  первой эмиссии; обновление до нового Success при изменении Flow.
  Прогнать — падает (класса нет)
- [ ] 1.2 Green (main): `ui/state/ExercisesUiState.kt` — sealed
  `Loading` / `Success(exercises: List<Exercise>)` без Error (design
  Decision 2, прецедент `MainUiState`); `ui/viewmodel/ExercisesViewModel.kt`
  — `repository.exercises("")` → `stateIn(viewModelScope,
  SharingStarted.WhileSubscribed(5000), Loading)` (design Decision 1,
  НЕ `getAllExercises()`) + `factory(repository)`. Прогнать 1.1 — зелёный

## 2. Настройка видимости вкладки (data)

- [ ] 2.1 `AppSettingsDataStore`: ключ `SHOW_EXERCISES_TAB_KEY =
  booleanPreferencesKey("show_exercises_tab")`, `val showExercisesTab:
  Flow<Boolean>` (default `false`, по образцу `useDynamicColors`) +
  `suspend fun setShowExercisesTab(Boolean)` (design Decision 6; имя
  файла `appearance_prefs` НЕ менять). Там же обновить `@implNote`
  класса: «Хранит 3 ключа (theme/use_dynamic_colors/
  show_exercises_tab)». Прямых DataStore-тестов в проекте
  нет (прецедент theme/useDynamicColors) — поведение покрывается
  androidTest задачи 4 и проводкой задачи 6. Верификация:
  `./gradlew compileDebugKotlin` зелёный

## 3. Строковые ресурсы

- [ ] 3.1 Добавить в `res/values/strings.xml` и
  `res/values-en/strings.xml`: `exercises_title` («Упражнения» /
  "Exercises"), `exercises_add_exercise` («Добавить упражнение» /
  "Add exercise"), `exercise_measured_by_reps` («повторы» / "reps"),
  `exercise_measured_by_time` («длительность» / "duration"),
  `exercises_list_empty` («Нет упражнений» / "No exercises"),
  `more_exercises_tab` («Показывать вкладку "Упражнения"» /
  "Show Exercises tab").
  Верификация: проект собирается, нет lint-ошибок MissingTranslation

## 4. Тоггл на экране «Ещё» (androidTest, TDD)

- [ ] 4.1 Red (androidTest): `MoreScreenTest` — новый кейс: строка с
  `Switch` и подписью «Показывать вкладку "Упражнения"»/"Show Exercises
  tab" рендерится; `checked` следует за `showExercisesTab` (false/true);
  тап по свитчу вызывает `onShowExercisesTabChange` с новым значением.
  Существующие вызовы `MoreScreen(navController)` перевести на новую
  сигнатуру. Других androidTest, завязанных на `BottomTabs`/состав нижнего
  бара, нет (единственное использование — `MoreScreenTest`, проверено
  поиском по `app/src/androidTest/`); полный контрольный прогон — задача
  8.2. Прогнать — падает
- [ ] 4.2 Green (main): `MoreScreen.kt` — параметры `showExercisesTab:
  Boolean, onShowExercisesTabChange: (Boolean) -> Unit` (design
  Decision 8, без ViewModel); строка со `Switch` сразу после
  `NavigationButtons` и до feedback-кнопки (Column экрана, подпись
  `more_exercises_tab`). Прогнать 4.1 —
  зелёный

## 5. Экран «Упражнения» (androidTest, по образцу MainScreen)

- [ ] 5.1 Red (androidTest): `ExercisesScreenTest`
  (`app/src/androidTest/`, JUnit 4) — список рендерит упражнения:
  название + лейбл типа измерения («повторы»/«длительность»), комментарий
  виден при наличии и отсутствует при `comment = null`, пустое состояние
  «Нет упражнений» при пустом списке, FAB присутствует; навигация:
  NavHost-harness по образцу `MoreScreenTest.MoreScreenWithNavHost`
  (stub-destinations + `onControllerReady`, нижнего бара нет —
  проверяется только, что
  `navController.navigate(Screen.Exercises.route)` открывает экран
  «Упражнения», не FAB-навигация). Прогнать — падает
- [ ] 5.2 Green (main): `ui/screens/exercises/ExercisesScreen.kt` —
  обёртка + `internal ExercisesScreenContent` + data class
  `ExercisesScreenState` (design Decision 3); Scaffold с TopAppBar
  `exercises_title`, `contentWindowInsets = safeDrawing.only(Horizontal)`;
  `LazyColumn` c `key = { it.id }` и `ExerciseListItemRow` (name
  bodyLarge Medium weight(1f), лейбл measuredBy bodyMedium
  onSurfaceVariant TextAlign.End, comment ниже при `!= null`);
  private `@Composable`-маппинг `ExerciseMeasuredBy` с тотальным `when`
  (design Decision 5); пустое состояние по центру; FAB-заглушка
  `Icons.Filled.Add` + `Log.d` c русским сообщением, тег в
  `private const` (design Decision 4). Прогнать 5.1 — зелёный

## 6. Навигация и проводка настройки

- [ ] 6.1 Red (JVM): `navigation/VisibleTabsTest` (`app/src/test/`,
  JUnit 5) — `visibleTabs(showExercisesTab = false)` → routes
  `["main", "more"]` (порядок сохранён); `visibleTabs(true)` →
  `["main", "exercises", "more"]` (Exercises между Main и More). Прогнать
  — падает (функции нет)
- [ ] 6.2 Green (main): `navigation/Screen.kt` — `object Exercises :
  Screen(route = "exercises")`; `BottomTab` с `Icons.Filled.FitnessCenter`
  и `R.string.exercises_title` между Main и More в `BottomTabs` (design
  Decision 7 — полный статичный список); pure-функция
  `fun visibleTabs(showExercisesTab: Boolean): List<BottomTab>` рядом с
  `BottomTabs` (design Decision 7 — покрытие состояния «3 вкладки» без
  androidTest с реальным DataStore). Прогнать 6.1 — зелёный
- [ ] 6.3 `ui/screens/root/RootScreen.kt` (все зависимости уже есть:
  настройки из 2.1, параметры MoreScreen из 4.2, экран из 5.2, функция из
  6.2): `val showExercisesTab by dataStore.showExercisesTab.collectAsState(initial
  = false)`; `shouldShowBottomBar` и `AppBottomBar` — по
  `visibleTabs(showExercisesTab)` (design Decision 7);
  composable-destination `Screen.Exercises.route` → `ExercisesScreen`
  регистрируется ВСЕГДА (design Decision 7); в destination More передать
  `showExercisesTab` + `onShowExercisesTabChange` (запись через
  `rememberCoroutineScope().launch { dataStore.setShowExercisesTab(it) }`,
  design Decision 8). Верификация: `./gradlew compileDebugKotlin`
  зелёный; тап по тогглу меняет состав нижнего бара без перезапуска
  (ручная проверка на эмуляторе)

## 7. Превью

- [ ] 7.1 `ui/screens/exercises/ExercisesScreenPreview.kt` — светлая +
  тёмная (`UI_MODE_NIGHT_YES`), `showBackground = true`, `locale = "ru"`,
  обёртка в `JetpackMyWorkoutsTheme`; ключевые состояния: список с
  комментарием и без, FAB. Верификация: превью рендерятся (сборка
  зелёная)

## 8. Финальная проверка

- [ ] 8.1 `make format && make lint && make test` — зелёные
- [ ] 8.2 `make android-test` на подключённом устройстве — зелёные
