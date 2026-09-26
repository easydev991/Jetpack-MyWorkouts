# Room and Repository — интеграционные тесты данных

Тесты слоя данных в `app/src/androidTest/java/com/myworkouts/data/`:
DAO, база, репозиторий. Используют реальную Room in-memory базу —
быстро, без устройства хранения, с полным SQL-движком.

Примеры (список не исчерпывающий — добавьте новый DAO/репозиторий,
когда появится):
- `data/db/ExerciseDaoTest.kt` — DAO каталога упражнений: поиск по
  подстроке, сортировка `is_custom DESC, created_at DESC, name COLLATE NOCASE`,
  tie-break по имени.
- `data/db/ExerciseEntryDaoTest.kt` — DAO записей о выполнении.
- `data/db/ExerciseEntryUpdateTest.kt` — обновление существующих
  записей (edge-cases обновления).
- `data/db/WorkoutDatabaseTest.kt` — создание БД, prepopulate
  13 стандартных упражнений.
- `data/db/StandardExercisesLocalizationTest.kt` — состав
  стандартного набора в разных локалях.
- `data/repository/RoomWorkoutDataRepositoryTest.kt` — CRUD +
  маппинг Entity ↔ Domain + Flow invalidation.
- `data/repository/RoomWorkoutDataRepositoryDataManagementTest.kt` —
  методы `getAllExercises` / `getAllEntries` / `importData` /
  `deleteAllData`.

## Скелет DAO-теста

Полный код — `references/EXAMPLE.md` (раздел «DAO-тест (Room in-memory)»).
Ключевое:

- `Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java)`
  `.allowMainThreadQueries().build()` — свежая БД на каждый тест
- `.allowMainThreadQueries()` — обязателен: запросы идут из `runBlocking`
  на main thread
- `@After` — `database.close()`, пересоздание в `@Before` каждого теста
  (`clearAllTables()` не нужен — база и так свежая)
- Flow-запросы тестируются через `.first()`:

```kotlin
val all = exerciseDao.observeExercises("").first()
assertEquals(expectedCount, all.size)
```

## Репозиторий-интеграция

`RoomWorkoutDataRepositoryTest` — реальный
`RoomWorkoutDataRepository(database, standardExercises)` поверх
in-memory `WorkoutDatabase` + `allowMainThreadQueries()`. Полный
`@Before setUp` (in-memory DB + два `StandardExercise` REPS/TIME +
вставка двух `ExerciseEntity` для покрытия обоих `measuredBy`) —
в `references/EXAMPLE.md` → «Репозиторий-интеграция (Room in-memory)».

Ключевое (без кода):

- `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()`
  в `@Before`, `database.close()` в `@After` — `clearAllTables()`
  не используется (правило 9)
- Два стандартных упражнения в `standardExercises` (REPS и TIME) —
  покрывают оба `ExerciseMeasuredBy`
- `runBlocking { repository.addExercise(...).getOrThrow() }` +
  `runBlocking { repository.entries().first() }` — `.first()` для
  получения текущего значения `Flow`

Учебный пример (не из реального теста): иллюстрация контракта
`runBlocking` + `Result` на CRUD-цикле. В реальном
`RoomWorkoutDataRepositoryTest` сквозного full-cycle-метода нет —
реальные имена `@Test`-методов см. в самом тест-классе.

```kotlin
@Test
fun fullCycle_createReadUpdateDelete_thenWorksCorrectly() =
    runBlocking {
        // Given
        val entry = ExerciseEntry(
            id = 0L,
            exerciseId = 1L,
            date = 1_700_000_000_000L,
            sets = 4,
            reps = 10,
            durationSec = null,
            weightKg = null,
            comment = null
        )

        // When
        val insertedId = repository.addEntry(entry).getOrThrow()
        val retrieved = repository.getEntry(insertedId).getOrThrow()

        // Then
        assertNotNull(retrieved)
        assertEquals(4, retrieved?.sets)

        // When
        val updated = retrieved?.copy(sets = 5) ?: entry
        repository.updateEntry(updated)

        // Then
        val updatedEntry = repository.getEntry(insertedId).getOrThrow()
        assertEquals(5, updatedEntry?.sets)

        // When
        repository.deleteEntry(insertedId)

        // Then
        assertNull(repository.getEntry(insertedId).getOrNull())
    }
```

Тестируется:
- CRUD-цикл (create → read → update → delete)
- Поиск по подстроке (`exercises("под").first()`)
- Сортировка `is_custom DESC, created_at DESC, name COLLATE NOCASE`,
  tie-break по имени
- Маппинг Entity ↔ Domain (проверяются доменные `Exercise` /
  `ExerciseEntry`, не `*Entity`)
- null-safe поля (`comment = null`)
- `Result<...>` контракт — ошибки возвращаются как `isFailure`,
  не бросаются

Правила:
- Репозиторий создаётся вручную: `RoomWorkoutDataRepository(database,
  standardExercises)` — без DI
- Доменные модели в assertions — `Exercise` / `ExerciseEntry`,
  а не `*Entity`
- `runBlocking` для синхронных проверок; Flow — через `.first()` или
  `withTimeout(...)` для активной подписки (invalidation)
