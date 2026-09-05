# Running tests — запуск и отчётность

Как запускаются инструментированные тесты Jetpack-MyWorkouts и как читать
результаты.

## Запуск

```bash
make android-test    # все androidTest на подключённом устройстве/эмуляторе
```

Команда выполняет:

```makefile
./gradlew connectedDebugAndroidTest --console=plain
ANDROID_TEST_GRADLE_EXIT_CODE=$$? python3 scripts/android_test_report.py
```

- `connectedDebugAndroidTest` — инструментированные тесты в debug-сборке
- Требуется подключённое устройство или запущенный эмулятор

## Отчёт

`scripts/android_test_report.py` — человекочитаемый отчёт:

- Exit code 0 — все тесты прошли
- Exit code 1 — упавшие тесты или ошибка Gradle
  (`sys.exit(1)` при `failed > 0` или `GRADLE_EXIT_CODE != 0`)

Парсинг XML из `app/build/outputs/androidTest-results/connected/debug/`
(и других возможных путей, включая
`app/build/reports/androidTests/connected/debug/results`).

HTML-отчёт в браузере:

```bash
make android-test-report    # открывает index.html отчёта
```

## Все тесты сразу

В `Makefile` есть отдельные цели для unit и android-тестов:

```bash
make test           # JVM unit-тесты (./gradlew test --console=plain)
make android-test   # androidTest на подключённом устройстве
```

Результаты:
- Unit: `app/build/test-results/`
- Интеграционные: `app/build/reports/androidTests/connected/debug/index.html`

## screenshot-tests модуль

Отдельный Gradle-модуль `screenshot-tests/` — генерация скриншотов
для магазина приложений (fastlane), не обычные тесты:

- `targetProjectPath = ":app"` — зависит от app-модуля
- Кастомный раннер `ScreenshotTestRunner` (`AndroidJUnitRunner`)
- `createAndroidComposeRule<MainActivity>()` + `LocaleTestRule`
  (переключение локалей)
- Скриншоты через `Screengrab` с `UiAutomatorScreenshotStrategy`
- Демо-данные вставляются через `database.workoutDao().insertWorkout()` напрямую
- Результат: `fastlane/metadata/android` (см. `make screenshots`)

## Один класс

Фильтрация отдельных androidTest-классов через `--tests` **НЕ работает**
с `connectedDebugAndroidTest` — Gradle/AGP ограничение: задача не
принимает этот параметр (`Unknown command-line option '--tests'`).
Варианты:

- Прогнать весь набор — `make android-test` (все android-тесты за раз).
- В Android Studio: правая кнопка по классу/методу → Run.
- **Один класс или метод через Gradle CLI** — параметр
  `android.testInstrumentationRunnerArguments.class`, который пробрасывается
  в `AndroidJUnitRunner`. После `#` — конкретный метод; без `#` —
  все тесты класса:

  ```bash
  # Один тестовый метод
  ./gradlew :app:connectedDebugAndroidTest --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.main.MainScreenTest#content_long_press_then_row_selected_semantic_is_true_then_dismiss_restores_false'

  # Весь класс
  ./gradlew :app:connectedDebugAndroidTest --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.main.MainScreenTest'
  ```

  Кавычки вокруг значения обязательны (в `class#method` решётка — не
  комментарий оболочки, а разделитель имени класса и метода для
  `AndroidJUnitRunner`). Прогон только нужного метода обычно
  завершается за 5–10 секунд вместо полного набора. Способ применим
  и к `connectedAndroidTest` (без `Debug`) и к `connectedReleaseAndroidTest`.

Для unit-тестов фильтрация `--tests` работает стандартно
(`./gradlew test --tests "com.myworkouts.ExampleUnitTest"`).

## Зависимости androidTest (build.gradle.kts)

```kotlin
androidTestImplementation(libs.androidx.junit)            // androidx.test:junit
androidTestImplementation(libs.turbine)                   // Turbine 1.2.1
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
