package com.myworkouts.domain.review

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Контрактные тесты лаунчера review-флоу (tasks 2.3, design D2): фиксируют
 * форму доменного контракта, а не содержательную логику — состав
 * sealed-иерархии [ReviewFlowOutcome], полный набор wire-значений
 * [ReviewErrorType] и вид fake-реализаций [ReviewFlowLauncher].
 * Содержательная логика лаунчера тестируется flavor-реализацией (tasks 4.1),
 * fake-лаунчер переиспользуется тестами отложенного показа (tasks 7.3).
 */
class ReviewFlowContractTest {
    @Test
    fun outcome_hierarchy_contains_exactly_shown_and_error() {
        // Given — sealed-иерархия исходов запуска review-флоу. when без else
        // компилируется только при исчерпывающем покрытии наследников: добавление
        // третьего исхода сломает компиляцию теста — контракт «ровно Shown
        // и Error(errorType)» зафиксирован на этапе компиляции, без reflection
        // (kotlin-reflect в тестовом classpath отсутствует)
        val outcomes =
            listOf(
                ReviewFlowOutcome.Shown,
                ReviewFlowOutcome.Error(ReviewErrorType.UNKNOWN)
            )
        // When — классификация каждого исхода исчерпывающим when
        val kindNames =
            outcomes.map { outcome ->
                when (outcome) {
                    is ReviewFlowOutcome.Shown -> "Shown"
                    is ReviewFlowOutcome.Error -> "Error"
                }
            }
        // Then — ровно два исхода, оба существуют и различимы
        assertEquals(listOf("Shown", "Error"), kindNames)
    }

    @Test
    fun error_type_contains_exactly_six_wire_values() {
        // Given — имена enum-констант являются wire-значениями аналитики,
        // отдельное строковое поле в доменную модель не вводится
        val expected =
            listOf(
                "NOT_INSTALLED",
                "OUTDATED",
                "USER_UNAUTHORIZED",
                "REQUEST_LIMIT_REACHED",
                "REVIEW_EXISTS",
                "UNKNOWN"
            )
        // When
        val actual = ReviewErrorType.entries.map { it.name }
        // Then — полный набор wire-значений в порядке объявления
        assertEquals(expected, actual)
    }

    @Test
    fun fake_launcher_reports_shown_outcome() =
        runTest {
            // Given — лаунчер, имитирующий успешный показ нативной формы оценки
            val launcher = ShownLauncher()

            // When
            val outcome = launcher.launch()

            // Then — исход Shown без данных
            assertEquals(ReviewFlowOutcome.Shown, outcome)
        }

    @Test
    fun fake_launcher_reports_error_with_wire_type() =
        runTest {
            // Given — лаунчер, имитирующий ошибку запуска SDK-флоу
            val launcher = ErrorLauncher(ReviewErrorType.OUTDATED)

            // When
            val outcome = launcher.launch()

            // Then — исход Error несёт тип ошибки (равенство data class и поле)
            assertEquals(ReviewFlowOutcome.Error(ReviewErrorType.OUTDATED), outcome)
            val error =
                when (outcome) {
                    is ReviewFlowOutcome.Error -> outcome
                    is ReviewFlowOutcome.Shown -> throw AssertionError("Ожидался исход Error, получен Shown")
                }
            assertEquals(ReviewErrorType.OUTDATED, error.errorType)
        }

    /** Fake-лаунчер: всегда сообщает об успешном показе формы. */
    private class ShownLauncher : ReviewFlowLauncher {
        override suspend fun launch(): ReviewFlowOutcome = ReviewFlowOutcome.Shown
    }

    /** Fake-лаунчер: всегда возвращает ошибку заданного типа. */
    private class ErrorLauncher(
        private val errorType: ReviewErrorType
    ) : ReviewFlowLauncher {
        override suspend fun launch(): ReviewFlowOutcome = ReviewFlowOutcome.Error(errorType)
    }
}
