package com.myworkouts.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.myworkouts.R

/**
 * Алерт подтверждения удаления записи: заголовок «Удалить запись?»,
 * текст с названием упражнения, кнопка подтверждения в `colorScheme.error`
 * (design Decision 5).
 *
 * @param itemName Название упражнения в записи (для текста алерта)
 * @param onConfirm Обработчик «Удалить» — VM вызывает `confirmDelete()`
 * @param onCancel Обработчик «Отмена» — VM вызывает `cancelDelete()`
 */
@Composable
internal fun EntryDeleteDialog(
    itemName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.entry_list_delete_dialog_title)) },
        text = {
            Text(stringResource(R.string.entry_list_delete_dialog_message, itemName))
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors =
                    androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
            ) {
                Text(stringResource(R.string.entry_list_menu_delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
