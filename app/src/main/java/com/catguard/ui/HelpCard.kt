package com.catguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First-run guidance, shown over the preview until dismissed.
 *
 * The app is meant to be set up once on a phone with no PC attached, so the
 * three things that actually have to happen - check the speaker, aim the camera,
 * press start - are on screen rather than in a document the user will not read.
 */
@Composable
fun HelpCard(
    audioReady: Boolean,
    onTestSound: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xF210161F)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "SET UP IN 3 STEPS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = StatusColors.monitoring,
            )
            Spacer(Modifier.height(8.dp))

            HelpStep(1, "Turn the phone volume up, then tap TEST SOUND below. You should hear a dog bark.")
            HelpStep(2, "Prop the phone up facing the area the cats come into. Keep it on a charger.")
            HelpStep(3, "Tap START GUARD. Leave it; you can lock the screen.")

            Spacer(Modifier.height(6.dp))
            Text(
                "It needs light. If you cannot clearly see a cat in the preview, " +
                    "neither can the app - leave a lamp on at night.",
                fontSize = 11.sp,
                color = StatusColors.possible,
            )

            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onTestSound,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusColors.monitoring,
                        contentColor = Color(0xFF07231A),
                    ),
                ) {
                    Text("TEST SOUND", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                TextButton(onClick = onDismiss) { Text("Got it") }
            }

            if (!audioReady) {
                Text(
                    "Sound is still loading - give it a second.",
                    fontSize = 11.sp,
                    color = Color(0xFFAEBBCB),
                )
            }
        }
    }
}

@Composable
private fun HelpStep(number: Int, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            "$number",
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            color = StatusColors.monitoring,
            modifier = Modifier
                .background(Color(0x334DD0A7), RoundedCornerShape(4.dp))
                .padding(horizontal = 7.dp, vertical = 1.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = Color.White, lineHeight = 17.sp)
    }
}
