# Makefile для проекта Jetpack-MyWorkouts

# Цвета и шрифт для вывода в консоль
YELLOW=\033[1;33m
GREEN=\033[1;32m
RED=\033[1;31m
BOLD=\033[1m
RESET=\033[0m

## help: Показать это справочное сообщение
help:
	@echo "Доступные команды Makefile:"
	@echo ""
	@sed -n 's/^##//p' ${MAKEFILE_LIST} | \
	awk -F ':' '{printf "  $(BOLD)%s$(RESET):%s\n", $$1, $$2}' BOLD="$(BOLD)" RESET="$(RESET)" | column -t -s ':'
	@echo ""

# Сборка проекта
## build: Сборка APK для отладки
build:
	./gradlew assembleDebug

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
test:
	$(call run_tests,test,app/build/test-results)

## android-test: Запуск интеграционных тестов на Android устройстве со статистикой в консоли
android-test:
	$(call run_tests,connectedDebugAndroidTest,app/build/outputs/androidTest-results/connected/debug)

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
## install: Установка APK на устройство
install:
	./gradlew installDebug

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

.PHONY: help build clean test lint format check install all android-test setup _check_jdk _check_markdownlint update_readme_versions setup_git_hooks
