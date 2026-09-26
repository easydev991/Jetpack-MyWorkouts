---
name: kotlin-ui-testing
description: >
  Экспертное руководство по инструментированным (androidTest) тестам
  в Android-проекте Jetpack-MyWorkouts: JUnit 4, Compose Testing
  (createComposeRule / createAndroidComposeRule), Room in-memory,
  реальные классы (WorkoutDatabase, RoomWorkoutDataRepository),
  запуск через make android-test. Использовать при написании новых
  UI- и интеграционных тестов, отладке flaky androidTest и
  улучшении покрытия.
---

# Kotlin UI Testing — kotlin-ui-testing skill

## Overview

Этот skill — для инструментированных тестов Jetpack-MyWorkouts из
`app/src/androidTest/`. Стек: **JUnit 4** (`org.junit.*`),
**Compose Testing** (`createComposeRule`, `createAndroidComposeRule`),
**Room in-memory**, **`kotlinx-coroutines-core`** (`runBlocking` —
транзитивно через Room/Compose). В `androidTest` нет ни
`kotlinx-coroutines-test`, ни Turbine (правило 13):
`kotlinx-coroutines-test` подключён только в unit-тестах
`app/src/test/` (`app/build.gradle.kts:128`).

Два типа тестов в `androidTest/` (различаются по правилу, а не по слою данных):

- **Компонентные UI-тесты** — `createComposeRule()` (v2 API,
  `androidx.compose.ui.test.junit4.v2`), без Activity. Внутри —
  изолированный Compose-компонент с готовым `*ScreenState` (без VM),
  экран с реальной ViewModel поверх Room in-memory +
  `NavHost`-harness, либо экран с реальной VM и фейк-репозиторием
  на `MutableStateFlow` без Room (`MainScreenSectionsTest`, см.
  `references/component-testing.md` → «Тест с фейковым репозиторием
  на MutableStateFlow»).
- **Интеграционные тесты** — `createAndroidComposeRule<MainActivity>()`
  (полный стек с реальной `MainActivity` и production-DI), либо
  Room DAO/репозиторий без Compose-правила (см.
  `references/activity-integration.md`, `references/room-repository.md`).
  С реальной `MainActivity` — `EntryListLiveUpdateIntegrationTest`,
  `ExerciseCreateLiveUpdateIntegrationTest`, `RootScreenTest`,
  `MainScreenDetailCardRecreateTest`; кроме них, два компонентных
  теста живут на `ComponentActivity` (см. правило 2).

> Unit-тесты — это `app/src/test/` (JUnit 5, MockK). Их правила описаны
> в навыке `kotlin-testing`. Этот skill про `app/src/androidTest/`.

## Agent behavior contract (следуй этим правилам)

1. **JUnit 4 — фреймворк androidTest.** Импорты из `org.junit.*`:
   `@Test`, `@Before`, `@After`. JUnit 5
   (`org.junit.jupiter.api.*`) — только в unit-тестах `app/src/test/`.
   Все классы androidTest в проекте аннотированы
   `@RunWith(AndroidJUnit4::class)` (включая компонентные
   Compose-тесты). `@RunWith(Parameterized::class)` —
   параметризованные тесты (`AppearanceScreenTest`,
   `ExercisesDeleteFlowTest`).
2. **ComposeTestRule — v2 API (`androidx.compose.ui.test.junit4.v2`).**
   Компонентный тест — `createComposeRule()`; тест с реальной
   MainActivity — `createAndroidComposeRule<MainActivity>()`.
   Исключение: компонентный тест с `DropdownMenu` —
   `createAndroidComposeRule(ComponentActivity)` (меню нужен
   window-host; `MainScreenSectionsTest`, `MainScreenDetailCardTest`).
   Правило объявляется как `@get:Rule val composeTestRule = ...`.
3. **Имена тестов: без обратных кавычек, обязательные
   `when_` / `then_`.** Префикс-subject опционален, регистр сегментов
   не нормируется — в реальных тестах встречается и snake_case
   (`exercises_screen_when_empty_then_shows_empty_state`,
   `sections_when_scrolled_then_date_header_sticks_above_rows`), и
   смешанный camelCase-сегменты
   (`exercisesScreen_whenReturnedQuickly_afterInsert_thenShowsNewExercise`,
   `app_whenReturnedToCatalogAfterInsert_thenShowsNewExercise`,
   `row_long_press_when_standard_then_no_context_menu`). Главные
   маркеры смысла — `when_<условие>_then_<результат>`; префикс-subject
   ставится, если без него имя двусмысленно
   (`sections_…` от `rows_…` и т.п.).
4. **Given / When / Then в каждом тесте, где это осмыслено.**
   Разделяй тело комментариями `// Given`, `// When`, `// Then` — в
   тестах со сценарием (insert / navigate / perform action). В чисто
   рендер-тестах (готовый `*ScreenState`, нет действий) маркер
   `// When` опускается — остаются `// Given` и `// Then`.
5. **Никакого `!!`.** Даже в тестах. Используй `?.`, `?:`, `let`,
   `checkNotNull`, `assertNotNull(...)` перед доступом к полям.
6. **Сообщения `assert` — на русском.** По образцу существующих тестов:
   «Sticky-заголовок должен остаться у верхнего края над строкой секции».
7. **Строки UI — через ресурсы, не хардкод.** `context.getString(R.string.save)`
   вместо «Сохранить». `context` — из
   `InstrumentationRegistry.getInstrumentation().targetContext`
   или `ApplicationProvider.getApplicationContext()`.
8. **Компонентный тест изолирует зависимости.** `setContent`
   оборачивает компонент в `JetpackMyWorkoutsTheme` (если компонент
   зависит от темы). Для рендер-тестов передаётся готовый
   `*ScreenState` (`ExercisesScreenState`, `ExerciseCreateScreenState`)
   без ViewModel. Для тестов с VM — реальная `ViewModel` поверх
   Room in-memory.
9. **Интеграционный тест с данными — Room in-memory.**
   `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`,
   `@After` закрывает базу (`database.close()`), `@Before` —
   пересоздание (`clearAllTables()` не используется — каждый тест
   получает свежую БД). Вставка данных — через `runBlocking`.
10. **`waitForIdle()` после вставки данных в Compose-тестах.** Любой
    `runBlocking { ... }` с изменением БД перед `assert*` в
    Compose-тесте обязан сопровождаться `composeTestRule.waitForIdle()`.
    В DAO/репозиторий-тестах Compose-правила нет — `waitForIdle()`
    не нужен.
11. **Никакого `Thread.sleep` в проверках.** Для ожидания условий —
    `waitUntil(timeoutMillis = ...) { ... }` или `waitForIdle()`.
    Единственное исключение — тесты, **воспроизводящие реальный
    пользовательский таймаут** (например,
    `ExerciseCreateLiveUpdateIntegrationTest` ждёт `delay(5_300L)` —
    это часть сценария `WhileSubscribed(5000)`, не ожидание UI).
12. **ViewModel-интеграция — только в существующем тесте.**
    Новые интеграционные ViewModel-тесты **не писать** —
    логика VM покрывается unit-тестами с Fake-репозиторием
    (см. навык `kotlin-testing`, правило 7).
    Существующие тесты с VM в androidTest (`ExercisesLiveUpdateTest`,
    `MainScreenSectionsTest`) обслуживать как есть.
13. **Без `MainDispatcherRule` / Turbine в androidTest.**
    `Turbine` не подключён нигде. `kotlinx-coroutines-test` подключён
    только в unit-тестах `app/src/test/`
    (`app/build.gradle.kts:128`) — в `androidTest/` его нет
    (зависимости `androidTestImplementation` в `app/build.gradle.kts:134-137`).
    В androidTest используется реальный `Dispatchers.Main` + `runBlocking`
    (Room + Compose UI Test работают с ним корректно).
14. **Один `setContent` на тест (sealed v2 API).** Повторный
    `composeTestRule.setContent` в том же тесте бросает
    `IllegalStateException: ... has already set content`. «Второй экран»
    в рамках теста — подмена состояния через `mutableStateOf` внутри
    первого `setContent` (образец — `EntryEditArchivedExerciseTest`:
    свап `currentViewModel` с последующим `waitForIdle`); смена темы/конфигурации —
    разносить по двум тестам (образец — `MainScreenTest`:
    `row_when_dark_theme_then_icon_uses_dark_variant` +
    `row_when_light_theme_then_icon_uses_light_variant`).
15. **`assertExists` / `assertDoesNotExist` — методы
    `SemanticsNodeInteraction`, импорт не нужен.** Top-level импорта
    `androidx.compose.ui.test.assertExists` в v2-артефактах нет
    (unresolved reference) — вызывай метод на receiver:
    `onNodeWithText(...).assertExists()`. Для коллекций нод —
    `assertCountEquals(n)` (top-level extension, импорт обязателен);
    видимость — `assertIsDisplayed`. Не путать: «не displayed» = узел
    есть, но за границами окна (частый случай — нижние опции
    dropdown-меню).

## Запуск тестов

- **Все androidTest**: `make android-test` — запуск
  `./gradlew connectedGithubDebugAndroidTest --console=plain` (задача
  флейвор-квалифицированная: github — дефолт, rustore —
  `connectedRustoreDebugAndroidTest`; голый `connectedDebugAndroidTest`
  не существует — Gradle ответит «ambiguous») + человекочитаемый
  отчёт через `scripts/test_report.py` (тот же скрипт, что и для
  `make test`, — XML из `app/build/outputs/androidTest-results/connected/debug/`).
- **Workflow** (точечный прогон в разработке / полный suite перед
  коммитом / `make emulator-fast`) — канон:
  `.opencode/rules/test-execution.md`; шпаргалка команд —
  `references/running-tests.md`.
- **Unit + интеграционные раздельно**: `make test` (JVM),
  `make android-test` (на устройстве). Объединённой цели нет
  (`make test-all` отсутствует).
- Отчёт `test_report.py` запускается **всегда** (Makefile запускает его
  и при упавших тестах — `BUILD_STATUS` берётся отдельно). На выходе —
  `✅ СБОРКА УСПЕШНА` / `❌ ТЕСТЫ ПРОВАЛИЛИСЬ`, статистика, **список
  каждого упавшего теста** (`ClassName::testName`) и per-class таблица.
  Exit code 0 при успехе, 1 при упавших. Подробности —
  `references/running-tests.md`.

## First 60 seconds (triage template)

Прежде чем писать код, собери факты:

- **Цель**: новые UI-тесты, миграция, flaky failures, покрытие
  экрана / компонента / слоя данных?
- **Факты**:
  - Что тестируем: отдельный Compose-компонент (компонентный тест,
    `createComposeRule`) или целый экран в MainActivity
    (интеграционный, `createAndroidComposeRule<MainActivity>`) или слой
    данных (DAO / Repository)?
  - Нужен ли реальный стейт ViewModel? Если да — реальная VM
    поверх Room in-memory (см. `ExercisesLiveUpdateTest`).
    Если нет — готовый `*ScreenState` через `setContent`.
  - Нужна ли реальная `MainActivity`? Почти всегда нет —
    `createComposeRule()` + реальный `NavHost`-harness
    покрывают навигацию без Activity. С реальной `MainActivity`
    — `EntryListLiveUpdateIntegrationTest`,
    `ExerciseCreateLiveUpdateIntegrationTest`, `RootScreenTest`,
    `MainScreenDetailCardRecreateTest`; для `DropdownMenu` в
    компонентном тесте — `createAndroidComposeRule(
    ComponentActivity)` (правило 2).
  - Нужна ли production-БД? Собственный
    `Room.databaseBuilder(context, ..., DATABASE_NAME)` открывают
    `EntryListLiveUpdateIntegrationTest`,
    `ExerciseCreateLiveUpdateIntegrationTest` и `RootScreenTest`;
    `MainScreenDetailCardRecreateTest` пишет в ту же production-БД
    через продакшен-репозиторий процесса (`app.repository`, без
    собственного `databaseBuilder`). Остальные тесты — Room
    in-memory или без БД.

## Routing map (читай нужный reference быстро)

- Анатомия теста, именование, Given/When/Then, JUnit 4 →
  `references/fundamentals.md`
- Compose: селекторы, действия, assertions, `waitForIdle` /
  `waitUntil` / `runOnIdle`, bounds → `references/compose-selectors.md`
- Компонентные UI-тесты: `createComposeRule`, `JetpackMyWorkoutsTheme`,
  готовые `*ScreenState`, NavHost-harness, реальная VM поверх Room in-memory
  → `references/component-testing.md`
- Сквозной сценарий с реальной `MainActivity` (продакшен-DI,
  `WhileSubscribed(5000)`, `ExerciseCreateLiveUpdateIntegrationTest`) →
  `references/activity-integration.md`
- Room DAO / Repository: in-memory, `runBlocking`, `.first()`, CRUD,
  маппинг → `references/room-repository.md`
- Запуск `make android-test`, отчёт, фильтрация одного класса →
  `references/running-tests.md`
- Ручная проверка на эмуляторе, `android-debug-bridge`, что UI-тесты
  не покрывают → `references/manual-verification.md`
- Поиск иконки упражнения (`testTag` + unmerged-дерево), проверка
  тёмного/светлого варианта по яркости →
  `references/exercise-icon-testing.md`
- Полные канонические примеры → `references/EXAMPLE.md`

> Файлы `viewmodel-integration.md`, `alarm-reminder.md` в
> текущей версии skill удалены: ViewModel-интеграция закрыта
> правилом 12, alarm/reminder в проекте пока не реализован.

## Common pitfalls → next best move

| Проблема | Решение |
|---|---|
| `onNodeWithText` не находит строку | Используй `context.getString(R.string.xxx)` — строка может быть в ресурсах, а не хардкодом; проверь, что вызван `setContent` (тема `JetpackMyWorkoutsTheme` — если компонент её использует) |
| Данные вставил, а UI их не видит | После `runBlocking { ... }` в Compose-тесте вызови `composeTestRule.waitForIdle()` |
| `onAllNodesWithContentDescription` без проверки количества | Добавь `.assertCountEquals(n)` — иначе тест проходит при 0 нод |
| `assertIsDisplayed()` падает, хотя нода есть вне экрана | Проверяй позицию через `fetchSemanticsNode().boundsInRoot` или проскролль до ноды (`performScrollToNode(hasText(...))`) |
| `task 'connectedDebugAndroidTest' is ambiguous` | В проекте флейворы (github/rustore) — задача всегда с флейвором: `connectedGithubDebugAndroidTest` (дефолт) / `connectedRustoreDebugAndroidTest`; `make android-test` подставляет сам |
| Тест Room падает «cannot access database on the main thread» | `.allowMainThreadQueries()` при создании in-memory базы |
| `navController.navigate(...)` бросает `IllegalArgumentException: destination not found` | Оберни экран в `NavHost`-harness с заглушкой destination (см. `ExercisesScreenTest.ExercisesScreenWithNavHost`) |
| `navController` нужен снаружи `setContent` | `lateinit var navController; composeTestRule.setContent { rememberNavController().also { navController = it } }` |
| `assertTrue(x is Y)` без проверки sealed перед `as Y` | Сначала `assertTrue(state is Success)`, потом `as` — иначе ClassCastException |
| `onNodeWithText("20")` не находит день в M3 `DatePicker` | В material3 1.4+ номер дня вычищен из семантики ячейки (доступен только локализованный description даты). См. `references/compose-selectors.md` → «M3 DatePicker: семантика дней (material3 1.4+)» (канон) |
| `performClick` на недоступной (disabled) ноде падает | У disabled-ноды нет click-action — паттерн «тап по недоступному → ничего не произошло» не работает. Проверяй `assertIsNotEnabled`, подсчёт disabled-нод или физический тап по координатам через `performTouchInput` |
| `Unresolved reference 'click'` в `performTouchInput` | `click`/`longClick`/`doubleClick` — extension-функции `TouchInjectionScope`, каждой нужен свой импорт: `import androidx.compose.ui.test.click` |
| Нод в диалоге больше, чем ожидал `assertCountEquals` | `onAllNodes(...)` видит форму под диалогом — см. `references/compose-selectors.md` → «M3 DatePicker: семантика дней (material3 1.4+)» (канон) |
| `onAllNodesWithTag` находит 0 нод, хотя тег проставлен | Тег на ноде внутри контейнера с `mergeDescendants = true` (строка списка, кнопка) не виден в merged-дереве — ищи с `useUnmergedTree = true` (канон — `ExerciseIcon` + `MainScreenTest`) |
| Второй `setContent` бросает `IllegalStateException: has already set content` | Sealed v2 API разрешает один `setContent` на тест — подменяй состояние через `mutableStateOf` внутри первого `setContent` (правило 14) |
| `Unresolved reference 'assertExists'` | Top-level импорта нет: `.assertExists()` — метод receiver'а, вызывай без импорта (`onNodeWithText(...).assertExists()`, правило 15); для `onAllNodes*` — `assertCountEquals` |
| Позиционная проверка «что левее/выше» даёт неверные bounds | `onNodeWithText` в merged-дереве возвращает слитую строку целиком (bounds = вся строка, left = 0) — оба сравниваемых узла бери из unmerged-дерева (канон — `MainScreenTest.row_when_icon_assigned_...`) |
| Assert на `FontWeight`/стиль шрифта — `GetTextLayoutResult` не резолвится | В Compose 1.12+ стиль `Text` удалён из семантики — через compose-test не проверить, закрывается превью. См. `references/compose-selectors.md` → «Семантика текста: стиль не проверить» (канон) |
| Тест ждёт «Август – Сентябрь», а в UI «Сентябрь – Август» | Фикстуры с датами строй в порядке прод-контракта (хитмап — по возрастанию): order-dependent вывод (`distinct().joinToString`) рендерит порядок входа. См. `references/component-testing.md` → «Фикстуры с датами» (канон — `ActivityScreenTest.heatmapOf`) |
| `println` в androidTest не виден в выводе gradle, дебажить падение нечем | Брось `AssertionError` с диагностикой (текст/bounds нод) — попадёт в `<failure>` XML-отчёта. См. `references/running-tests.md` → «Диагностика падающего теста» |

## Поиск иконки упражнения в UI-тестах

`ExerciseIcon` рисует `Image` с `contentDescription = null` (требование
спеки) — для тестов он помечен тегом `EXERCISE_ICON_TEST_TAG`
(`ui/icons/ExerciseIcon.kt`); поиск — только с `useUnmergedTree = true`.
Проверка тёмного/светлого варианта — `captureToImage()` + яркость
«чернил». Подробности и канонические примеры →
`references/exercise-icon-testing.md`.

## Verification checklist

- [ ] Тест на JUnit 4: импорты из `org.junit.*`, класс аннотирован
      `@RunWith(AndroidJUnit4::class)` (или `Parameterized::class`)
- [ ] `@get:Rule val composeTestRule` — `createComposeRule()` (компонент)
      или `createAndroidComposeRule<MainActivity>()` (экран)
- [ ] Имя метода без обратных кавычек, с маркерами `when_` / `then_`
      (регистр сегментов и префикс-subject — по правилу 3)
- [ ] Комментарии `// Given` / `// When` / `// Then` — в тестах
      со сценарием. В чисто рендер-тестах допустимо `// Given` +
      `// Then` (маркер `// When` опускается)
- [ ] Нет `!!` — `?.`, `?:`, `let`, `checkNotNull`, `assertNotNull`
- [ ] Сообщения `assert` на русском
- [ ] Строки UI через `context.getString(R.string.xxx)`, не хардкод
- [ ] Компонентный тест — готовый `*ScreenState` или реальная VM
      поверх Room in-memory; обёртка в `JetpackMyWorkoutsTheme`
      (по необходимости)
- [ ] Интеграционный тест с БД — Room in-memory с
      `allowMainThreadQueries()`, `database.close()` в `@After`,
      вставки через `runBlocking` + (в Compose-тестах) `waitForIdle()`
- [ ] Нет `Thread.sleep` для ожидания UI — `waitUntil(timeoutMillis = ...)`
      / `waitForIdle()`. Реальные таймауты сценария — через
      `kotlinx.coroutines.delay`
- [ ] Нет новых интеграционных ViewModel-тестов — логика VM
      покрывается JVM-тестами с Fake-репозиторием (правило 12)
- [ ] Нет `MainDispatcherRule` / `Turbine` / `kotlinx-coroutines-test`
      в androidTest — реальный `Dispatchers.Main` + `runBlocking`
      (правило 13)
- [ ] Файлы в `app/src/androidTest/` зеркалят структуру `app/src/main/`
- [ ] Имя класса оканчивается на `Test` (или `UiTest` / `IntegrationTest` /
      `InstrumentedTest`)
- [ ] Поиск по `testTag` (иконки упражнений) — только с
      `useUnmergedTree = true`; один `setContent` на тест
- [ ] Запуск — `make android-test`, все тесты зелёные; повторные прогоны
      ради чтения отчёта запрещены (см. `.opencode/rules/test-execution.md`)

## References

- `references/fundamentals.md`
- `references/compose-selectors.md`
- `references/component-testing.md`
- `references/activity-integration.md`
- `references/room-repository.md`
- `references/exercise-icon-testing.md`
- `references/running-tests.md`
- `references/manual-verification.md`
- `references/EXAMPLE.md`
