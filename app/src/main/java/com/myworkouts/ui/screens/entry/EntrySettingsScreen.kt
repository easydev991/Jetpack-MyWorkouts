package com.myworkouts.ui.screens.entry

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.EntryField
import com.myworkouts.ui.components.SettingToggleRow
import com.myworkouts.ui.state.EntrySettingsUiState
import com.myworkouts.ui.viewmodel.EntrySettingsViewModel

/**
 * Экран «Настроить экран» (entry-settings-screen): тогглы видимости
 * опциональных полей формы записи, кнопка «Включить все». VM-обёртка —
 * подписка на состояние и колбэки, вся верстка — в
 * [EntrySettingsScreenContent].
 *
 * @param viewModel ViewModel экрана
 * @param onBackClick Навигация назад (закрытие экрана)
 */
@Composable
fun EntrySettingsScreen(
    viewModel: EntrySettingsViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    EntrySettingsScreenContent(
        state =
            EntrySettingsScreenState(
                visibility = uiState.visibility,
                hasAnyDisabled = uiState.hasAnyDisabled,
                onToggleField = viewModel::setFieldVisible,
                onEnableAll = viewModel::enableAll,
                onBackClick = onBackClick
            )
    )
}

/**
 * Test/Preview-state для [EntrySettingsScreenContent]: данные
 * [EntrySettingsUiState] плюс колбэки (паттерн [EntryCreateScreenState]).
 * UI-тесты и превью передают готовый state без ViewModel.
 */
internal data class EntrySettingsScreenState(
    val visibility: Map<EntryField, Boolean>,
    val hasAnyDisabled: Boolean,
    val onToggleField: (EntryField, Boolean) -> Unit,
    val onEnableAll: () -> Unit,
    val onBackClick: () -> Unit
)

/**
 * Контент экрана настройки видимости полей: TopAppBar с кнопкой «назад»,
 * подзаголовок, скроллящийся список тогглов по [EntryField.entries],
 * кнопка «Включить все» при [EntrySettingsScreenState.hasAnyDisabled] —
 * последний элемент списка (не `bottomBar`: в ландшафте закреплённая
 * кнопка отрисовывалась поверх прокручиваемого списка, ревью 9.2).
 *
 * Используется для UI-тестов и превью в изоляции без ViewModel —
 * тесты передают готовый [EntrySettingsScreenState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EntrySettingsScreenContent(state: EntrySettingsScreenState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.entry_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = state.onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.entry_settings_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            EntryField.entries.forEach { field ->
                SettingToggleRow(
                    labelResId = field.labelResId,
                    checked = state.visibility[field] ?: true,
                    onToggle = { state.onToggleField(field, it) },
                    hintResId = field.hintResId
                )
            }

            if (state.hasAnyDisabled) {
                EnableAllButton(onEnableAll = state.onEnableAll)
            }
        }
    }
}

/**
 * Кнопка «Включить все» — последний элемент скроллящегося списка.
 * Инсеты системной панели уже учтены в `paddingValues` контента
 * (bottomBar нет), собственных insets-модификаторов не нужно.
 */
@Composable
private fun EnableAllButton(onEnableAll: () -> Unit) {
    TextButton(
        onClick = onEnableAll,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.entry_visibility_enable_all),
            textAlign = TextAlign.Center
        )
    }
}
