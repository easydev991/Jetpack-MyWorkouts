## Why

Приложение запускается с пустого шаблона Android Studio (`MainActivity` показывает `Greeting`). До добавления первой фичи (списка тренировок) нужен минимальный, но рабочий навигационный каркас: две вкладки (`Тренировки`/`Ещё`), `NavHost`, `NavigationBar`, экран «Тренировки» с `FloatingActionButton` (placeholder для будущей фичи «добавить тренировку»). Без каркаса невозможно даже запустить приложение с фичей — навигации просто нет.

## What Changes

- Добавляем зависимости: `androidx.navigation:navigation-compose 2.9.8`, `material-icons-core` (явно, в `composeBom` его нет), JUnit 5 (`junit-jupiter 6.1.3` + `junit-platform-launcher 6.1.3`) для будущих unit-тестов.
- Добавляем `sealed class Screen` с двумя объектами `Main`/`More`, общим списком `AllTabs` (источник истины порядка вкладок) и non-null `icon`/`titleResId` (отход от `JetpackDays`: nullable там был для экранов вне табов, у нас такие не предвидятся).
- Добавляем `RootScreen` с `NavHost`, инлайновой `NavigationBar` поверх `Screen.AllTabs`, активная вкладка — через `currentBackStackEntryAsState()` (без отдельного `ViewModel`). Insets-контракт outer/inner `Scaffold` — см. `design.md` Decision 3.
- Добавляем `MainScreen` с `Scaffold` (`TopAppBar` + `FloatingActionButton` placeholder + пустое тело); `contentWindowInsets` — см. `design.md` Decision 3.
- Добавляем `MoreScreen` с `Scaffold` (только `TopAppBar`, пустое тело); `contentWindowInsets` симметрично с `MainScreen` (см. `design.md` Decision 3).
- Подключаем `RootScreen()` как корневой composable в `MainActivity`, удаляем шаблонные `Greeting`/`GreetingPreview`.
- Добавляем ru/en строки: `workouts_title`, `more_title`, `add_workout`.
- Настраиваем JUnit 5 (`useJUnitPlatform()`) и `failOnNoDiscoveredTests = false` для сборки без unit-тестов.

## Capabilities

### New Capabilities

- `tab-navigation`: sealed `Screen` с двумя табами + `RootScreen` с `NavHost` и `NavigationBar`, переключение вкладок, insets-контракт outer/inner Scaffold, `MainActivity` оборачивает `RootScreen`.
- `workouts-main-screen`: экран «Тренировки» — `Scaffold` с `TopAppBar` и `FloatingActionButton` placeholder (`Icons.Filled.Add`, content description `R.string.add_workout`). Пустое тело до появления списка тренировок.
- `more-screen`: экран «Ещё» — `Scaffold` с `TopAppBar`, `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)` (insets-контракт симметричен `workouts-main-screen`; см. `design.md` Decision 3), пустое тело.

### Modified Capabilities

- Нет. Существующих specs в `openspec/specs/` ещё нет — это первый change.

## Impact

- Зависимости Gradle (`gradle/libs.versions.toml`, `app/build.gradle.kts`): новые `navigation-compose`, `material-icons-core`, JUnit 5. Без сетевых зависимостей — offline-only требование AGENTS.md сохраняется.
- Шаблонный код `MainActivity` (`Greeting`, `GreetingPreview`) — удалён.
- Ресурсы (`res/values/strings.xml`, `res/values-en/strings.xml`): +3 ключа в каждом.
- `app/src/main/java/com/myworkouts/navigation/Screen.kt` — новый пакет `navigation`.
- `app/src/main/java/com/myworkouts/ui/screens/{root,main,more}/` — новые composable destinations.
- Тесты: добавлен JUnit 5 infra, но unit-тесты для VM отменены после ponytail-ревью (не нужны для двух статических табов).
- `make format && make lint && make test && make build` — все зелёные. Ручная проверка на устройстве: переключение вкладок и нажатие FAB подтверждены.
