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
fun whenitemloadedsuccessfully_thenupdatestosuccessstate() = runTest {
    // ...
    val currentState = viewModel.uiState.value
    assertTrue(currentState is WorkoutDetailsState.Success, "Состояние должно быть Success")
    val successState = currentState as WorkoutDetailsState.Success
    assertEquals(testWorkout, successState.workout, "Элемент должен совпадать")
    assertEquals(null, successState.reminder, "Активного напоминания быть не должно")
}
```

Преимущества над `assertEquals(Success(testWorkout), currentState)`:
- При провале сразу видно, **какой** подкласс ожидался
  (`Состояние должно быть Success`).
- Доступ к полям — без лишних cast'ов в data class equality.

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
fun invoke_whenJsonInvalid_thenThrowsSerializationException() = runTest {
    // Given
    val invalidJson = "{ invalid json }"
    val uri = mockk<Uri>()

    // When & Then
    assertThrows<SerializationException> {
        useCase(uri, invalidJson)
    }
}
```

`assertThrows` возвращает брошенное исключение — можно
проверить поля:

```kotlin
val ex = assertThrows<IllegalArgumentException> {
    AppTheme.valueOf("INVALID_THEME")
}
assertEquals("INVALID_THEME", ex.message)   // если нужно
```

## Групповые проверки

```kotlin
@Test
fun user_action_type_contains_all_expected_values() {
    assertEquals("create", UserActionType.CREATE.value)
    assertEquals("edit", UserActionType.EDIT.value)
    assertEquals("delete", UserActionType.DELETE.value)
    assertEquals("sort", UserActionType.SORT.value)
    // ...
}
```

Это серия независимых проверок в одном тесте — допустимо для
проверки enum-контракта, где одна ошибка enum-члена — это одна
логическая проблема.

## Сообщения на русском

Все assertion-сообщения — на русском, как в существующих тестах:

```kotlin
assertEquals(2, items.size, "Должно быть 2 элемента")
assertTrue(result is Success, "Состояние должно быть Success")
assertEquals("День рождения", workout.title, "Название должно совпадать")
```

Исключение — если тест читает только автор и потом забудет.
Но даже в этом случае русское сообщение полезнее — пройденный
тест не виден, а проваленный читает только разработчик, и ему
проще на русском.

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