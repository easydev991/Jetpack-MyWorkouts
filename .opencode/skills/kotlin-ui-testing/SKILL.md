---
name: kotlin-ui-testing
description: >
  Экспертное руководство по инструментированным (androidTest) тестам
  в Android-проекте Jetpack-MyWorkouts: JUnit 4, Compose Testing
  (createComposeRule / createAndroidComposeRule), Room in-memory,
  Turbine для ViewModel, тесты AlarmManager/Receiver, TestViewModel
  и фейковые репозитории, запуск через make android-test.
  Использовать при написании новых UI- и интеграционных тестов,
  отладке flaky androidTest и улучшении покрытия.
---

# Kotlin UI Testing — kotlin-ui-testing skill

## Overview

Этот skill — для инструментированных тестов Jetpack-MyWorkouts из
`app/src/androidTest/`. Стек: **JUnit 4** (`org.junit.*`),
**Compose Testing** (`createComposeRule`, `createAndroidComposeRule`),
**Room in-memory**, **Turbine**, **kotlinx-coroutines-test**
(`runTest`, `MainDispatcherRule`).

Два типа тестов в `androidTest/`:

- **Компонентные UI-тесты** — `createComposeRule()` (v2 API,
  `androidx.compose.ui.test.junit4.v2`), изолированный
  Compose-компонент, зависимости подменяются фейками
  (см. `references/component-testing.md`).
- **Интеграционные тесты** — `createAndroidComposeRule<MainActivity>()`
  (полный стек с реальной Activity), либо без Activity: Room DAO /
  репозиторий / ViewModel / Alarm (см. `references/activity-integration.md`,
  `references/room-repository.md`, `references/viewmodel-integration.md`).

> Unit-тесты — это `app/src/test/` (JUnit 5, MockK). Их правила описаны
> в навыке `kotlin-testing`. Этот skill про `app/src/androidTest/`.

## Agent behavior contract (следуй этим правилам)

1. **JUnit 4 — фреймворк androidTest.** Импорты из `org.junit.*`:
   `@Test`, `@Before`, `@After`. JUnit 5
   (`org.junit.jupiter.api.*`) — только в unit-тестах `app/src/test/`.
   `@RunWith(AndroidJUnit4::class)` — в интеграционных тестах
   (Activity, data/, reminder/) и в большинстве компонентных
   Compose-тестов (7 из 10). Без него — WorkoutDurationTextTest,
   ColorSelectorUiTest, WorkoutTagFilterDialogTest (с v2 API
   не требуется).
2. **ComposeTestRule — v2 API (`androidx.compose.ui.test.junit4.v2`).**
   Компонентный тест — `createComposeRule()`; тест с реальной
   MainActivity — `createAndroidComposeRule<MainActivity>()`. Правило
   объявляется как `@get:Rule val composeTestRule = ...`.
3. **snake_case для имён тестов, без обратных кавычек.** Форматы:
   `subject_whenCondition_thenResult` (`dialog_whenSameColorClicked_thenDeselects`),
   `when_condition_then_result` (`when_items_count_4_then_search_field_not_displayed`),
   составной `subject_verb_suffix_thenResult` (`themeIconScreen_displaysAppBarWithBackButton`,
   `themeIconScreen_clicksLightTheme_callsOnThemeChange`).
   Обратные кавычки в существующих тестах не используются.
4. **Given / When / Then в каждом тесте.** Разделяй тело тремя
   комментариями-маркерами.
5. **Никакого `!!`.** Даже в тестах. Используй `?.`, `?:`, `let`,
   `checkNotNull`, `assertNotNull(...)` перед доступом к полям.
6. **Сообщения `assert` — на русском.** По образцу существующих тестов:
   «A (09:00) должен быть выше B (18:00) при сортировке «сначала старые»...».
7. **Строки UI — через ресурсы, не хардкод.** `context.getString(R.string.save)`
   вместо «Сохранить». `context` — из `InstrumentationRegistry.getInstrumentation().targetContext`
   или `ApplicationProvider.getApplicationContext()`.
8. **Компонентный тест изолирует зависимости.** `setContent`
   оборачивает компонент в `MyWorkoutsTheme` (в большинстве тестов;
   WorkoutDurationTextTest — без темы, если компонент не зависит от темы).
   ViewModel создаётся factory-функцией с фейковыми зависимостями
   (см. `references/component-testing.md` — `TestViewModel.kt`).
9. **Интеграционный тест с данными — реальный Room in-memory.**
   `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`,
   `@After` закрывает базу (`database.close()`). Для ViewModel-теста —
   `clearAllTables()` в `@Before`. Вставка данных — через `runBlocking`.
10. **`waitForIdle()` после вставки данных.** Любой `runBlocking { ... }`
    с изменением БД перед assert обязан сопровождаться
    `composeTestRule.waitForIdle()`.
11. **Никакого `Thread.sleep`.** Для ожидания условий — 
    `waitUntil(timeoutMillis = ...) { ... }` или `waitForIdle()`.
    `Thread.sleep` в существующих тестах не используется.
12. **Turbine — только для ViewModel-интеграции** в `androidTest/`
    (`viewModel.uiState.test { awaitItem() }` внутри `runTest`).
    В unit-тестах Turbine запрещён.
13. **Асинхронность ViewModel — через `MainDispatcherRule`.**
    `@get:Rule val mainDispatcherRule = MainDispatcherRule()` — заменяет
    `Dispatchers.Main` на `StandardTestDispatcher`. Без него
    `viewModelScope.launch` не контролируется тестом.

## Запуск тестов

- **Все androidTest**: `make android-test` — запуск
  `./gradlew connectedDebugAndroidTest --console=plain` + человекочитаемый
  отчёт через `scripts/android_test_report.py`.
- **Отчёт в браузере**: `make android-test-report` — открывает
  `app/build/reports/androidTests/connected/debug/index.html`.
- **Все тесты (unit + интеграционные)**: `make test-all`.
- Отчёт `android_test_report.py`: exit code 0 при успехе, 1 при упавших
  тестах или ошибке Gradle. Подробности — `references/running-tests.md`.

## First 60 seconds (triage template)

Прежде чем писать код, собери факты:

- **Цель**: новые UI-тесты, миграция, flaky failures, покрытие
  экрана / компонента / слоя данных?
- **Факты**:
  - Что тестируем: отдельный Compose-компонент (компонентный тест,
    `createComposeRule`) или целый экран в MainActivity
    (интеграционный, `createAndroidComposeRule<MainActivity>`) или слой
    данных (DAO / Repository / ViewModel / Alarm)?
  - Нужны ли реальные данные? Если да — Room in-memory +
    `runBlocking`-вставки. Если нет — фейковый `WorkoutRepository`
    (`object : WorkoutRepository`) из `TestViewModel.kt`.
  - Есть ли ViewModel? Тогда `MainDispatcherRule` (+ Turbine для
    интеграции).
  - Есть ли системные сервисы (AlarmManager, NotificationManager)?
    Тогда `InstrumentationRegistry` + shell-пермишены для API 33+.
  - Нужен ли реальный стейт Activity (ротация, интенты)? Тогда
    `createAndroidComposeRule<MainActivity>()`.

## Routing map (читай нужный reference быстро)

- Анатомия теста, именование, Given/When/Then, JUnit 4 →
  `references/fundamentals.md`
- Compose: селекторы, действия, assertions, `waitForIdle` /
  `waitUntil` / `runOnIdle`, bounds → `references/compose-selectors.md`
- Компонентные UI-тесты: `createComposeRule`, `MyWorkoutsTheme`,
  `TestViewModel` → `references/component-testing.md`
- Интеграция с MainActivity: in-memory Room, ротация, очистка БД →
  `references/activity-integration.md`
- Room DAO / Repository: in-memory, `runBlocking`, `.first()`, CRUD,
  маппинг → `references/room-repository.md`
- ViewModel-интеграция: `MainDispatcherRule`, Turbine, `SavedStateHandle` →
  `references/viewmodel-integration.md`
- AlarmManager / Receiver: `PendingIntent`, пермишены API 33+ →
  `references/alarm-reminder.md`
- Запуск `make android-test`, отчёт, screenshot-tests →
  `references/running-tests.md`
- Полные канонические примеры → `references/EXAMPLE.md`

## Common pitfalls → next best move

| Проблема | Решение |
|---|---|
| Тест с MainActivity падает «no compose hierarchy found» | Используй `createAndroidComposeRule<MainActivity>()`, а не `createComposeRule()`, если тестируешь реальный экран |
| `onNodeWithText` не находит строку | Используй `context.getString(R.string.xxx)` — строка может быть в ресурсах, а не хардкодом; проверь, что вызван `setContent` (тема `MyWorkoutsTheme` — если компонент её использует) |
| Данные вставил, а UI их не видит | После `runBlocking { ... }` вызови `composeTestRule.waitForIdle()` |
| `onAllNodesWithContentDescription` без проверки количества | Добавь `.assertCountEquals(n)` — иначе тест проходит при 0 нод |
| `assertIsDisplayed()` падает, хотя нода есть вне экрана | Проверяй позицию через `fetchSemanticsNode().boundsInRoot` или проскролль до ноды |
| ViewModel в тесте не реагирует на действия | Добавь `MainDispatcherRule` (`Dispatchers.setMain`), иначе `viewModelScope` использует реальный Main |
| Тест Room падает «cannot access database on the main thread» | `.allowMainThreadQueries()` при создании in-memory базы |
| Тест ViewModel зависает навсегда | Не используй `runBlocking` с `viewModelScope.launch`. Замени на `runTest { ... }` + Turbine `.test { awaitItem() }` (см. `references/viewmodel-integration.md`) |
| `assertTrue(x is Y)` без проверки sealed перед `as Y` | Сначала `assertTrue(state is WorkoutDetailsState.Success)`, потом `as` — иначе ClassCastException |
| Alarm-тест не видит PendingIntent | Ищи через `PendingIntent.getBroadcast(...)` с `FLAG_NO_CREATE or FLAG_IMMUTABLE` (см. `references/alarm-reminder.md`) |
| Тест уведомлений падает на API 33+ | Оберни в `runWithNotificationPermission { ... }` — `adoptShellPermissionIdentity(POST_NOTIFICATIONS)` (см. `references/alarm-reminder.md`) |
| Ротация не работает в тесте | `composeTestRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE` + `waitForIdle()` (см. `references/activity-integration.md`) |

## Verification checklist

- [ ] Тест на JUnit 4: импорты из `org.junit.*`, класс аннотирован
      `@RunWith(AndroidJUnit4::class)`
- [ ] `@get:Rule val composeTestRule` — `createComposeRule()` (компонент)
      или `createAndroidComposeRule<MainActivity>()` (экран)
- [ ] Имя метода в `snake_case` без обратных кавычек
- [ ] Комментарии `// Given`, `// When`, `// Then` в каждом тесте
- [ ] Нет `!!` — `?.`, `?:`, `let`, `checkNotNull`, `assertNotNull`
- [ ] Сообщения `assert` на русском
- [ ] Строки UI через `context.getString(R.string.xxx)`, не хардкод
- [ ] Компонентный тест — изоляция через `TestViewModel` / фейковые
      зависимости, обёртка в `MyWorkoutsTheme` (по необходимости)
- [ ] Интеграционный тест — Room in-memory с `allowMainThreadQueries()`,
      `database.close()` в `@After` (или `clearAllTables()` в `@Before`),
      вставки через `runBlocking` + `waitForIdle()`
- [ ] ViewModel-тест — `MainDispatcherRule`; Turbine только в `androidTest/`
- [ ] Нет `Thread.sleep` — `waitUntil(timeoutMillis = ...)` / `waitForIdle()`
- [ ] Файлы в `app/src/androidTest/` зеркалят структуру `app/src/main/`
- [ ] Имя класса оканчивается на `Test` (или `UiTest` / `IntegrationTest` /
      `InstrumentedTest`)
- [ ] Запуск — `make android-test`, все тесты зелёные

## References

- `references/fundamentals.md`
- `references/compose-selectors.md`
- `references/component-testing.md`
- `references/activity-integration.md`
- `references/room-repository.md`
- `references/viewmodel-integration.md`
- `references/alarm-reminder.md`
- `references/running-tests.md`
- `references/EXAMPLE.md`
