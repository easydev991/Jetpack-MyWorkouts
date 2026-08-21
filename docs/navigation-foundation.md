# Навигация: фундамент (две вкладки + FAB)

> **Статус (август 2026):** реализация завершена и отрефакторена по результатам
> ponytail-ревью (убраны `RootScreenViewModel`, `Log.d`, `NavigationBarContent`-обёртка,
> `Box+Text` плейсхолдеры и `@Preview`). Коммиты на `main`:
> `97743b3` → `f8f7f1f` (foundation) + `bd2a77f` (обновление JUnit 6.1.3)
> + `a96a7f2` (ponytail-рефакторинг) + `dac396c` (FAB insets как в JetpackDays).

Фундамент навигации по образцу `JetpackDays`: `Scaffold` с `NavigationBar` (две
вкладки — «Тренировки» и «Ещё») и `NavHost` с двумя destinations. На экране
«Тренировки» — `FloatingActionButton` с `Icons.Filled.Add` (placeholder, действие
появится с фичей «добавить тренировку»). Все строки локализованы (ru/en).

Стек и библиотеки — как в `JetpackDays`:
`androidx.navigation:navigation-compose 2.9.8`,
`androidx.lifecycle:lifecycle-viewmodel-compose 2.11.0`,
`org.junit.jupiter:junit-jupiter 5.11.0` + `junit-platform-launcher` (unit-тесты на JUnit 5).
Иконки — только из `material-icons-core` (добавляется явно, в `composeBom` его нет):
`Icons.Filled.Home`, `Icons.Filled.Menu`, `Icons.Filled.Add`. `material-icons-extended`
не подключаем — расширенный набор ради двух иконок не нужен.

> **Отход от `JetpackDays` по иконкам:** план выбирает `Icons.Filled.Home` и
> `Icons.Filled.Menu`; в `JetpackDays/Screen.kt:19,25` стоят
> `Icons.AutoMirrored.Filled.List` и `Icons.Filled.MoreVert`. Это собственный
> выбор для тематики тренировок, а не копирование образца.

---

## Цели

- Завести две вкладки `NavigationBar` с локализованными подписями.
- `RootScreen` с `NavHost` на два пункта: `main` (стартовый) и `more`.
- `MainScreen` — `Scaffold` с `TopAppBar` и `FloatingActionButton` (placeholder).
- `MoreScreen` — `Scaffold` с `TopAppBar`, пустое тело.
- Подсветка активной вкладки — `currentBackStackEntryAsState()` без отдельного `ViewModel`.
- Локализация: «Тренировки» / «Ещё», плюс описание FAB.
- Зарегистрировать `RootScreen` как корневой composable в `MainActivity`.

---

## Этап 1. Зависимости и строки

- [x] `gradle/libs.versions.toml`
  - в `[versions]`:
    - `navigationCompose = "2.9.8"`
    - `lifecycleViewmodelCompose = "2.11.0"`
    - `junitJupiter = "5.11.0"`
    - `junitPlatform = "1.11.0"`  // синхронно с `junitJupiter`
  - в `[libraries]`:
    - `androidx-navigation-compose`
    - `androidx-lifecycle-viewmodel-compose`
    - `junit-jupiter = { group = "org.junit.jupiter", name = "junit-jupiter", version.ref = "junitJupiter" }`
    - `junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher", version.ref = "junitPlatform" }`
  - версии Compose BOM / Kotlin / AGP / detekt / ktlint — оставить как есть.
  - добавить `androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }`
    в `[libraries]` (без `version.ref` — версия приходит из `composeBom`). `material-icons-core`
    **не входит** в `composeBom`, его нужно объявить явно.
  - **не добавлять** `material-icons-extended` и версию `material`: расширенный набор ради
    двух иконок не нужен.
- [x] `app/build.gradle.kts`
  - в `dependencies`:
    - `implementation(libs.androidx.navigation.compose)`
    - `implementation(libs.androidx.lifecycle.viewmodel.compose)`
    - `implementation(libs.androidx.compose.material.icons.core)`
    - `testImplementation(libs.junit.jupiter)`
    - `testRuntimeOnly(libs.junit.platform.launcher)`
  - добавить блок `tasks.withType<Test> { useJUnitPlatform() }` (по образцу
    `JetpackDays/app/build.gradle.kts:148–150`), иначе Gradle не подхватит
    JUnit 5 тесты.
- [x] `app/src/main/res/values/strings.xml` (ru):
  - `<string name="workouts_title">Тренировки</string>` (заголовок вкладки и TopBar)
  - `<string name="more_title">Ещё</string>` (заголовок вкладки и TopBar)
  - `<string name="add_workout">Добавить тренировку</string>` (contentDescription FAB)
- [x] `app/src/main/res/values-en/strings.xml` (en):
  - `<string name="workouts_title">Workouts</string>`
  - `<string name="more_title">More</string>`
  - `<string name="add_workout">Add workout</string>`
  - Намеренно одна строка на вкладку — используется и в `NavigationBarItem.label`,
    и в `TopAppBar.title`. Дублировать `tab_*` / `screen_*` не нужно.
- [x] Проверка: `make format && make lint` без ошибок.

## Этап 2. Sealed `Screen` (навигационные маршруты)

- [x] `app/src/main/java/com/myworkouts/navigation/Screen.kt`
  - `sealed class Screen(val route: String, val icon: ImageVector, val titleResId: Int)` —
    по образцу `JetpackDays` (`Screen.kt:12–15`): `val` сразу в сигнатуре,
    иначе `screen.route`/`screen.icon`/`screen.titleResId` в `NavigationBarContent`
    (см. Этап 5) не компилируются.
  - **Отход от `JetpackDays`:** `icon` и `titleResId` здесь **non-null**.
    В `JetpackDays/Screen.kt:14–15` они `ImageVector?` и `Int?` (nullable)
    — там есть экраны без иконок (`ThemeIcon`, `ItemDetail`, …).
    Для двух вкладок MyWorkouts nullable избыточен, и это убирает `!!` в
    `NavigationBarContent` (см. Этап 5). Возвращать к nullable — только если
    появятся destinations без иконок (отдельные экраны вне табов).
  - `object Main : Screen(route = "main", icon = Icons.Filled.Home, titleResId = R.string.workouts_title)`
  - `object More : Screen(route = "more", icon = Icons.Filled.Menu, titleResId = R.string.more_title)`
  - Иконки — только из `core` (`Icons.Filled.Home`, `Icons.Filled.Menu`).
  - `val AllTabs = listOf(Main, More)` — **единственный источник истины** для
    порядка вкладок. Используется в `RootScreen` (`NavigationBarContent`) и
    не дублируется ни в `RootScreenViewModel`, ни в `RootScreen.items`.
    Ponytail: один список на весь модуль, не три.

## Этап 3. ~~ViewModel корневого экрана~~ — отменён

> После ponytail-ревью решено не вводить `RootScreenViewModel`:
> `currentBackStackEntryAsState()` из Jetpack Navigation уже отдаёт текущий
> route, и для двух статических вкладок `MutableStateFlow<Screen>` — оверкил.
> Сохранение состояния между rotation/process death обеспечивает
> `popUpTo(startDestination) { saveState = true }` + `restoreState = true`
> в `onClick` `NavigationBarItem` (см. Этап 5).

Файлы, которые планировались здесь, удалены:
- `app/src/main/java/com/myworkouts/ui/viewmodel/RootScreenViewModel.kt`
- `app/src/test/java/com/myworkouts/ui/viewmodel/RootScreenViewModelTest.kt`

JUnit 5 инфраструктура (`junit-jupiter`, `junit-platform-launcher`,
`useJUnitPlatform()`) оставлена — для будущих unit-тестов на доменные
`ViewModel`.

## Этап 4. Экраны (composable destinations)

> Вёрстка без поведенческой логики — TDD не применяется, только прямые шаги.
> `TopAppBar` помечен `@ExperimentalMaterial3Api` — нужен opt-in.
> Тела `Scaffold` пустые до появления реальных списков; `@Preview` не добавляем
> — превью плейсхолдеров без визуального контента мёртвый код.

- [x] `app/src/main/java/com/myworkouts/ui/screens/main/MainScreen.kt`
  - `@OptIn(ExperimentalMaterial3Api::class)`
  - `@Composable fun MainScreen(modifier: Modifier = Modifier)`.
  - `Scaffold(modifier, contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal), topBar = { TopAppBar(...) }, floatingActionButton = { FAB(...) })` —
    только горизонтальные insets: вертикальный `safeDrawing` уже учтён
    `NavigationBar` в `RootScreen`, иначе FAB уедет вверх на ~2× высоту
    system nav bar (см. коммит `dac396c`).
  - `floatingActionButton = { FloatingActionButton(onClick = { /* ponytail: FAB placeholder */ }) { Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_workout)) } }`.
  - Тело `Scaffold` пустое — список тренировок добавится отдельной фичей.
- [x] `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt`
  - `@OptIn(ExperimentalMaterial3Api::class)`
  - `@Composable fun MoreScreen(modifier: Modifier = Modifier)`.
  - `Scaffold` с `topBar = { TopAppBar(title = { Text(stringResource(R.string.more_title)) }) }`.
  - Тело `Scaffold` пустое.

## Этап 5. Корневой экран с навигацией

- [x] `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt`
  - `@Composable fun RootScreen(modifier: Modifier = Modifier)` — без `viewModel`.
  - `val navController = rememberNavController()`.
  - `val navBackStackEntry by navController.currentBackStackEntryAsState()` —
    нативный источник «текущей вкладки» из Jetpack Navigation. Никаких
    `MutableStateFlow`, никакого `ViewModel`.
  - `val currentRoute = navBackStackEntry?.destination?.route`.
  - `Scaffold(modifier, contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { NavigationBar { ... } }) { paddingValues -> NavHost(...) }` —
    `NavigationBar` в Compose сама учитывает system nav bar inset через свой
    параметр `windowInsets` (default `NavigationBarDefaults.windowInsets`), поэтому
    outer Scaffold обнуляет свои insets; иначе внутренний `MainScreen` вычтет
    их повторно и FAB уедет вверх (см. коммит `dac396c`).
  - `NavigationBar` инлайнен в `bottomBar` (после ponytail-ревью отдельный
    `NavigationBarContent` wrapper с одним вызовом признан оверкилом).
    Список вкладок берётся из `Screen.AllTabs`; `paddingValues` обязательно
    пробрасываются, иначе контент уйдёт под `NavigationBar`.
  - Для каждой `screen` рисуем
    `NavigationBarItem(selected = currentRoute == screen.route, onClick = { navController.navigate(screen.route) { popUpTo(navController.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }, icon = { Icon(screen.icon, contentDescription = stringResource(screen.titleResId)) }, label = { Text(stringResource(screen.titleResId)) })`.
    `popUpTo(...) + restoreState = true` сохраняет состояние вкладки после
    rotation/process death — то, ради чего раньше держали `ViewModel`.
  - `NavHost`:
    - `composable(Screen.Main.route) { MainScreen() }`
    - `composable(Screen.More.route) { MoreScreen() }`
  - `testTag` сейчас **не добавляем** — мёртвые модификаторы без UI-тестов.
    Вернуть, когда стартует задача «UI-тесты навигации».

## Этап 6. Подключение в `MainActivity`

- [x] `app/src/main/java/com/myworkouts/MainActivity.kt`
  - удалить `Greeting` / `GreetingPreview`.
  - в `setContent { JetpackMyWorkoutsTheme { RootScreen() } }`.
  - `RootScreen()` — без параметров (после удаления `ViewModel`).
- [x] Сборка: `make build` — без ошибок.
- [x] `make test` — `testDebugUnitTest` отрабатывает (после удаления
  `RootScreenViewModelTest` в `tasks.withType<Test>` добавлено
  `failOnNoDiscoveredTests = false`, иначе Gradle валит сборку при пустом
  test source set).

---

## Проверка результата

- [ ] Запустить приложение, убедиться:
  - стартовый экран — «Тренировки» с FAB «+»;
  - переключение вкладки «Ещё» меняет экран;
  - контент экрана не уходит под `NavigationBar` (визуально сверху-снизу
    есть отступы, `Modifier.padding(paddingValues)` отрабатывает);
  - повторное нажатие активной вкладки не дублирует destination
    (`launchSingleTop = true` + `restoreState = true`);
  - заголовки вкладок и описания FAB подгружаются на ru/en
    (через системные настройки / `adb shell setprop persist.sys.locale`);
  - FAB по оси Y находится на одном уровне с эталоном `JetpackDays`
    (визуально 16dp над нижним краем NavigationBar, **не** поверх неё).
- [x] `make format && make lint && make test && make build` — зелёные.

> Ручной пункт («Запустить приложение…») требует устройства и остаётся на
> совести исполнителя после слияния плана. Проверка `Logcat` снята — FAB
> теперь placeholder без `Log.d`.

---

## Что отложено (понадобится дальше, не в этом плане)

- Реальные `ViewModel` доменных экранов и проброс зависимостей через
  `AppModule` (DI) — добавляется по мере появления фич.
- Аналитика (`AnalyticsService`) — перенесём из `JetpackDays`, позже.
- Логирование через `util/Logger` — когда появится общий логгер.
- Условное скрытие `bottomBar` (как `shouldShowNavigationBar` в
  `JetpackDays`) — нужно, когда добавятся вложенные экраны, не участвующие
  в табах.
- Compose-тесты экрана / BottomBar / FAB + `testTag` — отдельная задача
  «UI-тесты навигации», повесим в `androidTest/`.
