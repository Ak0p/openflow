package ro.akop.openflow.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Indigo40,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = IndigoOnContainer,
    secondary = Teal40,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OutlineLight,
    outline = OutlineLight,
    error = ErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = Indigo80,
    onPrimary = IndigoOnContainer,
    primaryContainer = IndigoDarkContainer,
    onPrimaryContainer = IndigoContainer,
    secondary = Teal80,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OutlineDark,
    outline = OutlineDark,
    error = ErrorDark,
)

@Composable
fun OpenFlowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}
