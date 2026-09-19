# android-release-toolkit

Инструменты для релизов моих android-приложений (JetpackDays, MyWorkouts, WorkoutApp).
Подключается в приложение через `git subtree` в `tools/release/`.

## Приложения-потребители

- [JetpackDays](https://github.com/easydev991/JetpackDays) — `com.dayscounter`
- [Jetpack-MyWorkouts](https://github.com/easydev991/Jetpack-MyWorkouts) — `com.myworkouts`
- Jetpack-WorkoutApp — `com.swparks` (подключение запланировано)

## Состав (`scripts/`)

- `rustore_publish.sh` — загрузка AAB в RuStore: auth → draft → upload → commit.
  Использование: `rustore_publish.sh <credentials.json> <app.aab> [priority]`.
  Режимы через `RUSTORE_MODE=all|upload|commit` (`RUSTORE_VID` нужен для commit).
  Требует env `RUSTORE_APP_ID` — guard до сетевых вызовов.
- `_generate_whats_new.sh` — генерирует
  `fastlane/metadata/android/ru-RU/whats_new/<VERSION>.txt` из git log
  (теги формата `1.5` без префикса `v`; нет тегов → «(первый релиз)»).
- `rustore_publish_test.py`, `whats_new_test.py` — тесты.

## Конвенции в приложении-потребителе

- `gradle.properties`: `VERSION_CODE`, `VERSION_NAME` — источник версии.
- Makefile: `RUSTORE_APP_ID ?= <applicationId>` + `export` — единственное
  app-специфичное место; скрипты генерик. Артефакт: `$(APP_NAME){VERSION_CODE}.aab`.
- Секреты: `.secrets/rustore-credentials.json` (поля `key_id` и `client_secret` —
  base64 RSA private key PKCS8), кладётся make-целью `_load_secrets` из
  приватного репозитория android-secrets.

## Запуск тестов

    python3 -m unittest discover -s scripts -p "*_test.py"

## Обновление в приложении (sync-цикл)

    git subtree pull --prefix tools/release https://github.com/easydev991/android-release-toolkit.git v1.0.1 --squash

Копии в `tools/release/` приложений руками не править — только `git subtree pull`.

## Выпуск новой версии тулкита

Правка → тесты → коммит → тег `vX.Y.Z` → `git push origin main --tags` →
`git subtree pull` в приложениях-потребителях.
