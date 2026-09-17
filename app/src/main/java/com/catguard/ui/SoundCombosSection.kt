package com.catguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catguard.deterrent.DeterrentAudioConfig
import com.catguard.deterrent.DeterrentSound
import com.catguard.deterrent.SoundCombo
import com.catguard.deterrent.SoundLibrary

/**
 * Saved sound combinations.
 *
 * Built-ins give a starting point; anything the user builds by ticking sounds can
 * be saved under a name and switched back to in one tap.
 */
@Composable
fun SoundCombosSection(
    audio: DeterrentAudioConfig,
    savedCombos: List<SoundCombo>,
    customSounds: List<DeterrentSound.Custom>,
    onApplyCombo: (SoundCombo) -> Unit,
    onSaveCombo: (String) -> Unit,
    onDeleteCombo: (String) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }

    Column {
        Text(
            "Combinations",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            "Tap one to switch to it, or build your own below and save it.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        (SoundCombo.BUILT_IN + savedCombos).forEach { combo ->
            ComboRow(
                combo = combo,
                active = combo.matches(audio),
                soundNames = describe(combo, customSounds),
                onApply = { onApplyCombo(combo) },
                onDelete = if (combo.builtIn) null else {
                    { onDeleteCombo(combo.id) }
                },
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showSaveDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("SAVE CURRENT SELECTION AS A COMBINATION")
        }
        if (savedCombos.size >= SoundCombo.MAX_USER_COMBOS) {
            Text(
                "You have ${savedCombos.size} saved. Saving another replaces the oldest.",
                fontSize = 11.sp,
                color = StatusColors.possible,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }

    if (showSaveDialog) {
        SaveComboDialog(
            suggestedName = suggestName(audio, customSounds),
            onConfirm = { name ->
                onSaveCombo(name)
                showSaveDialog = false
            },
            onDismiss = { showSaveDialog = false },
        )
    }
}

@Composable
private fun ComboRow(
    combo: SoundCombo,
    active: Boolean,
    soundNames: String,
    onApply: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (active) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                },
            )
            .border(
                width = if (active) 1.dp else 0.dp,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onApply)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    combo.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                if (active) {
                    Spacer(Modifier.width(8.dp))
                    Text("IN USE", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                soundNames,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${if (combo.layered) "together" else "in turn"}  ·  ${combo.durationMs / 1000}s",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Delete", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun SaveComboDialog(
    suggestedName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(suggestedName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name this combination") },
        text = {
            Column {
                Text(
                    "The sounds you have ticked, whether they play together, and the " +
                        "duration will all be saved.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= SoundCombo.MAX_NAME_LENGTH) name = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Human-readable list of what a combo actually plays. */
private fun describe(combo: SoundCombo, customSounds: List<DeterrentSound.Custom>): String {
    val names = combo.soundIds.mapNotNull { id ->
        SoundLibrary.bundledById(id)?.displayName
            ?: customSounds.firstOrNull { it.id == id }?.displayName
    }
    return when {
        names.isEmpty() -> "No sounds available"
        names.size <= 3 -> names.joinToString(", ")
        else -> names.take(2).joinToString(", ") + " and ${names.size - 2} more"
    }
}

/** A sensible default name so the dialog is one tap for most people. */
private fun suggestName(
    audio: DeterrentAudioConfig,
    customSounds: List<DeterrentSound.Custom>,
): String {
    val ids = audio.effectiveSoundIds
    val names = ids.mapNotNull { id ->
        SoundLibrary.bundledById(id)?.displayName
            ?: customSounds.firstOrNull { it.id == id }?.displayName
    }
    return when {
        names.size == 1 -> names.first()
        names.size >= SoundLibrary.bundled.size -> "Everything"
        names.size > 1 -> "${names.size} sounds, ${audio.effectiveDurationMs / 1000}s"
        else -> "My combination"
    }
}
