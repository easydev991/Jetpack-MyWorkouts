# more-screen Specification

## Purpose

Экран «Ещё»: точка входа во второстепенные функции — версия приложения,
переход к настройкам внешнего вида и отправке отзыва, с локализованными
ru/en подписями.

## Requirements
### Requirement: MoreScreen Scaffold with TopAppBar

`MoreScreen` SHALL be a `@Composable fun MoreScreen(navController:
NavHostController, showExercisesTab: Boolean, onShowExercisesTabChange:
(Boolean) -> Unit, modifier: Modifier = Modifier)` annotated with
`@OptIn(ExperimentalMaterial3Api::class)`. Its `Scaffold` SHALL contain
`topBar = { TopAppBar(title = { Text(stringResource(
R.string.more_title)) }) }` and a body with the screen buttons and the
app version text at the bottom.

#### Scenario: Параметры без default-значений

- **WHEN** объявляется сигнатура `MoreScreen`
- **THEN** новые параметры — без default-значений: состояние и запись —
  выше по потоку, в `RootScreen` (ADDED-требование про тоггл выше;
  design Decision 8)

#### Scenario: Структура body Scaffold

- **WHEN** рендерится body `Scaffold` экрана
- **THEN** это `Box` с `Column(fillMaxSize,
  verticalArrangement = Arrangement.spacedBy(spacing_xxsmall),
  horizontalAlignment = Alignment.CenterHorizontally)`
- **AND** direct children `Column` — кнопки экрана без layout-обёрток
  (интервал между ними задаёт `spacedBy`), первая —
  `FilledTonalButton(...) { Text(stringResource(R.string.
  appearance_title)) }` near the top
- **AND** `Text` with the app version at the bottom, aligned via
  `Alignment.BottomCenter` as a direct child of the `Box`

#### Scenario: MoreScreen renders with localized title

- **WHEN** `MoreScreen(navController, showExercisesTab = false,
  onShowExercisesTabChange = {})` is composed with the Russian
  locale active
- **THEN** the `TopAppBar` displays "Ещё"
- **AND** the `Text` with the version string is displayed at the bottom
  of the body

#### Scenario: MoreScreen has appearance button

- **WHEN** `MoreScreen(navController, showExercisesTab = false,
  onShowExercisesTabChange = {})` is composed
- **THEN** a `FilledTonalButton` labeled "Оформление приложения" /
  "Appearance" is visible and horizontally centered

### Requirement: MoreScreen has no FAB or BottomBar

`MoreScreen` SHALL NOT include a `FloatingActionButton` or any `BottomBar`. The `NavigationBar` belongs to the outer `RootScreen` only — `MoreScreen` SHALL NOT duplicate it.

#### Scenario: Only one NavigationBar exists

- **WHEN** the user navigates to the `More` tab
- **THEN** exactly one `NavigationBar` is visible (from `RootScreen`)
- **AND** no FAB is rendered

### Requirement: MoreScreen Scaffold uses horizontal-only safeDrawing insets

`MoreScreen`'s `Scaffold` SHALL set `contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)` — symmetric with `MainScreen`. Rationale: see `specs/tab-navigation/spec.md` (insets contract). Without this, when the body becomes non-empty (list, `LazyColumn`, etc.), its content shifts upward by the system nav bar height.

#### Scenario: Insets do not double-subtract

- **WHEN** `MoreScreen` body contains content (list, etc.)
- **THEN** the content sits flush with the `NavigationBar` top edge via `paddingValues`
- **AND** does not shift upward by the system nav bar height

### Requirement: More tab label is localized ru/en

The string `more_title` (used for both `NavigationBarItem.label` and `TopAppBar.title`) SHALL exist in both `res/values/strings.xml` (Russian: "Ещё") and `res/values-en/strings.xml` (English: "More").

#### Scenario: Russian locale

- **WHEN** the device locale is `ru`
- **THEN** the more tab label is "Ещё"

#### Scenario: English locale

- **WHEN** the device locale is `en`
- **THEN** the more tab label is "More"

### Requirement: MoreScreen version text shows BuildConfig.VERSION_NAME

`MoreScreen` SHALL display the value of `BuildConfig.VERSION_NAME`
formatted via `stringResource(R.string.app_version,
BuildConfig.VERSION_NAME)`. The string `app_version` SHALL exist in
both `res/values/strings.xml` and `res/values-en/strings.xml` with a
`%1$s` placeholder for the version value.

#### Scenario: Стиль и ресурсы версии

- **WHEN** рендерится `Text` версии
- **THEN** он использует `MaterialTheme.typography.bodyMedium`,
  `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)`,
  `TextAlign.Center` and `Modifier.fillMaxWidth()`
- **AND** `app_version` — "Версия приложения: %1$s" (Russian,
  `res/values/strings.xml`) и "App version: %1$s" (English,
  `res/values-en/strings.xml`)

#### Scenario: Russian locale shows version

- **WHEN** `MoreScreen` is composed with the Russian locale active and `BuildConfig.VERSION_NAME = "1.0.0"`
- **THEN** the displayed text is "Версия приложения: 1.0.0"

#### Scenario: English locale shows version

- **WHEN** `MoreScreen` is composed with the English locale active and `BuildConfig.VERSION_NAME = "1.0.0"`
- **THEN** the displayed text is "App version: 1.0.0"

### Requirement: MoreScreen accepts a required NavController

`MoreScreen` SHALL accept `navController: NavHostController` as a
required first parameter; it is used to navigate to
`Screen.Appearance.route` when the appearance button is tapped. There
is no fallback or `LocalNavController` lookup — the parameter is
explicit and required.

#### Scenario: NavController is passed from RootScreen

- **WHEN** `RootScreen` composes the `More` destination
- **THEN** it invokes `MoreScreen(navController = navController,
  showExercisesTab = showExercisesTab, onShowExercisesTabChange =
  onShowExercisesTabChange)` with the `RootScreen`'s
  `rememberNavController()` and the setting collected from
  `AppSettingsDataStore`
- **AND** параметры `showExercisesTab: Boolean` и
  `onShowExercisesTabChange: (Boolean) -> Unit` — также обязательные,
  без default-значений (design Decision 8): пара используется кнопкой
  видимости вкладки из ADDED-требования выше

### Requirement: Appearance button navigates to Screen.Appearance

The "Оформление приложения" / "Appearance" `FilledTonalButton` SHALL have
`onClick = { navController.navigate(Screen.Appearance.route) {
popUpTo(Screen.More.route) } }`. The button SHALL use
`contentPadding = PaddingValues(horizontal =
dimensionResource(R.dimen.button_horizontal_padding))` where
`button_horizontal_padding` is `16dp` in `res/values/dimens.xml`.

#### Scenario: Russian locale button text

- **WHEN** `MoreScreen` is composed with the Russian locale active
- **THEN** the button text is "Оформление приложения"

#### Scenario: English locale button text

- **WHEN** `MoreScreen` is composed with the English locale active
- **THEN** the button text is "Appearance"

#### Scenario: Clicking the button navigates to appearance

- **WHEN** user taps the "Оформление приложения" / "Appearance" button
- **THEN** `navController.navigate(Screen.Appearance.route)` is called
  with `popUpTo(Screen.More.route)`
- **AND** the active destination becomes `Screen.Appearance.route`

#### Scenario: Button is horizontally centered

- **WHEN** `MoreScreen` is composed
- **THEN** the button's left margin (distance from `root.left` to
  `button.left`) equals the right margin (distance from `button.right`
  to `root.right`) within ±1px (regression test for the
  `Column(horizontalAlignment = CenterHorizontally)` wrapper)

### Requirement: MoreScreen has app data button

На экране «Ещё» под кнопкой «Оформление приложения» SHALL быть кнопка
«Данные приложения» / "App data" (`FilledTonalButton`, центрирование и
отступы — по образцу существующих кнопок). Нажатие SHALL выполнять
переход на маршрут `app_data` с `popUpTo(Screen.More.route)`. Строка
`app_data` SHALL существовать в `res/values/strings.xml` («Данные
приложения») и `res/values-en/strings.xml` ("App data").

#### Scenario: Russian locale button text

- **WHEN** `MoreScreen` composed с активной русской локалью
- **THEN** текст кнопки — «Данные приложения»

#### Scenario: English locale button text

- **WHEN** `MoreScreen` composed с активной английской локалью
- **THEN** текст кнопки — "App data"

#### Scenario: Clicking the button navigates to app data screen

- **WHEN** пользователь нажимает кнопку «Данные приложения» / "App data"
- **THEN** выполняется `navController.navigate(Screen.AppData.route)` с
  `popUpTo(Screen.More.route)`
- **AND** активным становится экран «Данные приложения»

### Requirement: MoreScreen имеет кнопку видимости вкладки «Упражнения»

На экране «Ещё» сразу после навигационных кнопок и до feedback-кнопки
(`R.string.feedback_send`) SHALL быть кнопка переключения видимости
вкладки «Упражнения». Надпись кнопки SHALL зависеть от состояния:
`R.string.more_exercises_tab_show` при `showExercisesTab = false`,
`R.string.more_exercises_tab_hide` при `showExercisesTab = true`;
нажатие кнопки SHALL вызывать `onShowExercisesTabChange` с новым
значением.

#### Scenario: Стиль и параметры кнопки видимости

- **WHEN** рендерится кнопка переключения видимости вкладки
- **THEN** это `FilledTonalButton` в стиле остальных кнопок экрана
- **AND** `MoreScreen` принимает параметры `showExercisesTab: Boolean` и
  `onShowExercisesTabChange: (Boolean) -> Unit` (состояние и запись —
  выше по потоку, в `RootScreen`; у экрана нет ViewModel)
- **AND** подписи: `R.string.more_exercises_tab_show` («Показать вкладку
  "Упражнения"» / "Show Exercises tab"),
  `R.string.more_exercises_tab_hide` («Скрыть вкладку "Упражнения"» /
  "Hide Exercises tab")

#### Scenario: Russian locale button label when tab is hidden

- **WHEN** `MoreScreen` composed с активной русской локалью и
  `showExercisesTab = false`
- **THEN** на кнопке виден текст «Показать вкладку "Упражнения"»

#### Scenario: English locale button label when tab is hidden

- **WHEN** `MoreScreen` composed с активной английской локалью и
  `showExercisesTab = false`
- **THEN** на кнопке виден текст "Show Exercises tab"

#### Scenario: Надпись отражает состояние настройки

- **WHEN** `MoreScreen(navController, showExercisesTab = true, ...)`
- **THEN** на кнопке текст «Скрыть вкладку "Упражнения"» /
  "Hide Exercises tab"

#### Scenario: Нажатие кнопки вызывает колбэк

- **WHEN** пользователь нажимает кнопку при `showExercisesTab = false`
- **THEN** вызывается `onShowExercisesTabChange(true)`

### Requirement: Настройка видимости вкладки сохраняется в DataStore

Настройка SHALL храниться в `AppSettingsDataStore` (ключ
`show_exercises_tab`, файл `appearance_prefs`): `Flow<Boolean>` с
дефолтом `false` при первом запуске + suspend-setter. `RootScreen` SHALL
собирать настройку из `dataStore` и при переключении кнопки писать её в
DataStore. Включение настройки SHALL добавлять вкладку «Упражнения» в
`NavigationBar`, выключение — убирать.

#### Scenario: Правила хранилища настройки

- **WHEN** определяется хранилище настройки
- **THEN** файл `appearance_prefs` — правило хранилища «новые ключи —
  точечно по фичам»
- **AND** `RootScreen` использует тот же экземпляр `dataStore`, что
  передан из `MainActivity`
- **AND** видимые вкладки — см. `tab-navigation/spec.md`

#### Scenario: Первый запуск — вкладка скрыта

- **WHEN** приложение запускается впервые (ключа в DataStore нет)
- **THEN** `showExercisesTab` эмитит `false`
- **AND** в нижней навигации нет вкладки «Упражнения»

#### Scenario: Настройка переживает перезапуск

- **WHEN** пользователь включил настройку, перезапустил приложение
- **THEN** `showExercisesTab` эмитит `true`
- **AND** вкладка «Упражнения» видна в нижней навигации

#### Scenario: Переключение настройки меняет нижнюю навигацию

- **WHEN** настройка включена и пользователь выключает её кнопкой
- **THEN** вкладка «Упражнения» исчезает из `NavigationBar` без
  перезапуска приложения
- **AND** порядок оставшихся вкладок («Тренировки», «Ещё») не меняется

### Requirement: MoreScreen имеет кнопки «Оценить приложение» и «Поделиться приложением» на flavor rustore

На экране «Ещё» после кнопки «Отправить обратную связь»
(`R.string.feedback_send`) SHALL быть две кнопки в стиле остальных
кнопок экрана, в порядке: «Оценить приложение»
(`R.string.rate_the_app`), затем «Поделиться приложением»
(`R.string.share_the_app`). Кнопки SHALL отрисовываться только когда
`BuildConfig.RUSTORE_FEATURES` равен `true` (flavor rustore); на flavor
github (`false`) кнопки SHALL отсутствовать в UI.

#### Scenario: Intents кнопок rustore

- **WHEN** пользователь нажимает «Оценить приложение»
- **THEN** открывается страница отзывов приложения в RuStore:
  `ACTION_VIEW` на
  `https://apps.rustore.ru/app/com.myworkouts/reviews`
- **WHEN** пользователь нажимает «Поделиться приложением»
- **THEN** открывается системный chooser (`Intent.createChooser`) для
  `ACTION_SEND` с `type = "text/plain"`, `EXTRA_TEXT` =
  `share_text(app_name)` + `"\n"` +
  `https://apps.rustore.ru/app/com.myworkouts`, `EXTRA_TITLE` =
  `R.string.share_the_app` (отдельный `share_chooser_title` не
  заводится — текст совпадает с подписью кнопки байт-в-байт)

#### Scenario: Обработка ошибок и строки ресурсов

- **WHEN** запуск intent падает
- **THEN** каждый запуск intent обёрнут в `try/catch
  ActivityNotFoundException`, ошибка логируется (`Log.e`, без падения
  приложения): для `rateApp` — сообщение «Не удалось открыть страницу
  приложения», для `shareApp` — «Не удалось открыть ShareSheet»
- **AND** строки существуют в `res/values/strings.xml` и
  `res/values-en/strings.xml`: `rate_the_app` — «Оценить приложение» /
  "Rate app"; `share_the_app` — «Поделиться приложением» / "Share app";
  `share_text` — «%1$s — приложение-дневник тренировок» / "%1$s -
  workout tracking app" (плейсхолдер — название приложения из
  существующего `app_name`)
- **AND** гейт `RUSTORE_FEATURES` — компайл-тайм, а не runtime-условие
  по данным; аналитические события на тапы по кнопкам SHALL NOT
  добавляться

#### Scenario: Кнопки видны на rustore

- **WHEN** сборка flavor rustore (`RUSTORE_FEATURES = true`), composed
  `MoreScreen`
- **THEN** видны обе кнопки: «Оценить приложение», затем ниже
  «Поделиться приложением» — после кнопки обратной связи

#### Scenario: Кнопки скрыты на github

- **WHEN** сборка flavor github (`RUSTORE_FEATURES = false`), composed
  `MoreScreen`
- **THEN** ни «Оценить приложение», ни «Поделиться приложением» не
  существует в иерархии UI

#### Scenario: Тап по «Оценить приложение» открывает RuStore

- **WHEN** пользователь на rustore-сборке нажимает «Оценить приложение»
- **THEN** системой открывается `ACTION_VIEW` на URL страницы отзывов
  приложения в RuStore

#### Scenario: Тап по «Поделиться приложением» открывает chooser

- **WHEN** пользователь на rustore-сборке нажимает «Поделиться
  приложением»
- **THEN** открывается системный chooser с заголовком
  `share_the_app`
- **AND** текст Share содержит название приложения (текст `share_text` с
  подставленным `app_name`) и ссылку на страницу приложения в RuStore

#### Scenario: Нет обработчика ссылки — приложение не падает

- **WHEN** на устройстве нет приложения, способного открыть intent
  (например, браузера), и пользователь нажимает «Оценить приложение»
- **THEN** ошибка логируется, приложение продолжает работу

#### Scenario: Локализация кнопок (ru)

- **WHEN** локаль устройства `ru`
- **THEN** кнопки подписаны «Оценить приложение» и «Поделиться
  приложением»

#### Scenario: Локализация кнопок (en)

- **WHEN** локаль устройства `en`
- **THEN** кнопки подписаны "Rate app" и "Share app"

### Requirement: MoreScreen имеет кнопку-ссылку на GitHub

На экране «Ещё» SHALL быть кнопка «GitHub» (`R.string.github_button`,
`FilledTonalButton` в стиле остальных кнопок экрана), размещённая
последней в `Column`; кнопка SHALL отрисовываться на обоих флейворах.
Нажатие SHALL открывать
`https://github.com/easydev991/Jetpack-MyWorkouts` в системном браузере
через `Intent.ACTION_VIEW`; при необработанном intent ошибка SHALL
логироваться без падения приложения.

#### Scenario: Позиция и аналитика кнопки GitHub

- **WHEN** рендерится `Column` кнопок экрана «Ещё»
- **THEN** кнопка «GitHub» стоит последней — после flavor-гейта
  `BuildConfig.RUSTORE_FEATURES` (кнопки «Оценить приложение» /
  «Поделиться приложением»)
- **AND** тап по кнопке SHALL NOT порождать аналитическое событие (по
  образцу кнопок «Оценить приложение» / «Поделиться приложением»)
- **AND** при `ActivityNotFoundException` логирование — через
  `Log.e("MoreScreen", …)`; пользователь остаётся на экране «Ещё»

#### Scenario: Кнопка видна на обоих флейворах

- **WHEN** composed `MoreScreen` на flavor github или rustore
- **THEN** кнопка «GitHub» присутствует в UI в конце колонки кнопок

#### Scenario: Тап по кнопке открывает репозиторий в браузере

- **WHEN** пользователь нажимает кнопку «GitHub»
- **THEN** системой выпускается `Intent.ACTION_VIEW` с URI
  `https://github.com/easydev991/Jetpack-MyWorkouts`

#### Scenario: Нет браузера — приложение не падает

- **WHEN** пользователь нажимает кнопку «GitHub», и ни одно activity не
  обрабатывает `Intent.ACTION_VIEW` для URI
- **THEN** ошибка перехватывается (`ActivityNotFoundException`) и
  логируется через `Log.e("MoreScreen", …)`; приложение остаётся на
  экране «Ещё»

#### Scenario: Локализация кнопки (ru)

- **WHEN** локаль устройства `ru`
- **THEN** кнопка подписана «GitHub»

#### Scenario: Локализация кнопки (en)

- **WHEN** локаль устройства `en`
- **THEN** кнопка подписана "GitHub"
