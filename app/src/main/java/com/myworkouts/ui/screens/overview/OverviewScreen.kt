package com.myworkouts.ui.screens.overview

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/*
 * DRAFT-раннер экрана «Обзор»: собирает вкладки-черновики (*Preview.kt)
 * в интерактивный экран на демо-данных. Всё состояние — remember в
 * композиции, без ViewModel. Запуск на устройстве —
 * OverviewDemoActivity (adb shell am start). При переносе в фичу:
 * состояние → ViewModel (openspec-задача), демо-данные → реальные
 * UiState, guard последней вкладки — по TDD.
 */

/**
 * Интерактивный черновик «Обзора» на демо-данных: переключение вкладок,
 * пикеры периодов, drill-down статистики, тогглы настроек (последнюю
 * включённую вкладку выключить нельзя — логика макета), поворот экрана —
 * ландшафтная раскладка статистики.
 */
@Composable
internal fun OverviewScreen(modifier: Modifier = Modifier) {
    var showSettings by remember { mutableStateOf(false) }
    var enabledTabs by remember { mutableStateOf(OverviewTab.entries.toSet()) }
    var selectedTab by remember { mutableStateOf(OverviewTab.STATS) }
    var statsPeriod by remember { mutableStateOf(StatsPeriod.WEEK) }
    var statsCategory by remember { mutableStateOf<StatsCategory?>(null) }
    var recordsMode by remember { mutableStateOf(RecordsMode.PER_SET) }
    var activityPeriod by remember { mutableStateOf(ActivityPeriod.MONTH) }

    BackHandler(enabled = showSettings) { showSettings = false }
    if (showSettings) {
        OverviewSettingsScreenContent(
            enabledTabs = enabledTabs,
            // Логика макета: последнюю включённую вкладку выключить нельзя —
            // тоггл игнорируется
            onTabToggle = { tab, checked ->
                if (checked || enabledTabs.size > 1) {
                    enabledTabs =
                        if (checked) {
                            enabledTabs + tab
                        } else {
                            enabledTabs - tab
                        }
                    if (selectedTab !in enabledTabs) {
                        selectedTab = enabledTabs.first()
                    }
                }
            },
            onEnableAll = { enabledTabs = OverviewTab.entries.toSet() },
            onBackClick = { showSettings = false },
            modifier = modifier
        )
    } else {
        OverviewScreenContent(
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            visibleTabs = OverviewTab.entries.filter { it in enabledTabs },
            onSettingsClick = { showSettings = true },
            statsState = demoStatsState(period = statsPeriod, selected = statsCategory),
            // Как в макете: смена периода сбрасывает drill-down
            onStatsPeriodChange = { period ->
                statsPeriod = period
                statsCategory = null
            },
            onCategoryClick = { statsCategory = it },
            recordsState = demoRecordsState(mode = recordsMode),
            onRecordsModeChange = { recordsMode = it },
            activityState = demoActivityState(period = activityPeriod),
            onActivityPeriodChange = { activityPeriod = it },
            modifier = modifier
        )
    }
}
