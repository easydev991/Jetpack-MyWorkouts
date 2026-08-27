## 1. Локализация

- [x] 1.1 В `app/src/main/res/values/strings.xml` (ru) добавить 4 строки:
  `feedback_send`, `feedback_subject` (с placeholder `%1$s`),
  `feedback_body` (с placeholders `%1$s` Android-версия и `%2$s` версия приложения),
  `feedback_no_email_client`.
- [x] 1.2 В `app/src/main/res/values-en/strings.xml` (en) добавить те же
  4 строки в английской локализации. Проверка: `grep -r "feedback_send"
  app/src/main/res/` возвращает ровно 2 совпадения — по одному в
  `values/strings.xml` и `values-en/strings.xml` (строка 20 в обоих).

## 2. Dimension

- [x] 2.1 В `app/src/main/res/values/dimens.xml` добавить
  `<dimen name="spacing_xxsmall">4dp</dimen>` — вертикальный отступ
  между кнопками экрана «Ещё». Проверка: dimension компилируется и
  доступен как `R.dimen.spacing_xxsmall`.

## 3. UI — кнопка и `sendFeedback()`

- [x] 3.1 В `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt`
  оформить `Column` с дефолтным `Arrangement.Top` (кнопки прижаты к
  верху) и горизонтальным центрированием через `Alignment.CenterHorizontally`.
  Под существующей кнопкой «Оформление приложения» добавить вторую
  `FilledTonalButton` с текстом `stringResource(R.string.feedback_send)`,
  вызывающую `sendFeedback(context)`. Между кнопками — `Spacer`
  высотой `dimensionResource(R.dimen.spacing_xxsmall)`. Проверка:
  экран компилируется, обе кнопки прижаты к верху и горизонтально
  отцентрированы.
- [x] 3.2 В конец `MoreScreen.kt` добавить приватную функцию
  `sendFeedback(context: Context)`: формирует `subject` и `body` через
  `context.getString(R.string.feedback_subject, appName)` и
  `context.getString(R.string.feedback_body, Build.VERSION.RELEASE,
  BuildConfig.VERSION_NAME)`, URL-кодирует их через `android.net.Uri.encode`,
  собирает `Intent(ACTION_SENDTO, "mailto:easy_dev991@mail.ru?subject=...&body=...")`
  через `.toUri()` и запускает через `context.startActivity(intent)`.
  В `catch (e: ActivityNotFoundException)` пишет
  `Log.e("MoreScreen", "Почтовый клиент не найден", e)` и показывает
  long-duration `Toast` с `R.string.feedback_no_email_client`.
  Проверка: компиляция без warnings; `Log.e` вызывается на устройстве
  без почтового клиента.
- [x] 3.3 Обновить KDoc функции `MoreScreen` — упомянуть `Arrangement.Top`
  (дефолт) и `Box.BottomCenter` для версии приложения.
- [x] 3.4 Удалить неиспользуемый импорт `Arrangement` из `MoreScreen.kt`
  (после отказа от `verticalArrangement = Arrangement.Center`).
  Проверка: `gradle compileDebugKotlin` без warning о неиспользуемых
  импортах.

## 4. UI-тест

- [x] 4.1 В `app/src/androidTest/java/com/myworkouts/ui/screens/more/MoreScreenTest.kt`
  добавить `@Test fun more_screen_when_displayed_then_shows_feedback_send_button()`
  через существующий harness `MoreScreenWithNavHost`. Проверка: тест
  ассертит `assertIsDisplayed()` на узел с текстом `R.string.feedback_send`.
  Итого в файле 5 `@Test`-методов (4 регресс-теста + новый на отображение
  кнопки).

## 5. Финальная верификация

- [x] 5.1 `make format && make lint && make test` — все три зелёные.
  Несвязанное предупреждение `MagicNumber` в `AppearanceViewModel.kt:62`
  (`5000L`) к этому change не относится.
- [x] 5.2 `make android-test` на эмуляторе `emulator-5554` — 18/18 ✅,
  включая 5 тестов `MoreScreenTest` и новое требование
  `more_screen_when_displayed_then_shows_feedback_send_button`.
- [x] 5.3 Побочный фикс: в
  `app/src/androidTest/java/com/myworkouts/ui/screens/appearance/AppearanceScreenTest.kt`
  добавлен `isDynamicColorsAvailable = true` в вызов `AppearanceScreenState`
  (строка 71) — пре-существующий баг, всплывший при прогоне `make
  android-test` для этой фичи.
- [x] 5.4 Ручная проверка на устройстве: обе кнопки прижаты к верху
  под `TopAppBar`, горизонтально отцентрированы, отступ между ними
  4dp; «Отправить обратную связь» открывает почтовый клиент с темой
  «Мои тренировки: Обратная связь» и телом, содержащим версии Android
  и приложения; на устройстве без почтового клиента — `Toast`
  «Почтовый клиент не найден» и `Log.e` с тегом `MoreScreen`.
