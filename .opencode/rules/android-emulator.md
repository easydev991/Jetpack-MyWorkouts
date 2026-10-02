# Правила работы с эмулятором Android через MCP `mobile-mcp`

## Когда использовать

- **Ручная проверка UI** на устройстве/эмуляторе — нужен снимок экрана, чтобы увидеть, что отрисовалось
- **Симуляция пользовательского ввода** — тапы, свайпы, ввод текста, нажатия клавиш
- **Запуск/проверка приложения** — `mobile_launch_app` по packageName, `mobile_list_apps` для поиска
- **Исследование UI-иерархии** — `mobile_list_elements_on_screen` для нахождения элементов по label с `@ref`

## Когда НЕ использовать

- **Регрессионное UI-тестирование** — канонический путь: `make android-test` (Compose UI Tests в `app/src/androidTest/`). MCP — для ad-hoc проверок, не для автоматизации.
- **Запуск Gradle-сборки или тестов** — для этого `bash` (`./gradlew ...`, `make ...`).
- **Простой дебаг без UI** — обычные инструменты, не эмулятор.

## Доступные инструменты

| Инструмент | Назначение |
|---|---|
| `mobile_list_available_devices` | Список устройств; взять `id` в состоянии `online` |
| `mobile_list_apps` | Список установленных приложений |
| `mobile_install_app` | Установка APK |
| `mobile_launch_app` | Запуск приложения по packageName |
| `mobile_list_elements_on_screen` | Элементы экрана: `@ref`, координаты, label |
| `mobile_click_on_screen_at_coordinates` | Тап по `@ref` (предпочтительно) или `(x, y)` |
| `mobile_long_press_on_screen_at_coordinates` | Long-press |
| `mobile_type_keys` | Ввод текста в фокус |
| `mobile_swipe_on_screen` | Свайп (up/down/left/right) |
| `mobile_press_button` | Системные кнопки (BACK, HOME, ENTER, ...) |
| `mobile_take_screenshot` / `mobile_save_screenshot` | Снимок экрана (в чат / в файл) |
| `mobile_batch_commands` | Последовательность тап/тип/тап одним вызовом |
| `mobile_get_device_logs` | logcat (фильтруй по `process`) |
| `mobile_get_foreground_app` | Приложение на переднем плане |

## Правила работы

1. **Workflow**: `mobile_list_available_devices` → `mobile_launch_app` (packageName из `app/build.gradle.kts`) → `mobile_list_elements_on_screen` → `mobile_click_on_screen_at_coordinates` по `@ref`.
2. **Тап по `@ref`, не по голым координатам** — ref переживает смену координат; refs валидны до навигации — после перехода на другой экран перечитай элементы.
3. **Скриншот — для вида, элементы — для действий.** `mobile_take_screenshot` — быстрая оценка; `mobile_save_screenshot` в файл + `Read` файла — для артефактов.
4. **Последовательности (тип/тап/тап) — одним `mobile_batch_commands`.**
5. **Если MCP недоступен — фоллбек на `adb` через `bash`.** Проверь `adb devices`. Если девайс есть, а MCP не отвечает: `adb shell input tap X Y`, `adb shell screencap -p /sdcard/...` и т.п.
6. **Не модифицируй файлы эмулятора.** MCP читает (`list_elements_on_screen`, `list_apps`, `take_screenshot`) и пишет только ввод (`click`, `type_keys`, `swipe`). Установка/удаление APK — `mobile_install_app` / `mobile_uninstall_app` (или `bash` + `adb`).

## Проверка

Убедись, что MCP подключён: `mobile_list_available_devices` возвращает непустой список. Если пустой — проверь `adb devices`, перезапусти opencode (конфиг грузится на старте). Подробный workflow — навык `mobile-mcp-ui`.
