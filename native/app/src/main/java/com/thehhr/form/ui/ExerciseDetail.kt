@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thehhr.form.FormStore
import com.thehhr.form.data.Exercise
import com.thehhr.form.data.Limits
import com.thehhr.form.data.Muscles
import com.thehhr.form.data.ProgressLog
import com.thehhr.form.data.WorkoutLogic
import com.thehhr.form.data.jsTitle
import com.thehhr.form.data.round1
import com.thehhr.form.data.round4
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Composable
fun ExerciseDetailScreen(vm: FormStore, nav: Nav, id: String) {
    val e = vm.exercise(id)
    val d = vm.data
    val units = d.prefs.units
    val logs = d.logs.filter { it.exerciseId == id }.sortedWith(compareByDescending<ProgressLog> { it.date }.thenByDescending { it.createdAt })
    val pr = WorkoutLogic.record(d.logs, id)
    var routineMenu by remember { mutableStateOf(false) }
    var logging by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            FormTopBar(exTitle(e), scroll, subtitle = e?.let { jsTitle("${it.target} · ${it.equipment}") }, onBack = nav::pop) {
                LikeButton(id in d.liked) { vm.toggleLike(id) }
            }
        },
    ) { pad ->
        // No spacedBy: grouped history rows bring their own gap; other items pad themselves.
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                // Short, wide hero like the web app; the demo itself stays square in the middle.
                Box(
                    Modifier.fillMaxWidth().height(154.dp).clip(MaterialTheme.shapes.extraLarge).background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { ExerciseImage(e, Modifier.fillMaxHeight().aspectRatio(1f), animated = true) }
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOfNotNull(e?.category, e?.muscleGroup?.takeIf { it.isNotEmpty() }).plus(e?.secondary.orEmpty()).distinct()
                        .forEach { Tag(jsTitle(it)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { routineMenu = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Routine")
                        }
                        DropdownMenu(expanded = routineMenu, onDismissRequest = { routineMenu = false }) {
                            if (d.routines.isEmpty()) DropdownMenuItem(text = { Text("No routines yet") }, onClick = { routineMenu = false })
                            d.routines.forEach { r ->
                                DropdownMenuItem(text = { Text(r.name) }, onClick = {
                                    routineMenu = false; vm.addToRoutine(r.id, id); vm.message = "Added to ${r.name}"
                                })
                            }
                        }
                    }
                    Button(onClick = { logging = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Log progress") }
                }
            }
            if (pr != null) item {
                Card(Modifier.padding(top = 12.dp), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(MaterialShapes.SoftBurst.toShape()).background(MaterialTheme.colorScheme.tertiary), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.EmojiEvents, null, tint = MaterialTheme.colorScheme.onTertiary)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Personal record", style = MaterialTheme.typography.labelLarge)
                            Text("${num(pr)} ${units.weight}", style = MaterialTheme.typography.headlineSmallEmphasized)
                        }
                    }
                }
            }
            if (e != null) {
                item { SectionTitle("Muscles") }
                item {
                    Card(shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                        MuscleMap(Muscles.forExercise(e), d.profile.sex, Modifier.padding(16.dp).height(220.dp))
                    }
                }
            }
            if (e?.description?.isNotEmpty() == true) item { Text(e.description, Modifier.padding(top = 12.dp)) }
            if (e?.instructions?.isNotEmpty() == true) {
                item { SectionTitle("How to") }
                itemsIndexed(e.instructions) { i, step ->
                    Row(Modifier.padding(bottom = 12.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(28.dp).clip(MaterialShapes.Circle.toShape()).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(step, Modifier.weight(1f))
                    }
                }
            }
            item { SectionTitle("History") }
            if (logs.isEmpty()) item { EmptyNote("No logs yet") }
            itemsIndexed(logs, key = { _, l -> l.id }) { i, log -> LogRow(vm, log, i, logs.size, showName = false) }
        }
    }
    if (logging && e != null) LogProgressSheet(vm, e) { logging = false }
}

/**
 * The web app's progress panel: date, reps or timed (sec/min) mode, per-set values, optional weight /
 * distance, and a note. Save rules follow saveProgressLog in www/app.js.
 */
@Composable
private fun LogProgressSheet(vm: FormStore, e: Exercise, onClose: () -> Unit) {
    val units = vm.data.prefs.units
    val logs = vm.data.logs.filter { it.exerciseId == e.id }.sortedWith(compareByDescending<ProgressLog> { it.date }.thenByDescending { it.createdAt })
    val lastTimed = logs.firstOrNull { it.timed }
    val lastStrength = logs.firstOrNull { !it.timed }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var timed by remember { mutableStateOf(e.timedCardio || (lastStrength == null && lastTimed != null)) }
    var sec by remember { mutableStateOf(lastTimed?.durUnit?.let { it == "sec" } ?: !e.timedCardio) }
    var showWeight by remember { mutableStateOf(e.equipment != "body weight" || (lastStrength?.setWeights?.any { it > 0 } == true)) }
    var showDistance by remember { mutableStateOf(e.timedCardio || lastTimed?.setDistances != null) }
    val lastW = WorkoutLogic.lastWeight(vm.data.logs, e.id, includeTimed = true)
    var reps by remember { mutableStateOf(lastStrength?.setReps?.takeIf { it.isNotEmpty() } ?: List(3) { lastStrength?.reps ?: 10 }) }
    var weights by remember { mutableStateOf(lastStrength?.setWeights?.takeIf { it.isNotEmpty() }?.let { w -> List(reps.size) { w.getOrElse(it) { w.last() } } } ?: List(reps.size) { lastW }) }
    // Durations kept in the display unit while editing; stored as minutes on save.
    var durations by remember {
        mutableStateOf(lastTimed?.setDurations?.map { if (sec) round1(it * 60) else round1(it) }?.takeIf { it.isNotEmpty() } ?: List(lastTimed?.intervals ?: 1) { if (sec) 30.0 else 20.0 })
    }
    var distances by remember { mutableStateOf(lastTimed?.setDistances?.takeIf { it.isNotEmpty() } ?: List(durations.size) { 0.0 }) }
    var timedWeights by remember { mutableStateOf(List(durations.size) { lastW }) }
    var notes by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }

    fun <T> resize(list: List<T>, n: Int, fill: T) = List(n) { list.getOrElse(it) { list.lastOrNull() ?: fill } }
    val count = if (timed) durations.size else reps.size
    fun setCount(n: Int) {
        if (timed) { durations = resize(durations, n, if (sec) 30.0 else 20.0); distances = resize(distances, n, 0.0); timedWeights = resize(timedWeights, n, lastW) }
        else { reps = resize(reps, n, 10); weights = resize(weights, n, lastW) }
    }

    fun save() {
        val now = System.currentTimeMillis()
        val base = ProgressLog(id = now.toString(), exerciseId = e.id, date = date.toString(), notes = notes.trim().take(Limits.NOTES), createdAt = now)
        val log = if (timed) {
            val mins = durations.map { (if (sec) round4(it / 60) else it).coerceIn(0.0, Limits.DURATION) }
            val dists = if (showDistance) distances.map { round1(it.coerceIn(0.0, Limits.DISTANCE)) } else emptyList()
            val totalDur = mins.sum(); val totalDist = dists.sum()
            if (totalDur <= 0 && totalDist <= 0) { vm.message = if (showDistance) "Add a duration or a distance" else "Add a duration"; return }
            val tw = timedWeights.map { round1(it.coerceIn(0.0, Limits.WEIGHT)) }
            val weighted = showWeight && tw.any { it > 0 }
            base.copy(intervals = count, durUnit = if (sec) "sec" else "min",
                setDurations = mins.takeIf { totalDur > 0 }, setDistances = dists.takeIf { totalDist > 0 },
                weight = if (weighted) tw.last { it > 0 } else null, setWeights = tw.takeIf { weighted })
        } else {
            val w = weights.map { round1(it.coerceIn(0.0, Limits.WEIGHT)) }
            val weighted = showWeight && w.any { it > 0 }
            base.copy(sets = count, reps = reps.first(), setReps = reps,
                weight = if (weighted) w.last { it > 0 } else null, setWeights = w.takeIf { weighted })
        }
        vm.addLog(log)
        vm.message = "Progress saved"
        onClose()
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(exTitle(e), style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickingDate = true }, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                    Text(if (date == LocalDate.now()) "Today" else displayDate(date.toString()), maxLines = 1)
                }
                ChoiceGroup(listOf(false to "Reps", true to "Timed"), timed, { timed = it }, Modifier.weight(1f))
            }
            if (timed) ChoiceGroup(listOf(true to "Seconds", false to "Minutes"), sec, { new ->
                if (new != sec) durations = durations.map { if (new) round1(it * 60) else round1(it / 60) }
                sec = new
            })
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Stepper(if (timed) "intervals" else "sets", count.toDouble(), 1.0, 1.0..Limits.SETS.toDouble(), { setCount(it.toInt()) })
                Pill(showWeight, { showWeight = !showWeight }, label = { Text("Weight") })
                if (timed) Pill(showDistance, { showDistance = !showDistance }, label = { Text("Distance") })
            }
            // Column header, then one row per set.
            val cols = buildList {
                if (timed) { add(if (sec) "sec" else "min"); if (showDistance) add(units.distance) } else add("reps")
                if (showWeight) add(units.weight)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Spacer(Modifier.width(24.dp))
                cols.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
            }
            for (i in 0 until count) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", Modifier.width(24.dp), style = MaterialTheme.typography.labelLargeEmphasized, textAlign = TextAlign.Center)
                    if (timed) {
                        StepperField(durations[i], if (sec) "seconds" else "minutes", if (sec) 5.0 else 1.0, 0.0..(if (sec) 36000.0 else 600.0),
                            { v -> durations = durations.toMutableList().also { it[i] = v } }, Modifier.weight(1f))
                        if (showDistance) StepperField(distances[i], units.distance, if (units.distance == "mi") 0.25 else 0.5, 0.0..500.0,
                            { v -> distances = distances.toMutableList().also { it[i] = v } }, Modifier.weight(1f))
                        if (showWeight) StepperField(timedWeights[i], units.weight, if (units.weight == "lb") 5.0 else 2.5, 0.0..2000.0,
                            { v -> timedWeights = timedWeights.toMutableList().also { it[i] = v } }, Modifier.weight(1f))
                    } else {
                        StepperField(reps[i].toDouble(), "reps", 1.0, 1.0..100.0, { v -> reps = reps.toMutableList().also { it[i] = v.toInt() } }, Modifier.weight(1f))
                        if (showWeight) StepperField(weights[i], units.weight, if (units.weight == "lb") 5.0 else 2.5, 0.0..2000.0,
                            { v -> weights = weights.toMutableList().also { it[i] = v } }, Modifier.weight(1f))
                    }
                }
            }
            OutlinedTextField(notes, { notes = it.take(Limits.NOTES) }, Modifier.fillMaxWidth(), placeholder = { Text("Note (optional)") }, singleLine = true)
            // Dismissing the sheet cancels, so one full-width action is enough.
            Button(onClick = ::save, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)) {
                Text("Save", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
            }
        }
    }

    if (pickingDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc // no future logs, as the web app
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker speaks UTC midnight; reading it in the local zone would shift west-of-UTC users a day back.
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
