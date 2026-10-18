package com.myworkouts.domain.crash

/**
 * Порт записи сбоев в телеметрию:
 * домен и ViewModel зависят от интерфейса, реализация (Firebase/Noop)
 * выбирается в композиционном корне по типу сборки.
 *
 * Контракт безопасности — как у предшественника `CrashlyticsHelper`:
 * реализация обязана поглощать собственные ошибки, телеметрия не
 * прерывает пользовательский сценарий.
 */
interface CrashReporter {
    /**
     * Записывает исключение в телеметрию сбоев.
     *
     * @param throwable исходное исключение
     * @param message контекст операции (например, «экспорт резервной копии»)
     */
    fun logException(
        throwable: Throwable,
        message: String? = null
    )
}
