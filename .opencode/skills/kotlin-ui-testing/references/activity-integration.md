# Activity integration — тесты с реальной MainActivity

Интеграционные UI-тесты запускают реальную `MainActivity` и полный
стек: Activity + Compose + продакшен-Room (БД на диске) + ViewModel
+ manual DI.

Таких тестов в проекте пять:
`EntryListLiveUpdateIntegrationTest`, `ExerciseCreateLiveUpdateIntegrationTest`,
`RootScreenTest`, `MainScreenDetailCardRecreateTest`,
`ExercisesCategoriesTabRecreateTest`. Канонический
разбор ниже — `ExerciseCreateLiveUpdateIntegrationTest`
(см.
`app/src/androidTest/java/com/myworkouts/ExerciseCreateLiveUpdateIntegrationTest.kt`).
Он воспроизводит проводку продакшена: `MainActivity` поднимает DI
с собственным `WorkoutDatabase.create(context, ...)`, подменить
на in-memory нельзя без переписывания DI (production-БД также
у `EntryListLiveUpdateIntegrationTest` и `RootScreenTest`;
`MainScreenDetailCardRecreateTest` собственного `databaseBuilder`
не создаёт — пишет в ту же production-БД через продакшен-репозиторий
процесса `app.repository`; `ExercisesCategoriesTabRecreateTest` сам БД
не пишет, но VM его экрана читает production-БД приложения при старте).

Большинство остальных интеграционных сценариев (навигация, секции,
поиск,
long-press → меню, диалоги, формы) покрываются **компонентными**
тестами — `createComposeRule()` (или
`createAndroidComposeRule(ComponentActivity)` для window-host
`DropdownMenu`) + реальный `NavHost`-harness или реальная ViewModel
поверх Room in-memory
(см. `references/component-testing.md` →
«Тест с реальной ViewModel поверх Room in-memory»).

## Когда реальная MainActivity оправдана

Когда воспроизводится продакшен-DI целиком — например,
проверка, что `WhileSubscribed(5000)` в `stateIn` не теряет
invalidation при паузе > таймаута. Без реального DI
(`MainActivity → manual DI → WorkoutDatabase.create(...)`) подменять
БД на in-memory невозможно.

## Скелет (канонический пример — ExerciseCreateLiveUpdateIntegrationTest)

```kotlin
@RunWith(AndroidJUnit4::class)
class ExerciseCreateLiveUpdateIntegrationTest {
    companion object {
        @JvmField
        @ClassRule
        val timeoutRule = SubscriptionTimeoutRule()
    }

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

        // When — уходим на форму создания, ждём > пониженного rule'ом
        // таймаута подписки (100 мс), вставляем упражнение в файловую БД
        // приложения, возвращаемся системным back
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.exercises_add_exercise))
            .performClick()
        composeTestRule.waitForIdle()
        runBlocking { delay(300L) }
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
- Пауза `delay(300L)` — часть сценария, не ожидание UI: rule
  `SubscriptionTimeoutRule` понижает таймаут подписки до 100 мс,
  пауза выходит за его пределы (в проде окно — `WhileSubscribed(5000)`)
- Тест быстрый: пауза 300 мс вместо пятисекундного ожидания
  продового таймаута

Проверка взаимного расположения элементов (`boundsInRoot`, sticky-заголовки,
сортировка секций) — в `references/compose-selectors.md` → «Проверка
позиции нод (boundsInRoot)».

## Известный флейк: `SQLiteException: no such table` в production-БД

В отличие от in-memory-тестов, эти пять зависят от состояния данных
приложения на AVD. Диагноз:

- падают **только** они (остальные ~350 — Room in-memory, зелёные);
- стек — `SQLiteException: no such table: exercises/exercise_entries`;

причина — застойный/повреждённый файл `workout_database` в данных
приложения (например, AVD используется и генерацией скриншотов с
демо-данными). Лечение — переустановка приложения:

```bash
adb uninstall com.myworkouts   # AGP обычно снимает APK после прогона:
                               # pm list packages без myworkouts — не поломка
```

затем точечный перегон упавших классов (см. `running-tests.md` →
«Один класс») и полный `make android-test`. Проверенный кейс
(2026-10-25): 3 таких теста падали на AVD «RuStore-Screenshots»,
после переустановки — зелёные.

Укрепление при повторении: `@Before` с
`runCatching { context.deleteDatabase(WorkoutDatabase.DATABASE_NAME) }`
в классе — процесс теста удаляет старый файл до первого обращения к
`by lazy`-репозиторию, БД пересоздаётся с предзаполнением. DI-корень
не переписывается; добавлять по факту повторения, не профилактически.
