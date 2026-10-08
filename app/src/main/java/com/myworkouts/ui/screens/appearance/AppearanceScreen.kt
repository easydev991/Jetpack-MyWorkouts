package com.myworkouts.ui.screens.appearance

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.domain.model.AppTheme
import com.myworkouts.ui.viewmodel.AppearanceViewModel

/**
 * Экран «Оформление приложения»: выбор темы и переключатель динамических цветов.
 *
 * @param viewModel ViewModel с состоянием настроек и методами обновления
 * @param onBackClick Обработчик нажатия на кнопку «Назад» в TopAppBar
 *
 * @implNote ThemeRadioRow/DynamicColorsSection inline (без
 * common/RadioButton.kt и common/DynamicColors.kt — YAGNI для одного
 * места использования) + internal State + Content-wrapper для
 * детерминированных тестов (assertDoesNotExist не зависит от SDK_INT).
 * Если радиокнопка/dynamicColors понадобятся во втором экране — вынести
 * в common/.
 */
@Composable
fun AppearanceScreen(
    viewModel: AppearanceViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    // Dynamic-цвета доступны только на Android 12+. Проверка через
    // remember: SDK_INT не меняется за жизнь процесса, пересчёт не нужен.
    // @Composable-scope (нельзя вычислить в plain-функции или companion object
    // — иначе упадёт «@Composable invocations can only happen from the
    // context of a @Composable function»).
    val isDynamicColorsAvailable = remember { Build.VERSION.SDK_INT >= Build.VERSION_CODES.S }

    AppearanceScreenContent(
        state =
            AppearanceScreenState(
                theme = uiState.theme,
                useDynamicColors = uiState.useDynamicColors,
                onThemeChange = { viewModel.updateTheme(it) },
                onDynamicColorsChange = { viewModel.updateDynamicColors(it) },
                isDynamicColorsAvailable = isDynamicColorsAvailable,
                onBackClick = onBackClick
            )
    )
}

/** Test-only state для AppearanceScreenContent. */
internal data class AppearanceScreenState(
    val theme: AppTheme,
    val useDynamicColors: Boolean,
    val onThemeChange: (AppTheme) -> Unit,
    val onDynamicColorsChange: (Boolean) -> Unit,
    val isDynamicColorsAvailable: Boolean,
    val onBackClick: () -> Unit
)

/**
 * Контент экрана «Оформление приложения».
 *
 * Используется для UI-тестов в изоляции без ViewModel — тесты передают
 * готовый [AppearanceScreenState] и проверяют отображение/колбэки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppearanceScreenContent(state: AppearanceScreenState) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.appearance_title)) },
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ThemeSection(
                theme = state.theme,
                onThemeChange = state.onThemeChange
            )

            HorizontalDivider()

            if (state.isDynamicColorsAvailable) {
                DynamicColorsSection(
                    useDynamicColors = state.useDynamicColors,
                    onDynamicColorsChange = state.onDynamicColorsChange
                )
            }
        }
    }
}

/**
 * Секция выбора темы приложения: заголовок + три радио-кнопки (светлая/тёмная/системная).
 */
@Composable
private fun ThemeSection(
    theme: AppTheme,
    onThemeChange: (AppTheme) -> Unit
) {
    Text(
        text = stringResource(R.string.app_theme),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth()
    )

    Column(modifier = Modifier.selectableGroup()) {
        ThemeRadioRow(
            label = stringResource(R.string.light),
            selected = theme == AppTheme.LIGHT,
            enabled = theme != AppTheme.LIGHT,
            onClick = { onThemeChange(AppTheme.LIGHT) }
        )
        ThemeRadioRow(
            label = stringResource(R.string.dark),
            selected = theme == AppTheme.DARK,
            enabled = theme != AppTheme.DARK,
            onClick = { onThemeChange(AppTheme.DARK) }
        )
        ThemeRadioRow(
            label = stringResource(R.string.system),
            selected = theme == AppTheme.SYSTEM,
            enabled = theme != AppTheme.SYSTEM,
            onClick = { onThemeChange(AppTheme.SYSTEM) }
        )
    }
}

/**
 * Одна строка радио-кнопки в секции выбора темы: `RadioButton` + label.
 *
 * `Modifier.selectable` ставит `Selected` semantic на родительский Row —
 * его ловят тесты через `hasAnyChild(hasText(label))` в unmerged-дереве.
 * `onClick = null` на вложенном RadioButton рекомендован для accessibility
 * (клик обрабатывает selectable на родителе).
 */
@Composable
private fun ThemeRadioRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier =
            Modifier
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    enabled = enabled,
                    role = Role.RadioButton
                ).fillMaxWidth()
                .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp),
            textAlign = TextAlign.Start
        )
    }
}

/**
 * Секция переключателя динамических цветов: заголовок, Switch и подсказка.
 *
 * Вызывается только при `state.isDynamicColorsAvailable` (см. [AppearanceScreenContent]),
 * поэтому внутри НЕ вычисляем доступность динамических цветов —
 * это работа Content-уровня, не секции.
 */
@Composable
private fun DynamicColorsSection(
    useDynamicColors: Boolean,
    onDynamicColorsChange: (Boolean) -> Unit
) {
    Text(
        text = stringResource(R.string.dynamic_colors),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.fillMaxWidth()
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.use_dynamic_colors),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = useDynamicColors,
                onCheckedChange = onDynamicColorsChange
            )
        }
        Text(
            text = stringResource(R.string.dynamic_colors_hint),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
