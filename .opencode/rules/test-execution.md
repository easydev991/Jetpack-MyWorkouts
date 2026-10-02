# Правила запуска тестов в Jetpack-MyWorkouts

## Главное правило: один прогон → один отчёт

`make test` и `make android-test` **уже** запускают `scripts/test_report.py` и печатают:

- статистику (`tests=X failures=Y errors=Z`),
- **список каждого упавшего** `ClassName::testName`,
- per-class таблицу.

Агенту не нужно ничего дополнительно гонять или парсить. XML-результаты
лежат на диске и доступны для повторного чтения без нового прогона.

## Запрещено

Антипаттерн «4 запуска вместо одного» — повторный `make test` / `make android-test`
только ради чтения вывода. Каждый прогон — 30 секунд (JVM) или 2+ минуты
(androidTest). Четыре прогона = минуты впустую + двойная нагрузка на эмулятор.

```bash
# НЕПРАВИЛЬНО — 4 прогона, лишние минуты:
make android-test 2>&1 | tail -3
make android-test 2>&1 | grep -E 'BUILD |\[FAIL\]' | head -3
find app/build/outputs -name 'TEST-*.xml' | xargs grep 'tests=' …
find app/build -name 'TEST-*.xml' … # повторные поиски

# ПРАВИЛЬНО — 1 прогон, читаем отчёт скрипта:
make android-test           # в выводе уже есть статистика + список фейлов
```

## Flavors: только по одному

НЕЛЬЗЯ запускать оба флейвора сразу в одной команде:

```bash
# НЕПРАВИЛЬНО:
make android-test FLAVOR=rustore && make android-test FLAVOR=github
```

Один прогон — один флейвор; следующий флейвор — только отдельной
командой после полного завершения предыдущей.

## Правильно

**Прогнать тесты**:

```bash
make test           # JVM (JUnit 5) — XML в app/build/test-results/
make android-test   # androidTest (JUnit 4) на эмуляторе — XML в app/build/outputs/androidTest-results/connected/debug/
```

**Workflow**: в разработке — точечный прогон класса затронутого экрана
(см. «Один тест-класс / один метод» ниже и шпаргалку в
`kotlin-ui-testing/references/running-tests.md`); полный
`make android-test` — перед коммитом задачи и перед релизом.
Эмулятор после каждого старта — `make emulator-fast` (выключает
анимации, androidTest идёт заметно быстрее).

**Перечитать отчёт БЕЗ нового прогона** (XML ещё на диске — `make clean` стирает):

```bash
python3 scripts/test_report.py app/build/test-results                                  # JVM
python3 scripts/test_report.py app/build/outputs/androidTest-results/connected/debug   # androidTest
```

**Один тест-класс / один метод** (если не нужны все):

```bash
./gradlew testDebugUnitTest --tests "com.myworkouts.ui.viewmodel.ArchivedExercisesViewModelTest"
./gradlew testDebugUnitTest --tests "*ArchivedExercises*"                                              # wildcard
./gradlew testDebugUnitTest --tests "com.myworkouts.ui.viewmodel.ArchivedExercisesViewModelTest.unarchive_calls_setArchived_with_false"
# androidTest: `--tests` с connectedDebugAndroidTest НЕ работает (Unknown
# command-line option). Только через runner-arguments. Задача — с флейвором
# (github — дефолт, rustore → connectedRustoreDebugAndroidTest):
./gradlew :app:connectedGithubDebugAndroidTest --console=plain \
  -Pandroid.testInstrumentationRunnerArguments.class='com.myworkouts.ui.screens.exercises.ArchivedExercisesScreenTest'
# метод — через # : …class='com.myworkouts.…ScreenTest#row_long_press_when_standard_then_no_context_menu'
```

Несколько классов:

```bash
./gradlew testDebugUnitTest --tests "ClassA" --tests "ClassB"
```

## Когда `make` НЕ подходит

Только если нужно **запустить без отчёта** (например, отладка конкретного
теста с большим выводом, который скрипт обрезает). Тогда:

```bash
./gradlew testDebugUnitTest --tests "Class.testName" --info
```

Но **проверка результатов** после такого запуска — тоже через
`test_report.py` по XML на диске, не через повторный `make`.

## Если тесты упали

1. Запустить `make test` / `make android-test` — список фейлов в выводе
   скрипта (`ClassName::testName`).
2. Прочитать конкретный failure: либо из вывода скрипта (для первых
   нескольких), либо из HTML-отчёта `app/build/reports/tests/.../index.html`.
3. Перечитать после фикса **без нового `make`**: `python3 scripts/test_report.py <dir>`.

Не надо повторно вызывать `make test` / `make android-test`, чтобы просто
посмотреть тот же отчёт заново.

## Эмулятор для androidTest

`make android-test` требует подключённого эмулятора или устройства.
Проверка — `adb devices` (или `mobile_list_available_devices` через MCP).
Без девайса androidTest не запустятся — это ожидаемо, не повторять попытки.

## Полные руководства

- `kotlin-testing/SKILL.md` — JVM unit-тесты, Fake-репозитории, MockK.
- `kotlin-ui-testing/SKILL.md` — androidTest, Compose Testing, Room in-memory.
- `kotlin-testing/references/running-tests.md` — канонические команды и фильтрация.
