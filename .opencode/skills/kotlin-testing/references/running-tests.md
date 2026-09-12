# Running Tests

Команды для запуска unit-тестов в Jetpack-MyWorkouts.

## `make test` — основной путь

```bash
make test
```

Под капотом:

```bash
./gradlew test --console=plain
python3 scripts/test_report.py app/build/test-results
```

`scripts/test_report.py` читает XML-отчёты из
`app/build/test-results/` и печатает:
- Общую статистику (всего / успешных / упавших).
- **Список каждого упавшего теста** (`ClassName::testName`) — не надо
  `grep`'ом вылавливать из Gradle-лога.
- Per-class таблицу (сортировка по числу упавших).
- Exit code 0 при успехе, 1 при наличии упавших.

Это **предпочтительный** способ запуска для агента: одна команда,
человекочитаемый отчёт, exit code для CI.

### Анти-паттерн: 4 запуска вместо одного

Не надо после `make test` повторно грепать Gradle-вывод — `test_report.py`
**уже вызывается** после тестов (и при упавших, и при зелёных) и сам
печатает список фейлов. Лишние `make test | tail` / `| grep -E 'FAIL|FAILED'`
только тратят время на повторный запуск Gradle (минуты на полном
androidTest-наборе).

```bash
# НЕПРАВИЛЬНО — 4 запуска, лишние ~минуты:
make test 2>&1 | tail -5
make test 2>&1 | grep -E 'FAIL|FAILED' | head -10
make android-test 2>&1 | grep -E 'FAIL|FAILED' | head -10
make android-test 2>&1 | tail -5

# ПРАВИЛЬНО — 1 запуск на каждый test suite:
make test           # список упавших виден в выводе скрипта, если есть
make android-test   # то же самое
```

Если нужно перечитать отчёт после фикса без нового запуска:

```bash
python3 scripts/test_report.py app/build/test-results        # unit
python3 scripts/test_report.py app/build/outputs/androidTest-results/connected/debug  # android
```

(Только если XML ещё на диске — `make clean` его стирает.)

## Прямой Gradle

```bash
# Все unit-тесты (без отчёта)
./gradlew test

# Один тестовый класс
./gradlew test --tests "com.myworkouts.data.repository.EntryValidatorTest"

# По паттерну (Class содержит подстроку)
./gradlew test --tests "*EntryValidatorTest"

# Один тестовый метод
./gradlew test --tests "com.myworkouts.data.repository.EntryValidatorTest.sets_below_one_rejected"

# Все тесты в пакете
./gradlew test --tests "com.myworkouts.ui.viewmodel.*"

# Несколько классов
./gradlew test --tests "com.myworkouts.data.repository.EntryValidatorTest" \
                --tests "com.myworkouts.ui.viewmodel.MainViewModelTest"
```

JUnit Platform понимает wildcard `*` как часть имени класса.
Gradle поддерживает `--tests` многократно.

## Фильтрация по тегу или группе

JUnit 5 поддерживает `@Tag("fast")` / `@Tag("slow")`. В проекте
не используется. Если введёшь — фильтрация настраивается только в
`app/build.gradle.kts`:

```kotlin
android {
    testOptions {
        unitTests.all { useJUnitPlatform() }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        includeTags("fast")     // только тесты с @Tag("fast")
        excludeTags("slow")     // всё кроме @Tag("slow")
    }
}
```

## Интеграционные тесты (`androidTest/`)

Требуют устройство или эмулятор:

```bash
make android-test              # запуск + статистика в консоли
```

Под капотом тот же `scripts/test_report.py` (не `android_test_report.py` —
его не существует), читает XML из
`app/build/outputs/androidTest-results/connected/debug/`. Универсальный
скрипт: один и тот же `test_report.py` парсит XML и для unit, и для
androidTest — список упавших виден в выводе `make android-test`.

Для запуска через CI/агента — нужен запущенный эмулятор или
подключённое устройство.

## Полная проверка

```bash
make check
```

Build + test + lint.

## Где Gradle кладёт результаты

```
app/build/reports/tests/testDebugUnitTest/index.html   # HTML-отчёт
app/build/test-results/testDebugUnitTest/*.xml        # XML для test_report.py
app/build/reports/tests/testDebugUnitTest/classes/    # детали по классам
```

`test_report.py` читает именно XML из `app/build/test-results/`.

## Типичные проблемы и их решение

### Тест не нашёлся класс

```bash
# Проверь, что компилируется testDebugUnitTest
./gradlew compileDebugUnitTestKotlin
```

### Gradle кэш не подхватил изменения в тестах

```bash
./gradlew clean test
```

### Падает `UncompletedCoroutinesError`

Забыл `advanceUntilIdle()` или `Dispatchers.resetMain()` в тесте.
См. `references/viewmodel-testing.md`.

### `android.util.Log` runtime exception в unit-тесте

Прямой вызов `android.util.Log` в unit-тесте — JVM-stub роняет короутину.
Текущие ViewModel: `ExercisesViewModel` и `AppDataViewModel` логируют
через `android.util.Log.e(...)` (failure-ветки), `AppearanceViewModel`
и `MainViewModel` — нет. Лечится `mockkStatic(Log::class)` +
`every { Log.e(...) } returns 0` в `@BeforeEach`, `unmockkStatic(Log::class)`
в `@AfterEach`. См. `references/mocking-mockk.md`.

### Случайные провалы (flaky)

Скорее всего, тест зависит от времени или порядка выполнения.
См. `references/use-cases.md` (Clock.fixed) и `references/fakes.md`
(MutableStateFlow вместо `flowOf`).

## Советы для CI

- Используй `--console=plain` для логов без прогресс-бара.
- Используй `--no-daemon` для CI (избегает проблем с кэшем демона):
  ```bash
  ./gradlew test --no-daemon --console=plain
  ```
- `make test` уже включает нужные флаги для CI.