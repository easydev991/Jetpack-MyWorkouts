# Эмулятор Android через MCP `mobile-mcp` — проектные правила

Общий workflow (devices → launch → `list_elements_on_screen` → тап по
`@ref`) и список инструментов сервер `mobile-mcp` инжектит сам — здесь
только проектное.

- **Регрессионное UI-тестирование** — канонический путь: `make android-test`
  (Compose UI Tests в `app/src/androidTest/`). MCP — для ad-hoc проверок,
  не для автоматизации.
- **Gradle-сборка и тесты** — через `bash` (`./gradlew ...`, `make ...`), не MCP.
- **После старта эмулятора** — `make emulator-fast` (см.
  `.opencode/rules/test-execution.md`).
-  Если **MCP недоступен** — фоллбек на `adb` через `bash`: проверь `adb devices`;
  если девайс есть, а MCP не отвечает — `adb shell input tap X Y`,
  `adb shell screencap -p /sdcard/...` и т.п. Пустой список — эмулятор не
  запущен; конфиг opencode грузится на старте.
- Установка/удаление APK — `mobile_install_app` / `mobile_uninstall_app`
  (или `bash` + `adb`).

Подробный workflow — навык `mobile-mcp-ui`.
