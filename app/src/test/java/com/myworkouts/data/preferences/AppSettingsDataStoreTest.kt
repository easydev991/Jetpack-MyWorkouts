package com.myworkouts.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.myworkouts.domain.model.EntryField
import com.myworkouts.domain.model.OverviewTab
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
     * [base] — готовый DataStore (для тестов с прямой записью мимо API).
     */
    private fun TestScope.newDataStore(base: DataStore<Preferences> = newPreferencesDataStore()): AppSettingsDataStore =
        AppSettingsDataStore(base)

    /**
     * Создаёт базовый [DataStore] поверх временного файла (для тестов,
     * где нужна прямая запись мусора в ключ мимо API хранилища).
     */
    private fun TestScope.newPreferencesDataStore(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                tempDir.resolve("appearance_prefs.preferences_pb").toFile()
            }
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

    // ---------- entry-settings-screen: видимость опциональных полей формы записи ----------

    /**
     * Spec scenario «Все переключатели включены по умолчанию»: без записи
     * ключей [AppSettingsDataStore.entryFieldVisibility] эмитит `true`
     * для всех [EntryField] (отсутствие ключа = поле отображается).
     */
    @Test
    fun entryFieldVisibility_first_collect_all_true() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When — первый сбор Flow без записи
            val visibility = dataStore.entryFieldVisibility.first()

            // Then
            assertEquals(
                EntryField.entries.associateWith { true },
                visibility,
                "До первой записи все опциональные поля видимы"
            )
        }

    /**
     * Spec scenario «Настройка восстанавливается после перезапуска»:
     * `setFieldVisible(SETS, false)` → Flow эмитит `SETS = false`,
     * остальные поля остаются `true` (persistence повторным чтением).
     */
    @Test
    fun setFieldVisible_sets_false_then_collect_returns_false_others_true() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When
            dataStore.setFieldVisible(EntryField.SETS, false)

            // Then
            val visibility = dataStore.entryFieldVisibility.first()
            assertEquals(
                false,
                visibility[EntryField.SETS],
                "После setFieldVisible(SETS, false) Flow эмитит false для SETS"
            )
            assertTrue(
                visibility.filterKeys { it != EntryField.SETS }.all { it.value },
                "Остальные поля остаются видимыми"
            )
        }

    /**
     * Ревью `enableAll` (entry-settings-screen): `setAllFieldsVisible`
     * включает все поля одной записью — после вызова карта все-true,
     * включая ранее скрытые поля.
     */
    @Test
    fun setAllFieldsVisible_after_hidden_fields_then_all_true() =
        runTest {
            // Given — два поля скрыты
            val dataStore = newDataStore()
            dataStore.setFieldVisible(EntryField.DATE, false)
            dataStore.setFieldVisible(EntryField.COMMENT, false)

            // When
            dataStore.setAllFieldsVisible()

            // Then
            assertEquals(
                EntryField.entries.associateWith { true },
                dataStore.entryFieldVisibility.first(),
                "После setAllFieldsVisible все опциональные поля видимы"
            )
        }

    // ---------- overview-screen: видимость вкладок и последняя вкладка ----------

    @Test
    fun overviewTabVisibility_first_collect_all_true() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When — первый сбор Flow без записи
            val visibility = dataStore.overviewTabVisibility.first()

            // Then — отсутствие ключа = вкладка видима (design D8)
            assertEquals(
                OverviewTab.entries.associateWith { true },
                visibility,
                "До первой записи все вкладки обзора видимы"
            )
        }

    @Test
    fun setOverviewTabVisible_records_false_then_others_stay_true() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When
            dataStore.setOverviewTabVisible(OverviewTab.RECORDS, false)

            // Then
            val visibility = dataStore.overviewTabVisibility.first()
            assertEquals(
                false,
                visibility[OverviewTab.RECORDS],
                "После setOverviewTabVisible(RECORDS, false) Flow эмитит false для RECORDS"
            )
            assertTrue(
                visibility.filterKeys { it != OverviewTab.RECORDS }.all { it.value },
                "Остальные вкладки остаются видимыми"
            )
        }

    @Test
    fun setAllOverviewTabsVisible_after_hidden_then_all_true() =
        runTest {
            // Given — две вкладки скрыты
            val dataStore = newDataStore()
            dataStore.setOverviewTabVisible(OverviewTab.RECORDS, false)
            dataStore.setOverviewTabVisible(OverviewTab.ACTIVITY, false)

            // When
            dataStore.setAllOverviewTabsVisible()

            // Then — один edit включает всё
            assertEquals(
                OverviewTab.entries.associateWith { true },
                dataStore.overviewTabVisibility.first(),
                "После setAllOverviewTabsVisible все вкладки видимы"
            )
        }

    @Test
    fun lastOverviewTab_first_collect_returns_stats() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When — первый сбор Flow без записи
            val tab = dataStore.lastOverviewTab.first()

            // Then — дефолт «Статистика» (спека «Сохранение выбранной вкладки»)
            assertEquals(OverviewTab.STATS, tab, "Без записи выбрана вкладка «Статистика»")
        }

    @Test
    fun setLastOverviewTab_records_then_collect_returns_records() =
        runTest {
            // Given
            val dataStore = newDataStore()

            // When
            dataStore.setLastOverviewTab(OverviewTab.RECORDS)

            // Then
            assertEquals(
                OverviewTab.RECORDS,
                dataStore.lastOverviewTab.first(),
                "После setLastOverviewTab(RECORDS) Flow эмитит RECORDS"
            )
        }

    @Test
    fun lastOverviewTab_garbage_value_falls_back_to_stats() =
        runTest {
            // Given — в файле мусорное значение (запись мимо API хранилища)
            val base = newPreferencesDataStore()
            val dataStore = newDataStore(base)
            base.edit { preferences ->
                preferences[stringPreferencesKey("last_overview_tab")] = "GARBAGE"
            }

            // When
            val tab = dataStore.lastOverviewTab.first()

            // Then — fallback на STATS, паттерн theme (design D8)
            assertEquals(OverviewTab.STATS, tab, "Мусорное значение — вкладка «Статистика»")
        }
}
