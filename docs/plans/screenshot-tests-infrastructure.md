# План: модуль `:screenshot-tests` (инфраструктура для скриншотов)

## Цель

Поднять модуль, аналогичный `JetpackDays/screenshot-tests/`,
для автоматической генерации скриншотов на двух локалях (`ru-RU`,
`en-US`) и укладки их в
`fastlane/metadata/android/<locale>/images/phoneScreenshots/`.
**Только инфраструктура.** Конкретные сценарии («главный экран с
10 записями», «форма создания с открытым DatePicker» и т. п.) выносятся
в отдельный план — здесь их писать не нужно.

## Контекст

- Референс — `JetpackDays/screenshot-tests/`:
  - `build.gradle.kts` (плагин `com.android.test` + `kotlin.compose`,
    `targetProjectPath = ":app"`,
    `missingDimensionStrategy("distribution", "rustore")`,
    `testInstrumentationRunner = "…ScreenshotTestRunner"`).
  - `AndroidManifest.xml` с `<instrumentation>` и `WAKE_LOCK`,
    `DISABLE_KEYGUARD`, `CHANGE_CONFIGURATION`, `WRITE/READ_EXTERNAL_STORAGE`.
  - `ScreenshotTestRunner.kt` — пустой `class … : AndroidJUnitRunner()`.
  - `ScreenshotsTest.kt` — `createAndroidComposeRule<MainActivity>()` +
    `@Rule LocaleTestRule` + `Screengrab.setDefaultScreenshotStrategy(
    UiAutomatorScreenshotStrategy())` + `Screengrab.screenshot("1-demoList")`.
  - `Screengrabfile`/`Fastfile` (`capture_android_screenshots` для
    `locales: ['ru-RU', 'en-US']`).
  - `Makefile`-цели `screenshots{,ru,en}`,
    `_build_screenshots_apk` (`./gradlew :app:assembleRustoreDebug
    :screenshot-tests:assembleDebug`), `_cleanup_screenshots_apk`.
- В проекте **нет fastlane** (публикация — через `tools/release`,
  Ruby/bundler не используется; проверено: в репо нет ни `Fastfile`,
  ни `Screengrabfile`) — что из этого следует для плана, см.
  «Принципы и ограничения». Вместо `Screengrab.screenshot(name)` —
  `composeTestRule.onRoot().captureToImage()` + запись Bitmap через
  `java.io.File` (механика записи — Этап 2, хранение и пермишены —
  «Принципы»).
- Уже есть: `app/src/androidTest/` (`make android-test` через
  `:app:connectedGithubDebugAndroidTest`),
  `.opencode/skills/kotlin-ui-testing/`, `kotlin-testing`, `Makefile`,
  GitHub + RuStore flavors (`github` — дефолт; см. `AGENTS.md`,
  `docs/plans/flavors-implementation.md`).
- `fastlane/metadata/android/` уже создан для `whats_new` —
  `images/phoneScreenshots/` появится при первой генерации.
- TDD здесь точечно (один `@Test`-smoke, проверяющий, что инфраструктура
  пишет валидный PNG); реальные сценарии — в отдельной задаче.

## Принципы и ограничения

- **Без fastlane.** План должен работать через `make` + `adb` без
  Ruby. Это означает: без `Screengrabfile`/`Fastfile`, без
  `capture_android_screenshots`, без зависимости
  `tools.fastlane:screengrab` (она тянет screengrab-нам-не-нужные API).
- **Без фича-сетевых зависимостей** (AGENTS.md): скриншоты — офлайн
  pipeline (девайс → `adb pull` → `fastlane/metadata/...`). Не
  подключаем Retrofit/OkHttp/Ktor ни в `:screenshot-tests`, ни в `:app`.
- **Без новых пермишенов в `:app`** и без лишних прав в манифесте
  тест-модуля: PNG пишутся в app-specific external storage —
  `WRITE_EXTERNAL_STORAGE` на API 29+ не нужен. Чтение с хоста —
  через `adb root` перед pull-циклом (на API 30+ shell не читает
  `/sdcard/Android/data/<pkg>/`; так же делает JetpackDays через
  `use_adb_root true`, см. Этап 4; у JetpackDays путь другой —
  app-private `/data/data/<pkg>/app_screengrab/`). `adb root`
  доступен на AOSP/Google-APIs образах, на google_play — нет
  (проверка — «Проверка окружения», Этап 0).
- **Переключение локали UI — снаружи теста, пер-апп.** Тег локали для
  фикстуры передаётся runner-аргументом
  (`-Pandroid.testInstrumentationRunnerArguments.locale=ru-RU`, читается
  в `@Before`, дефолт `ru-RU`). Сама UI-локаль приложения переключается
  **до** instrumentation-запуска:
  `adb shell cmd locale set-app-locales com.myworkouts --user 0 --locales <tag>`,
  после цикла — `reset-app-locales` (аналог `ending_locale` у JetpackDays).
  Это официальный shell-API (API 33+), система сама доставит новую
  конфигурацию в приложение: подхватятся и ресурсы `values-en`, и
  `LocalConfiguration.current.locales[0]` (`ActivityScreen.kt`),
  и `Locale.getDefault()` (`EntryValueFormatter.kt`). Пер-апп локаль
  легальна — `locales_config.xml` в проекте уже подключён (AGENTS.md).
  `am instrument -e locale` **не** используется: оно не меняет конфигурацию
  приложения. Рефлексия из screengrab (`LocaleUtil.changeDeviceLocaleTo`
  → `ActivityManagerNative` + тихий catch) сознательно не копируется:
  скрытые API, мутация всей системы и зелёный тест при упавшей локали.
  Тестовый код никаких locale-правил не содержит — Activity создаётся
  уже с нужной конфигурацией.
- **Паттерн работы с БД в androidTest — через production-репозиторий
  процесса** (как в `MainScreenDetailCardRecreateTest.kt`):
  `ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()`
  → `app.repository` (конкретный класс `RoomWorkoutDataRepository`).
  Один инстанс Room = один `InvalidationTracker` = реактивный Flow
  `MainViewModel.uiState` видит вставку быстрее, чем истекает окно
  `WhileSubscribed(5000)` (стратегия подписки остаётся,
  меняется только источник данных), и без навигационных хаков —
  **ровно** то свойство, которое нужно для детерминированной
  отрисовки скриншотов.
  Альтернатива «второй инстанс `Room.databaseBuilder(...)`» из
  `EntryListLiveUpdateIntegrationTest.kt` (KDoc + тело;
  тот же хелпер — дубликат в `RootScreenTest.kt`)
  для скриншотов проигрывает: даст вставку в ту же файловую БД,
  но `Flow` не узнает о ней мгновенно.
- **Тест-модуль БЕЗ своих `productFlavors`** — только `com.android.test`,
  `targetProjectPath = ":app"` и `missingDimensionStrategy`.
  `missingDimensionStrategy` фиксирует, **какую** flavor-карту
  `:app` тест-APK компилируется против (`distribution = github` —
  не `rustore`, как в референсе: github — дефолтный флейвор проекта,
  распределение на содержимое скриншотов не влияет),
  но **не создаёт** `productFlavors` в самом тест-модуле и не
  попадает в имена его gradle-задач — у тест-модуля без собственных
  flavors остаются варианты `debug`/`release` независимо от flavor-карты
  `:app` (поведение AGP для `com.android.test` модулей; окончательная
  проверка имен задач — после создания модуля, см. «Критерии»).
  → Gradle-задача для прогона — `:screenshot-tests:connectedDebugAndroidTest`
  (без `-Github`/`-Rustore`), не `connectedGithubDebugAndroidTest`.

## Этапы

### Этап 0 — Проверка окружения (гейт всего подхода)

- [ ] Выполнить **до** Этапов 1–4, на целевом эмуляторе (~2 минуты;
      если гейт не проходит — подход не работает вовсе, см. «Принципы»):
      `adb root` отвечает `restarting adbd as root` (не `adbd cannot run
      as root` — google_play-образ не годится);
      `adb shell cmd locale set-app-locales com.myworkouts --user 0
      --locales en-US` + ручной запуск приложения — UI на английском
      (`cmd locale` доступен с API 33; эмулятор для скриншотов — API 34+).
- [ ] Плюс **probe по файловому пути** — цепочка, которую не закрывают
      первые два пункта и которая ломается чаще всего (app-specific
      external storage + чтение с хоста):
      `adb root` → `adb shell mkdir -p <DEVICE_PNG_DIR>` →
      `adb shell touch <DEVICE_PNG_DIR>/probe.txt` →
      `adb pull <DEVICE_PNG_DIR>/probe.txt` на хост.
      Подтверждает, что `DEVICE_PNG_DIR` из Этапа 4 существует, доступен
      и читается после `adb root` (почему нужен именно root —
      «Принципы и ограничения»).
      Чего гейт **не** проверяет: само совпадение
      `context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)` с этим
      путём — вызова `getExternalFilesDir` в коде приложения пока нет
      (в репо 0 обращений), и создать каталог из-под `:app` вне Этапа 3
      нечем. Совпадение доказывает smoke-тест Этапа 3: он пишет через
      `ScreenshotWriter` в target-каталог и тянется тем же `DEVICE_PNG_DIR`.

### Этап 1 — Каркас модуля

- [ ] Создать `screenshot-tests/` с пустым `build.gradle.kts`.
- [ ] `gradle/libs.versions.toml`: добавить алиас
      `android-test = { id = "com.android.test", version.ref = "agp" }`
      (в `app/build.gradle.kts` все плагины идут через `alias`,
      для единообразия тест-плагин подключаем так же; `version.ref = "agp"`
      переиспользует уже зафиксированную версию AGP).
- [ ] `screenshot-tests/build.gradle.kts`: плагины
      `alias(libs.plugins.android.test)` +
      `alias(libs.plugins.kotlin.compose)` +
      `alias(libs.plugins.ktlint)` (обязательно, иначе код модуля
      не пройдёт `make lint` — ktlint применяется per-module,
      как в `app/build.gradle.kts`).
- [ ] `android { namespace = "com.myworkouts.screenshots";
      compileSdk { version = release(37) };
      minSdk = 26;
      targetProjectPath = ":app";
      testInstrumentationRunner = "com.myworkouts.screenshots.ScreenshotTestRunner";
      missingDimensionStrategy("distribution", "github");
      buildFeatures { compose = true } }` — повторяет форму `app/build.gradle.kts`
      (top-level форма `compileSdk = 37`, как в референсе JetpackDays,
      на AGP 9 устарела — используем блочную
      `compileSdk { version = release(37) }`);
      `targetSdk` для тест-модуля не задаём (модуль не публикуется).
- [ ] `kotlin { jvmToolchain(17) }` — пин toolchain (как в референсе
      `JetpackDays/screenshot-tests/build.gradle.kts`); `compileOptions`
      JVM 17 не нужен — toolchain синхронизирует target.
- [ ] Зависимости (`screenshot-tests/build.gradle.kts`; порядок в списке
      не важен — резолв от него не зависит):
      `implementation(project(":app"))` (без него не резолвятся
      `MainActivity`, `WorkoutDatabase`, `RoomWorkoutDataRepository`,
      `MyWorkoutsApplication` — весь Этап 3 на них опирается);
      `androidx-junit`;
      Compose BOM + `androidx-compose-ui-test-junit4`
      (Compose BOM и `ui-test-junit4` тянут `espresso-core` и `core-ktx`
      транзитивно — для AGP-`com.android.test`-модулей это рабочее
      поведение; **проверяется после создания модуля** через
      `./gradlew :screenshot-tests:dependencies --configuration debugRuntimeClasspath`
      — если `espresso-core`/`core-ktx` не пришли транзитивно, добавить явно).
      **Без** Room — `project(":app")` тянет Room/KSP-генерированный
      код транзитивно, дублировать deps не нужно. **Без**
      `tools.fastlane:screengrab`.
- [ ] `screenshot-tests/src/main/AndroidManifest.xml` — **пустой**
      `<manifest/>`. `testInstrumentationRunner` из DSL (Этап 1)
      сам порождает `<instrumentation>` в merged-манифесте;
      ручной тег в source-манифесте только создаёт риск конфликта merger'а.
      Проверка после `processDebugManifest`:
      `build/intermediates/merged_manifests/debug/AndroidManifest.xml`
      должен содержать ровно один `<instrumentation>` с `targetPackage="com.myworkouts"`.
      (Хранение PNG и пермишены — решение в «Принципах и ограничениях».)
- [ ] `screenshot-tests/src/main/java/com/myworkouts/screenshots/ScreenshotTestRunner.kt`
      — пустой наследник `androidx.test.runner.AndroidJUnitRunner`.
- [ ] `settings.gradle.kts`: `include(":screenshot-tests")`.

### Этап 2 — Утилита записи PNG

- [ ] `screenshot-tests/src/main/java/com/myworkouts/screenshots/ScreenshotWriter.kt`:
      без `util/`-уровня — модуль из 3 файлов, отдельная папка ради
      одной функции (AGENTS.md: `di/`/`util/` не плодим):
      функция
      `fun writeScreenshotBitmap(
        context: Context,
        localeTag: String,        // "ru-RU" / "en-US"
        fileName: String,         // "1-mainScreen" (без расширения)
        bitmap: android.graphics.Bitmap
      ): File`,
      которая кладёт PNG в
      `File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
        "screenshots/<localeTag>/<fileName>.png")`.
      Если директории нет — `mkdirs()`. Возврат `File` нужен
      для `adb pull`. Логирование пути — на русском
      (`"Скриншот сохранён: <path>"`).
- [ ] Sanity-check `Bitmap.compress`/try-with-resources **не нужен** —
      покрывается smoke-тестом в Этапе 3 (проверка сигнатуры PNG +
      ненулевой длины).

### Этап 3 — Test-харнесс и smoke-тест

- [ ] `screenshot-tests/src/main/java/com/myworkouts/screenshots/ScreenshotsTest.kt`
      — `@RunWith(AndroidJUnit4::class)`,
      `@get:Rule val composeTestRule = createAndroidComposeRule<MainActivity>()`.
- [ ] Тег локали читается **один раз** в `@Before` через
      `InstrumentationRegistry.getArguments().getString("locale")
        ?: "ru-RU"`,
      сохраняется в `private lateinit var localeTag: String` (не
      `val` — из `@Before` присвоить нельзя). Фикстура
      использует его для выбора русских/английских полей. Сама
      UI-локаль выставляется **снаружи** (см. Этап 4, `cmd locale
      set-app-locales`) — в тесте никаких locale-правил и `LocaleTestRule`.
- [ ] В `@Before` (после чтения `localeTag`):
      ```kotlin
      val app = ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()
      // оба вызова — Result<Unit> (WorkoutDataRepository.kt):
      // игнорировать нельзя — упавший импорт даст зелёный setup на
      // пустой БД. Канон проверки — assertTrue(result.isSuccess),
      // RoomWorkoutDataRepositoryDataManagementTest.kt.
      runBlocking {
        assertTrue(app.repository.deleteAllData().isSuccess) // восстановит набор
        val imported = app.repository.importData(            // demo-записи
          exercises = demoExercises(),
          entries = demoEntries(localeTag)
        )
        assertTrue("Импорт demo-данных не удался: $imported", imported.isSuccess)
      }
      composeTestRule.waitForIdle() // safety-net: слив pending-рекомпозиций;
      // основная гарантия готовности данных — waitUntil в @Test ниже
      ```
      Никакого отдельного helper-класса: всё, что нужно для setup,
      попадает в `@Before` (он не принимает параметров, но может
      инкапсулировать всё, что не зависит от тела `@Test`).
- [ ] Единственный smoke-`@Test fun capturesSingleScreenshot_whenInfraBuilt_thenWritesValidPng()`:
      открыть главный экран, **дождаться асинхронной готовности данных**:
      ```kotlin
      composeTestRule.waitUntil(ENTRY_TEXT_TIMEOUT) {
          composeTestRule.onAllNodesWithText(<первая demo-запись>)
              .fetchSemanticsNodes().isNotEmpty()
      }
      ```
      (идиома из `MainScreenDetailCardRecreateTest.kt`;
      `ENTRY_TEXT_TIMEOUT` объявляем **локально** в `ScreenshotsTest.kt`
      (`private companion object { const val ENTRY_TEXT_TIMEOUT = 5_000L }`) —
      в `:app` он `private` внутри companion-объекта этого класса
      (`MainScreenDetailCardRecreateTest.kt`), из другого gradle-модуля
      не виден. `waitForIdle()`
      недостаточен — `createAndroidComposeRule` поднимает Activity
      **до** `@Before`, а `waitForIdle()` ждёт только рекомпозиции,
      а не round-trip'а в БД).
      Затем — **ассерт на UI-локаль**: на экране есть строка из ресурсов
      приложения (`composeTestRule.onNodeWithText(
      activity.getString(R.string.workouts_title)).assertIsDisplayed()`;
      `workouts_title` — заголовок TopAppBar главного экрана
      (`MainScreen.kt`), ru «Тренировки» / en «Workouts») — это
      доказывает, что пер-апп локаль
      применилась к UI, а не только к фикстуре. Демо-текст из фикстуры
      этого не доказывает: он локале-зависим по построению.
      Затем `composeTestRule.onRoot().captureToImage().asAndroidBitmap()`,
      записать через `ScreenshotWriter.writeScreenshotBitmap(
        context, localeTag, "0-smoke", bitmap)` и **проверить PNG, а не
      «файл непустой»** — `length() > 0` пропустит обрезанный файл:
      ```kotlin
      assertTrue(file.exists() && file.length() > PNG_SIGNATURE.size)
      file.inputStream().use { stream ->
        val header = ByteArray(PNG_SIGNATURE.size)
        assertTrue(stream.read(header) == PNG_SIGNATURE.size)
        assertTrue(header.contentEquals(PNG_SIGNATURE))
      }
      ```
      с локальной константой в том же companion-объекте:
      `private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)`.
      Заявленный в «Контексте» «валидный PNG» — это 8 байт **сигнатуры
      PNG** (magic number `89 50 4E 47 0D 0A 1A 0A`; IHDR — следующий за
      ней chunk, здесь он не проверяется) плюс ненулевая длина; полная
      валидность (раскраска, целостность IDAT) остаётся за ручной
      проверкой `file <png>` из Этапа 5.
      `context` берётся один раз через
      `InstrumentationRegistry.getInstrumentation().targetContext`
      (проектский канон из `MainScreenDetailCardRecreateTest.kt`;
      `getContext()` в `com.android.test` модуле может указывать на
      instrumentation-package, а не на `:app`-package, и `adb pull`
      потом молча не найдёт файлы).
- [ ] Фикстура demo-данных строится по образцу JetpackDays
      (`loadDemoData()` в `ScreenshotsTest.kt` в референсном проекте —
      параллельные поля titleRu/titleEn, detailsRu/detailsEn на
      каждый демо-элемент).
- [ ] Без `!!`, `Thread.sleep`; синхронизация — `waitUntil` с таймаутом
      + `waitForIdle()` (см. smoke-тест выше); строки UI — через
      `activity.getString(R.string.*)`.
- [ ] Прямая запись **не** покрывается JVM-юнит-тестом — пишем Bitmap
      через Android-API (`Bitmap.compress` + `FileOutputStream`),
      который в `app/src/test/` недоступен без моков. Покрытие —
      smoke-`@Test` (там же, где живёт реальный код) — это сделано
      осознанно, см. принцип «TDD точечно» в разделе «Контекст».

### Этап 4 — Makefile

- [ ] **Make-переменные для путей** (вводим в начале `screenshots`-блока,
      чтобы не дублировать строки между `ScreenshotWriter`, рецептом
      `adb pull` и рецептом копирования в `fastlane/`):
      ```make
      DEVICE_PNG_DIR := /storage/emulated/0/Android/data/com.myworkouts/files/Pictures/screenshots
      META_DIR := fastlane/metadata/android
      ```
      `applicationId = "com.myworkouts"` берётся из `app/build.gradle.kts`;
      flavors не добавляют `applicationIdSuffix` (`app/build.gradle.kts`),
      поэтому путь фиксированный для обоих flavors.
      Чтение `$(DEVICE_PNG_DIR)` с хоста — через `adb root` перед
      pull-циклом (обоснование и ограничения образов — «Принципы»,
      проверка — «Проверка окружения» в Этапе 0).
- [ ] `screenshots: _ensure_secrets` (без `_build_screenshots_apk`).
      `_ensure_secrets` нужен не для сборки (debug-APK не требует keystore),
      а потому что `app/build.gradle.kts` вызывает
      `checkNotNull(secretsProperties["KEYSTORE_FILE"])` в фазе
      конфигурации `:app` — любая задача с `targetProjectPath = ":app"`
      (включая `:screenshot-tests:*`) валится без секретов, даже если
      ей нужен только debug-вариант.
      Тело `screenshots` собирает+ставит APK сам через
      `:screenshot-tests:connectedDebugAndroidTest`; отдельный
      `assembleDebug`/`assembleGithubDebug` избыточен.
- [ ] Главная gradle-задача для тестового прогона (по образцу
      `make android-test`, которая вызывает
      `./gradlew :app:connectedGithubDebugAndroidTest` через
      `run_tests`):
      `./gradlew :screenshot-tests:connectedDebugAndroidTest
       -Pandroid.testInstrumentationRunnerArguments.locale=<locale>`
      (без `-Github` в имени — модуль не имеет своих
      `productFlavors`, см. «Принцип» про имена задач).
- [ ] `LOCALES ?= ru-RU en-US` с двумя проверками, обе в стиле
      `$(error ...)` валидации `FLAVOR` (`Makefile`):
      `ifeq ($(strip $(LOCALES)),)` → `$(error LOCALES is empty. Expected: ru-RU en-US)`.
      Без неё `make screenshots LOCALES=` проходит валидацию
      (`$(filter …,$(LOCALES))` и `$(LOCALES)` оба пусты → `ifneq` ложно),
      цикл `for locale in` делает ноль итераций и цель «успешна» без
      скриншотов; затем `ifneq ($(filter ru-RU en-US,$(LOCALES)),$(LOCALES))` —
      опечатки в `make screenshots LOCALES=ru` падают с понятной
      ошибкой, а не тихо прогоняют одну локаль или мусор.
- [ ] Тело `screenshots` (после `_ensure_secrets` и подключения
      `DEVICE_PNG_DIR`/`META_DIR`):
      ```make
      adb root && adb wait-for-device; \
      adb shell rm -rf $(DEVICE_PNG_DIR); \
      status=0; \
      for locale in $(LOCALES); do \
          adb shell cmd locale set-app-locales com.myworkouts --user 0 \
              --locales $$locale || exit 1; \
          ./gradlew :screenshot-tests:connectedDebugAndroidTest \
              -Pandroid.testInstrumentationRunnerArguments.locale=$$locale \
              || status=1; \
          mkdir -p $(META_DIR)/$$locale/images/phoneScreenshots; \
          adb pull $(DEVICE_PNG_DIR)/$$locale/ \
              $(META_DIR)/$$locale/images/phoneScreenshots/ || status=1; \
      done; \
      adb shell cmd locale reset-app-locales com.myworkouts --user 0; \
      exit $$status
      ```
      `status`/`exit $$status` — по канону `run_tests` в `Makefile`
      (`BUILD_STATUS=$$?` … `exit $$BUILD_STATUS`). Без этого падение
      gradle в одной локале не прерывает цикл: добегает следующая
      локаль, `reset-app-locales` отрабатывает, а код цели = код
      последней команды → `make screenshots` возвращает 0 вообще без
      PNG, что формально проходит «Критерии завершения».
      Локаль сбрасывается **всегда**, даже если какая-то итерация
      упала, — девайс не остаётся с чужой локалью для следующего
      запуска.
      Исключение — `set-app-locales`: там `|| exit 1` сразу, потому что
      локаль не выставилась → тянуть нечего (иначе в
      `$(META_DIR)/<locale>/` лягут скриншоты чужой локали и уедут в
      git), и сбрасывать нечего: предыдущий запуск сбросил сам.
      Гарантию «для локали реально есть PNG» даёт instrumentation-тест
      (Этап 3), а не `make`.
      Назначение `set-app-locales`/`reset-app-locales` и `adb root` —
      см. «Принципы» (механика пер-апп локали) и блок переменных выше.
      PNG-файлы в `$(META_DIR)/<locale>/images/phoneScreenshots/`
      **коммитятся в git** (это и есть цель цели). Разрешение в
      `.gitignore` **уже есть**: правило `*.png` снято исключением
      `!fastlane/metadata/android/**/*.png` (с комментарием
      «Скриншоты сохраняем в гите») — действий на этом этапе не
      требуется, только не удалить исключение при будущих правках
      `.gitignore`: без allow-list PNG остались бы только локально
      и не попали бы в релиз.
- [ ] `screenshots-ru: ; $(MAKE) screenshots LOCALES=ru-RU` (целиком
      одна строка — single-source-of-truth на основную `screenshots`).
      Аналогично `screenshots-en: ; $(MAKE) screenshots LOCALES=en-US`.
      Переопределение `LOCALES` внутри выключает цикл — `make`
      подставляет значение переменной в рецепт вызываемой цели.
      **Без** отдельной `_cleanup_screenshots_apk` — `screenshot-tests/build/`
      уже в `.gitignore` (`build/` — глобально), `make clean` чистит
      `screenshot-tests/build/` через `./gradlew clean`, а
      `connectedDebugAndroidTest` собирает/ставит APK самостоятельно;
      очистку PNG-каталога при этом рецепт делает сам (`rm -rf` перед
      циклом, аналог `clear_previous_screenshots` у screengrab).
- [ ] `.PHONY` дополнить. Для каждой новой цели (`screenshots`,
      `screenshots-ru`, `screenshots-en`) — своя строка
      `## <имя>: <описание>` **над** целью: без неё `make help`
      (`Makefile`, парсинг `sed -n 's/^##//p'`) новые цели не
      покажет, даже после добавления в `.PHONY`.

### Этап 5 — Документация и проверка

- [ ] Параграф «Screenshot-tests» в **двух** местах (оба — разделы `AGENTS.md`):
      - `AGENTS.md`, раздел «Tests»: что за модуль, как запускать
        (`make screenshots{,ru,en}`);
      - `AGENTS.md`, раздел «Project Structure»: добавить
        `:screenshot-tests` рядом с `:app`, упомянуть, что это
        инструментальный модуль для генерации скриншотов.
      **В «Build» не вставляем** — `make screenshots` пока не часть
      ежедневного воркфлоу (нет CI, чек-листов в release); когда/если
      появится — перенесём одной строкой.
      `docs/deployment.md` сейчас описывает только `whats-new`
      и rustore-публикацию; в нём секция для screenshots тоже не нужна.
      Skill `kotlin-screenshot-testing` **не упоминается** (YAGNI:
      в этом плане его нет, Этап 6 явный «опционально, если
      понадобится», а AGENTS.md — это не roadmap).
- [ ] `make format && make lint && make test` — без новых ktlint-замечаний
      (через `alias(libs.plugins.ktlint)` в Этапе 1).
      **detekt новый модуль не покрывает**: `Makefile` вызывает
      `./gradlew app:detekt` (только `:app`); для модуля из 2-3 файлов
      detekt-плагин сознательно не подключаем — ktlint уже ловит основное,
      а добавление `detekt { config.setFrom(files("../config/detekt/detekt.yml")) }`
      под каждый новый модуль — лишний шум (см. AGENTS.md про detekt-лимит).
- [ ] **Сквозная проверка** после прогона:
      `adb shell ls $(DEVICE_PNG_DIR)/ru-RU/` — PNG видны
      (гейт-проверки `adb root`/`cmd locale` — Этап 0).
- [ ] Запуск: подключить эмулятор (`adb devices` — если нет, прогон
      `make android-test` тоже не пройдёт; см. `.opencode/rules/android-emulator.md`),
      затем `make emulator-fast` (выключает анимации — без этого
      скриншоты на эмуляторе смазываются), затем `make screenshots-ru`
      и `make screenshots-en`. На выходе — пара PNG в обеих локалях.
      Проверка: `file <png>` — валидный PNG. Каталог
      `fastlane/metadata/.../images/phoneScreenshots/` создаётся самим
      рецептом, `.gitkeep` не нужен.

### Этап 6 (опционально, за рамками этого плана)

- [ ] Skill `.opencode/skills/kotlin-screenshot-testing/SKILL.md` —
      «как писать сценарии скриншотов» (фикстуры, порядок
      `waitForIdle` после вставки в БД, имена PNG
      `<номер>-<описание>`, локализация полей фикстуры). Заводится,
      когда появится второй сценарий и захочется зафиксировать
      соглашения. В этом плане skill **не объявляется обязательным**.

## Критерии завершения

- [ ] `./gradlew :screenshot-tests:assembleDebug` собирается без ошибок
      на машине разработчика.
- [ ] `./gradlew :screenshot-tests:tasks --all | grep -i connected`
      показывает ровно `connectedDebugAndroidTest` (а не две/три — это
      доказательство, что модуль не имеет своих `productFlavors`).
      Без `--offline`: plugin marker `com.android.test` ещё не в кеше
      до первой успешной сборки и `--offline` упадёт на резолве.
- [ ] `make screenshots` проходит end-to-end: build →
      `:screenshot-tests:connectedDebugAndroidTest` (UI реально на
      целевой локали — smoke-ассерт на `R.string` зелёный) → PNG в
      `$(DEVICE_PNG_DIR)` → `adb pull` → файлы появились в
      `fastlane/metadata/android/ru-RU/images/phoneScreenshots/` и
      `fastlane/metadata/android/en-US/images/phoneScreenshots/`,
      пары ru/en для одного сценария визуально различаются.
- [ ] `make screenshots LOCALES=fr-FR` падает с понятной ошибкой
      (валидация на `ru-RU|en-US` срабатывает).
- [ ] Существующие `make test`, `make android-test`, `make format`,
      `make lint` не сломаны.
- [ ] Detekt-лимит не повышен.
- [ ] В `AGENTS.md` отражён новый модуль в **двух** местах
      («Tests», «Project Structure»). «Build»/«deployment.md»
      пока не трогаем — `make screenshots` не входит в release-чек-лист.

## Не входит (открытые задачи)

- Сценарии скриншотов (`MainScreen`, `EntryCreateScreen`,
  `ExercisesScreen`, `ArchivedExercisesScreen`, `AppDataScreen` и т.д.)
  — **отдельный план** после закрытия этого. Опирается на
  `ScreenshotWriter`, `ScreenshotsTest` и соглашения по именованию PNG.
- Подключение `make screenshots` к CI / pre-release чек-листу —
  обсуждается отдельно (offline-only проект, CI не активен).
- Выбор стратегии сжатия PNG (качество, размеры под RuStore/Google
  Play) — на этапе сценариев.

## Обоснование «без OpenSpec»

Это **инфраструктурная** правка: новый Gradle-модуль, новые `make`-цели,
новый способ получать PNG из Compose. Меняется только build/CI,
**никакая** `openspec/specs/*` возможность не затрагивается:

- В `openspec/changes/archive/` 42 записи — преимущественно фичи
  (`workout-data-layer`, `exercises-archive`, `entry-create-screen`).
  Есть и инфраструктурные прецеденты через OpenSpec:
  `2026-09-17-firebase-crashlytics-mvp` (SDK + gradle-обвязка),
  `2026-09-18-rustore-flavor-buttons` (flavors), `2026-09-17-seed-on-destructive-migration`.
  То есть само наличие OpenSpec-записи — **не** признак «это фича».
- Инфраструктурные прецеденты через `docs/plans/` —
  `docs/plans/flavors-implementation.md`, `docs/plans/ui-tests-optimization.md`,
  `docs/plans/fix-ide-warnings.md`. То есть и OpenSpec, и `docs/plans/`
  пригодны для инфраструктуры в зависимости от контекста.
- Скриншоты — это артефакт для маркетплейса, не пользовательская
  функциональность. Когда появятся сценарии — если сценарий иллюстрирует
  новую видимую пользователю возможность (например, новый экран N),
  фича пойдёт через OpenSpec. Сами скриншоты уже существующих экранов —
  продолжение инфраструктуры, без OpenSpec.

Если в будущем возникнет спорный момент («это фича или инфра?»),
ориентир — `openspec/specs/<capability>/spec.md`: если изменение не
требует редактирования ни одного существующего спек-файла и не
добавляет нового — это инфраструктура, идёт через `docs/plans/`.
