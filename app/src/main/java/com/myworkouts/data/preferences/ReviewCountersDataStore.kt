package com.myworkouts.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.myworkouts.domain.model.ReviewCounters
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** DataStore-ключ счётчика созданных записей. */
private val CREATED_ENTRIES_COUNT_KEY = intPreferencesKey("created_entries_count")

/**
 * DataStore-ключ набора пройденных milestones: int-ы сериализуются
 * строками (design D3 — по аналогии со строковыми ключами enum в
 * [AppSettingsDataStore]).
 */
private val PASSED_MILESTONES_KEY = stringSetPreferencesKey("passed_milestones")

/** DataStore-ключ pending-milestone; отсутствие ключа = pending нет (sentinel, design D3). */
private val PENDING_MILESTONE_KEY = intPreferencesKey("pending_milestone")

/**
 * Preferences DataStore служебных счётчиков review-майлстоунов
 * (add-rustore-in-app-review, design D3) — ОТДЕЛЬНЫЙ файл
 * `review_counters`, не расширение [AppSettingsDataStore]:
 * пользовательские настройки UI и служебные счётчики не смешиваются.
 *
 * @param dataStore Базовый [DataStore] для `review_counters` (singleton
 *   per file — обеспечивается `preferencesDataStore` делегатом в
 *   [Companion.create] для production; в тестах инжектится через
 *   `PreferenceDataStoreFactory.create(produceFile = ...)`).
 *
 * @implNote Хранит 3 ключа (created_entries_count/passed_milestones/
 *   pending_milestone). Имя файла review_counters НЕ менять (миграция
 *   DataStore). Инвариант «pending ∉ passedMilestones» обеспечивается
 *   атомарностью [markMilestonePassed] без guard-веток (design D3):
 *   состояние «pending ∈ passed» возникнуть и сохраниться не может.
 *   Чтение счётчиков может упасть IOException (сбой файла DataStore) —
 *   потребители обрабатывают тихо (design D5).
 */
class ReviewCountersDataStore(
    private val dataStore: DataStore<Preferences>
) {
    /**
     * Flow текущего состояния счётчиков: одно чтение `data` для всех
     * трёх ключей (по образцу `entryFieldVisibility`). Отсутствие
     * ключей = дефолты [ReviewCounters] (0/пусто/null); мусорная
     * строка в наборе пройденных молча отбрасывается (тот же контракт
     * fallback-а, что у [AppSettingsDataStore.lastOverviewTab]).
     */
    val counters: Flow<ReviewCounters> =
        dataStore.data.map { preferences ->
            ReviewCounters(
                createdEntriesCount = preferences[CREATED_ENTRIES_COUNT_KEY] ?: 0,
                passedMilestones =
                    preferences[PASSED_MILESTONES_KEY]
                        ?.mapNotNull { milestone -> milestone.toIntOrNull() }
                        ?.toSet()
                        ?: emptySet(),
                pendingMilestone = preferences[PENDING_MILESTONE_KEY]
            )
        }

    /**
     * Атомарно инкрементит счётчик созданных записей (внутри
     * `edit {}` — design D3). Вызывается после успешного создания
     * записи; редактирование и импорт бэкапа счётчик не трогают (D5).
     */
    suspend fun incrementCreatedEntriesCount() {
        dataStore.edit { preferences ->
            val current = preferences[CREATED_ENTRIES_COUNT_KEY] ?: 0
            preferences[CREATED_ENTRIES_COUNT_KEY] = current + 1
        }
    }

    /**
     * Запоминает запланированный к показу milestone (pending).
     *
     * @param milestone Порог, запланированный к показу
     */
    suspend fun setPendingMilestone(milestone: Int) {
        dataStore.edit { preferences ->
            preferences[PENDING_MILESTONE_KEY] = milestone
        }
    }

    /**
     * Отмечает milestone пройденным: ОДИН атомарный `edit {}` добавляет
     * milestone в пройденные и снимает ТОЛЬКО совпадающий pending —
     * другой или отсутствующий pending не трогается (design D3).
     *
     * @param milestone Порог, для которого review-флоу запускался
     */
    suspend fun markMilestonePassed(milestone: Int) {
        dataStore.edit { preferences ->
            val passed = preferences[PASSED_MILESTONES_KEY] ?: emptySet()
            preferences[PASSED_MILESTONES_KEY] = passed + milestone.toString()
            if (preferences[PENDING_MILESTONE_KEY] == milestone) {
                preferences.remove(PENDING_MILESTONE_KEY)
            }
        }
    }

    /**
     * Полный сброс счётчиков (design D3 — вызов из onSuccess-ветки
     * `deleteAllData`): счётчик, пройденные milestones и pending
     * возвращаются к дефолтам.
     */
    suspend fun reset() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }

    companion object {
        /** Контекст тихого лога сбоя хранилища счётчиков review-флоу (design D5). */
        const val REVIEW_COUNTERS_FAILURE_CONTEXT =
            "Review-флоу: сбой хранилища счётчиков"

        /**
         * Singleton-per-file [DataStore] в файле `review_counters` —
         * повторные обращения возвращают один и тот же инстанс (иначе
         * DataStore бросил бы `IllegalStateException`).
         */
        private val Context.reviewCountersDataStoreSingleton: DataStore<Preferences>
            by preferencesDataStore(name = "review_counters")

        /**
         * Создаёт [ReviewCountersDataStore] поверх [Context] с правильным
         * именем файла. Для тестов — прямой конструктор с
         * `PreferenceDataStoreFactory.create(produceFile = ...)`,
         * минуя singleton-делегат (нужен temp-файл).
         */
        fun create(context: Context): ReviewCountersDataStore =
            ReviewCountersDataStore(context.reviewCountersDataStoreSingleton)
    }
}
