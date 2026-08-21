## Context

Приложение создано из шаблона Android Studio. `MainActivity` запускает `setContent { JetpackMyWorkoutsTheme { Greeting(...) } }` с шаблонным `Greeting`/`GreetingPreview`. Никакой навигации, никаких экранов — только заглушка. До первой реальной возможности (список тренировок) нужен минимальный навигационный каркас.

Образец — `JetpackDays` (локальный репо рядом). В нём тот же подход: `Scaffold` с `NavigationBar` + `NavHost` на 2+ destinations, активная вкладка подсвечивается через `currentBackStackEntryAsState()`, FAB на главном экране. Отличия от `JetpackDays` учтены в proposal (иконки `Home`/`Menu` вместо `List`/`MoreVert`, non-null `icon`/`titleResId` в `Screen`).

AGENTS.md требует offline-only (без сетевых зависимостей), manual DI (без Hilt), StateFlow + `WhileSubscribed(5000)` для VM, sealed для UI-состояний, без `!!`.

## Goals / Non-Goals

**Goals:**
- Минимальный рабочий навигационный каркас: 2 вкладки, `NavHost`, активная вкладка.
- Единый список вкладок `Screen.AllTabs` как источник истины.
- Корректный insets-контракт outer/inner `Scaffold` (FAB на правильной высоте).
- Локализованные ru/en строки для заголовков и `contentDescription` FAB.
- `make format && make lint && make test && make build` зелёные.
- Визуальная и UX-проверка: вкладки переключаются, FAB нажимается (подтверждено вручную).

**Non-Goals:**
- Реальная логика добавления тренировки — FAB placeholder (`onClick = { /* placeholder */ }`).
- Список тренировок — отдельная фича, не в этом change.
- Реальные `ViewModel` доменных экранов — добавятся по мере появления фич.
- DI модуль (`AppModule`) — manual DI фабриками, не требуется для 2 статических экранов без зависимостей.
- `shouldShowNavigationBar` (условное скрытие `bottomBar`) — нужно, когда появятся вложенные экраны вне табов.
- Compose UI-тесты на навигацию — отдельная задача «UI-тесты навигации».
- Логирование через `util/Logger` — когда появится общий логгер.

## Decisions

### 1. Никакого `ViewModel` для `RootScreen`

**Решение:** использовать нативный `navController.currentBackStackEntryAsState()` для определения активной вкладки.

**Альтернативы:**
- `RootScreenViewModel` с `MutableStateFlow<Screen>` и `WhileSubscribed(5000)` (как в AGENTS.md).

**Почему отвергнуто:** для двух статических табов VM не даёт ничего — `currentBackStackEntryAsState()` уже отдаёт текущий route. Сохранение состояния между rotation/process death обеспечивают `popUpTo(startDestination) { saveState = true }` + `restoreState = true` в `onClick` `NavigationBarItem` — это нативная фича Navigation Compose, отдельный VM не нужен. Ponytail: меньше кода, меньше церемоний.

### 2. `Screen.AllTabs` как единственный источник истины порядка вкладок

**Решение:** список `val AllTabs = listOf(Main, More)` живёт в `Screen.kt`, используется в `RootScreen.NavigationBar`.

**Альтернативы:**
- Дублировать список в `RootScreen` и `RootScreenViewModel`.
- Хардкодить `NavigationBarItem` на каждый таб отдельно.

**Почему отвергнуто:** при добавлении 3-го таба дублирование = шанс забыть обновить одно из мест. Один список — добавляешь объект в `Screen` и одну строку в `AllTabs`. Порядок визуально читается из `AllTabs`.

### 3. Insets-контракт outer/inner Scaffold (как в JetpackDays)

**Решение:**
- `RootScreen.Scaffold` (outer): `contentWindowInsets = WindowInsets(0, 0, 0, 0)`.
- `MainScreen.Scaffold` (inner): `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)`.
- `MoreScreen.Scaffold` (inner): `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)` — симметрично `MainScreen`. Без этого при появлении контента (список, `LazyColumn`) тело уедет вверх на высоту system nav bar.

**Альтернатива:** все `Scaffold` используют default `safeDrawing` insets (наивно).

**Почему отвергнуто:** `Material3.NavigationBar` сама учитывает system nav bar inset через свой параметр `windowInsets` (default `NavigationBarDefaults.windowInsets`). Если outer `Scaffold` тоже учитывает `safeDrawing`, а inner `Scaffold` повторно учитывает для FAB — FAB получает двойной subtract и оказывается на ~2× высоты system nav bar выше, чем в `JetpackDays`. То же касается любого контента inner-Scaffold: тело `MainScreen`/`MoreScreen` через свой `paddingValues` повторно учтёт system nav bar и уедет вверх. Точное совпадение с образцом = правильный инсет-контракт. Ponytail: стандартный паттерн Compose, описан в комментариях у каждого из трёх `Scaffold`.

### 4. `material-icons-core` объявлен явно (без `version.ref`)

**Решение:** в `gradle/libs.versions.toml`:

```
androidx-compose-material-icons-core = { group = "androidx.compose.material", name = "material-icons-core" }
```

(без `version.ref` — версия приходит из `composeBom`).

**Альтернативы:**
- Не объявлять вообще — тогда `Icons.Filled.Add`/`Home`/`Menu` не компилируются.
- Объявить с фиксированной версией — расходится с `composeBom`.

**Почему отвергнуто:** `composeBom` содержит `material-icons-extended`, но НЕ содержит `material-icons-core` — это известная особенность `composeBom`. `material-icons-extended` ради трёх иконок (Home/Menu/Add) — оверкилл (размер APK ~3 MB).

### 5. JUnit 5 + `failOnNoDiscoveredTests = false`

**Решение:** подключить `junit-jupiter`/`junit-platform-launcher` и `useJUnitPlatform()`, плюс в `tasks.withType<Test>` добавить `failOnNoDiscoveredTests = false`.

**Альтернативы:**
- Не подключать JUnit 5 — нет инфраструктуры для будущих unit-тестов.
- Оставить `failOnNoDiscoveredTests = true` (по умолчанию) — `make test` падает, потому что после удаления `RootScreenViewModelTest` test source set пуст.

**Почему отвергнуто:** будущим фичам (доменные `ViewModel`) нужны unit-тесты. Пустой test source set при `useJUnitPlatform()` = guaranteed failure. `failOnNoDiscoveredTests = false` — стандартный workaround для проектов с ещё-не-написанными тестами.

### 6. `Screen.icon` и `Screen.titleResId` — non-null (отход от JetpackDays)

**Решение:** в `sealed class Screen(val route: String, val icon: ImageVector, val titleResId: Int)` оба параметра non-null.

**Альтернатива:** как в `JetpackDays` — `ImageVector?` и `Int?` (там есть destinations без иконок).

**Почему отвергнуто:** у `MyWorkouts` все destinations — табы с иконками. Nullable = шанс NPE в `NavigationBarItem` (где иконка обязательна). Non-null убирает `!!`/`?.let` в одном месте. Возвращать к nullable — только если появятся destinations вне табов (отдельные экраны).

### 7. `NavigationBar` инлайнен в `bottomBar` (без отдельного `NavigationBarContent` wrapper)

**Решение:** блок `NavigationBar { Screen.AllTabs.forEach { ... } }` живёт прямо в `Scaffold(bottomBar = { ... })`.

**Альтернатива:** отдельная `@Composable fun NavigationBarContent(currentRoute: String?, onTabSelected: (Screen) -> Unit)` для читабельности.

**Почему отвергнуто:** единственный вызов `NavigationBarContent` — в `RootScreen`. Wrapper с одним caller'ом = pure overhead. Inline читается так же понятно, как вызов функции, но без лишнего indirection. Ponytail: indirection ради indirection — антипаттерн.

### 8. `RootScreen()` без `viewModel`-параметра

**Решение:** сигнатура `RootScreen(modifier: Modifier = Modifier)`, никаких `viewModel: RootScreenViewModel = viewModel()`.

**Альтернатива:** `RootScreen(modifier, viewModel)` как в типичном MVVM-сценарии.

**Почему отвергнуто:** после решения №1 (без VM) параметр не нужен. `RootScreen(modifier)` — минимальный публичный API. Если VM понадобится в будущем (для cross-tab shared state) — добавим.

## Risks / Trade-offs

- **[Нет тестов на навигацию]** → Митигация: ручная проверка на устройстве (вкладки переключаются, FAB нажимается). Compose UI-тесты — отдельная задача.
- **[Insets-контракт хрупкий — кто-то может убрать `contentWindowInsets` при рефакторинге]** → Митигация: комментарии-обоснования у каждого из трёх `Scaffold` (уже в коде).
- **[Пустой test source set — Gradle warning `No tests to run`]** → Митигация: `failOnNoDiscoveredTests = false`; при появлении первого теста флаг можно убрать.
- **[Двойной учёт insets = стандартная ловушка Material3]** → Митигация: точное следование паттерну JetpackDays (проверено визуально, что FAB на правильной высоте).
- **[Нет `shouldShowNavigationBar` — `NavigationBar` видна всегда]** → Допустимо для текущего скоупа (2 таба). Добавим, когда появятся вложенные экраны вне табов.

## Migration Plan

Не требуется — это первый change в проекте, существующих пользователей/инсталляций нет. Все правки — внутри репозитория, фиксируются в коммитах на ветке `main`:

- `97743b3` → `f8f7f1f` — foundation (зависимости, `Screen`, экраны, `RootScreen`, `MainActivity`).
- `bd2a77f` — обновление JUnit до 6.1.3 (после которого `tasks.withType<Test>` падал с `--jvm-target=25` на JDK 25, и был введён `failOnNoDiscoveredTests = false`).
- `a96a7f2` — ponytail-рефакторинг: убраны `RootScreenViewModel`, `NavigationBarContent`, `Box+Text` плейсхолдеры, `@Preview`, `Log.d`.
- `dac396c` — FAB insets как в `JetpackDays` (outer `WindowInsets(0,0,0,0)` + inner `safeDrawing.only(Horizontal)`).
- `96eb61f` — план `docs/navigation-foundation.md` актуализирован под FAB insets (doc-only).
- `07bcc24` — отмечена ручная проверка на устройстве (doc-only).

## Open Questions

Нет — все решения приняты и реализованы в коде. Дальнейшие вопросы (список тренировок, реальная FAB-логика, DI) — отдельные changes.
