package com.catguard.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catguard.data.AppSettings
import com.catguard.data.DayStats
import com.catguard.detection.NormalizedBox
import com.catguard.detection.SupportedAnimals
import com.catguard.monitoring.GuardUiState
import com.catguard.monitoring.ThermalLevel

@Composable
fun MainScreen(
    ui: GuardUiState,
    settings: AppSettings,
    today: DayStats,
    permissionGranted: Boolean,
    attachKey: Any?,
    zoneEditing: Boolean,
    onRequestPermission: () -> Unit,
    onSurfaceReady: (PreviewView) -> Unit,
    onSurfaceReleased: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    onToggleZoneEditing: () -> Unit,
    onZoneDrawn: (NormalizedBox) -> Unit,
    onResetZone: () -> Unit,
    onTestSound: () -> Unit,
    onDismissHelp: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Header(
            today = today,
            targets = settings.guard.effectiveTargets,
            onOpenSettings = onOpenSettings,
            onOpenHistory = onOpenHistory,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp)),
        ) {
            if (permissionGranted) {
                CameraPreviewPane(
                    attachKey = attachKey,
                    onSurfaceReady = onSurfaceReady,
                    onSurfaceReleased = onSurfaceReleased,
                    detections = ui.detections,
                    sourceWidth = ui.sourceWidth,
                    sourceHeight = ui.sourceHeight,
                    zone = settings.guard.roi,
                    state = ui.state,
                    showOverlay = settings.showOverlay,
                    targets = settings.guard.effectiveTargets,
                    zoneEditing = zoneEditing,
                    onZoneDrawn = onZoneDrawn,
                    modifier = Modifier.fillMaxSize(),
                )
                if (ui.torchOn) {
                    TorchBadge(Modifier.align(Alignment.TopEnd))
                }
                if (zoneEditing) {
                    ZoneEditingBanner(
                        onDone = onToggleZoneEditing,
                        onReset = onResetZone,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else if (!settings.helpDismissed) {
                    HelpCard(
                        audioReady = ui.audioStatus.ready,
                        onTestSound = onTestSound,
                        onDismiss = onDismissHelp,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            } else {
                PermissionPrompt(onRequestPermission, Modifier.fillMaxSize())
            }
        }

        StatusCard(ui = ui, settings = settings, modifier = Modifier.padding(12.dp))

        Controls(
            running = ui.running,
            enabled = permissionGranted,
            zoneEditing = zoneEditing,
            onStart = onStart,
            onStop = onStop,
            onToggleZoneEditing = onToggleZoneEditing,
        )
        Spacer(Modifier.height(12.dp))
    }
}

/**
 * The running day total: how many animals were confirmed and how many of those
 * actually triggered a bark. Both numbers matter - a large gap means encounters
 * are landing inside cooldowns, or the sound is switched off.
 */
@Composable
private fun Header(
    today: DayStats,
    targets: Set<String>,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "CAT GUARD",
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onOpenHistory) { Text("History") }
            TextButton(onClick = onOpenSettings) { Text("Settings") }
        }

        Spacer(Modifier.height(2.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            TodayStat(
                value = today.detections,
                label = if (targets.size == 1 && SupportedAnimals.cat.label in targets) {
                    "cats today"
                } else {
                    "detections today"
                },
                color = StatusColors.confirmed,
            )
            Spacer(Modifier.width(20.dp))
            TodayStat(value = today.barks, label = "barks today", color = StatusColors.barking)
            if (today.silent > 0) {
                Spacer(Modifier.width(20.dp))
                TodayStat(value = today.silent, label = "silent", color = StatusColors.idle)
            }
        }

        if (today.byLabel.size > 1) {
            Text(
                today.byLabel.joinToString("   ") { (label, count) ->
                    "${SupportedAnimals.byLabel(label)?.emoji ?: ""} " +
                        "${SupportedAnimals.displayNameFor(label)} $count"
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun TodayStat(value: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$value", fontSize = 26.sp, fontWeight = FontWeight.Black, color = color)
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

@Composable
private fun StatusCard(ui: GuardUiState, settings: AppSettings, modifier: Modifier = Modifier) {
    val color = StatusColors.forState(ui.state)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = ui.state.label(ui.cooldownRemainingMs),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }

            Spacer(Modifier.height(8.dp))

            val detail = buildString {
                if (ui.running) {
                    append("Confirm ${ui.hitsInWindow}/${settings.guard.effectiveWindow} frames")
                    append("  ·  ")
                    append("${(settings.guard.confidenceThreshold * 100).toInt()}% threshold")
                    if (ui.lastInferenceMs > 0) append("  ·  ${ui.lastInferenceMs} ms/frame")
                } else {
                    append("Guard stopped. Press START GUARD to begin monitoring.")
                }
            }
            Text(detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text(
                "Watching for " + settings.guard.effectiveTargets.joinToString(", ") {
                    SupportedAnimals.displayNameFor(it).lowercase()
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (ui.running && ui.volumeWarning != null) {
                Spacer(Modifier.height(6.dp))
                Text(ui.volumeWarning, fontSize = 12.sp, color = StatusColors.possible)
            }

            if (ui.running && ui.darknessWarning != null) {
                Spacer(Modifier.height(6.dp))
                Text(ui.darknessWarning, fontSize = 12.sp, color = StatusColors.possible)
            }

            if (ui.running && ui.thermalLevel != ThermalLevel.NORMAL) {
                Text(
                    "Phone is ${ui.thermalLevel.label} - inference slowed to " +
                        "%.1f/s to let it cool.".format(ui.effectiveFps),
                    fontSize = 12.sp,
                    color = StatusColors.possible,
                )
            }

            if (ui.running && settings.guard.soundEnabled && !ui.audioStatus.ready) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Deterrent sound unavailable: ${ui.audioStatus.message ?: "still loading"}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (ui.running && !settings.guard.soundEnabled) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Sound is switched off in Settings - encounters are counted silently.",
                    fontSize = 12.sp,
                    color = StatusColors.possible,
                )
            }
            ui.errorMessage?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TorchBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC10161F))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("🔦 LIGHT ON", fontSize = 11.sp, color = StatusColors.possible)
    }
}

@Composable
private fun Controls(
    running: Boolean,
    enabled: Boolean,
    zoneEditing: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onToggleZoneEditing: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = if (running) onStop else onStart,
            enabled = enabled,
            modifier = Modifier
                .weight(1f)
                .height(54.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (running) StatusColors.barking else MaterialTheme.colorScheme.primary,
                contentColor = if (running) Color.White else MaterialTheme.colorScheme.onPrimary,
            ),
        ) {
            Text(
                if (running) "STOP GUARD" else "START GUARD",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
        }
        OutlinedButton(
            onClick = onToggleZoneEditing,
            enabled = enabled,
            modifier = Modifier.height(54.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(if (zoneEditing) "DONE" else "ZONE")
        }
    }
}

@Composable
private fun ZoneEditingBanner(
    onDone: () -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xCC10161F)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                "Drag on the preview to draw the detection zone.",
                fontSize = 13.sp,
                color = Color.White,
            )
            Text(
                "Animals are only acted on inside this zone.",
                fontSize = 11.sp,
                color = Color(0xFFAEBBCB),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onReset) { Text("Use full frame") }
                TextButton(onClick = onDone) { Text("Done") }
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequestPermission: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                "Camera access is required",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "CatGuard watches the camera for cats entirely on this phone. " +
                    "No image ever leaves the device and nothing is uploaded anywhere.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onRequestPermission) { Text("GRANT CAMERA ACCESS") }
        }
    }
}
