package com.catguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catguard.data.DayStats
import com.catguard.data.DetectionEvent
import com.catguard.detection.SupportedAnimals
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    events: List<DetectionEvent>,
    days: List<DayStats>,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val dayFormat = remember { SimpleDateFormat("EEE d MMM", Locale.getDefault()) }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "History",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "${events.size} confirmed encounters, last 14 days",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (events.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
            TextButton(onClick = onBack) { Text("Done") }
        }

        Spacer(Modifier.height(8.dp))

        if (events.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing recorded yet.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (days.isNotEmpty()) {
                item(key = "daily-summary") {
                    DailySummaryCard(days = days, dayFormat = dayFormat)
                }
                item(key = "log-heading") {
                    Text(
                        "EVERY ENCOUNTER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 2.dp),
                    )
                }
            }

            items(events, key = { it.timestampMs }) { event ->
                EventRow(event = event, timeFormat = timeFormat, dayFormat = dayFormat)
            }
        }
    }
}

/**
 * Per-day totals: how many animals were confirmed and how many of those actually
 * triggered the deterrent. The two differ when the sound is off, when playback
 * fails, or when an encounter lands inside a cooldown.
 */
@Composable
private fun DailySummaryCard(days: List<DayStats>, dayFormat: SimpleDateFormat) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "DAY BY DAY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))

            Row {
                Text(
                    "Day",
                    Modifier.weight(1f),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Detected",
                    Modifier.width(72.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Barked",
                    Modifier.width(56.dp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))

            days.take(14).forEach { day ->
                Row(
                    Modifier.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            labelForDay(day.dayStartMs, dayFormat),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (day.byLabel.size > 1 || day.byLabel.firstOrNull()?.first != "cat") {
                            Text(
                                day.byLabel.joinToString("  ") { (label, count) ->
                                    "${SupportedAnimals.displayNameFor(label)} $count"
                                },
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        "${day.detections}",
                        Modifier.width(72.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusColors.confirmed,
                    )
                    Text(
                        "${day.barks}",
                        Modifier.width(56.dp),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = StatusColors.barking,
                    )
                }
            }

            val totalDetections = days.sumOf { it.detections }
            val totalBarks = days.sumOf { it.barks }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row {
                Text(
                    "Total",
                    Modifier.weight(1f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$totalDetections",
                    Modifier.width(72.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$totalBarks",
                    Modifier.width(56.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun EventRow(
    event: DetectionEvent,
    timeFormat: SimpleDateFormat,
    dayFormat: SimpleDateFormat,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                SupportedAnimals.byLabel(event.label)?.emoji ?: "🐾",
                fontSize = 20.sp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    timeFormat.format(Date(event.timestampMs)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    labelForDay(event.timestampMs, dayFormat),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${SupportedAnimals.displayNameFor(event.label)}  ${(event.confidence * 100).toInt()}%",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (event.deterrentTriggered) "Bark triggered" else "No sound",
                    fontSize = 11.sp,
                    color = if (event.deterrentTriggered) {
                        StatusColors.barking
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** "Today" and "Yesterday" read better than a date for the two most recent days. */
private fun labelForDay(dayStartMs: Long, dayFormat: SimpleDateFormat): String {
    val startOfToday = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayMs = 24L * 60 * 60 * 1000
    return when {
        dayStartMs >= startOfToday -> "Today"
        dayStartMs >= startOfToday - dayMs -> "Yesterday"
        else -> dayFormat.format(Date(dayStartMs))
    }
}
