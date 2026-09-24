package com.myworkouts.ui.screens.overview

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.OverviewTab
import com.myworkouts.ui.state.OverviewSettingsUiState
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme
import com.myworkouts.ui.viewmodel.OverviewSettingsViewModel

/**
 * Экран настроек обзора: видимость вкладок (overview-screen). Образец —
 * `EntrySettingsScreen`: Scaffold с TopAppBar («назад»), скроллящийся
 * список строк, кнопка «Включить все», пока выключена хотя бы одна
 * вкладка.
 *
 * VM-обёртка: подписка на [OverviewSettingsViewModel.uiState]; guard
 * последней включённой вкладки — внутри VM.
 *
 * @param viewModel ViewModel настроек
 * @param onBackClick Обработчик нажатия на кнопку «Назад» в TopAppBar
 * @param modifier Модификатор корневого контейнера
 */
@Composable
fun OverviewSettingsScreen(
    viewModel: OverviewSettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    OverviewSettingsScreenBody(
        state = state,
        onTabToggle = viewModel::setTabVisible,
        onEnableAll = viewModel::enableAll,
        onBackClick = onBackClick,
        modifier = modifier
    )
}

/**
 * Вёрстка настроек обзора: UI не владеет состоянием — запрет выключения
 * последней вкладки обеспечивает VM (тоггл игнорируется, спека).
 *
 * @param state Состояние ([OverviewSettingsUiState])
 * @param onTabToggle Переключение видимости вкладки
 * @param onEnableAll Включить все вкладки
 * @param onBackClick Нажатие «назад»
 * @param modifier Модификатор корневого контейнера
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverviewSettingsScreenBody(
    state: OverviewSettingsUiState,
    onTabToggle: (OverviewTab, Boolean) -> Unit,
    onEnableAll: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.entry_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.overview_settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            OverviewTab.entries.forEach { tab ->
                TabToggleRow(
                    tab = tab,
                    enabled = state.visibility[tab] ?: true,
                    onToggle = { checked -> onTabToggle(tab, checked) }
                )
            }
            if (state.hasAnyDisabled) {
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
    // toggleable на строке целиком: TalkBack анонсирует название
    // вкладки вместе с состоянием, сам Switch — только индикатор
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .toggleable(
                    value = enabled,
                    role = Role.Switch,
                    onValueChange = onToggle
                ).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(id = tab.labelResId),
            modifier = Modifier.weight(1f)
        )
        Switch(checked = enabled, onCheckedChange = null)
    }
}

/**
 * Кнопка «Включить все» (общий ресурс с EntrySettings) — последний
 * элемент скроллящегося списка.
 */
@Composable
private fun EnableAllButton(onEnableAll: () -> Unit) {
    TextButton(
        onClick = onEnableAll,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.entry_visibility_enable_all),
            textAlign = TextAlign.Center
        )
    }
}

// ===== Превью (обе темы, showBackground, locale = "ru") =====
// Ключевые состояния: все вкладки включены и часть выключена (видна
// кнопка «Включить все»).

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun SettingsAllEnabledPreview() {
    OverviewSettingsPreview(state = OverviewSettingsUiState())
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
internal fun SettingsPartiallyDisabledPreview() {
    OverviewSettingsPreview(
        state =
            OverviewSettingsUiState(
                visibility =
                    OverviewTab.entries.associateWith { it != OverviewTab.ACTIVITY }
            )
    )
}

@Composable
private fun OverviewSettingsPreview(state: OverviewSettingsUiState) {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            OverviewSettingsScreenBody(
                state = state,
                onTabToggle = { _, _ -> },
                onEnableAll = {},
                onBackClick = {}
            )
        }
    }
}
