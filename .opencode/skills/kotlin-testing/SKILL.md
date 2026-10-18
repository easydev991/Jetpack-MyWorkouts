---
name: kotlin-testing
description: >
  Экспертное руководство по unit-тестированию в Android-проекте Jetpack-MyWorkouts:
  JUnit 5, MockK, Fake-репозитории на MutableStateFlow, kotlinx-coroutines-test
  для ViewModel и Use Case, AAA / Given-When-Then, проектные соглашения
  (snake_case, без !!, русские сообщения), а также ограничения (запрет
  новых интеграционных тестов ViewModel).
  Использовать при написании новых unit-тестов, рефакторинге существующих,
  отладке flaky-тестов и улучшении качества покрытия в проекте.
---

# Kotlin Testing — kotlin-testing skill

## Overview

Этот skill — для unit-тестов Kotlin-кода в Jetpack-MyWorkouts. Стек:
**JUnit 5** (`org.junit.jupiter.api.*`), **MockK** (`io.mockk.*`),
**kotlinx-coroutines-test** (`runTest`, `StandardTestDispatcher`,
`Dispatchers.setMain` / `resetMain`), плюс **Fake-репозитории** на
`MutableStateFlow` для Flow-эмиссий.

Цель — читаемые, быстрые, независимые unit-тесты, которые проектная
конвенция требует как основу тестовой пирамиды (70% unit).

> Интеграционные и UI тесты — `app/src/androidTest/` (JUnit 4, Espresso,
> Compose Testing, Room in-memory). Этот skill про `app/src/test/`.

## Agent behavior contract (следуй этим правилам)

1. **JUnit 5 — основной фреймворк unit-тестов.** Все импорты из
   `org.junit.jupiter.api.*`. Используй `@Test`, `@BeforeEach`,
   `@AfterEach`. JUnit 4 (`org.junit.*`, `@RunWith(AndroidJUnit4::class)`)
   — только в `androidTest/`.
2. **Given / When / Then в каждом тесте.** Разделяй тело тремя
   комментариями-маркерами. Это и документация, и визуальный ритм.
   Для тривиальных тестов (`assertThrows` от `useCase(badInput)`) и
   тестов с одной строкой действия допустим объединённый маркер
   `// When + Then` (см. `references/fundamentals.md`,
   `references/assertions.md`).
3. **snake_case для имён тестов, без обратных кавычек.** Формат:
   `function_whenCondition_thenExpectedResult` — разделители `_`, имя
   функции и сегменты `when`/`then` могут быть в camelCase
   (`calculate_when_same_day_then_returns_today`,
   `invoke_whenAfterIntervalDays_thenKeepsCurrentTimeOfDayAndAddsDays`,
   `roundTripConversion_thenPreservesAllFields`).
   Обратные кавычки запрещены AGENTS.md — пиши без них. Допустимы
   русские слова в snake_case для названий свойств
   (`totaldays_в_calculated_содержит_общее_количество_дней`), но
   английский формат предпочтительнее.
4. **Никакого `!!`.** Даже в тестах. Используй `?.`, `?:`, `let`,
   `checkNotNull`. Это требование AGENTS.md, не обсуждается.
5. **MockK для интерфейсов, Fake для репозиториев с Flow.**
   `mockk()` / `mockk(relaxed = true)` — для интерфейсов без сложной
   логики эмиссий (`AppSettingsDataStore`, `Context`, `ContentResolver`).
   Для `WorkoutDataRepository` — пиши `Fake<Name>` (например,
   `MainViewModelFakeRepository`, `FakeExerciseRepository`) на
   `MutableStateFlow` (см. `references/fakes.md`).
6. **`coEvery` / `coVerify` для suspend-функций.** `every` / `verify` —
   только для не-suspend. MockK не делает автоматический выбор. Для
   Flow-возвращающих методов (`fun foo(): Flow<T>`) — обычный `every`,
   не `coEvery`.
7. **ViewModel — только через unit-тест.** Никаких новых интеграционных
   тестов ViewModel (причина: конфликт `runBlocking` ↔
   `viewModelScope.launch`, Flow репозитория не активируется).
   Существующие — обслуживай как есть.
8. **`StandardTestDispatcher` + `Dispatchers.setMain` / `resetMain`.**
   В `@BeforeEach` создавай `StandardTestDispatcher` и зови
   `Dispatchers.setMain(testDispatcher)`. В `@AfterEach` —
   `Dispatchers.resetMain()`. Аннотируй класс
   `@OptIn(ExperimentalCoroutinesApi::class)`.
9. **`advanceUntilIdle()` после действий ViewModel.** Любой вызов
   `viewModel.someAction()` обязан сопровождаться
   `testDispatcher.scheduler.advanceUntilIdle()` перед `assert`,
   иначе корутины не отработают.
10. **Use Case возвращают `Result<T>` для ошибок.**
    `assertTrue(result.isSuccess)` / `assertTrue(result.isFailure)`,
    `result.getOrThrow()` для happy path,
    `result.exceptionOrNull()` для проверки типа ошибки.
11. **`mockkStatic(Log::class)` — если ViewModel логирует.** В проекте
    используются `android.util.Log` в `ExercisesViewModel` и
    `AppDataViewModel` (failure-ветки). На JVM `android.util.Log` — stub,
    роняет короутину; в `@BeforeEach` мокай статически, в `@AfterEach`
    размокавай `unmockkStatic(Log::class)`. `AppearanceViewModel` и
    `MainViewModel` логов не делают — мокать там нечего.

## Запуск тестов

- **Предпочтительно**: `make test` — запуск `./gradlew test` с
  человекочитаемым отчётом через `scripts/test_report.py`
  (статистика + список упавших тестов + per-class таблица).
- **Один класс**:
  `./gradlew test --tests "com.myworkouts.domain.repository.EntryValidatorTest"`.
- **Поиск по паттерну**:
  `./gradlew test --tests "*EntryValidatorTest"`.
- **Один метод**:
  `./gradlew test --tests "com.myworkouts.domain.repository.EntryValidatorTest.sets_below_one_rejected"`.
- **Полная проверка**: `make check` — build + test + lint.

## First 60 seconds (triage template)

Прежде чем писать код, собери факты:

- **Цель**: новые unit-тесты, миграция с другого подхода, flaky failures,
  новый ViewModel, новый Use Case, рефакторинг?
- **Факты**:
  - Какой слой тестируется: domain model / Use Case / ViewModel /
    repository (data/) / provider / mapper?
  - Используется ли Flow / StateFlow / suspend? Это меняет диспетчер.
  - Есть ли I/O: DAO, ContentResolver, Clock, system time, `android.util.Log`?
    Это меняет выбор Fake vs Mock vs Stub и нужен ли `mockkStatic(Log::class)`.
  - Тесты TDD или покрытие существующего кода?
  - Используется ли Room / DataStore / Android Context напрямую?
    Если да — это уже не unit, а integration.

## Routing map (читай нужный reference быстро)

- Анатомия теста, именование, Given/When/Then →
  `references/fundamentals.md`
- MockK: `every`, `coEvery`, `verify`, `coVerify`, `relaxed`, `slot`,
  `capture`, `answers`, `mockkStatic` →
  `references/mocking-mockk.md`
- Fake-репозитории на `MutableStateFlow` (главный паттерн проекта) →
  `references/fakes.md`
- ViewModel: диспетчер, `setMain` / `resetMain`,
  `advanceUntilIdle`, `WhileSubscribed`-подписка →
  `references/viewmodel-testing.md`
- Use Case: `Result<T>`, `Context`+`ContentResolver` →
  `references/use-cases.md`
- Data layer: `Mappers` (`toDomain`/`toEntity`), `EntryValidator`,
  JSON-парсинг `ExportBackupUseCase`/`ImportBackupUseCase` →
  `references/data-layer.md`
- Assertions JUnit 5 + sealed-class проверки + `@ParameterizedTest` →
  `references/assertions.md`
- Проектные конвенции Jetpack-MyWorkouts →
  `references/project-conventions.md`
- Запуск `make test`, одиночные тесты, отчёт →
  `references/running-tests.md`
- Полные канонические примеры → `references/EXAMPLE.md`

## Common pitfalls → next best move

| Проблема | Решение |
|---|---|
| Тест ViewModel зависает навечно | Не используй `runBlocking` в тесте с `viewModelScope.launch`. Замени на `runTest(testDispatcher) { … }` + `StandardTestDispatcher` + `advanceUntilIdle()` |
| `every { mockRepo.exercises("") } returns flowOf(...)` не доезжает до UI state | Замени `mockk()` для `WorkoutDataRepository` на Fake-репозиторий (`MainViewModelFakeRepository`, `FakeExerciseRepository` и т.п.) на `MutableStateFlow`. См. `references/fakes.md` |
| `kotlinx.coroutines.test.UncompletedCoroutinesError` после теста | Добавь `testDispatcher.scheduler.advanceUntilIdle()` перед `assert` |
| Mockito / Mockito-Kotlin в импортах | В проекте только MockK (`io.mockk`). Замени на `mockk()` + `coEvery` |
| `android.util.Log` runtime exception в unit-тесте | ViewModel логирует — `mockkStatic(Log::class)` в `@BeforeEach` + `unmockkStatic` в `@AfterEach`. См. `references/mocking-mockk.md` |
| Сообщение `assertEquals` на английском | Сообщения — на русском (как в существующих тестах: «Должен быть 1 элемент»). См. `references/assertions.md` |
| `!!` в тесте (например, `successState.reminder!!`) | Запрещено AGENTS.md. Используй `assertNotNull(...)` или `?.let { ... }` |
| Сравнение sealed-state через `assertEquals(Success(item), state)` | Работает, но принято `assertTrue(state is Success)` + доступ к полям — лучше видно намерение |
| Параметр `currentDate` / `nowProvider` не задан — flaky по дню | Передавай `nowProvider = { fixedNowMillis }` явно (как в `EntryCreateViewModelTest`) |
| Backticks в имени теста (`` `function with spaces` ``) | Запрещено AGENTS.md. Используй snake_case без обратных кавычек |
| `mockk()` без `relaxed = true` падает на не-stub методах | Либо стабь через `every`, либо `mockk(relaxed = true)` (разрешено для интерфейсов) |
| Flow с `.catch { }` — assertion не срабатывает | `.catch` поглощает исключение. В unit-тесте используй `first()` / `try-catch`. |
| Дублирование `setupViewModel` в каждом `@Test` | Вынеси в `@BeforeEach fun setUp()` (как в `MainViewModelTest`) |
| `StateFlow` отдаёт `initialValue` — `WhileSubscribed` ещё не подписан | В тесте явно подпишись через `backgroundScope.launch { viewModel.uiState.collect { } }` ДО `advanceUntilIdle()` |
| `coEvery { mockRepo.getAllExercises() } returns flowOf(…)` — неверно | `getAllExercises(): suspend Result<List<Exercise>>` → `coEvery … returns Result.success(…)`. Для Flow-методов (`fun foo(): Flow<T>`) — только `every`. См. `references/use-cases.md` |
| `coEvery` для Flow-возвращающего метода (`fun foo(): Flow<T>`) | Неверно — `coEvery` только для `suspend`. Используй `every`. См. `references/mocking-mockk.md` |

## Verification checklist

- [ ] Тест на JUnit 5: импорты из `org.junit.jupiter.api.*`
- [ ] Имя метода в `snake_case` без обратных кавычек
- [ ] Комментарии `// Given`, `// When`, `// Then` в каждом тесте
      (или `// When + Then` для тривиальных)
- [ ] Нет `!!` — используй `?.`, `?:`, `let`, `checkNotNull`
- [ ] Для `ViewModel` — Fake-репозиторий на `MutableStateFlow`,
      не `mockk()` на `WorkoutDataRepository`
- [ ] Для `ViewModel` — `@BeforeEach` ставит `setMain`,
      `@AfterEach` — `resetMain`
- [ ] После действий ViewModel — `advanceUntilIdle()`
- [ ] Перед чтением `.value` для `WhileSubscribed`-stateIn — подписка
      через `backgroundScope.launch { uiState.collect { } }`
- [ ] Для suspend-функций — `coEvery` / `coVerify`,
      для Flow-функций — `every` / `verify`
- [ ] Сообщения `assert` на русском
- [ ] Нет новых интеграционных тестов ViewModel
- [ ] Файлы в `app/src/test/` зеркалят структуру `app/src/main/`
- [ ] Имя класса оканчивается на `Test`
- [ ] Если ViewModel логирует — `mockkStatic(Log::class)` +
      `unmockkStatic` пара
- [ ] `@OptIn(ExperimentalCoroutinesApi::class)` на классе,
      если используется `StandardTestDispatcher`

## References

- `references/fundamentals.md`
- `references/mocking-mockk.md`
- `references/fakes.md`
- `references/viewmodel-testing.md`
- `references/use-cases.md`
- `references/data-layer.md`
- `references/assertions.md`
- `references/project-conventions.md`
- `references/running-tests.md`
- `references/EXAMPLE.md`