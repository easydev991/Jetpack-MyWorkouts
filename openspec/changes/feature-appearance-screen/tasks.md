> **Замечание по порядку:** этот change задокументирован post-factum — все
> пункты `[x]` отражают фактическое состояние кода. Часть деталей плана
> устарела во время реализации (см. `design.md` Decisions 1–4):
> `MainActivityViewModel` не создан (YAGNI), `ui/screens/common/` не
> создан (inline-реализация), `Screen` рефакторен через отдельный
> `BottomTab` вместо nullable-полей. Ключи строк переименованы
> (`app_theme` вместо `appearance_section_theme` и т.п.).
>
> **Структура:** ниже — **12 секций** (## 1–11 — этапы реализации +
> ## 12 «Финальная верификация» как meta-check). Нумерация секций
> tasks.md **не совпадает 1:1** с `design.md` Migration Plan: ## 7
> покрывает коммиты `e4be290+f129b5d` (радиокнопки + dynamic colors
> inline) + `83b85df+1686746` (финальный экран + androidTest);
> ## 8 (Screen-рефакторинг) + ## 9 (MainActivity) = один коммит
> `7a9a2e3`; ## 10 (кнопка в MoreScreen) — UI-деталь внутри
> `085a68a` (Этап 11 design.md). Сверяйтесь по коммитам в
> `design.md:397–412`.

## 1. Зависимости

- [x] 1.1 В `gradle/libs.versions.toml` добавить `datastore = "1.2.1"`,
  `lifecycleViewmodelCompose = "2.11.0"`, `coroutines = "1.11.0"`,
  `mockk = "1.14.11"`; библиотеки `androidx-datastore-preferences`,
  `androidx-lifecycle-viewmodel-compose`, `kotlinx-coroutines-android`,
  `kotlinx-coroutines-test` (testImplementation), `mockk` (testImplementation).
- [x] 1.2 В `app/build.gradle.kts` добавить 4 `implementation(...)` и 2
  `testImplementation(...)` ссылки на новые библиотеки.

## 2. Domain — AppTheme

- [x] 2.1 Создать `app/src/main/java/com/myworkouts/domain/model/AppTheme.kt`
  с `enum class AppTheme { LIGHT, DARK, SYSTEM }` и extension
  `fun AppTheme.isDark(systemDark: Boolean): Boolean`.
- [x] 2.2 Создать `app/src/test/java/com/myworkouts/domain/model/AppThemeTest.kt`
  с одним тестом `app_theme_names_should_match_data_store_keys`,
  фиксирующим набор `{"LIGHT", "DARK", "SYSTEM"}` как контракт с
  `AppSettingsDataStore`.

## 3. Data Layer — AppSettingsDataStore

- [x] 3.1 Создать `app/src/main/java/com/myworkouts/data/preferences/AppSettingsDataStore.kt`:
  `class AppSettingsDataStore(context: Context)`, top-level
  `private val Context.dataStore by preferencesDataStore(name = "appearance_prefs")`,
  `PreferencesKeys { THEME_KEY = stringPreferencesKey("theme");
  USE_DYNAMIC_COLORS_KEY = booleanPreferencesKey("use_dynamic_colors") }`.
- [x] 3.2 Реализовать `val theme: Flow<AppTheme>` с дефолтом `SYSTEM` и
  fallback на `SYSTEM` при `IllegalArgumentException` от
  `AppTheme.valueOf(themeName)`.
- [x] 3.3 Реализовать `val useDynamicColors: Flow<Boolean>` с дефолтом
  `true`.
- [x] 3.4 Реализовать `suspend fun setTheme(theme: AppTheme)` и
  `suspend fun setUseDynamicColors(useDynamicColors: Boolean)`.

## 4. Theme — динамическое применение

- [x] 4.1 Обновить `app/src/main/java/com/myworkouts/ui/theme/Theme.kt`:
  `JetpackMyWorkoutsTheme(darkTheme = isSystemInDarkTheme(),
  appTheme: AppTheme = AppTheme.SYSTEM, dynamicColor: Boolean = true,
  content)`. Внутри `val useDarkTheme = appTheme.isDark(darkTheme)`.
  Условие `dynamicColor && Build.VERSION.SDK_INT >= S` →
  `dynamicLight/DarkColorScheme(context)`; иначе — статические
  `Light/DarkColorScheme`.

## 5. ViewModel — AppearanceViewModel + тесты

- [x] 5.1 Создать `app/src/main/java/com/myworkouts/ui/state/AppearanceUiState.kt`:
  `data class AppearanceUiState(theme: AppTheme = AppTheme.SYSTEM,
  useDynamicColors: Boolean = true)`.
- [x] 5.2 Создать `app/src/main/java/com/myworkouts/ui/viewmodel/AppearanceViewModel.kt`:
  `class AppearanceViewModel(dataStore: AppSettingsDataStore) : ViewModel()`.
- [x] 5.3 В `AppearanceViewModel` добавить `val uiState: StateFlow<AppearanceUiState>`
  через `combine(dataStore.theme, dataStore.useDynamicColors).stateIn(
  viewModelScope, SharingStarted.WhileSubscribed(5000L),
  AppearanceUiState())`.
- [x] 5.4 Добавить `fun updateTheme(theme: AppTheme)` и
  `fun updateDynamicColors(useDynamicColors: Boolean)` через
  `viewModelScope.launch { dataStore.set*(...) }`.
- [x] 5.5 Добавить `companion object { fun factory(dataStore:
  AppSettingsDataStore): ViewModelProvider.Factory = viewModelFactory {
  initializer { AppearanceViewModel(dataStore) } } }`.
- [x] 5.6 `MainActivityViewModel` НЕ создаётся — YAGNI (см. `design.md`
  Decision 2; `MainActivity` подписывается на `dataStore` напрямую).
- [x] 5.7 Создать `app/src/test/java/com/myworkouts/ui/viewmodel/AppearanceViewModelTest.kt`
  с 4 методами (8 кейсов с учётом параметризации):
  - `update_theme_should_update_all_themes(theme: AppTheme)` — `@EnumSource`,
    3 кейса; проверяет `coVerify { mockDataStore.setTheme(theme) }`.
  - `update_dynamic_colors_should_save_true_to_data_store` —
    проверяет `coVerify { mockDataStore.setUseDynamicColors(true) }`.
  - `update_dynamic_colors_should_save_false_to_data_store` —
    аналогично для `false`.
  - `ui_state_should_emit_combined_theme_and_dynamic_colors(theme,
    useDynamicColors)` — `@MethodSource("themeAndDynamicColorsPairs")`,
    3 кейса; через `backgroundScope.launch { collect { } }` +
    `advanceUntilIdle()` + `.value` (НЕ через `.first()` — для
    `StateFlow` с `WhileSubscribed` `.first()` возвращает
    `initialValue`).
- [x] 5.8 `setUp`/`tearDown` с `mockk(relaxed = true)` +
  `StandardTestDispatcher` + `Dispatchers.setMain/resetMain`.
  Property-getters (`theme`/`useDynamicColors`) — `every`; suspend
  сеттеры — `coEvery`/`coAnswers` (см. `kotlin-testing` skill).
- [x] 5.9 `make test` — 9/9 unit-тестов зелёные (1 `AppTheme` + 8
  `AppearanceViewModel`).

## 6. Локализация

- [x] 6.1 В `app/src/main/res/values/strings.xml` (ru) добавить
  `appearance_title`, `app_theme`, `light`, `dark`, `system`,
  `dynamic_colors`, `use_dynamic_colors`, `dynamic_colors_hint`, `back`.
- [x] 6.2 В `app/src/main/res/values-en/strings.xml` (en) добавить те же
  ключи на английском.
- [x] 6.3 В `app/src/main/res/values/dimens.xml` добавить
  `button_horizontal_padding = 16dp` (без `values-en/` — Android
  резолвит для всех локалей из дефолтного).

## 7. UI — AppearanceScreen + тесты

- [x] 7.1 Создать `app/src/main/java/com/myworkouts/ui/screens/appearance/AppearanceScreen.kt`:
  `@Composable fun AppearanceScreen(viewModel: AppearanceViewModel,
  onBackClick: () -> Unit)`.
- [x] 7.2 В `AppearanceScreen` подписаться на `viewModel.uiState.collectAsState()`
  и вычислить
  `val isDynamicColorsAvailable = remember { Build.VERSION.SDK_INT >=
  Build.VERSION_CODES.S }`. Смапить в `AppearanceScreenState`.
- [x] 7.3 Создать `internal data class AppearanceScreenState(theme,
  useDynamicColors, onThemeChange, onDynamicColorsChange,
  isDynamicColorsAvailable, onBackClick)`.
- [x] 7.4 Создать `internal @Composable fun AppearanceScreenContent(state)`:
  `Scaffold` + `TopAppBar` (с back IconButton +
  `contentDescription = stringResource(R.string.back)`) + `Column` с
  `Modifier.fillMaxWidth().verticalScroll(scrollState).padding(paddingValues)
  .padding(16.dp)` и `verticalArrangement = Arrangement.spacedBy(16.dp)`
  (порядок модификаторов важен: `padding(paddingValues)` — insets
  от `Scaffold`, затем `padding(16.dp)` — отступ тела экрана от краёв);
  внутри `ThemeSection`, затем `HorizontalDivider()` (между секциями;
  см. `specs/appearance/spec.md` — требование о разделителе), и
  опциональной `DynamicColorsSection` (обёрнута в `if
  (state.isDynamicColorsAvailable)`).
- [x] 7.5 Реализовать private `ThemeSection(theme, onThemeChange)`:
  `Text(stringResource(R.string.app_theme))` + `Column(Modifier.selectableGroup())`
  с тремя `ThemeRadioRow(label, selected = theme == X, enabled = theme != X,
  onClick = { onThemeChange(X) })`.
- [x] 7.6 Реализовать private `ThemeRadioRow(label, selected, enabled, onClick)`:
  `Row(Modifier.selectable(selected, onClick, enabled, role = Role.RadioButton).fillMaxWidth().height(56.dp))`
  с `MaterialRadioButton(selected, onClick = null)` + `Text`.
- [x] 7.7 Реализовать private `DynamicColorsSection(useDynamicColors, onDynamicColorsChange)`:
  `Text(dynamic_colors)` + `Row(SpaceBetween) { Text(use_dynamic_colors, weight=1f); Switch(...) }`
  + `Text(dynamic_colors_hint, bodySmall)`.
- [x] 7.8 Создать `app/src/androidTest/java/com/myworkouts/ui/screens/appearance/AppearanceScreenTest.kt`
  с `@RunWith(Parameterized::class)` + 3 параметрами (`AppTheme.entries`)
  и 2 тестами (6 кейсов): `appearance_screen_theme_radio_button_selected`
  (через `hasAnyChild(hasText(label)).and(isSelected)` в
  `useUnmergedTree = true` + `assertCountEquals(1)`) и
  `appearance_screen_clicks_theme_radio_button_calls_on_theme_change`
  (стартует с `initialTheme != theme`, чтобы radio был enabled).
- [x] 7.9 Создать `app/src/androidTest/java/com/myworkouts/ui/screens/appearance/AppearanceScreenStaticTest.kt`
  с `@RunWith(AndroidJUnit4::class)` (НЕ parameterized, чтобы не было
  ×3-буста) и 6 тестами: `appearance_screen_displays_app_bar_with_back_button`,
  `appearance_screen_displays_app_theme_section`,
  `appearance_screen_displays_dynamic_colors_section_when_available`,
  `appearance_screen_hides_dynamic_colors_section_when_not_available`
  (через `assertDoesNotExist()`, а не `assertIsNotDisplayed()` — детерминированно
  независимо от SDK эмулятора),
  `appearance_screen_switch_dynamic_colors_with_use_dynamic_colors_true_changes_to_false`,
  `appearance_screen_switch_dynamic_colors_with_use_dynamic_colors_false_changes_to_true`
  (через `onNode(isToggleable()).performClick()`).

## 8. Навигация — рефакторинг Screen + добавление Appearance

- [x] 8.1 Обновить `app/src/main/java/com/myworkouts/navigation/Screen.kt`:
  `sealed class Screen(val route: String)` (только `route`, без
  `icon`/`titleResId`/`AllTabs` — UI-метаданные вынесены в
  `BottomTab`). Добавить `object Appearance : Screen(route = "appearance")`.
- [x] 8.2 В том же файле добавить
  `data class BottomTab(val route: String, val icon: ImageVector,
  val titleResId: Int)` + `val BottomTabs: List<BottomTab> = listOf(
  BottomTab(Screen.Main.route, Icons.Filled.Home, R.string.workouts_title),
  BottomTab(Screen.More.route, Icons.Filled.Menu, R.string.more_title))`.
- [x] 8.3 Обновить `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt`:
  `fun RootScreen(dataStore: AppSettingsDataStore, modifier: Modifier = Modifier)`.
  Внутри `NavigationBar` итерировать `BottomTabs.forEach { tab -> ... }`
  с `selected = currentRoute == tab.route`, `icon = { Icon(tab.icon,
  contentDescription = title) }`, `label = { Text(title) }`.
- [x] 8.4 В `RootScreen.NavHost` добавить
  `composable(Screen.Appearance.route) { AppearanceScreen(viewModel =
  viewModel(factory = AppearanceViewModel.factory(dataStore)),
  onBackClick = { navController.popBackStack() }) }`.
- [x] 8.5 `RootScreen.Scaffold` — `contentWindowInsets = WindowInsets(0, 0, 0, 0)`
  (как в `tab-navigation/design.md` Decision 3 — иначе double-subtract
  с `NavigationBar.windowInsets`).

## 9. MainActivity — интеграция

- [x] 9.1 Обновить `app/src/main/java/com/myworkouts/MainActivity.kt`:
  в `onCreate` ДО `setContent` — **одноразовый (cold-start)**
  `enableEdgeToEdge()` (initial state) + `val dataStore =
  AppSettingsDataStore(applicationContext)`. Это первая из двух точек
  вызова `enableEdgeToEdge`; см. п. 9.3 для реактивной.
- [x] 9.2 Внутри `setContent` — `val theme by dataStore.theme.collectAsState(
  initial = AppTheme.SYSTEM)`, `val useDynamicColors by
  dataStore.useDynamicColors.collectAsState(initial = true)`, `val
  isDarkTheme = theme.isDark(isSystemInDarkTheme())`.
- [x] 9.3 **Реактивный** `LaunchedEffect(isDarkTheme) { enableEdgeToEdge(
  statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT)
  else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)) }` —
  вторая (и последняя) из двух точек вызова `enableEdgeToEdge` (первая —
  п. 9.1, cold-start). Без него при рантайм-переключении темы статус-бар
  остаётся в cold-start стиле.
- [x] 9.4 `JetpackMyWorkoutsTheme(appTheme = theme, dynamicColor =
  useDynamicColors) { RootScreen(dataStore = dataStore) }`.
- [x] 9.5 (см. п. 5.6 — `MainActivityViewModel` НЕ создаётся).

## 10. MoreScreen — кнопка «Оформление приложения»

- [x] 10.1 Обновить `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt`:
  `fun MoreScreen(navController: NavHostController, modifier: Modifier =
  Modifier)` (обязательный `navController` — единственный вызов в
  `RootScreen`).
- [x] 10.2 В `Scaffold` body добавить `Box(fillMaxSize, padding(8.dp))`
  с двумя детьми: `Row(fillMaxWidth, Arrangement.Center) { FilledTonalButton(
  onClick = { navController.navigate(Screen.Appearance.route) {
  popUpTo(Screen.More.route) } }, contentPadding = PaddingValues(
  horizontal = dimensionResource(R.dimen.button_horizontal_padding))) {
  Text(stringResource(R.string.appearance_title)) } }` и существующий
  `Text(app_version, ..., Alignment.BottomCenter)`.
- [x] 10.3 `MoreScreen.Scaffold` —
  `contentWindowInsets = WindowInsets.safeDrawing.only(
  WindowInsetsSides.Horizontal)` (симметрично `tab-navigation`
  insets-контракту).

## 11. androidTest — MoreScreen

- [x] 11.1 Создать `app/src/androidTest/java/com/myworkouts/ui/screens/more/MoreScreenTest.kt`
  с `@RunWith(AndroidJUnit4::class)` и 4 тестами:
  `more_screen_when_displayed_then_shows_appearance_button` (через
  `onNodeWithText(R.string.appearance_title).assertIsDisplayed()`),
  `more_screen_when_appearance_button_clicked_then_navigates_to_appearance_route`
  (через `lateinit var navController` + `onControllerReady`-колбэк
  внутри test-harness `MoreScreenWithNavHost` + `waitForIdle()` +
  `navController.currentDestination?.route == Screen.Appearance.route`),
  `more_screen_when_displayed_then_shows_app_version` (через
  `context.getString(R.string.app_version, BuildConfig.VERSION_NAME)`),
  `more_screen_when_displayed_then_appearance_button_is_horizontally_centered`
  (регресс-тест: `getBoundsInRoot().value` → `leftMargin == rightMargin
  ±1px`).
- [x] 11.2 Test-harness `MoreScreenWithNavHost` оборачивает `MoreScreen`
  в `NavHost(startDestination = Screen.More.route) { composable(More) {
  MoreScreen(navController) }; composable(Appearance) {} }` — иначе
  `navigate()` падает с `IllegalArgumentException: Navigation graph
  has not been set`.

## 12. Финальная верификация

- [x] 12.1 `make format && make lint && make test && make build` — все
  четыре зелёные (полный список команд верификации также
  продублирован в `proposal.md:178` и `design.md:47`).
- [x] 12.2 `make android-test` — 16/16 кейсов зелёные
  (`AppearanceScreenTest` 6 + `AppearanceScreenStaticTest` 6 +
  `MoreScreenTest` 4).
- [x] 12.3 Ручная проверка на эмуляторе:
  - «Ещё» → кнопка «Оформление приложения» видна и **горизонтально
    отцентрирована** (фикс `Row(fillMaxWidth, Arrangement.Center)`).
  - Клик → открывается Appearance с секцией «Тема приложения» (3 radio)
    и «Динамические цвета» (Switch); секции «Иконка приложения» нет.
  - Переключение темы на «Тёмная» → весь UI перекрашивается
    (включая статус-бар); после `am force-stop` тема сохраняется.
  - Per-app locale (`cmd locale set-app-locales --locales ru`) → все
    строки переводятся на русский.
- [x] 12.4 Никаких секций/моделей/кода для иконки приложения в проекте
  не появилось (`AppIcon`/`IconManager`/`IconSection` — out of scope).
- [x] 12.5 `AppSettingsDataStore` создаётся строго один раз в
  `MainActivity.onCreate` (`RootScreen` принимает его параметром).
