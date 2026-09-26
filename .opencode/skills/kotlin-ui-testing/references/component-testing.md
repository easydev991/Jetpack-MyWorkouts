# Component testing — изолированные UI-тесты Compose

Компонентные тесты проверяют отдельный Compose-компонент без реальной
Activity. В Jetpack-MyWorkouts так тестируются (список не
исчерпывающий — добавьте новый тест, когда появится):

- `ExercisesScreenTest` — экран каталога упражнений через
  `ExercisesScreenContent(state = ...)` без ViewModel
- `ExerciseCreateScreenTest` — форма создания через
  `ExerciseCreateScreenContent(state = ...)`
- `EntryCreateScreenTest` — форма записи через
  `EntryCreateScreenContent(state = ...)`
- `EntryEditArchivedExerciseTest` — правка записи, ссылающейся на
  заархивированное упражнение: реальная `EntryCreateViewModel`
  поверх Room in-memory; подмена VM через `mutableStateOf` внутри
  первого `setContent` (образец для правила 14 SKILL.md)
- `MainScreenTest` — главный экран через `MainScreenContent(state = ...)`
- `MainScreenEditNavigationTest` — клик по строке секции → переход
  в `EntryCreateScreen`
- `MainScreenFabNavigationTest` — клик по FAB главного экрана →
  переход в `ExerciseCreateScreen`
- `MainScreenSectionsTest` — секции главного экрана с реальной VM
  и фейковым репозиторием на `MutableStateFlow` (без Room);
  использует `createAndroidComposeRule(ComponentActivity)`,
  чтобы у `DropdownMenu` был host (то же — `MainScreenDetailCardTest`)
- `MainScreenDetailCardTest` — карточка деталей записи: открытие
  тапом, закрытие (крестик/скрим/Back), поведение при открытом
  контекстном меню; `createAndroidComposeRule(ComponentActivity)`,
  часть тестов — реальный `MainScreen` поверх Room in-memory
  (не путать с `MainScreenDetailCardRecreateTest` — интеграционный
  тест на реальной `MainActivity`, см. `references/activity-integration.md`)
- `AppearanceScreenTest` / `AppearanceScreenStaticTest` — экран темы
- `AppDataScreenTest`, `MoreScreenTest` — экраны настроек
- `ExercisesLiveUpdateTest` — экран каталога с реальной VM + Room in-memory

## Скелет компонентного теста

Полный код — `references/EXAMPLE.md` (раздел «Компонентный UI-тест
(без Activity)»). Особенности скелета (без повтора кода):

- `createComposeRule()` — v2 API (`androidx.compose.ui.test.junit4.v2`),
  без Activity
- Готовый `*ScreenState` (data class с `uiState` и колбэками) —
  ViewModel и репозиторий не нужны, логика VM покрыта JVM-тестами
- `setContent { ... }` оборачивает компонент в `JetpackMyWorkoutsTheme`,
  если компонент зависит от темы; для самодостаточных компонентов
  обёртка необязательна (тест `MainScreenTest` обходится без неё для
  проверки чистой логики рендера)
- Все классы компонентных тестов аннотированы
  `@RunWith(AndroidJUnit4::class)` (см. `references/fundamentals.md`,
  раздел «Стек и аннотации»)

## Фикстуры с датами — порядок как в прод-контракте

Данные фикстуры строй в том же порядке, который обещает контракт
прод-данных: хитмап — по возрастанию дат, списки записей — от свежих.
Order-dependent вывод (`distinct()`, `joinToString`, grouping) в
компоненте рендерит **порядок входа**, и фикстура «в обратную
сторону от сегодня» даёт падение с вводящим в заблуждение сообщением
(«Сентябрь – Август» вместо «Август – Сентябрь», далее ложный след
«not displayed»). Надёжно: сортируй внутри хелпера фикстуры.

```kotlin
/** Хитмап из дат с ненулевым счётом; даты — по возрастанию (контракт). */
private fun heatmapOf(dates: List<LocalDate>): List<HeatmapDayData> =
    dates
        .distinct()
        .sorted()
        .map { date -> HeatmapDayData(date = date, count = 1, level = 1) }
```

Канон — `ActivityScreenTest.heatmapOf`
(`app/src/androidTest/.../ui/screens/overview/ActivityScreenTest.kt`).

## Навигация через NavHost-harness

Когда нужно проверить `navController.navigate(...)` без реальной
`MainActivity` — оборачиваем экран в минимальный `NavHost` с
заглушками destinations. Без `NavHost` `navController.navigate(...)`
бросит `IllegalArgumentException: Navigation graph has not been set`,
без нужного destination — `IllegalArgumentException: destination not found`.

Полный код `ExercisesScreenWithNavHost` — в `references/EXAMPLE.md`
→ «Компонентный тест с NavHost-harness и навигацией».

## Тест с реальной ViewModel поверх Room in-memory

`ExercisesLiveUpdateTest` — сценарий «уход с экрана → вставка →
возврат»: проверяет, что `WhileSubscribed(5000)` доставляет
invalidation корректно. Полный код (`setUp`/`tearDown` + `setContent`
с реальной `ExercisesViewModel.factory(repository)` поверх Room in-memory
+ полный сценарий) — в `references/EXAMPLE.md` →
«Компонентный тест с реальной ViewModel поверх Room in-memory».

Ключевые точки (без кода):

- `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`
  в `@Before`, `database.close()` в `@After`
- `ExercisesViewModel.factory(repository)` — единая точка входа для VM
  в тестах (factory живёт рядом с VM)
- `JetpackMyWorkoutsTheme` оборачивает `NavHost` (если экран зависит
  от темы)
- После `runBlocking { repository.addExercise(...) }` в Compose-тесте —
  `composeTestRule.waitForIdle()` (правило 10)
- `composeTestRule.runOnIdle { navController.popBackStack() }` —
  навигация через `runOnIdle`, не из тела теста напрямую

## Тест с фейковым репозиторием на MutableStateFlow

`MainScreenSectionsTest` — реальная VM с фейковым репозиторием
(без Room). Фейк живёт внутри файла теста как `private class`:

```kotlin
private lateinit var repository: SectionsFakeRepository
private lateinit var viewModel: MainViewModel

@Before
fun setUp() {
    repository =
        SectionsFakeRepository(
            initialEntries =
                todayEntries() +
                    entry(id = 101L, name = "Приседания", date = today.minusDays(1), hour = 19) +
                    entry(id = 201L, name = "Планка", date = today.minusDays(5), hour = 8)
        )
    viewModel = MainViewModel(repository = repository)
}

private class SectionsFakeRepository(
    initialEntries: List<EntryListItem>
) : WorkoutDataRepository {
    private val entriesFlow = MutableStateFlow(initialEntries)

    override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())
    override fun entries(): Flow<List<EntryListItem>> = entriesFlow

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> = Result.success(1L)
    override suspend fun addExercise(exercise: Exercise): Result<Long> = Result.success(1L)
    override suspend fun updateExercise(exercise: Exercise): Result<Unit> = Result.success(Unit)
    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> = Result.success(Unit)
    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(null)
    override suspend fun deleteEntry(id: Long): Result<Unit> {
        entriesFlow.value = entriesFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }
    override suspend fun deleteExercise(id: Long): Result<Unit> = Result.success(Unit)
    override suspend fun getExercise(id: Long): Result<Exercise?> = Result.success(null)
    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = MutableStateFlow(emptySet())

    override suspend fun getAllExercises(): Result<List<Exercise>> = error("Не используется в этом тесте")
    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = error("Не используется в этом тесте")
    override suspend fun importData(
        exercises: List<Exercise>,
        entries: List<BackupEntry>
    ): Result<Unit> = error("Не используется в этом тесте")
    override suspend fun deleteAllData(): Result<Unit> = error("Не используется в этом тесте")
}
```

Правила для фейков:
- Никакого `!!` — все методы возвращают `Result.success(...)`,
  не null
- `MutableStateFlow` для реактивных полей — UI перерисуется на
  изменение через `value = ...`
- `error(...)` для методов, которые тест не использует — падает
  громко, если случайно задействуют
- Фейк лежит рядом с тестом в одном файле (`private class`), не
  выносится в общий хелпер

## Long-press и контекстное меню (DropdownMenu)

Паттерн «длинное нажатие на строке списка → меню» — `longClick()`
через `performTouchInput`. UI-тесты проверяют только наличие
пунктов меню (`onNodeWithText(context.getString(
R.string.exercise_list_menu_delete)).assertIsDisplayed()`),
не позицию `DropdownMenu` — она закрывается ручной проверкой на
эмуляторе (см. `references/manual-verification.md`):

```kotlin
@Test
fun row_long_press_when_standard_then_no_context_menu() {
    setContentWith(ExercisesUiState.Success(exercises = listOf(pullups, plank)))

    composeTestRule
        .onNodeWithText("Подтягивания")
        .performTouchInput { longClick() }
    composeTestRule.waitForIdle()

    composeTestRule
        .onAllNodesWithText(context.getString(R.string.exercise_list_menu_edit))
        .fetchSemanticsNodes()
        .let { nodes ->
            assertTrue(
                "У стандартного упражнения не должно быть пунктов контекстного меню",
                nodes.isEmpty()
            )
        }
}
```

`import androidx.compose.ui.test.longClick` — extension-функция
`TouchInjectionScope`, нужен явный импорт.
