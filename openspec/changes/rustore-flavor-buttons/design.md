# Design

## Context

Порт готового решения из JetpackDays (`/Users/Oleg991/Documents/GitHub/JetpackDays`):
кнопки rate/share уже реализованы и протестированы там, портируем 1:1 с
адаптацией под наш проект. У нас флейворы уже объявлены
(`app/build.gradle.kts`, `create("github")` / `create("rustore")`, оба
пустые), `buildConfig = true` включён. MoreScreen — `Box` + `Column` с
единым `Arrangement.spacedBy`, кнопки без layout-обёрток; у экрана нет
ViewModel.

## Goals / Non-Goals

**Goals:**

- Кнопки «Оценить приложение» → «Поделиться приложением» на MoreScreen,
  только на rustore-сборке, порядок именно такой.
- Компайл-тайм-гейт через `BuildConfig.RUSTORE_FEATURES` (не runtime-конфиг).
- Покрытие androidTest-парой с `Assume` по флейву — как в JetpackDays.

**Non-Goals:**

- Аналитика на кнопки (в JetpackDays её нет).
- Размещение в других магазинах / обработка Play Market URL.
- Отдельный `util/AppConstants` — верхнеуровневый `util/` в проекте
  не создаётся (AGENTS.md).

## Decisions

- **D1. Флейв-флаг**: `buildConfigField("Boolean", "RUSTORE_FEATURES",
  "true")` в rustore и `"false"` в github, как в JetpackDays. Альтернатива
  — отдельные исходники `src/rustore/...` с константой: избыточно для
  одного булева, buildConfigField проще и уже опробован в референсе.
- **D2. URL и константы**: `private const val` вверху `MoreScreen.kt` —
  `APP_RATE_URL = "https://apps.rustore.ru/app/com.myworkouts/reviews"`,
  `APP_SHARE_URL = "https://apps.rustore.ru/app/com.myworkouts"`
  (applicationId у нас `com.myworkouts`, у JetpackDays — другой пакет,
  поэтому URL не копируем, а пересобираем). В `util/AppConstants` не
  выносим — константы нужны только MoreScreen.
- **D3. UI-расположение**: две `FilledTonalButton` после `FeedbackButton`
  внутри той же `Column` (интервалы — родительский `spacedBy`, спейсеры
  не нужны), `contentPadding = PaddingValues(horizontal =
  button_horizontal_padding)` — как у соседних кнопок. Гейт —
  `if (BuildConfig.RUSTORE_FEATURES) { ... }` прямо в `Column` блока.
  Порядок: сначала «Оценить приложение», затем «Поделиться приложением».
- **D4. Intent-обработчики**: `rateApp(context)` — `ACTION_VIEW` на
  rate-URL; `shareApp(context)` — `ACTION_SEND`, `type = "text/plain"`,
  `EXTRA_TEXT = share_text(app_name) + "\n" + share-URL`,
  `EXTRA_TITLE = share_chooser_title`, запуск через
  `Intent.createChooser(intent, chooserTitle)`. Оба обработчика —
  `try/catch (e: ActivityNotFoundException) { Log.e("MoreScreen", ..., e) }`
  с разными сообщениями: `rateApp` → «Не удалось открыть страницу
  приложения», `shareApp` → «Не удалось открыть ShareSheet» (1:1 с
  JetpackDays). По образцу `sendFeedback` (там catch + Toast; здесь
  только Log.e). Функции `private fun`, вынесены из composable ради
  detekt `LongMethod` — тем же приёмом, что `sendFeedback`.
- **D5. Строки**: `rate_the_app`, `share_the_app`, `share_chooser_title`,
  `share_text` (с `%1$s`) в `values/strings.xml` (ru, дефолт) и
  `values-en/strings.xml`. Название приложения берём из существующего
  `app_name` — аналог `app_display_name` из JetpackDays не заводим.
- **D6. Тесты**: в `MoreScreenTest.kt` два теста с `Assume`:
  `more_screen_when_rustore_features_true_then_shows_rate_and_share_buttons`
  (`assumeTrue(BuildConfig.RUSTORE_FEATURES)`, `assertIsDisplayed` на
  обеих кнопках) и `more_screen_when_rustore_features_false_then_hides_rate_and_share_buttons`
  (`assumeFalse(...)`, `assertDoesNotExist`). Имена — snake_case, как
  уже в существующих тестах этого файла. На github-прогоне первый
  тест пропускается, второй выполняется; на rustore — наоборот. Прогон:
  `make android-test FLAVOR=rustore` и `make android-test FLAVOR=github`.
- **D7. Preview**: `MoreScreenPreview` остаётся валидным на flavor по
  умолчанию (github — кнопки скрыты). Превью с rate/share теоретически
  возможно через параметр с дефолтом `BuildConfig.RUSTORE_FEATURES`,
  но это лишний код и лишнее composable для состояния, которое видно
  только в rustore-артефакте — YAGNI, не добавляем.
- **D8. Без изменений в MainActivity/RootScreen**: кнопки целиком внутри
  `MoreScreen.kt`, новые параметры экрана не нужны (контекст —
  `LocalContext.current`, уже используемый на экране).

## Risks / Trade-offs

- [URL страницы отзывов в RuStore может измениться] → константы в одном
  месте (верх `MoreScreen.kt`), правка — одна строка.
- [`Assume`-тест «кнопки видны» пропускается на github-прогоне] →
  осознанный компромисс из JetpackDays: полный прогон CI делает на обоих
  флейвах (`make android-test FLAVOR=rustore` + `FLAVOR=github`).
- [Тап по share в androidTest реально открывает chooser] → тесты
  проверяют только видимость кнопок, не тапают по ним — как в
  JetpackDays.
