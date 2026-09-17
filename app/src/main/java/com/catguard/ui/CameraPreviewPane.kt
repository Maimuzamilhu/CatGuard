package com.catguard.ui

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.catguard.detection.Detection
import com.catguard.detection.NormalizedBox
import com.catguard.monitoring.GuardState

/**
 * Live camera preview with the detection overlay drawn on top.
 *
 * The preview view is created once and reused; the surface provider is attached
 * on composition and detached on disposal so that monitoring survives the UI
 * going away.
 */
@Composable
fun CameraPreviewPane(
    /**
     * Identity of the camera owner. The surface is re-attached only when this
     * changes (the service connecting or reconnecting), never on every
     * recomposition - re-attaching a live surface restarts the preview stream.
     */
    attachKey: Any?,
    onSurfaceReady: (PreviewView) -> Unit,
    onSurfaceReleased: () -> Unit,
    detections: List<Detection>,
    sourceWidth: Int,
    sourceHeight: Int,
    zone: NormalizedBox,
    state: GuardState,
    showOverlay: Boolean,
    targets: Set<String>,
    modifier: Modifier = Modifier,
    zoneEditing: Boolean = false,
    onZoneDrawn: (NormalizedBox) -> Unit = {},
) {
    val context = LocalContext.current
    val previewView = remember(context) {
        PreviewView(context).apply {
            // FIT_CENTER so the user sees the entire frame the detector sees.
            scaleType = PreviewView.ScaleType.FIT_CENTER
            // TextureView-backed: more reliable on the older handsets this targets.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        DisposableEffect(previewView, attachKey) {
            if (attachKey != null) onSurfaceReady(previewView)
            onDispose { onSurfaceReleased() }
        }

        DetectionOverlay(
            detections = detections,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            zone = zone,
            state = state,
            showBoxes = showOverlay,
            targets = targets,
            zoneEditing = zoneEditing,
            onZoneDrawn = onZoneDrawn,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun DetectionOverlay(
    detections: List<Detection>,
    sourceWidth: Int,
    sourceHeight: Int,
    zone: NormalizedBox,
    state: GuardState,
    showBoxes: Boolean,
    targets: Set<String>,
    zoneEditing: Boolean,
    onZoneDrawn: (NormalizedBox) -> Unit,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }

    val gestureModifier = if (zoneEditing) {
        Modifier.pointerInput(sourceWidth, sourceHeight) {
            detectDragGestures(
                onDragStart = { offset ->
                    dragStart = offset
                    dragCurrent = offset
                },
                onDrag = { change, _ ->
                    change.consume()
                    dragCurrent = change.position
                },
                onDragEnd = {
                    val start = dragStart
                    val end = dragCurrent
                    if (start != null && end != null) {
                        val vp = Viewport.fitCenter(
                            sourceWidth.takeIf { it > 0 } ?: size.width,
                            sourceHeight.takeIf { it > 0 } ?: size.height,
                            size.width.toFloat(),
                            size.height.toFloat(),
                        )
                        val box = NormalizedBox.fromCorners(
                            vp.toNormalizedX(start.x), vp.toNormalizedY(start.y),
                            vp.toNormalizedX(end.x), vp.toNormalizedY(end.y),
                        )
                        // Ignore accidental taps; a zone must be a real rectangle.
                        if (box.width > 0.05f && box.height > 0.05f) onZoneDrawn(box)
                    }
                    dragStart = null
                    dragCurrent = null
                },
                onDragCancel = {
                    dragStart = null
                    dragCurrent = null
                },
            )
        }
    } else {
        Modifier
    }

    Canvas(modifier = modifier.then(gestureModifier)) {
        // When no frame has been analyzed yet, fall back to the view itself so the
        // zone rectangle still lines up with what is on screen.
        val srcW = if (sourceWidth > 0) sourceWidth else size.width.toInt()
        val srcH = if (sourceHeight > 0) sourceHeight else size.height.toInt()
        val vp = Viewport.fitCenter(srcW, srcH, size.width, size.height)

        val strokeThin = with(density) { 1.5.dp.toPx() }
        val strokeThick = with(density) { 3.dp.toPx() }

        // --- detection zone ------------------------------------------------
        val isFullFrame = zone.left <= 0.001f && zone.top <= 0.001f &&
            zone.right >= 0.999f && zone.bottom >= 0.999f
        if (!isFullFrame || zoneEditing) {
            val z = vp.toViewRect(zone)
            // Dim everything outside the zone so the monitored area is unmistakable.
            val dim = Color.Black.copy(alpha = 0.45f)
            drawRect(dim, topLeft = Offset(0f, 0f), size = Size(size.width, z[1]))
            drawRect(dim, topLeft = Offset(0f, z[3]), size = Size(size.width, size.height - z[3]))
            drawRect(dim, topLeft = Offset(0f, z[1]), size = Size(z[0], z[3] - z[1]))
            drawRect(
                dim,
                topLeft = Offset(z[2], z[1]),
                size = Size(size.width - z[2], z[3] - z[1]),
            )
            drawRect(
                color = StatusColors.cooldown,
                topLeft = Offset(z[0], z[1]),
                size = Size(z[2] - z[0], z[3] - z[1]),
                style = Stroke(width = strokeThin),
            )
        }

        // --- rectangle being drawn right now --------------------------------
        val start = dragStart
        val current = dragCurrent
        if (zoneEditing && start != null && current != null) {
            drawRect(
                color = Color.White,
                topLeft = Offset(minOf(start.x, current.x), minOf(start.y, current.y)),
                size = Size(kotlin.math.abs(current.x - start.x), kotlin.math.abs(current.y - start.y)),
                style = Stroke(width = strokeThick),
            )
        }

        // --- detections -----------------------------------------------------
        if (!showBoxes) return@Canvas
        for (detection in detections) {
            // Targets are drawn boldly in the status colour; everything else the model
            // saw is drawn faintly - useful while tuning, never alarming.
            val isTarget = detection.label.lowercase() in targets
            val color = if (isTarget) StatusColors.forState(state) else Color(0x66FFFFFF)
            val r = vp.toViewRect(detection.box)
            drawRect(
                color = color,
                topLeft = Offset(r[0], r[1]),
                size = Size(r[2] - r[0], r[3] - r[1]),
                style = Stroke(width = if (isTarget) strokeThick else strokeThin),
            )

            val label = "${detection.label.uppercase()} ${(detection.score * 100).toInt()}%"
            val measured = textMeasurer.measure(
                text = label,
                style = TextStyle(
                    fontSize = if (isTarget) 15.sp else 11.sp,
                    fontWeight = if (isTarget) FontWeight.Bold else FontWeight.Normal,
                ),
            )
            val padding = with(density) { 4.dp.toPx() }
            val boxW = measured.size.width + padding * 2
            val boxH = measured.size.height + padding
            val labelTop = (r[1] - boxH).coerceAtLeast(0f)
            drawRect(
                color = color.copy(alpha = 0.85f),
                topLeft = Offset(r[0], labelTop),
                size = Size(boxW, boxH),
            )
            drawText(
                textLayoutResult = measured,
                color = Color(0xFF10161F),
                topLeft = Offset(r[0] + padding, labelTop + padding / 2),
            )
        }
    }
}
