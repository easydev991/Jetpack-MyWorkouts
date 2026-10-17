#!/usr/bin/env bash
# Синхронизирует тулкит в локальные приложения-потребители через git subtree pull.
#
# Использование:
#   scripts/sync_consumers.sh [tag]
#
# Аргументы:
#   tag — тег тулкита (по умолчанию — последний по версии).
#
# Потребители — соседние с тулкитом директории, содержащие tools/release
# (см. README «Приложения-потребители»). Директории без subtree пропускаются.
#
# Env-переопределения (для тестов):
#   CONSUMERS_DIR — где искать приложения (по умолчанию "..")
#   TOOLKIT_URL   — откуда тянуть (по умолчанию GitHub)
#
# Зависимости: bash, git. cwd: корень репозитория тулкита.
set -euo pipefail

TAG="${1:-$(git tag --sort=-v:refname | head -1)}"
URL="${TOOLKIT_URL:-https://github.com/easydev991/android-release-toolkit.git}"
CONSUMERS_DIR="${CONSUMERS_DIR:-..}"

if [[ -z "$TAG" ]]; then
	echo "Не найден ни один тег — укажите аргументом: sync_consumers.sh <tag>" >&2
	exit 1
fi

echo "Синхронизирую тег $TAG из $URL"
failures=0
for subtree in "$CONSUMERS_DIR"/*/tools/release; do
	[[ -d "$subtree" ]] || continue
	app="$(basename "$(dirname "$(dirname "$subtree")")")"
	echo "— $app: git subtree pull --prefix tools/release $TAG --squash"
	# GIT_MERGE_AUTOEDIT=no: merge внутри subtree не должен открывать редактор
	# (в интерактивном терминале без рабочей $EDITOR подвешивает merge)
	if GIT_MERGE_AUTOEDIT=no git -C "$CONSUMERS_DIR/$app" subtree pull --prefix tools/release "$URL" "$TAG" --squash; then
		echo "— $app: обновлён"
	else
		echo "— $app: ОШИБКА синка" >&2
		failures=$((failures + 1))
	fi
done

if [[ "$failures" -gt 0 ]]; then
	echo "Готово с ошибками: $failures" >&2
	exit 1
fi
echo "Готово"
