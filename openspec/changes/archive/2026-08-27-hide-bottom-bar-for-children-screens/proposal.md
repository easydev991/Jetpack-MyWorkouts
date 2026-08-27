## Why

На дочерних (не-tab) экранах, открытых с табов (например, «Оформление приложения» из «Ещё», маршрут `Screen.Appearance.route`), сейчас по-прежнему отображается `NavigationBar` корневого `Scaffold` с двумя табами «Workouts» и «More». Это визуальная регрессия: таб «More» остаётся выделенным `selected`, и пользователь видит навигацию к экрану, на котором он уже находится, поверх `TopAppBar`. Шаблон JetpackDays уже умеет прятать `NavigationBar` на не-tab экранах — переносим то же поведение.

## What Changes

- `RootScreen` теперь скрывает `NavigationBar` в `Scaffold.bottomBar`, когда активный маршрут — не-tab (сейчас это `Screen.Appearance.route`). На табах `Main` и `More` поведение не меняется.
- Видимость вычисляется как `currentRoute in BottomTabs.map { it.route }` — один источник правды; новый таб автоматически получает панель, любой не-tab экран — автоматически её прячет.
- Требование в `tab-navigation` про «`NavigationBar` всегда видна» обновляется: на `Screen.Appearance.route` она скрыта.

Никаких изменений в `Screen` / `BottomTab` / `AppearanceScreen` / `MoreScreen`. Никаких сетевых зависимостей, новых библиотек и DI.

## Capabilities

### New Capabilities

Нет.

### Modified Capabilities

- `tab-navigation`: сценарий «Appearance destination has no NavigationBarItem» заменяется — `NavigationBar` не просто не имеет `selected` элемента, она полностью скрыта на `Screen.Appearance.route`. Добавляется отдельное требование о видимости `NavigationBar` в зависимости от маршрута.

## Impact

- `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt` — `bottomBar` обёрнут в `if (shouldShowBottomBar)`.
- `openspec/specs/tab-navigation/spec.md` — дельта в одном сценарии существующего требования + новый Requirement «NavigationBar visibility follows current route».
- Android UI: визуально меняется только экран «Оформление приложения» — `BottomNavigationBar` убран.
- Тесты: `AppearanceScreenTest` и `AppearanceScreenStaticTest` используют `AppearanceScreenContent` напрямую (без `RootScreen`), поэтому изоляция сохранена, регрессий нет.
