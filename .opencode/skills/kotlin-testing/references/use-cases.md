# Use Cases

Use Case в проекте — это `class` с `operator fun invoke(...)` в
`com.myworkouts.domain.usecase`. Тесты лежат в этом же пакете.

## Три формы Use Case

### 1. Pure (нет зависимостей, нет I/O)

`CalculateWorkoutDurationUseCase`, `CalculateWorkoutDurationUseCaseTest`:

```kotlin
class CalculateWorkoutDurationUseCaseTest {
    private val useCase = CalculateWorkoutDurationUseCase()

    @Test
    fun calculate_when_1_day_difference_then_returns_1_day() {
        // Given
        val currentDate = LocalDate.now()
        val eventDate = currentDate.minusDays(1)
        val timestamp = eventDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // When
        val result = useCase(eventTimestamp = timestamp)

        // Then
        assertTrue(result is WorkoutDuration.Calculated, "Результат должен быть Calculated")
        val calculated = result as WorkoutDuration.Calculated
        assertEquals(1, calculated.period.days, "Должен быть 1 день")
    }
}
```

Use Case создаётся один раз как `val` — он immutable.

### 2. С зависимостями (MockK)

`FormatWorkoutDurationUseCase` зависит от `WorkoutDurationFormatter`. Тест
мокирует форматтер:

```kotlin
class FormatWorkoutDurationUseCaseTest {
    private val daysFormatter: WorkoutDurationFormatter = mockk()
    private val resourceProvider: ResourceProvider = mockk(relaxed = true)
    private val useCase = FormatWorkoutDurationUseCase(daysFormatter = daysFormatter)

    @Test
    fun invoke_when_calculated_positive_days_and_showminus_true_then_shows_number_with_sign() {
        // Given
        val period = WorkoutPeriod(years = 0, months = 0, days = 10)
        val difference = WorkoutDuration.Calculated(
            period = period, totalDays = 10, timestamp = 1234567890000L
        )
        every {
            daysFormatter.formatComposite(
                period = period,
                displayOption = DisplayOption.DAY,
                resourceProvider = resourceProvider,
                totalDays = 10,
                showMinus = true
            )
        } returns "10 дней"

        // When
        val result = useCase.invoke(
            difference = difference,
            displayOption = DisplayOption.DAY,
            resourceProvider = resourceProvider,
            showMinus = true
        )

        // Then
        assertEquals("10 дней", result)
    }
}
```

`ResourceProvider` в примере — интерфейс-зависимость из шаблона; в
текущем проекте его нет, подставь мок или свой стаб под свою задачу.

### 3. С `Result<T>` и валидацией

`BuildWorkoutReminderUseCase` возвращает `Result<WorkoutReminder>` — ошибки для
невалидных входов. Тесты проверяют обе ветки:

```kotlin
class BuildWorkoutReminderUseCaseTest {
    private val fixedInstant = Instant.parse("2026-04-27T10:15:30Z")
    private val zoneId = ZoneId.of("Europe/Moscow")
    private val clock: Clock = Clock.fixed(fixedInstant, zoneId)

    @Test
    fun invoke_whenAfterIntervalDays_thenKeepsCurrentTimeOfDayAndAddsDays() {
        // Given
        val useCase = BuildWorkoutReminderUseCase(clock = clock)
        val request = WorkoutReminderRequest(
            workoutId = 42L,
            mode = WorkoutReminderMode.AFTER_INTERVAL,
            afterAmount = 3,
            afterUnit = WorkoutReminderIntervalUnit.DAY
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isSuccess, "Результат должен быть успешным")
        val reminder = result.getOrThrow()
        val expected = ZonedDateTime.ofInstant(fixedInstant, zoneId).plusDays(3)
        assertEquals(expected.toInstant().toEpochMilli(), reminder.targetEpochMillis)
    }

    @Test
    fun invoke_whenAfterIntervalAmountIsZero_thenReturnsFailure() {
        // Given
        val useCase = BuildWorkoutReminderUseCase(clock = clock)
        val request = WorkoutReminderRequest(
            workoutId = 42L,
            mode = WorkoutReminderMode.AFTER_INTERVAL,
            afterAmount = 0,
            afterUnit = WorkoutReminderIntervalUnit.WEEK
        )

        // When
        val result = useCase(request)

        // Then
        assertTrue(result.isFailure, "Должна быть ошибка валидации")
    }
}
```

## `Clock.fixed` — обязательно для time-зависимых Use Case

Если Use Case читает `Clock.systemDefaultZone()` или
`LocalDate.now()`, тест становится **flaky по дню** — он пройдёт в
полдень, упадёт за полночь.

Решение — инжектить `Clock` и в тесте передавать `Clock.fixed(...)`:

```kotlin
private val fixedInstant = Instant.parse("2026-04-27T10:15:30Z")
private val zoneId = ZoneId.of("Europe/Moscow")
private val clock: Clock = Clock.fixed(fixedInstant, zoneId)

private val useCase = BuildWorkoutReminderUseCase(clock = clock)
```

То же для `currentTimeMillisProvider: () -> Long` — инжектим как
параметр, в тесте — лямбда.

## Явная передача `currentDate`

`CalculateWorkoutDurationUseCase` принимает `currentDate: LocalDate`
опционально. В тесте **всегда передавай явно** — иначе flaky:

```kotlin
// ПЛОХО — неявный currentDate = LocalDate.now() внутри use case
@Test
fun calculate_when_30_days_difference_then_returns_30_days() {
    val currentDate = LocalDate.now()
    // ...
}

// ХОРОШО — фиксируем
@Test
fun calculate_when_30_days_difference_then_returns_30_days() {
    val currentDate = LocalDate.of(2024, 3, 31)  // детерминированно
    val eventDate = LocalDate.of(2024, 3, 1)
    // ...
    val result = useCase(eventTimestamp = timestamp, currentDate = currentDate)
    // ...
}
```

Исключение — тесты, которые явно проверяют поведение "сегодня"
(например, `calculate_when_same_day_then_returns_today`).

## `Result<T>` — assert-паттерны

```kotlin
// Happy path
val result = useCase(request)
assertTrue(result.isSuccess, "Результат должен быть успешным")
val value = result.getOrThrow()

// Failure — есть ошибка
val result = useCase(invalidRequest)
assertTrue(result.isFailure, "Должна быть ошибка валидации")

// Failure — конкретный тип ошибки
val ex = result.exceptionOrNull()
assertTrue(ex is MyException, "Тип ошибки должен быть MyException")
```

## Use Case + I/O (Context, ContentResolver)

`ExportBackupUseCase`, `ImportBackupUseCase` работают с
`ContentResolver`. Это suspend-функции с I/O — мокируем
`Context` и `WorkoutRepository`:

```kotlin
class ExportBackupUseCaseTest {
    private val repository: WorkoutRepository = mockk()
    private val context: Context = mockk()
    private val contentResolver: ContentResolver = mockk()

    @BeforeEach
    fun setup() {
        every { context.contentResolver } returns contentResolver
        useCase = ExportBackupUseCase(repository, context)
    }

    @Test
    fun invoke_whenexporting_thencontainsformatandroid() = runBlocking {
        // Given
        val items = listOf(testWorkout)
        val uri: Uri = mockk()
        coEvery { repository.getAllWorkouts() } returns flowOf(items)
        every { contentResolver.openOutputStream(uri) } returns outputStream

        // When
        val result = useCase(uri)

        // Then
        assertTrue(result.isSuccess)
    }
}
```

Здесь `runBlocking` допустим — нет `viewModelScope.launch`,
нет `viewModelScope`. Это обычная suspend-функция без диспетчеризации.

## Проверка исключений (не `Result<T`)

Если Use Case **бросает** исключение (а не оборачивает в
`Result.failure`), тестируй через `assertThrows`:

```kotlin
@Test
fun invoke_whenJsonInvalid_thenThrowsSerializationException() = runTest {
    // Given
    val invalidJson = "{ invalid json }"
    val uri = mockk<Uri>()

    // When & Then
    assertThrows<SerializationException> {
        useCase(uri, invalidJson)
    }
}
```

## Частые ошибки

| Ошибка | Решение |
|---|---|
| Use Case читает `LocalDate.now()` | Инжектить `Clock`, в тесте `Clock.fixed(...)` |
| Use Case читает `System.currentTimeMillis()` | Инжектить `currentTimeMillisProvider: () -> Long` |
| `runBlocking` для suspend use case | Допустимо, если нет `viewModelScope`. Иначе — `runTest` |
| `assertEquals(Result.failure(...), result)` | Не сравнивай `Result` целиком. Используй `assertTrue(result.isFailure)` |
| Тест Use Case с реальным `Context` | Не делай так — нужен `mockk<Context>()`, иначе это не unit |
| `every` вместо `coEvery` для suspend | Тест не сработает, ошибка в рантайме |