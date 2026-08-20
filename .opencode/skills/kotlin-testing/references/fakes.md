# Fakes — Fake-репозитории на MutableStateFlow

Для `WorkoutRepository`, `WorkoutReminderRepository` и других интерфейсов,
отдающих `Flow`, в проекте принят паттерн **Fake** на `MutableStateFlow`.
Это надёжнее `mockk() returns flowOf(...)`, потому что эмиссия
детерминирована и не зависит от порядка `collect`.

## Почему Fake, а не Mock

| Подход | Когда использовать |
|---|---|
| `mockk() returns flowOf(...)` | Один-два вызова, не реактивно |
| **Fake на MutableStateFlow** | `WorkoutRepository`, `WorkoutReminderRepository` — эмиссии, реактивные комбинации, несколько подписчиков |
| `mockk(relaxed = true)` | `Logger`, `DataStore`, `ResourceProvider` — простые stateless интерфейсы |

Проблема `mockk + flowOf`: `flowOf(...)` — холодный. Если в
ViewModel несколько `combine` / `flatMapLatest`, эмиссия может
произойти до того, как подписчик `collect`-ит — тест будет flaky.

## Канонический `FakeWorkoutRepository`

```kotlin
private class FakeWorkoutRepository : WorkoutRepository {
    private val _items = MutableStateFlow<List<Workout>>(emptyList())

    fun setWorkouts(items: List<Workout>) {
        _items.value = items
    }

    fun containsWorkout(id: Long): Boolean = _items.value.any { it.id == id }

    override fun getAllWorkouts(): Flow<List<Workout>> = _items

    override fun getAllWorkouts(sortOrder: SortOrder): Flow<List<Workout>> =
        _items.map { items ->
            when (sortOrder) {
                SortOrder.ASCENDING -> items.sortedWith(compareBy({ it.timestamp }, { it.id }))
                SortOrder.DESCENDING -> items.sortedWith(
                    compareByDescending<Workout> { it.timestamp }.thenByDescending { it.id }
                )
            }
        }

    override suspend fun getWorkoutById(id: Long): Workout? =
        _items.value.find { it.id == id }

    override fun getWorkoutFlow(id: Long): Flow<Workout?> =
        _items.map { items -> items.find { it.id == id } }

    override fun searchWorkouts(query: String): Flow<List<Workout>> =
        _items.map { items ->
            items.filter { workout ->
                workout.title.contains(query, ignoreCase = true) ||
                    workout.details.contains(query, ignoreCase = true)
            }
        }

    override suspend fun insertWorkout(workout: Workout): Long {
        val newId = (_items.value.maxOfOrNull { it.id } ?: 0) + 1
        val newWorkout = workout.copy(id = newId)
        _items.value = _items.value + newWorkout
        return newId
    }

    override suspend fun updateWorkout(workout: Workout) {
        _items.value = _items.value.map { if (it.id == workout.id) item else it }
    }

    override suspend fun deleteWorkout(workout: Workout) {
        _items.value = _items.value.filterNot { it.id == workout.id }
    }

    override suspend fun deleteAllWorkouts() {
        _items.value = emptyList()
    }

    override suspend fun getWorkoutsCount(): Int = _items.value.size
}
```

Тонкости:
- `private class` — внутри тест-класса, не выноси в общий файл.
- Метод `setWorkouts(items)` — публичный для теста, не часть интерфейса.
- Дополнительные геттеры (`containsWorkout`, `items`) — допустимы,
  если упрощают assertions.
- `_items.value = newList` — триггерит эмиссию во всех подписчиках.

## Канонический `FakeWorkoutReminderDao`

Для data-слоя (тестирование `WorkoutReminderRepositoryImpl`):

```kotlin
private class FakeWorkoutReminderDao : WorkoutReminderDao {
    private val storage = linkedMapOf<Long, WorkoutReminderEntity>()

    override suspend fun getReminderByItemId(workoutId: Long): WorkoutReminderEntity? = storage[workoutId]

    override suspend fun getFutureActiveReminders(nowEpochMillis: Long): List<WorkoutReminderEntity> =
        storage.values.filter {
            it.status == WorkoutReminderStatus.ACTIVE.name &&
                it.targetEpochMillis > nowEpochMillis
        }

    override suspend fun upsertReminder(reminder: WorkoutReminderEntity) {
        storage[reminder.workoutId] = reminder
    }

    override suspend fun updateStatus(workoutId: Long, status: String, updatedAt: Long) {
        val current = storage[workoutId] ?: return
        storage[workoutId] = current.copy(status = status, updatedAt = updatedAt)
    }

    override suspend fun deleteByItemId(workoutId: Long) {
        storage.remove(workoutId)
    }
}
```

## Другие Fakes

**`FakeWorkoutReminderManager`** — для тестов `WorkoutDetailsScreenViewModel`:

```kotlin
private class FakeWorkoutReminderManager : WorkoutReminderManager {
    var activeReminder: WorkoutReminder? = null
    val clearedItemIds = mutableListOf<Long>()

    override suspend fun saveReminder(request: WorkoutReminderRequest, itemTitle: String): Result<Unit> =
        Result.success(Unit)

    override suspend fun clearReminder(workoutId: Long) {
        clearedItemIds += workoutId
        activeReminder = null
    }

    override suspend fun getActiveReminder(workoutId: Long): WorkoutReminder? =
        activeReminder?.takeIf { it.workoutId == workoutId }

    override suspend fun consumeReminder(workoutId: Long) = Unit
    override suspend fun rescheduleFutureReminders() = Unit
}
```

Использование в тесте:

```kotlin
reminderManager.activeReminder = futureReminder
// ...
assertEquals(listOf(testItemId), reminderManager.clearedItemIds)
```

**`FakeWorkoutReminderScheduler`** — для `DefaultWorkoutReminderManagerTest`:

```kotlin
private class FakeWorkoutReminderScheduler : WorkoutReminderScheduler {
    val scheduled = mutableListOf<ScheduledReminder>()
    var lastCancelledItemId: Long? = null

    override fun schedule(reminder: ScheduledReminder) {
        scheduled += reminder
    }

    override fun cancel(workoutId: Long) {
        lastCancelledItemId = workoutId
    }
}
```

## Где размещать Fakes

**Внутри тест-класса как `private class`** — основной путь.
Плюсы: видно полный контракт, нет утечек между тестами,
не загрязняет `app/src/test/java/`.

Выносить в общий файл (`test/fakes/`) — **только если** один и тот же
Fake используется в 3+ тест-классах. Порог формально пройден:
`FakeWorkoutRepository` сейчас дублируется в 5 тест-классах:

- `WorkoutsScreenViewModelTest`
- `WorkoutDetailsScreenViewModelTest`
- `WorkoutEditScreenViewModelTest` (вариация `FakeWorkoutRepositoryWithLoggingDisabled`)
- `WorkoutEditScreenViewModelReminderTest`
- `DefaultWorkoutReminderManagerTest`

Но выносить не стали — каждая версия подогнана под свой набор
assertions (например, `containsWorkout` есть только в
`WorkoutDetailsScreenViewModelTest`, отключённый логгер — только в
`WorkoutEditScreenViewModelTest`). Решение по выносу принимать
по месту при появлении 6+ дублирований или при существенных
изменениях контракта `WorkoutRepository`.

## Именование Fake-классов

- `Fake<InterfaceName>` — стандартный префикс.
- `Fake<InterfaceName>With<Modifier>` — если есть вариант:
  `FakeWorkoutRepositoryWithLoggingDisabled` (для
  `WorkoutEditScreenViewModelTest`, где нужно убедиться, что логгер
  не вызывается).

## Когда Fake НЕ нужен

- Repository не использует Flow (только suspend `Result<T>`) —
  хватит `mockk()` + `coEvery`.
- Repository возвращает данные из реального источника (DAO, сеть) —
  это integration test, не unit.
- Один метод, одна проверка — `mockk(relaxed = true)` быстрее
  написать и читать.