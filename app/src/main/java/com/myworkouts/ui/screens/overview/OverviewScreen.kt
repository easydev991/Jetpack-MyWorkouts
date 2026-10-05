package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.domain.usecase.ActivityPeriod
import com.myworkouts.domain.usecase.RecordsMode
import com.myworkouts.domain.usecase.StatsPeriod
import com.myworkouts.ui.state.ActivityUiState
import com.myworkouts.ui.state.OverviewUiState
import com.myworkouts.ui.state.RecordsUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.OverviewViewModel

/**
 * Колбэки пользовательских действий экрана «Обзор» — мост от вёрстки
 * к методам [OverviewViewModel]. Отдельный класс (образец —
 * `MainScreenState`): [OverviewScreenBody] остаётся превью-способным без
 * ViewModel, а список параметров не разрастается.
 */
internal data class OverviewScreenActions(
    val onTabSelect: (OverviewTab) -> Unit,
    val onStatsPeriodChange: (StatsPeriod) -> Unit,
    val onStatsCategorySelect: (String?) -> Unit,
    val onRecordsModeChange: (RecordsMode) -> Unit,
    val onActivityPeriodChange: (ActivityPeriod) -> Unit
)

/**
 * Экран «Обзор» (overview-screen): статистика, рекорды и активность.
 *
 * VM-обёртка: подписка на [OverviewViewModel.uiState]; вёрстка —
 * [OverviewScreenBody]. Переход на [OverviewSettingsScreen] — через
 * [onOpenSettings], возврат — системный Back
 * (non-tab-screen-navigation).
 *
 * @param viewModel ViewModel экрана
 * @param onOpenSettings Переход на экран настроек вкладок
 * @param onBack Возврат на предыдущий экран (системный Back и кнопка в шапке)
 * @param modifier Модификатор корневого контейнера
 */
@Composable
fun OverviewScreen(
    viewModel: OverviewViewModel,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    OverviewScreenBody(
        state = state,
        actions =
            OverviewScreenActions(
                onTabSelect = viewModel::selectTab,
                onStatsPeriodChange = viewModel::selectStatsPeriod,
                onStatsCategorySelect = viewModel::selectStatsCategory,
                onRecordsModeChange = viewModel::selectRecordsMode,
                onActivityPeriodChange = viewModel::selectActivityPeriod
            ),
        onSettingsClick = onOpenSettings,
        onBackClick = onBack,
        modifier = modifier
    )
}

/**
 * Вёрстка экрана «Обзор» по макету `progress-drafts/iter4_v1.html`
 * (title-row + `.tabs`): TopAppBar («Обзор» + подзаголовок при одной
 * вкладке + шестерёнка настроек), под ним [SecondaryTabRow] с видимыми
 * вкладками и содержимое выбранной вкладки.
 *
 * @param state Состояние экрана ([OverviewUiState])
 * @param actions Колбэки действий ([OverviewScreenActions])
 * @param onSettingsClick Тап по кнопке настроек
 * @param onBackClick Возврат на предыдущий экран (шапка)
 * @param modifier Модификатор корневого контейнера
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverviewScreenBody(
    state: OverviewUiState,
    actions: OverviewScreenActions,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        OverviewTopBar(
            visibleTabs = state.visibleTabs,
            onSettingsClick = onSettingsClick,
            onBackClick = onBackClick
        )
        // Системные панели — ответственность экрана (RootScreen обнуляет
        // contentWindowInsets, паттерн MainScreen): статус-бар паддит сам
        // TopAppBar, горизонталь (cutout в ландшафте) и нижнюю навигационную
        // панель — контейнер контента ниже
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    )
        ) {
            // Как в макете: меньше двух видимых вкладок — таб-бар скрыт
            if (state.visibleTabs.size >= 2) {
                OverviewTabs(
                    state = state,
                    onTabSelect = actions.onTabSelect
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .weight(1f)
            ) {
                when (state.selectedTab) {
                    OverviewTab.STATS ->
                        StatsScreen(
                            state = state.stats,
                            onPeriodChange = actions.onStatsPeriodChange,
                            onCategoryClick = actions.onStatsCategorySelect
                        )
                    OverviewTab.RECORDS ->
                        RecordsScreen(
                            state = state.records,
                            onModeChange = actions.onRecordsModeChange
                        )
                    OverviewTab.ACTIVITY ->
                        ActivityScreen(
                            state = state.activity,
                            onPeriodChange = actions.onActivityPeriodChange
                        )
                }
            }
        }
    }
}

/**
 * Шапка экрана: стрелка «Назад», «Обзор» (+ подзаголовок — название
 * единственной видимой вкладки, как title-sub макета) и шестерёнка настроек.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewTopBar(
    visibleTabs: List<OverviewTab>,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back)
                )
            }
        },
        title = {
            Column {
                Text(text = stringResource(R.string.overview_title))
                if (visibleTabs.size == 1) {
                    Text(
                        text = stringResource(visibleTabs.first().labelResId),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.a11y_overview_settings)
                )
            }
        }
    )
}

/**
 * Вкладки обзора: [SecondaryTabRow] по видимым вкладкам; при одной
 * видимой не вызывается (см. [OverviewScreenBody]).
 */
@Composable
private fun OverviewTabs(
    state: OverviewUiState,
    onTabSelect: (OverviewTab) -> Unit,
    modifier: Modifier = Modifier
) {
    SecondaryTabRow(
        selectedTabIndex = state.visibleTabs.indexOf(state.selectedTab),
        modifier = modifier
    ) {
        state.visibleTabs.forEach { tab ->
            Tab(
                selected = tab == state.selectedTab,
                onClick = { onTabSelect(tab) },
                text = { Text(text = stringResource(id = tab.labelResId)) }
            )
        }
    }
}

// ===== Превью (обе темы, showBackground, locale = "ru") — по одной на вкладку =====

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewStatsTabPreview() {
    OverviewTabPreview(selectedTab = OverviewTab.STATS)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewRecordsTabPreview() {
    OverviewTabPreview(selectedTab = OverviewTab.RECORDS)
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewActivityTabPreview() {
    OverviewTabPreview(selectedTab = OverviewTab.ACTIVITY)
}

/** Единственная видимая вкладка: подзаголовок в шапке, таб-бар скрыт. */
@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun OverviewSingleTabPreview() {
    OverviewTabPreview(
        selectedTab = OverviewTab.STATS,
        visibleTabs = listOf(OverviewTab.STATS)
    )
}

/** Демо-состояние «Активности» для каркасного превью. */
private val demoActivityUiState =
    ActivityUiState(
        streakDays = 14,
        recordStreakDays = 21,
        heatmap = demoHeatmap(ActivityPeriod.MONTH)
    )

@Composable
private fun OverviewTabPreview(
    selectedTab: OverviewTab,
    visibleTabs: List<OverviewTab> = OverviewTab.entries.toList()
) {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            OverviewScreenBody(
                state =
                    OverviewUiState(
                        selectedTab = selectedTab,
                        visibleTabs = visibleTabs,
                        stats = demoStatsState(period = StatsPeriod.WEEK),
                        records = RecordsUiState(records = demoRecords),
                        activity = demoActivityUiState
                    ),
                actions =
                    OverviewScreenActions(
                        onTabSelect = {},
                        onStatsPeriodChange = {},
                        onStatsCategorySelect = {},
                        onRecordsModeChange = {},
                        onActivityPeriodChange = {}
                    ),
                onSettingsClick = {},
                onBackClick = {}
            )
        }
    }
}
