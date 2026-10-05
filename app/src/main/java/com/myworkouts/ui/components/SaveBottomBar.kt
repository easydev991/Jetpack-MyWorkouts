package com.myworkouts.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.myworkouts.R

/**
 * Нижняя панель экранов создания/редактирования (общая для
 * `ExerciseCreateScreen` и `CategoryCreateScreen`, реюз вместо
 * близнецов; паттерн `EntryCreateScreen.SaveBottomBar`): единственная
 * кнопка «Сохранить» поверх клавиатуры (`navigationBarsPadding` +
 * `imePadding` — максимум инсетов, не сумма) и блок ошибки под ней.
 *
 * @param canSave Активность кнопки «Сохранить» (`uiState.canSave`)
 * @param onSave Тап по кнопке
 * @param showError Показывать блок ошибки под кнопкой (`isSaveError`)
 * @param errorRes Ресурс текста ошибки экрана
 * @param hintRes Ресурс подсказки между кнопкой и ошибкой (например,
 *   «упражнение не найдено» — `ExerciseCreateScreen`); `null` — подсказки нет
 */
@Composable
internal fun SaveBottomBar(
    canSave: Boolean,
    onSave: () -> Unit,
    showError: Boolean,
    @StringRes errorRes: Int,
    @StringRes hintRes: Int? = null
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = canSave
        ) {
            Text(stringResource(R.string.entry_save))
        }

        if (hintRes != null) {
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (showError) {
            Text(
                text = stringResource(errorRes),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
