# Makefile для проекта Jetpack-MyWorkouts

# Цвета и шрифт для вывода в консоль
YELLOW=\033[1;33m
GREEN=\033[1;32m
RED=\033[1;31m
BOLD=\033[1m
RESET=\033[0m

# Флейвор дистрибуции и секреты (план flavors-implementation.md)
FLAVOR ?= github
FLAVOR_TITLE := $(if $(filter rustore,$(FLAVOR)),Rustore,Github)
ifeq ($(filter rustore github,$(FLAVOR)),)
$(error Unknown FLAVOR=$(FLAVOR). Expected: rustore, github)
endif

SECRETS_REPO = git@github.com:easydev991/android-secrets.git
SECRETS_DIR = myworkouts
APP_NAME ?= myworkouts

## help: Показать это справочное сообщение
help:
	@echo "Доступные команды Makefile:"
	@echo ""
	@sed -n 's/^##//p' ${MAKEFILE_LIST} | \
	awk -F ':' '{printf "  $(BOLD)%s$(RESET):%s\n", $$1, $$2}' BOLD="$(BOLD)" RESET="$(RESET)" | column -t -s ':'
	@echo ""

# Сборка проекта
## build: Сборка debug APK. Использование: make build FLAVOR=rustore (по умолчанию FLAVOR=github)
build: _ensure_secrets
	./gradlew assemble$(FLAVOR_TITLE)Debug

## clean: Очистка кеша проекта
clean:
	./gradlew clean

# Тестирование
# $(1) — gradle-задача, $(2) — директория с XML-результатами
# ponytail: report-скрипт запускается ВСЕГДА — иначе при упавших тестах
# список фейлов пришлось бы выковыривать `grep`'ом из Gradle-вывода.
# Финальный exit = gradle-status (ошибка сборки или тестов), иначе
# report-status (упавшие тесты → sys.exit(1) внутри скрипта).
define run_tests
	@./gradlew $(1) --console=plain; BUILD_STATUS=$$?; \
	echo ""; \
	python3 scripts/test_report.py $(2); REPORT_STATUS=$$?; \
	if [ $$BUILD_STATUS -ne 0 ]; then \
		printf "$(RED)[FAIL] СБОРКА ПРОВАЛИЛАСЬ (exit=$$BUILD_STATUS) — отчёт может быть от предыдущего прогона$(RESET)\n"; \
		exit $$BUILD_STATUS; \
	else exit $$REPORT_STATUS; fi
endef

## test: Запуск unit-тестов (JVM, без устройства) со статистикой в консоли
test: _ensure_secrets
	$(call run_tests,test,app/build/test-results)

## android-test: Запуск интеграционных тестов на Android устройстве со статистикой в консоли. Использование: make android-test FLAVOR=rustore (по умолчанию FLAVOR=github)
android-test: _ensure_secrets
	$(call run_tests,connected$(FLAVOR_TITLE)DebugAndroidTest,app/build/outputs/androidTest-results/connected/debug)

## emulator-fast: Выключить анимации на запущенном эмуляторе (ускоряет androidTest). Применять после каждого старта эмулятора
emulator-fast:
	@adb shell settings put global window_animation_scale 0
	@adb shell settings put global transition_animation_scale 0
	@adb shell settings put global animator_animation_scale 0
	@printf "$(GREEN)Анимации эмулятора выключены (window/transition/animator = 0)$(RESET)\n"

## scripts-test: Запуск unit-тестов Python-скриптов
scripts-test:
	python3 -m unittest discover -s scripts -p "*_test.py"
	python3 -m unittest discover -s tools/release/scripts -p "*_test.py"

# Анализ кода
## lint: Запуск ktlint, detekt и markdownlint (проверка)
lint:
	./gradlew ktlintCheck
	./gradlew app:detekt
	@if command -v markdownlint >/dev/null 2>&1; then \
		markdownlint "**/*.md"; \
	else \
		echo "$(YELLOW)markdownlint-cli не установлен. Для установки: npm install -g markdownlint-cli$(RESET)"; \
	fi

## format: Форматирование кода (ktlint + detekt с исправлениями) и Markdown-файлов
format:
	./gradlew ktlintFormat
	./gradlew app:detekt -Pdetekt.autoCorrect=true
	@if command -v markdownlint >/dev/null 2>&1; then \
		markdownlint --fix "**/*.md"; \
	else \
		echo "$(YELLOW)markdownlint-cli не установлен. Для установки: npm install -g markdownlint-cli$(RESET)"; \
	fi

# Сборка и запуск всех проверок
## check: Полная проверка (сборка + тесты + линтер)
check: build test
	./gradlew ktlintCheck detekt

# Установка приложения
## install: Установка debug APK на устройство. Использование: make install FLAVOR=rustore (по умолчанию FLAVOR=github)
install: _ensure_secrets
	./gradlew install$(FLAVOR_TITLE)Debug

# Релизная сборка и секреты
## _load_secrets: Загрузить секреты из SSH-репозитория во временную директорию
_load_secrets:
	@printf "$(YELLOW)Загрузка секретов из репозитория по SSH...$(RESET)\n"
	@TEMP_DIR=$$(mktemp -d); \
	trap "rm -rf $$TEMP_DIR" EXIT; \
	printf "$(YELLOW)Клонирую репозиторий $(SECRETS_REPO)...$(RESET)\n"; \
	if ! git clone --depth 1 $(SECRETS_REPO) "$$TEMP_DIR" 2>/dev/null; then \
		printf "$(RED)Ошибка: не удалось клонировать репозиторий$(RESET)\n"; \
		printf "$(YELLOW)Проверьте SSH-доступ к GitHub: ssh -T git@github.com$(RESET)\n"; \
		exit 1; \
	fi; \
	mkdir -p .secrets; \
	cp -r "$$TEMP_DIR/$(SECRETS_DIR)/*" .secrets/ 2>/dev/null || cp -r "$$TEMP_DIR/$(SECRETS_DIR)"/* .secrets/; \
	sed -i.tmp 's|^KEYSTORE_FILE=.*|KEYSTORE_FILE=.secrets/keystore/$(APP_NAME)-release.keystore|' .secrets/secrets.properties && rm -f .secrets/secrets.properties.tmp; \
	cp -f .secrets/google-services.json app/google-services.json; \
	printf "$(GREEN)Секреты загружены успешно$(RESET)\n"

## _ensure_secrets: Обёртка над _load_secrets — клонирует секреты, если нет .secrets/ или app/google-services.json
_ensure_secrets:
	@if [ ! -d .secrets ] || [ ! -f app/google-services.json ]; then $(MAKE) _load_secrets; fi

## apk: Создать подписанный release APK (без повышения версии). Использование: make apk FLAVOR=rustore (по умолчанию FLAVOR=github). Файл: $(APP_NAME){VERSION_CODE}.apk в корне репо
apk: _ensure_secrets
	@printf "$(YELLOW)Создаю релизный APK ($(FLAVOR))...$(RESET)\n"
	@./gradlew assemble$(FLAVOR_TITLE)Release --console=plain
	@VERSION_CODE=$$(grep "^VERSION_CODE=" gradle.properties | cut -d'=' -f2); \
	VERSION_NAME=$$(grep "^VERSION_NAME=" gradle.properties | cut -d'=' -f2); \
	OUTPUT_FILE="$(APP_NAME)$$VERSION_CODE.apk"; \
	cp app/build/outputs/apk/$(FLAVOR)/release/app-$(FLAVOR)-release.apk "$$OUTPUT_FILE"; \
	printf "$(GREEN)APK создан: $$OUTPUT_FILE$(RESET)\n"; \
	printf "$(YELLOW)Версия: $$VERSION_NAME (build $$VERSION_CODE)$(RESET)\n"

# Подготовка к публикации
# VERSION_NAME и _WHATS_NEW_FILE используются target-ами whats-new,
# rustore, rustore-draft. Берутся из gradle.properties при каждом запуске.
VERSION_NAME := $(shell grep '^VERSION_NAME=' gradle.properties | cut -d= -f2)
_WHATS_NEW_FILE := fastlane/metadata/android/ru-RU/whats_new/$(VERSION_NAME).txt

# App-специфичные значения публикации: единственное место в репо с
# идентификатором приложения — скрипты в tools/release (тулкит
# android-release-toolkit) генерик. RUSTORE_APP_ID экспортируется: без export
# make-переменная не попадает в env рецепта, и скрипт упадёт на guard.
RUSTORE_APP_ID ?= com.myworkouts
export RUSTORE_APP_ID

# Общий prerequisite для rustore и rustore-draft: инкремент VERSION_CODE,
# build AAB, upload Crashlytics mapping, копирование в корень.
_rustore_build_aab: _ensure_secrets
	@printf "$(YELLOW)Увеличиваю VERSION_CODE...$(RESET)\n"
	@CURRENT_VERSION_CODE=$$(grep "^VERSION_CODE=" gradle.properties | cut -d'=' -f2); \
	NEW_VERSION_CODE=$$((CURRENT_VERSION_CODE + 1)); \
	sed -i.tmp "s/^VERSION_CODE=.*/VERSION_CODE=$$NEW_VERSION_CODE/" gradle.properties && rm -f gradle.properties.tmp; \
	printf "$(GREEN)VERSION_CODE обновлен с $$CURRENT_VERSION_CODE на $$NEW_VERSION_CODE$(RESET)\n"
	@printf "$(YELLOW)Создаю релиз-сборку (AAB)...$(RESET)\n"
	@./gradlew bundleRustoreRelease uploadCrashlyticsMappingFileRustoreRelease --console=plain
	@VERSION_CODE=$$(grep "^VERSION_CODE=" gradle.properties | cut -d'=' -f2); \
	OUTPUT_FILE="$(APP_NAME)$$VERSION_CODE.aab"; \
	cp app/build/outputs/bundle/rustoreRelease/app-rustore-release.aab "$$OUTPUT_FILE"; \
	printf "$(GREEN)AAB создан и mapping files загружены в Firebase: $$OUTPUT_FILE$(RESET)\n"
	@printf "$(YELLOW)Версия для публикации: $$(grep "^VERSION_NAME=" gradle.properties | cut -d'=' -f2)$(RESET)\n"

## whats-new: Сгенерировать fastlane/metadata/android/ru-RU/whats_new/<VERSION>.txt из git log
##   Если файл уже существует — печатает содержимое и НЕ перезаписывает.
##   Используется автоматически как prerequisite для rustore/rustore-draft.
whats-new:
	@./tools/release/scripts/_generate_whats_new.sh "$(VERSION_NAME)" "$(_WHATS_NEW_FILE)"

## rustore: Собрать AAB и опубликовать в RuStore (все 4 шага: auth → draft → upload → commit). Файл: $(APP_NAME){VERSION_CODE}.aab
##   SKIP_PUBLISH=1 — собрать AAB без загрузки в RuStore (escape hatch для локальной отладки)
rustore: _rustore_build_aab whats-new
	@VERSION_CODE=$$(grep "^VERSION_CODE=" gradle.properties | cut -d'=' -f2); \
	OUTPUT_FILE="$(APP_NAME)$$VERSION_CODE.aab"; \
	if [ -z "$(SKIP_PUBLISH)" ]; then \
		printf "$(YELLOW)Загружаю в RuStore...$(RESET)\n"; \
		./tools/release/scripts/rustore_publish.sh .secrets/rustore-credentials.json "$$OUTPUT_FILE"; \
	else \
		printf "$(YELLOW)SKIP_PUBLISH=1 — пропускаю загрузку в RuStore. AAB готов: $$OUTPUT_FILE$(RESET)\n"; \
	fi

## rustore-draft: Собрать AAB и создать черновик в RuStore (без отправки на модерацию).
##   Используется для двухшагового workflow: draft → проверка в Console → make rustore-commit VID=<vid>.
rustore-draft: _rustore_build_aab whats-new
	@VERSION_CODE=$$(grep "^VERSION_CODE=" gradle.properties | cut -d'=' -f2); \
	OUTPUT_FILE="$(APP_NAME)$$VERSION_CODE.aab"; \
	printf "$(YELLOW)Загружаю в RuStore (auth → draft → upload, без commit)...$(RESET)\n"; \
	RUSTORE_MODE=upload ./tools/release/scripts/rustore_publish.sh .secrets/rustore-credentials.json "$$OUTPUT_FILE"; \
	VID=$$(cat .secrets/.last_rustore_vid 2>/dev/null); \
	printf "\n$(GREEN)Черновик создан в RuStore Console: VID=%s$(RESET)\n" "$$VID"; \
	printf "Следующий шаг: $(YELLOW)make rustore-commit VID=%s$(RESET)\n" "$$VID"

## rustore-commit: Отправить черновик VID=<versionId> на модерацию.
##   Использование: make rustore-commit VID=12345
rustore-commit:
	@if [ -z "$(VID)" ]; then \
		echo "usage: make rustore-commit VID=<versionId>" >&2; \
		echo "Найдите versionId в RuStore Console → Черновики." >&2; \
		exit 1; \
	fi
	@printf "$(YELLOW)Отправляю черновик VID=$(VID) на модерацию...$(RESET)\n"
	RUSTORE_MODE=commit RUSTORE_VID=$(VID) ./tools/release/scripts/rustore_publish.sh \
		.secrets/rustore-credentials.json ""

# Настройка окружения
## setup: Установка и настройка инструментов для локальной разработки (JDK, markdownlint-cli, git hooks)
setup:
	@$(MAKE) _check_jdk
	@$(MAKE) _check_markdownlint
	@$(MAKE) setup_git_hooks

## _check_jdk: Проверка JDK 17 (требование сборки и detekt)
_check_jdk:
	@printf "\n"
	@printf "$(YELLOW)Проверка JDK 17...$(RESET)\n"
	@if [ -n "$$JAVA_HOME" ] && [ -x "$$JAVA_HOME/bin/java" ]; then \
		JAVA_BIN="$$JAVA_HOME/bin/java"; \
	else \
		JAVA_BIN="java"; \
	fi; \
	JAVA_VERSION=$$($$JAVA_BIN -version 2>&1 | head -n 1 | sed -E 's/.*"([0-9]+).*/\1/'); \
	if [ "$$JAVA_VERSION" = "17" ]; then \
		printf "$(GREEN)JDK 17 найден: $$JAVA_BIN$(RESET)\n"; \
	else \
		printf "$(RED)Ошибка: требуется JDK 17, найдена версия '$${JAVA_VERSION:-неизвестно}' ($$JAVA_BIN)$(RESET)\n"; \
		printf "$(YELLOW)Проверьте JAVA_HOME: $$JAVA_HOME$(RESET)\n"; \
		exit 1; \
	fi

## _check_markdownlint: Проверка наличия markdownlint-cli
_check_markdownlint:
	@printf "\n"
	@printf "$(YELLOW)Проверка markdownlint-cli...$(RESET)\n"
	@if ! command -v npm >/dev/null 2>&1; then \
		printf "$(RED)Ошибка: Node.js/npm не установлен. Установите Node.js с https://nodejs.org/$(RESET)\n"; \
		exit 1; \
	fi
	@if ! command -v markdownlint >/dev/null 2>&1; then \
		printf "Установка markdownlint-cli...\n"; \
		npm install -g markdownlint-cli; \
	else \
		printf "$(GREEN)markdownlint-cli уже установлен$(RESET)\n"; \
	fi

# Документация
## update_readme_versions: Обновить бейджи версий в README.md (Kotlin, Android SDK, Gradle, AGP)
update_readme_versions:
	@printf "$(YELLOW)Обновляю версии в README.md...$(RESET)\n"
	./gradlew updateReadmeVersions --no-configuration-cache --quiet
	@printf "$(GREEN)Версии обновлены успешно$(RESET)\n"

## setup_git_hooks: Настроить Git hooks для автоматического обновления README.md
setup_git_hooks:
	@printf "$(YELLOW)Настройка Git hooks...$(RESET)\n"
	@if [ ! -f ".githooks/pre-commit" ]; then \
		printf "$(RED)Файл .githooks/pre-commit не найден$(RESET)\n"; \
		exit 1; \
	fi
	@chmod +x .githooks/pre-commit
	@git config core.hooksPath .githooks
	@printf "$(GREEN)Git hooks настроены: core.hooksPath = .githooks$(RESET)\n"
	@printf "$(YELLOW)Теперь pre-commit хук будет автоматически обновлять версии в README.md$(RESET)\n"

# Запуск всех задач
## all: Полная проверка (сборка + тесты + линтер) и установка APK на устройство
all: check install

.PHONY: help build clean test scripts-test lint format check install all android-test emulator-fast setup _check_jdk _check_markdownlint update_readme_versions setup_git_hooks apk _ensure_secrets _load_secrets rustore rustore-draft rustore-commit whats-new _rustore_build_aab
