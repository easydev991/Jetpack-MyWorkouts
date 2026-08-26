## Context

Пользовательский путь к настройкам оформления отсутствует:
`JetpackMyWorkoutsTheme` принимает только `darkTheme`/`dynamicColor` —
выбор темы и динамических цветов зашит в `MainActivity` (системная
`isSystemInDarkTheme()` + `dynamicColor = true`). Сохранения между
перезапусками нет. Секция «Оформление» в нижней навигации не нужна —
это вторичный экран, доступ через `MoreScreen` (как в `JetpackDays`).

Образец — `JetpackDays` (`ThemeIconScreen` + `AppSettingsDataStore` +
`MainActivityViewModel`). В этом change переносим **только оформление**
(выбор темы + переключатель динамических цветов) — секция смены иконки
приложения и весь связанный код (`AppIcon`/`IconManager`/
`MainActivityIcon2..6` в `AndroidManifest.xml`/`AnalyticsService`/
`Logger`/`MyWorkoutsApplication`/`di/AppModule`) **не переносим** — у нас
их нет и не предвидится.

AGENTS.md требует: offline-only (без сетевых зависимостей), manual DI
(без Hilt), `StateFlow` + `WhileSubscribed(5000)`, sealed для
UI-состояний, `Result<T>` для use-cases, без `!!`, ru/en локализация
(`values/strings.xml` = ru primary, `values-en/strings.xml` = en).

Источник истины — `docs/appearance-screen-plan.md` (375 строк; все 11
этапов закрыты и протестированы). План писался до реализации; часть
деталей устарела (см. Decision 1, 2, 3, 4). Реальные артефакты —
исходный код, ресурсы и тесты.

## Goals / Non-Goals

**Goals:**
- Экран «Оформление приложения» с секциями «Тема приложения» (3 radio:
  `LIGHT`/`DARK`/`SYSTEM`) и «Динамические цвета» (`Switch` + подсказка;
  секция целиком скрывается, если `Build.VERSION.SDK_INT < S`).
- Персистентность выбора в `DataStore` (`appearance_prefs`, ключи
  `theme` + `use_dynamic_colors`, дефолты `SYSTEM`/`true`).
- Применение выбора ко всему UI (включая статус-бар) через
  `MainActivity` (`dataStore.collectAsState`) +
  `JetpackMyWorkoutsTheme(appTheme = theme, dynamicColor = useDynamicColors)`.
- Кнопка «Оформление приложения» в `MoreScreen` (горизонтально
  центрированная `FilledTonalButton`) с навигацией на
  `Screen.Appearance.route` через `popUpTo(Screen.More.route)`.
- Unit-покрытие: 9 кейсов (1 `AppTheme` + 8 `AppearanceViewModel`).
- AndroidTest покрытие: 16 кейсов (6 parameterized + 6 static на
  `AppearanceScreen`, 4 на `MoreScreen` включая регресс на
  центрирование).
- Локализация ru/en: 9 ключей × 2 локали.
- `make format && make lint && make test && make build && make android-test` —
  все зелёные; ручная проверка: переключение темы, persistence,
  per-app locale ru.

**Non-Goals:**
- Секция смены иконки приложения (нет в нашем проекте; `AppIcon`/
  `IconManager`/алиасы в `AndroidManifest.xml`/`IconPreviewItem`/
  `IconGrid` — out of scope).
- Аналитика (`AnalyticsService`/`NoopAnalyticsProvider`) — у нас нет
  Firebase; AGENTS.md не требует.
- `Logger`/`AndroidLogger`/`NoOpLogger` — YAGNI: в `AppearanceViewModel`
  логгер только пишет в `logger.d`, поведение не меняет.
- `MyWorkoutsApplication` — не нужен: `AppSettingsDataStore` создаётся
  один раз в `MainActivity.onCreate`.
- `di/AppModule.kt` (Hilt-like) — manual DI через standalone constructor
  достаточно для единственной зависимости `AppSettingsDataStore`.
- `MainActivityViewModel` — YAGNI: два `dataStore.*.collectAsState`
  в `MainActivity.setContent` дают ровно тот же результат без
  дополнительной VM и factory.
- `AppConstants.STATE_TIMEOUT_MS = 5000L` — вынос в общую константу
  для одного места over-engineering; литерал инлайнен в
  `AppearanceViewModel`.
- Отдельный `ui/screens/common/RadioButton.kt` — YAGNI: радиокнопка
  нужна ровно в одном месте (`ThemeSection`); `Modifier.selectable` —
  стандартный Compose API, не требует обёртки.
- Отдельный `ui/screens/common/DynamicColors.kt` — YAGNI: проверка
  `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` нужна ровно в двух
  местах (`Theme.kt` для colorScheme + `AppearanceScreen` для условного
  рендера секции). Однострочный inline, выделение в helper ничего
  не даёт.
- Другие пользовательские сценарии (добавление тренировки,
  фильтрация, сортировка) — отдельные changes.

## Decisions

### 1. `Screen` хранит только `route`; UI-метаданные вынесены в
`BottomTab` (отход от плана)

**Решение:** `sealed class Screen(val route: String)` (только `route`);
отдельно `data class BottomTab(val route: String, val icon: ImageVector,
val titleResId: Int)` + `val BottomTabs: List<BottomTab> = listOf(
BottomTab(Screen.Main.route, Icons.Filled.Home, R.string.workouts_title),
BottomTab(Screen.More.route, Icons.Filled.Menu, R.string.more_title),
)`. `Screen.Appearance` — без `BottomTab` (не в нижней навигации).

**Альтернативы (что было в плане):**
- `sealed class Screen(route, icon: ImageVector?, titleResId: Int?)` с
  nullable-полями (BREAKING от non-null контракта в
  `tab-navigation/spec.md`).
- Sealed-подтипы `TabScreen`/`NonTabScreen`.

**Почему отвергнуто:** nullable-поля в `Screen` плохо читаются
(`.icon?.let { ... }` + `.orEmpty()` в `NavigationBarItem`); sealed-подтипы
— over-engineering для **одного** не-tab экрана. Разделение
`Screen` (route) + `BottomTab` (UI-метаданные) делает ответственности
явными: `Screen` — для навигации, `BottomTab` — для UI таба. Цена —
один дополнительный тип и `BottomTabs` (5 строк), выигрыш — чистый
`Screen` без `null`-ов и расширяемость на любые non-tab экраны без
села.

**Breaking:** `tab-navigation/spec.md` (`Screen.icon`/`titleResId` non-null
- `AllTabs: List<Screen>`) устарел → обновится при архивации этого change.

### 2. `MainActivity` подписывается на DataStore напрямую (без
`MainActivityViewModel`)

**Решение:** `MainActivity.onCreate` создаёт
`dataStore = AppSettingsDataStore(applicationContext)`. Внутри `setContent`:

```
val theme by dataStore.theme.collectAsState(initial = AppTheme.SYSTEM)
val useDynamicColors by dataStore.useDynamicColors.collectAsState(initial = true)
val isDarkTheme = theme.isDark(isSystemInDarkTheme())
```

**Альтернативы (что было в плане):** `MainActivityViewModel` с
`dataStore.theme.stateIn(...)` + `dataStore.useDynamicColors.stateIn(...)`
- `companion factory(dataStore)`.

**Почему отвергнуто:** `MainActivity` — единственный consumer двух
потоков. `collectAsState` уже даёт `State<T>` с lifecycle-aware
подпиской. `stateIn(viewModelScope, WhileSubscribed(5000), default)` в
отдельной VM — это `collectAsState` + `viewModelScope` (5 секунд
таймаут) + factory + тесты (3 кейса) — при идентичном результате.
Ponytail: меньше кода, меньше церемоний, меньше тестов.

### 3. Радиокнопка inline в `AppearanceScreen`, без `common/RadioButton.kt`

**Решение:** private `ThemeRadioRow(label, selected, enabled, onClick)` в
`AppearanceScreen.kt` — `Row` с `Modifier.selectable(selected, onClick,
enabled, role = Role.RadioButton)`, `MaterialRadioButton` (`onClick = null`
рекомендован для accessibility), `Text`.

**Альтернативы (что было в плане):** отдельный
`ui/screens/common/RadioButton.kt` (копия `DaysRadioButton` без префикса).

**Почему отвергнуто:** единственное место использования — `ThemeSection`
в `AppearanceScreen`. Обёртка в общий файл ради одной строки — лишний
import, лишний файл, лишний indirection. `Modifier.selectable` — стандартный
Compose API, не требует адаптера. Selected semantic ловится тестами
через `hasAnyChild(hasText(label)).and(isSelected)` в `useUnmergedTree = true`.

### 4. `isDynamicColorsAvailable` вычисляется inline, без
`common/DynamicColors.kt`

**Решение:** в `AppearanceScreen` (внешний wrapper, не `Content`):
`val isDynamicColorsAvailable = remember { Build.VERSION.SDK_INT >=
Build.VERSION_CODES.S }`. Прокидывается в `AppearanceScreenState`. В
`Theme.kt` — отдельный inline-чек `Build.VERSION.SDK_INT >=
Build.VERSION_CODES.S` для `dynamicColorScheme()`.

**Альтернативы (что было в плане):** отдельный
`ui/screens/common/DynamicColors.kt` с `@Composable fun
isDynamicColorAvailable(): Boolean`.

**Почему отвергнуто:** проверка — однострочная inline-операция.
Обёртка в composable-функцию ради того, чтобы написать `isDynamicColorAvailable()`
вместо `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S`, ничего не
даёт. `remember` в `AppearanceScreen` гарантирует, что проверка
выполнится один раз за lifecycle экрана.

**Альтернатива внутри `Content`:** вычислить в `Content` (план
предлагал). Отвергнуто: `Content` принимает plain `State` — если
`isDynamicColorsAvailable` вычисляется в `Content`, тесты получают
недетерминированное поведение в зависимости от SDK эмулятора
(`assertIsNotDisplayed()` для скрытой секции = flakiness). Прокидывание
готового `Boolean` через `State` детерминирует тесты
(`assertDoesNotExist()` для скрытой секции).

### 5. `AppSettingsDataStore` через `class`, не через `MyWorkoutsApplication`

**Решение:** `class AppSettingsDataStore(private val context: Context)`.
Top-level `private val Context.dataStore by preferencesDataStore(name =
"appearance_prefs")` (делегат создаёт DataStore при первом обращении
через `applicationContext`).

**Альтернативы:**
- `class MyWorkoutsApplication : Application()` с `val dataStore by
  preferencesDataStore(...)` (как в JetpackDays).
- Standalone `fun createAppSettingsDataStore(context: Context):
  DataStore<Preferences>` (план предлагал).

**Почему отвергнуто:** `class AppSettingsDataStore(context)` — тот же
контракт, что и standalone factory (`createAppSettingsDataStore(context)`),
но не требует глобальной функции и проще в тестах (конструктор принимает
`Context` напрямую, без unwrap). `MyWorkoutsApplication` — лишняя
сущность для одной зависимости, требует регистрации в `AndroidManifest.xml`.
`MainActivity` создаёт `AppSettingsDataStore(applicationContext)` один
раз → передаёт в `RootScreen`.

**Критично:** DataStore создаётся строго один раз. Повторное создание
с тем же `name` → `IllegalStateException: There are multiple DataStores
active for the same file`. Поэтому `RootScreen(dataStore: AppSettingsDataStore)`
принимает его параметром, а не создаёт внутри.

### 6. `AppTheme.isDark(systemDark: Boolean): Boolean` extension

**Решение:** extension в `AppTheme.kt`:

```
fun AppTheme.isDark(systemDark: Boolean): Boolean = when (this) {
    AppTheme.LIGHT -> false
    AppTheme.DARK -> true
    AppTheme.SYSTEM -> systemDark
}
```

**Альтернативы:** inline `when` в `MainActivity` и в `Theme.kt`.

**Почему отвергнуто:** один и тот же `when` повторяется в `MainActivity`
(`isDarkTheme = theme.isDark(isSystemInDarkTheme())`) и в `Theme.kt`
(`val useDarkTheme = appTheme.isDark(darkTheme)`). Extension-функция
даёт одно место истины для семантики `AppTheme → dark/light`. Ponytail:
single source of truth без лишних абстракций.

### 7. `JetpackMyWorkoutsTheme` принимает `appTheme: AppTheme = SYSTEM`
non-null

**Решение:** `JetpackMyWorkoutsTheme(darkTheme: Boolean = isSystemInDarkTheme(),
appTheme: AppTheme = AppTheme.SYSTEM, dynamicColor: Boolean = true, content)`.

**Альтернативы:** nullable `appTheme: AppTheme?` (как в JetpackDays).

**Почему отвергнуто:** `MainActivity` подписывается на `dataStore.theme`
через `collectAsState(initial = AppTheme.SYSTEM)` — значение есть
сразу с `SYSTEM`-дефолтом. null-период невозможен, nullable излишен.
Меньше церемоний с `?.let` в `Theme.kt`.

### 8. `AppearanceViewModel` — `combine` в один `stateIn`

**Решение:** `val uiState: StateFlow<AppearanceUiState>` через
`combine(dataStore.theme, dataStore.useDynamicColors).stateIn(viewModelScope,
WhileSubscribed(5000L), AppearanceUiState())`.

**Альтернативы:** два отдельных `stateIn` (как предлагалось для
`MainActivityViewModel` в плане).

**Почему отвергнуто:** `AppearanceScreen` рендерит обе секции вместе
(ThemeSection + DynamicColorsSection в одной `Column`). Composable
подписывается на `uiState` как на единый snapshot — два потока = два
`collectAsState` + ручной `data class`-merge в UI. `combine` + один
`stateIn` = один `collectAsState`.

**Согласованность с Decision 2:** в `MainActivity` потоки независимы
(`theme` → `Theme`, `useDynamicColors` → `Theme` через разные
параметры), но `collectAsState` + прямые prop'ы проще, чем VM; в
`AppearanceViewModel` — один snapshot (один consumer, две настройки,
одна точка выхода). Ponytail: не плодить абстракции «для единообразия».

### 9. `AppearanceScreen` через internal `State` + внешний wrapper

**Решение:** `internal data class AppearanceScreenState(theme,
useDynamicColors, onThemeChange, onDynamicColorsChange,
isDynamicColorsAvailable, onBackClick)` + `internal @Composable fun
AppearanceScreenContent(state)` + внешний `@Composable fun
AppearanceScreen(viewModel, onBackClick)`.

**Альтернативы:** один `AppearanceScreen` с прямой подпиской на VM.

**Почему отвергнуто:** тестируемость. `AppearanceScreenContent` принимает
plain `State` (без `viewModel()`/`hiltViewModel()`) → тесты на
`createComposeRule` рендерят `Content` напрямую с фейковым state, без
поднятия `viewModel`-инфраструктуры в `androidTest`. Точно так же
сделан `ThemeIconScreen` в JetpackDays.

### 10. `enableEdgeToEdge` через `LaunchedEffect(isDarkTheme)`

**Решение:** `LaunchedEffect(isDarkTheme) { enableEdgeToEdge(statusBarStyle =
if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else
SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)) }` в
`MainActivity.setContent`. Однократный `enableEdgeToEdge()` в `onCreate`
вызывается **до** `setContent` (для initial state).

**Альтернативы:** однократный `enableEdgeToEdge` в `onCreate`.

**Почему отвергнуто:** без `LaunchedEffect(isDarkTheme)` при
`updateTheme(DARK)` `MaterialTheme` перекрасится, а статус-бар останется
в стиле cold-start (светлые иконки на светлом фоне) — визуальная
регрессия. `LaunchedEffect` пересоздаёт edge-to-edge при смене темы.
Ponytail: один `LaunchedEffect` с ключом дешевле, чем ручной
`DisposableEffect` + отслеживание.

### 11. `MoreScreen(navController: NavHostController)` — обязательный параметр

**Решение:** `MoreScreen(navController: NavHostController, modifier:
Modifier = Modifier)`.

**Альтернативы:** `MoreScreen(onNavigateToAppearance: () -> Unit, modifier)`.

**Почему отвергнуто:** единственный вызов `MoreScreen` — в
`composable(Screen.More.route) { MoreScreen(navController = navController) }`
внутри `RootScreen`. Превью (если понадобится) — `rememberNavController()`.
Callback-обёртка ради одной навигации = лишний indirection; прямое
`navController.navigate(Screen.Appearance.route) { popUpTo(Screen.More.route) }`
— ровно то, что нужно.

### 12. Кнопка «Оформление приложения» через `FilledTonalButton` в
`Row(fillMaxWidth, Arrangement.Center)`

**Решение:** внутри `Box` (для `BottomCenter`-версии) — `Row(modifier =
Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
FilledTonalButton(onClick = { ... }, contentPadding = PaddingValues(
horizontal = dimensionResource(R.dimen.button_horizontal_padding))) {
Text(stringResource(R.string.appearance_title)) } }`.

**Альтернативы:** `Box { MoreButton(Modifier.align(Alignment.TopStart)) }`
(без `Row`).

**Почему отвергнуто:** `Box` по умолчанию ставит ребёнка в `TopStart` —
кнопка уезжает в левый верхний угол. `Row(fillMaxWidth, Arrangement.Center)`
центрирует только по горизонтали, оставляя остальной контент
(`AppVersionText` в `BottomCenter`) без изменений. **Регресс-тест:**
`MoreScreenTest` через `getBoundsInRoot().value` проверяет
`leftMargin == rightMargin ±1px` (фикс на `085a68a`).

### 13. `AppSettingsDataStore` — только 2 ключа (`theme`, `use_dynamic_colors`)

**Решение:** `appearance_prefs` хранит ровно `theme` (`String`, default
`SYSTEM.name`) и `use_dynamic_colors` (`Boolean`, default `true`).

**Альтернативы:** перенести полный набор ключей из JetpackDays
(`icon`/`sortOrder`/`colorTagFilter` и т.д.).

**Почему отвергнуто:** YAGNI. У нас нет ни смены иконки, ни сортировки
списка тренировок, ни фильтрации. Расширение DataStore — точечное, по
мере появления фич. **Trigger-watch:** при добавлении нового ключа —
добавляем пару Flow/suspend + дефолт; имя файла `appearance_prefs` не
менять (DataStore переход на новый файл = миграция).

### 14. `AppearanceUiState` — plain `data class`, не `sealed class`
(отход от правила AGENTS.md)

**Решение:** `data class AppearanceUiState(theme: AppTheme =
AppTheme.SYSTEM, useDynamicColors: Boolean = true)` —
простой snapshot двух настроек без `Loading`/`Error` веток.

**Альтернативы:** `sealed class AppearanceUiState { object Loading;
data class Success(...); data class Error(message: String) }` — по
букве AGENTS.md:44.

**Почему отвергнуто:** `AppearanceScreen` рендерит два `StateFlow`-значения
из `AppSettingsDataStore` (`theme` + `useDynamicColors`); оба имеют
дефолты (`SYSTEM` / `true`) и никогда не уходят в loading/error
(DataStore всегда отдаёт значение синхронно после первого collect).
Sealed-иерархия ради двух состояний, которые никогда не случаются, —
over-engineering. **Trigger-watch:** если в UI добавятся async-сценарии
(Flow из сети, retry, ошибка записи) — переделать на `sealed class`
по правилу AGENTS.md. До этого — plain `data class`.

## Risks / Trade-offs

- **[BREAKING: `Screen` теряет `icon`/`titleResId` + `AllTabs`; ввод
  `BottomTab` + `BottomTabs`]** → Митигация: `Main`/`More` остаются
  объектами `Screen` (без изменений в `route`); `RootScreen` обновлён
  для работы с `BottomTabs`; `tab-navigation/spec.md` обновится при
  архивации этого change. Один дополнительный тип (`BottomTab`, 5 строк)
  окупается чистотой `Screen`.
- **[DataStore singleton требует аккуратного создания]** → Митигация:
  конструктор `AppSettingsDataStore(applicationContext)` вызывается
  ровно один раз в `MainActivity.onCreate` ДО `setContent`; `RootScreen`
  принимает DataStore параметром и пробрасывает в `composable`. Любая
  попытка создать второй DataStore с тем же файлом = `IllegalStateException`
  (явная ошибка).
- **[Single ViewModel с `WhileSubscribed(5000L)`]** → Единственное
  использование литерала — `AppearanceViewModel.kt:59` (`stateIn`).
  Вынос в `AppConstants.STATE_TIMEOUT_MS` — over-engineering
  (один потребитель; см. также `design.md:62–68` Non-Goal и
  Decision 2 — `MainActivityViewModel` не создан). Trigger-watch:
  при появлении второго VM с тем же timeout — константа.
- **[Insets-контракт хрупкий]** → Митигация: комментарии-обоснования
  у `RootScreen.Scaffold` и `MoreScreen.Scaffold` (унаследовано из
  `tab-navigation/design.md` Decision 3 + insets-фикс).
- **[Dynamic colors section скрывается в зависимости от SDK]** →
  Митигация: `assertDoesNotExist()` в `AppearanceScreenStaticTest`
  (а не `assertIsNotDisplayed()`) — детерминированно независимо от
  SDK эмулятора.
- **[Per-app locale `cmd locale set-app-locales --locales ru` не
  покрывается androidTest]** → Допустимо: ручная проверка на эмуляторе,
  per-app locale = стандартный API Android 13+, тест-инфраструктура
  Compose Testing не предоставляет надёжного хука.
- **[Long-term: `AppSettingsDataStore` — единственная зависимость, но
  `MainActivity` создаёт её сам]** → При появлении второй VM с
  зависимостью от `DataStore` — вынести в `MyWorkoutsApplication` (один
  source of truth + lazy delegate). Сейчас — YAGNI.

## Migration Plan

Не требуется — приложение до этого change не имело пользовательских
настроек (тема/динамические цвета были захардкожены). Существующих
пользователей/инсталляций с пользовательскими настройками нет. Все
правки — внутри репозитория, фиксируются в коммитах на ветке `main`
(см. `docs/appearance-screen-plan.md` §"Сводка реализации по этапам"):

- `c2ca5ed` — зависимости (Этап 1).
- `d72f70d` — `AppTheme` + `AppThemeTest` (Этап 2).
- `c273490` — `AppSettingsDataStore` (Этап 3).
- `e4be290` + `f129b5d` — радиокнопки + dynamic colors inline в
  `AppearanceScreen.kt` (Этап 4; план предлагал `common/`, реально —
  inline).
- `bb5269e` — `JetpackMyWorkoutsTheme(appTheme)` (Этап 5).
- `4ac494c` — `AppearanceViewModel` + тесты (Этап 6; `MainActivityViewModel`
  удалён — YAGNI).
- `e228c7c` — локализация ru/en (Этап 7).
- `83b85df` + `1686746` — `AppearanceScreen` + androidTest (Этап 8).
- `7a9a2e3` — `Screen.Appearance` (с `BottomTab`/`BottomTabs`-рефакторингом)
  - `RootScreen` + `MainActivity` (Этап 9).
- `8c039fc` — `MoreScreen` androidTest (Этап 10).
- `085a68a` — центрирование кнопки + регресс-тест (Этап 11).
- `083b8e8` — сжатие §11 плана после ручной верификации (doc-only).

При будущем добавлении ключей в `AppSettingsDataStore`:
миграция не нужна (новые ключи с дефолтами), но имя файла
`appearance_prefs` оставить.

## Open Questions

- Нет. Все решения приняты, реализованы и проверены (9/9 unit + 16/16
  androidTest + ручная верификация на эмуляторе `emulator-5554`).
  Дальнейшие вопросы (список тренировок, FAB-логика, реальная сортировка
  тренировок) — отдельные changes.
