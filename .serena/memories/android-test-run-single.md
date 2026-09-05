# Запуск отдельного androidTest через Gradle CLI

`--tests` НЕ работает с `connectedDebugAndroidTest` (Gradle/AGP
ограничение: `Unknown command-line option '--tests'`).

Рабочий способ — `-Pandroid.testInstrumentationRunnerArguments.class`,
который пробрасывается в `AndroidJUnitRunner`. После `#` — конкретный
метод, без `#` — весь класс:

```bash
./gradlew :app:connectedDebugAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.main.MainScreenTest#content_long_press_then_row_selected_semantic_is_true_then_dismiss_restores_false'
```

Кавычки обязательны — решётка в `class#method` это разделитель для
runner-а, не символ комментария оболочки. Один метод — за 5-10с
вместо всего набора (97 тестов за ~50с). Применно к `connectedAndroidTest`
и `connectedReleaseAndroidTest` тоже.

Документировано в `.opencode/skills/kotlin-ui-testing/references/running-tests.md`.