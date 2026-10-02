# Manual verification — то, что UI-тесты не покрывают

Ряд пользовательских сценариев **нельзя** проверить инструментированными
тестами (`androidTest`); единственный способ — ручная проверка на эмуляторе
или устройстве через MCP `mobile-mcp` (или fallback на `adb`).

## Что НЕ покрывается UI-тестами

- **Позиция `DropdownMenu` / `BottomSheet` / `Popup` относительно точки
  тача.** `offset` меню не имеет семантики точной привязки в Compose UI
  Test — `assertIsDisplayed()` покажет только, что нода в дереве.
  Визуальная проверка — только screenshot.
- **Визуальная раскладка и темизация**: edge-to-edge insets, системные
  бары, динамические цвета, шрифты при разных настройках устройства.
- **Поведение системных бар и жестов**: навигация жестами, IME/keyboard,
  back press, перекрытие экранной клавиатурой.
- **Реальные интеграции с системой**: `AlarmManager`, уведомления в
  шторке, broadcast'ы другим приложениям, deeplink'и.

## Когда ручная проверка обязательна

Пункты плановых артефактов (`openspec/changes/<change>/tasks.md`
или аналогов в проекте) с фразами-маркерами «ручная проверка»,
«на эмуляторе», «визуальная проверка», «в UI Test не покрывается» —
закрываются через MCP `mobile-mcp`, не тестом.

Пример из практики (для ориентира, привязка к проекту
необязательна) — workout-трекер Jetpack-MyWorkouts, задача
workout-entries-list 5.5: «сохранённая запись видна первой,
long-press → меню в точке нажатия → удаление с алертом, пустое
состояние при отсутствии записей». Точка «long-press в точке
нажатия» не проверяется UI-тестом (см.
`references/component-testing.md` → Long-press) — закрывалась
на эмуляторе.

## MCP `mobile-mcp` — шпаргалка

| Инструмент | Назначение |
|---|---|
| `mobile_list_available_devices` | Эмулятор онлайн — взять `id` со статусом `online`; если пусто — fallback на `adb devices` |
| `mobile_install_app` | Поставить APK |
| `mobile_launch_app` | Запустить приложение по `packageName` |
| `mobile_list_elements_on_screen` | Элементы экрана с `@ref`, координатами и label |
| `mobile_click_on_screen_at_coordinates` | Тап по `@ref` (предпочтительно) или координатам |
| `mobile_long_press_on_screen_at_coordinates` | Long-press |
| `mobile_type_keys` | Ввод текста в фокус |
| `mobile_swipe_on_screen` / `mobile_press_button` | Жесты и системные кнопки (BACK, HOME, ENTER) |
| `mobile_take_screenshot` | Быстрая оценка вида (снимок в чат) |
| `mobile_save_screenshot` | Снимок в файл — затем `Read` файла |
| `mobile_batch_commands` | Последовательность тап/тип/тап одним вызовом |
| `mobile_get_device_logs` | logcat: краши и исключения (фильтруй по `process`) |
| `mobile_get_foreground_app` | Какое приложение на переднем плане |

Правила (из `android-emulator.md`):
1. Сначала элементы (`mobile_list_elements_on_screen`) — потом действия; тап по `@ref`, не по голым координатам.
2. Refs валидны до навигации — после перехода на другой экран перечитай элементы.
3. Последовательности (тип/тап/тап) — одним `mobile_batch_commands`; скриншоты шагов — `mobile_save_screenshot` в отдельные файлы.
4. Если MCP недоступен — `adb shell input tap X Y`, `adb shell screencap
   -p /sdcard/...`, вытянуть через `adb pull`.

## Минимальная последовательность для проверки сценария

1. Сборка + установка: `make install` (APK на подключённом эмуляторе).
2. Открыть app: `mobile_list_available_devices` → `mobile_launch_app`
   (packageName из `app/build.gradle.kts`).
3. По сценарию: `mobile_list_elements_on_screen` → действия
   (`mobile_click_on_screen_at_coordinates` /
   `mobile_long_press_on_screen_at_coordinates` / `mobile_type_keys`),
   между ними `mobile_take_screenshot`, чтобы увидеть состояние.
4. Сравнить результат с ожиданием из задачи.
5. Скриншоты шагов — `mobile_save_screenshot` в отдельные файлы
   (`001_*.png`, `002_*.png`, ...).

## Частые сценарии в проекте

- **long-press + DropdownMenu**: установить, открыть app, добавить
  запись (или через БД/репозиторий), `mobile_long_press_on_screen_at_coordinates`
  на строке, screenshot — убедиться, что меню открылось **в точке
  тача** (не в дефолтном месте под строкой).
- **алерт подтверждения**: продолжение предыдущего — тап по пункту
  меню, screenshot алерта, тап по «Отмена» и проверка, что алерт
  закрыт; повтор — тап по «Удалить» и проверка, что состояние
  изменилось (запись исчезла).
- **edge-to-edge/insets**: `mobile_set_orientation` landscape/portrait,
  screenshot в обеих ориентациях, проверить, что системные бары не
  перекрывают контент.
- **deferred-эффекты (alarm, notification)**: `mobile_get_device_logs`
  + проверка реальных эффектов в системе (shell-команды `adb`, напр.
  `dumpsys notification`).
