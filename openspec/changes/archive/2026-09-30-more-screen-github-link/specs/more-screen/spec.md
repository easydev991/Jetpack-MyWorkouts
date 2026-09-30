# Spec Delta

## ADDED Requirements

### Requirement: MoreScreen имеет кнопку-ссылку на GitHub

На экране «Ещё» SHALL быть кнопка «GitHub» (`R.string.github_button`,
`FilledTonalButton`, в стиле остальных кнопок экрана), размещённая
последней в `Column` — после flavor-гейта
`BuildConfig.RUSTORE_FEATURES` (кнопки «Оценить приложение» /
«Поделиться приложением»). Кнопка SHALL отрисовываться на обоих
флейворах (flavor-нейтральна). Нажатие SHALL открывать
`https://github.com/easydev991/Jetpack-MyWorkouts` в системном браузере
через `Intent.ACTION_VIEW`. Если ни одно приложение не обработало
intent, ошибка SHALL логироваться через `Log.e("MoreScreen", …)` без
падения приложения — пользователь остаётся на экране «Ещё». Тап по
кнопке SHALL NOT порождать аналитическое событие (по образцу кнопок
«Оценить приложение» / «Поделиться приложением»).

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
