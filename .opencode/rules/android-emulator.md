# Правила работы с эмулятором Android через MCP

## Когда использовать

- **Ручная проверка UI** на устройстве/эмуляторе — нужен снимок экрана, чтобы увидеть, что отрисовалось
- **Симуляция пользовательского ввода** — тапы, свайпы, ввод текста, нажатия клавиш
- **Запуск/проверка приложения** — `open_app` по packageName, `list_apps` для поиска
- **Исследование UI-иерархии** — `capture_ui_dump` для нахождения элементов по тексту/contentDescription

## Когда НЕ использовать

- **Регрессионное UI-тестирование** — канонический путь: `make android-test` (Compose UI Tests в `app/src/androidTest/`). MCP — для ad-hoc проверок, не для автоматизации.
- **Запуск Gradle-сборки или тестов** — для этого `bash` (`./gradlew ...`, `make ...`).
- **Простой дебаг без UI** — обычные инструменты, не эмулятор.

## Доступные инструменты

| Инструмент | Назначение |
|---|---|
| `android-debug-bridge_capture_screenshot` | Снимок экрана |
| `android-debug-bridge_capture_ui_dump` | XML-дамп UI-иерархии |
| `android-debug-bridge_list_apps` | Список установленных приложений |
| `android-debug-bridge_open_app` | Запуск приложения по packageName |
| `android-debug-bridge_input_tap` | Тап по координатам `(x, y)` |
| `android-debug-bridge_input_text` | Ввод текста |
| `android-debug-bridge_input_scroll` | Свайп (up/down/left/right) |
| `android-debug-bridge_input_keyevent` | Аппаратные клавиши (BACK, HOME, ENTER, DELETE) |
| `android-debug-bridge_create_test_folder` | Группировка скриншотов в папку сценария |

## Правила работы

1. **Сначала скриншот — потом действия.** Перед тапом или вводом — `capture_screenshot`, чтобы понять текущее состояние экрана.
2. **Координаты из скриншота, не из головы.** Тап по `(x, y)` — координаты берутся из последнего снимка, а не угадываются.
3. **Группируй сценарии через `create_test_folder`.** Для длинных проверок (onboarding, multi-step flows) — `test_name` + `step_name`, чтобы скриншоты не сваливались в общую кучу.
4. **Если MCP недоступен — фоллбек на `adb` через `bash`.** Проверь `adb devices`. Если девайс есть, а MCP не отвечает: `adb shell input tap X Y`, `adb shell screencap -p /sdcard/...` и т.п.
5. **Не модифицируй файлы эмулятора.** MCP читает (`capture_*`, `list_apps`, `capture_ui_dump`) и пишет только ввод (`input_*`, `open_app`). Установка/удаление APK — через `bash` + `adb`.

## Проверка

Убедись, что MCP подключён: `list_apps` возвращает непустой список. Если пустой — проверь `adb devices`, перезапусти opencode (конфиг грузится на старте).