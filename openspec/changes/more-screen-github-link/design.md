# Design

## Context

`MoreScreen.kt` уже содержит весь нужный паттерн: константы URL на
верхнем уровне файла (`APP_RATE_URL`, `APP_SHARE_URL`), file-private
функции открытия (`rateApp` через `Intent.ACTION_VIEW` + `toUri()` +
`try/catch ActivityNotFoundException` + `Log.e("MoreScreen", …)`),
private composable-кнопки (`RateAppButton`, `ShareAppButton`) на
`FilledTonalButton` с `contentPadding = PaddingValues(horizontal =
dimensionResource(R.dimen.button_horizontal_padding))`. Экран осознанно
без ViewModel. См. proposal.md (Why) и спеку more-screen
(Requirement «MoreScreen имеет кнопку-ссылку на GitHub»).

## Goals / Non-Goals

- Цель: кнопка «GitHub» по существующему шаблону экрана, минимальный дифф.
- Non-Goals: без нового helper/объекта/UseCase, без ViewModel, без
  изменений навигации (`MoreNavDestination` не трогаем), без
  аналитических событий на тап, без androidTest (кнопка без логики —
  достаточно превью).

## Decisions

1. **Позиция — последней в `Column`, после flavor-гейта.** Вызов
   `GitHubButton(context = context)` ставится после блока
   `if (BuildConfig.RUSTORE_FEATURES) { RateAppButton(…);
   ShareAppButton(…) }`. Кнопка flavor-нейтральна: на github она
   оказывается после feedback-кнопки (гейт свёрнут), на rustore — после
   «Поделиться приложением»; порядок в коде един для обоих флейворов.
   Альтернатива (внутрь гейта / между feedback и гейтом) отклонена:
   ссылка на исходники не зависит от канала дистрибуции.
2. **Копия паттерна `rateApp`, без выноса общего helper.** `openGitHub`
   — file-private рядом с `rateApp`/`shareApp`; URL — константа
   `GITHUB_REPOSITORY_URL` на верхнем уровне файла. Два call-site'а
   одной строки `startActivity(Intent(ACTION_VIEW, url.toUri()))`
   (`rateApp` и будущий `openGitHub`; `shareApp` запускает
   `ACTION_SEND` через `createChooser` — другая форма) не оправдывают
   параметризуемую абстракцию. Лог-сообщение —
   «Не удалось открыть страницу GitHub» (в стиле существующих
   русскоязычных сообщений).
3. **Кнопка без аналитики и без параметров onClick.** `GitHubButton`
   принимает `context: Context` и сам вызывает `openGitHub` —
   сигнатура единообразна с соседними
   `RateAppButton`/`ShareAppButton` (тоже `(context: Context)`); вариант
   `(text: String, onClick: () -> Unit)` отклонён ради этого же
   единообразия. Превью — отдельной функцией в `MoreScreenPreview.kt`
   (одно состояние — одно превью, светлая + тёмная, `locale = "ru"`).
4. **Строка `github_button` = «GitHub» в обеих локалях.** Имя сервиса
   не переводится; ключ всё равно заводится (а не литерал в коде) —
   единообразие с остальными подписями кнопок экрана.

## Risks / Trade-offs

- [Дублирование шаблона intent-открытия в третьей функции] → Приемлемо:
  тело — 5 строк, ошибка обрабатывается локально; вынос в util-объект —
  по факту четвёртого call-site'а. (Каркас try/catch+startActivity
  общий у всех трёх intent-открытий; точный однострочник `ACTION_VIEW`
  — у двух, см. Decision 2.)
- [Длинный список кнопок на маленьких экранах: версия прижата к низу
  через `Box.BottomCenter`, кнопки — к верху] → Коллизия при малой
  высоте не нова для экрана и не усугубляется одной кнопкой
  существенно; обработка переполнения — вне скоупа.

## Migration Plan

Не требуется: аддитивное UI-изменение, отката достаточно от rever'та
коммита. Схемы БД, DataStore, миграции не затрагиваются.

## Open Questions

(нет)
