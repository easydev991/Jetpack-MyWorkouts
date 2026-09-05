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

## Long-press и контекстное меню (DropdownMenu)

Паттерн «длинное нажатие на строке списка → меню в точке тача»:
`pointerInput { detectTapGestures.onLongPress }` на строке +
`onGloballyPositioned` для глобальной позиции, **один**
`DropdownMenu` на уровне списка (не внутри строки — позиция
привязки плывёт при скролле).

### Состояние меню

```kotlin
data class ContextMenuState(
    val item: ListItem,           // ваш доменный тип
    val offsetInRoot: Offset
)
```

### Жест на строке

Сохраняем глобальную позицию в `remember`, локальный `localOffset`
приходит из `detectTapGestures.onLongPress`:

```kotlin
@Composable
private fun RowWithLongPress(
    item: ListItem,
    separator: String,
    onLongPress: (localOffset: Offset, positionInRoot: Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }
    Row(
        item = item,
        separator = separator,
        modifier = modifier
            .onGloballyPositioned { coords ->
                positionInRoot = coords.positionInRoot()
            }
            .pointerInput(item.id) {
                detectTapGestures(
                    onLongPress = { localOffset ->
                        onLongPress(localOffset, positionInRoot)
                    }
                )
            }
    )
}
```

### Меню на уровне списка

Один `DropdownMenu` (не на строку); px→dp через `LocalDensity`;
M3 сам клампит `offset` в границы экрана:

```kotlin
@Composable
private fun ListWithContextMenu(
    items: List<ListItem>,
    separator: String,
    contextMenu: ContextMenuState?,
    onItemLongPress: (ListItem, Offset) -> Unit,
    onMenuDismiss: () -> Unit,
    onItemAction: (ListItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn {
            items(items = items, key = { it.id }) { item ->
                RowWithLongPress(
                    item = item,
                    separator = separator,
                    onLongPress = { localOffset, positionInRoot ->
                        onItemLongPress(item, positionInRoot + localOffset)
                    }
                )
            }
        }

        if (contextMenu != null) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = onMenuDismiss,
                offset =
                    DpOffset(
                        x = with(density) { contextMenu.offsetInRoot.x.toDp() },
                        y = with(density) { contextMenu.offsetInRoot.y.toDp() }
                    )
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete)) },
                    onClick = { onItemAction(contextMenu.item) }
                )
            }
        }
    }
}
```

Подставьте свои имена (`ListItem`, `Row`, `R.string.*`, колбэки) на
свои — структура и логика те же.

### Тест-покрытие в `androidTest`

- `performTouchInput { longClick() }` →
  `onNodeWithText("...").assertExists()` — наличие меню и пункта.
- **Позиция** меню относительно тача UI-тестами не проверяется
  (`offset` `DropdownMenu` не имеет семантики точной привязки в
  Compose UI Test) — закрывается ручной проверкой на эмуляторе
  (см. `references/manual-verification.md`).

## Эмуляция локального состояния в тесте через `var X by remember`

Когда Composable принимает `state = ScreenState(...,
onTransientChange = ...)` (test/preview-state с колбэками) и внутри
логики использует **транзитное локальное состояние** (открыто ли
меню, какой id «pending», что выбрано) — частый антипаттерн:
callback инкрементируется, но в `state.transient` не проставляется,
Composable не перерисуется.

**Антипаттерн** (callback срабатывает, но UI не рисуется —
`assertExists()` для нужной ноды падает):

```kotlin
var callbackCount = 0
composeTestRule.setContent {
    MyScreen(
        state = ScreenState(
            uiState = ...,
            // transient по умолчанию = null
            onTransientChange = {
                callbackCount++           // счётчик растёт
                // но state.transient остаётся null — UI не обновляется
            }
        )
    )
}
// ...
assertEquals(1, callbackCount)            // проходит
onNodeWithText("Действие").assertExists() // падает: ноды нет
```

**Корректно** — завести локальный `var transient by remember`,
callback обновляет **и** локальный `transient`, передаваемый в
`state.transient`:

```kotlin
var callbackCount = 0
composeTestRule.setContent {
    var transient by remember { mutableStateOf<MyTransient?>(null) }
    MyScreen(
        state = ScreenState(
            uiState = ...,
            transient = transient,           // обратная подписка
            onTransientChange = {
                callbackCount++
                transient = it
            }
        )
    )
}
```

`MutableState` гарантирует рекомпозицию с новым значением. Шаблон
распространяется на любые транзитные поля стейта: `pendingX`,
`openMenu`, `selectedItem` и т.п.

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
