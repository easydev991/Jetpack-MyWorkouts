# EXAMPLE — канонические примеры

Полные примеры тестов из реального кода Jetpack-MyWorkouts.
Файлы: `app/src/androidTest/java/com/myworkouts/...`

Все примеры в этом файле — **реальные** сокращённые фрагменты
существующих тестов. Сверяйся с ними при написании новых.

## Компонентный UI-тест (без Activity)

`ui/screens/exercises/ExercisesScreenTest.kt` — компонент
`ExercisesScreenContent` с готовым `ExercisesScreenState`,
v2 API `createComposeRule()`, `@RunWith(AndroidJUnit4::class)`
(все компонентные тесты в проекте аннотированы):

```kotlin
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExercisesScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun setContentWith(uiState: ExercisesUiState) {
        composeTestRule.setContent {
            ExercisesScreenContent(
                state = ExercisesScreenState(
                    uiState = uiState,
                    onAddExerciseClick = {}
                )
            )
        }
    }

    @Test
    fun exercises_screen_when_empty_then_shows_empty_state() {
        // Given
        setContentWith(ExercisesUiState.Success(exercises = emptyList()))

        // Then
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_list_empty))
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithText("Подтягивания")
            .assertDoesNotExist()
    }
}
```

## Компонентный тест с NavHost-harness и навигацией

Тот же `ExercisesScreenTest` — навигация проверяется через
минимальный `NavHost` с заглушкой destination (без неё
`navController.navigate(...)` бросит `IllegalArgumentException`):

```kotlin
@Composable
private fun ExercisesScreenWithNavHost(onControllerReady: (NavHostController) -> Unit = {}) {
    val navController = rememberNavController()
    onControllerReady(navController)
    NavHost(navController = navController, startDestination = "stub") {
        composable("stub") { /* Заглушка стартового маршрута */ }
        composable(Screen.Exercises.route) {
            ExercisesScreenContent(
                state = ExercisesScreenState(
                    uiState = ExercisesUiState.Success(exercises = listOf(pullups, plank)),
                    onAddExerciseClick = { navController.navigate(Screen.ExerciseCreate.route) }
                )
            )
        }
        composable(Screen.ExerciseCreate.route) {
            // Заглушка destination-маршрута.
        }
    }
}

@Test
fun exercises_screen_when_fab_clicked_then_navigates_to_exercise_create_route() {
    // Given — экран открыт через NavHost-harness
    lateinit var navController: NavHostController
    composeTestRule.setContent {
        ExercisesScreenWithNavHost(onControllerReady = { navController = it })
    }
    composeTestRule.runOnIdle {
        navController.navigate(Screen.Exercises.route)
    }
    composeTestRule.waitForIdle()

    // When — тап по FAB
    composeTestRule
        .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
        .performClick()

    // Then — активный маршрут сменился на форму создания
    composeTestRule.runOnIdle {
        assertEquals(
            "FAB должен навигировать на Screen.ExerciseCreate.route",
            Screen.ExerciseCreate.route,
            navController.currentDestination?.route
        )
    }
}
```

## Компонентный тест с реальной ViewModel поверх Room in-memory

`ui/screens/exercises/ExercisesLiveUpdateTest.kt` — реальная
`ExercisesViewModel`, реальный Room in-memory, `NavHost`-harness
(без `MainActivity`):

```kotlin
@RunWith(AndroidJUnit4::class)
class ExercisesLiveUpdateTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = listOf(
                    StandardExercise(name = "Подтягивания", category = ExerciseCategory.PULL)
                )
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exercisesScreen_whenReturnedQuickly_afterInsert_thenShowsNewExercise() {
        // Given — NavHost с реальной VM/Room и заглушкой
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            navController = rememberNavController()
            JetpackMyWorkoutsTheme {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Exercises.route
                ) {
                    composable(Screen.Exercises.route) {
                        ExercisesScreen(
                            viewModel = viewModel(
                                factory = ExercisesViewModel.factory(repository)
                            ),
                            onAddExerciseClick = { navController.navigate("stub") }
                        )
                    }
                    composable("stub") { /* Заглушка другого экрана */ }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_title))
            .assertIsDisplayed()

        // When — уход на заглушку, вставка в БД, возврат назад
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
            .performClick()
        composeTestRule.waitForIdle()
        runBlocking {
            repository.addExercise(
                Exercise(
                    id = 0L,
                    name = "Жим",
                    category = ExerciseCategory.PUSH,
                    isCustom = true,
                    comment = null,
                    createdAt = 1L,
                    measuredBy = ExerciseMeasuredBy.REPS
                )
            )
        }
        composeTestRule.runOnIdle {
            navController.popBackStack()
        }
        composeTestRule.waitForIdle()

        // Then — каталог показывает новое упражнение
        composeTestRule.onNodeWithText("Жим").assertIsDisplayed()
    }
}
```

## DAO-тест (Room in-memory)

`data/db/ExerciseDaoTest.kt` — реальный DAO каталога упражнений.
Покрывает поиск по подстроке, сортировку `is_custom DESC,
created_at DESC, name COLLATE NOCASE`, tie-break по имени:

```kotlin
@RunWith(AndroidJUnit4::class)
class ExerciseDaoTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var dao: ExerciseDao

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        dao = database.exerciseDao()

        runBlocking {
            listOf(
                ExerciseEntity(name = "Подтягивания", category = "PULL", isCustom = false, createdAt = 0L),
                ExerciseEntity(name = "Подъёмы ног в висе", category = "CORE", isCustom = false, createdAt = 0L),
                ExerciseEntity(name = "Отжимания", category = "PUSH", isCustom = false, createdAt = 0L),
                ExerciseEntity(name = "Приседания", category = "LEGS", isCustom = false, createdAt = 0L)
            ).forEach { dao.insert(it) }
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun search_substring_matchesCaseInsensitive() =
        runBlocking {
            // When
            val result = dao.observeExercises("под").first()

            // Then
            val names = result.map { it.name }
            assertEquals(2, result.size)
            assertTrue(
                "Expected 'Подтягивания' and 'Подъёмы ног в висе', got $names",
                names.containsAll(listOf("Подтягивания", "Подъёмы ног в висе"))
            )
        }
}
```

## Репозиторий-интеграция (Room in-memory)

`data/repository/RoomWorkoutDataRepositoryTest.kt` — реальный
`RoomWorkoutDataRepository(database, standardExercises)`,
проверяет CRUD, маппинг Entity ↔ Domain, Flow invalidation:

```kotlin
@RunWith(AndroidJUnit4::class)
class RoomWorkoutDataRepositoryTest {
    private lateinit var database: WorkoutDatabase
    private lateinit var repository: RoomWorkoutDataRepository

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    WorkoutDatabase::class.java
                ).allowMainThreadQueries()
                .build()
        repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = listOf(
                    StandardExercise(name = "Подтягивания", category = ExerciseCategory.PULL),
                    StandardExercise(
                        name = "Планка",
                        category = ExerciseCategory.CORE,
                        measuredBy = ExerciseMeasuredBy.TIME
                    )
                )
            )
        runBlocking {
            database.exerciseDao().insert(
                ExerciseEntity(
                    name = "Подтягивания",
                    category = "PULL",
                    isCustom = false,
                    createdAt = 0L
                )
            )
            database.exerciseDao().insert(
                ExerciseEntity(
                    name = "Планка",
                    category = "CORE",
                    isCustom = false,
                    createdAt = 0L,
                    measuredBy = "TIME"
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addEntry_whenExerciseIdMissing_thenReturnsFailureAndTableStaysEmpty() =
        runBlocking {
            // Given — несуществующий exerciseId
            val bogusEntry = ExerciseEntry(
                id = 0L,
                exerciseId = 9_999L,
                date = 1_700_000_000_000L,
                sets = 4,
                reps = 10,
                durationSec = null,
                weightKg = null,
                comment = null
            )

            // When
            val result = repository.addEntry(bogusEntry)

            // Then — Result.isFailure, таблица записей пустая
            assertTrue(result.isFailure)
            val allEntries = repository.getAllEntries().getOrThrow()
            assertTrue(allEntries.isEmpty())
        }
}
```

## Тест с реальной MainActivity (живое обновление через WhileSubscribed)

`ExerciseCreateLiveUpdateIntegrationTest.kt` — тест
с реальной `MainActivity` и **production-БД** (не in-memory;
production-БД также у `EntryListLiveUpdateIntegrationTest` и
`RootScreenTest`):
проверяет, что `WhileSubscribed(5000)` корректно доставляет
invalidation после паузы > 5 с. Сокращённый скелет (тело хелпера
`insertExerciseIntoAppDatabase()` + проверка включённой вкладки
«Каталог» — полная версия):

```kotlin
@RunWith(AndroidJUnit4::class)
class ExerciseCreateLiveUpdateIntegrationTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // Уникальное имя на прогон — вставки предыдущих прогонов не
    // маскируют замороженный список, если `WhileSubscribed` не доставил
    // invalidation.
    private val exerciseName = "Отжимания Int-${System.currentTimeMillis() % 100_000L}"

    @Test
    fun app_whenReturnedToCatalogAfterInsert_thenShowsNewExercise() {
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_title))
            .performClick()
        composeTestRule.waitForIdle()

        // Каталог открыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_title))
            .performClick()
        composeTestRule.waitForIdle()

        // Уходим на форму создания, ждём > таймаута подписки (5 с),
        // вставляем упражнение напрямую в БД приложения, возвращаемся
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
            .performClick()
        composeTestRule.waitForIdle()
        runBlocking { delay(5_300L) }
        insertExerciseIntoAppDatabase()

        composeTestRule.runOnIdle {
            composeTestRule.activityRule.scenario.onActivity { activity ->
                activity.onBackPressedDispatcher.onBackPressed()
            }
        }

        // Каталог показывает новое упражнение без перезапуска
        // exerciseName — уникальное на прогон имя из `insertExerciseIntoAppDatabase()`
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(exerciseName))
        composeTestRule.onNodeWithText(exerciseName).assertIsDisplayed()
    }
}
```

> Это исключение из правила «in-memory». Реальная БД нужна
> потому, что `MainActivity` поднимает DI с собственным
> `WorkoutDatabase.create(context, ...)` — подменить его на
> in-memory в этом тесте нельзя без переписывания DI (то же
> относится к `EntryListLiveUpdateIntegrationTest` и
> `RootScreenTest`). Для DAO/репозиторий-тестов — in-memory
> обязателен.
>
> Канонический разбор (полный `insertExerciseIntoAppDatabase()`
> с `Room.databaseBuilder(... DATABASE_NAME)` и guard
> «включена ли вкладка Каталог») — в
> `references/activity-integration.md` → «Скелет (канонический
> пример — ExerciseCreateLiveUpdateIntegrationTest)».
