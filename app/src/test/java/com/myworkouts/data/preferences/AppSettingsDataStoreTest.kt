package com.myworkouts.data.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/**
 * Unit-тесты для [AppSettingsDataStore] (archive-alert-hide, tasks §1.0;
 * spec `appearance` «AppSettingsDataStore persists archive-dialog preference»).
 *
 * Тесты поднимают реальный [AppSettingsDataStore] поверх
 * `PreferenceDataStoreFactory.create(produceFile = { tempFile })` — без
 * Robolectric. `PreferenceDataStoreFactory` достаточно JVM-окружения.
 *
 * Scope DataStore — `backgroundScope` из [runTest]: это гарантирует
 * отмену внутренних корутин DataStore при завершении теста
 * (без `UncompletedCoroutinesError`). Каждый тест создаёт свежий
 * инстанс через extension [newDataStore] — между тестами нет
 * разделяемого состояния.
 *
 * Тесты покрывают сценарии `appearance`:
 * - «dontShowArchiveDialog returns false on first launch» (default);
 * - «setDontShowArchiveDialog persists and Flow re-emits» (true и обратно).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppSettingsDataStoreTest {
    @TempDir
    lateinit var tempDir: Path

    /**
     * Создаёт [AppSettingsDataStore] поверх временного файла со scope,
     * привязанным к [backgroundScope] текущего `runTest`. Вызывается
     * внутри каждого теста, чтобы каждый получил свежий инстанс.
     */
    private fun TestScope.newDataStore(): AppSettingsDataStore =
        AppSettingsDataStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = {
                    tempDir.resolve("appearance_prefs.preferences_pb").toFile()
                }
            )
        )

    /**
     * Spec scenario «dontShowArchiveDialog returns false on first launch»:
     * без явной записи [AppSettingsDataStore.dontShowArchiveDialog] эмитит
     * default `false`.
     */
    @Test
    fun dontShowArchiveDialog_first_collect_returns_false() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When — первый сбор Flow без записи
            val value = dataStore.dontShowArchiveDialog.first()

            // Then
            assertFalse(value, "До первой записи dontShowArchiveDialog == false")
        }

    /**
     * Spec scenario «setDontShowArchiveDialog persists and Flow re-emits»
     * (true-ветка): после `setDontShowArchiveDialog(true)` Flow эмитит
     * `true`.
     */
    @Test
    fun setDontShowArchiveDialog_true_then_collect_returns_true() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When
            dataStore.setDontShowArchiveDialog(true)

            // Then
            assertTrue(
                dataStore.dontShowArchiveDialog.first(),
                "После setDontShowArchiveDialog(true) Flow эмитит true"
            )
        }

    /**
     * Spec scenario «setDontShowArchiveDialog persists and Flow re-emits»
     * (false-ветка после true): смена значения в обе стороны корректно
     * отражается в Flow.
     */
    @Test
    fun setDontShowArchiveDialog_false_after_true_returns_false() =
        runTest {
            // Given
            val dataStore = newDataStore()
            dataStore.setDontShowArchiveDialog(true)
            assertTrue(
                dataStore.dontShowArchiveDialog.first(),
                "После установки true Flow эмитит true"
            )

            // When
            dataStore.setDontShowArchiveDialog(false)

            // Then
            assertFalse(
                dataStore.dontShowArchiveDialog.first(),
                "После установки false Flow эмитит false"
            )
        }
}
