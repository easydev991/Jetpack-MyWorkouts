# Tasks

## 1. Базовая инфраструктура (чтобы тесты компилировались)

- [x] 1.1 `app/build.gradle.kts`: добавить
  `buildConfigField("Boolean", "RUSTORE_FEATURES", "true")` в flavor
  rustore и `buildConfigField("Boolean", "RUSTORE_FEATURES", "false")` в
  flavor github (design D1). Проверка: сборка
  `./gradlew assembleGithubDebug assembleRustoreDebug` проходит, в
  сгенерированном `BuildConfig` есть поле.
- [x] 1.2 Строки (имена и точные тексты — spec/more-screen/spec.md,
  буллет «Строки SHALL существовать…»; design D5): `rate_the_app`,
  `share_the_app`, `share_text` — в
  `res/values/strings.xml` и `res/values-en/strings.xml`. Проверка:
  сборка проходит, lint не ругается на недостающие переводы.

## 2. Тесты (red на rustore)

- [x] 2.1 `MoreScreenTest.kt`: добавить
  `more_screen_when_rustore_features_true_then_shows_rate_and_share_buttons`
  — `Assume.assumeTrue(BuildConfig.RUSTORE_FEATURES)`, composed
  `MoreScreenWithNavHost()`, `assertIsDisplayed` на
  `R.string.rate_the_app` и `R.string.share_the_app` (по образцу
  JetpackDays). Проверка на эмуляторе:
  `make android-test FLAVOR=rustore` — тест падает (кнопок ещё нет).
- [x] 2.2 `MoreScreenTest.kt`: добавить
  `more_screen_when_rustore_features_false_then_hides_rate_and_share_buttons`
  — `Assume.assumeFalse(BuildConfig.RUSTORE_FEATURES)`,
  `assertDoesNotExist` на обеих кнопках. Проверка:
  `make android-test FLAVOR=github` — тест зелёный уже сейчас, на
  rustore пропускается (assumption failed).

## 3. Реализация UI (green)

- [x] 3.1 `MoreScreen.kt`: константы `APP_RATE_URL` /
  `APP_SHARE_URL` — `private const val` вверху файла (design D2,
  applicationId `com.myworkouts`). Проверка: компиляция.
- [x] 3.2 `MoreScreen.kt`: `private fun rateApp(context)` и
  `private fun shareApp(context)` с `try/catch ActivityNotFoundException`
  → `Log.e` (design D4): `rateApp` → «Не удалось открыть страницу
  приложения», `shareApp` → «Не удалось открыть ShareSheet». Проверка:
  компиляция, detekt в рамках `make lint`.
- [x] 3.3 `MoreScreen.kt`: гейт
  `if (BuildConfig.RUSTORE_FEATURES)` — две `FilledTonalButton` после
  `FeedbackButton`, порядок «Оценить приложение» → «Поделиться
  приложением», `contentPadding = button_horizontal_padding`, без
  спейсеров (design D3). Проверка:
  `make android-test FLAVOR=rustore` — тест из 2.1 зелёный.
- [x] 3.4 Ручная проверка на эмуляторе: установить rustore-debug APK
  (`make install FLAVOR=rustore`), убедиться, что кнопки видны и
  открывают RuStore/chooser; на github-сборке кнопок нет.

## 4. Финальная верификация

- [x] 4.1 `make format && make lint && make test` — зелёные.
- [x] 4.2 `make android-test FLAVOR=rustore` и
  `make android-test FLAVOR=github` — зелёные (на github тест 2.1
  пропускается по assumption — ожидаемо).
