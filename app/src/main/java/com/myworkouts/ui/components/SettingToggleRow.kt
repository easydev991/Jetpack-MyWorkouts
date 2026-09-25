package com.myworkouts.ui.components

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.R
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Строка настройки-переключателя (общая для экранов настроек): название
 * (и подсказка, если задана) слева, Switch справа. Клиабельна строка
 * целиком (`toggleable` с `Role.Switch`): TalkBack анонсирует название
 * с подсказкой и состоянием, сам Switch — только индикатор.
 *
 * @param labelResId Ресурс названия строки
 * @param checked Включён ли переключатель
 * @param onToggle Переключение (тап по любому месту строки)
 * @param modifier Модификатор строки
 * @param hintResId Ресурс подсказки под названием (`null` — без подсказки)
 */
@Composable
internal fun SettingToggleRow(
    @StringRes labelResId: Int,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    @StringRes hintResId: Int? = null
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onToggle
                ).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(labelResId))
            hintResId?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

// ===== Превью (обе темы, showBackground, locale = "ru") =====
// Ключевые состояния: с подсказкой и без неё.

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
private fun SettingToggleRowWithHintPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            SettingToggleRow(
                labelResId = R.string.entry_visibility_sets_label,
                checked = true,
                onToggle = {},
                hintResId = R.string.entry_visibility_sets_hint
            )
        }
    }
}

@Preview(showBackground = true, locale = "ru")
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true, locale = "ru")
@Composable
private fun SettingToggleRowWithoutHintPreview() {
    JetpackMyWorkoutsTheme {
        Surface {
            SettingToggleRow(
                labelResId = R.string.entry_visibility_weight_label,
                checked = false,
                onToggle = {}
            )
        }
    }
}
