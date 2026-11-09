package com.myworkouts.ui.review

/**
 * In-memory holder сессии процесса (add-rustore-in-app-review, design D2):
 * единственный флаг «авто-запрос оценки приложения в этой сессии уже
 * выполнялся». Presentation-слой (`ui/`) — flavor-независимый, `main`:
 * поведение гейта не зависит от flavor'а, зависит только лаунчер.
 *
 * Пишется `MainViewModel` при запуске авто-флоу (задача 7.4 — закрывает
 * флаг ДО запуска лаунчера), читается `EntryCreateViewModel` при
 * планировании (задача 7.2 — подаётся в
 * `ReviewMilestoneDecider.decide`). До задач 7.x не используется.
 *
 * Не персистится: смерть процесса — новая сессия (флаг сбрасывается
 * фактом пересоздания process-singleton'а в `MyWorkoutsApplication`).
 * Метода reset нет: сброс счётчиков при полном удалении данных
 * (`ReviewCountersDataStore.reset`, задача 3.3) флаг сессии не трогает
 * (design D2/D3).
 *
 * @property hasAutoRequestRun true — авто-запрос в этой сессии уже
 *   выполнялся, повторный в той же сессии не инициируется
 */
class ReviewSessionHolder {
    @Volatile
    var hasAutoRequestRun: Boolean = false
}
