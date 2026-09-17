package com.catguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catguard.data.AppSettings
import com.catguard.data.TorchMode
import com.catguard.detection.NormalizedBox
import com.catguard.detection.SupportedAnimals
import com.catguard.deterrent.DeterrentAudioConfig
import com.catguard.deterrent.DeterrentChannel
import com.catguard.deterrent.DeterrentSound
import com.catguard.deterrent.SoundCategory
import com.catguard.deterrent.SoundCombo
import com.catguard.deterrent.SoundLibrary
import com.catguard.monitoring.GuardConfig
import com.catguard.monitoring.RoiMode
import kotlin.math.roundToInt

/** Callbacks the settings screen needs. Grouped so the signature stays readable. */
data class SettingsActions(
    val onConfidence: (Float) -> Unit,
    val onCooldown: (Long) -> Unit,
    val onConfirmation: (required: Int, window: Int) -> Unit,
    val onToggleTarget: (label: String, enabled: Boolean) -> Unit,
    val onSound: (Boolean) -> Unit,
    val onOverlay: (Boolean) -> Unit,
    val onRequireReentry: (Boolean) -> Unit,
    val onInferenceFps: (Int) -> Unit,
    val onRoiMode: (RoiMode) -> Unit,
    val onResetZone: () -> Unit,
    val onToggleSound: (id: String, enabled: Boolean) -> Unit,
    val onLayerSounds: (Boolean) -> Unit,
    val onDuration: (Long) -> Unit,
    val onStopWhenClear: (Boolean) -> Unit,
    val onApplyCombo: (SoundCombo) -> Unit,
    val onSaveCombo: (String) -> Unit,
    val onDeleteCombo: (String) -> Unit,
    val onVolume: (Float) -> Unit,
    val onChannel: (DeterrentChannel) -> Unit,
    val onMaximiseVolume: (Boolean) -> Unit,
    val onMaximumLoudness: () -> Unit,
    val onImportSound: () -> Unit,
    val onDeleteSound: (String) -> Unit,
    val onTorchMode: (TorchMode) -> Unit,
    val onNightHours: (start: Int, end: Int) -> Unit,
    val onLowLightCapture: (Boolean) -> Unit,
    val onWarnWhenDark: (Boolean) -> Unit,
    val onTestSound: () -> Unit,
    val onBatterySettings: () -> Unit,
    val onShowCredits: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun SettingsScreen(
    settings: AppSettings,
    audioReady: Boolean,
    audioMessage: String?,
    volumeWarning: String?,
    activeSoundName: String?,
    detectorName: String?,
    customSounds: List<DeterrentSound.Custom>,
    torchAvailable: Boolean,
    actions: SettingsActions,
) {
    val guard = settings.guard
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = actions.onBack) { Text("Done") }
        }

        Spacer(Modifier.height(8.dp))

        WhatWeDetectCard(
            detectorName = detectorName,
            selected = guard.effectiveTargets,
            onToggle = actions.onToggleTarget,
        )

        DeterrentSoundCard(
            settings = settings,
            audioReady = audioReady,
            audioMessage = audioMessage,
            volumeWarning = volumeWarning,
            activeSoundName = activeSoundName,
            customSounds = customSounds,
            savedCombos = settings.savedCombos,
            actions = actions,
        )

        SettingsCard("Detection sensitivity") {
            SliderRow(
                title = "Confidence threshold",
                value = "${(guard.confidenceThreshold * 100).roundToInt()}%",
                explanation = "How sure the model must be before a detection counts. Lower " +
                    "catches more animals but also more false alarms. Start at 55-65% and tune " +
                    "after testing.",
                sliderValue = guard.confidenceThreshold,
                range = GuardConfig.MIN_CONFIDENCE..GuardConfig.MAX_CONFIDENCE,
                steps = 14,
                onChange = actions.onConfidence,
            )

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            val window = guard.effectiveWindow
            val required = guard.effectiveRequired
            SliderRow(
                title = "Confirmation sensitivity",
                value = "$required of last $window frames",
                explanation = "A target must appear in this many of the most recent analyzed " +
                    "frames before the deterrent fires. Higher is stricter and rejects one-frame " +
                    "flukes; lower reacts faster to an animal passing through.",
                sliderValue = required.toFloat(),
                range = 1f..window.toFloat().coerceAtLeast(1f),
                steps = (window - 2).coerceAtLeast(0),
                onChange = { actions.onConfirmation(it.roundToInt(), window) },
            )
            SliderRow(
                title = "Confirmation window",
                value = "$window frames",
                explanation = "How many recent frames are remembered for the check above.",
                sliderValue = window.toFloat(),
                range = 2f..6f,
                steps = 3,
                onChange = { actions.onConfirmation(required, it.roundToInt()) },
            )
        }

        SettingsCard("Cooldown and repeats") {
            SliderRow(
                title = "Cooldown",
                value = "${guard.cooldownMs / 1000}s",
                explanation = "After a bark, no second bark until this has elapsed. Monitoring " +
                    "continues throughout.",
                sliderValue = guard.cooldownMs.toFloat(),
                range = GuardConfig.MIN_COOLDOWN_MS.toFloat()..GuardConfig.MAX_COOLDOWN_MS.toFloat(),
                steps = 0,
                onChange = { actions.onCooldown(it.toLong()) },
            )

            HorizontalDivider(Modifier.padding(vertical = 10.dp))

            SwitchRow(
                title = "Require re-entry",
                explanation = "After a bark, the same animal must leave the zone before it can " +
                    "trigger again. Stops an animal that settles down from being barked at forever.",
                checked = guard.requireReentry,
                onChange = actions.onRequireReentry,
            )
        }

        NightCard(
            settings = settings,
            torchAvailable = torchAvailable,
            actions = actions,
        )

        SettingsCard("Detection zone") {
            Text(
                "Only animals inside the zone trigger the deterrent. Draw it with the ZONE " +
                    "button on the main screen.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Current zone: " + if (guard.roi == NormalizedBox.FULL_FRAME) {
                    "full frame"
                } else {
                    "%.0f%%, %.0f%% to %.0f%%, %.0f%%".format(
                        guard.roi.left * 100, guard.roi.top * 100,
                        guard.roi.right * 100, guard.roi.bottom * 100,
                    )
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Text("Zone test", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(
                    label = "Centre point",
                    selected = guard.roiMode == RoiMode.CENTER_POINT,
                    onClick = { actions.onRoiMode(RoiMode.CENTER_POINT) },
                )
                ChoiceChip(
                    label = "30% overlap",
                    selected = guard.roiMode == RoiMode.INTERSECTION,
                    onClick = { actions.onRoiMode(RoiMode.INTERSECTION) },
                )
            }
            Text(
                "Centre point: the middle of the animal must be inside the zone. Overlap: at " +
                    "least 30% of it must be. Either way a single pixel of overlap is not enough.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = actions.onResetZone, modifier = Modifier.fillMaxWidth()) {
                Text("RESET TO FULL FRAME")
            }
        }

        SettingsCard("Display and performance") {
            SwitchRow(
                title = "Detection overlay",
                explanation = "Draw bounding boxes and confidence on the preview.",
                checked = settings.showOverlay,
                onChange = actions.onOverlay,
            )
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            SliderRow(
                title = "Inference rate",
                value = "${settings.inferenceFps} per second",
                explanation = "How often a frame is run through the model. Lower means a cooler, " +
                    "longer-lasting phone; higher catches fast-moving animals more reliably. " +
                    "3 per second is a good balance for a 24/7 run on an old device.",
                sliderValue = settings.inferenceFps.toFloat(),
                range = AppSettings.MIN_INFERENCE_FPS.toFloat()..AppSettings.MAX_INFERENCE_FPS.toFloat(),
                steps = AppSettings.MAX_INFERENCE_FPS - AppSettings.MIN_INFERENCE_FPS - 1,
                onChange = { actions.onInferenceFps(it.roundToInt()) },
            )
        }

        SettingsCard("24/7 operation") {
            Text(
                "Monitoring must be started while CatGuard is on screen - Android only grants " +
                    "background camera access to a foreground service that began this way. Once " +
                    "started it keeps running with the app closed and shows a notification.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Some manufacturers (Xiaomi, Huawei, Oppo, Samsung and others) still kill " +
                    "long-running services. Exempting CatGuard from battery optimisation makes " +
                    "an unattended run far more reliable.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = actions.onBatterySettings, modifier = Modifier.fillMaxWidth()) {
                Text("OPEN BATTERY OPTIMISATION SETTINGS")
            }
        }

        SettingsCard("Privacy") {
            Text(
                "Everything runs on this phone. No video, image or detection ever leaves the " +
                    "device; there is no account, no analytics and no internet permission in " +
                    "this app.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = actions.onShowCredits) { Text("Sound credits and licences") }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ------------------------------------------------------------------ what we detect

/**
 * Shown first, because it answers the first question anyone has: what can this
 * thing actually recognise, and with what model.
 *
 * The full catalogue is exposed - all 80 COCO categories, not just the animals.
 * Hiding 69 of them would make the app look less capable than it is, and knowing
 * the real list is what makes a false alarm explicable ("it called the bag a
 * cat") rather than mysterious.
 */
@Composable
private fun WhatWeDetectCard(
    detectorName: String?,
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
) {
    var showOtherAnimals by remember { mutableStateOf(false) }
    var showEverything by remember { mutableStateOf(false) }

    SettingsCard("What we can detect") {
        Text(
            "Model: ${detectorName ?: "EfficientDet-Lite0 (COCO)"}",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "A general object detector trained on the COCO dataset. It can recognise " +
                "${SupportedAnimals.totalCategories} different things - every one is listed " +
                "below and any of them can trigger the deterrent. Nothing outside this list " +
                "can be detected without replacing the model.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Tick what should trigger the deterrent:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))

        SupportedAnimals.common.forEach { target ->
            AnimalRow(
                emoji = target.emoji,
                name = target.displayName,
                note = target.note,
                checked = target.label in selected,
                onChange = { onToggle(target.label, it) },
            )
        }

        // --- other animals -------------------------------------------------
        ExpanderRow(
            label = "Other animals",
            count = SupportedAnimals.otherAnimals.size,
            expanded = showOtherAnimals,
            onToggle = { showOtherAnimals = !showOtherAnimals },
        )
        if (showOtherAnimals) {
            SupportedAnimals.otherAnimals.forEach { target ->
                AnimalRow(
                    emoji = target.emoji,
                    name = target.displayName,
                    note = target.note,
                    checked = target.label in selected,
                    onChange = { onToggle(target.label, it) },
                )
            }
        }

        // --- everything else the model knows --------------------------------
        ExpanderRow(
            label = "Everything else this model knows",
            count = SupportedAnimals.otherGroups.sumOf { it.targets.size },
            expanded = showEverything,
            onToggle = { showEverything = !showEverything },
        )
        if (showEverything) {
            Text(
                "Not what CatGuard is for, but these work exactly the same way - useful " +
                    "for testing, or for watching something other than an animal.",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            SupportedAnimals.otherGroups.forEach { group ->
                Text(
                    group.name.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                group.targets.forEach { target ->
                    AnimalRow(
                        emoji = target.emoji,
                        name = target.displayName,
                        note = target.note,
                        checked = target.label in selected,
                        onChange = { onToggle(target.label, it) },
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (selected.size == 1 && SupportedAnimals.cat.label in selected) {
            Text(
                "Cats only - the recommended setting.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            Text(
                "Watching: " + selected.joinToString(", ") { SupportedAnimals.displayNameFor(it) },
                fontSize = 11.sp,
                color = StatusColors.possible,
            )
        }
    }
}

@Composable
private fun ExpanderRow(
    label: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
        Text(
            "${if (expanded) "Hide" else "Show"} $label ($count)",
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun AnimalRow(
    emoji: String,
    name: String,
    note: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}

// ------------------------------------------------------------------- sound card

/**
 * Every available sound is listed and independently tickable, so the choice is
 * the user's: one bark, or every sound in the library layered together.
 */
@Composable
private fun DeterrentSoundCard(
    settings: AppSettings,
    audioReady: Boolean,
    audioMessage: String?,
    volumeWarning: String?,
    activeSoundName: String?,
    customSounds: List<DeterrentSound.Custom>,
    savedCombos: List<SoundCombo>,
    actions: SettingsActions,
) {
    val audio = settings.audio
    val selected = audio.effectiveSoundIds

    SettingsCard("Deterrent sound") {
        SwitchRow(
            title = "Play a sound",
            explanation = "Off still counts and logs encounters, silently.",
            checked = settings.guard.soundEnabled,
            onChange = actions.onSound,
        )

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = actions.onMaximumLoudness,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = StatusColors.barking,
                contentColor = Color.White,
            ),
        ) {
            Text("MAKE IT AS LOUD AS POSSIBLE", fontWeight = FontWeight.Bold)
        }
        Text(
            "Ticks every sound, layers them together, full volume on the alarm channel, " +
                "system volume pushed to maximum, 30 seconds. Everything below stays adjustable.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        SoundCombosSection(
            audio = audio,
            savedCombos = savedCombos,
            customSounds = customSounds,
            onApplyCombo = actions.onApplyCombo,
            onSaveCombo = actions.onSaveCombo,
            onDeleteCombo = actions.onDeleteCombo,
        )

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        Text(
            "Tick everything you want to play",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "${selected.size} selected. More than one is louder and harder for an animal " +
                "to get used to.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))

        SoundCategory.entries.forEach { category ->
            val sounds: List<DeterrentSound> = when (category) {
                SoundCategory.MINE -> customSounds
                else -> SoundLibrary.bundled.filter { it.category == category }
            }
            if (sounds.isEmpty()) return@forEach

            Text(
                category.displayName.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
            )
            sounds.forEach { sound ->
                SoundRow(
                    name = sound.displayName,
                    detail = "${sound.description}  ·  ${sound.durationMs / 1000}s clip",
                    checked = sound.id in selected,
                    onToggle = { actions.onToggleSound(sound.id, it) },
                    onDelete = if (sound is DeterrentSound.Custom) {
                        { actions.onDeleteSound(sound.id) }
                    } else {
                        null
                    },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = actions.onImportSound, modifier = Modifier.fillMaxWidth()) {
            Text("ADD YOUR OWN SOUND")
        }
        Text(
            "Pick an MP3, WAV, OGG or M4A under 30 seconds. It is copied into CatGuard, so it " +
                "keeps working even if you later move or delete the original.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        if (selected.size > 1) {
            Spacer(Modifier.height(10.dp))
            SwitchRow(
                title = "Play them all at once",
                explanation = "On: everything ticked sounds together - much louder. " +
                    "Off: one sound per visit, taking turns.",
                checked = audio.layerSounds,
                onChange = actions.onLayerSounds,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---- how long --------------------------------------------------------
        Text("How long it plays", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        SliderRow(
            title = "Duration",
            value = "${audio.effectiveDurationMs / 1000}s",
            explanation = "Short clips are looped to fill this. Up to a full minute.",
            sliderValue = audio.effectiveDurationMs.toFloat(),
            range = DeterrentAudioConfig.MIN_DURATION_MS.toFloat()..
                DeterrentAudioConfig.MAX_DURATION_MS.toFloat(),
            steps = 0,
            onChange = { actions.onDuration(it.toLong()) },
        )
        SwitchRow(
            title = "Stop early once the cat has gone",
            explanation = "Cuts the sound the moment the zone is clear. Keeps a long " +
                "duration useful without filling the house with noise every time.",
            checked = audio.stopWhenClear,
            onChange = actions.onStopWhenClear,
        )
        if (audio.effectiveDurationMs > 20_000 && !audio.stopWhenClear) {
            Text(
                "At ${audio.effectiveDurationMs / 1000}s with early stop off, this will play " +
                    "in full every time - including at night, for everyone in the house and " +
                    "next door. The cat will normally have left in the first second or two.",
                fontSize = 11.sp,
                color = StatusColors.possible,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp))

        // ---- loudness --------------------------------------------------------
        Text("Loudness", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        SliderRow(
            title = "Volume",
            value = "${(audio.volume * 100).roundToInt()}%",
            explanation = "Relative to the phone volume for the channel below.",
            sliderValue = audio.volume,
            range = 0.1f..1f,
            steps = 8,
            onChange = actions.onVolume,
        )

        Text("Audio channel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        DeterrentChannel.entries.forEach { channel ->
            RadioRow(
                label = channel.displayName,
                note = channel.description,
                selected = audio.channel == channel,
                onSelect = { actions.onChannel(channel) },
            )
        }

        SwitchRow(
            title = "Maximise volume",
            explanation = "Turns the chosen channel up to full while it plays, then puts it " +
                "straight back where you had it. The loudest option.",
            checked = audio.maximiseVolume,
            onChange = actions.onMaximiseVolume,
        )

        Spacer(Modifier.height(10.dp))
        Button(onClick = actions.onTestSound, modifier = Modifier.fillMaxWidth()) {
            Text("TEST SOUND")
        }
        Text(
            "A phone speaker has a hard ceiling. If it is still not loud enough for the " +
                "whole house, plug the phone into a Bluetooth or wired speaker - CatGuard " +
                "uses the normal audio output, so it follows automatically.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (activeSoundName != null) {
            Text(
                "Will play: $activeSoundName for ${audio.effectiveDurationMs / 1000}s",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (!audioReady && audioMessage != null) {
            Spacer(Modifier.height(6.dp))
            Text(audioMessage, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
        }
        if (volumeWarning != null) {
            Spacer(Modifier.height(6.dp))
            Text(volumeWarning, fontSize = 11.sp, color = StatusColors.possible)
        }
    }
}

@Composable
private fun SoundRow(
    name: String,
    detail: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Column(Modifier.weight(1f)) {
            Text(
                name,
                fontSize = 14.sp,
                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(detail, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Remove", fontSize = 12.sp) }
        }
    }
}

// -------------------------------------------------------------------- night card

@Composable
private fun NightCard(
    settings: AppSettings,
    torchAvailable: Boolean,
    actions: SettingsActions,
) {
    val night = settings.night

    SettingsCard("Night and low light") {
        Text(
            "A phone camera cannot see in real darkness, and CatGuard will not pretend " +
                "otherwise. These settings get the most out of whatever light there is.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(10.dp))

        SwitchRow(
            title = "Low-light capture",
            explanation = "Lets the sensor slow down and expose for longer in a dim room. " +
                "Costs nothing when it is bright. Recommended.",
            checked = night.lowLightCapture,
            onChange = actions.onLowLightCapture,
        )

        HorizontalDivider(Modifier.padding(vertical = 10.dp))

        Text("Camera light", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        if (!torchAvailable) {
            Text(
                "This camera has no flash, so the light options do nothing on this device.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(4.dp))
        TorchMode.entries.forEach { mode ->
            RadioRow(
                label = mode.displayName,
                note = mode.description,
                selected = night.torchMode == mode,
                onSelect = { actions.onTorchMode(mode) },
                enabled = torchAvailable,
            )
        }

        if (night.torchMode == TorchMode.NIGHT_HOURS) {
            Spacer(Modifier.height(6.dp))
            HourRow(
                label = "Light on from",
                hour = night.nightStartHour,
                onChange = { actions.onNightHours(it, night.nightEndHour) },
            )
            HourRow(
                label = "Light off at",
                hour = night.nightEndHour,
                onChange = { actions.onNightHours(night.nightStartHour, it) },
            )
        }

        if (night.torchMode != TorchMode.OFF) {
            Spacer(Modifier.height(6.dp))
            Text(
                "A torch left on for hours warms the phone noticeably and will shorten the " +
                    "life of an old battery. A cheap lamp on a timer is gentler on the phone " +
                    "and lights the room far better.",
                fontSize = 11.sp,
                color = StatusColors.possible,
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 10.dp))

        SwitchRow(
            title = "Warn when it is too dark",
            explanation = "Shows a warning on the main screen when the scene is too dim for " +
                "detection to be reliable.",
            checked = night.warnWhenDark,
            onChange = actions.onWarnWhenDark,
        )
    }
}

@Composable
private fun HourRow(label: String, hour: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            Modifier.weight(1f),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        TextButton(onClick = { onChange((hour + 23) % 24) }) { Text("-") }
        Text(
            "%02d:00".format(hour),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        TextButton(onClick = { onChange((hour + 1) % 24) }) { Text("+") }
    }
}

// ----------------------------------------------------------------- shared pieces

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: String,
    explanation: String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column {
        Row {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(value, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = sliderValue.coerceIn(range.start, range.endInclusive),
            onValueChange = onChange,
            valueRange = range,
            steps = steps.coerceAtLeast(0),
        )
        Text(explanation, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    explanation: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(explanation, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RadioRow(
    label: String,
    note: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontSize = 14.sp,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(note, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 12.sp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
