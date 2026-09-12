# Use Cases

Use Case в проекте — это `class` с `operator fun invoke(...)` в
`com.myworkouts.domain.usecase`. Тесты лежат в этом же пакете.

В проекте сейчас только два use-case'а: `ExportBackupUseCase` и
`ImportBackupUseCase` (app-data-screen). Все примеры ниже — на них.

## Формы Use Case'ов

### 1. Pure (нет зависимостей, нет I/O)

В проекте нет pure use-case'ов (нет `Calculate*` / `Build*`), но
форматтер `DurationFormatter` — `object` в `ui/util`, тестируется
аналогично:

```kotlin
class DurationFormatterTest {
    @Test
    fun format_duration_when_seconds_only_then_seconds() {
        assertEquals("5 сек", formatDuration(5, "", "мин", "сек"))
        assertEquals("45 сек", formatDuration(45, "", "мин", "сек"))
    }
}
```

Файл — `app/src/test/java/com/myworkouts/ui/util/DurationFormatterTest.kt`.

### 2. С зависимостями (mockk на suspend-методы)

`ExportBackupUseCase` зависит от `WorkoutDataRepository` (suspend `Result`)
и `Context` (I/O). Канонический пример (полный класс с двумя тестами —
успех `buildBackupJson` и провал репозитория) — `EXAMPLE.md` §4.

Особенности:
- `buildBackupJson` — чистая `internal` функция в том же файле
  (`ExportBackupUseCase.kt`). Тестируется без mock'ов, как pure.
- `runTest { … }` — без `viewModelScope`, без диспетчеризации;
  `runBlocking` тут тоже подойдёт, но `runTest` — единообразие.
- `coEvery` — для `suspend` методов (`getAllExercises()`, `getAllEntries()`).
- `every` — для не-suspend (`context.contentResolver`,
  `contentResolver.openOutputStream(...)`).
- `verify(exactly = 0)` — негативная проверка: поток не открыт при провале.

## Use Case + I/O (Context, ContentResolver)

`ExportBackupUseCase`, `ImportBackupUseCase` работают с
`ContentResolver`. Это suspend-функции с I/O — мокируем
`Context`/`ContentResolver` и `WorkoutDataRepository`.

Хелперы `useCase(...)` + `repository(...)` + `plankFile` —
канонический пример в `data-layer.md` → «Парсинг» (там больше контекста);
ниже — только контракт и форма JSON.

```kotlin
// JSON-файл подаётся строкой (ByteArrayInputStream), без app/src/test/resources/
val plankFile = """
    {
      "version": 1,
      "exercises": [ { "name": "Планка", "category": "CORE", ... } ],
      "entries":  [ { "exerciseName": "Планка", "date": 1757371250000, ... } ]
    }
""".trimIndent()
```

Особенности:
- `ByteArrayInputStream(fileContent.toByteArray())` — файл-копия
  подаётся строкой, без `app/src/test/resources/`.
- `mockk<…>(relaxed = true)` для всего, что не влияет на тест —
  не нужно стаббить каждый метод.
- `repository()` как helper — переиспользуется между `@Test` с разными
  данными.

## `Result<T>` — assert-паттерны

```kotlin
// Happy path
val result = useCase.invoke(uri)
assertTrue(result.isSuccess, "Результат должен быть успешным")

// Failure — есть ошибка (без проверки типа)
val result = useCase.invoke(uri)
assertTrue(result.isFailure, "Должна быть ошибка")

// Failure — конкретный тип ошибки (BackupException)
val ex = result.exceptionOrNull()
assertInstanceOf<BackupException>(ex, "Тип ошибки должен быть BackupException")
assertEquals(BackupError.INVALID_FILE, (ex as BackupException).error)
```

## `@ParameterizedTest` для негативных кейсов

`ImportBackupUseCaseTest` использует `@ParameterizedTest` +
`@MethodSource("invalidFiles")` для таблицы невалидных файлов:

```kotlin
@ParameterizedTest(name = "[{index}] {0}")
@MethodSource("invalidFiles")
fun invoke_when_invalid_file_then_failure(label: String, rawJson: String, expectedError: BackupError) =
    runTest {
        // Given
        val repository = repository()
        val useCase = useCase(repository, rawJson)

        // When
        val result = useCase(uri)

        // Then
        assertTrue(result.isFailure)
        assertEquals(expectedError, (result.exceptionOrNull() as BackupException).error)
    }

companion object {
    @JvmStatic
    fun invalidFiles(): List<Arguments> = listOf(
        Arguments.of("не JSON", "{ invalid json }", BackupError.INVALID_FILE),
        Arguments.of("version отсутствует", """{"exercises":[],"entries":[]}""", BackupError.INVALID_FILE),
        Arguments.of("version != 1", """{"version":2,"exercises":[],"entries":[]}""", BackupError.INVALID_FILE),
        Arguments.of("category неизвестна",
            """{"version":1,"exercises":[{"name":"X","category":"NOPE","measuredBy":"REPS","isCustom":false,"createdAt":0}],"entries":[]}""",
            BackupError.INVALID_FILE)
    )
}
```

Полный файл — `app/src/test/java/com/myworkouts/domain/usecase/ImportBackupUseCaseTest.kt`.

## Частые ошибки

| Ошибка | Решение |
|---|---|
| Use Case читает `LocalDate.now()` | Инжектить `Clock`, в тесте `Clock.fixed(...)` |
| Use Case читает `System.currentTimeMillis()` | Инжектить `currentTimeMillisProvider: () -> Long` |
| `runBlocking` для suspend use case | Допустимо, если нет `viewModelScope`. Иначе — `runTest` |
| `assertEquals(Result.failure(...), result)` | Не сравнивай `Result` целиком. Используй `assertTrue(result.isFailure)` |
| Тест Use Case с реальным `Context` | Не делай так — нужен `mockk<Context>()`, иначе это не unit |
| `every` вместо `coEvery` для suspend | Тест не сработает, ошибка в рантайме |
| `coEvery` для Flow-возвращающего метода (`fun foo(): Flow<T>`) | Тоже неверно — `coEvery` только для `suspend`. Используй `every`. |
| `outputStream` не объявлен в `every { … returns outputStream }` | Объяви явно: `val outputStream: OutputStream = ByteArrayOutputStream()` или `mockk<OutputStream>()` |
| `runBlocking` + ViewModel | Запрещено — конфликт с `viewModelScope.launch` |