package com.catguard.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.catguard.monitoring.GuardState

private val Ink = Color(0xFF10161F)
private val Surface1 = Color(0xFF1B2430)
private val Surface2 = Color(0xFF243040)
private val Accent = Color(0xFF4DD0A7)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF07231A),
    secondary = Color(0xFF7FB6FF),
    background = Ink,
    onBackground = Color(0xFFE6ECF3),
    surface = Surface1,
    onSurface = Color(0xFFE6ECF3),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFAEBBCB),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    background = Color(0xFFF4F6F9),
    surface = Color(0xFFFFFFFF),
)

/**
 * Dark by default: this is a camera monitor that will often sit in a dim room,
 * and a dark surface keeps the preview readable.
 */
@Composable
fun CatGuardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}

/** Status colours, shared by the status card and the bounding-box overlay. */
object StatusColors {
    val monitoring = Color(0xFF4DD0A7)
    val possible = Color(0xFFFFC857)
    val confirmed = Color(0xFFFF8A3D)
    val barking = Color(0xFFFF5252)
    val cooldown = Color(0xFF7FB6FF)
    val idle = Color(0xFF8E9AAB)

    fun forState(state: GuardState): Color = when (state) {
        GuardState.IDLE -> idle
        GuardState.MONITORING -> monitoring
        GuardState.POSSIBLE_CAT -> possible
        GuardState.CONFIRMED_CAT -> confirmed
        GuardState.BARKING -> barking
        GuardState.COOLDOWN -> cooldown
    }
}

fun GuardState.label(cooldownRemainingMs: Long): String = when (this) {
    GuardState.IDLE -> "STOPPED"
    GuardState.MONITORING -> "MONITORING"
    GuardState.POSSIBLE_CAT -> "CAT DETECTED"
    GuardState.CONFIRMED_CAT -> "CAT CONFIRMED"
    GuardState.BARKING -> "BARKING"
    GuardState.COOLDOWN -> "COOLDOWN - ${(cooldownRemainingMs + 999) / 1000}s"
}
