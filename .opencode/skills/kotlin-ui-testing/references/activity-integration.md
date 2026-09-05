# Activity integration — тесты с реальной MainActivity

Интеграционные UI-тесты запускают реальную `MainActivity` и полный стек:
Activity + Compose + реальный Room + ViewModel. Используются, когда
нужно проверить поведение экрана целиком: поиск, сортировка, ротация,
загрузка данных из БД.

Примеры: `ui/screens/events/WorkoutsScreenSearchVisibilityUiTest.kt`,
`ui/screens/events/WorkoutsScreenSortByTimeOfDayUiTest.kt`,
`ui/screens/createedit/CreateEditHasChangesRegressionUiTest.kt`.

## Скелет

```kotlin
@RunWith(AndroidJUnit4::class)
class WorkoutsScreenSomeBehaviorUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        runBlocking { dao.deleteAllWorkouts() }   // чистый стейт перед тестом
    }

    @After
    fun tearDown() {
        runBlocking { dao.deleteAllWorkouts() }   // чистота после теста
    }

    @Test
    fun when_condition_then_result() {
        runBlocking { insertTestData() }
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.search))
            .assertIsDisplayed()
    }

    private suspend fun insertTestData() {
        dao.insertWorkout(WorkoutEntity(...))
    }
}
```

Ключевое:
- `createAndroidComposeRule<MainActivity>()` — реальная Activity
- Вставка данных — `runBlocking { dao.insertWorkout(...) }`
- После вставки — `composeTestRule.waitForIdle()`, иначе UI не увидит данные
- Очистка БД в `@Before` и `@After` — изолированность тестов
- Обращение к Activity — `composeTestRule.activity`

## Реальный ViewModel с in-memory Room

`CreateEditHasChangesRegressionUiTest` — гибрид: реальная in-memory БД
+ ViewModel создаётся вручную с реальным репозиторием:

```kotlin
@Before
fun setUp() {
    database = Room
        .inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            MyWorkoutsDatabase::class.java
        )
        .allowMainThreadQueries()
        .build()
    repository = WorkoutRepositoryImpl(database.workoutDao())
}

@Test
fun checkHasChanges_afterLoadItem_doesNotFalselyDetectTimeOfDayChange() {
    // Given: событие с известным временем суток (14:30)
    val timestamp = LocalDate.of(2026, 5, 1)
        .atTime(LocalTime.of(14, 30))
        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val insertedId = runBlocking { repository.insertWorkout(workout) }
    val viewModel = WorkoutEditScreenViewModel(
        repository = repository,
        resourceProvider = createTestResourceProvider(),
        savedStateHandle = SavedStateHandle(mapOf("workoutId" to insertedId)),
        analyticsService = AnalyticsService(listOf(NoopAnalyticsProvider()))
    )

    // When: открываем экран редактирования без изменения данных
    composeTestRule.setContent {
        MyWorkoutsTheme {
            WorkoutEditScreen(
                workoutId = insertedId,
                viewModel = viewModel,
                analyticsService = AnalyticsService(listOf(NoopAnalyticsProvider()))
            )
        }
    }
    composeTestRule.waitForIdle()

    // Then: кнопка «Сохранить» неактивна
    composeTestRule.onNodeWithText(context.getString(R.string.save)).assertIsNotEnabled()
}
```

## Ротация (recreation Activity)

Полный тест — `references/EXAMPLE.md` (раздел «Интеграционный тест
с MainActivity», метод `when_device_rotates_then_search_field_remains_displayed`).
Паттерн: вставить данные → `waitForIdle()` → сменить ориентацию →
assert → вернуть ориентацию:

```kotlin
val activity = composeTestRule.activity
val originalOrientation = activity.requestedOrientation
activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
composeTestRule.waitForIdle()
// ... asserts ...
activity.requestedOrientation = originalOrientation
```

## Проверка сортировки через boundsInRoot

```kotlin
@Test
fun sameDateDifferentTimeOfDay_ascOldFirst_thenEarlierTimeIsAboveLaterTime() {
    composeTestRule.waitForIdle()

    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.sort))
        .performClick()
    composeTestRule
        .onNodeWithText(context.getString(R.string.old_first))
        .performClick()
    composeTestRule.waitForIdle()

    val nodeA = composeTestRule.onNodeWithText(titleA)
    val nodeB = composeTestRule.onNodeWithText(titleB)
    nodeA.assertIsDisplayed()
    nodeB.assertIsDisplayed()

    val topA = nodeA.fetchSemanticsNode("nodeA").boundsInRoot.top
    val topB = nodeB.fetchSemanticsNode("nodeB").boundsInRoot.top
    assertTrue(
        "A (09:00) должен быть выше B (18:00) при сортировке «сначала старые», " +
            "но topA=$topA, topB=$topB",
        topA < topB
    )
}
```

## Поиск и скрытие элементов

Полные тесты — `references/EXAMPLE.md` (раздел «Интеграционный тест
с MainActivity»). Суть: поле поиска скрыто при <= 4 элементах
(`assertDoesNotExist`), появляется при 5+ (`assertIsDisplayed`),
остаётся видимым при введённом запросе даже после удаления элементов.
