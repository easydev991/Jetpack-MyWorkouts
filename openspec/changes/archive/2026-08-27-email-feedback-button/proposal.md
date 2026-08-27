## Why

У пользователей нет прямого канала связи с разработчиком, чтобы сообщить о проблеме или предложить улучшение. Сейчас, чтобы отправить отзыв, пришлось бы копировать адрес вручную, что снижает вероятность обратной связи. Кнопка «Отправить обратную связь» на экране «Ещё» запускает системный почтовый клиент с предзаполненными темой и телом письма.

## What Changes

- Добавлена кнопка «Отправить обратную связь» на экран «Ещё» (`MoreScreen`) под кнопкой «Оформление приложения».
- Реализована функция `MoreScreen.sendFeedback(context)`: формирует `Intent.ACTION_SENDTO` с `mailto:easy_dev991@mail.ru`, предзаполненными темой («`<app_name>`: Обратная связь») и телом (версия Android + версия приложения + запрос предложений).
- Добавлены строки `feedback_send`, `feedback_subject`, `feedback_body`, `feedback_no_email_client` в `res/values/strings.xml` (ru) и `res/values-en/strings.xml` (en).
- Добавлен dimension `spacing_xxsmall = 4dp` в `res/values/dimens.xml` — вертикальный отступ между кнопками экрана «Ещё».
- При отсутствии почтового клиента на устройстве показывается Toast «Почтовый клиент не найден» и записывается `Log.e("MoreScreen", "Почтовый клиент не найден", e)`.
- Добавлен UI-тест `more_screen_when_displayed_then_shows_feedback_send_button` в `MoreScreenTest`.
- Попутно исправлен пре-существующий баг в `AppearanceScreenTest.kt:71` — добавлен параметр `isDynamicColorsAvailable = true` в `AppearanceScreenState`.

## Capabilities

### New Capabilities
- `feedback`: экран «Ещё» предоставляет пользователю возможность отправить отзыв о приложении через системный почтовый клиент с предзаполненной темой и телом письма.

### Modified Capabilities
<!-- Отсутствуют: требования существующего `more-screen` не меняются, к экрану лишь добавляется ещё одна кнопка; его структурные инварианты (Scaffold, TopAppBar, отсутствие FAB/BottomBar, горизонтальные insets, версия внизу) сохранены. -->

## Impact

- Затронутые файлы:
  - `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt` — добавлены кнопка и `sendFeedback()`.
  - `app/src/main/res/values/strings.xml`, `app/src/main/res/values-en/strings.xml` — 4 строки `feedback_*`.
  - `app/src/main/res/values/dimens.xml` — `spacing_xxsmall = 4dp`.
  - `app/src/androidTest/java/com/myworkouts/ui/screens/more/MoreScreenTest.kt` — новый `@Test` на отображение кнопки.
  - `app/src/androidTest/java/com/myworkouts/ui/screens/appearance/AppearanceScreenTest.kt` — побочный фикс `isDynamicColorsAvailable = true`.
- AndroidManifest не изменяется: для `Intent.ACTION_SENDTO` с `mailto:` не требуются ни `QUERY_ALL_PACKAGES`, ни иные permissions.
- Название приложения в теме берётся через локализованную строку `R.string.app_name` (`Мои тренировки` / `My workouts`), а не через `PackageManager.getApplicationLabel(...)`.
