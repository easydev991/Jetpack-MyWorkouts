## Purpose

Даёт пользователю способ отправить отзыв о приложении — сообщить о проблеме или предложить улучшение — через системный почтовый клиент с предзаполненной темой и телом письма.

## ADDED Requirements

### Requirement: Feedback button is displayed on MoreScreen

`MoreScreen` SHALL display a `FilledTonalButton` labeled with the localized value of `R.string.feedback_send` (`"Отправить обратную связь"` in Russian, `"Send feedback"` in English). The button SHALL be positioned below the "Оформление приложения" / "Appearance" button with a vertical spacing equal to `R.dimen.spacing_xxsmall` (4dp), and SHALL use `contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.button_horizontal_padding))` symmetric with the appearance button.

#### Scenario: Russian locale shows feedback button
- **WHEN** `MoreScreen` is composed with the Russian locale active
- **THEN** a `FilledTonalButton` with text "Отправить обратную связь" is visible below the appearance button with 4dp spacing

#### Scenario: English locale shows feedback button
- **WHEN** `MoreScreen` is composed with the English locale active
- **THEN** a `FilledTonalButton` with text "Send feedback" is visible below the appearance button with 4dp spacing

### Requirement: Tapping the button launches a prefilled email intent

Tapping the feedback button SHALL launch `Intent.ACTION_SENDTO` with a `mailto:` URI containing the recipient address `easy_dev991@mail.ru` and the following query parameters:

- `subject`: the value of `R.string.feedback_subject` with `%1$s` substituted by the localized `R.string.app_name` (yielding `"<app_name>: Обратная связь"` in Russian, `"<app_name>: Feedback"` in English).
- `body`: the value of `R.string.feedback_body` with `%1$s` substituted by `Build.VERSION.RELEASE` and `%2$s` substituted by `BuildConfig.VERSION_NAME`.

Both `subject` and `body` SHALL be URL-encoded via `android.net.Uri.encode` before insertion into the URI. The intent SHALL be launched via `context.startActivity(intent)` without `Intent.createChooser` — `ACTION_SENDTO` combined with `mailto:` is already filtered by the system to email clients only.

#### Scenario: Tapping opens email composition with prefilled fields
- **WHEN** the user taps the feedback button
- **THEN** the system opens the user's default email composition UI
- **AND** the To field contains `easy_dev991@mail.ru`
- **AND** the Subject field contains the localized template `"<app_name>: Обратная связь"` (Russian) or `"<app_name>: Feedback"` (English)
- **AND** the Body field contains the Android version on the first line and the app version on the second line, followed by the localized prompt `"Что можно улучшить в приложении?"` (Russian) or `"What can be improved in the app?"` (English)

### Requirement: Missing email client falls back to Toast and log

If `context.startActivity(intent)` throws `ActivityNotFoundException`, the system SHALL record `Log.e("MoreScreen", "Почтовый клиент не найден", e)` and show a long-duration `Toast` with the text from `R.string.feedback_no_email_client` localized for the current device locale (`"Почтовый клиент не найден"` in Russian, `"No email client found"` in English). No email composition UI is shown.

#### Scenario: Device without email client
- **WHEN** the user taps the feedback button on a device with no app registered to handle `ACTION_SENDTO` with `mailto:` URIs
- **THEN** no email composition UI is shown
- **AND** a `Toast` displays "Почтовый клиент не найден" (Russian) or "No email client found" (English)
- **AND** an error is recorded in logcat with tag `"MoreScreen"`

### Requirement: Feedback strings exist in both locales

The strings `feedback_send`, `feedback_subject`, `feedback_body`, and `feedback_no_email_client` SHALL exist in both `res/values/strings.xml` (Russian) and `res/values-en/strings.xml` (English). The string `feedback_subject` SHALL contain a `%1$s` placeholder for the app name; `feedback_body` SHALL contain `%1$s` (Android version) and `%2$s` (app version) placeholders in that order.

#### Scenario: Russian locale resolves all feedback strings
- **WHEN** the resource compiler processes `res/values/strings.xml`
- **THEN** all four `feedback_*` strings resolve to Russian text

#### Scenario: English locale resolves all feedback strings
- **WHEN** the resource compiler processes `res/values-en/strings.xml`
- **THEN** all four `feedback_*` strings resolve to English text
