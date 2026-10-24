# Tasks

## 1. Чистая логика решения по шорткату (TDD, JVM)

- [ ] 1.1 Падающий тест `ShortcutLaunchResolverTest` (app/src/test/.../navigation/): наш action + текущий маршрут не entry_create → решение «открыть создание записи» с событием `OPEN_ENTRY_CREATE`; наш action + уже на entry_create → решения нет (не дублировать открытую форму); чужой/null action → решения нет. Проверка: `./gradlew testDebugUnitTest --tests "com.myworkouts.navigation.ShortcutLaunchResolverTest"` — красный (класса ещё нет).
- [ ] 1.2 Реализация: константа action `com.myworkouts.action.ADD_WORKOUT` + чистые функции `isAddWorkoutAction(action)` и `resolve(isAddWorkout, currentRoute)` в пакете `navigation`. Проверка: тот же точечный прогон — зелёный.

## 2. Объявление шортката: манифест и ресурсы (проверка фактом)

- [ ] 2.1 Создать `app/src/main/res/xml/shortcuts.xml`: статический шорткат `add_workout`, `shortcutShortLabel="@string/add_workout"`, intent с action из 1.2; атрибут `android:icon` НЕ задавать. Подключить meta-data `android.app.shortcuts` к MainActivity в AndroidManifest.xml. Проверка: `./gradlew :app:assembleGithubDebug` — сборка и merge ресурсов проходят.
- [ ] 2.2 В AndroidManifest.xml у MainActivity: intent-filter с action шортката + `category="android.intent.category.DEFAULT"`; `android:launchMode="singleTop"`. Проверка: `./gradlew :app:assembleGithubDebug` — манифест собирается без ошибок merger; запуск шортката подтверждается задачами 3.3–3.4.

## 3. Навигация по шорткату и UI-проверки (androidTest)

- [ ] 3.1 MainActivity: в `onCreate` и `onNewIntent` определять intent шортката (функция из 1.2) и выставлять одноразовый запрос «открыть создание записи»; неизвестные action игнорируются (обычный запуск не меняется). Проверка: `./gradlew :app:assembleGithubDebug`; поведение подтверждается 3.3–3.4.
- [ ] 3.2 RootScreen: после построения графа (`LaunchedEffect`) выполнять запрос через резолвер с текущим маршрутом — `navigate(Screen.EntryCreate.createRoute()) { launchSingleTop = true }` и логировать `AnalyticsEvent.UserAction(UserActionType.OPEN_ENTRY_CREATE)`; при «уже на entry_create» навигацию не выполнять. Проверка: `./gradlew :app:assembleGithubDebug`; аналитика покрыта тестом 1.1, переход — 3.3–3.4.
- [ ] 3.3 androidTest `LauncherShortcutTest`: холодный старт `ActivityScenario` с intent шортката → открыт экран «Новая запись»; «назад» → главный экран (back stack main → entry_create). Проверка: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.LauncherShortcutTest'` — зелёный.
- [ ] 3.4 androidTest: приложение на экране «Новая запись», повторная доставка intent шортката (`ActivityScenario.onActivity`/`onNewIntent`) → форма не дублируется поверх (введённый текст остаётся), второй экземпляр Activity не создаётся; отдельный сценарий — форма закрыта, Activity пересоздана (поворот) → экран создания записи самовольно не открывается. Проверка: тот же точечный androidTest-класс — зелёный.
- [ ] 3.5 androidTest: `ShortcutManager.getStaticShortcuts()` содержит шорткат с id `add_workout` и непустой подписью из строки `add_workout`, без иконки. Проверка: тот же точечный androidTest-класс — зелёный.

## 4. Ручная проверка в лаунчере (эмулятор/устройство)

- [ ] 4.1 Долгое нажатие на иконку приложения: в меню есть пункт «Добавить тренировку»; выбор открывает «Новая запись»; при свёрнутом приложении с открытой формой — форма не открывается заново. Зафиксировать, рисует ли конкретный лаунчер иконку приложения рядом с текстом (если рисует — принять как поведение лаунчера, в требованиях не менять). Проверка: наблюдение на эмуляторе (`make install` + лаунчер), заметка в итоговом описании задачи.

## 5. Полная проверка перед завершением

- [ ] 5.1 Полный прогон: `make format && make lint && make test` — зелёные; detekt `maxIssues: 5` не повышался, JDK 17. Проверка: команды проходят; отчёт JVM один прогон → один отчёт, XML при необходимости через `python3 scripts/test_report.py app/build/test-results`.
- [ ] 5.2 Полный androidTest: `make android-test` (эмулятор подключён; один прогон → один отчёт, не перезапускать make ради чтения отчёта; отчёт — `python3 scripts/test_report.py app/build/outputs/androidTest-results/connected/debug`). Проверка: отчёт без упавших тестов.
- [ ] 5.3 Финальная валидация: `openspec validate add-workout-quick-action --type change --strict`. Проверка: команда проходит без ошибок.
