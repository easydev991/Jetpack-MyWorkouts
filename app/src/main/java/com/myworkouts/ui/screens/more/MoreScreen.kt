package com.myworkouts.ui.screens.more

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavHostController
import com.myworkouts.BuildConfig
import com.myworkouts.R
import com.myworkouts.analytics.AnalyticsEvent
import com.myworkouts.analytics.AnalyticsProvider
import com.myworkouts.analytics.UserActionType
import com.myworkouts.navigation.Screen

/**
 * Экран «Ещё» — точка входа в дополнительные настройки приложения.
 *
 * Содержит кнопку переключения видимости вкладки «Упражнения»
 * (exercises-tab), кнопки перехода к экрану «Оформление приложения»,
 * «Данные приложения» и «Отправить обратную связь». По мере появления
 * новых кнопок структура экрана — `Box` + `Column` с единым интервалом
 * через `Arrangement.spacedBy`. Версия приложения прижата к низу через
 * `Box.BottomCenter`.
 *
 * @implNote NavController обязательный (callback-обёртка — лишний
 * indirection при единственном вызове в RootScreen). Состояние кнопки и
 * запись — выше по потоку, в RootScreen (design Decision 8): экран
 * остаётся без ViewModel, получает `Boolean` + колбэк. Column
 * с дефолтным `Arrangement.Top` прижимает кнопки к верху, горизонтальное
 * центрирование через `Alignment.CenterHorizontally`; версия
 * приложения позиционируется независимо в Box.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    navController: NavHostController,
    showExercisesTab: Boolean,
    onShowExercisesTabChange: (Boolean) -> Unit,
    analyticsProvider: AnalyticsProvider,
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
        val context = LocalContext.current
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                // Единый интервал между всеми кнопками колонки, включая
                // обе кнопки внутри NavigationButtons (они без обёртки —
                // прямые measurables этой Column)
                verticalArrangement =
                    Arrangement.spacedBy(dimensionResource(R.dimen.spacing_xxsmall)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NavigationButtons(navController = navController)

                // Кнопка видимости вкладки «Упражнения» (more-screen,
                // design Decision 8): сразу после навигационных кнопок,
                // до feedback.
                ExercisesTabToggle(
                    showExercisesTab = showExercisesTab,
                    onShowExercisesTabChange = { show ->
                        // Действие на тапе; value — итоговое состояние
                        // вкладки: show/hide (design D10)
                        analyticsProvider.log(
                            AnalyticsEvent.UserAction(
                                UserActionType.TOGGLE_EXERCISES_TAB,
                                if (show) "show" else "hide"
                            )
                        )
                        onShowExercisesTabChange(show)
                    }
                )

                FeedbackButton(
                    context = context,
                    analyticsProvider = analyticsProvider
                )
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

/**
 * Кнопка видимости вкладки «Упражнения» (exercises-tab, design
 * Decision 8): в стиле остальных кнопок экрана, текст зависит от
 * состояния — «Показать вкладку…» при скрытой вкладке, «Скрыть…» при
 * показанной. Тап по кнопке переключает настройку.
 */
@Composable
internal fun ExercisesTabToggle(
    showExercisesTab: Boolean,
    onShowExercisesTabChange: (Boolean) -> Unit
) {
    FilledTonalButton(
        onClick = { onShowExercisesTabChange(!showExercisesTab) },
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(R.dimen.button_horizontal_padding)
            )
    ) {
        Text(
            stringResource(
                if (showExercisesTab) R.string.more_exercises_tab_hide else R.string.more_exercises_tab_show
            )
        )
    }
}

/**
 * Кнопки перехода на не-табовые экраны настроек: «Оформление
 * приложения» и «Данные приложения» (app-data-screen, спека
 * more-screen). Без layout-обёртки: интервал между кнопками задаёт
 * родительская `Column` через `spacedBy`.
 */
@Composable
private fun NavigationButtons(navController: NavHostController) {
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

    FilledTonalButton(
        onClick = {
            navController.navigate(Screen.AppData.route) {
                popUpTo(Screen.More.route)
            }
        },
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(R.dimen.button_horizontal_padding)
            )
    ) {
        Text(stringResource(R.string.app_data))
    }
}

/**
 * Кнопка «Отправить обратную связь» (more-screen): тап логируется
 * (design D10) и письмо уходит через почтовый клиент устройства.
 * Вынесено из [MoreScreen] ради лимита detekt `LongMethod: 60`.
 */
@Composable
private fun FeedbackButton(
    context: Context,
    analyticsProvider: AnalyticsProvider
) {
    FilledTonalButton(
        onClick = {
            analyticsProvider.log(
                AnalyticsEvent.UserAction(UserActionType.SEND_FEEDBACK)
            )
            sendFeedback(context)
        },
        contentPadding =
            PaddingValues(
                horizontal = dimensionResource(R.dimen.button_horizontal_padding)
            )
    ) {
        Text(stringResource(R.string.feedback_send))
    }
}

/** Отправляет отзыв по электронной почте. */
private fun sendFeedback(context: Context) {
    val appName = context.getString(R.string.app_name)
    val subject = context.getString(R.string.feedback_subject, appName)
    val body =
        context.getString(
            R.string.feedback_body,
            Build.VERSION.RELEASE,
            BuildConfig.VERSION_NAME
        )

    val intent =
        Intent(
            Intent.ACTION_SENDTO,
            "mailto:easy_dev991@mail.ru?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}".toUri()
        )

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e("MoreScreen", "Почтовый клиент не найден", e)
        Toast.makeText(context, R.string.feedback_no_email_client, Toast.LENGTH_LONG).show()
    }
}
