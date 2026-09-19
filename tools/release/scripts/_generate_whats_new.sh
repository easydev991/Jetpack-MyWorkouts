#!/usr/bin/env bash
# Генерирует whats_new/<VERSION>.txt из заголовков коммитов.
#
# Использование:
#   scripts/_generate_whats_new.sh <version_name> <output_file>
#
# Аргументы:
#   version_name  — значение VERSION_NAME из gradle.properties (например, "1.13.3")
#   output_file   — путь к файлу whats_new/<version_name>.txt
#
# Поведение:
#   - Если <output_file> уже существует: печатает "Файл уже существует: <path>"
#     и содержимое файла в stdout. Ничего не перезаписывает.
#   - Если файла нет: создаёт с заголовком "Что нового в <version_name>"
#     и буллетами из `git log <last_release_tag>..HEAD --pretty=format:"- %s"`.
#   - Если релизных тегов нет: пишет пометку "(первый релиз)".
#
# Зависимости: bash, git, mkdir, cat. Никаких внешних утилит.
#
# cwd: должен быть корнем git-репозитория (нужен для `git tag` и `git log`).
set -euo pipefail

VERSION_NAME="${1:?usage: _generate_whats_new.sh <version_name> <output_file>}"
OUTPUT_FILE="${2:?usage: _generate_whats_new.sh <version_name> <output_file>}"

if [[ -f "$OUTPUT_FILE" ]]; then
	echo "Файл уже существует: $OUTPUT_FILE"
	echo "---"
	cat "$OUTPUT_FILE"
	# trailing newline: cat не добавляет его если файл заканчивается без \n —
	# иначе следующий stdout (от Makefile) склеится с последней строкой файла.
	echo ""
	exit 0
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"

# Последний релизный тег: в репо формат "1.5"–"1.9" без префикса "v".
# grep -E '^[0-9]' исключает v-prefixed теги если такие появятся в будущем.
LAST_TAG=$(git tag --sort=-version:refname 2>/dev/null | grep -E '^[0-9]' | head -1 || true)

{
	echo "Что нового в $VERSION_NAME"
	echo ""
	if [[ -n "$LAST_TAG" ]]; then
		git log "$LAST_TAG"..HEAD --pretty=format:"- %s" 2>/dev/null || echo "- (пустой git log)"
	else
		echo "- (первый релиз)"
	fi
	# trailing newline: git log --format не добавляет \n после последнего коммита —
	# без этого последняя буллет-точка приклеивается к следующему stdout.
	echo ""
} >"$OUTPUT_FILE"

echo "Создан: $OUTPUT_FILE"
