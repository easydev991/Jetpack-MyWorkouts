# Скриншот-тесты — модуль `:screenshot-tests`

Скриншоты маркетплейса пишутся отдельным gradle-модулем
`:screenshot-tests` (`com.android.test`, `targetProjectPath = ":app"`,
namespace `com.myworkouts.screenshots`). Правила 1–15 SKILL.md
применимы полностью (JUnit 4 + Compose v2 API). Канон реализации —
`ScreenshotsTest` в этом модуле.

## Что отличается от androidTest в `:app`

- Другой gradle-модуль — запуск **не** через `make android-test` и не
  даёт ему `test_report.py`. Свой запуск: `make screenshots-ru` /
  `make screenshots-en` (или `make screenshots LOCALES=...`).
- Пайплайн и сборка/установка APK — в `Makefile`; этот файл — только
  о том, что агенту важно при *написании* сценария.

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

`localeTag` используется только в фикстуре (выбор русских/английских
полей). Ассерт на UI-локаль в `@Test` — через ресурс приложения,
например `R.string.workouts_title` (см. питфолл ниже). Никаких
`LocaleTestRule`, рефлексии над `ActivityManagerNative` или
`am instrument -e locale` — они не меняют пер-апп конфигурацию
или тянут скрытые API.

## Данные через production-репозиторий процесса

Канон — `MainScreenDetailCardRecreateTest.kt`:

```kotlin
val app = ApplicationProvider.getApplicationContext<MyWorkoutsApplication>()
runBlocking {
    assertTrue(app.repository.deleteAllData().isSuccess)
    val imported = app.repository.importData(
        exercises = demoExercises(localeTag),
        entries = demoEntries(localeTag)
    )
    assertTrue("Импорт demo-данных не удался: $imported", imported.isSuccess)
}
composeTestRule.waitForIdle()
```

Один инстанс Room = один `InvalidationTracker`: реактивный Flow
`MainViewModel.uiState` видит вставку до истечения окна
`WhileSubscribed(5000)` — без навигационных хаков. Подробности —
`references/activity-integration.md` («Паттерн работы с БД …»).

Имена demo-упражнений **не должны** совпадать со стандартными 13
`deleteAllData` восстанавливает стандартный набор, `importData`
матчится по имени — совпадение создаёт дубли в каталоге. Фикстура
строится параллельными полями `nameRu/nameEn`, `commentRu/commentEn`
(как `loadDemoData` в референсном проекте).

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

## Capture и запись PNG

```kotlin
val bitmap: Bitmap =
    checkNotNull(
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).takeScreenshot()
    ) { "..." }
val dir = File(
    checkNotNull(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)) { ... },
    "screenshots/$localeTag"
)
dir.mkdirs()
val file = File(dir, "0-smoke.png")
file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
```

**`UiDevice.takeScreenshot()` для нав-/статусбара** — Compose-API
рисует только корневой узел в offscreen-Canvas, поэтому статусбар и
навбар в PNG не попадают.

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

## Зависимости модуля (кратко)

Свои, не копировать из `app/build.gradle.kts`. Сейчас в
`screenshot-tests/build.gradle.kts`:

- `implementation(project(":app"))` — даёт `MainActivity`,
  `WorkoutDatabase`, `RoomWorkoutDataRepository`, `MyWorkoutsApplication`.
- `androidx.test:runner` — **явно** (1.7.0): `AndroidJUnitRunner` НЕ
  приходит транзитивно от `androidx-junit`/`ui-test-junit4`
  (в `debugCompileClasspath` только `core`+`monitor`); без явного —
  `Unresolved reference 'AndroidJUnitRunner'`.
- Compose BOM + `ui-test-junit4`.

Плагин `com.android.test` алиасится в `libs.versions.toml`
(`android-test = { id = "com.android.test", version.ref = "agp" }`)
и **дополнительно** объявляется `apply false` в корневом
`build.gradle.kts` — иначе резолв в модуле падает
(«plugin is already on the classpath with an unknown version»,
AGP уже на classpath через `:app`).
