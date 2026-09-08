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
define run_tests
	@./gradlew $(1) --console=plain; BUILD_STATUS=$$?; \
	if [ $$BUILD_STATUS -ne 0 ]; then \
		echo ""; \
		echo "================================================================================"; \
		printf "$(RED)[FAIL] СБОРКА ПРОВАЛИЛАСЬ С ОШИБКАМИ$(RESET)\n"; \
		echo "================================================================================"; \
		exit 1; \
	else \
		echo ""; \
		python3 scripts/test_report.py $(2); \
	fi
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

# Запуск всех задач
## all: Полная проверка (сборка + тесты + линтер) и установка APK на устройство
all: check install

.PHONY: help build clean test lint format check install all android-test
