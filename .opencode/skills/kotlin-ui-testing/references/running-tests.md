# Running tests — запуск и отчётность

Как запускаются инструментированные тесты Jetpack-MyWorkouts и как читать
результаты.

## Запуск

```bash
make android-test    # все androidTest на подключённом устройстве/эмуляторе
```

Под капотом:

```makefile
./gradlew connectedDebugAndroidTest --console=plain
python3 scripts/test_report.py app/build/outputs/androidTest-results/connected/debug
```

- `connectedDebugAndroidTest` — инструментированные тесты в debug-сборке
- Требуется подключённое устройство или запущенный эмулятор

## Отчёт

`scripts/test_report.py` — человекочитаемый отчёт (тот же скрипт, что
использует `make test`):

- Exit code 0 — все тесты прошли
- Exit code 1 — упавшие тесты (`sys.exit(1)` при `failed > 0`)

Парсинг XML из `app/build/outputs/androidTest-results/connected/debug/`.

Скрипт запускается **всегда** (и при упавших тестах) — список фейлов
виден сразу, без `grep` по Gradle-выводу.

### Анти-паттерн: 4 запуска вместо одного

`make android-test` сам печатает список упавших тестов через
`test_report.py`. Не надо после запуска снова грепать Gradle-вывод:

```bash
# НЕПРАВИЛЬНО — 4 запуска, лишние ~минуты:
make android-test 2>&1 | tail -5
make android-test 2>&1 | grep -E 'FAIL|FAILED' | head -10
make android-test 2>&1 | tail -5
make android-test 2>&1 | grep -E 'FAIL|FAILED' | head -10

# ПРАВИЛЬНО — 1 запуск:
make android-test   # список упавших виден в выводе скрипта, если есть
```

Если нужно перечитать отчёт после фикса без нового запуска:

```bash
python3 scripts/test_report.py app/build/outputs/androidTest-results/connected/debug
```

(Только если XML ещё на диске — `make clean` его стирает.)

HTML-отчёт Gradle остаётся в
`app/build/reports/androidTests/connected/debug/flavors/<flavor>/index.html`
(AGP 9; до AGP 9 был `connected/debug/`) — открывается
вручную (`make android-test-report` в Makefile отсутствует).

## Все тесты сразу

В `Makefile` есть раздельные цели для unit и android-тестов.
Объединённой цели `make test-all` нет:

```bash
make test           # JVM unit-тесты (./gradlew test --console=plain)
make android-test   # androidTest на подключённом устройстве
```

Результаты:
- Unit: `app/build/test-results/`
- Интеграционные: `app/build/outputs/androidTest-results/connected/debug/`
  + `app/build/reports/androidTests/connected/debug/flavors/<flavor>/index.html`

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
    -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.exercises.ExercisesScreenTest#row_long_press_when_standard_then_no_context_menu'

  # Весь класс
  ./gradlew :app:connectedDebugAndroidTest --console=plain \
    -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.exercises.ExercisesScreenTest'
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
androidTestImplementation(platform(libs.androidx.compose.bom))
androidTestImplementation(libs.androidx.compose.ui.test.junit4)
androidTestImplementation(libs.androidx.espresso.core)
androidTestImplementation(libs.androidx.junit)
debugImplementation(libs.androidx.compose.ui.test.manifest)
```

> `Turbine` в проекте **не подключён** нигде.
> `kotlinx-coroutines-test` есть только в unit-тестах
> (`app/build.gradle.kts:66` — `testImplementation`); в `androidTest/`
> его нет (`androidTestImplementation` в `app/build.gradle.kts:72-75`).
> В androidTest используется `kotlinx.coroutines.runBlocking` + `.first()`
> для проверки Flow.

`testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`
