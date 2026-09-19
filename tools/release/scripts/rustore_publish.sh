#!/usr/bin/env bash
# Загружает AAB в RuStore: auth → create draft → upload → submit.
#
# Использование:
#   scripts/rustore_publish.sh <credentials.json> <app.aab> [priority]
#
# Аргументы:
#   credentials.json  — JSON с полями key_id и client_secret (base64 RSA private key, PKCS8)
#   app.aab           — путь к AAB-файлу (не требуется в режиом RUSTORE_MODE=commit)
#   priority          — 0..5 (по умолчанию 0); обычно 0, для критических багфиксов вручную 5
#
# Окружение:
#   RUSTORE_MODE ∈ {all, upload, commit} (по умолчанию all):
#     all    — все 4 шага (auth → create-draft → upload AAB → commit).
#     upload — шаги 1–3 (auth → create-draft → upload AAB). Без commit.
#              Используется `make rustore-draft` — черновик создаётся, на модерацию
#              отправляется вручную через `make rustore-commit VID=<vid>`.
#     commit — только шаг 4 (commit). требует RUSTORE_VID.
#              Используется `make rustore-commit VID=<vid>`.
#   RUSTORE_VID — versionId черновика, обязателен в режиом RUSTORE_MODE=commit.
#   cwd должен быть корнем проекта (для чтения gradle.properties и whats_new/<VERSION>.txt)
#
# Зависимости: openssl, curl, jq (есть в macOS и стандартных CI-образах).
#
# При сбое между шагами 2 и 4 в RuStore Console останется черновик без AAB.
# Очистка: DELETE /public/v1/application/$APP_ID/version/{vid} или через Console.
set -euo pipefail
set +x # защита от случайного bash -x — иначе credentials утекут в логи
umask 077

RUSTORE_MODE="${RUSTORE_MODE:-all}"
case "$RUSTORE_MODE" in
all | upload | commit) ;;
*)
	echo "Неизвестный RUSTORE_MODE: $RUSTORE_MODE (ожидается all, upload, commit)" >&2
	exit 1
	;;
esac

# Guard до сети: тулкит генерик и не знает идентификаторы приложений;
# ${VAR:?} печатает сообщение в stderr и завершает скрипт с кодом 1.
APP_ID="${RUSTORE_APP_ID:?RUSTORE_APP_ID не задан. Укажите package name: RUSTORE_APP_ID ?= <package.id> в Makefile (постоянно) или make rustore RUSTORE_APP_ID=<id> (разово)}"

CRED_FILE="${1:?usage: rustore_publish.sh <credentials.json> <app.aab> [priority]}"
PRIORITY="${3:-0}"

# AAB обязателен только в режиом all/upload. В commit не используется.
if [[ "$RUSTORE_MODE" != "commit" ]]; then
	AAB_FILE="${2:?usage: rustore_publish.sh <credentials.json> <app.aab> [priority]}"
fi

# В режиом commit требуется RUSTORE_VID — иначе скрипт падает до curl.
if [[ "$RUSTORE_MODE" == "commit" ]]; then
	: "${RUSTORE_VID:?RUSTORE_VID required for RUSTORE_MODE=commit}"
fi

BASE="https://public-api.rustore.ru"
LAST_VID_FILE=".secrets/.last_rustore_vid"

# ---- Проверка зависимостей ----
for cmd in openssl curl jq; do
	command -v "$cmd" >/dev/null 2>&1 || {
		echo "Не найдено: $cmd. Установите и повторите." >&2
		exit 1
	}
done

# ---- Проверка входных файлов ----
[[ -f "$CRED_FILE" ]] || {
	echo "Файл credentials не найден: $CRED_FILE" >&2
	exit 1
}
# AAB и whats_new нужны только в режиом all/upload (для создания черновика).
# В commit режиом мы отправляем на модерацию уже созданный черновик.
if [[ "$RUSTORE_MODE" != "commit" ]]; then
	[[ -f "$AAB_FILE" ]] || {
		echo "Файл AAB не найден: $AAB_FILE" >&2
		exit 1
	}

	VERSION_NAME=$(grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)
	[[ -n "$VERSION_NAME" ]] || {
		echo "VERSION_NAME не найден в gradle.properties" >&2
		exit 1
	}

	WHATS_NEW_FILE="fastlane/metadata/android/ru-RU/whats_new/$VERSION_NAME.txt"
	[[ -f "$WHATS_NEW_FILE" ]] || {
		echo "Файл release notes не найден: $WHATS_NEW_FILE" >&2
		exit 1
	}
fi

# ---- Шаг 1: Авторизация ----
KEY_ID=$(jq -r .key_id "$CRED_FILE")
CLIENT_SECRET=$(jq -r .client_secret "$CRED_FILE")
TIMESTAMP=$(date -u +"%Y-%m-%dT%H:%M:%S+00:00")
MESSAGE="${KEY_ID}${TIMESTAMP}"
SIGNATURE=$(printf '%s' "$MESSAGE" |
	openssl dgst -sha512 -sign <(printf '%s' "$CLIENT_SECRET" | base64 -d) -binary |
	base64 -w0)

JWE=$(curl -fsS -X POST "$BASE/public/auth/" \
	-H "Content-Type: application/json" \
	-d "$(jq -n --arg k "$KEY_ID" --arg t "$TIMESTAMP" --arg s "$SIGNATURE" \
		'{keyId:$k, timestamp:$t, signature:$s}')" |
	jq -r '.body.jwe // empty')
[[ -n "$JWE" ]] || {
	echo "Не получен JWE-токен" >&2
	exit 1
}

# ---- Проверка VERSION_CODE vs существующих версий (all/upload) ----
# Без этой проверки RuStore принимает create-draft, но upload AAB падает с HTTP 400
# если versionCode не выше уже существующего (на модерации/опубликованного). Черновик
# остаётся в Console без файла — выглядит как «AAB загружен», но фактически нет.
if [[ "$RUSTORE_MODE" != "commit" ]]; then
	VERSION_CODE=$(grep '^VERSION_CODE=' gradle.properties | cut -d= -f2)
	[[ -n "$VERSION_CODE" ]] || {
		echo "VERSION_CODE не найден в gradle.properties" >&2
		exit 1
	}

	LIST_RESP=$(curl -fsS -G "$BASE/public/v1/application/$APP_ID/version" \
		-H "Public-Token: $JWE" \
		--data-urlencode "page=0" --data-urlencode "size=100")
	MAX_CODE=$(echo "$LIST_RESP" | jq -r '[.body.content[]?.versionCode] | max // 0')

	if [[ "$VERSION_CODE" -le "$MAX_CODE" ]]; then
		echo "VERSION_CODE=$VERSION_CODE не выше максимального в RuStore (max=$MAX_CODE)." >&2
		echo "Поднимите VERSION_CODE в gradle.properties минимум до $((MAX_CODE + 1))," >&2
		echo "иначе RuStore примет create-draft, но отклонит upload AAB с HTTP 400." >&2
		exit 1
	fi
fi

# ---- Шаги 2-3: Создание черновика + загрузка AAB (только all/upload) ----
if [[ "$RUSTORE_MODE" != "commit" ]]; then
	WHAT_NEW=$(jq -Rs . <"$WHATS_NEW_FILE")
	RESP=$(curl -fsS -X POST "$BASE/public/v1/application/$APP_ID/version" \
		-H "Content-Type: application/json" -H "Public-Token: $JWE" \
		-d "$(jq -n --argjson wn "$WHAT_NEW" \
			'{whatsNew:$wn, publishType:"MANUAL", appType:"MAIN"}')")

	VID=$(echo "$RESP" | jq -r '.body // empty')
	[[ -n "$VID" && "$VID" != "null" ]] || {
		echo "Не удалось создать черновик: $RESP" >&2
		exit 1
	}
	echo "Создан черновик versionId=$VID"
	# VID сохраняется в файл: Makefile читает его и подставляет в подсказку
	# про make rustore-commit VID=<vid> (раньше выводился VID скриптом,
	# но Makefile его не видел и просил «Найдите versionId в Console»).
	mkdir -p "$(dirname "$LAST_VID_FILE")"
	printf '%s' "$VID" >"$LAST_VID_FILE"

	# Upload AAB: тело ответа печатается для диагностики (раньше `> /dev/null`
	# гасил всё — если Console не показывал файл, понять причину было нельзя).
	# --max-time 600 — защита от зависания на больших AAB.
	# -# — прогресс-бар в stderr вместо ранее существовавшего polling-цикла в Makefile
	# (видно, что загрузка идёт; сам curl молчит на stdout, поэтому не мешает парсеру $AAB_RESP).
	# type=application/octet-stream — без явного Content-Type RuStore интерпретирует
	# файл как APK (загружает в «главный APK» слот, commit падает с
	# «There can be only one main APK file», в Console не виден как AAB).
	AAB_RESP=$(curl -f#S --max-time 600 -X POST "$BASE/public/v1/application/$APP_ID/version/$VID/aab" \
		-H "Public-Token: $JWE" -F "file=@$AAB_FILE;type=application/octet-stream")
	echo "AAB загружен: $AAB_FILE (ответ: $AAB_RESP)"
fi

# ---- Шаг 4: Отправка на модерацию (all/commit) ----
if [[ "$RUSTORE_MODE" != "upload" ]]; then
	# В commit режиом берём VID из env; в all — из шага 2 (всегда задан, см. выше).
	if [[ "$RUSTORE_MODE" == "commit" ]]; then
		VID="$RUSTORE_VID"
	fi
	curl -fsS -X POST "$BASE/public/v1/application/$APP_ID/version/$VID/commit?priorityUpdate=$PRIORITY" \
		-H "Public-Token: $JWE" >/dev/null
	echo "Отправлено на модерацию (priorityUpdate=$PRIORITY)"
	echo "Готово. Версия появится в RuStore Console в статусе «На модерации»."
fi
