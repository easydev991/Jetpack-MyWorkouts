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

```bash
make test-all
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

Отфильтровать один тест-класс можно параметром `--tests`
(пример для unit-тестов из `kotlin-testing`, для androidTest — аналогично):

```bash
./gradlew connectedDebugAndroidTest --tests "com.myworkouts.ui.screens.events.WorkoutsScreenSortByTimeOfDayUiTest"
```

## Зависимости androidTest (build.gradle.kts)

```kotlin
androidTestImplementation(libs.androidx.junit)            // androidx.test:junit
androidTestImplementation(libs.turbine)                   // Turbine 1.2.1
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
