package com.example.trellocontroller.ui.theme // Geändert

import android.os.Build // Behalte bestehende Imports
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
// import androidx.compose.material3.Typography // Dieser Import ist nicht nötig, wenn Type.kt im selben package ist
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color // Behalte diesen Import, falls md_theme_light_... Farben hier definiert bleiben
import androidx.compose.ui.platform.LocalContext
// Entferne diese Text-Style spezifischen Imports, wenn Typography aus Type.kt kommt
// import androidx.compose.ui.text.TextStyle
// import androidx.compose.ui.text.font.FontFamily
// import androidx.compose.ui.text.font.FontWeight
// import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
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

// Optional: Wenn du benutzerdefinierte Farben hier definierst, behalte sie.
// Ansonsten könnten diese auch in Color.kt sein.
val md_theme_light_primary = Color(0xFF00629B) // Beispiel, falls hier definiert
val md_theme_light_onPrimary = Color(0xFFFFFFFF) // Beispiel
val md_theme_light_background = Color(0xFFFDFCFB) // Beispiel
val md_theme_light_surface = Color(0xFFFDFCFB) // Beispiel

// REMOVE THE TYPOGRAPHY DEFINITION FROM HERE
/*
val Typography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    // ... other text styles ...
)
*/

@Composable
fun TrelloControllerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Dynamic color is available on Android 12+
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme // Hier könntest du dein benutzerdefiniertes LightColorScheme verwenden, wenn du eines hast
        // else -> lightColorScheme( // Oder verwende deine md_theme_light_ Farben direkt
        // primary = md_theme_light_primary,
        // onPrimary = md_theme_light_onPrimary,
        // background = md_theme_light_background,
        // surface = md_theme_light_surface
        // )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography, // Diese Zeile greift auf Typography aus Type.kt zu
        content = content
    )
}