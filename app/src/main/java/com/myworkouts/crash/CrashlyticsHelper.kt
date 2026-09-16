@file:Suppress("TooGenericExceptionCaught", "SwallowedException")

package com.myworkouts.crash

import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Безопасная обёртка над Crashlytics (design D2): любая ошибка самой
 * телеметрии — включая неинициализированный Firebase на JVM —
 * поглощается и не логируется рекурсивно (спека «Безопасность вызовов
 * телеметрии»). Вызывается из use-cases и ViewModel напрямую, без
 * инъекции.
 */
object CrashlyticsHelper {
    /** Кастомный ключ Crashlytics с контекстом операции. */
    private const val KEY_ERROR_MESSAGE = "error_message"

    /**
     * Записывает исключение в Crashlytics.
     *
     * @param throwable исходное исключение
     * @param message контекст операции (например, «экспорт резервной копии»)
     */
    fun logException(
        throwable: Throwable,
        message: String? = null
    ) {
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            message?.let { crashlytics.setCustomKey(KEY_ERROR_MESSAGE, it) }
            crashlytics.recordException(throwable)
        } catch (e: Exception) {
            // Телеметрия не должна прерывать пользовательский сценарий.
        }
    }
}
