package com.pixelthings.uecapupdater.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA0D0C4), // 保持你的薄荷綠/青色
    secondary = PurpleGrey80,
    tertiary = Pink80,

    // 🚀 關鍵修復：手動補上 Pixel 系統設定的標誌性墨綠深灰基底（防止在未觸發動態色彩時死黑）
    background = androidx.compose.ui.graphics.Color(0xFF111413),
    surface = androidx.compose.ui.graphics.Color(0xFF111413),

    // 容器色
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF1A1D1C),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF171A19),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF202423),

    onBackground = androidx.compose.ui.graphics.Color(0xFFE1E3E1),
    onSurface = androidx.compose.ui.graphics.Color(0xFFE1E3E1)
)

private val LightColorScheme = lightColorScheme(
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

@Composable
fun ShannonConfigProTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}