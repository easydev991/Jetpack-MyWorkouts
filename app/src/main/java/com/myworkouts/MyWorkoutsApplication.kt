package com.myworkouts

import android.app.Application
import com.myworkouts.data.db.WorkoutDatabase
import com.myworkouts.data.db.standardExercises
import com.myworkouts.data.preferences.AppSettingsDataStore
import com.myworkouts.data.repository.RoomWorkoutDataRepository

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

    /** БД приложения с prepopulate стандартного набора. */
    private val database: WorkoutDatabase by lazy { WorkoutDatabase.create(this, standardList) }

    /** Репозиторий над [database]. */
    val repository: RoomWorkoutDataRepository by lazy { RoomWorkoutDataRepository(database, standardList) }
}
