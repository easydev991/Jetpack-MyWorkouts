package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/*
 * DRAFT экрана настроек «Обзора» по макету `progress-drafts/iter4_v1.html`
 * (panel-settings): тогглы видимости вкладок «Статистика»/«Рекорды»/
 * «Активность». Верстка — по образцу EntrySettingsScreen: Scaffold с
 * TopAppBar («назад»), скроллящийся список строк, кнопка «Включить все»,
 * пока выключена хотя бы одна вкладка.
 *
 * Черновик в одном файле: экран и демо-состояния. При переносе в фичу:
 * - [OverviewSettingsScreenContent] → `OverviewSettingsScreen.kt`
 *   (+ UiState/VM, состояние в DataStore),
 * - тексты → strings.xml,
 * - запрет выключения последней включённой вкладки (логика макета:
 *   тоггл откатывается) — логика состояния, в VM по TDD (сначала тест).
 */

/**
 * Содержимое экрана настроек «Обзора»: TopAppBar с кнопкой «назад»,
 * подзаголовок, строки тогглов видимости вкладок [OverviewTab] и кнопка
 * «Включить все», пока выключена хотя бы одна вкладка — как в
 * EntrySettingsScreen. UI не владеет состоянием: запрет выключения
 * последней вкладки обеспечивает VM через [onTabToggle].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverviewSettingsScreenContent(
    enabledTabs: Set<OverviewTab>,
    onTabToggle: (OverviewTab, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // DRAFT: как в EntrySettingsScreen («Настроить экран»),
                    // при переносе — тот же строковый ресурс
                    Text(text = "Настроить экран")
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            // DRAFT: описание захардкожено, при переносе — ресурс
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
        ) {
            Text(
                // DRAFT: строка захардкожена, при переносе — строковый ресурс
                text = "Выберите разделы, которые показывать на экране обзора",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            OverviewTab.entries.forEach { tab ->
                TabToggleRow(
                    tab = tab,
                    enabled = tab in enabledTabs,
                    onToggle = { checked -> onTabToggle(tab, checked) }
                )
            }
            if (enabledTabs.size < OverviewTab.entries.size) {
                EnableAllButton(onEnableAll = onEnableAll)
            }
        }
    }
}

/** Строка тоггла одной вкладки: название слева, Switch справа. */
@Composable
private fun TabToggleRow(
    tab: OverviewTab,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tab.label,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

/** Кнопка «Включить все» — последний элемент скроллящегося списка. */
@Composable
private fun EnableAllButton(onEnableAll: () -> Unit) {
    TextButton(
        onClick = onEnableAll,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
    ) {
        // DRAFT: строка захардкожена, при переносе — строковый ресурс
        Text(
            text = "Включить все",
            textAlign = TextAlign.Center
        )
    }
}

// ===== Превью (конвенция: светлая + тёмная темы, showBackground, locale = "ru") =====
// Ключевые состояния: все вкладки включены и часть выключена (видна
// кнопка «Включить все»).

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewSettingsAllEnabledPreview() {
    OverviewSettingsPreview(enabledTabs = OverviewTab.entries.toSet())
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewSettingsPartiallyDisabledPreview() {
    OverviewSettingsPreview(enabledTabs = setOf(OverviewTab.STATS, OverviewTab.RECORDS))
}

@Composable
private fun OverviewSettingsPreview(enabledTabs: Set<OverviewTab>) {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            OverviewSettingsScreenContent(
                enabledTabs = enabledTabs,
                onTabToggle = { _, _ -> },
                onEnableAll = {},
                onBackClick = {}
            )
        }
    }
}
