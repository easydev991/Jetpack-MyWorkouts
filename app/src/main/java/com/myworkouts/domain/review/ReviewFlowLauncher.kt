package com.myworkouts.domain.review

/**
 * Порт запуска нативного review-флоу (форма оценки приложения в магазине):
 * домен и ViewModel зависят от интерфейса, flavor-реализация (rustore —
 * RuStore Review SDK, github — no-op) подставляется на этапе компиляции
 * flavor'а через фабрику в flavor source-set. Прецедент порта —
 * `CrashReporter` (design D2).
 *
 * Контракт без платформенных типов: исход запуска описывается доменной
 * иерархией [ReviewFlowOutcome], маппинг ошибок SDK в [ReviewErrorType]
 * выполняет flavor-реализация (tasks 4.1).
 */
interface ReviewFlowLauncher {
    /**
     * Запускает нативную форму оценки.
     *
     * @return исход запуска: показ формы ([ReviewFlowOutcome.Shown]) или
     * типизированная ошибка ([ReviewFlowOutcome.Error]) — известные сбои
     * SDK классифицируются реализацией в [ReviewErrorType], а не бросаются
     * наружу исключениями
     */
    suspend fun launch(): ReviewFlowOutcome
}

/**
 * Исход запуска review-флоу.
 */
sealed interface ReviewFlowOutcome {
    /** Нативная форма оценки показана пользователю. */
    data object Shown : ReviewFlowOutcome

    /** Показать форму не удалось; тип ошибки — [errorType]. */
    data class Error(
        val errorType: ReviewErrorType
    ) : ReviewFlowOutcome
}

/**
 * Доменная классификация ошибок запуска review-флоу (design D2). Имена
 * констант — wire-значения аналитики (параметр error_type события
 * review_auto_request), отдельное строковое поле не вводится.
 * Классификация сознательно не шире шести значений: неименованные подтипы
 * ошибок SDK маппятся flavor-реализацией в UNKNOWN.
 */
enum class ReviewErrorType {
    NOT_INSTALLED,
    OUTDATED,
    USER_UNAUTHORIZED,
    REQUEST_LIMIT_REACHED,
    REVIEW_EXISTS,
    UNKNOWN
}
