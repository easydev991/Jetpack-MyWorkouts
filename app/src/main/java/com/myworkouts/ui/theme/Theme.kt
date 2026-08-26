package com.myworkouts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.myworkouts.domain.model.AppTheme
import com.myworkouts.domain.model.isDark

private val DarkColorScheme =
    darkColorScheme(
        primary = Purple80,
        secondary = PurpleGrey80,
        tertiary = Pink80
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Purple40,
        secondary = PurpleGrey40,
        tertiary = Pink40
    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
     */
    )

/**
 * Применяет к переданному контенту тему оформления приложения.
 *
 * @param appTheme предпочитаемая тема (`LIGHT` / `DARK` / `SYSTEM`)
 * @param content  Compose-контент, который нужно обернуть темой
 *
 * @implNote `appTheme` non-null (MainActivity передаёт значение сразу
 * через `collectAsState(initial = SYSTEM)` — null-период невозможен).
 * `darkTheme` inlined в `appTheme.isDark(isSystemInDarkTheme())` —
 * единственный caller (MainActivity) не передавал параметр.
 * Inline `Build.VERSION.SDK_INT >= S` для dynamicColor (а не helper
 * isDynamicColorsAvailable) — единственный consumer на этом уровне.
 * Если добавятся ещё Composable с тем же чеком — вынести.
 */
@Composable
fun JetpackMyWorkoutsTheme(
    appTheme: AppTheme = AppTheme.SYSTEM,
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val useDarkTheme = appTheme.isDark(isSystemInDarkTheme())

    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (useDarkTheme) {
                    dynamicDarkColorScheme(context)
                } else {
                    dynamicLightColorScheme(context)
                }
            }

            useDarkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
