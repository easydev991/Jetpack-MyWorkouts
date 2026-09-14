package com.myworkouts.ui.screens.appdata

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.ui.state.AppDataResult
import com.myworkouts.ui.viewmodel.AppDataViewModel
import java.time.LocalDate

/**
 * Экран «Данные приложения» (app-data-screen, design Decision 7):
 * создание резервной копии, восстановление слиянием и удаление всех
 * данных.
 *
 * @param viewModel ViewModel с состоянием и операциями
 * @param onBackClick обработчик стрелки «Назад» в TopAppBar
 */
@Composable
fun AppDataScreen(
    viewModel: AppDataViewModel,
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Toast одноразовый: показывается по факту завершения операции и
    // сразу сбрасывается — повтор на recomposition исключён.
    LaunchedEffect(uiState.result) {
        val result = uiState.result ?: return@LaunchedEffect
        val (textResId, duration) =
            when (result) {
                AppDataResult.EXPORTED -> R.string.backup_created to Toast.LENGTH_SHORT
                AppDataResult.RESTORED -> R.string.backup_restored to Toast.LENGTH_SHORT
                AppDataResult.DELETED -> R.string.all_data_deleted to Toast.LENGTH_SHORT
                AppDataResult.ERROR_EXPORT -> R.string.error_export_failed to Toast.LENGTH_LONG
                AppDataResult.ERROR_INVALID_FILE -> R.string.error_invalid_backup to Toast.LENGTH_LONG
                AppDataResult.ERROR_IMPORT -> R.string.error_import_failed to Toast.LENGTH_LONG
                AppDataResult.ERROR_DELETE -> R.string.error_delete_failed to Toast.LENGTH_LONG
            }
        Toast.makeText(context, textResId, duration).show()
        viewModel.clearResult()
    }

    AppDataScreenContent(
        state =
            AppDataScreenState(
                hasData = uiState.hasData,
                isBusy = uiState.isBusy,
                onExportRequest = viewModel::exportBackup,
                onRestoreRequest = viewModel::restoreFromBackup,
                onDeleteAll = viewModel::deleteAllData,
                onBackClick = onBackClick
            )
    )
}

/** Test/preview-only состояние [AppDataScreenContent] без ViewModel. */
internal data class AppDataScreenState(
    val hasData: Boolean,
    val isBusy: Boolean,
    val onExportRequest: (Uri) -> Unit,
    val onRestoreRequest: (Uri) -> Unit,
    val onDeleteAll: () -> Unit,
    val onBackClick: () -> Unit
)

/**
 * Контент экрана «Данные приложения».
 *
 * Используется для UI-тестов и preview в изоляции без ViewModel. SAF-
 * лаунчеры живут здесь: отмена пикера (`uri == null`) — no-op без
 * запуска операции (design Risks). Экспорт и удаление скрыты при
 * `!hasData` (нет ни записей, ни пользовательских упражнений);
 * `isBusy` блокирует все три кнопки.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppDataScreenContent(state: AppDataScreenState) {
    var isDeleteDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_data)) },
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
        AppDataButtons(
            state = state,
            onDeleteClick = { isDeleteDialogVisible = true },
            modifier = Modifier.padding(paddingValues)
        )
    }

    if (isDeleteDialogVisible) {
        DeleteAllDialog(
            onConfirm = {
                isDeleteDialogVisible = false
                state.onDeleteAll()
            },
            onDismiss = { isDeleteDialogVisible = false }
        )
    }
}

/**
 * Кнопки операций с SAF-лаунчерами: восстановление доступно всегда,
 * экспорт и удаление скрыты без данных пользователя, `isBusy` блокирует
 * все три (спека app-data-management «Результаты операций»). Отмена
 * пикера (`uri == null`) — no-op (design Risks).
 */
@Composable
private fun AppDataButtons(
    state: AppDataScreenState,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exportLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            uri?.let(state.onExportRequest)
        }
    val restoreLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri?.let(state.onRestoreRequest)
        }
    // Единый стиль кнопок «Ещё»: ширина по тексту,
    // горизонтальный паддинг из ресурса
    val buttonContentPadding =
        PaddingValues(horizontal = dimensionResource(R.dimen.button_horizontal_padding))

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.spacing_xxsmall)),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.hasData) {
            FilledTonalButton(
                onClick = {
                    exportLauncher.launch("myworkouts-backup-${LocalDate.now()}.json")
                },
                enabled = !state.isBusy,
                contentPadding = buttonContentPadding
            ) {
                Text(stringResource(R.string.create_backup))
            }
        }

        FilledTonalButton(
            onClick = { restoreLauncher.launch(arrayOf("*/*")) },
            enabled = !state.isBusy,
            contentPadding = buttonContentPadding
        ) {
            Text(stringResource(R.string.restore_from_backup))
        }

        if (state.hasData) {
            Button(
                onClick = onDeleteClick,
                enabled = !state.isBusy,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                contentPadding = buttonContentPadding
            ) {
                Text(stringResource(R.string.delete_all_data))
            }
        }
    }
}

/** Диалог подтверждения массового удаления данных (спека «Удаление»). */
@Composable
private fun DeleteAllDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.delete_all_dialog_text)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
            ) {
                Text(stringResource(R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
