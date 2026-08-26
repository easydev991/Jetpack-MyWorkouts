package com.myworkouts.ui.screens.more

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.myworkouts.BuildConfig
import com.myworkouts.R
import com.myworkouts.navigation.Screen

/**
 * Экран «Ещё» — точка входа в дополнительные настройки приложения.
 *
 * На текущем этапе содержит только одну кнопку перехода к экрану
 * «Оформление приложения». По мере появления новых кнопок структура может
 * вырасти до `Column + Spacer(weight(1f))` (см. план в
 * `docs/appearance-screen-plan.md` §9.2).
 */
// ponytail: NavController обязательный (callback-обёртка — лишний
// indirection при единственном вызове в RootScreen). Row(fillMaxWidth,
// Arrangement.Center) центрирует кнопку только по горизонтали, не
// затрагивая BottomCenter версию. Если добавится второй вызов
// MoreScreen (превью/тест) — ввести callback-обёртку или
// rememberNavController (см. design.md Decisions 11, 12).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        // System nav bar inset уже учтён NavigationBar в RootScreen;
        // здесь оставляем только горизонтальные insets (displayCutout и т.п.).
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.more_title)) })
        }
    ) { paddingValues ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp)
        ) {
            // Row с fillMaxWidth + Arrangement.Center центрирует кнопку только
            // по горизонтали, оставляя её вверху (Box по умолчанию
            // ставил бы ребёнка в TopStart → к левому краю).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilledTonalButton(
                    onClick = {
                        navController.navigate(Screen.Appearance.route) {
                            popUpTo(Screen.More.route)
                        }
                    },
                    contentPadding =
                        PaddingValues(
                            horizontal = dimensionResource(R.dimen.button_horizontal_padding)
                        )
                ) {
                    Text(stringResource(R.string.appearance_title))
                }
            }

            Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )
        }
    }
}
