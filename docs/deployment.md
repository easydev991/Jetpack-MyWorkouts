# Релизный процесс

- [Секреты для подписи](#секреты-для-подписи)
- [Каналы дистрибуции](#каналы-дистрибуции)
  - [Maven-репозиторий RuStore SDK](#maven-репозиторий-rustore-sdk)
  - [Flavor source-sets](#flavor-source-sets)
  - [Ручная проверка review-флоу (RuStore SDK)](#ручная-проверка-review-флоу-rustore-sdk)
- [Создание сборки](#создание-сборки)
  - [Релизный AAB (для публикации в RuStore)](#релизный-aab-для-публикации-в-rustore)
    - [Первый релиз (вручную)](#первый-релиз-вручную)
    - [Двухшаговый workflow (draft → commit)](#двухшаговый-workflow-draft--commit)
  - [Релизный APK (для GitHub Release)](#релизный-apk-для-github-release)
- [Управление версией](#управление-версией)
- [Публикация в RuStore](#публикация-в-rustore)
  - [Release notes](#release-notes)
  - [API-авторизация](#api-авторизация)
  - [Что делать при сбое публикации](#что-делать-при-сбое-публикации)

## Секреты для подписи

Секреты для подписи хранятся в отдельном приватном репозитории: `git@github.com:easydev991/android-secrets.git` (каталог `myworkouts/`).

Все gradle-цели Makefile автоматически загружают их по SSH во временную директорию и копируют в `.secrets/` перед сборкой (цель `_ensure_secrets` клонирует репозиторий, только если `.secrets/` или `app/google-services.json` ещё отсутствуют).

**Требования:**
- SSH-доступ к GitHub — им клонируется репозиторий секретов; проверка: `ssh -T git@github.com`
- Ключ добавлен в GitHub аккаунт

## Каналы дистрибуции

Проект собирается в двух `productFlavors` (`app/build.gradle.kts`) — flavor определяет канал дистрибуции и набор интегрированных фич.

| Flavor | Канал | `BuildConfig.RUSTORE_FEATURES` | Назначение |
| --- | --- | --- | --- |
| `rustore` | RuStore (основной) | `true` | Видны кнопки «Оценить приложение» и «Поделиться» в `MoreScreen`; AAB публикуется в RuStore через `make rustore`. |
| `github` | GitHub Release (альтернативный) | `false` | Те же кнопки скрыты; APK распространяется вручную через `make apk FLAVOR=github`. |

### Maven-репозиторий RuStore SDK

Артефакт RuStore In-App Review SDK `ru.rustore.sdk:review` (версия — в `gradle/libs.versions.toml`) резолвится из репозитория `https://nexus-external.rustore.ru/repository/maven-rustore-exposed`. Репозиторий подключён с content-filter по группе `ru.rustore.sdk`, чтобы сторонний источник не отдавал остальные артефакты проекта.

Подключение — только в `dependencyResolutionManagement` в `settings.gradle.kts`: проект работает в режиме `FAIL_ON_PROJECT_REPOS`, репозиторий, объявленный в `app/build.gradle.kts`, приводит к ошибке сборки.

### Flavor source-sets

Flavor-специфичный код живёт в flavor source-sets `app/src/rustore/` и `app/src/github/` (первые в проекте — добавлены для in-app review). Сборка flavor-варианта компилирует код из `app/src/<flavor>/` вместе с `main`; код другого flavor'а в вариант не попадает. GitHub-вариант не видит rustore-код и не содержит RuStore SDK (SDK подключён как `rustoreImplementation` — только для flavor `rustore`); изоляция подтверждается самой компиляцией github-варианта.

### Ручная проверка review-флоу (RuStore SDK)

Авто-запрос оценки через RuStore In-App Review SDK (пороги `[1, 5, 15]` по числу созданных записей) автотестами не покрывается — SDK-ветку проверяют вручную. Ниже — окружение и эмпирика, подтверждённые при внедрении фичи; актуальны только для flavor `rustore` (в github-сборке авто-запросы выключены гейтом `isReviewEnabled`, SDK отсутствует).

**Требования к окружению:**

- **Release-подпись обязательна.** Сервер RuStore сверяет подпись установленного APK с подписями packageName из RuStore Консоли: debug-подпись даёт generic `RuStoreException` «Application is not verified yet» (уходит в `UNKNOWN`). Проверять только на release-сборке:

```bash
make apk FLAVOR=rustore
adb install -r app/build/outputs/apk/rustore/release/app-rustore-release.apk
```

  `make install` ставит debug-вариант и для этого шага не подходит.

- **ARM64-образ эмулятора**: в APK приложения RuStore нет x86_64 — на x86_64-образе RuStore не установится. Рабочая конфигурация: arm64-v8a, API 36, образ `sdk_gphone64_arm64`.
- **Официальное приложение RuStore** (<https://www.rustore.ru/download>) с авторизацией VK ID — без входа под аккаунтом нативная форма не показывается. Страница приложения для ручной проверки отзывов: <https://apps.rustore.ru/app/com.myworkouts/reviews>.

**Эмпирика SDK (учтена в реализации — помнить при прогоне и правках лаунчера):**

- **Дневной лимит — на стороне RuStore.** Число показов формы в сутки регулирует магазин: при исчерпанном лимите флоу завершается тихим `REQUEST_LIMIT_REACHED` без UI. Это штатный исход, а не регрессия.
- **Форма закрывается только системным BACK** (своей кнопки закрытия у неё нет); закрытие засчитывается как показ (`SHOWN`).
- **Все ошибки SDK — тихо**, без UI и фолбэка: исход уходит в событие аналитики `review_auto_request` (`outcome`/`error_type`). «Тишина» на экране при ошибке — ожидаемое поведение.
- **Первая попытка после свежей установки/входа в RuStore может упасть тихо** (SDK ещё не готов на устройстве): счётчики без `passed`/`pending`, в logcat следов ошибки нет. Это не дефект лаунчера — повтор в новой сессии приложения проходит успешно (`SHOWN`).
- **`Task.await()` SDK блокирует поток** (CountDownLatch, доставка результата через main-looper): вызов на main-диспетчере даёт дедлок/ANR. Закрыто обёрткой `withContext(Dispatchers.IO)` вокруг SDK-флоу (`app/src/rustore/java/com/myworkouts/review/RuStoreReviewFlowLauncher.kt`); если после правок лаунчера ANR воспроизводится — первым делом проверить, что обёртка не потеряна.
- **Счётчики фичи** — файл `files/datastore/review_counters.preferences_pb` в app-хранилище: на эмуляторе доступны после `adb root`; `run-as com.myworkouts` работает только на debug-сборке (на release не сработает). Штатный сброс — «Удалить все данные» в UI; `pm clear` и удаление приложения тоже сбрасывают счётчики.
- **Предпроверки установки RuStore у приложения нет**: отсутствие RuStore проявляется только как тихий `NOT_INSTALLED` из ошибки SDK.
- **Кнопка «Оценить приложение» при установленном RuStore** открывает магазин на его главном экране, а не на странице отзывов (ограничение app links из-за `/reviews` в ссылке); без RuStore — браузер. SDK-флоу кнопка не инициирует — поведение штатное, исправлять нечего.

**Сценарии прогона (сводка; последняя матрица — в tasks change'а `add-rustore-in-app-review`):**

- Первая созданная запись — нативная форма показывается с задержкой (~0,8 с) после возврата на главный экран; закрытие формы BACK — исход `SHOWN`.
- Несколько записей в одной сессии — второй авто-запрос не запускается (не более одного за сессию); следующий milestone подхватывается в новой сессии при следующем создании записи.
- Pending, запланированный до рестарта (приложение закрыто до показа формы), показывается после рестарта без новых записей; при исчерпанном дневном лимите — тихий `REQUEST_LIMIT_REACHED` без UI.
- Сборка без RuStore (чистый эмулятор) — тихий `NOT_INSTALLED`, интерфейс не меняется.
- Кнопка «Оценить приложение» в «Ещё» — поведение прежнее (открывает страницу отзывов, SDK-флоу не инициирует).
- «Удалить все данные» сбрасывает счётчики: следующая запись снова планирует milestone 1; если авто-запрос в текущей сессии уже выполнялся — при следующем создании записи в новой сессии.

**Ветки, открытые после публикации** (до неё невоспроизводимы):

- Первый реальный submit отзыва пользователем — `RuStoreReviewExists` (`REVIEW_EXISTS`).
- Бан-ветки `RuStoreApplicationBannedException` / `RuStoreUserBannedException` сейчас уходят в `UNKNOWN` (маппинг в `RuStoreReviewFlowLauncher`); выделять ли их в отдельные значения `ReviewErrorType` — решение владельца после публикации.

## Создание сборки

### Релизный AAB (для публикации в RuStore)

Для создания подписанной AAB-сборки выполните:

```bash
make rustore
```

Канал — `rustore`. Команда автоматически:

1. Увеличивает номер сборки (`VERSION_CODE`) на 1
2. Создаёт подписанную AAB-сборку: `myworkouts{VERSION_CODE}.aab` — flavor `rustore`
3. Загружает mapping-файлы Crashlytics в Firebase (для деобфускации стектрейсов крашей)
4. Копирует AAB в корень проекта
5. Загружает AAB в RuStore через RuStore API (см. [Публикация в RuStore](#публикация-в-rustore))

Для локальной отладки или когда нужно собрать AAB без загрузки в RuStore:

```bash
make rustore SKIP_PUBLISH=1
```

### Первый релиз (вручную)

RuStore API требует, чтобы у приложения была **хотя бы одна активная версия**, и не даёт
включить приложение без версий в API-ключ. Поэтому первый релиз выполняется через веб-консоль:

1. `make rustore SKIP_PUBLISH=1` — подписанный AAB готов в корне проекта
2. Загрузите AAB в [RuStore Console](https://console.rustore.ru) вручную
3. После появления активной версии включите `com.myworkouts` в API-ключ (создайте ключ
   на все приложения), положите `rustore-credentials.json` в `android-secrets/myworkouts/`
4. Дальнейшие релизы — `make rustore` / `make rustore-draft` (см. ниже)

#### Двухшаговый workflow (draft → commit)

По умолчанию `make rustore` отправляет релиз сразу на модерацию. Если хочется сначала проверить release notes в RuStore Console — разделите процесс:

```bash
make whats-new          # release notes из git log (существующий файл не перезаписывается)
make rustore-draft      # черновик в RuStore, без модерации
# → Найдите versionId в RuStore Console → Черновики.
$EDITOR fastlane/metadata/android/ru-RU/whats_new/<VERSION_NAME>.txt
make rustore-commit VID=<versionId>   # отправить черновик на модерацию
```

### Релизный APK (для GitHub Release)

```bash
make apk FLAVOR=github
```

Создаёт подписанный APK `myworkouts{VERSION_CODE}.apk` **без** увеличения `VERSION_CODE`. После сборки загрузите его на GitHub Release вручную.

## Управление версией

### Версия приложения (VERSION_NAME)

Изменяется вручную в `gradle.properties`: `VERSION_NAME=1.0.0`.

### Номер сборки (VERSION_CODE)

Автоматически увеличивается при каждом `make rustore`. Никогда не сбрасывается.

## Публикация в RuStore

Логика публикации живёт в тулките [android-release-toolkit](https://github.com/easydev991/android-release-toolkit),
подключённом как `git subtree` в `tools/release/`. Копии скриптов руками не правятся —
только `git subtree pull` нового тега тулкита.

`make rustore` вызывает `tools/release/scripts/rustore_publish.sh`, который выполняет проверку и 4 запроса к RuStore API:

1. **Авторизация** (`POST /public/auth/`) — JWE-токен (TTL 900 с)
2. **Проверка VERSION_CODE** (`GET /public/v1/application/{id}/version`) — если локальный код не выше опубликованного, скрипт падает **до** создания черновика
3. **Создание черновика** (`POST /public/v1/application/{id}/version`) — payload `{whatsNew, publishType:"MANUAL", appType:"MAIN"}`
4. **Загрузка AAB** (`POST /public/v1/application/{id}/version/{vid}/aab`) — multipart (таймаут 600 с)
5. **Отправка на модерацию** (`POST /public/v1/application/{id}/version/{vid}/commit?priorityUpdate=0`)

### Конфигурация приложения (RUSTORE_APP_ID / APP_NAME)

Скрипты тулкита генерик: package name задаёт `RUSTORE_APP_ID`, префикс артефактов — `APP_NAME`.
Обе объявлены в Makefile — единственное место в репо с идентификатором приложения:

```make
RUSTORE_APP_ID ?= com.myworkouts
APP_NAME ?= myworkouts
```

`RUSTORE_APP_ID` экспортируется в окружение и проверяется скриптом до сетевых вызовов.

### Release notes

Файл `fastlane/metadata/android/ru-RU/whats_new/<VERSION_NAME>.txt` отправляется в поле
`whatsNew` при создании черновика. `make whats-new` генерирует его из git log
(последний релизный тег → HEAD; тегов нет → «(первый релиз)»), существующий файл
не перезаписывается. Шаблон для ручной редактуры: [`fastlane/metadata/TEMPLATE-whats-new.md`](../fastlane/metadata/TEMPLATE-whats-new.md).

### API-авторизация

Скрипт читает `key_id` и `client_secret` (RSA private key PKCS8, base64) из
`.secrets/rustore-credentials.json`. Файл подгружается из приватного репозитория
`easydev991/android-secrets` (`_load_secrets` копирует всё содержимое каталога `myworkouts/`).

Подпись запроса: `base64(SHA512withRSA(private_key, keyId + timestamp))`.

### Что делать при сбое публикации

Скрипт fail-fast: ошибка на любом шаге прерывает выполнение. При сбое после создания
черновика, но до отправки на модерацию, в RuStore Console останется **orphan-черновик** —
удалите его в Console (Versions → «Черновик») и запустите `make rustore` повторно.
