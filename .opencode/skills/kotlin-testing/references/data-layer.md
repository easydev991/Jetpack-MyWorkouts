# Data Layer

Data layer тесты — для mapper, converter, provider, и парсеров JSON.
Эти классы не имеют ViewModel-специфики, поэтому проще.

## Mapper (entity ↔ domain)

`WorkoutMapperTest` — образец:

```kotlin
class WorkoutMapperTest {
    @Test
    fun toDomain_whenEntityWithAllFields_thenConvertsCorrectly() {
        // Given
        val entity = WorkoutEntity(
            id = 1L,
            title = "Тестовое событие",
            details = "Описание",
            timestamp = 1234567890000L,
            colorTag = 0xFFFF0000.toInt(),
            displayOption = DisplayOption.MONTH_DAY.name
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertEquals(1L, domain.id)
        assertEquals("Тестовое событие", domain.title)
        assertEquals(0xFFFF0000.toInt(), domain.colorTag)
        assertEquals(DisplayOption.MONTH_DAY, domain.displayOption)
    }

    @Test
    fun toDomain_whenEntityWithNullColorTag_thenConvertsCorrectly() {
        // Given
        val entity = WorkoutEntity(
            id = 2L,
            title = "Событие",
            timestamp = 1234567890000L,
            colorTag = null,
            displayOption = DisplayOption.DAY.name
        )

        // When
        val domain = entity.toDomain()

        // Then
        assertNull(domain.colorTag)
    }

    @Test
    fun roundTripConversion_thenPreservesAllFields() {
        // Given
        val original = Workout(
            id = 5L,
            title = "Оригинальное событие",
            timestamp = 9876543210000L,
            displayOption = DisplayOption.YEAR_MONTH_DAY
        )

        // When
        val entity = original.toEntity()
        val converted = entity.toDomain()

        // Then
        assertEquals(original, converted)
    }
}
```

Правила:
- Отдельные кейсы для каждого null-поля.
- Round-trip — отдельный тест, проверяет, что `toEntity` + `toDomain`
  = identity.
- `assertEquals(original, converted)` — структурное сравнение через
  `data class` equality.

## TypeConverter

`DisplayOptionConverterTest` — простой конвертер enum ↔ String:

```kotlin
class DisplayOptionConverterTest {
    private val converter = DisplayOptionConverter()

    @Test
    fun fromDisplayOption_whenDay_thenReturnsDayString() {
        // When
        val result = converter.fromDisplayOption(DisplayOption.DAY)

        // Then
        assertEquals("DAY", result)
    }

    @Test
    fun roundTripConversion_thenPreservesValue() {
        // Given
        val original = DisplayOption.MONTH_DAY

        // When
        val stringValue = converter.fromDisplayOption(original)
        val converted = converter.toDisplayOption(stringValue)

        // Then
        assertEquals(original, converted)
    }
}
```

## Provider (с моком ResourceProvider)

`WorkoutDurationFormatterImplTest` — `WorkoutDurationFormatterImpl` использует
`ResourceProvider` для plurals. Мокируем провайдер, проверяем
форматирование:

```kotlin
class WorkoutDurationFormatterImplTest {
    private val resourceProvider = mockk<ResourceProvider>()
    private val formatter = WorkoutDurationFormatterImpl()

    @Test
    fun formatdays_when_singular_then_returns_singular_form() {
        // Given
        every {
            resourceProvider.getQuantityString(
                resId = R.plurals.days_count,
                quantity = 1
            )
        } returns "1 день"

        // When
        val result = formatter.format(1, resourceProvider)

        // Then
        assertEquals("1 день", result, "Ожидалась форма единственного числа")
    }
}
```

Здесь `R.plurals.days_count` — реальный resource ID из production.
Мок подменяет результат, тест проверяет только логику форматтера.

## RepositoryImpl с FakeDao

`WorkoutReminderRepositoryImplTest` — `WorkoutReminderRepositoryImpl` оборачивает
`WorkoutReminderDao`. DAO заменяется на `FakeWorkoutReminderDao`:

```kotlin
class WorkoutReminderRepositoryImplTest {
    @Test
    fun saveReminder_whenValid_thenPersistsAndReturnsFromGetByItemId() = runTest {
        // Given
        val dao = FakeWorkoutReminderDao()
        val repository = WorkoutReminderRepositoryImpl(dao)
        val reminder = WorkoutReminder(
            workoutId = 15L,
            mode = WorkoutReminderMode.AFTER_INTERVAL,
            targetEpochMillis = 1_777_000_000_000L,
            intervalAmount = 3,
            intervalUnit = WorkoutReminderIntervalUnit.DAY,
            status = WorkoutReminderStatus.ACTIVE,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )

        // When
        repository.saveReminder(reminder)
        val stored = repository.getReminderByItemId(15L)

        // Then
        assertEquals(reminder, stored)
    }

    @Test
    fun markAsConsumed_whenReminderExists_thenChangesStatus() = runTest {
        // Given
        val dao = FakeWorkoutReminderDao()
        val repository = WorkoutReminderRepositoryImpl(dao)
        val reminder = WorkoutReminder(
            workoutId = 2L,
            mode = WorkoutReminderMode.AT_DATE,
            targetEpochMillis = 1_777_000_000_000L,
            status = WorkoutReminderStatus.ACTIVE,
            createdAt = 1_700_000_000_000L,
            updatedAt = 1_700_000_000_000L
        )
        repository.saveReminder(reminder)

        // When
        repository.markAsConsumed(2L)

        // Then
        val stored = repository.getReminderByItemId(2L)
        assertEquals(WorkoutReminderStatus.CONSUMED, stored?.status)
    }

    private class FakeWorkoutReminderDao : WorkoutReminderDao {
        private val storage = linkedMapOf<Long, WorkoutReminderEntity>()

        override suspend fun getReminderByItemId(workoutId: Long): WorkoutReminderEntity? = storage[workoutId]
        override suspend fun upsertReminder(reminder: WorkoutReminderEntity) {
            storage[reminder.workoutId] = reminder
        }
        // ... остальные методы
    }
}
```

## JSON парсинг с реальными файлами

`BackupImportRealFilesTest` — парсит JSON из
`app/src/test/resources/`. Это **не** интеграционный тест (Android
Context не нужен), но приближен к реальному формату:

```kotlin
class BackupImportRealFilesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parse_old_backup_sample_json_as_list_of_backupitem() {
        // Given
        val jsonString = loadResource("/old-backup-sample.json")

        // When
        val items = json.decodeFromString<List<WorkoutBackupItem>>(jsonString)

        // Then
        assertEquals(8, items.size)
        val firstBackupWorkout = items[0]
        assertEquals("День рождения", firstBackupWorkout.title)
        assertEquals("#FF5722", firstBackupWorkout.colorTag)
    }

    @Test
    fun parse_new_ios_backup_and_convert_timestamps_correctly() {
        // Given
        val jsonString = loadResource("/new-ios-backup.json")
        val wrapper = json.decodeFromString<IosWorkoutBackupWrapper>(jsonString)

        // When
        val androidItems = wrapper.items.mapNotNull { it.toWorkoutBackupItem() }

        // Then
        val victoryDay = androidItems.find { it.title == "День победы" }
        assertNotNull(victoryDay)
        // iOS: секунды с 2001-01-01 → Android: миллисекунды с 1970-01-01
        val expected = ((-1756176000.0 + 978307200.0) * 1000.0).toLong()
        assertEquals(expected, victoryDay?.timestamp)
    }

    private fun loadResource(path: String): String {
        val stream: InputStream? = javaClass.getResourceAsStream(path)
        assertNotNull(stream, "Resource not found: $path")
        return checkNotNull(stream, "Resource stream is null").bufferedReader().use { it.readText() }
    }
}
```

Тестовые файлы лежат в `app/src/test/resources/com/` или просто
в `app/src/test/resources/`. Имя — `<format>-<platform>-sample.json`:

```
app/src/test/resources/old-backup-sample.json
app/src/test/resources/new-backup-sample.json
app/src/test/resources/old-ios-backup-sample.json
app/src/test/resources/new-ios-backup.json
```

Загрузка:

```kotlin
javaClass.getResourceAsStream("/old-backup-sample.json")
```

Слэш в начале — обязателен для абсолютного пути в classpath.

## Чеклист для data layer тестов

- [ ] Mapper: round-trip тест (entity → domain → entity → equals)
- [ ] Converter: каждый enum-значение + round-trip
- [ ] Provider: тесты на каждый публичный метод, мок `ResourceProvider`
- [ ] Repository: Fake DAO / DataStore (не реальный), каждая ветка CRUD
- [ ] JSON парсер: реальные файлы из resources, разные форматы
      (старый Android, новый Android, старый iOS, новый iOS)
- [ ] Не использовать `Context` напрямую — иначе это integration