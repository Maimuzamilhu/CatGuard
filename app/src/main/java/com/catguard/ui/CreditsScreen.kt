package com.catguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catguard.deterrent.SoundLibrary

/**
 * Attribution for the bundled sounds.
 *
 * The clips are Creative Commons BY-SA, which requires crediting the authors
 * wherever the work is distributed, so this is part of the app rather than a
 * file in the repository that a user would never see.
 */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Credits",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onBack) { Text("Done") }
        }

        Spacer(Modifier.height(8.dp))

        CreditCard("Deterrent sounds") {
            Text(
                "The bundled dog recordings come from Wikimedia Commons and are used under " +
                    "Creative Commons Attribution-ShareAlike licences. Each was loudness-" +
                    "normalised, downmixed to mono and re-encoded for use here; the sustained " +
                    "clip was also trimmed. Because the originals are ShareAlike, these modified " +
                    "audio files carry the same licence.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            SoundLibrary.bundled.forEach { sound ->
                Column(Modifier.padding(vertical = 5.dp)) {
                    Text(
                        sound.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        sound.credit,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text(
                "Full details, including links to the original files, are in CREDITS.md in the " +
                    "project.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CreditCard("Your own sounds") {
            Text(
                "Sounds you import are copied into CatGuard's private storage on this phone. " +
                    "They are never uploaded or shared, and you are responsible for having the " +
                    "right to use them.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CreditCard("Detection model") {
            Text(
                "EfficientDet-Lite0, trained on the COCO dataset and published by Google for " +
                    "TensorFlow Lite. Licensed under the Apache License 2.0. It runs entirely " +
                    "on this device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        CreditCard("Libraries") {
            Text(
                "AndroidX, Jetpack Compose, CameraX and TensorFlow Lite Task Vision, all under " +
                    "the Apache License 2.0.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CreditCard(title: String, content: @Composable () -> Unit) {
    Card(
        Modifier
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
