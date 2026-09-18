# Proposal

## Why

На flavor rustore приложение распространяется через RuStore, где нет
автоматического канала обратной связи с пользователем: пользователю нужно
больше оценок и проще делиться приложением. В JetpackDays для этого уже
есть готовое решение — кнопки «Оценить приложение» и «Поделиться
приложением», видимые только на rustore-сборке; портируем его 1:1.

## What Changes

- `app/build.gradle.kts`: в оба флейвора добавляется
  `buildConfigField("Boolean", "RUSTORE_FEATURES", ...)` — `true` для
  rustore, `false` для github (компайл-тайм-гейт).
- MoreScreen: после feedback-кнопки добавляются две кнопки —
  «Оценить приложение» и «Поделиться приложением», отрисовываемые только
  при `BuildConfig.RUSTORE_FEATURES`.
- «Оценить» открывает страницу отзывов приложения на RuStore
  (`ACTION_VIEW` на `https://apps.rustore.ru/app/com.myworkouts/reviews`),
  «Поделиться» — системный chooser с текстом и ссылкой
  (`ACTION_SEND`, `type = text/plain`). Обе — с
  `try/catch ActivityNotFoundException → Log.e`.
- Новые строки `rate_the_app`, `share_the_app`, `share_chooser_title`,
  `share_text` в `res/values/strings.xml` и `res/values-en/strings.xml`.
- androidTest: пара тестов видимости кнопок c `Assume.assumeTrue/false`
  по `BuildConfig.RUSTORE_FEATURES` (видны на rustore, отсутствуют на
  github).

## Capabilities

### New Capabilities

(нет)

### Modified Capabilities

- `more-screen`: ADDED-требование — кнопки «Оценить приложение» и
  «Поделиться приложением», видимые только на flavor rustore
  (`BuildConfig.RUSTORE_FEATURES`), с обработкой ошибок запуска intent.

## Impact

- `app/build.gradle.kts` — productFlavors: новый `buildConfigField` в
  обоих флейворах.
- `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt` —
  две приватные кнопки + `rateApp`/`shareApp`, константы URL —
  `private const val` вверху файла (отдельный `util/AppConstants` не
  создаётся — верхнеуровневый `util/` в проекте не заводится).
- `app/src/main/res/values/strings.xml`,
  `app/src/main/res/values-en/strings.xml` — 4 новые строки.
- `app/src/androidTest/java/com/myworkouts/ui/screens/more/MoreScreenTest.kt` —
  2 новых теста (Assume по флейву).
- Аналитика на кнопки не добавляется (как в JetpackDays). Offline-политика
  не нарушается: URL открывается через системный браузер, сетевых
  зависимостей нет.
