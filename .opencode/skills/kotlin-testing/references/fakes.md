# Fakes — Fake-репозитории на MutableStateFlow

Для `WorkoutDataRepository` и других интерфейсов с `Flow`-методами в
проекте принят паттерн **Fake** на `MutableStateFlow`. Это надёжнее
`mockk() returns flowOf(...)`, потому что эмиссия детерминирована и
не зависит от порядка `collect`.

## Почему Fake, а не Mock

| Подход | Когда использовать |
|---|---|
| `mockk() returns flowOf(...)` | Один-два вызова, не реактивно |
| **Fake на MutableStateFlow** | `WorkoutDataRepository` — реактивные комбинации, несколько подписчиков |
| `mockk(relaxed = true)` | `AppSettingsDataStore`, `Context`, `ContentResolver` — простые stateless интерфейсы |

Проблема `mockk + flowOf`: `flowOf(...)` — холодный. Если в
ViewModel несколько `combine` / `flatMapLatest`, эмиссия может
произойти до того, как подписчик `collect`-ит — тест будет flaky.

## Канонический `FakeWorkoutDataRepository` (для MainViewModel)

В тестах `MainViewModel` (список записей на главном экране):

```kotlin
private class MainViewModelFakeRepository(
    initialEntries: List<EntryListItem> = emptyList()
) : WorkoutDataRepository {
    private val entriesFlow = MutableStateFlow(initialEntries)

    /** Идентификации, переданные в [deleteEntry], в порядке вызова. */
    val deletedIds: MutableList<Long> = mutableListOf()

    override fun entries(): Flow<List<EntryListItem>> = entriesFlow
    override fun exercises(query: String): Flow<List<Exercise>> = MutableStateFlow(emptyList())

    override suspend fun deleteEntry(id: Long): Result<Unit> {
        deletedIds += id
        entriesFlow.value = entriesFlow.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

    // ... остальные методы контракта — заглушки Result.success / MutableStateFlow(emptyList())
    // (addEntry, addExercise, updateExercise, updateEntry, getEntry,
    //  deleteExercise, getExercise, exerciseIdsWithLinkedEntries,
    //  getAllExercises, getAllEntries, importData, deleteAllData).
}
```

Полный пример — `app/src/test/java/com/myworkouts/ui/viewmodel/MainViewModelTest.kt`.

Тонкости:
- `private class` — внутри тест-класса, не выноси в общий файл.
- Метод-запись (`deletedIds`) — публичный для теста, не часть интерфейса.
- `entriesFlow.value = newList` — триггерит эмиссию во всех подписчиках.
- Имя класса — префикс по тестируемой VM: `MainViewModelFakeRepository`,
  `ExercisesViewModelFakeRepository`, `AppDataViewModelFakeRepository`.
  В test source set `private` на top-level изолирует только в пределах
  модуля — разные классы не должны конфликтовать.

## Канонический `FakeWorkoutDataRepository` (для EntryCreateViewModel)

Каталог фиксирован, `addEntry`/`updateEntry` записывают переданную
запись и возвращают настраиваемый результат, `getEntry` отвечает
настраиваемой записью (`null` — «не найдена»):

```kotlin
private class FakeWorkoutDataRepository(
    exercises: List<Exercise>
) : WorkoutDataRepository {
    private val catalog = MutableStateFlow(exercises)

    var addedEntry: ExerciseEntry? = null
        private set

    var addEntryResult: Result<Long> = Result.success(1L)

    /** Ответ `getEntry`; `null` — запись не найдена. */
    var entryById: ExerciseEntry? = null

    var updatedEntry: ExerciseEntry? = null
        private set

    override fun exercises(query: String): Flow<List<Exercise>> = catalog
    override fun entries(): Flow<List<EntryListItem>> = MutableStateFlow(emptyList())

    override suspend fun addEntry(entry: ExerciseEntry): Result<Long> {
        addedEntry = entry
        return addEntryResult
    }

    override suspend fun updateEntry(entry: ExerciseEntry): Result<Unit> {
        updatedEntry = entry
        return Result.success(Unit)
    }

    override suspend fun getEntry(id: Long): Result<ExerciseEntry?> = Result.success(entryById)

    // ... остальные методы — заглушки
}
```

Полный пример — `app/src/test/java/com/myworkouts/ui/viewmodel/EntryCreateViewModelTest.kt`.

## Канонический `FakeExerciseRepository` (для ExerciseCreateViewModel)

Каталог реактивный, `addExercise` записывает переданное упражнение и
возвращает настраиваемый результат:

```kotlin
private class FakeExerciseRepository : WorkoutDataRepository {
    private val catalog = MutableStateFlow<List<Exercise>>(emptyList())

    /** Упражнения, переданные в `addExercise`, в порядке вызовов. */
    val addedExercises = mutableListOf<Exercise>()

    /** Настраиваемый результат `addExercise`. */
    var addExerciseResult: Result<Long> = Result.success(1L)

    override fun exercises(query: String): Flow<List<Exercise>> = catalog
    override suspend fun addExercise(exercise: Exercise): Result<Long> {
        addedExercises.add(exercise)
        return addExerciseResult
    }

    // ... остальные методы — заглушки
}
```

Полный пример — `app/src/test/java/com/myworkouts/ui/viewmodel/ExerciseCreateViewModelTest.kt`.

## Fake с настраиваемыми failure-ветками (для ExercisesViewModel)

`ExercisesViewModel` тестирует pre-check FK + ошибки репозитория —
`deleteExerciseResult` и `linkedEntries` настраиваются из тестов:

```kotlin
private class ExercisesViewModelFakeRepository(
    initialCatalog: List<Exercise> = emptyList()
) : WorkoutDataRepository {
    val catalog = MutableStateFlow(initialCatalog)

    val linkedEntries: MutableStateFlow<Set<Long>> = MutableStateFlow(emptySet())

    var deleteExerciseResult: Result<Unit> = Result.success(Unit)
    var lastDeletedExerciseId: Long? = null

    override fun exercises(query: String): Flow<List<Exercise>> = catalog
    override fun exerciseIdsWithLinkedEntries(): Flow<Set<Long>> = linkedEntries

    override suspend fun deleteExercise(id: Long): Result<Unit> {
        lastDeletedExerciseId = id
        return deleteExerciseResult
    }
}
```

Полный пример — `app/src/test/java/com/myworkouts/ui/viewmodel/ExercisesViewModelTest.kt`.

## Fake для AppDataViewModel

`entries()`/`exercises("")` — реактивные (нужны для `hasData`), use-case'ы
замоканы отдельно, поэтому снимки (`getAllExercises`/`getAllEntries`) и
`importData` — тривиальные заглушки:

```kotlin
private class AppDataViewModelFakeRepository(
    entries: List<EntryListItem> = emptyList(),
    private val exercises: List<Exercise> = emptyList()
) : WorkoutDataRepository {
    private val entriesFlow = MutableStateFlow(entries)
    private val catalogFlow = MutableStateFlow(exercises)

    var deleteAllDataResult: Result<Unit> = Result.success(Unit)

    override fun entries(): Flow<List<EntryListItem>> = entriesFlow
    override fun exercises(query: String): Flow<List<Exercise>> = catalogFlow

    override suspend fun getAllExercises(): Result<List<Exercise>> = Result.success(catalogFlow.value)
    override suspend fun getAllEntries(): Result<List<ExerciseEntry>> = Result.success(emptyList())
    override suspend fun deleteAllData(): Result<Unit> = deleteAllDataResult

    // ... остальные методы — заглушки
}
```

Полный пример — `app/src/test/java/com/myworkouts/ui/viewmodel/AppDataViewModelTest.kt`.

## Где размещать Fakes

**Внутри тест-класса как `private class`** — основной путь.
Плюсы: видно полный контракт, нет утечек между тестами,
не загрязняет `app/src/test/java/`.

Выносить в общий файл (`test/fakes/`) — **только если** один и тот же
Fake переиспользуется без модификаций в **6+** тест-классах. Практический
порог: даже при 5 дублях выносить рано — каждый экземпляр подгоняется
под свой набор assertions (`deletedIds` нужен только в одном тесте,
настраиваемые `addEntryResult`/`linkedEntries` — только в других), и
единый Fake теряет в гибкости.

> Каждый из существующих Fake (`MainViewModelFakeRepository`,
> `ExercisesViewModelFakeRepository`, `AppDataViewModelFakeRepository`,
> `FakeWorkoutDataRepository`, `FakeExerciseRepository`) — подогнан
> под свой набор assertions. Выносить пока рано: 5/6 не достигли
> порога, и вариации перевешивают выгоду от общего файла. При 6+ дублях
> или при существенных изменениях контракта `WorkoutDataRepository`
> решение принимается по месту.

## Именование Fake-классов

- `<ViewModelName>FakeRepository` — стандартный префикс
  (`MainViewModelFakeRepository`, `ExercisesViewModelFakeRepository`).
- `Fake<InterfaceName>` — допустимо, если один Fake покрывает несколько
  тестов одной VM (`FakeWorkoutDataRepository`,
  `FakeExerciseRepository`).

## Когда Fake НЕ нужен

- Repository не использует Flow (только suspend `Result<T>`) —
  хватит `mockk()` + `coEvery` (`AppearanceViewModel` +
  `AppSettingsDataStore` — пример).
- Repository возвращает данные из реального источника (DAO, сеть) —
  это integration test, не unit.
- Один метод, одна проверка — `mockk(relaxed = true)` быстрее
  написать и читать.