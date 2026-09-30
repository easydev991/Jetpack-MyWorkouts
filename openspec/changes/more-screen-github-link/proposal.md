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
- Добавляется file-private функция `openGitHub(context)` — открытие URL
  через `Intent.ACTION_VIEW` по существующему образцу `rateApp`
  (`toUri()` + `try/catch ActivityNotFoundException` + `Log.e`).
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
  новая константа URL, private composable `GitHubButton`, private fun
  `openGitHub`, вызов кнопки в `Column`.
- `app/src/main/java/com/myworkouts/ui/screens/more/MoreScreenPreview.kt` —
  превью новой кнопки.
- `app/src/main/res/values/strings.xml` — строка `github_button` (ru).
- `app/src/main/res/values-en/strings.xml` — строка `github_button` (en).
- Никаких изменений в навигации (`NavDestinations.kt`,
  `MoreNavDestination`), репозитории, ViewModel, флейворах и аналитике
  (события на тап не добавляются — по образцу кнопок
  «Оценить»/«Поделиться»).
