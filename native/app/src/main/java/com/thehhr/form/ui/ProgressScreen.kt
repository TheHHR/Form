@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thehhr.form.FormStore
import com.thehhr.form.data.Muscles
import com.thehhr.form.data.ProgressLog
import com.thehhr.form.data.WorkoutLogic
import com.thehhr.form.data.jsRound
import com.thehhr.form.data.vaultDateHeading
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** Week / Month / All, as the web dashboard. Stats, chart and muscle heatmap follow the scope. */
@Composable
fun ProgressTab(vm: FormStore, nav: Nav) {
    var scope by rememberSaveable { mutableStateOf(vm.data.prefs.defaultView.takeIf { it in listOf("week", "month", "all") } ?: "week") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var shownDays by rememberSaveable { mutableIntStateOf(14) }
    var selectedDay by rememberSaveable { mutableStateOf<String?>(null) }
    val logs = vm.data.logs
    val weekStart = vm.data.prefs.weekStart
    val today = LocalDate.now()
    val byDate = remember(logs) { logs.groupBy { it.date } }
    val units = vm.data.prefs.units

    // The scope's date range.
    val (from, to) = when (scope) {
        "week" -> {
            val back = (today.dayOfWeek.value % 7 - weekStart + 7) % 7
            val s = today.minusDays(back.toLong()).plusWeeks(offset.toLong()); s to s.plusDays(6)
        }
        "month" -> { val m = YearMonth.from(today).plusMonths(offset.toLong()); m.atDay(1) to m.atEndOfMonth() }
        else -> (logs.minOfOrNull { it.date }?.let(LocalDate::parse) ?: today) to today
    }
    val scopeLogs = remember(logs, from, to) { logs.filter { it.date >= from.toString() && it.date <= to.toString() } }
    val sessions = scopeLogs.map { it.date }.distinct().size
    val sets = scopeLogs.sumOf { if (it.timed) it.intervals ?: 0 else it.sets ?: 0 }
    val volume = scopeLogs.sumOf(WorkoutLogic::volume)
    val heat = remember(scopeLogs) { Muscles.heat(scopeLogs, vm::exercise) }
    val dates = (selectedDay?.let { listOf(it) } ?: byDate.keys.sortedDescending()).filter { byDate.containsKey(it) }

    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
        item {
            ChoiceGroup(listOf("week" to "Week", "month" to "Month", "all" to "All"), scope, { scope = it; offset = 0; selectedDay = null }, Modifier.padding(vertical = 8.dp))
        }
        if (scope != "all") item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { offset--; selectedDay = null }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous $scope") }
                val label = when {
                    scope == "week" && offset == 0 -> "This week"
                    scope == "week" -> "${from.format(DateTimeFormatter.ofPattern("MMM d"))} – ${to.format(DateTimeFormatter.ofPattern("MMM d"))}"
                    offset == 0 -> "This month"
                    else -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
                }
                Text(label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { offset++; selectedDay = null }, enabled = offset < 0) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next $scope") }
            }
        }
        item {
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Sessions", "$sessions", Modifier.weight(1f))
                Stat("Sets", "$sets", Modifier.weight(1f))
                Stat("Volume (${units.weight})", compact(volume), Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(top = 4.dp), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp)) {
                    when (scope) {
                        "week" -> {
                            val days = (0..6).map { from.plusDays(it.toLong()) }
                            Text("Volume by day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Bars(days.map { d -> byDate[d.toString()].orEmpty().sumOf(WorkoutLogic::volume) },
                                days.map { it.format(DateTimeFormatter.ofPattern("EEEEE")) }, days.indexOf(today))
                        }
                        "month" -> MonthGrid(YearMonth.from(from), weekStart, byDate.keys, today, selectedDay) { d ->
                            selectedDay = if (selectedDay == d) null else d
                        }
                        else -> {
                            // Up to the last 12 months with logs, volume per month.
                            val months = (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
                            Text("Volume by month", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Bars(months.map { m -> logs.filter { it.date.startsWith(m.toString()) }.sumOf(WorkoutLogic::volume) },
                                months.map { it.month.name.take(1) }, months.lastIndex)
                        }
                    }
                }
            }
        }
        item { SectionTitle("Muscles " + when (scope) { "week" -> "this week"; "month" -> "this month"; else -> "overall" }) }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                if (heat.isEmpty()) EmptyNote("Log a workout to see which muscles you trained.")
                else MuscleMap(heat, vm.data.profile.sex, Modifier.padding(16.dp).height(240.dp))
            }
        }
        item { SectionTitle(selectedDay?.let { "History · ${vaultDateHeading(it)}" } ?: "History") }
        if (dates.isEmpty()) item { EmptyNote("Finish a workout or log progress from an exercise to see it here.") }
        dates.take(shownDays).forEach { date ->
            if (selectedDay == null) item(key = "d-$date") {
                Text(vaultDateHeading(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
            }
            val dayLogs = byDate[date].orEmpty().sortedBy(ProgressLog::createdAt)
            itemsIndexed(dayLogs, key = { _, l -> l.id }) { i, log -> LogRow(vm, log, i, dayLogs.size) }
        }
        if (dates.size > shownDays) item { TextButton(onClick = { shownDays += 14 }, modifier = Modifier.fillMaxWidth()) { Text("Show more") } }
    }
}

private fun compact(v: Double) = if (v >= 10_000) "${jsRound(v / 100) / 10.0}k".replace(".0k", "k") else "${jsRound(v)}"

@Composable
private fun Bars(values: List<Double>, labels: List<String>, highlight: Int) {
    val max = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val bar = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(Modifier.fillMaxWidth().height(120.dp).padding(top = 12.dp).semantics {
        contentDescription = labels.zip(values).joinToString { (l, v) -> "$l ${jsRound(v)}" }
    }) {
        val slot = size.width / values.size
        val w = slot * 0.5f
        values.forEachIndexed { i, v ->
            val x = slot * i + (slot - w) / 2
            drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
            val h = (v / max).toFloat() * size.height
            if (h > 0) drawRoundRect(bar, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        labels.forEachIndexed { i, l ->
            Text(l, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
                color = if (i == highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Calendar for one month; days with logs are filled, tap a day to filter the history. */
@Composable
private fun MonthGrid(month: YearMonth, weekStart: Int, logged: Set<String>, today: LocalDate, selected: String?, onDay: (String) -> Unit) {
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value % 7 - weekStart + 7) % 7
    val cells = List(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
    Row(Modifier.fillMaxWidth()) {
        (0..6).forEach { i ->
            val d = java.time.DayOfWeek.of(((weekStart + i + 6) % 7) + 1)
            Text(d.name.take(1), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            (0..6).forEach { i ->
                val day = week.getOrNull(i)
                Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                    if (day != null) {
                        val key = day.toString()
                        val has = key in logged
                        val sel = key == selected
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(when { sel -> MaterialTheme.colorScheme.primary; has -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f) })
                                .clickable(enabled = has) { onDay(key) }
                                .semantics { contentDescription = day.format(DateTimeFormatter.ofPattern("MMMM d")) + if (has) ", trained" else "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelLarge,
                                color = when { sel -> MaterialTheme.colorScheme.onPrimary; has -> MaterialTheme.colorScheme.primary
                                    day == today -> MaterialTheme.colorScheme.onSurface; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
