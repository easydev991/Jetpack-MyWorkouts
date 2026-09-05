# Mocking — MockK

MockK (`io.mockk.*`) — основной фреймворк моков в проекте.
Mockito / Mockito-Kotlin не используется.

## Создание мока

```kotlin
// Строгий — все вызовы без стаба бросают MockKException
private val logger: Logger = mockk()

// Relaxed — для не-stub-нутых методов возвращает дефолт (null/0/false)
private val logger: Logger = mockk(relaxed = true)
```

`relaxed = true` удобен для `Logger`, `DataStore`, простых интерфейсов,
где методов много и они не центральные. **Не используй `relaxed` для
репозиториев с Flow** — там нужны Fake (см. `references/fakes.md`).

## `every` / `coEvery`

```kotlin
// Синхронный вызов
every { mockResourceProvider.getString(R.string.today) } returns "Сегодня"

// С аргументами-свойствами (named args в блоке every тоже допустимы)
every {
    daysFormatter.formatComposite(
        period = period,
        displayOption = DisplayOption.DAY,
        resourceProvider = resourceProvider,
        totalDays = 10,
        showMinus = true
    )
} returns "10 дней"

// Suspend-функция — ТОЛЬКО coEvery
coEvery { mockRepository.deleteWorkout(testWorkout) } returns Unit

// Бросить исключение
every { mockProvider.getName() } throws IllegalStateException("not set")

// Suspend, бросить
coEvery { mockRepository.insertWorkout(any()) } throws SQLException("db error")
```

## `verify` / `coVerify`

```kotlin
// Был вызван хотя бы раз
verify { mockLogger.d("Tag", "msg") }

// Suspend
coVerify { mockRepository.deleteWorkout(testWorkout) }

// Ровно N раз
coVerify(exactly = 1) { mockRepository.insertWorkout(any()) }
coVerify(exactly = 0) { mockRepository.insertWorkout(any()) } // не было
```

## `slot` + `capture` (для перехвата аргументов)

Когда нужно проверить, **с чем** вызвали мок:

```kotlin
private val sortOrderSlot = slot<SortOrder>()

@BeforeEach
fun setUp() {
    coEvery { dataStore.setSortOrder(capture(sortOrderSlot)) } answers {
        sortOrderFlow.value = sortOrderSlot.captured
    }
}

@Test
fun when_sort_order_changed_then_sorts_workouts() = runTest {
    viewModel.updateSortOrder(SortOrder.ASCENDING)
    testDispatcher.scheduler.advanceUntilIdle()
    // ...
    assertEquals(SortOrder.ASCENDING, sortOrderSlot.captured)
}
```

`capture(slot)` — захватывает первый аргумент. `slot.captured` —
последнее захваченное значение.

## `answers` (для side-effect при вызове)

Когда вызов мока должен менять состояние (например, записать в
`MutableStateFlow`):

```kotlin
coEvery { dataStore.setWorkoutsScreenColorTagFilter(any()) } answers {
    colorTagFilterFlow.value = firstArg()
}
```

`firstArg()`, `secondArg()` — доступ к аргументам по позиции.

## Типичные ошибки

### `every` для suspend-функции — NPE или молча игнорирует

```kotlin
// НЕПРАВИЛЬНО — без coEvery не сработает для suspend
every { mockRepo.insertWorkout(workout) } returns 1L

// ПРАВИЛЬНО
coEvery { mockRepo.insertWorkout(workout) } returns 1L
```

### `verify` для suspend-функции

```kotlin
// НЕПРАВИЛЬНО
verify { mockRepo.insertWorkout(workout) }

// ПРАВИЛЬНО
coVerify { mockRepo.insertWorkout(workout) }
```

### Мок Flow не эмитится в `StateFlow`

`every { mockRepo.getAllWorkouts() } returns flowOf(items)` создаёт
**холодный** Flow, который запускается только при `collect`. Если
ViewModel слушает через `combine` / `flatMapLatest` на TestDispatcher,
эмиссия может произойти, а может и нет — race-condition.

**Решение**: для `WorkoutRepository`, `WorkoutReminderRepository` — пиши Fake
на `MutableStateFlow` (см. `references/fakes.md`). MockK — только
для `Logger`, `DataStore`, `ResourceProvider` и подобных stateless
интерфейсов.

## Полный пример: `FormatWorkoutDurationUseCaseTest`

```kotlin
class FormatWorkoutDurationUseCaseTest {
    private val daysFormatter: WorkoutDurationFormatter = mockk()
    private val resourceProvider: ResourceProvider = mockk(relaxed = true)

    private val useCase = FormatWorkoutDurationUseCase(daysFormatter = daysFormatter)

    @Test
    fun invoke_when_calculated_negative_days_and_showminus_true_then_shows_negative_number() {
        // Given
        val period = WorkoutPeriod(years = 0, months = 0, days = 7)
        val difference = WorkoutDuration.Calculated(
            period = period, totalDays = -7, timestamp = 1234567890000L
        )
        every {
            daysFormatter.formatComposite(
                period = period,
                displayOption = DisplayOption.DAY,
                resourceProvider = resourceProvider,
                totalDays = -7,
                showMinus = true
            )
        } returns "-7 дней"

        // When
        val result = useCase.invoke(
            difference = difference,
            displayOption = DisplayOption.DAY,
            resourceProvider = resourceProvider,
            showMinus = true
        )

        // Then
        assertEquals("-7 дней", result)
    }
}
```

Здесь `mockk()` без `relaxed`, потому что единственный метод форматтера
стабится в каждом тесте.