package com.myworkouts.ui.viewmodel

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Тест fallback-ветки [defaultSubscriptionTimeoutMs]: без property —
 * дефолтные 5 секунд. Переопределение через system property покрыто
 * end-to-end androidTest-компаньонами (`SubscriptionTimeoutRule`).
 */
class SubscriptionTimeoutTest {
    @AfterEach
    fun clearProperty() {
        System.clearProperty(SUBSCRIPTION_TIMEOUT_PROPERTY)
    }

    @Test
    fun withoutProperty_thenReturnsDefault5000() {
        assertEquals(5_000L, defaultSubscriptionTimeoutMs())
    }
}
