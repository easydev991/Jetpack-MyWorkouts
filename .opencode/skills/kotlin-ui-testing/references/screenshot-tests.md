# Скриншот-тесты — модуль `:screenshot-tests`

Скриншоты маркетплейса пишутся отдельным gradle-модулем
`:screenshot-tests` (`com.android.test`, `targetProjectPath = ":app"`,
namespace `com.myworkouts.screenshots`). Правила 1–15 SKILL.md
применимы полностью (JUnit 4 + Compose v2 API). Канон реализации —
`ScreenshotsTest` (шесть сценариев, один `@Test` на сценарий).

## Пайплайн и окружение

- **Без fastlane.** Офлайн-pipeline `make` + `adb`: `adb root` →
  build → install → очистка каталога PNG на девайсе → цикл
  (`set-app-locales` → `am instrument` → `adb pull`) → сброс локали.
  Никаких `Screengrabfile`/Ruby и зависимости
  `tools.fastlane:screengrab`. Запуск — `make screenshots-ru` /
  `make screenshots-en` (или `make screenshots LOCALES=...`), не
  `make android-test` и без `test_report.py`.
- **Эмулятор — образ без google_play и API 33+** (для
  `cmd locale set-app-locales`): на google_play-образах `adb root`
  недоступен. На API 30+ shell не читает
  `/sdcard/Android/data/<pkg>/` — перед pull-циклом нужен `adb root`.
- **Прогон — ручной `am instrument`, НЕ `connectedDebugAndroidTest`:**
  AGP после connected-задачи удаляет оба APK, а вместе с приложением —
  его app-specific каталог с PNG (проверено на AGP 9.4.1). Успех в
  Makefile определяется по `^OK` в выводе `am instrument -w` — он
  всегда exit 0.

## Локаль (per-app, без LocaleTestRule)

UI-локаль приложения переключается **снаружи** прогона —
`adb shell cmd locale set-app-locales com.myworkouts --user 0
--locales <tag>` (компонент канона в Makefile). Тег передаётся
runner-аргументом `locale` и читается **один раз** в `@Before`:

```kotlin
private lateinit var localeTag: String

@Before
fun setup() {
    localeTag = InstrumentationRegistry.getArguments().getString("locale") ?: "ru-RU"
    ...
}
```

`localeTag` используется в фикстуре (выбор русских/английских полей)
и в пути записи PNG (`screenshots/$localeTag`). Ассерт на UI-локаль
в `@Test` — через ресурс приложения,
например `R.string.workouts_title` (см. питфолл ниже). Никаких
`LocaleTestRule`, рефлексии над `ActivityManagerNative` или
`am instrument -e locale` — они не меняют пер-апп конфигурацию
или тянут скрытые API.

## Данные: production-репозиторий, стандартный каталог

Канон — `MainScreenDetailCardRecreateTest.kt`:

```kotlin
private fun importFixture(entries: List<BackupEntry>) {
    val app = ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()
    runBlocking {
        val imported = app.repository.importData(
            categories = emptyList(),
            exercises = emptyList(),
            entries = entries
        )
        assertTrue("Импорт фикстуры не удался: $imported", imported.isSuccess)
    }
    composeTestRule.waitForIdle()
}
```

Один инстанс Room = один `InvalidationTracker`: реактивный Flow
`MainViewModel.uiState` видит вставку до истечения окна
`WhileSubscribed(5000)` — без навигационных хаков. Подробности —
`references/activity-integration.md` (вводная часть: production-БД
через `app.repository`).

Правила фикстуры:

- **Упражнения — только стандартные 13**: `deleteAllData()` в
  `@Before` восстанавливает каталог, `importData` зовётся с
  `exercises = emptyList()`. Имена резолвятся
  `getString(R.string.exercise_*)` в локали прогона и матчатся
  каталогом по `name.trim().lowercase()` — любые собственные имена
  создали бы дубли.
- **Даты DST-безопасно**: `date = LocalDate.now().minusDays(days)
  .atStartOfDay(zone)`; `createdAt = daysAgoMillis(1) + index *
  60_000L` — внутри дня записи обратно-хронологичны
  (`ORDER BY date DESC, created_at DESC`), последняя выполненная
  вверху группы.
- **Поля локализованы** хелпером `localizedComment(ru, en)` по
  `localeTag`: комментарии видны в кадре и меняют высоту строки.
- **Нужные для кадра записи — в фикстуре, не «создаются в UI»**:
  `tearDown` каждого теста стирает БД, записи соседних тестов не
  переживают свой прогон.

## `@After` — сбросить БД

**Обязательно** после каждого `@Test`:

```kotlin
@After
fun tearDown(): Unit = runBlocking {
    val app = ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()
    assertTrue("Не удалось очистить demo-данные", app.repository.deleteAllData().isSuccess)
}
```

Без уборки demo-записи остаются в production-БД процесса и роняют
`EntryListLiveUpdateIntegrationTest` в `make android-test`
(воспроизведённый кейс на AGP 9.4.1: 2 падения с waitUntil-таймаутом
на `editedOldName`/`editedNewName` после возврата на главный экран).

## Конвенции сценариев

- **Один `@Test` на сценарий**; фикстура импортируется первым шагом
  в теле теста (общий `@Before` — только локаль + очистка БД).
- **Имя PNG `<номер>-<slug>.png`** по порядку сценариев
  (`1-workouts-list.png` … `6-activity-screen.png`). Имена стабильны —
  их задаёт тест, поэтому скрипт-перегенератор README не нужен.
- **Якорь отрисовки перед capture**: ожидание узла-якоря экрана
  (`waitUntil(5_000)`, хелпер `waitFor(matcher)`) и
  `runBlocking { delay(5_000) }` перед снимком. `delay` переждивает
  системный тост Gboard «Changed the keyboard language…», который
  ложится на bottom-bar: в дереве доступности и `dumpsys window` его
  нет (проверено), остаётся переждать гарантированный срок жизни
  (LENGTH_LONG ≈ 3,5 с). Это срок жизни тоста, а не «реальный таймаут
  сценария» из правила 11 SKILL.md — тот `delay` воспроизводит окно
  `WhileSubscribed(5000)` (сейчас — `SubscriptionTimeoutRule` +
  `delay(300L)` в `ExerciseCreateLiveUpdateIntegrationTest`):
  другая причина, другая длина.
- **IME гасится перед кадром** хелпером `hideIme()`
  (`InputMethodManager.hideSoftInputFromWindow` на windowToken декора
  + `waitForIdle`): ввод текста поднимает клавиатуру, она перекрывает
  низ формы.
- **Текст в поля — `performTextReplacement`** (идёт через semantics,
  не через adb): кириллица вводится корректно.
- **Вкладки и периоды кликаются явно**, даже если значение — дефолт:
  `lastOverviewTab` переживает `deleteAllData()` в DataStore, а
  порядок `@Test` произволен.
- **Выбор опции в дропдауме**: точное совпадение `onNodeWithText` не
  путает «Отжимания» с «на коленях»/«на брусьях»; опцию ниже первого
  экрана доскролливать в открытом попапе (последний scrollable в
  дереве — `onAllNodes(hasScrollAction()).onLast()`) и кликать
  **видимой** — клик по невидимому пункту применялся через раз.

## Capture и запись PNG

```kotlin
val bitmap: Bitmap =
    checkNotNull(
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot()
    ) { "UiDevice.takeScreenshot() вернул null — framebuffer недоступен" }
val dir = File(
    checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)) { ... },
    "screenshots/$localeTag"
)
dir.mkdirs()
val file = File(dir, fileName)
file.outputStream().use {
    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) { "PNG не записан: $file" }
}
```

**`UiDevice.takeScreenshot()` для нав-/статусбара** — Compose-API
рисует только корневой узел в offscreen-Canvas, поэтому статусбар и
навбар в PNG не попадают. Валидация записи — `check(bitmap.compress(...))`:
`compress` возвращает false при ошибке, обрезанный файл мимо него не
пройдёт.

Контекст — `InstrumentationRegistry.getInstrumentation().targetContext`
(канон `MainScreenDetailCardRecreateTest`); `getContext()` в
`com.android.test`-модуле может указывать на instrumentation-package,
и `adb pull` потом молча не найдёт файлы. PNG кладётся в
`getExternalFilesDir(Pictures)/screenshots/<locale>/...`
(путь фиксирован для обоих flavors — `applicationIdSuffix` в них
нет). Отдельного writer-класса нет — запись инлайнится в тесте
(YAGNI: extracting при появлении второго сценария тривиален).

## Ассерт на UI-локаль: питфолл «Expected at most 1 node»

`workouts_title` (заголовок TopAppBar главного экрана) совпадает с
подписью вкладки bottom-bar — строка встречается на экране дважды.
`onNodeWithText(...).assertIsDisplayed()` падает; канон:

```kotlin
composeTestRule
    .onAllNodesWithText(activity.getString(R.string.workouts_title))
    .onFirst()
    .assertIsDisplayed()
```

Заодно это locale-sensitive ассерт: в en-прогоне `getString`
резолвит «Workouts», находится только при применившейся пер-апп
локали — доказывает, что пер-апп локаль дошла до UI.

## Зависимости и конфигурация модуля (кратко)

Свои, не копировать из `app/build.gradle.kts`. Сейчас в
`screenshot-tests/build.gradle.kts`:

- `implementation(project(":app"))` — даёт `MainActivity`,
  `WorkoutDatabase`, `RoomWorkoutDataRepository`, `MyWorkoutsApplication`.
- `androidx-junit` — `implementation(libs.androidx.junit)`.
- `androidx.test:runner` — **явно** (1.7.0): `AndroidJUnitRunner` НЕ
  приходит транзитивно от `androidx-junit`/`ui-test-junit4`
  (в `debugCompileClasspath` только `core`+`monitor`); без явного —
  `Unresolved reference 'AndroidJUnitRunner'`.
- Compose BOM + `ui-test-junit4`, `androidx.test.uiautomator`.

Плагин `com.android.test` алиасится в `libs.versions.toml`
(`android-test = { id = "com.android.test", version.ref = "agp" }`)
и **дополнительно** объявляется `apply false` в корневом
`build.gradle.kts` — иначе резолв в модуле падает
(«plugin is already on the classpath with an unknown version»,
AGP уже на classpath через `:app`). Своих `productFlavors` у модуля
нет — `missingDimensionStrategy("distribution", "github")`, варианты
только `debug`/`release`.

## Хранение и переиспользование

- PNG коммитятся в git: `.gitignore` уже разрешает
  (`!fastlane/metadata/android/**/*.png`) — после `make screenshots`
  достаточно `git add`.
- Канон-путь — `fastlane/metadata/android/<locale>/images/phoneScreenshots/`;
  ru-RU дублируется в секции «Скриншоты» README по относительным
  путям от корня репо.
