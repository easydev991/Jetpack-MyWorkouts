# Design

## Context

До изменения `MoreScreen.kt` содержал весь нужный паттерн: константы URL
на верхнем уровне файла (`APP_RATE_URL`, `APP_SHARE_URL`), file-private
функции открытия (`rateApp` через `Intent.ACTION_VIEW` + `toUri()` +
`try/catch ActivityNotFoundException` + `Log.e("MoreScreen", …)`),
private composable-кнопки (`RateAppButton`, `ShareAppButton`) на
`FilledTonalButton` с общим `contentPadding`. Экран осознанно
без ViewModel. В ходе задачи паттерн переработан: функции открытия
свёрнуты в хелпер `openUrl`, кнопки рендерятся через общий
`ScreenButton` (Decision 2/3), `ExercisesTabToggle` вынесен в отдельный
файл (detekt `TooManyFunctions: 11`).
См. proposal.md (Why) и спеку more-screen
(Requirement «MoreScreen имеет кнопку-ссылку на GitHub»).

## Goals / Non-Goals

- Цель: кнопка «GitHub» по существующему шаблону экрана, минимальный дифф.
- Non-Goals: без нового объекта/UseCase, без ViewModel, без
  изменений навигации (`MoreNavDestination` не трогаем), без
  аналитических событий на тап, без androidTest (кнопка без логики —
  достаточно превью). Хелперы `ScreenButton`/`openUrl` введены по
  решению владельца (review, 2026-09-30) — см. Decision 2.

## Decisions

1. **Позиция — последней в `Column`, после flavor-гейта.** Вызов
   `GitHubButton(context = context)` ставится после блока
   `if (BuildConfig.RUSTORE_FEATURES) { RateAppButton(…);
   ShareAppButton(…) }`. Кнопка flavor-нейтральна: на github она
   оказывается после feedback-кнопки (гейт свёрнут), на rustore — после
   «Поделиться приложением»; порядок в коде един для обоих флейворов.
   Альтернатива (внутрь гейта / между feedback и гейтом) отклонена:
   ссылка на исходники не зависит от канала дистрибуции.
2. **Хелпер `openUrl(context, url, logMessage)` — единый обработчик
   `ACTION_VIEW` + `try/catch ActivityNotFoundException` + `Log.e`.**
   Решение пересмотрено при review (2026-09-30): с появлением
   `openGitHub` скелет стал третьим (`rateApp`, `openGitHub`; прямых
   call-site'ов два — `shareApp` не подходит: `ACTION_SEND` +
   `createChooser`), хелпер окупился; `rateApp`/`openGitHub` удалены,
   `RateAppButton`/`GitHubButton` зовут `openUrl` напрямую. Лог-сообщение
   — параметр хелпера («Не удалось открыть страницу GitHub» — в стиле
   существующих русскоязычных сообщений).
3. **Кнопка без аналитики и без параметров onClick.** `GitHubButton`
   принимает `context: Context` и сам вызывает `openUrl` —
   сигнатура единообразна с соседними
   `RateAppButton`/`ShareAppButton` (тоже `(context: Context)`); вариант
   `(text: String, onClick: () -> Unit)` отклонён ради этого же
   единообразия. Видимость — `internal`, а не `private`: превью в
   `MoreScreenPreview.kt` обращается напрямую (по образцу
   `ExercisesTabToggle`). Все кнопки экрана (включая `GitHubButton`)
   рендерятся через общий `ScreenButton(text, onClick)` — единственное
   место с `contentPadding` (review, 2026-09-30: седьмая копия блока).
   Превью — отдельной функцией в `MoreScreenPreview.kt`
   (одно состояние — одно превью, светлая + тёмная, `locale = "ru"`).
4. **Строка `github_button` = «GitHub» в обеих локалях.** Имя сервиса
   не переводится; ключ всё равно заводится (а не литерал в коде) —
   единообразие с остальными подписями кнопок экрана.

## Risks / Trade-offs

- [Дублирование шаблона intent-открытия] → Снято хелпером `openUrl`
  (review, 2026-09-30); `shareApp` сохраняет свой `try/catch` — intent
  другой (chooser). Вынос в util-объект — по факту четвёртого
  call-site'а.
- [Длинный список кнопок на маленьких экранах: версия прижата к низу
  через `Box.BottomCenter`, кнопки — к верху] → Коллизия при малой
  высоте не нова для экрана и не усугубляется одной кнопкой
  существенно; обработка переполнения — вне скоупа.

## Migration Plan

Не требуется: аддитивное UI-изменение, отката достаточно от rever'та
коммита. Схемы БД, DataStore, миграции не затрагиваются.

## Open Questions

(нет)
