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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
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
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thehhr.form.FormStore
import com.thehhr.form.data.Muscles
import com.thehhr.form.data.ProgressLog
import com.thehhr.form.data.WorkoutLogic
import com.thehhr.form.data.jsRound
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Week / Month / All, as the web dashboard. Tapping a day (week, month) or a month (all) narrows the
 * stats, muscle heatmap and history to it; tapping it again clears the selection.
 */
@Composable
fun ProgressTab(vm: FormStore, nav: Nav) {
    val logs = vm.data.logs
    if (logs.isEmpty()) {
        CenteredEmpty("No training logged yet", "Finish a workout or log progress from an exercise and it shows up here.")
        return
    }
    var scope by rememberSaveable { mutableStateOf(vm.data.prefs.defaultView.takeIf { it in listOf("week", "month", "all") } ?: "week") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var shownDays by rememberSaveable { mutableIntStateOf(14) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) } // "YYYY-MM-DD" or, in All, "YYYY-MM"
    val weekStart = vm.data.prefs.weekStart
    val today = LocalDate.now()
    val byDate = remember(logs) { logs.groupBy { it.date } }

    val (from, to) = when (scope) {
        "week" -> {
            val back = (today.dayOfWeek.value % 7 - weekStart + 7) % 7
            val s = today.minusDays(back.toLong()).plusWeeks(offset.toLong()); s to s.plusDays(6)
        }
        "month" -> { val m = YearMonth.from(today).plusMonths(offset.toLong()); m.atDay(1) to m.atEndOfMonth() }
        else -> (logs.minOf { it.date }.let(LocalDate::parse)) to today
    }
    // The selection narrows the range everything below reads from.
    val (selFrom, selTo) = when {
        selected == null -> from.toString() to to.toString()
        selected!!.length == 7 -> YearMonth.parse(selected).let { it.atDay(1).toString() to it.atEndOfMonth().toString() }
        else -> selected!! to selected!!
    }
    val rangeLogs = remember(logs, selFrom, selTo) { logs.filter { it.date in selFrom..selTo } }
    val sessions = rangeLogs.map { it.date }.distinct().size
    val sets = rangeLogs.sumOf { if (it.timed) it.intervals ?: 0 else it.sets ?: 0 }
    val volume = rangeLogs.sumOf(WorkoutLogic::volume)
    val heat = remember(rangeLogs) { Muscles.heat(rangeLogs, vm::exercise) }
    val dates = byDate.keys.filter { it in selFrom..selTo }.sortedDescending()
    val selLabel = selected?.let { if (it.length == 7) YearMonth.parse(it).atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy")) else displayDate(it) }
    fun select(key: String) { selected = if (selected == key) null else key; shownDays = 14 }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
        item { ChoiceGroup(listOf("week" to "Week", "month" to "Month", "all" to "All"), scope, { scope = it; offset = 0; selected = null }, Modifier.padding(vertical = 8.dp)) }
        if (scope != "all") item {
            val label = when {
                scope == "week" && offset == 0 -> "This week"
                scope == "week" -> "${from.format(DateTimeFormatter.ofPattern("MMM d"))} – ${to.format(DateTimeFormatter.ofPattern("MMM d"))}"
                offset == 0 -> "This month"
                from.year == today.year -> from.format(DateTimeFormatter.ofPattern("MMMM"))
                else -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            }
            PeriodNav(label, scope, { offset--; selected = null }, { offset++; selected = null }, offset < 0)
        }
        item {
            // One tonal hero card instead of three boxes; the numbers follow the selection.
            Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(20.dp)) {
                    Text(selLabel ?: "Totals", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        StatValue("$sessions", if (sessions == 1) "session" else "sessions", Modifier.weight(1f))
                        StatValue("$sets", "sets", Modifier.weight(1f))
                        StatValue(compact(volume), "${vm.data.prefs.units.weight} volume", Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = MaterialTheme.shapes.extraLarge) {
                Column(Modifier.padding(16.dp)) {
                    when (scope) {
                        "week" -> {
                            val days = (0..6).map { from.plusDays(it.toLong()) }
                            Bars(days.map { d -> byDate[d.toString()].orEmpty().sumOf(WorkoutLogic::volume) },
                                days.map { it.format(DateTimeFormatter.ofPattern("EEEEE")) },
                                days.map { displayDate(it.toString()) }, days.indexOf(today),
                                days.indexOfFirst { it.toString() == selected }) { select(days[it].toString()) }
                        }
                        "month" -> MonthGrid(YearMonth.from(from), weekStart, byDate.keys, today, selected) { select(it) }
                        else -> {
                            val months = (11 downTo 0).map { YearMonth.from(today).minusMonths(it.toLong()) }
                            Bars(months.map { m -> logs.filter { it.date.startsWith(m.toString()) }.sumOf(WorkoutLogic::volume) },
                                months.map { it.month.name.take(1) }, months.map { it.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy")) },
                                months.lastIndex, months.indexOfFirst { it.toString() == selected }) { select(months[it].toString()) }
                        }
                    }
                }
            }
        }
        item { SectionTitle("Muscles") }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                if (heat.isEmpty()) EmptyNote("Nothing trained in this range.")
                else MuscleMap(heat, vm.data.profile.sex, Modifier.padding(16.dp).height(240.dp))
            }
        }
        item { SectionTitle("History") }
        if (dates.isEmpty()) item { EmptyNote("Nothing logged in this range.") }
        dates.take(shownDays).forEach { date ->
            item(key = "d-$date") {
                Text(displayDate(date), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp))
            }
            val dayLogs = byDate[date].orEmpty().sortedBy(ProgressLog::createdAt)
            itemsIndexed(dayLogs, key = { _, l -> l.id }) { i, log -> LogRow(vm, log, i, dayLogs.size) { nav.push(Route.Detail(log.exerciseId)) } }
        }
        if (dates.size > shownDays) item { TextButton(onClick = { shownDays += 14 }, modifier = Modifier.fillMaxWidth()) { Text("Show more") } }
    }
}

/** Full-height centered message for tabs with nothing to show yet. */
@Composable
fun CenteredEmpty(title: String, body: String) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Insights, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLargeEmphasized, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StatValue(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.headlineMediumEmphasized, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

private fun compact(v: Double) = if (v >= 10_000) "${jsRound(v / 100) / 10.0}k".replace(".0k", "k") else "${jsRound(v)}"

/** Tappable bars: each column selects its day / month. */
@Composable
private fun Bars(values: List<Double>, labels: List<String>, names: List<String>, today: Int, selected: Int, onTap: (Int) -> Unit) {
    val max = values.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
    val bar = MaterialTheme.colorScheme.primary
    val dim = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(Modifier.fillMaxWidth()) {
        values.forEachIndexed { i, v ->
            val isSel = i == selected
            Column(
                Modifier.weight(1f).clip(MaterialTheme.shapes.small).clickable { onTap(i) }
                    .semantics { contentDescription = "${names[i]}, volume ${jsRound(v)}"; this.selected = isSel }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                    val w = size.width * 0.5f
                    val x = (size.width - w) / 2
                    drawRoundRect(track, Offset(x, 0f), Size(w, size.height), CornerRadius(w / 2))
                    val h = (v / max).toFloat() * size.height
                    if (h > 0) drawRoundRect(if (selected < 0 || isSel) bar else dim, Offset(x, size.height - h), Size(w, h), CornerRadius(w / 2))
                }
                Text(labels[i], Modifier.padding(top = 4.dp), style = if (isSel) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelSmall,
                    color = if (isSel || i == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Calendar for one month; days with logs are filled, tap a day to select it. */
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
                                .semantics { contentDescription = displayDate(key) + if (has) ", trained" else "" },
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
