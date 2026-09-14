# План: GitHub-флейвор и `make apk` (по образцу JetpackDays)

## Цель

Добавить в Jetpack-MyWorkouts productFlavors `github` и `rustore` (заглушка)
и цель `make apk FLAVOR=github` — локальную сборку подписанного release-APK
в корень репо. Без GitHub Actions и без интеграции с магазином (как в
JetpackDays: релизы собираются локально).

## Контекст (уже готово, делать не надо)

- Секреты лежат в `~/Documents/GitHub/android-secrets/myworkouts/`:
  `keystore/myworkouts-release.keystore`, `secrets.properties`,
  `certificates/`. Для github-флейвора добавлять туда нечего.
- Версии: `VERSION_NAME=1.0.0` / `VERSION_CODE=1` в `gradle.properties`,
  читаются в `app/build.gradle.kts` через `findProperty` — совпадает
  с JetpackDays, не трогаем.
- `.gitignore` уже закрывает `.secrets/`, `*.keystore`, `*.apk` — не трогаем.
- R8 в release уже включён (`isMinifyEnabled`, `isShrinkResources`).
- Firebase нет — шаг копирования `google-services.json` из JetpackDays
  в наш `_load_secrets` НЕ переносится.

## Главный риск: не сломать существующее

1. **Появление flavors удаляет задачи `assembleDebug`/`installDebug`** —
   существующие `make build`, `make install`, `make all` сломаются.
   Чинятся FLAVOR-маппингом на этапе 2.
2. **Чтение `secrets.properties` идёт на конфигурационной фазе Gradle** —
   без безопасных дефолтов упадёт ЛЮБая gradle-задача (включая
   `make test`) на машине без `.secrets/`. Дефолты обязательны.
3. **Мастер не должен оставаться сломанным** — этапы 1, 2 и 4 коммитятся
   одним коммитом, в середине рабочее дерево может быть несобираемым.

## TDD

Не применяется: задача — сборочная инфраструктура, без бизнес-логики,
моделей и UI. Вместо red-green — verification-гейты в конце каждого
этапа и полный прогон проверок на этапе 4.

---

## Этап 0: Фиксация baseline

- [ ] Убедиться, что секреты клонируются: `ssh -T git@github.com` и
  репозиторий `android-secrets` доступен (для этапа 3).
- [ ] Прогнать и зафиксировать зелёные: `make build`, `make test`,
  `make lint` — это точка сравнения «ничего не сломалось».
- [ ] Записать вывод `./gradlew tasks --group=build` (список assemble-
  задач до изменений) — для сравнения после.

**Критерий завершения:** все три цели зелёные, baseline записан.

## Этап 1: Flavors и подпись в `app/build.gradle.kts`

По образцу: JetpackDays, `app/build.gradle.kts` (signingConfigs + flavors).

- [ ] Объявить `flavorDimensions += "distribution"`.
- [ ] Создать flavor `github` — пустой, без buildConfigField
  (flavor-зависимое поведение отложено, см. «Вне скоупа»).
- [ ] Создать flavor `rustore` — пустая заглушка, одинаковая с `github`
  (наполнение — см. «Вне скоупа»).
- [ ] Без `applicationIdSuffix`/`versionNameSuffix` — как в JetpackDays,
  оба флейвора = один package.
- [ ] Добавить `signingConfigs.create("release")`:
  - чтение `rootProject.file(".secrets/secrets.properties")` через
    `Properties()`;
  - ключи `KEYSTORE_FILE` (default `.secrets/keystore/myworkouts-release.keystore`),
    `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` — все с безопасными
    дефолтами (см. риск 2: конфигурация не должна падать без секретов);
  - `storeFile = rootProject.file(keystoreFile)`.
- [ ] Повесить `signingConfig` только на `buildTypes.release`
  (debug остаётся на debug-keystore — `make build` без секретов работает).

Не делать: `buildFeatures { buildConfig = true }`, Firebase-плагины,
суффиксы id, изменение `versionCode`/`versionName`.

**Критерий завершения:**
- [ ] `./gradlew tasks` показывает `assembleGithubDebug`,
  `assembleRustoreDebug`, `assembleGithubRelease`, …
- [ ] `./gradlew help` (конфигурационная фаза) проходит на машине
  БЕЗ `.secrets/` — дефолты работают.
- [ ] Осознанно сломано: `make build` (`assembleDebug` больше нет) —
  чинится на этапе 2; коммит — в этапе 4, до него мастер не коммитится.

## Этап 2: Makefile — FLAVOR-маппинг, секреты, `apk`

По образцу: JetpackDays, Makefile (`FLAVOR`, `_load_secrets`,
`_ensure_secrets`, `apk`).

- [ ] Переменные: `SECRETS_REPO = git@github.com:easydev991/android-secrets.git`,
  `SECRETS_DIR = myworkouts`,
  `APP_NAME ?= myworkouts` (конвенция тулкита публикации: имя приложения
  не хардкодится — из него выводятся и keystore-путь, и префикс артефакта;
  будущая make-проводка сведётся к замене конфига),
  `FLAVOR ?= github` с валидацией
  (`$(error)` на всё, кроме `github`/`rustore`),
  `FLAVOR_TITLE := $(if $(filter rustore,$(FLAVOR)),Rustore,Github)`.
- [ ] `_load_secrets`: клон репозитория секретов в `mktemp -d`
  (с `trap rm -rf`), `mkdir -p .secrets` (первый запуск — каталога
  ещё нет), `cp -r "$TEMP_DIR/myworkouts/"* .secrets/`,
  sed-перепись `KEYSTORE_FILE` на относительный
  `.secrets/keystore/$(APP_NAME)-release.keystore`.
- [ ] `_ensure_secrets`: вызов `_load_secrets`, только если нет `.secrets/`.
- [ ] Починка регрессии (риск 1):
  - `build`: `./gradlew assemble$(FLAVOR_TITLE)Debug`;
  - `install`: `./gradlew install$(FLAVOR_TITLE)Debug`;
  - `all`/`check` наследуют автоматически.
- [ ] Новая цель `apk` (FLAVOR-зависимая):
  `_ensure_secrets` → `assemble$(FLAVOR_TITLE)Release` →
  копия `app/build/outputs/apk/$(FLAVOR)/release/app-$(FLAVOR)-release.apk`
  в корень. Имя артефакта: `$(APP_NAME)$(VERSION_CODE).apk`
  (аналог `dayscounter$(VERSION_CODE).apk`; VERSION_CODE/VERSION_NAME
  берутся grep'ом из `gradle.properties`).
- [ ] Обновить `.PHONY` (`apk _ensure_secrets _load_secrets`) и
  help-комментарии `##` для новых целей.

**Критерий завершения:**
- [ ] `make build` снова зелёный (уже как `assembleGithubDebug`).
- [ ] `make help` показывает `apk` с описанием.
- [ ] `make apk FLAVOR=zosja` падает с валидационной ошибкой.

## Этап 3: Полная верификация «ничего не сломалось»

- [ ] `make build` — debug github собирается.
- [ ] `make build FLAVOR=rustore` — заглушка собирается.
- [ ] `make test` — JVM-тесты зелёные (не должны заметить flavors).
- [ ] `make lint` — ktlint/detekt/markdownlint зелёные.
- [ ] `make apk FLAVOR=github` — APK появился в корне;
  проверить подпись: `apksigner verify --print-certs
  $(APP_NAME)$(VERSION_CODE).apk` (сертификат = our release-keystore).
- [ ] `make install` на подключённом эмуляторе (или отметить как
  отложенную проверку, если девайса нет).
- [ ] Удалить `.secrets/` и убедиться, что `make build`/`make test`
  работают без секретов, а `make apk` сам подтягивает их через
  `_ensure_secrets`.
- [ ] Ручной шаг для IDE: в Android Studio выбрать flavor в
  Run Configuration (отметить в описании PR/коммита).
- [ ] Сравнить `git status` — в репо не должны оказаться `.secrets/`,
  `*.apk` (проверка, что `.gitignore` закрыл).

**Критерий завершения:** все команды из baseline зелёные + APK
подписан корректным ключом + секреты не утекли в git.

## Этап 4: Документация и коммит

- [ ] Обновить `AGENTS.md`, раздел Build: упоминание flavors
  (`make build FLAVOR=…`), `make apk`, что по умолчанию `github`;
  поправить строку про `installDebug` → `install$(FLAVOR)Debug`.
- [ ] `make format && make lint && make test` (правило из AGENTS.md
  перед коммитом).
- [ ] Один коммит: этапы 1+2+4 вместе (документация описывает то,
  что уже в коде). Сообщение по навыку `commit`.

---

## Зависимости этапов

```
0 → 1 → 2 → 3 → 4
```

Этапы 1+2+4 — один коммит (см. этап 4); между этапами 1 и 2 сборка
сломана осознанно.
Этап 3 — гейт перед коммитом; при падении любого пункта возврат в
этап 1/2, без «допилим потом».

## Вне скоупа этой задачи

- GitHub Actions / release-автоматизация — не делаем (в JetpackDays
  их тоже нет).
- Публикация в RuStore: `rustore-credentials.json` (нет в
  `android-secrets/myworkouts/`), publish-цели, `bundleRustoreRelease` —
  отдельная задача. План уже существует:
  `docs/plans/rustore-toolkit-extraction.md` в JetpackDays, наш проект
  там Этап 3, триггер — первая публикация после MVP. Наша задача —
  предшественник этого этапа («flavor-разделение — по рецепту»).
- Содержательные различия флейворов (BuildConfig-флаг) — когда
  появятся rustore-фичи.
- Чистка `.gitignore` от рудиментов JetpackDays (fastlane, screenshot-
  tests, Ruby) — косметика, можно отдельным коммитом.
