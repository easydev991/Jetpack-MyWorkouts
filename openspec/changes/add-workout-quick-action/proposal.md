# Proposal

## Why

Запуск тренировки начинается с создания записи, но сейчас до этого экрана можно добраться только через запуск приложения → главный экран → FAB. Долгое нажатие на иконку приложения в лаунчере — стандартный механизм Android для быстрых действий, и пункт «Добавить тренировку» сокращает путь до одного жеста ещё до открытия приложения.

## What Changes

- Добавляется статический шорткат лаунчера `add_workout` (res/xml/shortcuts.xml + meta-data в манифесте) с подписью из существующей строки `add_workout`.
- Шорткат объявляется без собственной иконки (`android:icon` не задаётся) — в меню лаунчера пункт отображается текстом; новых строковых и drawable-ресурсов не заводится.
- Шорткат использует собственный action `com.myworkouts.action.ADD_WORKOUT`; в MainActivity добавляется соответствующий intent-filter.
- MainActivity переключается на `launchMode="singleTop"` и обрабатывает intent (холодный старт и `onNewIntent`): переход на экран создания записи (`Screen.EntryCreate.createRoute()`) — тот же destination, что и по FAB; если экран создания записи уже открыт, повторно поверх него он не открывается.
- Стартовый маршрут NavHost не меняется (`Screen.Main.route`): экран создания записи открывается навигацией, «назад» возвращает на главный экран.
- Открытие экрана по шорткату логируется в аналитику существующим событием `OPEN_ENTRY_CREATE`.

## Capabilities

### New Capabilities
- `launcher-shortcut`: статический шорткат лаунчера «Добавить тренировку» — объявление без иконки, запуск экрана создания записи из любого состояния приложения (холодный старт, свёрнутое приложение, уже открытый экран), корректный back stack и аналитика.

### Modified Capabilities
- Нет.

## Impact

- app/src/main/res/xml/shortcuts.xml — новый файл: объявление статического шортката.
- app/src/main/AndroidManifest.xml — meta-data `android.app.shortcuts` у MainActivity, intent-filter action шортката, `launchMode="singleTop"`.
- app/src/main/java/com/myworkouts/MainActivity.kt — обработка intent шортката (onCreate/onNewIntent): запрос навигации на экран создания записи без дублирования уже открытого экрана.
- app/src/main/java/com/myworkouts/navigation/ShortcutLaunchResolver.kt — новый файл: константа action шортката и чистые функции решения (разбор action, guard от дублирования экрана).
- app/src/main/java/com/myworkouts/ui/screens/root/RootScreen.kt — выполнение навигационного запроса после построения графа NavHost.
- Строковые ресурсы не меняются — переиспользуется `add_workout` (ru «Добавить тренировку» / en «Add workout»).
- Тесты: app/src/test/ (JVM — разбор intent), app/src/androidTest/ (холодный старт по intent, singleTop-поведение, back stack, ShortcutManager).
