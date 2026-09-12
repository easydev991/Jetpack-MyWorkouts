# Mocking — MockK

MockK (`io.mockk.*`) — основной фреймворк моков в проекте.
Mockito / Mockito-Kotlin не используется.

## Создание мока

```kotlin
// Строгий — все вызовы без стаба бросают MockKException
private val dataStore: AppSettingsDataStore = mockk()

// Relaxed — для не-stub-нутых методов возвращает дефолт (null/0/false)
private val dataStore: AppSettingsDataStore = mockk(relaxed = true)

// Relaxed для всего, что не влияет на тест
private val contentResolver: ContentResolver = mockk(relaxed = true)
```

`relaxed = true` удобен для `AppSettingsDataStore`, `Context`,
`ContentResolver` — простых stateless интерфейсов, где методов много
и они не центральные. **Не используй `relaxed` для репозиториев с
Flow** — там нужны Fake (см. `references/fakes.md`).

## `every` / `coEvery`

```kotlin
// Синхронный вызов
every { mockContext.contentResolver } returns contentResolver

// Suspend-функция — ТОЛЬКО coEvery
coEvery { mockRepository.getAllExercises() } returns Result.success(exercises)

// Бросить исключение
every { mockProvider.getName() } throws IllegalStateException("not set")

// Suspend, бросить
coEvery { mockRepository.importData(any(), any()) } throws SQLException("db error")

// Flow-возвращающий метод — обычный every, НЕ coEvery
every { mockDataStore.theme } returns flowOf(AppTheme.DARK)
```

**`coEvery` ≠ `every` для Flow-методов.** Правило:
- `coEvery` / `coVerify` — только для `suspend fun`.
- `every` / `verify` — для обычных fun и для Flow-возвращающих fun
  (даже если реализация содержит `suspend` upstream).

`mockk + flowOf`: `flowOf(...)` — холодный Flow с риском race-condition
на TestDispatcher (см. «Типичные ошибки → Mock Flow не эмитится в StateFlow»
ниже; для `WorkoutDataRepository` — пиши Fake, см. `references/fakes.md`).

## `verify` / `coVerify`

```kotlin
// Был вызван хотя бы раз
verify { mockContext.contentResolver }

// Suspend
coVerify { mockRepository.deleteEntry(itemId) }

// Ровно N раз
coVerify(exactly = 1) { mockRepository.importData(any(), any()) }
coVerify(exactly = 0) { contentResolver.openOutputStream(any()) } // не было

// Для Flow-методов
verify { mockDataStore.theme }
```

## `slot` + `capture` (для перехвата аргументов)

Когда нужно проверить, **с чем** вызвали мок:

```kotlin
@Test
fun import_when_new_exercise_then_inserted_with_isCustom_true_and_zero_id() =
    runTest {
        // Given
        val repository = repository()
        val useCase = useCase(repository, plankFile)

        // When
        val result = useCase(uri)

        // Then
        assertTrue(result.isSuccess, "Новое упражнение и запись добавлены")
        val exercisesSlot = slot<List<Exercise>>()
        val entriesSlot = slot<List<BackupEntry>>()
        coVerify { repository.importData(capture(exercisesSlot), capture(entriesSlot)) }
        val inserted = exercisesSlot.captured.single()
        assertTrue(inserted.isCustom, "Вставленному из файла всегда isCustom = true")
    }
```

`capture(slot)` — захватывает первый аргумент. `slot.captured` —
последнее захваченное значение.

## `answers` (для side-effect при вызове)

Когда вызов мока должен менять состояние (например, записать в
`MutableStateFlow`):

```kotlin
every { mockProvider.getName() } answers { firstArg<String>() + "-suffix" }

coEvery { mockDataStore.setTheme(any()) } coAnswers {
    themeFlow.value = firstArg()
}
```

`firstArg()`, `secondArg()` — доступ к аргументам по позиции.
`coAnswers` — вариант для `coEvery`.

## `mockkStatic` + `unmockkStatic` (для `android.util.Log`)

`AppDataViewModel` и `ExercisesViewModel` логируют ошибки через
`android.util.Log.e(...)`. На JVM `android.util.Log` — stub и роняет
короутину операции. Лечится моком статического метода:

```kotlin
import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic

@BeforeEach
fun setUp() {
    // ...
    mockkStatic(Log::class)
    every { Log.e(any(), any<String>()) } returns 0
    every { Log.e(any(), any<String>(), any<Throwable>()) } returns 0
}

@AfterEach
fun tearDown() {
    unmockkStatic(Log::class)
    // ...
}
```

`AppearanceViewModel` и `MainViewModel` логов не делают — мокать там
нечего.

## Типичные ошибки

### `every` для suspend-функции — NPE или молча игнорирует

```kotlin
// НЕПРАВИЛЬНО — без coEvery не сработает для suspend
every { mockRepo.getAllExercises() } returns Result.success(emptyList())

// ПРАВИЛЬНО
coEvery { mockRepo.getAllExercises() } returns Result.success(emptyList())
```

### `verify` для suspend-функции

```kotlin
// НЕПРАВИЛЬНО
verify { mockRepo.deleteEntry(itemId) }

// ПРАВИЛЬНО
coVerify { mockRepo.deleteEntry(itemId) }
```

### `coEvery` для Flow-возвращающего метода

```kotlin
// НЕПРАВИЛЬНО — flowOf не suspend, метод тоже не suspend
coEvery { mockRepo.exercises("") } returns flowOf(listOf())

// ПРАВИЛЬНО
every { mockRepo.exercises("") } returns flowOf(listOf())
```

### Mock Flow не эмитится в `StateFlow`

`every { mockRepo.getAllExercises() } returns flowOf(items)` создаёт
**холодный** Flow, который запускается только при `collect`. Если
ViewModel слушает через `combine` / `flatMapLatest` на TestDispatcher,
эмиссия может произойти, а может и нет — race-condition.

**Решение**: для `WorkoutDataRepository` — пиши Fake на
`MutableStateFlow` (см. `references/fakes.md`). MockK — только для
`AppSettingsDataStore`, `Context`, `ContentResolver` и подобных
stateless интерфейсов.

### Мок Flow + `WhileSubscribed`-подписка — `.value` возвращает `initialValue`

ViewModel'и проекта используют `stateIn(WhileSubscribed(5000))`. Пока
на `uiState` никто не подписан — `.value` содержит `initialValue`,
даже если мок-Flow уже эмитнул. Лечится явной подпиской через
`backgroundScope.launch { uiState.collect {} }` (см.
`references/viewmodel-testing.md`).

## Итоговое правило выбора

| Зависимость | Подход |
|---|---|
| `WorkoutDataRepository` (с `Flow`) | Fake на `MutableStateFlow` (`references/fakes.md`) |
| `AppSettingsDataStore` | `mockk<AppSettingsDataStore>(relaxed = true)` + `coEvery { setX(...) }` |
| `Context` / `ContentResolver` / `Uri` | `mockk<…>(relaxed = true)` + `every { … }` для нужных методов |
| use-case (suspend `Result<T>`) | `mockk<UseCase>(relaxed = true)` + `coEvery { invoke(...) }` |
| `android.util.Log` (статический) | `mockkStatic(Log::class)` в `@BeforeEach` |