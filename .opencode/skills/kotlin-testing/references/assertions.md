# Assertions

JUnit 5 assertions + Kotlin-идиомы для sealed-классов и data class.

## Импорт

```kotlin
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertInstanceOf
```

`org.junit.Assert.*` — JUnit 4, не используй в unit-тестах.

## Базовые проверки

```kotlin
assertEquals(expected, actual, "Сообщение на русском")
assertEquals(expected, actual) { "Лямбда-сообщение — ленивое, для сложных случаев" }

assertTrue(condition, "Условие должно быть истинным")
assertFalse(condition, "Условие должно быть ложным")

assertNull(value, "Должно быть null")
assertNotNull(value, "Не должно быть null")

assertInstanceOf<BackupException>(ex, "Тип ошибки — BackupException")
```

## Bool-проверки

```kotlin
// ПЛОХО — сравнение через ==
assertEquals(true, condition)
assertEquals(false, condition)

// ХОРОШО
assertTrue(condition)
assertFalse(condition)

// Для negated
assertTrue(!condition)         // или assertFalse(condition)
```

## Sealed class / интерфейс — `is` + доступ к полям

```kotlin
@Test
fun state_when_subscribed_then_success_with_entries() =
    runTest(testDispatcher) {
        // Given
        viewModel = MainViewModel(repository)

        // When
        backgroundScope.launch { viewModel.uiState.collect { } }
        advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assertTrue(state is MainUiState.Success, "После эмиссии — Success")
        val success = state as MainUiState.Success
        assertEquals(listOf(item1, item2), success.entries, "Записи приходят из реактивного источника")
        assertNull(success.pendingDeleteId, "Без действия pendingDeleteId = null")
    }
```

Преимущества над `assertEquals(Success(testWorkout), currentState)`:
- При провале сразу видно, **какой** подкласс ожидался
  (`После эмиссии — Success`).
- Доступ к полям — без лишних cast'ов в data class equality.

Для `data class` state (без подклассов) — конструктор равенства
допустим и компактнее:

```kotlin
// AppearanceUiState — обычный data class
assertEquals(
    AppearanceUiState(theme = theme, useDynamicColors = useDynamicColors),
    actual
)
```

## `assertTrue(x is Y)` vs `assertEquals(Y, x)`

| Стиль | Когда |
|---|---|
| `assertTrue(x is Y)` + cast | sealed/интерфейс, нужно достать поля для отдельных assert |
| `assertEquals(Y(expected), x)` | data class со всеми полями, equality достаточно |

`assertTrue(x is Y)` **обязательно** для проверки sealed перед
приведением, если ниже идёт `as Y` — иначе получишь
`ClassCastException` без понятного сообщения.

## Условный assert через Kotlin `assert`

Для проверок, которые не критичны для теста, но могут указать на
проблему:

```kotlin
assert(AppTheme.entries.contains(AppTheme.LIGHT)) { "Должно содержать LIGHT" }
assertEquals(3, AppTheme.entries.size, "Должны быть 3 значения темы")
```

`assert(...) { "..." }` — это Kotlin-stdlib `kotlin.assert`,
не JUnit. Используй только если assert JUnit 5 не подходит по
семантике (например, проверка в середине Given-блока).

## `assertThrows` для исключений

```kotlin
@Test
fun valueOf_throws_for_unknown_name() {
    // Given
    val unknown = "MADE_UP"

    // When + Then
    val ex = assertThrows<IllegalArgumentException> {
        ExerciseCategory.valueOf(unknown)
    }
    assertTrue(
        ex.message?.contains(unknown) == true,
        "Сообщение содержит имя значения"
    )
}
```

Альтернатива через `runCatching` — компактнее, но не даёт доступа к
исключению как объекту:

```kotlin
@Test
fun valueOf_throws_for_unknown_name_compact() {
    assertTrue(
        runCatching { ExerciseCategory.valueOf("MADE_UP") }.isFailure
    )
}
```

## Групповые проверки

```kotlin
@Test
fun all_categories_contain_exactly_five_values() {
    // Given — enum ExerciseCategory
    // When
    val values = ExerciseCategory.values()

    // Then
    assertEquals(5, values.size)
}
```

Это серия независимых проверок в одном тесте — допустимо для
проверки enum-контракта, где одна ошибка enum-члена — это одна
логическая проблема.

## `@ParameterizedTest`

Для повторяющихся кейсов с одним и тем же тестом, но разными данными —
предпочтительнее обычного `@Test` + `forEach`. Снижает шум и считается
отдельным тестом в отчёте.

### `@ValueSource` — примитивы

`EntryValidatorTest`:

```kotlin
@ParameterizedTest
@ValueSource(ints = [0, -1, -100])
fun sets_below_one_rejected(invalidSets: Int) {
    // Given
    val entry = baseEntry(sets = invalidSets)

    // When
    val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

    // Then
    assertTrue(result.isFailure)
    assertEquals(
        EntryValidationError.INVALID_SETS,
        (result.exceptionOrNull() as? EntryValidationException)?.error
    )
}

@ParameterizedTest
@ValueSource(doubles = [-0.5, -1.0, -100.0])
fun negative_weight_rejected(invalidWeight: Double) {
    // Given
    val entry = baseEntry(weightKg = invalidWeight)

    // When
    val result = EntryValidator.validate(entry, ExerciseMeasuredBy.REPS)

    // Then
    assertTrue(result.isFailure)
    assertEquals(
        EntryValidationError.INVALID_WEIGHT,
        (result.exceptionOrNull() as? EntryValidationException)?.error
    )
}
```

### `@EnumSource` — перебор enum-значений

`ExerciseCategoryTest` / `AppearanceViewModelTest`:

```kotlin
@ParameterizedTest
@EnumSource(ExerciseCategory::class)
fun valueOf_roundtrips_for_known_name(category: ExerciseCategory) {
    // Given
    val name = category.name

    // When
    val parsed = ExerciseCategory.valueOf(name)

    // Then
    assertEquals(category, parsed)
}
```

### `@MethodSource` — таблицы (label + данные) из `companion object`

Канонический пример (полный тест-метод + companion) — `viewmodel-testing.md`
→ «`@ParameterizedTest` + `@MethodSource` для пар из `AppearanceUiState`».
Ниже — только сигнатура и пара лайвхаков:

```kotlin
@ParameterizedTest(name = "[{0}]")
@MethodSource("themeAndDynamicColorsPairs")
fun ui_state_should_emit_combined_theme_and_dynamic_colors(
    theme: AppTheme,
    useDynamicColors: Boolean
) = runTest(testDispatcher) {
    // Given / When
    every { mockDataStore.theme } returns flowOf(theme)
    every { mockDataStore.useDynamicColors } returns flowOf(useDynamicColors)
    val viewModel = AppearanceViewModel(mockDataStore)
    backgroundScope.launch { viewModel.uiState.collect { } }
    advanceUntilIdle()

    // Then
    assertEquals(
        AppearanceUiState(theme = theme, useDynamicColors = useDynamicColors),
        viewModel.uiState.value
    )
}
```

Каждая строка таблицы — отдельный запуск теста с понятным именем
(`[AppTheme.LIGHT, true]`). Если один кейс ломается — упадут все
сразу (как обычный `@Test`), в отчёте — несколько записей.

## Сообщения на русском

Все assertion-сообщения — на русском, как в существующих тестах:

```kotlin
assertEquals(2, items.size, "Должно быть 2 элемента")
assertTrue(result.isSuccess, "Результат должен быть успешным")
assertEquals("День рождения", workout.title, "Название должно совпадать")
```

Без исключений: проваленный тест читает разработчик, и ему проще
на русском — сообщения на английском увеличивают время локализации
бага. Лямбда-сообщения (`assertEquals(...) { "..." }`) — ленивые,
для тяжёлых строк.

## Что НЕ делать

- Не используй `assertEquals(true, x)` / `assertEquals(false, x)`.
- Не комментируй то, что уже говорит сообщение assertion:
  ```kotlin
  // ПЛОХО
  assertEquals(2, size)  // Должно быть 2
  // ХОРОШО
  assertEquals(2, size, "Должно быть 2 элемента")
  ```
- Не используй `assertTrue(x == null)` — `assertNull(x)` яснее.
- Не используй `org.junit.Assert.assertEquals` — это JUnit 4.
- Не глуши исключения в тесте: `try { ... } catch (e: Throwable) {}` —
  скрывает баги.