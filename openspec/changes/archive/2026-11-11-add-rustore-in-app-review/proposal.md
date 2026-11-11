# Proposal

## Why

На rustore-сборке единственный способ попросить пользователя оценить приложение — ручная кнопка «Оценить приложение», которая открывает страницу отзывов в браузере. Конверсия ниже, чем у нативной формы: пользователь должен сам добраться до формы оценки. RuStore In-App Review SDK показывает нативную форму оценки и отзыва (шкала 1–5, отзыв опционален) прямо внутри приложения, а автоматические триггеры позволяют запросить оценку в подходящий момент, не дожидаясь ручного действия.

## What Changes

- Подключение RuStore In-App Review SDK (артефакт `ru.rustore.sdk:review`, версия 10.5.2) ТОЛЬКО для rustore flavor: репозиторий `https://nexus-external.rustore.ru/repository/maven-rustore-exposed` с content-filter по группе `ru.rustore.sdk`, добавляемый в `dependencyResolutionManagement` в `settings.gradle.kts` (режим `FAIL_ON_PROJECT_REPOS` — в `app/build.gradle.kts` репозитории добавлять нельзя), зависимость через `rustoreImplementation`, версия — в `gradle/libs.versions.toml`. Это первая flavor-зависимость и первые flavor source-sets (`app/src/rustore/`, `app/src/github/`) в проекте.
- Автоматический запрос оценки по milestone-порогам кумулятивного счётчика успешно созданных записей о выполнении упражнений `[1, 5, 15]` (редактирование записи и импорт бэкапа счётчик не увеличивают): каждый milestone — один раз за жизнь установки, не более одного авто-запроса за сессию, показ отложен до возврата на главный экран списка тренировок (~0.8 с), непоказанный pending переживает перезапуск приложения, любая ошибка SDK в авто-режиме обрабатывается тихо (без UI и фолбэка на браузер).
- Персистентное хранилище счётчиков триггеров (новый preferences DataStore в `data/preferences/`): счётчик созданных записей, пройденные milestones, pending-milestone; сброс при действии «Удалить все данные» (экран «Данные приложения»), после которого триггеры начинаются с milestone 1.
- Кнопка «Оценить приложение» остаётся без изменений (спека `more-screen`: `ACTION_VIEW` на страницу отзывов); SDK кнопкой не вызывается — рекомендация доков SDK не использовать кнопку как триггер выполняется сама собой.
- Новое аналитическое событие авто-запроса review-флоу — четвёртый тип в sealed interface `AnalyticsEvent` (прецедент расширения событийной модели): исход «форма показана» / «ошибка» с типом ошибки SDK; номер milestone в событие не входит.
- Документация: `AGENTS.md` — RuStore Review SDK как второе разрешённое исключение офлайн-правила (сеть — внутри приложения RuStore, свой сетевой стек в приложение не добавляется); `docs/deployment.md` — новый maven-репозиторий и flavor source-sets.
- github flavor: поведение не меняется — счётчик не ведётся, авто-запросы не инициируются, SDK не подключается (изоляция подтверждается самой компиляцией github-варианта).

## Capabilities

### New Capabilities

- `app-review`: автоматический запрос оценки приложения на rustore-сборке — milestone-триггеры по числу успешно созданных записей, показ нативной формы RuStore In-App Review SDK, ограничение частоты и отложенный показ, тихая обработка ошибок, персистентность и сброс счётчиков, flavor-гейтинг, аналитика.

### Modified Capabilities

_(пусто — требования существующих specs не меняются; кнопка «Оценить приложение» уже заспекена в `more-screen` и остаётся без изменений, сброс счётчиков описывается в новой capability со ссылкой на существующий флоу «Удалить все данные» из `app-data-management`)_

## Impact

- **Gradle**: `settings.gradle.kts` (maven-репозиторий RuStore с content-filter в `dependencyResolutionManagement`), `gradle/libs.versions.toml` (версия SDK), `app/build.gradle.kts` (`rustoreImplementation`; flavor source-sets подключаются самим наличием каталогов `app/src/rustore/`, `app/src/github/`).
- **Новые файлы**: домен (контракт лаунчера review-флоу без android-типов, доменная модель исхода с классификацией ошибок, чистый use case milestone-логики с константой порогов, модель счётчиков), `data/preferences/ReviewCountersDataStore.kt` (по образцу `AppSettingsDataStore`), flavor-реализации (`app/src/rustore/` — SDK-лаунчер + фабрика; `app/src/github/` — no-op + фабрика одинакового публичного FQN), in-memory holder сессии (presentation-слой, flavor-независимый).
- **Изменённые файлы**: `ui/viewmodel/EntryCreateViewModel.kt` (хук счётчика: ветка создания — `originalEntry == null`, после успешного `repository.addEntry`, до `onSaved()`), `ui/viewmodel/MainViewModel.kt` + `ui/screens/main/MainScreen.kt` (отложенный показ pending на главном экране), `ui/viewmodel/AppDataViewModel.kt` (сброс счётчиков в `deleteAllData()` при `onSuccess`), `analytics/AnalyticsEvent.kt` + `analytics/FirebaseAnalyticsProvider.kt` (новое событие), `MyWorkoutsApplication.kt` (process-singleton'ы review-флоу), `MainActivity.kt` / `ui/screens/root/RootScreen.kt` (`AppNavHost`, фабрики ViewModel — прокидка зависимостей). Кнопка «Оценить приложение» (`MoreScreen`) не меняется.
- **Зависимости**: новая внешняя зависимость `ru.rustore.sdk:review:10.5.2` (только rustore flavor), новый maven-репозиторий.
- **Тесты**: новые unit-тесты в `app/src/test/` (milestone-логика, контракт лаунчера на fake, хранилище счётчиков, ViewModel) и flavor unit-тесты SDK-лаунчера в `app/src/testRustore/` (MockK; в общем `app/src/test/` не скомпилируются — классы под тестом существуют только в rustore source set). SDK-флоу автотестами невоспроизводим — ручная проверка (release-подпись обязательна, ARM64-эмулятор с официальным приложением RuStore).
- **Ограничение SDK**: дневной лимит отображений регулируется на стороне RuStore (тихий `RequestLimitReached`), частые вызовы не гарантируют показ формы; форма закрывается только системным BACK.
