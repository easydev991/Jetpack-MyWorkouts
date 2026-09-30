# Tasks

## 1. Строковые ресурсы

- [ ] 1.1 Добавить строку `github_button` («GitHub») в
  `app/src/main/res/values/strings.xml` по аналогии с `rate_the_app` /
  `share_the_app`. Проверка: ключ присутствует в файле, `grep` находит
  его в одном экземпляре.
- [ ] 1.2 Добавить строку `github_button` ("GitHub") в
  `app/src/main/res/values-en/strings.xml`. Проверка: ключ существует в
  обоих ресурсных файлах (линтер локализации не ругается).

## 2. Кнопка на MoreScreen

- [ ] 2.1 Добавить URL-константу на верхнем уровне `MoreScreen.kt`:
  `private const val GITHUB_REPOSITORY_URL =
  "https://github.com/easydev991/Jetpack-MyWorkouts"` с KDoc-комментарием
  по образцу `APP_RATE_URL`. Проверка: константа рядом с существующими
  URL-константами файла.
- [ ] 2.2 Реализовать private fun `openGitHub(context: Context)`:
  `Intent.ACTION_VIEW` + `GITHUB_REPOSITORY_URL.toUri()` +
  `try/catch ActivityNotFoundException` с
  `Log.e("MoreScreen", "Не удалось открыть страницу GitHub", e)` — по
  образцу `rateApp`. Без выноса в helper/объект. Проверка: чтение кода —
  шаблон идентичен `rateApp`, приложение не падает при отсутствии
  обработчика.
- [ ] 2.3 Реализовать private @Composable `GitHubButton(context: Context)`
  по образцу `RateAppButton`: `FilledTonalButton` с
  `contentPadding = PaddingValues(horizontal =
  dimensionResource(R.dimen.button_horizontal_padding))`, текст из
  `stringResource(R.string.github_button)`, `onClick = {
  openGitHub(context) }`, KDoc по образцу соседних кнопок. Проверка:
  чтение кода — структура копирует `RateAppButton`.
- [ ] 2.4 Встроить вызов `GitHubButton(context = context)` в `Column`
  экрана последним элементом — после блока
  `if (BuildConfig.RUSTORE_FEATURES) { RateAppButton(…);
  ShareAppButton(…) }` (вне гейта: кнопка flavor-нейтральна). Обновить
  KDoc `MoreScreen` (список кнопок экрана). Проверка: чтение кода —
  вызов после if-блока; на flavor github кнопка после feedback, на
  rustore — после «Поделиться приложением».

## 3. Превью

- [ ] 3.1 Добавить в `MoreScreenPreview.kt` отдельный `@Preview` для
  `GitHubButton` по аналогии с превью существующих кнопок: светлая +
  тёмная (`UI_MODE_NIGHT_YES`), `showBackground = true`, `locale =
  "ru"`, обёртка в `JetpackMyWorkoutsTheme`, одно состояние — одно
  превью. Проверка: превью рендерится в Android Studio / сборка
  компилируется без ошибок.

## 4. Верификация

- [ ] 4.1 Прогнать `make format && make lint && make test` — все зелёные,
  новых detekt-предупреждений нет (лимит `maxIssues: 5` не повышать).
  Новых androidTest не требуется: кнопка без логики, достаточно превью.
  Проверка: отчёт `scripts/test_report.py` — `failures=0 errors=0`.
- [ ] 4.2 Ручная проверка на устройстве/эмуляторе: установить debug-APK,
  открыть экран «Ещё», тапнуть «GitHub» — открывается браузер с
  `https://github.com/easydev991/Jetpack-MyWorkouts`. Проверка:
  скриншот браузера с открытой страницей репозитория.
