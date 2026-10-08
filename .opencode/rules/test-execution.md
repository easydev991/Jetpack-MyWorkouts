# Правила запуска тестов в Jetpack-MyWorkouts

## Главное правило: один прогон → один отчёт

`make test` и `make android-test` **уже** запускают `scripts/test_report.py`
и печатают статистику и список каждого упавшего `ClassName::testName`.
Повторный `make` или `find … -name 'TEST-*.xml'` ради чтения того же
отчёта — **запрещено** (JVM ~30 c, androidTest 2+ минуты; повторы — лишние
минуты и двойная нагрузка на эмулятор). XML лежат на диске (`make clean`
стирает) — перечитать без нового прогона:

```bash
python3 scripts/test_report.py app/build/test-results                                  # JVM
python3 scripts/test_report.py app/build/outputs/androidTest-results/connected/debug   # androidTest
```

## Flavors: только по одному

НЕЛЬЗЯ запускать оба флейвора в одной команде
(`make android-test FLAVOR=rustore && make android-test FLAVOR=github`).
Один прогон — один флейвор; следующий — отдельной командой после полного
завершения предыдущей.

## Команды

```bash
make test           # JVM (JUnit 5) — XML в app/build/test-results/
make android-test   # androidTest (JUnit 4) на эмуляторе
```

- **Точечный прогон** в разработке — класс затронутого экрана. JVM:
  `./gradlew testDebugUnitTest --tests "<FQN>"`. androidTest: `--tests` с
  `connectedDebugAndroidTest` НЕ работает — только runner-arguments:
  `./gradlew :app:connectedGithubDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class='<FQN>'`
  (метод — через `#`). Полный синтаксис фильтров — `references/running-tests.md`
  обоих скиллов.
- **Полный прогон** — перед коммитом задачи и перед релизом (ловит
  регрессии в соседних экранах).
- **Отладка без отчёта** — `./gradlew … --info`; проверка результатов всё
  равно через `test_report.py` по XML, не через повторный `make`.
- **Упали** — список фейлов в выводе отчёта; детали — HTML
  `app/build/reports/tests/.../index.html`.
- **Эмулятор** — нужен подключённый (`adb devices`); без девайса androidTest
  не запустятся — ожидаемо, не повторять попытки. После старта эмулятора —
  `make emulator-fast` (выключает анимации со сверкой read-back);
  `android-test` чинит их сам перед каждым прогоном (`_ensure_animations_off`),
  вручную вызывать `emulator-fast` обязательно только для скорости первого
  прогона. Симптом дрейфа масштабов (сами откатываются в 1.0): UI-тесты
  втрое дольше обычного → `adb shell settings get global animator_duration_scale`
  (и соседние). Краткий всплеск CPU эмулятора во время UI-теста — норма.

## Полные руководства

- `kotlin-testing/SKILL.md` — JVM unit-тесты, Fake-репозитории, MockK.
- `kotlin-ui-testing/SKILL.md` — androidTest, Compose Testing, Room in-memory.
