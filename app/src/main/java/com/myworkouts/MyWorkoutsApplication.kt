package com.myworkouts

import android.app.Application
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.db.standardExercises
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.preferences.ReviewCountersDataStore
import com.myworkouts.data.repository.RoomWorkoutDataRepository
import com.myworkouts.domain.review.ReviewFlowLauncher
import com.myworkouts.review.ReviewFlowLauncherFactory
import com.myworkouts.ui.review.ReviewSessionHolder

/**
 * Композиционный корень manual DI (fix-entry-list-live-update, design.md
 * Decision 1): один инстанс DataStore/БД/репозитория на ПРОЦЕСС приложения
 * вместо одного на Activity. Application — стандартная точка процесс-скоупа
 * Android; `lazy` сохраняет время холодного старта (ничего не создаётся до
 * первого обращения).
 *
 * Пересоздание Activity (смена конфигурации) больше не плодит инстансы БД:
 * `InvalidationTracker` нотифицирует подписки своего инстанса, а он теперь
 * один — списки актуализируются после сохранения без перезапуска приложения.
 */
class MyWorkoutsApplication : Application() {
    /** Стандартный набор упражнений: prepopulate БД и восстановление
     * в `deleteAllData` — один и тот же список. */
    private val standardList by lazy { standardExercises(this) }

    /**
     * Настройки оформления. Переносится вместе с БД: пересоздание DataStore
     * на том же файле при пересоздании Activity — риск нескольких активных
     * инстансов на один файл (design.md Decision 1).
     */
    val dataStore: AppSettingsDataStore by lazy { AppSettingsDataStore.create(this) }

    /**
     * Хранилище счётчиков review-майлстоунов (add-rustore-in-app-review,
     * design D3) — preferences DataStore на отдельном файле
     * `review_counters`, не расширение [dataStore]: пользовательские
     * настройки UI и служебные счётчики не смешиваются. Process-singleton
     * по образцу [dataStore]/[repository]: сброс из флоу полного удаления
     * данных (задача 3.3) и wiring review-флоу (этапы 6.x/7.x) обязаны
     * попадать в один инстанс на файл (singleton-делегат `preferencesDataStore`).
     */
    val reviewCountersDataStore: ReviewCountersDataStore by lazy { ReviewCountersDataStore.create(this) }

    /**
     * Лаунчер review-флоу (add-rustore-in-app-review, design D1): flavor-фабрика
     * `ReviewFlowLauncherFactory` — один и тот же публичный FQN в обоих flavor
     * source set'ах (rustore — RuStore Review SDK, github — no-op), конкретная
     * реализация подставляется компиляцией flavor'а, условной логики нет.
     * Process-singleton по образцу [dataStore]/[repository].
     */
    val reviewFlowLauncher: ReviewFlowLauncher by lazy { ReviewFlowLauncherFactory.create(this) }

    /**
     * Holder сессии review-флоу (add-rustore-in-app-review, design D2): флаг
     * «авто-запрос выполнялся в этой сессии». In-memory, не персистится —
     * смерть процесса даёт новую сессию. Process-singleton: пишется и читается
     * разными ViewModel (`MainViewModel`/`EntryCreateViewModel`), инстанс —
     * один на процесс.
     */
    val reviewSessionHolder: ReviewSessionHolder by lazy { ReviewSessionHolder() }

    /** БД приложения с prepopulate стандартного набора. */
    private val database: WorkoutDatabase by lazy { WorkoutDatabase.create(this, standardList) }

    /** Репозиторий над [database]. */
    val repository: RoomWorkoutDataRepository by lazy { RoomWorkoutDataRepository(database, standardList) }
}
