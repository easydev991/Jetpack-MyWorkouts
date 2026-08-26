## Why

Пользователь не имеет способа сменить тему оформления и переключить
динамические цвета — оба параметра захардкожены в `JetpackMyWorkoutsTheme`
(через `isSystemInDarkTheme()` и `dynamicColor = true`). Сохранения
между перезапусками нет. До добавления любой персонализации нужен
минимальный пользовательский путь: экран «Оформление приложения» в
`MoreScreen` с выбором темы (`LIGHT`/`DARK`/`SYSTEM`) и переключателем
динамических цветов, оба параметра персистентны и применяются ко всему
UI (включая статус-бар).

Образец — `JetpackDays` (`ThemeIconScreen` + `AppSettingsDataStore` +
`MainActivityViewModel`). В этом change переносим **только оформление**:
секция смены иконки приложения и весь связанный код (`AppIcon`/
`IconManager`/алиасы в `AndroidManifest.xml`/`AnalyticsService`/
`Logger`/`MyWorkoutsApplication`/`di/AppModule`) — out of scope, у нас
их нет и не предвидится.

## What Changes

- Добавляем зависимости: `datastore-preferences 1.2.1`,
  `lifecycle-viewmodel-compose 2.11.0`, `kotlinx-coroutines-android 1.11.0`,
  `mockk 1.14.11` (в `gradle/libs.versions.toml` + `app/build.gradle.kts`).
- Добавляем domain-модель `AppTheme` (`enum LIGHT`/`DARK`/`SYSTEM`) +
  extension `AppTheme.isDark(systemDark: Boolean): Boolean`. Unit-тест
  фиксирует имена enum как контракт с `AppSettingsDataStore`.
- Добавляем data-слой `AppSettingsDataStore` с двумя Flow/suspend-парами
  (`theme`, `useDynamicColors`), дефолты `SYSTEM`/`true`,
  `IllegalArgumentException` → fallback на `SYSTEM`. Прочие ключи
  (`icon`/`sortOrder`/`colorTagFilter`) — YAGNI, не переносим.
- Обновляем `JetpackMyWorkoutsTheme(darkTheme, appTheme: AppTheme, dynamicColor, content)`:
  `useDarkTheme` через `appTheme.isDark(darkTheme)`; dynamic colors
  применяются на API 31+ через `Build.VERSION.SDK_INT` (inline в `Theme.kt`).
- Добавляем `AppearanceUiState` (plain `data class`) и `AppearanceViewModel`
  (`combine(dataStore.theme, dataStore.useDynamicColors).stateIn(...)` с
  `WhileSubscribed(5000L)`), `companion factory(dataStore)` через
  `viewModelFactory { initializer { ... } }`. Без `MainActivityViewModel`:
  `MainActivity` напрямую подписывается на `dataStore.{theme,useDynamicColors}`
  через `collectAsState` — YAGNI для двух потоков, не нужна отдельная VM.
- Добавляем экран `AppearanceScreen` (`Scaffold` + `TopAppBar` с
  back-кнопкой + `Column(verticalScroll)` с `ThemeSection` и опциональной
  `DynamicColorsSection`). Радиокнопки реализованы inline через
  `Modifier.selectable` (private `ThemeRadioRow`) — отдельный
  `ui/screens/common/RadioButton.kt` из плана **не создан** (YAGNI для
  единственного места использования). Динамические цвета скрываются
  через `isDynamicColorsAvailable: Boolean`, вычисляемое в `remember`
  как `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` и прокидываемое
  в internal `AppearanceScreenState` (не в `Content`) — для
  детерминированных UI-тестов.
- Добавляем ru/en строки: `appearance_title`, `app_theme`, `light`,
  `dark`, `system`, `dynamic_colors`, `use_dynamic_colors`,
  `dynamic_colors_hint`, `back` (используется как `contentDescription`
  кнопки «Назад»).
- Расширяем навигацию: добавляем `object Appearance : Screen(route = "appearance")`.
  `Screen` остаётся **только с `route`** (без `icon`/`titleResId`) — см.
  Decision 1 в `design.md`. Дополнительно вводим
  `data class BottomTab(route, icon, titleResId)` + `val BottomTabs: List<BottomTab>` —
  источник истины для нижней навигации. Это **breaking change** для
  `tab-navigation/spec.md` (раньше `Screen` имел `icon: ImageVector`/
  `titleResId: Int` non-null + `AllTabs: List<Screen>`).
- Обновляем `MoreScreen` (обязательный `navController: NavHostController`):
  добавляем кнопку «Оформление приложения» (`FilledTonalButton` в
  `Row(fillMaxWidth, Arrangement.Center)`), навигация на
  `Screen.Appearance.route` с `popUpTo(Screen.More.route)`. Новый dimen
  `button_horizontal_padding = 16dp` в `values/dimens.xml`.
- Обновляем `RootScreen(dataStore: AppSettingsDataStore, modifier)`:
  принимает DataStore параметром; `composable(Screen.Appearance.route)`
  — `viewModel(factory = AppearanceViewModel.factory(dataStore))` +
  `popBackStack()` по back.
- Обновляем `MainActivity`: до `setContent` создаём
  `dataStore = AppSettingsDataStore(applicationContext)`; внутри
  `setContent` — `dataStore.theme.collectAsState(initial = AppTheme.SYSTEM)`
  и `dataStore.useDynamicColors.collectAsState(initial = true)`,
  `isDarkTheme = theme.isDark(isSystemInDarkTheme())`,
  `LaunchedEffect(isDarkTheme) { enableEdgeToEdge(statusBarStyle = ...) }`
  (без этого статус-бар остаётся в cold-start стиле при рантайм-переключении
  темы), `JetpackMyWorkoutsTheme(appTheme = theme, dynamicColor =
  useDynamicColors) { RootScreen(dataStore = dataStore) }`.
- Покрываем androidTest: `AppearanceScreenTest` (parameterized ×3
  `AppTheme` через `@RunWith(Parameterized::class)`: 2 кейса) +
  `AppearanceScreenStaticTest` (6 кейсов через `@RunWith(AndroidJUnit4::class)`,
  чтобы не было ×3-буста на AppTheme) и `MoreScreenTest` (4 кейса:
  кнопка видна, навигация, версия, регресс-тест горизонтального
  центрирования через `getBoundsInRoot()`).
- Покрываем unit-test: `AppThemeTest` (1) + `AppearanceViewModelTest`
  (8 кейсов: 3 `update_theme` через `@EnumSource` + 2 `update_dynamic_colors`
  + 3 `ui_state` через `@MethodSource`).
- `make format && make lint && make test` — все три зелёные.
  `make android-test` — `AppearanceScreenTest` +
  `AppearanceScreenStaticTest` + `MoreScreenTest` зелёные.
  Ручная проверка: переключение темы → весь UI перекрашивается
  (включая статус-бар); `am force-stop` → тема сохраняется;
  `cmd locale set-app-locales --locales ru` → русские строки.

## Capabilities

### New Capabilities

- `appearance`: экран «Оформление приложения» (выбор темы +
  переключатель динамических цветов), persistence через
  `AppSettingsDataStore`, применение выбора на уровне всего приложения
  через `JetpackMyWorkoutsTheme`. Включает домен (`AppTheme` +
  `isDark`), data-слой (`AppSettingsDataStore`), UI (`AppearanceScreen`),
  VM (`AppearanceViewModel`), интеграцию в `MainActivity` +
  `RootScreen`, локализацию ru/en и unit-/androidTest покрытие.
- `non-tab-screen-navigation`: `Screen.Appearance` (`route = "appearance"`)
  без иконки/заголовка, потому что в нижней навигации не участвует.
  Реализовано через **рефакторинг**: `Screen` теперь хранит только
  `route`; UI-метаданные (иконка, заголовок) живут в отдельной
  `data class BottomTab` + `val BottomTabs` — это **BREAKING** контракта
  из `tab-navigation/spec.md` (раньше `Screen` сам имел
  `icon: ImageVector` / `titleResId: Int` non-null + `AllTabs: List<Screen>`).

### Modified Capabilities

- `tab-navigation`: `Screen` теряет `icon: ImageVector` и
  `titleResId: Int`, становится `sealed class Screen(val route: String)`
  (**BREAKING** — ранее в `tab-navigation/spec.md` `Screen` содержал
  `icon`/`titleResId` non-null). Дополнительно: `AllTabs: List<Screen>`
  удаляется; вместо него — `data class BottomTab(route, icon, titleResId)`
  + `val BottomTabs: List<BottomTab>`. `Main`/`More` остаются объектами
  `Screen` (без изменений в `route`); `Appearance` добавляется как
  новый объект `Screen`. `RootScreen` теперь принимает
  `dataStore: AppSettingsDataStore` параметром.
- `more-screen`: `MoreScreen` принимает обязательный `navController:
  NavHostController` (раньше — только `modifier`); в `Scaffold` body
  добавляется центрированная `FilledTonalButton` с локализованным
  текстом `appearance_title`, навигация на `Screen.Appearance.route`
  через `popUpTo(Screen.More.route)`. Регресс-тест на
  горизонтальное центрирование через `getBoundsInRoot()`.

## Impact

- Зависимости Gradle (`gradle/libs.versions.toml`,
  `app/build.gradle.kts`): новые `datastore-preferences`,
  `lifecycle-viewmodel-compose`, `kotlinx-coroutines-android`, `mockk`.
  Никаких сетевых — offline-only требование AGENTS.md сохраняется.
- `app/src/main/java/com/myworkouts/`:
  - `domain/model/AppTheme.kt` — новый, с extension `isDark()`.
  - `data/preferences/AppSettingsDataStore.kt` — новый пакет `data`.
  - `ui/screens/appearance/AppearanceScreen.kt` — новый
    (включая inline `ThemeSection`/`ThemeRadioRow`/`DynamicColorsSection`).
  - `ui/screens/more/MoreScreen.kt` — обновлён.
  - `ui/screens/root/RootScreen.kt` — принимает `dataStore` параметром;
    `NavigationBar` итерирует `BottomTabs` вместо `Screen.AllTabs`.
  - `ui/state/AppearanceUiState.kt` — новый.
  - `ui/viewmodel/AppearanceViewModel.kt` — новый (без отдельного
    `MainActivityViewModel`).
  - `ui/theme/Theme.kt` — `JetpackMyWorkoutsTheme` принимает
    `appTheme: AppTheme = SYSTEM`.
  - `navigation/Screen.kt` — `Screen` только с `route`; добавлен
    `object Appearance`; `BottomTab` + `BottomTabs` — новое.
  - `MainActivity.kt` — создание `dataStore`, `collectAsState` напрямую
    из `dataStore`, `LaunchedEffect(enableEdgeToEdge)`.
- Ресурсы (`res/values/strings.xml` ru + `res/values-en/strings.xml` en):
  +9 ключей (`appearance_title`, `app_theme`, `light`, `dark`, `system`,
  `dynamic_colors`, `use_dynamic_colors`, `dynamic_colors_hint`, `back`).
  `res/values/dimens.xml`: +1 (`button_horizontal_padding = 16dp`).
- Тесты: `app/src/test/java/com/myworkouts/` — JUnit 5 + MockK +
  `kotlinx-coroutines-test`: `domain/model/AppThemeTest` (1),
  `ui/viewmodel/AppearanceViewModelTest` (8 кейсов). `app/src/androidTest/`:
  JUnit 4 + Compose Testing: `ui/screens/appearance/AppearanceScreenTest`
  (parameterized ×3 `AppTheme` = 6 кейсов),
  `ui/screens/appearance/AppearanceScreenStaticTest` (6 кейсов),
  `ui/screens/more/MoreScreenTest` (4 кейса). Всего **9 unit + 16 androidTest
  кейсов**.
- `make format && make lint && make test && make build && make android-test` —
  все зелёные. Ручная проверка на эмуляторе `emulator-5554`: тема
  переключается, статус-бар перекрашивается, persistence работает,
  per-app locale ru переводит все строки, кнопка центрирована.
