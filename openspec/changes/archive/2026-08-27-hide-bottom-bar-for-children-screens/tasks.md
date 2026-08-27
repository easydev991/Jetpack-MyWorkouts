> **Замечание по порядку:** этот change задокументирован post-factum —
> все пункты `[x]` отражают фактическое состояние кода. Правка уже
> применена в `RootScreen.kt` (см. предыдущую реплику: `if
> (shouldShowBottomBar) { NavigationBar { ... } }`). Задачи ниже —
> ретроспективная разбивка того, что было сделано.

## 1. Правка RootScreen

- [x] 1.1 В `app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt`
  вычислить `val shouldShowBottomBar = currentRoute in
  BottomTabs.map { it.route }` сразу после `currentRoute =
  navBackStackEntry?.destination?.route`.
- [x] 1.2 В `Scaffold.bottomBar` обернуть тело `NavigationBar { ...
  }` в `if (shouldShowBottomBar) { ... }`. Логика `NavigationBar`
  (`BottomTabs.forEach { tab -> ... NavigationBarItem(...) }`)
  не меняется — только внешний гард.
- [x] 1.3 Добавить строчный комментарий перед `shouldShowBottomBar`:
  «Нижняя навигация только на табах; на не-tab экранах (например,
  Appearance) её прячем, иначе лишний NavigationBarItem "More" висит
  над TopAppBar и выглядит как регрессия.»

## 2. Верификация

- [x] 2.1 Ручная проверка на эмуляторе (через `android-debug-bridge`
  + `make install`):
  - На табе `Main` (`Screen.Main.route`) — `NavigationBar` видна,
    `Workouts` `NavigationBarItem` `selected`.
  - На табе `More` (`Screen.More.route`) — `NavigationBar` видна,
    `More` `NavigationBarItem` `selected`.
  - «Ещё» → кнопка «Оформление приложения» → открывается
    `AppearanceScreen` БЕЗ `NavigationBar` под `TopAppBar` (раньше
    висела — это и был баг).
  - Стрелка «Назад» в `TopAppBar` → возврат на `More`,
    `NavigationBar` снова видна.
- [x] 2.2 Существующие тесты не сломаны:
  `AppearanceScreenTest` (2 `@Test`-метода × 3 параметра `AppTheme`
  в `@Parameterized` = 6 кейсов) и `AppearanceScreenStaticTest`
  (6 `@Test`-методов = 6 кейсов) используют `AppearanceScreenContent`
  напрямую через `composeTestRule.setContent`, минуя `RootScreen`
  и `NavigationBar`. `make android-test` — 12/12 зелёные
  (AppearanceScreenTest 6 + AppearanceScreenStaticTest 6).
- [x] 2.3 `make format && make lint && make test` — зелёные
  (правка синтаксически тривиальная, новых зависимостей/импортов нет).
