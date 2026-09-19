# Релизный процесс

- [Секреты для подписи](#секреты-для-подписи)
- [Каналы дистрибуции](#каналы-дистрибуции)
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
- SSH-доступ к GitHub (настраивается через `make setup_ssh` в JetpackDays — общая инструкция)
- Ключ добавлен в GitHub аккаунт

## Каналы дистрибуции

Проект собирается в двух `productFlavors` (`app/build.gradle.kts`) — flavor определяет канал дистрибуции и набор интегрированных фич.

| Flavor | Канал | `BuildConfig.RUSTORE_FEATURES` | Назначение |
| --- | --- | --- | --- |
| `rustore` | RuStore (основной) | `true` | Видны кнопки «Оценить приложение» и «Поделиться» в `MoreScreen`; AAB публикуется в RuStore через `make rustore`. |
| `github` | GitHub Release (альтернативный) | `false` | Те же кнопки скрыты; APK распространяется вручную через `make apk FLAVOR=github`. |

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
