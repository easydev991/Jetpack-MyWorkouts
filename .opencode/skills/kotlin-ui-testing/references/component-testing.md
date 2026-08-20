# Component testing — изолированные UI-тесты Compose

Компонентные тесты проверяют отдельный Compose-компонент без реальной
Activity. В Jetpack-MyWorkouts так тестируются `WorkoutDurationText`, `ColorSelector`,
`WorkoutTagFilterDialog`, `SaveButton`, секции формы CreateEdit.

Примеры: `app/src/androidTest/java/com/myworkouts/ui/ds/WorkoutDurationTextTest.kt`,
`ui/screens/createedit/ColorSelectorUiTest.kt`,
`ui/screens/events/WorkoutTagFilterDialogTest.kt`.

## Скелет компонентного теста

```kotlin
import androidx.compose.ui.test.junit4.v2.createComposeRule

class SomeComponentUiTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun subject_whenCondition_thenResult() {
        // Given
        val testText = "5 дней"

        // When
        composeTestRule.setContent {
            MyWorkoutsTheme {
                WorkoutDurationText(formattedText = testText)
            }
        }

        // Then
        composeTestRule
            .onNodeWithText(testText)
            .assertExists()
            .assertTextEquals(testText)
    }
}
```

Ключевое:
- `createComposeRule()` — v2 API (`androidx.compose.ui.test.junit4.v2`),
  без Activity, без `@RunWith`
- `setContent { MyWorkoutsTheme { ... } }` — компонент в теме приложения
  (WorkoutDurationTextTest обходится без темы — если компонент не зависит от
  неё, обёртка необязательна)
- Состояние компонента — через `mutableStateOf` локально в тесте:

```kotlin
var selectedColor by mutableStateOf<Color?>(customColor)

composeTestRule.setContent {
    MyWorkoutsTheme {
        ColorSelector(
            selectedColor = selectedColor,
            onColorSelected = { selectedColor = it }
        )
    }
}
```

## Изоляция зависимостей — TestViewModel

Для тестов экрана CreateEdit используется factory-функция из
`app/src/androidTest/java/com/myworkouts/ui/screens/createedit/TestViewModel.kt`:

```kotlin
fun createTestViewModel(): WorkoutEditScreenViewModel =
    WorkoutEditScreenViewModel(
        repository = createTestWorkoutRepository(),
        resourceProvider = createTestResourceProvider(),
        savedStateHandle = SavedStateHandle(),
        analyticsService = AnalyticsService(listOf(NoopAnalyticsProvider()))
    )
```

Фейковый репозиторий — анонимный `object : WorkoutRepository` с пустыми
реализациями (все Flow возвращают `flowOf()`, suspend-методы — no-op):

```kotlin
private fun createTestWorkoutRepository(): WorkoutRepository =
    object : WorkoutRepository {
        override fun getAllWorkouts(): Flow<List<Workout>> = flowOf()
        override fun getAllWorkouts(sortOrder: SortOrder): Flow<List<Workout>> = flowOf()
        override suspend fun getWorkoutById(id: Long): Workout? = null
        override fun getWorkoutFlow(id: Long): Flow<Workout?> = flowOf()
        override fun searchWorkouts(query: String): Flow<List<Workout>> = flowOf()
        override suspend fun insertWorkout(workout: Workout): Long = 0L
        override suspend fun updateWorkout(workout: Workout) {}
        override suspend fun deleteWorkout(workout: Workout) {}
        override suspend fun deleteAllWorkouts() {}
        override suspend fun getWorkoutsCount(): Int = 0
    }
```

Фейковый ResourceProvider — пустые строки, без `!!`:

```kotlin
internal fun createTestResourceProvider(): ResourceProvider =
    object : ResourceProvider {
        override fun getString(resId: Int, vararg formatArgs: Any): String = ""
        override fun getQuantityString(resId: Int, quantity: Int, vararg formatArgs: Any): String = ""
        override fun getYearsString(quantity: Int): String = ""
        override fun getMonthsString(quantity: Int): String = ""
    }
```

Правила для фейков:
- Никакого `!!` — все методы возвращают значения по умолчанию, не null
- `flowOf()` для Flow-методов, `0L` / no-op для suspend
- Factory-функции лежат рядом с тестами, которые их используют
