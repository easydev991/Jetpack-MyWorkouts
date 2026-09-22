package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/*
 * DRAFT общего экрана «Обзор» по макету `progress-drafts/iter4_v1.html`
 * (title-row + `.tabs`): TopAppBar («Обзор» + шестерёнка настроек), под
 * ним SecondaryTabRow с тремя вкладками — «Статистика», «Рекорды»,
 * «Активность» — и содержимое выбранной вкладки.
 *
 * Содержимое вкладок — черновики из соседних файлов, демо-данные
 * переиспользуются оттуда же ([demoStatsState], [demoRecordsState],
 * [demoActivityState]). При переносе в фичу: выбранная вкладка —
 * состояние экрана-родителя (VM), демо-данные заменяются UiState от
 * ViewModel, тексты вкладок — строковые ресурсы.
 */

/** Вкладка экрана «Обзор». */
internal enum class OverviewTab(
    val label: String
) {
    STATS("Статистика"),
    RECORDS("Рекорды"),
    ACTIVITY("Активность")
}

/**
 * Содержимое экрана «Обзор»: TopAppBar с настройками, под ним
 * [SecondaryTabRow] с видимыми вкладками и содержимое выбранной вкладки
 * (черновики «Статистика»/«Рекорды»/«Активность»). Состояния вкладок —
 * параметры с демо-значениями по умолчанию: превью работают без
 * аргументов, интерактивный раннер [OverviewScreen] передаёт свои.
 */
@OptIn(ExperimentalMaterial3Api::class)
// DRAFT: длинный список параметров исчезнет при переносе состояний в VM
@Suppress("LongParameterList")
@Composable
internal fun OverviewScreenContent(
    selectedTab: OverviewTab,
    onTabChange: (OverviewTab) -> Unit,
    visibleTabs: List<OverviewTab> = OverviewTab.entries.toList(),
    onSettingsClick: () -> Unit = {},
    statsState: StatsUiState = demoStatsState(period = StatsPeriod.WEEK),
    onStatsPeriodChange: (StatsPeriod) -> Unit = {},
    onCategoryClick: (StatsCategory?) -> Unit = {},
    recordsState: RecordsUiState = demoRecordsState(mode = RecordsMode.PER_SET),
    onRecordsModeChange: (RecordsMode) -> Unit = {},
    activityState: ActivityUiState = demoActivityState(period = ActivityPeriod.MONTH),
    onActivityPeriodChange: (ActivityPeriod) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        OverviewTopBar(
            visibleTabs = visibleTabs,
            onSettingsClick = onSettingsClick
        )
        // Как в макете: меньше двух видимых вкладок — таб-бар скрыт
        if (visibleTabs.size >= 2) {
            SecondaryTabRow(selectedTabIndex = visibleTabs.indexOf(selectedTab)) {
                visibleTabs.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onTabChange(tab) },
                        text = { Text(text = tab.label) }
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .weight(1f)
        ) {
            when (selectedTab) {
                OverviewTab.STATS ->
                    StatsScreenContent(
                        state = statsState,
                        onPeriodChange = onStatsPeriodChange,
                        onCategoryClick = onCategoryClick
                    )
                OverviewTab.RECORDS ->
                    RecordsScreenContent(
                        state = recordsState,
                        onModeChange = onRecordsModeChange
                    )
                OverviewTab.ACTIVITY ->
                    ActivityScreenContent(
                        state = activityState,
                        onPeriodChange = onActivityPeriodChange
                    )
            }
        }
    }
}

/**
 * Шапка экрана: «Обзор» (+ подзаголовок — название, если видима одна
 * вкладка, как title-sub макета) и шестерёнка настроек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewTopBar(
    visibleTabs: List<OverviewTab>,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                // DRAFT: строки захардкожены, при переносе — ресурсы
                Text(text = "Обзор")
                if (visibleTabs.size == 1) {
                    Text(
                        text = visibleTabs.first().label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                // DRAFT: описание захардкожено, при переносе — ресурс
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Настройки"
                )
            }
        }
    )
}

// ===== Превью (конвенция: светлая + тёмная темы, showBackground, locale = "ru") =====
// По одному превью на каждую вкладку.

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewScreenStatsPreview() {
    OverviewTabPreview(selectedTab = OverviewTab.STATS)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewScreenRecordsPreview() {
    OverviewTabPreview(selectedTab = OverviewTab.RECORDS)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewScreenActivityPreview() {
    OverviewTabPreview(selectedTab = OverviewTab.ACTIVITY)
}

@Composable
private fun OverviewTabPreview(selectedTab: OverviewTab) {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            OverviewScreenContent(
                selectedTab = selectedTab,
                onTabChange = {}
            )
        }
    }
}
