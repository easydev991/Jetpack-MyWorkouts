# Activity integration — тесты с реальной MainActivity

Интеграционные UI-тесты запускают реальную `MainActivity` и полный
стек: Activity + Compose + продакшен-Room (БД на диске) + ViewModel
+ manual DI.

В Jetpack-MyWorkouts **один** такой тест —
`ExerciseCreateLiveUpdateIntegrationTest` (см.
`app/src/androidTest/java/com/myworkouts/ExerciseCreateLiveUpdateIntegrationTest.kt`).
Он воспроизводит проводку продакшена: `MainActivity` поднимает DI
с собственным `WorkoutDatabase.create(context, ...)`, подменить
на in-memory нельзя без переписывания DI.

Все остальные интеграционные сценарии (навигация, секции, поиск,
long-press → меню, диалоги, формы) покрываются **компонентными**
тестами с `createComposeRule()` + реальный `NavHost`-harness или
реальная ViewModel поверх Room in-memory
(см. `references/component-testing.md` →
«Тест с реальной ViewModel поверх Room in-memory»).

## Когда реальная MainActivity оправдана

Только когда воспроизводится продакшен-DI целиком — например,
проверка, что `WhileSubscribed(5000)` в `stateIn` не теряет
invalidation при паузе > таймаута. Без реального DI
(`MainActivity → manual DI → WorkoutDatabase.create(...)`) подменять
БД на in-memory невозможно.

## Скелет (единственный реальный случай в проекте)

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
        // Given — приложение запущено
        composeTestRule.waitForIdle()

        // Включаем вкладку «Упражнения», если она скрыта
        composeTestRule
            .onNodeWithText(context.getString(R.string.more_title))
            .performClick()
        composeTestRule.waitForIdle()
        // ... клик по переключателю вкладки ...

        // Каталог открыт
        composeTestRule
            .onNodeWithText(context.getString(R.string.exercises_title))
            .performClick()
        composeTestRule.waitForIdle()

        // When — уходим на форму создания, ждём > таймаута
        // подписки (5 с), вставляем упражнение в файловую БД
        // приложения, возвращаемся системным back
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

        // Then — каталог показывает новое упражнение без перезапуска
        // exerciseName = "Отжимания Int-${System.currentTimeMillis() % 100_000L}"
        // — то же имя, что вставляется в insertExerciseIntoAppDatabase()
        composeTestRule
            .onNode(hasScrollAction())
            .performScrollToNode(hasText(exerciseName))
        composeTestRule.onNodeWithText(exerciseName).assertIsDisplayed()
    }

    /** Вставка в файловую БД приложения (не in-memory). */
    private fun insertExerciseIntoAppDatabase() {
        val database =
            Room
                .databaseBuilder(
                    context,
                    WorkoutDatabase::class.java,
                    WorkoutDatabase.DATABASE_NAME
                ).build()
        val repository =
            RoomWorkoutDataRepository(
                database = database,
                standardExercises = listOf(
                    StandardExercise(name = "Отжимания", category = ExerciseCategory.PUSH)
                )
            )
        runBlocking {
            repository.addExercise(
                Exercise(
                    id = 0L,
                    name = exerciseName,
                    category = ExerciseCategory.PUSH,
                    isCustom = true,
                    comment = null,
                    createdAt = System.currentTimeMillis(),
                    measuredBy = ExerciseMeasuredBy.REPS
                )
            )
        }
        database.close()
    }
}
```

Ключевое:
- `createAndroidComposeRule<MainActivity>()` — реальная Activity
- БД — production (`Room.databaseBuilder(context, ..., DATABASE_NAME)`),
  не in-memory. Это исключение из общего правила — только ради
  воспроизведения продакшен-DI
- Вставка идёт **напрямую в БД приложения** (тест живёт в том же
  процессе), форма не заполняется — проверяется изолированно
  живое обновление списка
- Имя упражнения уникально на прогон (метка времени) — вставки
  предыдущих прогонов не маскируют замороженный список
- Реальная пауза `delay(5_300L)` — часть сценария
  (`WhileSubscribed(5000)`), не ожидание UI
- Тест занимает ~8 с — это нормально для интеграционного сценария

Проверка взаимного расположения элементов (`boundsInRoot`, sticky-заголовки,
сортировка секций) — в `references/compose-selectors.md` → «Проверка
позиции нод (boundsInRoot)».
