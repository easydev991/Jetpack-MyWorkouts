# Proposal

## Why

Пользователи должны иметь возможность перейти на GitHub-репозиторий проекта
(исходники, звёзды, issues) прямо из приложения. Сейчас ссылка доступна
только через описание релиза — внутри приложения точки входа нет.

## What Changes

- Добавляется одна новая кнопка «GitHub» на экран «Ещё» (`MoreScreen`) —
  последней в `Column` (позиция и rationale — design.md, Decision 1).
  Кнопка flavor-нейтральна: рисуется на обоих флейворах.
- Добавляются строковые ресурсы `github_button` (ru: «GitHub»,
  en: "GitHub").
- Добавляется private fun `openUrl(context, url, logMessage)` — единый
  обработчик `Intent.ACTION_VIEW` (`toUri()` +
  `try/catch ActivityNotFoundException` + `Log.e`); поглощает бывший
  образец `rateApp` (review-решение владельца, design.md, Decision 2).
- Все кнопки экрана переводятся на общий composable
  `ScreenButton(text, onClick)` — `contentPadding` в одном месте
  (review-решение владельца, design.md, Decision 3).
- Добавляется preview `GitHubButton` в `MoreScreenPreview.kt`
  (светлая + тёмная, `locale = "ru"`).
- Навигация, ViewModel, репозиторий, флейворы — без изменений
  (MoreScreen осознанно без ViewModel, новых маршрутов нет).

## Capabilities

### New Capabilities

(нет)

### Modified Capabilities

- `more-screen`: добавляется требование о кнопке-ссылке на GitHub
  (отображение, открытие URL в системном браузере, отказоустойчивость
  при отсутствии обработчика intent).

## Impact

- `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreen.kt` —
  новая константа URL, internal composable `GitHubButton`, private fun
  `openUrl`, вызов кнопки в `Column`; все кнопки экрана переведены на
  общий `ScreenButton` (review-решение владельца).
- `app/src/main/java/com/myworkouts/ui/screens/more/ScreenButton.kt` —
  общий composable кнопки экрана (новый файл).
- `app/src/main/java/com/myworkouts/ui/screens/more/ExercisesTabToggle.kt`
  — вынос тоггла из `MoreScreen.kt` (detekt `TooManyFunctions`), переведён
  на `ScreenButton`.
- `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreenPreview.kt` —
  превью новой кнопки.
- `app/src/main/res/values/strings.xml` — строка `github_button` (ru).
- `app/src/main/res/values-en/strings.xml` — строка `github_button` (en).
- Никаких изменений в навигации (`NavDestinations.kt`,
  `MoreNavDestination`), репозитории, ViewModel, флейворах и аналитике
  (события на тап не добавляются — по образцу кнопок
  «Оценить»/«Поделиться»).
