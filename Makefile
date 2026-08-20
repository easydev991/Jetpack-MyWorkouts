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
## test: Запуск unit-тестов (JVM, без устройства)
test:
	./gradlew test --console=plain

## android-test: Запуск интеграционных тестов на Android устройстве
android-test:
	./gradlew connectedDebugAndroidTest --console=plain

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
