> **Замечание по порядку:** этот change задокументирован post-factum — все пункты
> `[x]` отражают фактическое состояние кода. §3 и §7 — пост-фактум-изменения поверх
> предыдущих пунктов (отмена VM в §3, пере-инлайн `NavigationBar` в §7 поверх §5.2).
> Линейный forward-plan порядок не строго соблюдается — это намеренно, см. коммиты
> `a96a7f2` (ponytail-ревью).

## 1. Зависимости и строки

- [x] 1.1 В `gradle/libs.versions.toml` добавить `navigationCompose = "2.9.8"`, `junitJupiter = "6.1.3"`, `junitPlatform = "6.1.3"`; библиотеки `androidx-navigation-compose`, `junit-jupiter`, `junit-platform-launcher` и `androidx-compose-material-icons-core` (без `version.ref`).
- [x] 1.2 В `app/build.gradle.kts` добавить `implementation(libs.androidx.navigation.compose)`, `implementation(libs.androidx.compose.material.icons.core)`, `testImplementation(libs.junit.jupiter)`, `testRuntimeOnly(libs.junit.platform.launcher)`.
- [x] 1.3 В `app/build.gradle.kts` добавить `tasks.withType<Test> { useJUnitPlatform(); failOnNoDiscoveredTests = false }` (последнее — потому что test source set пуст).
- [x] 1.4 В `app/src/main/res/values/strings.xml` (ru) добавить `workouts_title`, `more_title`, `add_workout`.
- [x] 1.5 В `app/src/main/res/values-en/strings.xml` (en) добавить те же ключи на английском.
- [x] 1.6 `make format && make lint` — без ошибок.

## 2. Sealed Screen

- [x] 2.1 Создать `app/src/main/java/com/myworkouts/navigation/Screen.kt` с `sealed class Screen(route, icon, titleResId)` (все три non-null) + `object Main`, `object More` + `val AllTabs = listOf(Main, More)`.
- [x] 2.2 `material-icons-core` уже подключён (п. 1.2) — `Icons.Filled.Home` и `Icons.Filled.Menu` доступны.

## 3. ~~ViewModel RootScreen~~ — отменено

- [x] 3.1 `RootScreenViewModel.kt` и `RootScreenViewModelTest.kt` не создаются (решение принято после ponytail-ревью: `currentBackStackEntryAsState()` достаточно).
- [x] 3.2 JUnit 5 инфраструктура (п. 1.1–1.3) оставлена для будущих тестов доменных VM.

## 4. Экраны (composable destinations)

- [x] 4.1 `app/src/main/java/com/myworkouts/ui/screens/main/MainScreen.kt` — `@Composable fun MainScreen(modifier)`, `Scaffold(modifier, contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal), topBar = { TopAppBar(stringResource(R.string.workouts_title)) }, floatingActionButton = { FAB(Icons.Filled.Add, R.string.add_workout) })`. Тело пустое.
- [x] 4.2 `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt` — `@Composable fun MoreScreen(modifier)`, `Scaffold(modifier, contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal), topBar = { TopAppBar(stringResource(R.string.more_title)) })`. Тело пустое.

## 5. RootScreen с навигацией

- [x] 5.1 `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt` — `rememberNavController()` + `currentBackStackEntryAsState()`.
- [x] 5.2 `Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { NavigationBar { Screen.AllTabs.forEach { ... } } })` с inline `NavigationBarItem` (selected = currentRoute == screen.route, onClick с `popUpTo + launchSingleTop + restoreState`).
- [x] 5.3 `NavHost(startDestination = Screen.Main.route)`: `composable("main") { MainScreen() }`, `composable("more") { MoreScreen() }`.
- [x] 5.4 Комментарии-обоснования у каждого из трёх `Scaffold` про insets-контракт (двойной учёт = FAB едет вверх).

## 6. Подключение в MainActivity

- [x] 6.1 `app/src/main/java/com/myworkouts/MainActivity.kt` — удалить `Greeting`/`GreetingPreview`, `setContent { JetpackMyWorkoutsTheme { RootScreen() } }`.
- [x] 6.2 `make build` — без ошибок.

## 7. Ponytail-рефакторинг

- [x] 7.1 Удалить `RootScreenViewModel.kt` и `RootScreenViewModelTest.kt`.
- [x] 7.2 Убрать `lifecycle-viewmodel-compose` из `app/build.gradle.kts` и `gradle/libs.versions.toml`.
- [x] 7.3 `MainScreen.kt` — убрать `Box+Text` плейсхолдер тела, `@Preview`, параметр `onFabClick` (FAB теперь placeholder inline).
- [x] 7.4 `MoreScreen.kt` — убрать `Box+Text` плейсхолдер тела, `@Preview`.
- [x] 7.5 `RootScreen.kt` — удалить `NavigationBarContent` wrapper (single-caller = оверкилл; inline-блок уже в §5.2). VM-параметр не нужен (см. §3).
- [x] 7.6 `docs/navigation-foundation.md` — синхронизировать (Этап 3 отменён, Этапы 4/5 переписаны, Logcat-проверка снята).

## 8. Insets-контракт фикс

- [x] 8.1 `RootScreen.kt` — добавить `import androidx.compose.foundation.layout.WindowInsets`, `contentWindowInsets = WindowInsets(0, 0, 0, 0)` в outer Scaffold.
- [x] 8.2 `MainScreen.kt` — добавить `import WindowInsets`/`WindowInsetsSides`/`only`/`safeDrawing`, `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)` в inner Scaffold.
- [x] 8.3 (тот же фикс применён к `MoreScreen.kt` после ревью openspec — см. §4.2).
- [x] 8.4 Рациональ insets-контракта см. `design.md` Decision 3 + комментарии в коде (`RootScreen.kt`, `MainScreen.kt`, `MoreScreen.kt`).
- [x] 8.5 `make format && make lint && make test && make build` — все зелёные.

## 9. Проверка

- [x] 9.1 Стартовый экран — «Тренировки» с FAB «+».
- [x] 9.2 Переключение вкладки «Ещё» меняет экран.
- [x] 9.3 Повторное нажатие активной вкладки не дублирует destination (`launchSingleTop` + `restoreState` работают).
- [x] 9.4 `make format && make lint && make test && make build` — все зелёные.
- [ ] 9.5 Контент экрана не уходит под `NavigationBar` (требует визуальной проверки на устройстве).
- [ ] 9.6 ru/en локализация (требует запуска с явной сменой локали).
- [ ] 9.7 FAB по оси Y на одном уровне с эталоном JetpackDays (требует визуального сравнения скриншотов).
