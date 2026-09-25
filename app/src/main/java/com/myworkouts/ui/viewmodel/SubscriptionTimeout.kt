package com.myworkouts.ui.viewmodel

/** Имя system property для переопределения таймаута подписки (тест-хук androidTest). */
internal const val SUBSCRIPTION_TIMEOUT_PROPERTY = "myworkouts.subscription_timeout_ms"

/**
 * Таймаут подписки StateFlow: 5 секунд (стандарт экранных ViewModel)
 * или значение property [SUBSCRIPTION_TIMEOUT_PROPERTY].
 */
internal fun defaultSubscriptionTimeoutMs(): Long =
    System.getProperty(SUBSCRIPTION_TIMEOUT_PROPERTY)?.toLongOrNull()
        ?: DEFAULT_SUBSCRIPTION_TIMEOUT_MS

// Константа вместо литерала: detekt MagicNumber запрещает 5_000 в теле функции.
private const val DEFAULT_SUBSCRIPTION_TIMEOUT_MS: Long = 5_000L
