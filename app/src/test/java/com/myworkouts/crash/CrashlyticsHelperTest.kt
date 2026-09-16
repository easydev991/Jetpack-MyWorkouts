package com.myworkouts.crash

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Test

/**
 * Тесты CrashlyticsHelper на JVM: Firebase не инициализирован,
 * getInstance() бросает исключение — helper обязан его поглотить
 * (design D2, спека «Безопасность вызовов телеметрии»).
 */
class CrashlyticsHelperTest {
    @Test
    fun logException_whenFirebaseNotInitialized_then_does_not_throw() {
        // Given: JVM-окружение без Firebase
        val throwable = IllegalStateException("тестовая ошибка")

        // When + Then
        assertDoesNotThrow { CrashlyticsHelper.logException(throwable) }
    }

    @Test
    fun logException_withMessage_whenFirebaseNotInitialized_then_does_not_throw() {
        // Given: JVM-окружение без Firebase, контекст операции задан
        val throwable = java.io.IOException("тестовый сбой ввода-вывода")

        // When + Then
        assertDoesNotThrow { CrashlyticsHelper.logException(throwable, "экспорт резервной копии") }
    }
}
