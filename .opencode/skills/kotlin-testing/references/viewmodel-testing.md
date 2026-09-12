# ViewModel Testing

Это самый частый кейс в проекте: тест ViewModel через
`Fake<Name>` на `MutableStateFlow` + `StandardTestDispatcher`.
Интеграционные тесты ViewModel (с реальным Room) **запрещены** AGENTS.md.

## Полный шаблон

Образец — `MainViewModelTest` / `EntryCreateViewModelTest`:

```kotlin
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MainViewModelTest {
    private lateinit var repository: MainViewModelFakeRepository
    private lateinit var viewModel: MainViewModel
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
        repository = MainViewModelFakeRepository(initialEntries = listOf(item1, item2))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun state_when_subscribed_then_success_with_entries() =
        runTest(testDispatcher) {
            // Given
            viewModel = MainViewModel(repository)

            // When — подписка активирует WhileSubscribed, список доезжает
            backgroundScope.launch { viewModel.uiState.collect { } }
            advanceUntilIdle()

            // Then
            val state = viewModel.uiState.value
            assertTrue(state is MainUiState.Success, "После эмиссии — Success")
            val success = state as MainUiState.Success
            assertEquals(listOf(item1, item2), success.entries)
        }
}
```

## Шесть обязательных элементов

1. `@OptIn(ExperimentalCoroutinesApi::class)` на классе.
2. `testDispatcher = StandardTestDispatcher()` в `@BeforeEach`.
3. `Dispatchers.setMain(testDispatcher)` — иначе `viewModelScope`
   по умолчанию использует `Main`, который не контролируется тестом.
4. `Dispatchers.resetMain()` в `@AfterEach` — обязательно,
   иначе следующий тест в том же процессе JVM получит грязный
   диспетчер.
5. Для простых интерфейсных зависимостей (`AppSettingsDataStore`,
   `Context`, `ContentResolver`) — `mockk(relaxed = true)`.
6. `testDispatcher.scheduler.advanceUntilIdle()` после каждого
   действия ViewModel — корутины не запустятся сами.

## Шесть + один: `mockkStatic(Log::class)` для ViewModel с `android.util.Log`

Если ViewModel логирует ошибки (через `android.util.Log.e(...)`),
в unit-тесте `android.util.Log` — stub и роняет короутину операции.
Полный код-рецепт (оба overload `Log.e`) — в `references/mocking-mockk.md`
→ «`mockkStatic(Log::class)`». Касается `AppDataViewModel` и
`ExercisesViewModel`. `AppearanceViewModel` и `MainViewModel` логов
не делают — мокать там нечего.

## Подписка на `WhileSubscribed`-stateIn

ViewModel'и проекта используют `stateIn(SharingStarted.WhileSubscribed(5000))`
— `.value` возвращает `initialValue`, пока никто не подписан на `uiState`.
Чтобы тест увидел эмиссию upstream, нужна явная подписка через
`backgroundScope` (он живёт до конца `runTest`):

```kotlin
// Given
val viewModel = MainViewModel(repository)

// When — подписка активирует WhileSubscribed, список доезжает
backgroundScope.launch { viewModel.uiState.collect { /* trigger */ } }
advanceUntilIdle()

// Then — теперь .value содержит Success с актуальными данными
val state = viewModel.uiState.value
assertTrue(state is MainUiState.Success)
```

Альтернативы, **которые не работают**:
- `viewModel.uiState.first()` — вернёт `initialValue`, не дождётся эмиссии.
- `viewModel.uiState.value` без подписки — тот же `initialValue`.
- `.first { it is Success }` — сработает, но `.value` после
  `advanceUntilIdle()` проще.

## `nowProvider` — для clock-зависимых ViewModel

`EntryCreateViewModel` принимает `nowProvider: () -> Long` для
детерминированного «сейчас» (фиксированный момент, не `System.currentTimeMillis()`):

```kotlin
private fun createViewModel(
    entryId: Long = -1L,
    nowProvider: () -> Long = { fixedNowMillis }
): EntryCreateViewModel =
    EntryCreateViewModel(
        repository = repository,
        nowProvider = nowProvider,
        entryId = entryId
    )

@Test
fun initial_state_should_load_catalog_prefill_sets_and_disable_save() =
    runTest(testDispatcher) {
        // Given
        val viewModel = createViewModel()  // nowProvider по умолчанию

        // When
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Then
        val expectedNow = Instant.ofEpochMilli(fixedNowMillis).atZone(ZoneId.systemDefault())
        val state = viewModel.uiState.value
        assertEquals(expectedNow.toLocalDate(), state.date, "Дата при открытии — «сейчас» из nowProvider")
    }
```

Полный пример — `EntryCreateViewModelTest`.

## Полный пример: `AppearanceViewModelTest` (только mockk, без Fake)

`AppearanceViewModel` зависит от `AppSettingsDataStore`. Стейт
комбинирует `Flow<AppTheme>` и `Flow<Boolean>` — мокируем через
`every { mockDataStore.theme } returns flowOf(theme)`:

```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class AppearanceViewModelTest {
    private lateinit var mockDataStore: AppSettingsDataStore
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setUp() {
        mockDataStore = mockk(relaxed = true)
        testDispatcher = StandardTestDispatcher()
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @ParameterizedTest(name = "[{0}]")
    @EnumSource(AppTheme::class)
    fun update_theme_should_update_all_themes(theme: AppTheme) =
        runTest(testDispatcher) {
            // Given
            coEvery { mockDataStore.setTheme(any()) } coAnswers { }
            val viewModel = AppearanceViewModel(mockDataStore)

            // When
            viewModel.updateTheme(theme)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            coVerify { mockDataStore.setTheme(theme) }
        }

    @ParameterizedTest(name = "[{0}]")
    @MethodSource("themeAndDynamicColorsPairs")
    fun ui_state_should_emit_combined_theme_and_dynamic_colors(
        theme: AppTheme,
        useDynamicColors: Boolean
    ) = runTest(testDispatcher) {
        // Given
        every { mockDataStore.theme } returns flowOf(theme)
        every { mockDataStore.useDynamicColors } returns flowOf(useDynamicColors)
        val viewModel = AppearanceViewModel(mockDataStore)

        // When — подписка через backgroundScope запускает WhileSubscribed
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()
        val actual = viewModel.uiState.value

        // Then
        assertEquals(
            AppearanceUiState(theme = theme, useDynamicColors = useDynamicColors),
            actual
        )
    }

    companion object {
        @JvmStatic
        fun themeAndDynamicColorsPairs(): List<Arguments> =
            listOf(
                Arguments.of(AppTheme.LIGHT, true),
                Arguments.of(AppTheme.DARK, false),
                Arguments.of(AppTheme.SYSTEM, true)
            )
    }
}
```

## Полный пример: `AppDataViewModelTest` (Fake + замоканные use-case'ы)

`AppDataViewModel` принимает репозиторий + два use-case'а
(`ExportBackupUseCase`, `ImportBackupUseCase`). Use-case'ы замоканы
(`mockk()` + `coEvery`), репозиторий — Fake (`AppDataViewModelFakeRepository`).

```kotlin
@BeforeEach
fun setUp() {
    testDispatcher = StandardTestDispatcher()
    Dispatchers.setMain(testDispatcher)
    // android.util.Log в JVM — stub, роняет короутину; мокаем статически
    mockkStatic(Log::class)
    every { Log.e(any(), any<String>()) } returns 0
    uri = mockk()
    exportUseCase = mockk()
    importUseCase = mockk()
    coEvery { exportUseCase.invoke(any()) } returns Result.success(Unit)
    coEvery { importUseCase.invoke(any()) } returns Result.success(Unit)
}

@AfterEach
fun tearDown() {
    unmockkStatic(Log::class)
    Dispatchers.resetMain()
}

@Test
fun exportBackup_whenUseCaseFails_thenErrorExport() =
    runTest(testDispatcher) {
        // Given
        coEvery { exportUseCase.invoke(any()) } returns
            Result.failure(BackupException("SAF не отдал поток", BackupError.EXPORT))
        val viewModel = viewModel(AppDataViewModelFakeRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // When
        viewModel.exportBackup(uri)
        advanceUntilIdle()

        // Then
        assertEquals(AppDataResult.ERROR_EXPORT, viewModel.uiState.value.result)
        assertFalse(viewModel.uiState.value.isBusy)
    }
```

Полный файл — `app/src/test/java/com/myworkouts/ui/viewmodel/AppDataViewModelTest.kt`.

## Почему НЕ `runBlocking` для ViewModel

```kotlin
// НЕПРАВИЛЬНО — конфликтует с viewModelScope.launch
@Test
fun bad() = runBlocking {
    viewModel.someAction()   // запускает viewModelScope.launch на Main
    // viewModelScope.launch НЕ выполнится — диспетчер Main не под контролем
}
```

Симптомы: тест зависает, `UncompletedCoroutinesError` после, flaky
failures.

**Правильно** — `runTest(testDispatcher) { … }` (он контролирует
`TestScope`) + `StandardTestDispatcher` + `advanceUntilIdle()`.

## Когда ViewModel использует Turbine

**Никогда в этом проекте.** Turbine в unit-тестах не подключён
(см. `app/build.gradle.kts` — `androidTestImplementation` тоже без
`turbine`); в unit-тестах Flow проверяется через
`viewModel.uiState.value` после подписки и `advanceUntilIdle()`.
Turbine в unit-тестах не нужен и не используется.

## Типичные ошибки

| Ошибка | Симптом | Решение |
|---|---|---|
| Забыл `setMain` | `viewModelScope.launch` висит навечно | Добавь `Dispatchers.setMain(testDispatcher)` в `@BeforeEach` |
| Забыл `resetMain` | Следующий тест в JVM получает грязный Main, flaky failures | Добавь в `@AfterEach` |
| Забыл `advanceUntilIdle()` | `uiState.value` — `Loading`, хотя ждёшь `Success` | Вставь после действия ViewModel |
| Забыл `backgroundScope.launch { uiState.collect }` | `.value` возвращает `initialValue` (Loading) | Подписка перед `advanceUntilIdle()` |
| Забыл `mockkStatic(Log::class)` | `android.util.Log` stub роняет короутину | `mockkStatic(Log::class)` + `unmockkStatic` |
| Забыл `@OptIn` | Компилятор ругается на `StandardTestDispatcher` | Добавь `@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)` |
| `mockk()` вместо `Fake` для `WorkoutDataRepository` | `mockRepo.exercises("") returns flowOf(...)` не эмитится в `combine` | Замени на `MainViewModelFakeRepository` (см. `references/fakes.md`) |