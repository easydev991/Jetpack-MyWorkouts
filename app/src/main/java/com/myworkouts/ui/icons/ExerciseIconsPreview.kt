package com.myworkouts.ui.icons

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Одна строка каталога: иконка-вариант по текущей теме (без tint, как
 * будет отображаться в списках) + имя светлого drawable-ресурса.
 */
@Composable
private fun ExerciseIconRow(
    ref: ExerciseIcons.Ref,
    modifier: Modifier = Modifier
) {
    val resId = if (isSystemInDarkTheme()) ref.dark else ref.light
    val entryName = LocalResources.current.getResourceEntryName(ref.light)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = entryName,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Каталог всех иконок упражнений списком: в каждом превью отображается
 * вариант, соответствующий теме превью.
 */
@Composable
private fun ExerciseIconsCatalog(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "Иконки упражнений",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Вариант по текущей теме (без tint — так будет в списках)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider()
        ExerciseIcons.byNumber.values.forEach { ref ->
            ExerciseIconRow(ref = ref)
        }
    }
}

@Preview(name = "Light", showBackground = true, locale = "ru")
@Composable
private fun ExerciseIconsCatalogLightPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            ExerciseIconsCatalog()
        }
    }
}

@Preview(
    name = "Dark",
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
    locale = "ru"
)
@Composable
private fun ExerciseIconsCatalogDarkPreview() {
    JetpackMyWorkoutsTheme {
        Surface(modifier = Modifier.fillMaxWidth()) {
            ExerciseIconsCatalog()
        }
    }
}
