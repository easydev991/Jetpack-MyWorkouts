## Context

См. `proposal.md` (Why/What Changes) и `specs/tab-navigation/spec.md` (требования). Текущее состояние: `RootScreen.Scaffold.bottomBar` рендерит `NavigationBar` безусловно для всех маршрутов `NavHost`. Цель — скрыть панель на не-tab маршрутах (сейчас `Screen.Appearance.route`).

Один файл правки (`RootScreen.kt`), без новых зависимостей и без DI.

## Goals / Non-Goals

**Goals:**
- Скрывать `NavigationBar` на не-tab маршрутах, оставлять на табах.
- Один источник правды для «является ли маршрут табом» (`BottomTabs`).
- Не дублировать логику скрытия в каждом экране.

**Non-Goals:**
- Анимации появления/скрытия `NavigationBar` (если нужны — отдельная задача).
- Изменение `Screen`, `BottomTab`, `BottomTabs`, `AppearanceScreen`, `MoreScreen`, `MainScreen`.
- Изменение инсетов (`contentWindowInsets`) — контракт из
  `tab-navigation/spec.md` (внешний `Scaffold` обнуляет insets, чтобы
  внутренние не вычитали дважды) сохраняется.

## Decisions

### 1. Скрывать в `RootScreen`, а не в дочерних экранах

`NavigationBar` живёт в `RootScreen.Scaffold.bottomBar` — это
единственное место, где она рендерится. Условие видимости ставим
рядом, на уровне `RootScreen`. Дочерние экраны (`AppearanceScreen`,
будущие не-tab) про `NavigationBar` ничего не знают.

**Альтернатива:** вложенный `Scaffold` в каждом не-tab экране с
пустым `bottomBar`. Отклонено: внешний `Scaffold.bottomBar` всё
равно занимает слот, плюс дублирование кода в каждом экране.

### 2. Список видимых маршрутов = `BottomTabs.map { it.route }`

```kotlin
val shouldShowBottomBar = currentRoute in BottomTabs.map { it.route }
```

**Альтернатива:** захардкоженный `listOf(Screen.Main.route, Screen.More.route)`.
Отклонено — при добавлении нового таба придётся править два места
(список табов + список «видимых маршрутов»), легко забыть. `BottomTabs`
уже единственный источник правды для табов — переиспользуем.

**Альтернатива:** добавить `Screen.isTab` или `val BottomTabRoutes =
BottomTabs.map { it.route }` как публичный список в `navigation/`.
Отклонено — YAGNI: один потребитель (RootScreen), вычисление в
одну строку. Если второй потребитель появится — вынести как
val.

### 3. Условный рендер через `if`, а не через `AnimatedVisibility`

```kotlin
bottomBar = {
    if (shouldShowBottomBar) {
        NavigationBar { ... }
    }
}
```

**Альтернатива:** `AnimatedVisibility(visible = shouldShowBottomBar)`.
Отклонено — добавляет анимационный indirection без явного запроса
на анимацию. Если позже понадобится плавное появление/скрытие —
обернуть в `AnimatedVisibility`, но это отдельная задача.

### 4. Не трогать `contentWindowInsets`

Внешний `Scaffold` обнуляет insets, потому что `NavigationBar` сама
учитывает system nav bar inset. Когда панель скрыта — `NavHost`
получает `paddingValues` с нулевым нижним inset, и контент
растягивается вниз до system nav bar. Это желаемое поведение
(полноэкранный режим на Appearance), совпадает с JetpackDays.

## Risks / Trade-offs

- **[Поведение `paddingValues` меняется на Appearance]** →
  Контент `AppearanceScreen` теперь доходит до низа экрана. У
  `AppearanceScreen` уже есть свой `Scaffold` с
  `padding(paddingValues)`, который получает корректные insets от
  родительского `Scaffold` (нулевые). Скроллируемый `Column` корректно
  обрабатывает полную высоту — регрессии нет.

- **[Новый не-tab экран может случайно получить панель]** → Закрыт
  предикатом `currentRoute in BottomTabs.map { it.route }`: любой
  маршрут, не перечисленный в `BottomTabs`, исключён из видимости
  панели автоматически. Чтобы это нарушить, нужно явно добавить
  маршрут в `BottomTabs` — тогда он перестаёт быть не-tab и
  наличие панели становится частью дизайна.

- **[Тесты `AppearanceScreen*` падают]** → Невозможно: тесты
  используют `AppearanceScreenContent` напрямую, минуя `RootScreen`
  и `NavigationBar`. Проверено: `androidTest/.../AppearanceScreenTest.kt`
  и `AppearanceScreenStaticTest.kt` зовут `composeTestRule.setContent
  { AppearanceScreenContent(...) }`.

## Migration Plan

Не требуется: изменение чисто клиентское, без миграций данных и
без серверной части. После `make install` пользователь видит
изменение сразу на следующем запуске.

Откат: `git revert` одного коммита в `RootScreen.kt` — возвращает
безусловный рендер `NavigationBar`.

## Open Questions

Нет. Все решения приняты, спецификация фиксирует наблюдаемое
поведение, реализация в одном файле.
