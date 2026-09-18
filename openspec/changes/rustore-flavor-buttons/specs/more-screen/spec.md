# Spec Delta

## ADDED Requirements

### Requirement: MoreScreen имеет кнопки «Оценить приложение» и «Поделиться приложением» на flavor rustore

На экране «Ещё» после кнопки «Отправить обратную связь»
(`R.string.feedback_send`) SHALL быть две кнопки в стиле остальных кнопок
экрана, в порядке: «Оценить приложение» (`R.string.rate_the_app`), затем
«Поделиться приложением» (`R.string.share_the_app`). Кнопки SHALL
отрисовываться только когда флейв-флаг `BuildConfig.RUSTORE_FEATURES`
равен `true` (flavor rustore); на flavor github (`false`) кнопки SHALL
отсутствовать в UI — компайл-тайм-гейт, а не runtime-условие по данным.

- «Оценить приложение» SHALL открывать страницу отзывов приложения в
  RuStore: `ACTION_VIEW` на `https://apps.rustore.ru/app/com.myworkouts/reviews`.
- «Поделиться приложением» SHALL открывать системный chooser
  (`Intent.createChooser`) для `ACTION_SEND` с `type = "text/plain"`,
  `EXTRA_TEXT` = `share_text(app_name)` + `"\n"` +
  `https://apps.rustore.ru/app/com.myworkouts`, `EXTRA_TITLE` =
  `R.string.share_the_app` (отдельный `share_chooser_title` не заводится —
  текст совпадает с подписью кнопки байт-в-байт).
- Запуск каждого intent SHALL быть обёрнут в `try/catch
  ActivityNotFoundException` с логированием ошибки (`Log.e`, без падения
  приложения): для `rateApp` — сообщение «Не удалось открыть страницу
  приложения», для `shareApp` — «Не удалось открыть ShareSheet».
- Строки SHALL существовать в `res/values/strings.xml` и
  `res/values-en/strings.xml`:
  - `rate_the_app`: «Оценить приложение» / "Rate app"
  - `share_the_app`: «Поделиться приложением» / "Share app"
  - `share_text`: «%1$s — приложение-дневник тренировок» /
    "%1$s - workout tracking app" (плейсхолдер — название приложения из
    существующего `app_name`)
- Аналитические события на тапы по кнопкам SHALL NOT добавляться.

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

#### Scenario: Локализация кнопок

- **WHEN** локаль устройства `ru`
- **THEN** кнопки подписаны «Оценить приложение» и «Поделиться
  приложением»
- **WHEN** локаль устройства `en`
- **THEN** кнопки подписаны "Rate app" и "Share app"
