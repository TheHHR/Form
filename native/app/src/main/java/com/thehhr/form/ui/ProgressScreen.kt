@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import com.thehhr.form.data.jsTitle
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
    // The web config may say "all"; Year replaced it natively.
    var scope by rememberSaveable { mutableStateOf(vm.data.prefs.defaultView.let { if (it == "all") "year" else it }.takeIf { it in listOf("week", "month", "year") } ?: "week") }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var shownDays by rememberSaveable { mutableIntStateOf(14) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) } // "YYYY-MM-DD" or, in Year, "YYYY-MM"
    val weekStart = vm.data.prefs.weekStart
    val today = LocalDate.now()
    val byDate = remember(logs) { logs.groupBy { it.date } }

    val (from, to) = when (scope) {
        "week" -> {
            val back = (today.dayOfWeek.value % 7 - weekStart + 7) % 7
            val s = today.minusDays(back.toLong()).plusWeeks(offset.toLong()); s to s.plusDays(6)
        }
        "month" -> { val m = YearMonth.from(today).plusMonths(offset.toLong()); m.atDay(1) to m.atEndOfMonth() }
        else -> { val y = today.year + offset; LocalDate.of(y, 1, 1) to LocalDate.of(y, 12, 31) }
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

    val periodLabel = when {
        scope == "year" && offset == 0 -> "This year"
        scope == "year" -> "${from.year}"
        scope == "week" && offset == 0 -> "This week"
        scope == "week" -> "${from.format(DateTimeFormatter.ofPattern("MMM d"))} – ${to.format(DateTimeFormatter.ofPattern("MMM d"))}"
        offset == 0 -> "This month"
        from.year == today.year -> from.format(DateTimeFormatter.ofPattern("MMMM"))
        else -> from.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
    }
    val trainedDays = rangeLogs.map { it.date }.distinct().size
    val exerciseCount = rangeLogs.map { it.exerciseId }.distinct().size

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
        item { ChoiceGroup(listOf("week" to "Week", "month" to "Month", "year" to "Year"), scope, { scope = it; offset = 0; selected = null }, Modifier.padding(vertical = 8.dp)) }
        item {
            // Header like M3 Expressive health apps: period title with its arrows, one big number, a plain-language line.
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Tap the title to jump back to the current period (plain text: no press effect).
                Text(selLabel ?: periodLabel, Modifier.weight(1f).animateContentSize()
                    .clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null,
                        enabled = offset != 0 || selected != null, onClickLabel = "Go to current $scope") { offset = 0; selected = null },
                    style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = 1)
                run {
                    FilledTonalIconButton(onClick = { offset--; selected = null }, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous $scope")
                    }
                    FilledTonalIconButton(onClick = { offset++; selected = null }, enabled = offset < 0, shapes = IconButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next $scope")
                    }
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                androidx.compose.animation.AnimatedContent(sets, label = "sets", transitionSpec = {
                    (androidx.compose.animation.slideInVertically { it / 2 } + androidx.compose.animation.fadeIn()) togetherWith
                        (androidx.compose.animation.slideOutVertically { -it / 2 } + androidx.compose.animation.fadeOut())
                }) { n -> Text("%,d".format(n), style = MaterialTheme.typography.displayMediumEmphasized) }
                Text(if (sets == 1) " set" else " sets", Modifier.padding(bottom = 10.dp, start = 4.dp), style = MaterialTheme.typography.titleMediumEmphasized)
            }
            Text(
                when {
                    trainedDays == 0 -> "Nothing logged here yet."
                    selected?.length == 10 -> "$exerciseCount exercise${if (exerciseCount == 1) "" else "s"} on this day."
                    else -> "You trained on $trainedDays day${if (trainedDays == 1) "" else "s"}" + if (selected == null && offset == 0) " this $scope." else "."
                },
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Box(Modifier.padding(top = 16.dp, bottom = 8.dp)) {
                // Week bars, month calendar and year bars cross-fade while the card resizes, instead of snapping.
                val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                val size = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
                androidx.compose.animation.AnimatedContent(scope, transitionSpec = {
                    (androidx.compose.animation.fadeIn(fade) togetherWith androidx.compose.animation.fadeOut(fade)) using androidx.compose.animation.SizeTransform(clip = false) { _, _ -> size }
                }, label = "chart") { sc ->
                when (sc) {
                    "week" -> {
                        val days = (0..6).map { from.plusDays(it.toLong()) }
                        Bars(days.map { d -> byDate[d.toString()].orEmpty().sumOf(::setCount) },
                            days.map { it.format(DateTimeFormatter.ofPattern("EEEEE")) },
                            days.map { displayDate(it.toString()) }, days.indexOf(today),
                            days.indexOfFirst { it.toString() == selected }) { select(days[it].toString()) }
                    }
                    "month" -> Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        Column(Modifier.padding(12.dp)) { MonthGrid(YearMonth.from(from), weekStart, byDate.keys, today, selected) { select(it) } }
                    }
                    else -> {
                        val months = (1..12).map { YearMonth.of(from.year, it) }
                        Bars(months.map { m -> logs.filter { it.date.startsWith(m.toString()) }.sumOf(::setCount) },
                            months.map { it.month.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault()) },
                            months.map { it.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy")) },
                            months.indexOf(YearMonth.from(today)), months.indexOfFirst { it.toString() == selected }) { select(months[it].toString()) }
                    }
                }
                }
            }
        }
        item {
            // Numbers as a segmented list: label left, big value right.
            val rows = listOf(
                (if (sessions == 1) "Session" else "Sessions") to "$sessions",
                "Exercises" to "$exerciseCount",
                "Volume (${vm.data.prefs.units.weight})" to compact(volume),
            )
            Column(Modifier.padding(top = 8.dp)) {
                rows.forEachIndexed { i, (label, value) ->
                    GroupedItem(i, rows.size, trailing = { Text(value, style = MaterialTheme.typography.headlineSmallEmphasized) }) {
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        item { SectionTitle("Muscles") }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                if (heat.isEmpty()) EmptyNote("Nothing trained in this range.")
                else Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MuscleMap(heat, vm.data.profile.sex, Modifier.height(240.dp))
                    // The web dashboard's breakdown: sets and exercises per body part, with a bar against the busiest.
                    val groups = rangeLogs.groupBy { vm.exercise(it.exerciseId)?.category ?: "other" }
                        .map { (cat, l) -> Triple(cat, l.sumOf(::setCount), l.map { it.exerciseId }.distinct().size) }
                        .sortedByDescending { it.second }
                    val top = groups.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
                    groups.forEach { (cat, s, ex) ->
                        Column(Modifier.semantics(mergeDescendants = true) {}) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(jsTitle(cat), Modifier.weight(1f), style = MaterialTheme.typography.titleSmallEmphasized)
                                Text("$s set${if (s == 1) "" else "s"} · $ex exercise${if (ex == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(progress = eased(s / top.toFloat()).let { v -> { v } }, Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp),
                                gapSize = 0.dp, drawStopIndicator = {})
                        }
                    }
                }
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

/** Sets in a log: intervals for timed logs. */
private fun setCount(l: ProgressLog) = if (l.timed) l.intervals ?: 0 else l.sets ?: 0

/**
 * Tappable pill bars (no track) with a right-hand scale. The selected column turns the complement colour with a
 * cookie badge just above it; today's label takes the accent. Pressing a bar squeezes it and squares its corners
 * (the M3 Expressive press morph). Empty columns are dots and can't be selected.
 */
@Composable
private fun Bars(values: List<Int>, labels: List<String>, names: List<String>, today: Int, selected: Int, onTap: (Int) -> Unit) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val pick = MaterialTheme.colorScheme.tertiary
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val guide = MaterialTheme.colorScheme.outlineVariant
    val chart = 168.dp
    val head = 32.dp // room above the tallest bar for the badge
    Row(Modifier.fillMaxWidth()) {
        Box(Modifier.weight(1f)) {
            // Guide line at the top value.
            Canvas(Modifier.fillMaxWidth().padding(top = head).height(1.dp)) {
                drawLine(guide, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            }
            Row(Modifier.fillMaxWidth()) {
                values.forEachIndexed { i, v ->
                    val isSel = i == selected
                    val enabled = v > 0
                    val h = androidx.compose.animation.core.animateFloatAsState(v / max.toFloat(), MaterialTheme.motionScheme.defaultSpatialSpec(), label = "bar").value
                    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    val corner by androidx.compose.animation.core.animateIntAsState(if (pressed) 30 else 50, MaterialTheme.motionScheme.fastSpatialSpec(), label = "corner")
                    val squeeze by androidx.compose.animation.core.animateFloatAsState(if (pressed) 0.9f else 1f, MaterialTheme.motionScheme.fastSpatialSpec(), label = "squeeze")
                    val color by androidx.compose.animation.animateColorAsState(if (!enabled) empty else if (isSel) pick else bar,
                        MaterialTheme.motionScheme.defaultEffectsSpec(), label = "barColor")
                    Column(
                        Modifier.weight(1f).clickable(interaction, indication = null, enabled = enabled, onClickLabel = "Select") { onTap(i) }
                            .semantics { contentDescription = "${names[i]}, $v sets"; this.selected = isSel },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Column(Modifier.fillMaxWidth().height(chart + head), verticalArrangement = Arrangement.Bottom,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.animation.AnimatedVisibility(isSel && enabled,
                                enter = androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn(),
                                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()) {
                                Box(Modifier.padding(bottom = 4.dp).size(24.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(pick),
                                    contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiary) }
                            }
                            Box(
                                Modifier.fillMaxWidth(0.72f)
                                    // At least as tall as wide, so an empty or tiny value is a dot, not a sliver.
                                    .layout { m, c ->
                                        val w = c.maxWidth
                                        val bh = if (enabled) maxOf((chart.toPx() * h).toInt(), w) else w
                                        val p = m.measure(androidx.compose.ui.unit.Constraints.fixed(w, bh))
                                        layout(w, bh) { p.place(0, 0) }
                                    }
                                    .graphicsLayer { scaleX = squeeze; scaleY = squeeze; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f) }
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(corner))
                                    .background(color),
                            )
                        }
                        Text(labels[i], Modifier.padding(top = 8.dp),
                            style = if (isSel || i == today) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                            color = when { isSel -> pick; i == today -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                    }
                }
            }
        }
        // Scale: each label is centred on its line, the top on the dashed guide, 0 on the baseline.
        Column(Modifier.padding(start = 6.dp, top = head - 8.dp).height(chart + 16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("$max", Modifier.height(16.dp), style = MaterialTheme.typography.labelMediumEmphasized)
            if (max >= 4) Text("${max / 2}", Modifier.height(16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("0", Modifier.height(16.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                .then(if (day == today && !sel) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                .background(when { sel -> MaterialTheme.colorScheme.primary; has -> MaterialTheme.colorScheme.primaryContainer; else -> MaterialTheme.colorScheme.surface.copy(alpha = 0f) })
                                .clickable(enabled = has) { onDay(key) }
                                .semantics { contentDescription = displayDate(key) + if (has) ", trained" else "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${day.dayOfMonth}", style = MaterialTheme.typography.labelLarge,
                                color = when { sel -> MaterialTheme.colorScheme.onPrimary; has -> MaterialTheme.colorScheme.primary
                                    day == today -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.onSurfaceVariant },
                                fontWeight = if (day == today) androidx.compose.ui.text.font.FontWeight.Bold else null)
                        }
                    }
                }
            }
        }
    }
}
