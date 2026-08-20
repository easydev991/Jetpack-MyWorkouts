# Running Tests

Команды для запуска unit-тестов в Jetpack-MyWorkouts.

## `make test` — основной путь

```bash
make test
```

Под капотом:

```bash
./gradlew test --console=plain
python3 scripts/test_report.py
```

`scripts/test_report.py` читает XML-отчёты из
`app/build/test-results/` и печатает:
- Общую статистику (всего / успешных / упавших).
- Список упавших тестов с именами классов и методов.
- Per-class таблицу (сортировка по числу упавших).
- Exit code 0 при успехе, 1 при наличии упавших.

Это **предпочтительный** способ запуска для агента: одна команда,
человекочитаемый отчёт, exit code для CI.

## Прямой Gradle

```bash
# Все unit-тесты (без отчёта)
./gradlew test

# Один тестовый класс
./gradlew test --tests "com.example.jetpack_myworkouts.domain.usecase.CalculateWorkoutDurationUseCaseTest"

# По паттерну (Class содержит подстроку)
./gradlew test --tests "*WorkoutDurationTest"

# Один тестовый метод
./gradlew test --tests "com.example.jetpack_myworkouts.domain.usecase.CalculateWorkoutDurationUseCaseTest.calculate_when_same_day_then_returns_today"

# Все тесты в пакете
./gradlew test --tests "com.example.jetpack_myworkouts.ui.viewmodel.*"

# Несколько классов
./gradlew test --tests "com.example.jetpack_myworkouts.domain.usecase.CalculateWorkoutDurationUseCaseTest" \
                --tests "com.example.jetpack_myworkouts.domain.usecase.FormatWorkoutDurationUseCaseTest"
```

JUnit Platform понимает wildcard `*` как часть имени класса.
Gradle поддерживает `--tests` многократно.

## Фильтрация по тегу или группе

JUnit 5 поддерживает `@Tag("fast")` / `@Tag("slow")`. В проекте
не используется. Если введёшь — запуск:

```bash
./gradlew test --tests "*" --groups "fast"          # только fast
./gradlew test --tests "*" --exclude-groups "slow"  # всё кроме slow
```

## Интеграционные тесты (`androidTest/`)

Требуют устройство или эмулятор:

```bash
make android-test              # запуск
make android-test-report       # открыть HTML-отчёт в браузере
```

Внутри:

```bash
./gradlew connectedAndroidTest --console=plain
python3 scripts/android_test_report.py
```

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

ViewModel использует `Logger` напрямую. Замени на `NoOpLogger()` в
тесте.

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