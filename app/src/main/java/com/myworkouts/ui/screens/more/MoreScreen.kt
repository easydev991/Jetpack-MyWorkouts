package com.myworkouts.ui.screens.more

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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

/** URL страницы отзывов о приложении в RuStore (кнопка «Оценить приложение»). */
private const val APP_RATE_URL = "https://apps.rustore.ru/app/com.myworkouts/reviews"

/** URL страницы приложения в RuStore (ссылка в share-тексте). */
private const val APP_SHARE_URL = "https://apps.rustore.ru/app/com.myworkouts"

/** URL репозитория проекта на GitHub (кнопка «GitHub»). */
private const val GITHUB_REPOSITORY_URL = "https://github.com/easydev991/Jetpack-MyWorkouts"

/**
 * Экран «Ещё» — точка входа в дополнительные настройки приложения.
 *
 * Содержит кнопку переключения видимости вкладки «Упражнения»
 * (exercises-tab), кнопки перехода к экрану «Оформление приложения»,
 * «Данные приложения», «Отправить обратную связь», кнопку «GitHub», а на
 * flavor rustore — «Оценить приложение» и «Поделиться приложением». По
 * мере появления новых кнопок структура экрана — единый `Column` с
 * `verticalScroll` (контент должен прокручиваться в landscape и при
 * крупных шрифтах) и единым интервалом через `Arrangement.spacedBy`.
 * Версия приложения прижата к низу через `Spacer(weight)` — в
 * скроллящейся колонке спейсер сжимается в ноль, когда контент
 * переполняет экран.
 *
 * @implNote NavController обязательный (callback-обёртка — лишний
 * indirection при единственном вызове в RootScreen). Состояние кнопки и
 * запись — выше по потоку, в RootScreen (design Decision 8): экран
 * остаётся без ViewModel, получает `Boolean` + колбэк. Column
 * прижимает кнопки к верху, горизонтальное центрирование через
 * `Alignment.CenterHorizontally`; версия приложения живёт в той же
 * колонке за `Spacer(weight)`.
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
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp),
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

            // Кнопки «Оценить приложение» и «Поделиться приложением»
            // (more-screen) — только на flavor rustore
            if (BuildConfig.RUSTORE_FEATURES) {
                RateAppButton(context = context)
                ShareAppButton(context = context)
            }

            GitHubButton(context = context)

            // Версия прижата к низу через weight-спейсер: в скроллящейся
            // колонке он сжимается в ноль, когда контент переполняет экран
            // (landscape, крупные шрифты), и добивает пустоту, когда
            // всё помещается (образец — MoreScreen в JetpackDays)
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(R.string.app_version, BuildConfig.VERSION_NAME),
                modifier =
                    Modifier
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
 * Кнопки перехода на не-табовые экраны настроек: «Оформление
 * приложения» и «Данные приложения» (app-data-screen, спека
 * more-screen).
 */
@Composable
private fun NavigationButtons(navController: NavHostController) {
    ScreenButton(stringResource(R.string.appearance_title)) {
        navController.navigate(Screen.Appearance.route) {
            popUpTo(Screen.More.route)
        }
    }

    ScreenButton(stringResource(R.string.app_data)) {
        navController.navigate(Screen.AppData.route) {
            popUpTo(Screen.More.route)
        }
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
    ScreenButton(stringResource(R.string.feedback_send)) {
        analyticsProvider.log(
            AnalyticsEvent.UserAction(UserActionType.SEND_FEEDBACK)
        )
        sendFeedback(context)
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

/**
 * Кнопка «Оценить приложение» (more-screen): открывает страницу отзывов
 * приложения в RuStore. Рисуется только на flavor rustore — вызов
 * обёрнут в гейт `BuildConfig.RUSTORE_FEATURES` в [MoreScreen].
 */
@Composable
private fun RateAppButton(context: Context) {
    ScreenButton(stringResource(R.string.rate_the_app)) {
        openUrl(context, APP_RATE_URL, "Не удалось открыть страницу приложения")
    }
}

/**
 * Кнопка «Поделиться приложением» (more-screen): открывает системный
 * chooser с текстом и ссылкой на приложение в RuStore. Рисуется только
 * на flavor rustore — гейт в [MoreScreen].
 */
@Composable
private fun ShareAppButton(context: Context) {
    ScreenButton(stringResource(R.string.share_the_app)) {
        shareApp(context)
    }
}

/** Делится ссылкой на приложение через системный ShareSheet. */
private fun shareApp(context: Context) {
    val appName = context.getString(R.string.app_name)
    val shareText = context.getString(R.string.share_text, appName)
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "$shareText\n$APP_SHARE_URL")
            putExtra(Intent.EXTRA_TITLE, context.getString(R.string.share_the_app))
        }

    try {
        context.startActivity(
            Intent.createChooser(
                intent,
                context.getString(R.string.share_the_app)
            )
        )
    } catch (e: ActivityNotFoundException) {
        Log.e("MoreScreen", "Не удалось открыть ShareSheet", e)
    }
}

/**
 * Кнопка «GitHub» (more-screen-github-link): открывает страницу
 * репозитория проекта в системном браузере. Рисуется на обоих флейворах
 * — вызов в [MoreScreen] вне гейта `BuildConfig.RUSTORE_FEATURES`.
 * `internal` (не private): превью в `MoreScreenPreview.kt` обращается
 * напрямую, по образцу [ExercisesTabToggle].
 */
@Composable
internal fun GitHubButton(context: Context) {
    ScreenButton(stringResource(R.string.github_button)) {
        openUrl(context, GITHUB_REPOSITORY_URL, "Не удалось открыть страницу GitHub")
    }
}

/** Открывает URL в системном браузере; при отсутствии обработчика логирует ошибку без падения. */
private fun openUrl(
    context: Context,
    url: String,
    logMessage: String
) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri())

    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Log.e("MoreScreen", logMessage, e)
    }
}
