# Реализация экрана «Оформление приложения»

## Цель

1. Добавить кнопку «Оформление приложения» на `MoreScreen` (как в JetpackDays).
2. Создать новый экран «Оформление приложения», повторяющий `ThemeIconScreen` из
   JetpackDays, **за исключением секции смены иконки приложения**. Остальная
   логика (выбор темы, переключатель динамических цветов) и тексты —
   идентичны JetpackDays.
3. Сделать выбор темы и переключателя динамических цветов
   персистентным (DataStore) и применить его на уровне всего приложения.
4. Покрыть код unit- и UI-тестами по образцу JetpackDays.

## Источник истины

JetpackDays (на которые опираемся, все пути относительно
`/Users/Oleg991/Documents/GitHub/JetpackDays/`):

UI и навигация:

- [`ui/screens/themeicon/ThemeIconScreen.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/screens/themeicon/ThemeIconScreen.kt) — основной образец для `AppearanceScreen` (за вычетом `IconSection`).
- [`ui/screens/themeicon/IconPreviewItem.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/screens/themeicon/IconPreviewItem.kt) — **не переносим** (секция иконки не нужна).
- [`ui/screens/more/MoreScreen.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/screens/more/MoreScreen.kt) — образец для кнопки «Оформление приложения» + структуры `SettingsButtons`/`MoreButton`/`AppVersionText`.
- [`ui/screens/common/RadioButton.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/screens/common/RadioButton.kt) — образец для `RadioButton` (имя **не** сохраняем, см. §4.1).
- [`ui/theme/DynamicColors.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/theme/DynamicColors.kt) — копируем один-в-один.
- [`MainActivity.kt`](../JetpackDays/app/src/main/java/com/dayscounter/MainActivity.kt) — образец интеграции `MainActivityViewModel` + `JetpackDaysTheme`.
- [`ui/theme/Theme.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/theme/Theme.kt) — образец для обновлённого `JetpackMyWorkoutsTheme` с параметром `appTheme`.

ViewModel и состояние:

- [`ui/viewmodel/ThemeIconViewModel.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/viewmodel/ThemeIconViewModel.kt) — образец для `AppearanceViewModel` (без `IconManager`/`AnalyticsService`/`Logger`).
- [`ui/viewmodel/MainActivityViewModel.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/viewmodel/MainActivityViewModel.kt) — копируем один-в-один (он уже работает только с `theme` + `useDynamicColors`).
- [`ui/state/ThemeIconUiState.kt`](../JetpackDays/app/src/main/java/com/dayscounter/ui/state/ThemeIconUiState.kt) — образец для `AppearanceUiState` (без поля `icon`).

Domain и Data:

- [`domain/model/AppTheme.kt`](../JetpackDays/app/src/main/java/com/dayscounter/domain/model/AppTheme.kt) — копируем один-в-один.
- [`data/preferences/AppSettingsDataStore.kt`](../JetpackDays/app/src/main/java/com/dayscounter/data/preferences/AppSettingsDataStore.kt) — образец для `AppSettingsDataStore` (используем только `theme` + `useDynamicColors`, остальные ключи — YAGNI).

Тесты:

- [`test/ui/viewmodel/ThemeIconViewModelTest.kt`](../JetpackDays/app/src/test/java/com/dayscounter/ui/viewmodel/ThemeIconViewModelTest.kt) — образец для `AppearanceViewModelTest` (без тестов на иконку).
- [`androidTest/ui/screens/themeicon/ThemeIconScreenTest.kt`](../JetpackDays/app/src/androidTest/java/com/dayscounter/ui/screens/themeicon/ThemeIconScreenTest.kt) — образец для `AppearanceScreenTest` (без тестов на `IconSection`).
- [`androidTest/ui/screens/more/MoreScreenTest.kt`](../JetpackDays/app/src/androidTest/java/com/dayscounter/ui/screens/more/MoreScreenTest.kt) — образец для `MoreScreenTest`.

Ресурсы (тексты побуквенно совпадают):

- [`res/values/strings.xml`](../JetpackDays/app/src/main/res/values/strings.xml) — английские строки (JetpackDays ru=en).
- [`res/values-ru/strings.xml`](../JetpackDays/app/src/main/res/values-ru/strings.xml) — русские строки (JetpackDays primary=ru).

## Что НЕ переносим

- [`domain/model/AppIcon.kt`](../JetpackDays/app/src/main/java/com/dayscounter/domain/model/AppIcon.kt) и весь связанный код (нет секции иконки).
- `IconManager` / `MainActivityIcon2..6` aliases в `AndroidManifest.xml`
  (нет смены иконки).
- Аналитика `AnalyticsService`/`NoopAnalyticsProvider` (в нашем проекте нет
  Firebase; AGENTS.md не требует).
- `Logger`/`AndroidLogger`/`NoOpLogger` — YAGNI: в нашей `AppearanceViewModel`
  логирование не нужно (как и в
  [`ThemeIconViewModel`](../JetpackDays/app/src/main/java/com/dayscounter/ui/viewmodel/ThemeIconViewModel.kt),
  логгер только пишет в `logger.d`, поведение не меняет). Если потребуется
  позже — добавим точечно.
- `MyWorkoutsApplication` — не нужен: `AppSettingsDataStore` создаётся через
  factory-функцию `createAppSettingsDataStore(context)` в `onCreate`
  (как в
  [`MainActivity.kt:52`](../JetpackDays/app/src/main/java/com/dayscounter/MainActivity.kt)).
- [`di/AppModule.kt`](../JetpackDays/app/src/main/java/com/dayscounter/di/AppModule.kt)
  — не нужен для текущей задачи: единственная зависимость —
  `AppSettingsDataStore`, и она создаётся через standalone factory.

## Конвенции проекта (напоминание)

- Kotlin + Compose + MVVM + Clean Architecture, manual DI.
- Никогда `!!` — `checkNotNull`, `?.let`, `?:`.
- Sealed для UI-состояний; `Result<T>` для use-cases.
- StateFlow + `SharingStarted.WhileSubscribed(5000)` в ViewModel.
- Локализация: `values/strings.xml` = русский (primary), `values-en/strings.xml` =
  английский.
- KDoc и логи на русском.
- Тесты: `app/src/test/` — JUnit 5 + MockK + kotlinx-coroutines-test;
  `app/src/androidTest/` — JUnit 4 + Compose Testing.
- Имена тестов — `snake_case`, без обратных кавычек.
- `// Given` / `// When` / `// Then` в каждом тесте.
- Перед коммитом: `make format && make lint && make test`.

## Зависимости от задачи

```
[AppTheme] → [AppSettingsDataStore] → [MainActivityViewModel, AppearanceViewModel]
                                        ↓
[DynamicColors + RadioButton] → [AppearanceScreen] → [Navigation] → [MainActivity]
```

Этапы 2–3 — фундамент. Этапы 4–6 — UI-инфраструктура. Этапы 7–9 — финальная
сборка экрана и навигации. Этапы 10–11 — тесты, покрытие, верификация.

---

## Этап 1: Подготовка зависимостей

- [x] Добавлены 4 версии (`datastore=1.2.1`, `lifecycleViewmodelCompose=2.11.0`,
  `coroutines=1.10.2`, `mockk=1.14.3`) + 6 библиотек в `gradle/libs.versions.toml`;
  4 `implementation` + 2 `testImplementation` в `app/build.gradle.kts`.

---

## Этап 2: Domain модель

- [x] `AppTheme` (enum `LIGHT`/`DARK`/`SYSTEM`) + `AppThemeTest` (1 тест
  фиксирует имена enum как контракт с `AppSettingsDataStore`).

---

## Этап 3: Data Layer — DataStore

- [x] `AppSettingsDataStore` (`appearance_prefs`, 2 ключа `theme` +
  `use_dynamic_colors`): suspend-сеттеры и Flow-геттеры с дефолтами
  (`SYSTEM` / `true`) и fallback на `IllegalArgumentException`. Factory
  `createAppSettingsDataStore(context)` оборачивает в `applicationContext`.
  `icon`/`sortOrder`/`colorTagFilter` НЕ перенесены (YAGNI).

---

## Этап 4: UI — дизайн-система

- [x] `RadioButton` (копия `DaysRadioButton`, **без** префикса `Days`; параметр
  `enabled: Boolean` вместо `onClickable`, `Modifier.selectable` сам обрабатывает
  отключение) и `DynamicColors` (копия 1-в-1).

---

## Этап 5: Theme — динамическое применение

- [x] `JetpackMyWorkoutsTheme(darkTheme, appTheme: AppTheme = SYSTEM,
  dynamicColor, content)`: `useDarkTheme` через `when(appTheme)`
  (LIGHT→false, DARK→true, SYSTEM→darkTheme). **Отличие от JetpackDays:**
  у нас `appTheme` non-null — DataStore создаётся до `setContent` (см. §9.4),
  гонки нет; в JetpackDays `appTheme: AppTheme?` nullable.

---

## Этап 6: ViewModel — состояние

### 6.1 `AppearanceUiState`

- [x] `data class AppearanceUiState(theme: AppTheme = SYSTEM, useDynamicColors: Boolean = true)` —
 plain snapshot (не sealed — для экрана настроек нет async-сценариев).

### 6.2 `AppearanceViewModelTest`

- [x] JUnit 5 + MockK + `StandardTestDispatcher` +
  `@OptIn(ExperimentalCoroutinesApi::class)`. `setUp`/`tearDown`
  (`mockk(relaxed=true)`, `Dispatchers.setMain/resetMain`). **8 кейсов:**
  `update_theme` ×3 (`@ParameterizedTest` + `@EnumSource`),
  `update_dynamic_colors` ×2, `ui_state` ×3 (`@MethodSource`).

### 6.3 `AppearanceViewModel`

- [x] `uiState: StateFlow<AppearanceUiState>` через
  `combine(dataStore.theme, dataStore.useDynamicColors).stateIn(viewModelScope,
  WhileSubscribed(5000L), AppearanceUiState())`. `updateTheme`/`updateDynamicColors`
  через `viewModelScope.launch { dataStore.set*(...) }`. `companion factory(dataStore)`
  через `viewModelFactory { initializer { AppearanceViewModel(dataStore) } }`.
  Без `iconManager`/`analyticsService`/`logger`/`TAG`/`STATE_TIMEOUT_MS` — YAGNI.

### 6.4 `MainActivityViewModel` + тест

- [x] **Критичный путь:** `theme: StateFlow<AppTheme>` и
  `useDynamicColors: StateFlow<Boolean>` через `dataStore.{...}.stateIn(...)`
  с `WhileSubscribed(STATE_TIMEOUT_MS=5000L)`. `companion factory(dataStore)`.
  Дублирование `5000L` с `AppearanceViewModel` (там литерал инлайнен)
  допустимо — вынос в `AppConstants` для двух мест over-engineering.
- [x] `MainActivityViewModelTest` (3 теста): `theme_should_emit_value_from_data_store`,
  `use_dynamic_colors_should_emit_value_from_data_store`,
  `factory_should_create_viewmodel_with_data_store` (end-to-end через
  `backgroundScope`+`advanceUntilIdle`).

### 6.5 Зафиксированные отклонения от плана

- [x] **`.first()` → `.value` + `backgroundScope`:** `StateFlow.first()` в
  `runTest` с `WhileSubscribed(5000)` + `StandardTestDispatcher` возвращает
  `initialValue`, не дожидаясь upstream. **Решение:** `backgroundScope.launch { collect { } }`
  - `advanceUntilIdle()` + `.value`. Применено в обоих тестовых классах.
- [x] **1-арг `create(Class)` → 2-арг `create(Class, CreationExtras.Empty)`:**
  `InitializerViewModelFactory` в lifecycle 2.11.0 переопределяет только
  `create(Class, CreationExtras)`; 1-арг бросает `UnsupportedOperationException`.
  Требует `import androidx.lifecycle.viewmodel.CreationExtras`.
- [x] **`coEvery` → `every`** для property-getters Flow (`val theme`/
  `val useDynamicColors`); `coEvery`/`coAnswers` — только для suspend-сеттеров.

### 6.6 Верификация

- [x] `make test`: 12/12 зелёные (AppThemeTest 1 + AppearanceViewModelTest 8 +
  MainActivityViewModelTest 3).

---

## Этап 7: Локализация

- [x] Конвенция путей (обратная JetpackDays): `values/strings.xml` = **ru**
  (primary), `values-en/strings.xml` = **en**. **9 ключей × 2 локали** —
  тексты 1-в-1 из JetpackDays. Ключ `appearance_title` используется и для
  кнопки в `MoreScreen`, и для заголовка экрана. `make build` — ресурсы
  компилируются.
- [ ] Превью/устройство: ru → «Оформление приложения», en → `Appearance` — перенесено в §11 (требует интеграции в `RootScreen`/`MainActivity`, см. Этап 9).

---

## Этап 8: UI — экран «Оформление приложения»

### 8.1 `AppearanceScreenTest` (parameterized, JUnit 4)

- [x] **Параметризованные тесты** (×3 `AppTheme`):
  `appearance_screen_theme_radio_button_selected` (3 radio отображены,
  целевая выбрана, **ровно 1 selected** через `assertCountEquals(1)`) и
  `appearance_screen_clicks_theme_radio_button_calls_on_theme_change` (клик
  по целевой → callback). **Фикс после прогона на эмуляторе** (1686746):
  Selected semantic живёт на `Modifier.selectable` родительского Row, а не
  на Text → matcher `hasAnyChild(hasText(label)).and(isSelected)` через
  `useUnmergedTree = true`. Click-тест стартует с `initialTheme ≠ theme`
  (selected radio отключён `enabled = theme != AppTheme.X`).
- [x] Статические тесты (DynamicColors available/unavailable) вынесены в
  §8.3 — иначе ×3-буст на `AppTheme.entries`.

### 8.2 `AppearanceScreen`

Файл: `app/src/main/java/com/myworkouts/ui/screens/appearance/AppearanceScreen.kt`.

- [x] Копия JetpackDays `ThemeIconScreen.kt` **за вычетом `IconSection`** и всех
  ссылок на `icon`/`updateIcon`/`onIconChange`/`AppIcon`/`IconPreviewItem`/`IconGrid`.
- [x] `internal data class AppearanceScreenState(theme, useDynamicColors,
  onThemeChange, onDynamicColorsChange, isDynamicColorsAvailable, onBackClick)`.
  `isDynamicColorsAvailable` — вычисляется снаружи через
  `DynamicColors.isDynamicColorAvailable()` и прокидывается в State для
  детерминизма теста (без чтения `Build.VERSION.SDK_INT`).
- [x] `internal @Composable fun AppearanceScreenContent(state)`: Scaffold+
  TopAppBar+Column (verticalScroll) с ThemeSection + (если
  `isDynamicColorsAvailable`) DynamicColorsSection. `if` оборачивает Section
  целиком, чтобы не оставлять осиротевший divider после скрытой секции.
- [x] Внешний `AppearanceScreen(viewModel, onBackClick)`: мапит `uiState +
  isDynamicColorsAvailable` → `AppearanceScreenState` → `Content`.
  `DynamicColors.isDynamicColorAvailable()` — `@Composable`, вызывается
  только здесь (не в `companion object`/init-блоке).

### 8.3 `AppearanceScreenStaticTest`

- [x] **6 статических тестов** (`@RunWith(AndroidJUnit4::class)`, чтобы не было
  ×3-буста): `app_bar_with_back_button`, `app_theme_section`,
  `dynamic_colors_section_when_available`,
  `hides_dynamic_colors_section_when_not_available` (через `assertDoesNotExist()`,
  а не `assertIsNotDisplayed()` — детерминированно независимо от SDK
  эмулятора) и 2 теста Switch (`with_use_dynamic_colors_true_changes_to_false`
  / `…_false_changes_to_true`) через `onNode(isToggleable()).performClick()`.

---

## Этап 9: Навигация

### 9.1 `Screen.kt`

- [x] `Screen.Appearance` добавлен без `icon`/`titleResId` (не входит в
  нижнюю навигацию). `icon: ImageVector?` и `titleResId: Int?` сделаны
  nullable (как в JetpackDays) — это **breaking change контракта**.
  Альтернатива — sealed `TabScreen`/`NonTabScreen` — over-engineering для
  одного не-tab экрана. **Trigger-watch:** при добавлении второго не-tab
  экрана — переход на sealed до интеграции. `Main`/`More` сохранили явные
  icon+titleResId (не null); `AllTabs = listOf(Main, More)`.

### 9.2 `MoreScreen` — кнопка

- [x] `MoreScreen(navController: NavHostController, modifier)` — обязательный
  `NavController` (убран nullable: единственный вызов — в `RootScreen`,
  превью берёт `rememberNavController()` локально). Сохранены Scaffold+
  TopAppBar+inline-версия (`AppVersionText()` НЕ извлекается — YAGNI
  для одной строки). Добавлена `MoreButton` (FilledTonalButton с
  `dimensionResource(R.dimen.button_horizontal_padding)` = 16dp) —
  навигация на `Screen.Appearance.route` с `popUpTo(Screen.More.route)`.
  Новый dimen в `values/dimens.xml` (без `values-en/` — Android резолвит
  для всех локалей из дефолтного `values/`).

### 9.3 `RootScreen` — изменения

- [x] `RootScreen(dataStore: AppSettingsDataStore, modifier)` — принимает
  DataStore параметром (внутри НЕ пересоздаём — запрещено:
  `There are multiple DataStores active for the same file`).
  `composable(Screen.More.route) { MoreScreen(navController = navController) }`,
  `composable(Screen.Appearance.route) { viewModel(factory =
  AppearanceViewModel.factory(dataStore)) + popBackStack() }`.
  KDoc/комментарии на русском. **Атомарно с §9.4** (один коммит).

### 9.4 `MainActivity`

- [x] **До `setContent`, один раз:** `val dataStore =
  createAppSettingsDataStore(applicationContext)` (без `remember` — мы в
  Activity-методе).
- [x] **Внутри `setContent`:** `MainActivityViewModel.factory(dataStore)`,
  `theme`/`useDynamicColors` через `collectAsState`,
  `isDarkTheme = when (theme) { DARK→true; LIGHT→false; SYSTEM→isSystemInDarkTheme() }`
  (вычисляется здесь, в Activity-scope, а не в `Theme.kt` — дубль 3 строк
  дешевле, чем вводить `CompositionLocal`),
  `LaunchedEffect(isDarkTheme) { enableEdgeToEdge(statusBarStyle = if (isDarkTheme) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)) }`
  — без этого при `updateTheme(DARK)` `MaterialTheme` перекрасится, а
  статус-бар останется в стиле cold-start (визуальная регрессия при
  переключении темы в рантайме).
- [x] `JetpackMyWorkoutsTheme(appTheme = theme, dynamicColor = useDynamicColors)
  { RootScreen(dataStore = dataStore) }`.

### 9.5 Верификация

- [x] `make build` — компиляция. `make test` — все unit-тесты зелёные.

---

## Этап 10: Тесты `MoreScreen` (androidTest)

- [x] `MoreScreenTest` (3 теста, `@RunWith(AndroidJUnit4::class)`):
  `more_screen_when_displayed_then_shows_appearance_button`,
  `more_screen_when_appearance_button_clicked_then_navigates_to_appearance_route`
  (через `lateinit var navController` + `waitForIdle()`,
  `navController.currentDestination?.route == Screen.Appearance.route`),
  `more_screen_when_displayed_then_shows_app_version`
  (`context.getString(R.string.app_version, BuildConfig.VERSION_NAME)`).
  **Test-harness `MoreScreenWithNavHost`** оборачивает `MoreScreen` в
  `NavHost(startDestination = Screen.More.route) { composable(More) { MoreScreen(...) };
  composable(Appearance) {} }` — иначе `navigate()` падает с
  `IllegalArgumentException: Navigation graph has not been set`.

---

## Этап 11: Финальная верификация

- [x] `make format && make lint && make test` — все три зелёные.
- [x] `make android-test` — `AppearanceScreenTest` + `MoreScreenTest` зелёные
  (17/17 на эмуляторе `emulator-5554`).
- [x] Ручная проверка на эмуляторе:
  - «Ещё» → кнопка «Оформление приложения» видна и **горизонтально
    отцентрирована** (фикс коммита `085a68a`: `Row(fillMaxWidth,
    Arrangement.Center)` вокруг `MoreButton`).
  - Клик → открывается Appearance с секцией «Тема приложения» (3 radio)
    и «Динамические цвета» (Switch); секции «Иконка приложения» нет.
  - Переключение темы на «Тёмная» → весь UI перекрашивается (включая
    статус-бар); после `am force-stop` тема сохраняется.
  - Per-app locale (`cmd locale set-app-locales ... --locales ru`) → все
    строки переводятся на русский (включая кнопку «Оформление
    приложения»).
  - Регресс-тест центрирования в `MoreScreenTest` — `getBoundsInRoot()`
    возвращает `DpRect`, `.value` → Float; `leftMargin == rightMargin ±1px`.

---

## Критерии завершения

- [x] Unit-покрытие (Этапы 2/6): `AppTheme` (1), `AppearanceViewModel` (8 кейсов), `MainActivityViewModel` (3 — критичный путь к MaterialTheme). Всего **12/12 зелёные**.
- [x] Локализация ru/en идентична JetpackDays (Этап 7).
- [x] Никаких секций/моделей/кода для иконки приложения в проекте не появилось.
- [x] `AppearanceScreen` androidTest — Этап 8.
- [x] `MoreScreen` androidTest с кнопкой «Оформление приложения» — Этап 10.
- [x] Экран достижим из `MoreScreen` — Этап 9.
- [x] Тема сохраняется между перезапусками и применяется ко всему UI — код сохранения в `AppSettingsDataStore` (Этап 3), интеграция в `MainActivity` — Этап 9.4, ручная верификация — Этап 11.

## Сводка реализации по этапам

| Этап | Состояф | Коммит(ы) |
|------|---------|-----------|
| 1. Зависимости | ✅ | `c2ca5ed` |
| 2. AppTheme + test | ✅ | `d72f70d` |
| 3. AppSettingsDataStore | ✅ | `c273490` |
| 4. RadioButton + DynamicColors | ✅ | `e4be290` + `f129b5d` (auto-fix) |
| 5. Theme.kt | ✅ | `bb5269e` |
| 6. ViewModel + тесты | ✅ | `4ac494c` |
| 7. Локализация ru/en | ✅ | `e228c7c` |
| 8. AppearanceScreen + тесты | ✅ | `83b85df` + `1686746` (фикс тестов после прогона на эмуляторе) |
| 9. Навигация + MainActivity | ✅ | `7a9a2e3` |
| 10. MoreScreen androidTest | ✅ | `8c039fc` |
| 11. Финальная верификация | ✅ | `085a68a` (центрирование кнопки + регресс-тест) |
