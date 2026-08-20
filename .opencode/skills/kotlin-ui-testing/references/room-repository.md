# Room and Repository — интеграционные тесты данных

Тесты слоя данных в `app/src/androidTest/java/com/jetpack_myworkouts/data/`:
DAO, база, репозиторий. Используют реальную Room in-memory базу —
быстро, без устройства хранения, с полным SQL-движком.

Примеры:
- `data/database/dao/WorkoutDaoTest.kt` — 14 тестов DAO
- `data/database/MyWorkoutsDatabaseTest.kt` — создание базы
- `data/repository/WorkoutRepositoryIntegrationTest.kt` — CRUD + маппинг

## Скелет DAO-теста

Полный код — `references/EXAMPLE.md` (раздел «DAO-тест (Room in-memory)»).
Ключевое:

- `Room.inMemoryDatabaseBuilder(context, MyWorkoutsDatabase::class.java)`
  `.allowMainThreadQueries().build()` — свежая БД на каждый тест
- `.allowMainThreadQueries()` — обязателен: запросы идут из `runBlocking`
  на main thread
- `@After` — `database.close()`, пересоздание в `@Before` каждого теста
  (в DAO-тестах `clearAllTables()` не нужен — база и так свежая)
- Flow-запросы тестируются через `.first()`:

```kotlin
val all = workoutDao.getAllWorkouts().first()
assertEquals(expectedCount, all.size)
```

## Репозиторий-интеграция

`WorkoutRepositoryIntegrationTest` — реальный `WorkoutRepositoryImpl(workoutDao)`:

```kotlin
@Before
fun setup() {
    database = Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries().build()
    workoutDao = database.workoutDao()
    repository = WorkoutRepositoryImpl(workoutDao)
}

@Test
fun fullCycle_createReadUpdateDelete_thenWorksCorrectly() =
    runBlocking {
        // Given
        val workout = Workout(title = "Тестовое событие", ...)

        // When
        val insertedId = repository.insertWorkout(workout)
        val retrieved = repository.getWorkoutById(insertedId)
        repository.updateWorkout(retrieved?.copy(title = "Обновлённое событие") ?: workout)
        val updated = repository.getWorkoutById(insertedId)

        // Then
        assertNotNull(retrieved)
        assertEquals("Тестовое событие", retrieved?.title)
        assertEquals("Обновлённое событие", updated?.title)

        // When
        repository.deleteWorkout(updated ?: workout)

        // Then
        assertNull(repository.getWorkoutById(insertedId))
    }
```

Тестируется:
- CRUD-цикл (create → read → update → delete)
- Поиск по title (`searchWorkouts("День").first()`)
- Сортировка DESC, tiebreaker id DESC
- Маппинг Entity ↔ Domain (проверяются доменные `Workout`, не `WorkoutEntity`)
- null-safe поля (`conversionWithNullColorTag_preservesNull`)

Правила:
- Репозиторий создаётся вручную: `WorkoutRepositoryImpl(workoutDao)` — без DI
- Доменные модели в assertions — `Workout`, а не `WorkoutEntity`
- Turbine не используется — обычные `assertEquals` / `assertNotNull`
- `runBlocking` для синхронных проверок
- Сортировка: `getAllWorkouts_whenSameTimestamp_thenSortedByIdDesc` —
  сначала по timestamp DESC, при равенстве — по id DESC
