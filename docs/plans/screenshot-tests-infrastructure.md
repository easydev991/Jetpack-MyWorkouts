# План: модуль `:screenshot-tests` (инфраструктура для скриншотов)

> **Статус: реализовано** (Этапы 0–5, 7–8). Этап 6 — опциональный,
> не делался.
> История коммитов — `git log -- screenshot-tests Makefile AGENTS.md docs/plans/screenshot-tests-infrastructure.md`.

## Цель

Поднять модуль `:screenshot-tests` для автоматической генерации
скриншотов на двух локалях (`ru-RU`, `en-US`) и укладки их в
`fastlane/metadata/android/<locale>/images/phoneScreenshots/`;
русские показываются в секции «Скриншоты» README (Этап 8).

Этапы 0–5 — **только инфраструктура**. Конкретные сценарии
(«главный экран со списком», «форма создания с открытым DatePicker»
и т. п.) — Этап 7: добавляются по одному по мере утверждения,
начиная с `1-workouts-list`.

## Контекст

- В проекте **нет fastlane** (публикация — через `tools/release`,
  Ruby/bundler не используется; проверено: в репо нет ни `Fastfile`,
  ни `Screengrabfile`) — что из этого следует для плана, см.
  «Принципы и ограничения». Вместо `Screengrab.screenshot(name)` —
  `UiDevice.takeScreenshot()` + запись Bitmap через
  `java.io.File` (механика записи — Этап 2, хранение и пермишены —
  «Принципы»).
- Уже есть: `app/src/androidTest/` (`make android-test` через
  `:app:connectedGithubDebugAndroidTest`),
  `.opencode/skills/kotlin-ui-testing/`, `kotlin-testing`, `Makefile`,
   GitHub + RuStore flavors (`github` — дефолт; см. `AGENTS.md`,
   `docs/deployment.md`).
- `fastlane/metadata/android/` уже создан для `whats_new` —
  `images/phoneScreenshots/` со smoke-PNG генерировался при первой
  проверке (локально, в git не попал — канон про хранение: Этап 5);
  реальные PNG сценариев заменили их (Этап 7).
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
  `/sdcard/Android/data/<pkg>/`). `adb root`
  доступен на AOSP/Google-APIs образах, на google_play — нет
  (проверка — «Проверка окружения», Этап 0).
- **Переключение локали UI — снаружи теста, пер-апп.** Тег локали для
  фикстуры передаётся runner-аргументом
  (`-Pandroid.testInstrumentationRunnerArguments.locale=ru-RU`, читается
  в `@Before`, дефолт `ru-RU`). Сама UI-локаль приложения переключается
  **до** instrumentation-запуска:
   `adb shell cmd locale set-app-locales com.myworkouts --user 0 --locales <tag>`,
   после цикла — `set-app-locales` с пустым списком (субкоманды
   `reset-app-locales` на API 36 нет — сброс это тот же вызов без
   `--locales`).
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
      `:app` тест-APK компилируется против (`distribution = github`,
      не `rustore`: github — дефолтный флейвор проекта,
      распределение на содержимое скриншотов не влияет),
  но **не создаёт** `productFlavors` в самом тест-модуле и не
  попадает в имена его gradle-задач — у тест-модуля без собственных
   flavors остаются варианты `debug`/`release` независимо от flavor-карты
   `:app` (поведение AGP для `com.android.test` модулей; подтверждено
   `tasks --all` после создания модуля, см. «Критерии завершения»).
   → Задач у модуля ровно `connectedDebugAndroidTest` (проверено
   `tasks --all`), но для прогона она **не используется**: AGP после
   connected-прогона удаляет оба APK, а вместе с приложением — и его
   app-specific каталог с PNG (проверено на AGP 9.4.1: `pm list
   packages` пуст сразу после задачи, каталог `Android/data/<pkg>/`
    исчез). Прогон — ручной `am instrument` из рецепта `make screenshots`
    (Этап 4): install/pull — под нашим контролем, анинсталла нет.
- **Ссылки на код в этом документе:** номер строки ставим только у
  файлов длиннее 300 строк (`MainScreen.kt`, `EntryCreateScreen.kt`,
  `EntryCreateViewModel.kt`, `OverviewScreen.kt`, `StatsScreen.kt`,
  `ActivityScreen.kt`, androidTest-файлы) — там поиск по символу
  неоднозначен. У коротких файлов (`Screen.kt`, `EntryCreateUiState.kt`,
  `EntryListItemRow.kt`, `ExerciseEntryDao.kt`, `OverviewViewModel.kt`)
  номер не пишем: он протухает при первом же коммите и вводит
  читателя в заблуждение.

## Этапы

### Этап 0 — Проверка окружения (гейт всего подхода)

- [x] Гейт пройден на целевом эмуляторе (API 36, google-APIs): `adb root`,
      `cmd locale set-app-locales` (API 33+) выставил английский UI.
- [x] Probe файлового пути: app-specific external + `mkdir`/`touch`/
      `adb pull` — успех; `getExternalFilesDir(Pictures)` совпал
      с `$(DEVICE_PNG_DIR)`.

### Этап 1 — Каркас модуля

- [x] Каркас: `screenshot-tests/` + `include(":screenshot-tests")`;
      плагин `com.android.test` через алиас в `libs.versions.toml` +
      `apply false` в корневом `build.gradle.kts` (без него резолв
      в модуле падает).
- [x] DSL `screenshot-tests/build.gradle.kts`: namespace, compileSdk 37,
      minSdk 26, `targetProjectPath=":app"`, стандартный
      `AndroidJUnitRunner`, `missingDimensionStrategy("distribution",
      "github")`, jvmToolchain(17). Зависимости: `project(":app")`,
      androidx-junit, явно `androidx-test-runner` и
      `androidx-uiautomator`, Compose BOM + `ui-test-junit4`. Без
      Room/screengrab/espresso; свой Runner и пустой манифест сделаны
      и удалены по ревью (AGP генерит манифест сам).

### Этап 2 — Утилита записи PNG

- [x] Запись PNG (`Bitmap.compress` 100 + `mkdirs` +
      `FileOutputStream.use`) в
      `getExternalFilesDir(Pictures)/screenshots/<localeTag>/<fileName>.png`
      — изначально в `ScreenshotWriter.kt`, инлайнена в `ScreenshotsTest`
      (YAGNI: единственный вызов).

### Этап 3 — Test-харнесс и smoke-тест

- [x] `ScreenshotsTest`: `createAndroidComposeRule<MainActivity>()`,
      `context` = targetContext (канон `MainScreenDetailCardRecreateTest`
      — иначе `adb pull` молча не найдёт файлы), `localeTag`
      из runner-аргументов (дефолт `ru-RU`); UI-локаль выставляется
      **снаружи** (Этап 4) — никаких `LocaleTestRule`. `@Before`:
      `deleteAllData()` + импорт фикстуры, `@After tearDown` —
      `deleteAllData()` (без уборки demo-записи ломают
      `EntryListLiveUpdateIntegrationTest` при следующем
      `make android-test`).
- [x] smoke-`@Test`: `waitUntil` → ассерт локали через
      `workouts_title` в локали прогона (`onFirst` — строка дублируется
      в TopAppBar и bottom-bar) → capture → валидация PNG.
- [x] Capture — **`UiDevice.takeScreenshot()`**, не
      `captureToImage()`: Compose-API рисует только корневой узел
      (нав-/статусбар не попадают), `UiDevice` даёт framebuffer целиком.
      *(Smoke-тест позже заменён Сценарием 1; валидация PNG —
      `check(bitmap.compress(...))` вместо `PNG_SIGNATURE`.)*
- [x] Фикстура smoke: `DemoExercise` ×6 + `DemoEntry` ×8; имена **не
      совпадают** со стандартными 13 (иначе `importData` дал бы дубли).
      JVM-покрытия записи нет (`Bitmap.compress` в `app/src/test/`
      недоступен без моков).

### Этап 4 — Makefile

- [x] Переменные `screenshots`-блока: `DEVICE_PNG_DIR`, `META_DIR`,
      `SCREENSHOTS_COMPONENT`, `LOCALES ?= ru-RU en-US`
      (flavors не добавляют `applicationIdSuffix` — путь фиксирован).
- [x] `screenshots: _ensure_secrets` — `:app`-конфиг валится на
      `checkNotNull(...KEYSTORE_FILE...)` для любой задачи с
      `targetProjectPath=":app"`.
- [x] Прогон **ручной**: build → install -r -t → `adb root` → цикл
      `set-app-locales` / `sleep 5` / `am instrument -w -e locale` /
      `mkdir -p` / `adb pull $DEVICE_PNG_DIR/$locale/.` → reset локали
      пустым списком. Успех — по `^OK` (`am instrument -w` всегда
      exit 0). Не `connectedDebugAndroidTest`: AGP удаляет APK вместе
      с каталогом PNG (см. «Принципы и ограничения»). `sleep 5` —
      буфер на тост IME (его также переждывает `captureAndValidate`,
      Этап 7).
- [x] Валидация `LOCALES` через `$(error …)` (пустое и опечатки падают
      явно); `set-app-locales` — `|| exit 1`; `adb pull` каталога —
      `/.` на конце (иначе вложенный `phoneScreenshots/<locale>/`).
- [x] `screenshots-ru/en` — single-line proxy `$(MAKE) screenshots
      LOCALES=…`; `.PHONY` + `## <имя>: <описание>` для `make help`.

### Этап 5 — Документация и проверка

- [x] AGENTS.md в **двух** местах: «Tests» (модуль, запуск
      `make screenshots{,-ru,-en}`) и «Project Structure»
      (`:screenshot-tests` рядом с `:app`). Detekt на модуль
      не подключаем.
- [x] Прогон: `adb devices` → `make emulator-fast` →
      `make screenshots-ru` + `-en` → PNG в
      `fastlane/metadata/android/<locale>/images/phoneScreenshots/`
      (следующий прогон перезаписывает); `format`/`lint`/`test`
      зелёные; `file <png>` — валидный PNG.
- [x] **Канон про хранение PNG** (сюда ссылаются Контекст, Критерии
      завершения и Этап 8): каталоги `images/phoneScreenshots/`
      появились в репозитории при съёмке Этапа 7 (12 PNG:
      6 × ru-RU/en-US); `.gitignore` уже разрешает хранение —
      `!fastlane/metadata/android/**/*.png`, `git add` после
      `make screenshots` коммитит PNG без дополнительных правил.

### Этап 6 (опционально)

- [ ] Skill `.opencode/skills/kotlin-screenshot-testing/SKILL.md` —
      «как писать сценарии скриншотов» (фикстуры, порядок
      `waitForIdle` после вставки в БД, имена PNG
      `<номер>-<описание>`, локализация полей фикстуры). Триггер —
       появление второго сценария (Этап 7); пока ни один сценарий
       Этапа 7 не реализован, skill **не обязателен**.

### Этап 7 — Сценарии скриншотов

Общие правила, действующие для каждого сценария:

- [x] **Smoke-@Test заменён Сценарием 1**: capture, валидация PNG
      и ассерт локали переехали в общие хелперы, `importFixture`
      импортирует только записи (иначе был бы лишний `0-smoke.png`).
- [x] **Только стандартные упражнения:** `importData(exercises =
      emptyList())`, имена — `getString(R.string.exercise_*)` в локали
      прогона (связь резолвится по `name.trim().lowercase()`).
- [x] **Даты и порядок:** `daysAgoMillis(days)` =
      `LocalDate.now().minusDays(days).atStartOfDay(zone)` (DST-безопасно),
      `createdAt = daysAgoMillis(1) + index * 60_000L` — внутри дня записи
      обратно-хронологичны (`ORDER BY date DESC, created_at DESC`),
      последнее выполненное вверху группы.
- [x] **Имя PNG** `<номер>-<slug>.png` по порядку сценариев плана.
- [x] **Один `@Test` на сценарий:** фикстура — Given в теле теста
      (`importFixture(baseEntries())` / `overviewEntries()`), общий
      `@Before` (тег локали + очистка БД), общий `@After tearDown`;
      общий код — в хелперах (`waitFor`, `openOverview`,
      `openFilledCreateForm`, `selectRepsExercise`, `hideIme`,
      `captureAndValidate`).
- [x] **Перед capture** — `waitForIdle()` + `waitFor(matcher)` на
      узел-якорь экрана (`waitUntil(5_000)`) и `delay(5_000)` на
      транзиентные оверлеи: тост Gboard «Changed the keyboard
      language…» в дереве доступности и `dumpsys window` не ловится
      (проверено), переждывается гарантированный срок жизни
      (LENGTH_LONG ≈3,5 с).
- [x] **Комментарии локализованы** (ru/en — видны в `CommentRow` и
      меняют высоту строки): пары выбирает `localizedComment(ru, en)`
      по `localeTag`.

#### Сценарий 1 — `1-workouts-list.png` (главный экран)

Три дня, 9 записей. Таблица в порядке **выполнения**; `d-N` — N дней
назад относительно сегодня.

| День | `R.string` | ru / en | sets | reps | durationSec | Комментарий ru / en |
| --- | --- | --- | --- | --- | --- | --- |
| d-1 | `exercise_lunges` | Выпады / Lunges | 2 | 20 | — | — |
| d-1 | `exercise_squats` | Приседания / Squats | 4 | 15 | — | — |
| d-2 | `exercise_pullups` | Подтягивания / Pull-ups | 2 | 6 | — | — |
| d-2 | `exercise_australian_pullups` | Австралийские подтягивания / Australian pull-ups | 2 | 12 | — | — |
| d-2 | `exercise_crunches` | Скручивания / Crunches | 1 | 30 | — | — |
| d-2 | `exercise_plank` | Планка / Plank | 1 | — | 45 | добиваю пресс / finishing off the core |
| d-3 | `exercise_pushups` | Отжимания / Push-ups | 1 | 20 | — | новый рекорд, мощно / new personal record, felt strong |
| d-3 | `exercise_bars` | Отжимания на брусьях / Bar dips | 1 | 4 | — | — |
| d-3 | `exercise_bars` | Отжимания на брусьях / Bar dips | 1 | 4 | — | — |

- [x] Зафиксировано и проверено: брусья — **две отдельные записи
      1×4** (группа d-3 — три строки), планка — `durationSec = 45`
      без reps последней в свой день; все записи `weightKg = null`.
      Порядок сверху вниз (следствие сортировки): d-1 Приседания,
      Выпады; d-2 Планка, Скручивания, Австралийские подтягивания,
      Подтягивания; d-3 Брусья (2-я), Брусья (1-я), Отжимания.
      PNG в обеих локалях, ru/en различаются, в кадре нет IME-тоста
      и обрезанных строк.

#### Сценарии 2–6 — создание записи и экран обзора

Один пользовательский поток в пяти кадрах: FAB → заполненная форма
создания → «Сохранить» → главный экран с новой записью → кнопка
«donut» → Обзор (Статистика «Неделя» → Рекорды → Активность).
Пять PNG, пять `@Test` (общее правило Этапа 7 «один `@Test` на
сценарий»), фикстура импортируется первым шагом каждого теста,
общий `@After tearDown` (`deleteAllData()`).

Зависимость: сценарии 2–6 переиспользуют фикстуру Сценария 1
(стандартные упражнения, `importData(exercises = emptyList())`,
относительные даты, `createdAt = daysAgoMillis(1) + index * 60_000L`) —
общие приватные хелперы `baseEntries()` / `overviewEntries()`.

| Шаг | Действие | PNG |
| --- | --- | --- |
| 1 | FAB → форма: Отжимания, 12 повторов, 5 кг, комментарий | `2-create-new-entry.png` |
| 2 | «Сохранить» → главный экран, скролл к новой записи | `3-new-entry-saved.png` |
| 3 | «donut» → Статистика, период «Неделя» | `4-week-stats.png` |
| 4 | вкладка «Рекорды» (режим «За подход» — дефолт) | `5-records-per-set.png` |
| 5 | вкладка «Активность» | `6-activity-screen.png` |

Фикстуры:

- `baseEntries()` — 9 записей Сценария 1 (d-1…d-3). Импортируют
  сценарии 1, 2, 3.
- `overviewEntries()` = `baseEntries` + запись **сегодня**:
  `exercise_pushups`, 1 × 12, 5 кг, комментарий из пары ru/en ниже.
  Импортируют сценарии 4–6.

Запись сегодня нужна именно в фикстуре 4–6, а не «создаётся в UI»:
`tearDown` каждого теста делает `deleteAllData()`, записи сценариев
2–3 не переживают свой тест. Без неё обзор снимался бы с другими
данными, чем показывает `3-new-entry-saved.png`, а на пустой БД — с
пустыми состояниями.

Общее для 2–6:

- **Комментарий ru/en**: «пробую с утяжелением» / «trying it
  weighted» — хелпер `weightedEntryComment()` поверх
  `localizedComment(ru, en)`, выбор по `localeTag` (правило
  «Комментарии локализованы» из общих правил Этапа 7). Ввод
  кириллицей работает: `performTextReplacement` идёт через semantics,
  а не через adb (канон — `ExerciseCreateScreenTest.kt:165`).
- **Клавиатура не попала в кадр.** `performTextReplacement` поднимает
  IME, а та перекрывает низ формы и половину кнопки «Сохранить».
  Гасится хелпером `hideIme()`
  (`InputMethodManager.hideSoftInputFromWindow` на
  `activity.window.decorView.windowToken` + `waitForIdle`): в
  Сценарии 2 — после заполнения формы, в Сценарии 3 — после
  сохранения (IME пережил бы навигацию). Навигации «назад» нет:
  экран создания закрывается только кнопкой «Сохранить»
  (`viewModel.save(onSaved = onBackClick)`, `EntryCreateScreen.kt:73`).

##### Сценарий 2 — `2-create-new-entry.png` (заполненная форма)

- [x] FAB (`add_workout`, `MainScreen.kt:252`) → форма
      (`entry_create_title`). «Отжимания» через `selectRepsExercise`:
      клик по полю `entry_field_exercise`, `waitFor(«Выпады»)` —
      признак открытого меню, доскролл опции в открытом попапе
      (`onAllNodes(hasScrollAction()).onLast().performScrollToNode(...)`)
      и клик по **видимой** опции (клик по невидимому пункту
      применялся через раз), `waitFor` поля повторов — признак
      выбора. Точное совпадение `onNodeWithText` не путает
      «Отжимания» с «на коленях» / «на брусьях».
- [x] Повторы `12` / вес `5` / комментарий — по label полей
      (`performTextReplacement`, канон `EntryCreateScreenTest.kt:255`);
      перед capture — `hideIme()` + `performScrollToNode` на
      комментарий (форма — `Column(verticalScroll)`). Не заполняем
      подходы (`setsText = "1"` — дефолт) и дату/время (дефолты
      годятся): предзаполнение из истории не мешает (гварды
      `repsText.isEmpty()` / `weightTouched`), вес `5` проходит
      нормализацию (`draftOf` отбрасывает только `<= 0`).

##### Сценарий 3 — `3-new-entry-saved.png` (главный экран, новая запись)

- [x] «Сохранить» (`entry_save`, `SaveBottomBar`) → `waitFor` FAB →
      `hideIme()` → `performScrollToIndex(0)` +
      `onAllNodesWithText(comment).onFirst().assertIsDisplayed()`.
      Кадр: строка «Отжимания» вверху списка — группа «Сегодня» без
      заголовка (`SectionDateFormatter`: `date == today -> null`),
      с повторами, блоком веса (`WeightRow`, рендерится при
      `weightKg > 0`) и комментарием (`CommentRow`).

##### Сценарий 4 — `4-week-stats.png` (Обзор → Статистика, Неделя)

- [x] Кнопка «donut» (`a11y_open_overview`, `MainScreen.kt:243`) —
      в TopAppBar, не в bottom-tabs (`Screen.Overview` вне вкладок);
      далее вкладка «Статистика» кликается явно: `lastOverviewTab`
      в DataStore переживает `deleteAllData()`, а порядок `@Test`
      произволен (сценарий 5 сохраняет «Рекорды»).
- [x] Период «Неделя» (`StatsScreen.kt:461`) кликается явно —
      идемпотентно (дефолт `StatsPeriod.WEEK`), но кадр не зависит
      от дефолта. Окно **скользящее** — 7 дней до сегодня
      (`rolling-stats-periods`): захватывает d-1…d-3 и сегодня в любой
      день календарной недели — донат из четырёх категорий (до ченджа
      ISO-понедельник вырождался в один сектор). Пикер виден на
      фикстуре глубже самой ранней записи (`visiblePeriods`);
      якорь: `overview_stats_empty` **не** отображается.

##### Сценарий 5 — `5-records-per-set.png` (Обзор → Рекорды)

- [x] Таб «Рекорды» (`OverviewScreen.kt:228`); режим «За подход»
      (`RecordsMode.PER_SET`) — дефолт, второй сегмент не трогаем.
      Якорь: `records_empty` не отображается (новая запись рекордом
      не станет, но участвует в расчёте и heatmap).

##### Сценарий 6 — `6-activity-screen.png` (Обзор → Активность)

- [x] Таб «Активность»; период не переключаем — дефолт
      `ActivityPeriod.MONTH` (в имени PNG не отражён). Якорь — легенда
      хитмапа `activity_less` (надёжнее plurals-текстов карточки серии).

Проверка сценариев 1–6 (6 PNG суммарно: `1-workouts-list` + пять из
этого блока):

- [x] `make screenshots-ru` / `-en`: 6 PNG в каждой локали, ru/en
      визуально различаются; кадры соответствуют сценариям — форма
      заполнена без клавиатуры, новая запись сверху с весом
      и комментарием, стат-кадры непустые («Неделя», «За подход»).
      `make format && make lint && make test` зелёные; после прогона
      `make android-test` БД чистая (`tearDown`).

### Этап 8 — Скриншоты в README

Зависит от Этапа 7: PNG должны лежать по фиксированным путям, иначе
`src` в README указывает в пустоту. Формат секции: markdown-таблица
из двух строк, первая — заголовки-подписи, вторая — `<img>`.

- [x] Секция `## Скриншоты` в `README.md` — сразу после
      `<!-- END_VERSIONS -->`, первой содержательной секцией файла.
      Таблица из двух строк: заголовки-подписи шести сценариев
      («Список тренировок» … «Активность») и `<img alt=""` в том же
      порядке. Только `ru-RU` (README русский, `en-US` лежит в
      fastlane-метаданных), путь относительный от корня репо:
      `./fastlane/metadata/android/ru-RU/images/phoneScreenshots/<файл>`.
      markdownlint не мешает: `MD013`/`MD033` выключены,
      `MD055`/`MD056` включены — колонок шесть в обеих строках.
- [x] **Скрипт-перегенератор не нужен.** Он уместен, когда имена PNG
      нестабильны (например, `fastlane screengrab` дописывает временную
      метку: `1-demoList_1786560117567.png`, и путь каждый прогон новый).
      У нас имя задаёт сам тест, оно стабильно (`1-workouts-list.png`),
      путь не меняется — перегенератор был бы мёртвым кодом. Вернуться
      к нему (`make update_readme` + `scripts/update_readme.py` + тест
      в `scripts/*_test.py`, который подхватит `make scripts-test`) —
      если PNG начнут получать временные метки или сценарии станут
      переезжать/переименовываться.
- [x] Проверка: `make lint` (markdownlint) зелёный; все шесть путей из
      README резолвятся в существующие файлы; `git status --porcelain
      fastlane/` пуст — PNG уже в git (канон хранения — Этап 5).

## Критерии завершения

- [x] `:screenshot-tests:assembleDebug` собирается; `tasks --all`
      показывает ровно `connectedDebugAndroidTest` (доказательство
      отсутствия `productFlavors` в тест-модуле).
- [x] `make screenshots` end-to-end: build → install → `am instrument`
      (UI на целевой локали — ассерт на `R.string` зелёный) → PNG
      в `fastlane/metadata/...`, пары ru/en визуально различаются.
      `LOCALES=fr-FR` и `LOCALES=` падают явно.
- [x] Существующие `make test`, `make android-test`, `make format`,
      `make lint` не сломаны; detekt-лимит не повышен.

## Не входит (открытые задачи)

- Остальные сценарии (`ExercisesScreen`, `ArchivedExercisesScreen`,
  `AppDataScreen` и т.д.) — продолжение Этапа 7 по мере утверждения
  (запланированные сценарии 1–6 реализованы все).
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
- Инфраструктурный прецедент через `docs/plans/` — сам этот документ.
  То есть и OpenSpec, и `docs/plans/`
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
