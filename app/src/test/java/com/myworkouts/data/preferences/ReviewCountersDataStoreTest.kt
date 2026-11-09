package com.myworkouts.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.myworkouts.domain.model.ReviewCounters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Unit-тесты для [ReviewCountersDataStore] (add-rustore-in-app-review,
 * tasks 3.1; design D3).
 *
 * Тесты поднимают реальный [ReviewCountersDataStore] поверх
 * `PreferenceDataStoreFactory.create(produceFile = { tempFile })` — без
 * Robolectric, по образцу [AppSettingsDataStoreTest].
 * `PreferenceDataStoreFactory` достаточно JVM-окружения.
 *
 * Scope DataStore — `backgroundScope` из [runTest]: это гарантирует
 * отмену внутренних корутин DataStore при завершении теста
 * (без `UncompletedCoroutinesError`). Каждый тест создаёт свежий
 * инстанс через extension [newDataStore] — между тестами нет
 * разделяемого состояния.
 *
 * Исключение — тест персистентности: первый инстанс создаётся со СВОИМ
 * scope (два одновременно живых DataStore на одном файле бросают
 * `IllegalStateException` «multiple DataStores active»), поэтому перед
 * созданием второго инстанса на том же файле его scope закрывается.
 *
 * Тесты покрывают сценарии tasks 3.1:
 * - инкремент счётчика созданных записей;
 * - отметка milestone пройденным: снимает совпадающий pending и не
 *   трогает другой/отсутствующий pending (design D3);
 * - установка, чтение и сброс pending-milestone;
 * - полный сброс;
 * - персистентность между инстансами хранилища.
 */
class ReviewCountersDataStoreTest {
    @TempDir
    lateinit var tempDir: Path

    /**
     * Создаёт [ReviewCountersDataStore] поверх временного файла со scope,
     * привязанным к [backgroundScope] текущего `runTest`. Вызывается
     * внутри каждого теста, чтобы каждый получил свежий инстанс.
     * [base] — готовый DataStore (для тестов с нестандартным scope).
     */
    private fun TestScope.newDataStore(
        base: DataStore<Preferences> = newPreferencesDataStore()
    ): ReviewCountersDataStore = ReviewCountersDataStore(base)

    /**
     * Создаёт базовый [DataStore] поверх временного файла
     * `review_counters` (то же имя, что в production — design D3).
     */
    private fun TestScope.newPreferencesDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                tempDir.resolve("review_counters.preferences_pb").toFile()
            }
        )

    @Test
    fun counters_first_collect_returns_defaults() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When — первый сбор Flow без записи
            val counters = dataStore.counters.first()

            // Then
            assertEquals(
                ReviewCounters(),
                counters,
                "До первой записи счётчики — дефолты (0/пусто/null)"
            )
        }

    @Test
    fun incrementCreatedEntriesCount_accumulates_across_calls() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When
            dataStore.incrementCreatedEntriesCount()

            // Then
            assertEquals(
                1,
                dataStore.counters.first().createdEntriesCount,
                "После первого инкремента счётчик == 1"
            )

            // When — повторный инкремент
            dataStore.incrementCreatedEntriesCount()

            // Then — накопление, а не перезапись
            assertEquals(
                2,
                dataStore.counters.first().createdEntriesCount,
                "Повторный инкремент накапливает счётчик (2)"
            )
        }

    // ---------- pending-milestone: установка, чтение, сброс ----------

    @Test
    fun setPendingMilestone_then_collect_returns_value() =
        runTest {
            // Given
            val dataStore = newDataStore()
            assertNull(dataStore.counters.first().pendingMilestone, "До установки pending нет")

            // When
            dataStore.setPendingMilestone(5)

            // Then
            assertEquals(
                5,
                dataStore.counters.first().pendingMilestone,
                "После setPendingMilestone(5) Flow эмитит 5"
            )
        }

    // ---------- markMilestonePassed: атомарный edit (design D3) ----------

    @Test
    fun markMilestonePassed_removes_matching_pending_and_adds_to_passed() =
        runTest {
            // Given — pending совпадает с отмечаемым milestone
            val dataStore = newDataStore()
            dataStore.setPendingMilestone(5)

            // When
            dataStore.markMilestonePassed(5)

            // Then — один атомарный edit: milestone в пройденных,
            // совпадающий pending снят (design D3)
            val counters = dataStore.counters.first()
            assertEquals(setOf(5), counters.passedMilestones, "Milestone добавлен в пройденные")
            assertNull(counters.pendingMilestone, "Совпадающий pending снят")
        }

    @Test
    fun markMilestonePassed_with_other_pending_does_not_touch_it() =
        runTest {
            // Given — pending указывает на другой milestone
            val dataStore = newDataStore()
            dataStore.setPendingMilestone(5)

            // When
            dataStore.markMilestonePassed(1)

            // Then — чужой pending не тронут (design D3)
            val counters = dataStore.counters.first()
            assertEquals(setOf(1), counters.passedMilestones, "Отмечен только milestone 1")
            assertEquals(5, counters.pendingMilestone, "Pending другого milestone не тронут")
        }

    @Test
    fun markMilestonePassed_without_pending_keeps_pending_null() =
        runTest {
            // Given — pending нет
            val dataStore = newDataStore()

            // When
            dataStore.markMilestonePassed(1)

            // Then — отсутствующий pending не «создаётся»
            val counters = dataStore.counters.first()
            assertEquals(setOf(1), counters.passedMilestones, "Milestone добавлен в пройденные")
            assertNull(counters.pendingMilestone, "Отсутствующий pending остался отсутствующим")
        }

    // ---------- полный сброс ----------

    @Test
    fun reset_clears_all_counters() =
        runTest {
            // Given — счётчик, пройденный milestone и pending
            val dataStore = newDataStore()
            dataStore.incrementCreatedEntriesCount()
            dataStore.markMilestonePassed(1)
            dataStore.setPendingMilestone(5)

            // When
            dataStore.reset()

            // Then
            assertEquals(ReviewCounters(), dataStore.counters.first(), "После reset все счётчики — дефолты")
        }

    // ---------- персистентность между инстансами ----------

    @Test
    fun counters_persist_between_instances() =
        runTest {
            // Given — файл, общий для двух инстансов хранилища
            val file = tempDir.resolve("review_counters.preferences_pb").toFile()

            // Первый инстанс — со СВОИМ scope: два одновременно живых
            // DataStore на одном файле бросают IllegalStateException,
            // поэтому перед созданием второго его scope закрывается.
            val firstScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            val firstStore =
                ReviewCountersDataStore(
                    PreferenceDataStoreFactory.create(
                        scope = firstScope,
                        produceFile = { file }
                    )
                )
            firstStore.incrementCreatedEntriesCount()
            firstStore.setPendingMilestone(5)
            firstStore.markMilestonePassed(1)
            firstScope.cancel()

            // When — второй инстанс на том же файле
            val secondStore =
                ReviewCountersDataStore(
                    PreferenceDataStoreFactory.create(
                        scope = backgroundScope,
                        produceFile = { file }
                    )
                )

            // Then — состояние восстановлено из файла
            val counters = secondStore.counters.first()
            assertEquals(1, counters.createdEntriesCount, "Счётчик созданных записей персистентен")
            assertEquals(setOf(1), counters.passedMilestones, "Пройденные milestones персистентны")
            assertEquals(5, counters.pendingMilestone, "Pending персистентен")
        }
}
