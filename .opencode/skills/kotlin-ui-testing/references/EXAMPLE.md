# EXAMPLE — канонические примеры

Полные примеры тестов из реального кода Jetpack-MyWorkouts.
Файлы: `app/src/androidTest/java/com/myworkouts/...`

## Компонентный UI-тест (без Activity)

`ui/ds/WorkoutDurationTextTest.kt` — компонент `WorkoutDurationText`, без темы и
без `@RunWith` (v2 API):

```kotlin
import androidx.compose.ui.test.junit4.v2.createComposeRule

class WorkoutDurationTextTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun workoutDurationText_whenDisplayed_thenShowsCorrectText() {
        // Given
        val testText = "5 дней"

        // When
        composeTestRule.setContent {
            WorkoutDurationText(formattedText = testText)
        }

        // Then
        composeTestRule
            .onNodeWithText(testText)
            .assertExists()
            .assertTextEquals(testText)
    }

    @Test
    fun workoutDurationText_whenEmptyText_thenShowsEmpty() {
        // Given
        val emptyText = ""

        // When
        composeTestRule.setContent {
            WorkoutDurationText(formattedText = emptyText)
        }

        // Then
        composeTestRule.onNodeWithText(emptyText).assertExists()
    }
}
```

## Компонентный тест с колбэками (диалог)

`ui/screens/events/WorkoutTagFilterDialogTest.kt` — без `@RunWith`,
в `MyWorkoutsTheme`, строки из ресурсов:

```kotlin
import androidx.compose.ui.test.junit4.v2.createComposeRule

class WorkoutTagFilterDialogTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val colorDescription = context.getString(R.string.color)
    private val resetText = context.getString(R.string.reset)
    private val applyText = context.getString(R.string.apply)
    private val titleText = context.getString(R.string.filter_by_color)

    @Test
    fun dialog_whenSameColorClicked_thenDeselects() {
        // Given - фильтр не установлен
        val currentFilter: Int? = null

        composeTestRule.setContent {
            MyWorkoutsTheme {
                WorkoutTagFilterDialog(
                    availableColors = availableColors,
                    currentFilter = currentFilter,
                    onApply = {},
                    onDismiss = {}
                )
            }
        }

        // When: кликаем по красному цвету дважды — выделение снимается
        composeTestRule
            .onAllNodesWithContentDescription(colorDescription)[0]
            .performClick()
        composeTestRule
            .onAllNodesWithContentDescription(colorDescription)[0]
            .performClick()

        // Then: нет фильтра и нет черновика — кнопка «Сбросить» неактивна
        composeTestRule
            .onNodeWithText(resetText)
            .assertIsNotEnabled()
    }

    @Test
    fun dialog_whenResetWithoutCurrentFilterButWithDraft_thenDialogStaysOpen() {
        // Given
        var onApplyCalled = false
        composeTestRule.setContent {
            MyWorkoutsTheme {
                WorkoutTagFilterDialog(
                    availableColors = availableColors,
                    currentFilter = null,
                    onApply = { onApplyCalled = true },
                    onDismiss = {}
                )
            }
        }

        // When: сброс без активного фильтра
        composeTestRule.onAllNodesWithContentDescription(colorDescription)[0].performClick()
        composeTestRule.onNodeWithText(resetText).performClick()

        // Then: диалог остаётся открытым, onApply не вызван
        composeTestRule.onNodeWithText(titleText).assertIsDisplayed()
        composeTestRule.runOnIdle {
            org.junit.Assert.assertFalse(
                "При сбросе черновика без фильтра onApply не должен вызываться",
                onApplyCalled
            )
        }
    }
}
```

## Интеграционный тест с MainActivity

`ui/screens/events/WorkoutsScreenSearchVisibilityUiTest.kt` — v2 API
`createAndroidComposeRule`, `@RunWith(AndroidJUnit4::class)`, реальный
DAO, вставка через `runBlocking`, `waitForIdle`:

```kotlin
@RunWith(AndroidJUnit4::class)
class WorkoutsScreenSearchVisibilityUiTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dao: WorkoutDao

    private val searchDescription: String
        get() = context.getString(R.string.search)

    @Before
    fun setUp() {
        runBlocking {
            dao = MyWorkoutsDatabase.getDatabase(context.applicationContext).workoutDao()
            dao.deleteAllWorkouts()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            dao.deleteAllWorkouts()
        }
    }

    @Test
    fun when_items_count_4_then_search_field_not_displayed() {
        // Given
        runBlocking { insertItems(4) }
        composeTestRule.waitForIdle()

        // Then: поле поиска скрыто
        composeTestRule
            .onNodeWithContentDescription(searchDescription)
            .assertDoesNotExist()
    }

    @Test
    fun when_user_typed_query_with_3_items_then_search_field_stays_visible() {
        // Given
        runBlocking { insertItems(5) }
        composeTestRule.waitForIdle()

        // When
        composeTestRule
            .onNodeWithContentDescription(searchDescription)
            .performTextInput("Workout")

        // And: удаляем элементы, пока введён запрос
        runBlocking { deleteFirstWorkout() }
        runBlocking { deleteFirstWorkout() }
        composeTestRule.waitForIdle()

        // Then: поле поиска остаётся видимым
        composeTestRule
            .onNodeWithContentDescription(searchDescription)
            .assertIsDisplayed()
    }

    @Test
    fun when_device_rotates_then_search_field_remains_displayed() {
        // Given
        runBlocking { insertItems(5) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription(searchDescription).assertIsDisplayed()

        // When: поворот устройства
        val activity = composeTestRule.activity
        val originalOrientation = activity.requestedOrientation
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeTestRule.waitForIdle()

        // Then: поле поиска осталось
        composeTestRule.onNodeWithContentDescription(searchDescription).assertIsDisplayed()

        // Cleanup
        composeTestRule.onNodeWithContentDescription(searchDescription).performClick()
        activity.requestedOrientation = originalOrientation
    }
}
```

## DAO-тест (Room in-memory)

`data/database/dao/WorkoutDaoTest.kt`:

```kotlin
@RunWith(AndroidJUnit4::class)
class WorkoutDaoTest {
    private lateinit var database: MyWorkoutsDatabase
    private lateinit var workoutDao: WorkoutDao

    @Before
    fun setup() {
        database = Room
            .inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                MyWorkoutsDatabase::class.java
            )
            .allowMainThreadQueries()
            .build()
        workoutDao = database.workoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertItem_thenRetrieveIt() =
        runBlocking {
            // Given
            val workout = WorkoutEntity(title = "Тестовое событие", ...)

            // When
            val insertedId = workoutDao.insertWorkout(workout)
            val retrieved = workoutDao.getWorkoutById(insertedId)

            // Then
            assertNotNull(retrieved)
            assertEquals("Тестовое событие", retrieved?.title)
        }

    @Test
    fun getAllItems_whenSameTimestamp_thenSortedByIdDesc() =
        runBlocking {
            // Given
            workoutDao.insertWorkout(workoutA.copy(timestamp = 1000L))
            workoutDao.insertWorkout(workoutB.copy(timestamp = 1000L))

            // When
            val all = workoutDao.getAllWorkouts().first()

            // Then
            assertEquals(2, all.size)
            assertTrue(
                "При равном timestamp первым должен быть больший id",
                all[0].id > all[1].id
            )
        }
}
```

## ViewModel-интеграция (Turbine)

`ui/viewmodel/WorkoutDetailsScreenViewModelIntegrationTest.kt`:

```kotlin
@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkoutDetailsScreenViewModelIntegrationTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var database: MyWorkoutsDatabase
    private lateinit var repository: WorkoutRepositoryImpl
    private lateinit var viewModel: WorkoutDetailsScreenViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MyWorkoutsDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WorkoutRepositoryImpl(database.workoutDao())
        database.clearAllTables()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun whenItemExistsInDatabase_thenLoadsSuccessfully() =
        runTest {
            // Given
            val insertedId = repository.insertWorkout(testWorkout)
            val savedStateHandle = SavedStateHandle(mapOf("workoutId" to insertedId))

            // When
            viewModel = WorkoutDetailsScreenViewModel(repository, NoOpLogger(), savedStateHandle)

            // Then
            viewModel.uiState.test {
                val loadingState = awaitItem()
                assertTrue(
                    "Начальное состояние должно быть Loading",
                    loadingState is WorkoutDetailsState.Loading
                )
                val successState = awaitItem()
                assertTrue(
                    "После загрузки — Success",
                    successState is WorkoutDetailsState.Success
                )
                assertEquals(
                    "Тестовое событие",
                    (successState as WorkoutDetailsState.Success).workout.title
                )
            }
        }
}
```

## Alarm-тест (PendingIntent + пермишены)

`reminder/WorkoutReminderAlarmSchedulerInstrumentedTest.kt`:

```kotlin
@RunWith(AndroidJUnit4::class)
class WorkoutReminderAlarmSchedulerInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun cancel_whenScheduled_thenPendingIntentIsRemoved() {
        // Given
        val scheduler = WorkoutReminderAlarmScheduler(context)
        val workoutId = 777L
        val reminder = WorkoutReminder(workoutId = workoutId, ...)

        // When
        scheduler.schedule(reminder, "title")

        // Then
        assertNotNull(findWorkoutReminderPendingIntent(workoutId))

        // When
        scheduler.cancel(workoutId)

        // Then
        assertNull(findWorkoutReminderPendingIntent(workoutId))
    }

    private fun findWorkoutReminderPendingIntent(workoutId: Long): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            WorkoutReminderIntentContract.requestCodeForWorkout(workoutId),
            Intent(context, WorkoutReminderReceiver::class.java).apply {
                action = WorkoutReminderIntentContract.ACTION_FIRE_REMINDER
            },
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
}
```
