# Tasks

## 1. Чистая логика решения по шорткату (TDD, JVM)

- [x] 1.1 Падающий тест `ShortcutLaunchResolverTest` (app/src/test/.../navigation/): наш action + текущий маршрут не entry_create → решение «открыть создание записи» с событием `OPEN_ENTRY_CREATE`; наш action + уже на entry_create → решения нет (не дублировать открытую форму); чужой/null action → решения нет. Проверка: `./gradlew testDebugUnitTest --tests "com.myworkouts.navigation.ShortcutLaunchResolverTest"` — красный (класса ещё нет).
- [x] 1.2 Реализация: константа action `com.myworkouts.action.ADD_WORKOUT` + чистые функции `isAddWorkoutAction(action)` и `resolve(isAddWorkout, currentRoute)` в пакете `navigation`. Проверка: тот же точечный прогон — зелёный.

## 2. Объявление шортката: манифест и ресурсы (проверка фактом)

- [x] 2.1 Создать `app/src/main/res/xml/shortcuts.xml`: статический шорткат `add_workout`, `shortcutShortLabel="@string/add_workout"`, intent с action из 1.2; атрибут `android:icon` НЕ задавать. Подключить meta-data `android.app.shortcuts` к MainActivity в AndroidManifest.xml. Проверка: `./gradlew :app:assembleGithubDebug` — сборка и merge ресурсов проходят. → Позже дополнено: иконка добавлена по решению владельца (6.1).
- [x] 2.2 В AndroidManifest.xml у MainActivity: intent-filter с action шортката + `category="android.intent.category.DEFAULT"`; `android:launchMode="singleTop"`. Проверка: `./gradlew :app:assembleGithubDebug` — манифест собирается без ошибок merger; запуск шортката подтверждается задачами 3.3–3.4.

## 3. Навигация по шорткату и UI-проверки (androidTest)

- [x] 3.1 MainActivity: в `onCreate` и `onNewIntent` определять intent шортката (функция из 1.2) и выставлять одноразовый запрос «открыть создание записи»; неизвестные action игнорируются (обычный запуск не меняется). Проверка: `./gradlew :app:assembleGithubDebug`; поведение подтверждается 3.3–3.4.
- [x] 3.2 RootScreen: после построения графа (`LaunchedEffect`) выполнять запрос через резолвер с текущим маршрутом — `navigate(Screen.EntryCreate.createRoute()) { launchSingleTop = true }` и логировать `AnalyticsEvent.UserAction(UserActionType.OPEN_ENTRY_CREATE)`; при «уже на entry_create» навигацию не выполнять. Проверка: `./gradlew :app:assembleGithubDebug`; аналитика покрыта тестом 1.1, переход — 3.3–3.4.
- [x] 3.3 androidTest `LauncherShortcutTest`: холодный старт `ActivityScenario` с intent шортката → открыт экран «Новая запись»; «назад» → главный экран (back stack main → entry_create). Проверка: `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.LauncherShortcutTest'` — зелёный.
- [x] 3.4 androidTest: приложение на экране «Новая запись», повторная доставка intent шортката (`ActivityScenario.onActivity`/`onNewIntent`) → форма не дублируется поверх (введённый текст остаётся), второй экземпляр Activity не создаётся; отдельный сценарий — форма закрыта, Activity пересоздана (поворот) → экран создания записи самовольно не открывается. Проверка: тот же точечный androidTest-класс — зелёный.
- [x] 3.5 androidTest: `ShortcutManager.getStaticShortcuts()` содержит шорткат с id `add_workout` и непустой подписью из строки `add_workout`, без иконки. Проверка: тот же точечный androidTest-класс — зелёный. → Позже изменено: проверка наличия иконки по цепочке манифест → shortcuts.xml → android:icon (6.2).

## 4. Ручная проверка в лаунчере (эмулятор/устройство)

- [x] 4.1 Долгое нажатие на иконку приложения: в меню есть пункт «Добавить тренировку»; выбор открывает «Новая запись»; при свёрнутом приложении с открытой формой — форма не открывается заново. Зафиксировать, рисует ли конкретный лаунчер иконку приложения рядом с текстом (если рисует — принять как поведение лаунчера, в требованиях не менять). Проверка: наблюдение на эмуляторе (`make install` + лаунчер), заметка в итоговом описании задачи. → Проверено на Pixel Launcher (Android 16): пункт есть, холодный запуск открывает «Новая запись»; пункт рисуется с серой заглушкой вместо иконки; лаунчер пересоздаёт Activity (см. design Risks) — документация скорректирована по решению владельца.

## 5. Полная проверка перед завершением

- [x] 5.1 Полный прогон: `make format && make lint && make test` — зелёные; detekt `maxIssues: 5` не повышался, JDK 17. Проверка: команды проходят; отчёт JVM один прогон → один отчёт, XML при необходимости через `python3 scripts/test_report.py app/build/test-results`. → 1126/1126, detekt — только 5 старых замечаний в лимите.
- [x] 5.2 Полный androidTest: `make android-test` (эмулятор подключён; один прогон → один отчёт, не перезапускать make ради чтения отчёта; отчёт — `python3 scripts/test_report.py app/build/outputs/androidTest-results/connected/debug`). Проверка: отчёт без упавших тестов. → 357 тестов: 356 зелёных, 1 осознанный пропуск (rustore-only тест на github-сборке, AssumptionViolated), 0 фейлов.
- [x] 5.3 Финальная валидация: `openspec validate add-workout-quick-action --type change --strict`. Проверка: команда проходит без ошибок.

## 6. Иконка шортката (доработка по решению владельца после 4.1)

- [x] 6.1 Векторная иконка `app/src/main/res/drawable/ic_shortcut_add_workout.xml` (белый круг с тёмным плюсом, по образцу «Добавить контакт» в «Контактах») + `android:icon="@drawable/ic_shortcut_add_workout"` в shortcuts.xml. Проверка: `./gradlew :app:assembleGithubDebug` — сборка зелёная.
- [x] 6.2 LauncherShortcutTest: проверка наличия иконки по цепочке манифест → shortcuts.xml → `android:icon` (публичный `ShortcutInfo.icon` для манифестных шорткатов всегда null); ручная проверка на Pixel Launcher — серая заглушка заменена нашей иконкой. Проверка: точечный прогон 4/4 зелёный, наблюдение на эмуляторе.

## 7. Исправления ревью (после реализации)

- [x] 7.1 Guard от дублирования расширен на весь back stack (`currentBackStack.value.any`): шорткат при стеке main → entry_create → entry_settings больше не пушит второй `entry_create`; `resolve(entryCreateOnStack)` — всегда-true параметр `isAddWorkoutAction` удалён; недостижимый `launchSingleTop` удалён; `setIntent(intent)` в `onNewIntent`. JVM-тесты резолвера обновлены. Проверка: `make test` — зелёный.
- [x] 7.2 LauncherShortcutTest: сверка согласованности action — `<intent android:action>` в shortcuts.xml и intent-filter манифеста (`queryIntentActivities`) равны `ACTION_ADD_WORKOUT` из Kotlin-константы. Проверка: точечный прогон — зелёный.
- [x] 7.3 design.md: честная формулировка изменения поведения обычного запуска при `singleTop` (возврат на текущий экран вместо перезапуска на список) — D3 и Risks.

## 8. Ручная проверка сценария 2 и утечки (после реализации)

- [x] 8.1 Сценарий 2 спеки («Приложение уже запущено на другом экране») проверен вручную владельцем на эмуляторе: «Данные приложения» → свёрнуто → шорткат → открылась форма; после сохранения/«назад» — главный экран; шорткат при открытом экране редактирования записи → корректно открылась «Новая запись», экран редактирования скрыт. Опциональный androidTest тёплой доставки на стороннем экране не добавлялся (решение владельца: ручная проверка достаточна, архив не блокирует).
- [x] 8.2 Утечки памяти в шорткат-сценариях: logcat LeakCanary за ручную сессию владельца без единого репорта (0 «LEAK»; вотчер в debug-сборке следит за всеми уничтожаемыми Activity/ViewModel), `make memory-check` — `MemoryLeakRegressionTest` 5/5 зелёный. Гарантии «утечек нет точно» не существует — выполнены оба доступных уровня проверки (runtime-вотчер + регрессионный тест).
