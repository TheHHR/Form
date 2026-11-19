package com.thehhr.form.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thehhr.form.AppViewModel
import com.thehhr.form.data.ProgressLog
import com.thehhr.form.data.WorkoutLogic
import com.thehhr.form.data.jsRound
import com.thehhr.form.data.vaultDateHeading
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ProgressTab(vm: AppViewModel, nav: Nav) {
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var shownDays by rememberSaveable { mutableIntStateOf(14) }
    val logs = vm.data.logs
    val weekStart = vm.data.prefs.weekStart
    val today = LocalDate.now()
    val back = (today.dayOfWeek.value % 7 - weekStart + 7) % 7
    val start = today.minusDays(back.toLong()).plusWeeks(weekOffset.toLong())
    val days = (0..6).map { start.plusDays(it.toLong()) }
    val byDate = logs.groupBy { it.date }
    val volumes = days.map { d -> byDate[d.toString()].orEmpty().sumOf(WorkoutLogic::volume) }
    val weekLogs = days.flatMap { byDate[it.toString()].orEmpty() }
    val sessions = days.count { byDate.containsKey(it.toString()) }
    val sets = weekLogs.sumOf { if (it.timed) it.intervals ?: 0 else it.sets ?: 0 }
    val units = vm.data.prefs.units
    val dates = byDate.keys.sortedDescending()

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { weekOffset-- }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous week") }
                Text(if (weekOffset == 0) "This week" else "${start.format(DateTimeFormatter.ofPattern("MMM d"))} – ${days.last().format(DateTimeFormatter.ofPattern("MMM d"))}",
                    Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { weekOffset++ }, enabled = weekOffset < 0) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next week") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Sessions", "$sessions", Modifier.weight(1f))
                Stat("Sets", "$sets", Modifier.weight(1f))
                Stat("Volume", "${jsRound(volumes.sum())} ${units.weight}", Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Volume by day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val max = volumes.max().coerceAtLeast(1.0)
                    val bar = MaterialTheme.colorScheme.primary
                    val track = MaterialTheme.colorScheme.surfaceContainerHighest
                    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp).semantics {
                        contentDescription = days.zip(volumes).joinToString { (d, v) -> "${d.dayOfWeek.name.take(3)} ${jsRound(v)}" }
                    }) {
                        val slot = size.width / 7
                        val w = slot * 0.5f
                        volumes.forEachIndexed { i, v ->
                            val x = slot * i + (slot - w) / 2
                            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                            val h = (v / max).toFloat() * size.height
                            if (h > 0) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        days.forEach { d ->
                            Text(d.format(DateTimeFormatter.ofPattern("EEEEE")), Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall, color = if (d == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item { SectionTitle("History") }
        if (dates.isEmpty()) item { EmptyNote("Finish a workout or log progress from an exercise to see it here.") }
        dates.take(shownDays).forEach { date ->
            item(key = "d-$date") { Text(vaultDateHeading(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
            items(byDate[date].orEmpty().sortedBy(ProgressLog::createdAt), key = { it.id }) { log -> LogRow(vm, log) }
        }
        if (dates.size > shownDays) item { TextButton(onClick = { shownDays += 14 }, modifier = Modifier.fillMaxWidth()) { Text("Show more") } }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
