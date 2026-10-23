package com.ayush.aynamusic.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ayush.aynamusic.data.prefs.AppSettings
import com.ayush.aynamusic.data.prefs.ThemeMode

private val DarkColors = darkColorScheme(
    primary = Acid,
    onPrimary = Color(0xFF1C2800),
    primaryContainer = Color(0xFF384D00),
    onPrimaryContainer = Color(0xFFE8FFA8),
    inversePrimary = Color(0xFF4E6600),
    secondary = Mint,
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005046),
    onSecondaryContainer = Color(0xFFA2F2E1),
    tertiary = Coral,
    onTertiary = Color(0xFF5D1600),
    tertiaryContainer = Color(0xFF7D2D12),
    onTertiaryContainer = Color(0xFFFFDBD0),
    background = Ink,
    onBackground = Color(0xFFF2F3EC),
    surface = InkRaised,
    onSurface = Color(0xFFF2F3EC),
    surfaceVariant = Color(0xFF1B1D24),
    onSurfaceVariant = Color(0xFFC4C7BD),
    surfaceTint = Acid,
    inverseSurface = Color(0xFFF2F3EC),
    inverseOnSurface = Color(0xFF2E3029),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8D9084),
    outlineVariant = Color(0xFF34373E),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF353840),
    surfaceDim = Color(0xFF08090C),
    surfaceContainerLowest = Color(0xFF050609),
    surfaceContainerLow = Color(0xFF0C0E12),
    surfaceContainer = Color(0xFF111319),
    surfaceContainerHigh = Color(0xFF181B21),
    surfaceContainerHighest = Color(0xFF20242B)
)

private val LightColors = lightColorScheme(
    primary = Forest,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F5A0),
    onPrimaryContainer = Color(0xFF132000),
    inversePrimary = Acid,
    secondary = TealDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F1E4),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF8B4A32),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBCF),
    onTertiaryContainer = Color(0xFF351000),
    background = Paper,
    onBackground = Color(0xFF1B1D17),
    surface = Paper,
    onSurface = Color(0xFF1B1D17),
    surfaceVariant = Color(0xFFE8E9DF),
    onSurfaceVariant = Color(0xFF45483F),
    surfaceTint = Forest,
    inverseSurface = Color(0xFF303229),
    inverseOnSurface = Paper,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF76786C),
    outlineVariant = Color(0xFFC6C8BB),
    scrim = Color(0xFF000000),
    surfaceBright = PaperRaised,
    surfaceDim = Color(0xFFDCDDD3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F4EC),
    surfaceContainer = Color(0xFFEDEEE5),
    surfaceContainerHigh = Color(0xFFE7E8DF),
    surfaceContainerHighest = Color(0xFFE1E2D9)
)

@Composable
fun AynaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val dynamic = settings.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var colors = when {
        dynamic && dark -> dynamicDarkColorScheme(context)
        dynamic -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    if (dark && settings.amoled) {
        colors = colors.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF07080A),
            surfaceContainer = Color(0xFF0C0D11),
            surfaceContainerHigh = Color(0xFF13151A),
            surfaceContainerHighest = Color(0xFF1B1E24)
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = context.findActivity() ?: return@SideEffect
            val window = activity.window
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = AynaTypography,
        shapes = androidx.compose.material3.Shapes(
            extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
            extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
        ),
        content = content
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
