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
import com.myworkouts.R
import com.myworkouts.ui.theme.JetpackMyWorkoutsTheme

/**
 * Пары иконок упражнений: светлый вариант + тёмный (`_dark`, инверсия
 * яркости, генерируется `scripts/generate_dark_icon_variants.py`).
 *
 * Dev-каталог для просмотра: в прод-код не подключён, при release-сборке
 * вырезается R8 (`isMinifyEnabled = true`).
 */
private val exerciseIconPairs =
    listOf(
        R.drawable.abs_hanging_leg_raises to R.drawable.abs_hanging_leg_raises_dark,
        R.drawable.barbell to R.drawable.barbell_dark,
        R.drawable.barbell_1 to R.drawable.barbell_1_dark,
        R.drawable.barbell_2 to R.drawable.barbell_2_dark,
        R.drawable.barbell_3 to R.drawable.barbell_3_dark,
        R.drawable.barbell_4 to R.drawable.barbell_4_dark,
        R.drawable.bars to R.drawable.bars_dark,
        R.drawable.crunches to R.drawable.crunches_dark,
        R.drawable.dumbbells to R.drawable.dumbbells_dark,
        R.drawable.dumbbells_1 to R.drawable.dumbbells_1_dark,
        R.drawable.jumping_rope to R.drawable.jumping_rope_dark,
        R.drawable.lunges to R.drawable.lunges_dark,
        R.drawable.pullups to R.drawable.pullups_dark,
        R.drawable.pullups_australian to R.drawable.pullups_australian_dark,
        R.drawable.pushups to R.drawable.pushups_dark,
        R.drawable.pushups_knee to R.drawable.pushups_knee_dark,
        R.drawable.run to R.drawable.run_dark,
        R.drawable.squats to R.drawable.squats_dark,
        R.drawable.squats_pistol to R.drawable.squats_pistol_dark
    )

/**
 * Одна строка каталога: иконка-вариант по текущей теме (без tint, как
 * будет отображаться в списках) + имя светлого drawable-ресурса.
 */
@Composable
private fun ExerciseIconRow(
    lightResId: Int,
    darkResId: Int,
    modifier: Modifier = Modifier
) {
    val resId = if (isSystemInDarkTheme()) darkResId else lightResId
    val entryName = LocalResources.current.getResourceEntryName(lightResId)
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
        exerciseIconPairs.forEach { (lightResId, darkResId) ->
            ExerciseIconRow(lightResId = lightResId, darkResId = darkResId)
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
