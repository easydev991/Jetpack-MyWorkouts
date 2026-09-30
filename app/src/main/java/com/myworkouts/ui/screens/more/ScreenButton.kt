package com.myworkouts.ui.screens.more

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.dimensionResource
import com.myworkouts.R

/**
 * Общая кнопка экрана «Ещё»: [FilledTonalButton] с единым горизонтальным
 * `contentPadding` — единственное место, где он задаётся (review:
 * седьмая копия блока на экране). Текст передаётся готовой строкой.
 */
@Composable
internal fun ScreenButton(
    text: String,
    onClick: () -> Unit
) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(R.dimen.button_horizontal_padding)
            )
    ) {
        Text(text)
    }
}
