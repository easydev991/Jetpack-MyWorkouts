---
name: kotlin-testing
description: >
  Экспертное руководство по unit-тестированию в Android-проекте Jetpack-MyWorkouts:
  JUnit 5, MockK, Fake-репозитории на MutableStateFlow, kotlinx-coroutines-test
  для ViewModel и Use Case, AAA / Given-When-Then, проектные соглашения
  (snake_case, без !!, русские сообщения, NoOpLogger, StubResourceProvider),
  а также ограничения (запрет новых интеграционных тестов ViewModel).
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
> Compose Testing, Room in-memory, Turbine). Этот skill про `app/src/test/`.

## Agent behavior contract (следуй этим правилам)

1. **JUnit 5 — основной фреймворк unit-тестов.** Все импорты из
   `org.junit.jupiter.api.*`. Используй `@Test`, `@BeforeEach`,
   `@AfterEach`. JUnit 4 (`org.junit.*`, `@RunWith(AndroidJUnit4::class)`)
   — только в `androidTest/`.
2. **Given / When / Then в каждом тесте.** Разделяй тело тремя
   комментариями-маркерами. Это и документация, и визуальный ритм.
3. **snake_case для имён тестов, без обратных кавычек.** Формат:
   `function_whenCondition_thenExpectedResult`. Обратные кавычки запрещены
   AGENTS.md — пиши без них. Допустимы русские слова в snake_case для
   названий свойств (`totaldays_в_calculated_содержит_общее_количество_дней`),
   но английский формат предпочтительнее.
4. **Никакого `!!`.** Даже в тестах. Используй `?.`, `?:`, `let`,
   `checkNotNull`. Это требование AGENTS.md, не обсуждается.
5. **MockK для интерфейсов, Fake для репозиториев с Flow.**
   `mockk()` / `mockk(relaxed = true)` — для интерфейсов без сложной
   логики эмиссий (`Logger`, `DataStore`, `ResourceProvider`).
   Для `WorkoutRepository`, `WorkoutReminderRepository` — пиши `Fake<Name>` на
   `MutableStateFlow` (см. `references/fakes.md`).
6. **`coEvery` / `coVerify` для suspend-функций.** `every` / `verify` —
   только для не-suspend. MockK не делает автоматический выбор.
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
11. **Logger в тестах ViewModel — `NoOpLogger` или `mockk(relaxed = true)`.**
    `com.myworkouts.util.NoOpLogger` — предпочтительный вариант.
    `mockk(relaxed = true)` — тоже допустим (relaxed-мок перехватывает
    любые вызовы; правило 5 и `EXAMPLE.md` отражают оба подхода).
    Смешивать в одном тест-классе не нужно — выбери один.

## Запуск тестов

- **Предпочтительно**: `make test` — запуск `./gradlew test` с
  человекочитаемым отчётом через `scripts/test_report.py`
  (статистика + список упавших тестов + per-class таблица).
- **Один класс**:
  `./gradlew test --tests "com.myworkouts.domain.usecase.CalculateWorkoutDurationUseCaseTest"`.
- **Поиск по паттерну**:
  `./gradlew test --tests "*WorkoutDurationTest"`.
- **Один метод**:
  `./gradlew test --tests "com.myworkouts.domain.usecase.CalculateWorkoutDurationUseCaseTest.calculate_when_same_day_then_returns_today"`.
- **Полная проверка**: `make check` — build + test + lint.

## First 60 seconds (triage template)

Прежде чем писать код, собери факты:

- **Цель**: новые unit-тесты, миграция с другого подхода, flaky failures,
  новый ViewModel, новый Use Case, рефакторинг?
- **Факты**:
  - Какой слой тестируется: domain model / Use Case / ViewModel /
    repository (data/) / provider / mapper?
  - Используется ли Flow / StateFlow / suspend? Это меняет диспетчер.
  - Есть ли I/O: DAO, ContentResolver, Clock, system time?
    Это меняет выбор Fake vs Mock vs Stub.
  - Тесты TDD или покрытие существующего кода?
  - Используется ли Room / DataStore / Android Context напрямую?
    Если да — это уже не unit, а integration.

## Routing map (читай нужный reference быстро)

- Анатомия теста, именование, Given/When/Then →
  `references/fundamentals.md`
- MockK: `every`, `coEvery`, `verify`, `coVerify`, `relaxed`, `slot`,
  `capture`, `answers`, `throws` → `references/mocking-mockk.md`
- Fake-репозитории на `MutableStateFlow` →
  `references/fakes.md`
- ViewModel: диспетчер, `setMain` / `resetMain`,
  `advanceUntilIdle`, `SavedStateHandle` → `references/viewmodel-testing.md`
- Use Case: `Result<T>`, `Clock.fixed`, `currentDate` →
  `references/use-cases.md`
- Data layer: mapper, converter, provider, JSON-ресурсы →
  `references/data-layer.md`
- Assertions JUnit 5 + sealed-class проверки →
  `references/assertions.md`
- Проектные конвенции Jetpack-MyWorkouts →
  `references/project-conventions.md`
- Запуск `make test`, одиночные тесты, отчёт →
  `references/running-tests.md`
- Полные канонические примеры → `references/EXAMPLE.md`

## Common pitfalls → next best move

| Проблема | Решение |
|---|---|
| Тест ViewModel зависает навечно | Не используй `runBlocking` в тесте с `viewModelScope.launch`. Замени на `runTest { ... }` + `StandardTestDispatcher` + `advanceUntilIdle()` |
| `every { mockRepo.getAllWorkouts() } returns flowOf(...)` не доезжает до UI state | Замени `mockk()` для `WorkoutRepository` на `FakeWorkoutRepository` на `MutableStateFlow`. См. `references/fakes.md` |
| `kotlinx.coroutines.test.UncompletedCoroutinesError` после теста | Добавь `testDispatcher.scheduler.advanceUntilIdle()` перед `assert` или после `Dispatchers.resetMain()` в `@AfterEach` |
| Mockito / Mockito-Kotlin в импортах | В проекте только MockK (`io.mockk`). Замени на `mockk()` + `coEvery` |
| Сообщение `assertEquals` на английском | Сообщения — на русском (как в существующих тестах: «Должен быть 1 элемент»). См. `references/assertions.md` |
| `!!` в тесте (например, `successState.reminder!!`) | Запрещено AGENTS.md. Используй `assertNotNull(...)` или `?.let { ... }` |
| Сравнение sealed-state через `assertEquals(Success(item), state)` | Работает, но принято `assertTrue(state is Success)` + доступ к полям — лучше видно намерение |
| Зависимость от `System.currentTimeMillis()` в тесте | Используй `Clock.fixed(...)` + `currentTimeMillisProvider { ... }`. См. `references/use-cases.md` (по образцу `BuildWorkoutReminderUseCaseTest`) |
| Интеграционный тест ViewModel с Room | Запрещено. Используй unit-тест с Fake-репозиторием на `MutableStateFlow` |
| Параметр `currentDate` не задан — flaky по дню | Передавай `currentDate` явно (`LocalDate.of(2024, ...)`) — см. `references/use-cases.md` |
| Backticks в имени теста (`` `function with spaces` ``) | Запрещено AGENTS.md. Используй snake_case без обратных кавычек |
| `mockk()` без `relaxed = true` падает на не-stub методах | Либо стабь через `every`, либо `mockk(relaxed = true)` (разрешено для интерфейсов) |
| Flow с `.catch { }` — assertion не срабатывает | `.catch` поглощает исключение. В unit-тесте используй `first()` / `try-catch`. Turbine — только в `androidTest/` |
| Дублирование `setupViewModel` в каждом `@Test` | Вынеси в `@BeforeEach fun setUp()` |

## Verification checklist

- [ ] Тест на JUnit 5: импорты из `org.junit.jupiter.api.*`
- [ ] Имя метода в `snake_case` без обратных кавычек
- [ ] Комментарии `// Given`, `// When`, `// Then` в каждом тесте
- [ ] Нет `!!` — используй `?.`, `?:`, `let`, `checkNotNull`
- [ ] Для `ViewModel` — Fake-репозиторий на `MutableStateFlow`,
      не `mockk()` на `WorkoutRepository`
- [ ] Для `ViewModel` — `@BeforeEach` ставит `setMain`,
      `@AfterEach` — `resetMain`
- [ ] После действий ViewModel — `advanceUntilIdle()`
- [ ] Для suspend-функций — `coEvery` / `coVerify`
- [ ] Сообщения `assert` на русском
- [ ] Нет новых интеграционных тестов ViewModel
- [ ] Файлы в `app/src/test/` зеркалят структуру `app/src/main/`
- [ ] Имя класса оканчивается на `Test`
- [ ] Если тест про время — `Clock.fixed(...)` или явный
      `currentTimeMillisProvider`
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