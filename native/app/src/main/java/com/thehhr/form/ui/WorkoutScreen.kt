@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.thehhr.form.FormStore
import com.thehhr.form.data.ActiveWorkout
import com.thehhr.form.data.RoutineItem
import com.thehhr.form.data.SetRow
import com.thehhr.form.data.WorkoutLogic
import kotlinx.coroutines.delay

@Composable
fun WorkoutTab(vm: FormStore, nav: Nav) {
    val w = vm.workout
    if (w == null) StartWorkout(vm, nav) else ActiveWorkoutView(vm, nav, w)
}

@Composable
private fun StartWorkout(vm: FormStore, nav: Nav) {
    val today = vm.todaysRoutine()
    val routines = vm.data.routines.filter { it.liked }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
        item {
            Card(
                Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Today", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                            Text(today?.name ?: "Rest day", style = MaterialTheme.typography.headlineMediumEmphasized)
                            Text(
                                if (today != null) "${today.items.size} exercises" else "Nothing scheduled. Start any routine, or plan your week.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            Modifier.size(72.dp).clip((if (today != null) MaterialShapes.Cookie9Sided else MaterialShapes.Sunny).toShape())
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) { Icon(if (today != null) Icons.Rounded.PlayArrow else Icons.Rounded.Timer, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onPrimary) }
                    }
                    if (today != null) Button(
                        onClick = { vm.startWorkout(today) }, enabled = today.items.isNotEmpty(), shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
                    ) { Text("Start workout", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
                }
            }
        }
        item {
            OutlinedButton(onClick = { nav.push(Route.Library()) }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text("Browse exercises")
            }
        }
        item { SectionTitle("Liked routines", action = { TextButton(onClick = { nav.push(Route.Plans) }) { Text("All routines") } }) }
        if (routines.isEmpty()) item { EmptyNote("Like a routine (♥) in Routines & week to pin it here.") }
        itemsIndexed(routines, key = { _, r -> r.id }) { i, r ->
            GroupedItem(
                i, routines.size,
                supporting = { Text("${r.items.size} exercises") },
                trailing = {
                    FilledTonalButton(onClick = { vm.startWorkout(r) }, enabled = r.items.isNotEmpty(), shapes = ButtonDefaults.shapes()) { Text("Start") }
                },
            ) { Text(r.name, style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun ActiveWorkoutView(vm: FormStore, nav: Nav, w: ActiveWorkout) {
    val routine = vm.data.routines.firstOrNull { it.id == w.routineId } ?: return
    val entries = WorkoutLogic.entries(w, routine)
    val target = WorkoutLogic.nextTarget(w, routine)
    val counted = entries.filter { it.item.exerciseId !in w.skipped }
    val doneCount = counted.count { w.complete(it.item.exerciseId) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var finishing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    RestCue(vm, w)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            // Room for the floating toolbar so the last card is never hidden behind it.
            contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(fmtTime(w.elapsedMs(now)), style = MaterialTheme.typography.displaySmallEmphasized, modifier = Modifier.weight(1f))
                        Text("$doneCount/${counted.size}", style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(if (w.paused) "Paused" else "exercises done", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                    LinearWavyProgressIndicator(
                        progress = { if (counted.isEmpty()) 0f else doneCount / counted.size.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
            if (w.rest != null) item(key = "rest") { RestCard(vm, w, now) }
            items(entries, key = { it.item.exerciseId }) { entry ->
                val id = entry.item.exerciseId
                val isTarget = target?.exerciseId == id
                ExerciseCard(
                    vm, nav, w, entry.item, entry.added,
                    open = isTarget || id in expanded, isTarget = isTarget,
                    onToggleOpen = { expanded = if (id in expanded) expanded - id else expanded + id },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = { if (target != null && !w.paused) { com.thehhr.form.Haptics.setDone(view); vm.logNextSet() } },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Rounded.Check, target?.let { "Log next set of ${exTitle(vm.exercise(it.exerciseId))}" } ?: "All done")
                }
            },
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            modifier = Modifier.align(Alignment.BottomCenter).offset(y = -FloatingToolbarDefaults.ScreenOffset),
        ) {
            IconButton(onClick = { nav.push(Route.Library(Pick.Extra)) }) { Icon(Icons.Rounded.Add, "Add exercise") }
            IconButton(onClick = vm::togglePause) { Icon(if (w.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (w.paused) "Resume" else "Pause") }
            IconButton(onClick = { finishing = true }) { Icon(Icons.Rounded.Flag, "Finish workout") }
        }
    }

    if (finishing) AlertDialog(
        onDismissRequest = { finishing = false },
        icon = { Icon(Icons.Rounded.Flag, null) },
        title = { Text("Finish workout?") },
        text = { Text("Checked sets are saved to your training log. Discard removes every set from this session.") },
        confirmButton = {
            TextButton(onClick = {
                finishing = false
                vm.finishWorkout()?.let { (d, t) -> vm.message = "Workout complete $d/$t · ${fmtTime(w.elapsedMs())}" }
            }) { Text("Finish") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { finishing = false; vm.discardWorkout(); vm.message = "Workout discarded" },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Discard") }
                TextButton(onClick = { finishing = false }) { Text("Keep going") }
            }
        },
    )
}

@Composable
private fun RestCard(vm: FormStore, w: ActiveWorkout, now: Long) {
    val rest = w.rest ?: return
    val left = rest.pausedRemaining ?: (rest.endsAt - now)
    if (left < -15 * 60_000) return // forgotten rest: hide the stale overtime
    val over = left < 0
    val bg = if (over) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val fg = if (over) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(
                    progress = { if (over) 1f else (left / (rest.total * 1000f)).coerceIn(0f, 1f) },
                    modifier = Modifier.size(64.dp), color = fg, trackColor = fg.copy(alpha = 0.2f),
                )
                Icon(Icons.Rounded.Timer, null, Modifier.size(22.dp))
            }
            val setTimer = rest.type == "set"
            Column(Modifier.weight(1f)) {
                val label = when {
                    setTimer -> "Set ${(rest.setIndex ?: 0) + 1}"
                    // Once rest runs out the clock keeps going as the time spent in the set (the web app's "set" mode).
                    over -> "Set"
                    rest.warmup -> "Warm-up"; rest.type == "exercise" -> "Next"; else -> "Rest"
                }
                Text(label, style = MaterialTheme.typography.labelLargeEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fmtTime(kotlin.math.abs(left) + if (over) 0 else 999), style = MaterialTheme.typography.displaySmallEmphasized)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (setTimer) {
                    FilledTonalButton(onClick = { vm.completeSetTimer() }, shapes = ButtonDefaults.shapes()) { Text("Done") }
                    IconButton(onClick = vm::skipRest) { Icon(Icons.Rounded.Close, "Cancel set timer") }
                } else {
                    FilledTonalButton(onClick = { vm.extendRest(30) }, shapes = ButtonDefaults.shapes()) { Text("+30s") }
                    IconButton(onClick = vm::skipRest) { Icon(Icons.Rounded.SkipNext, if (over) "Dismiss set clock" else "Skip rest") }
                }
            }
        }
    }
}

/** Beep + vibrate once when the rest countdown reaches zero. */
@Composable
private fun RestCue(vm: FormStore, w: ActiveWorkout) {
    val rest = w.rest
    val context = LocalContext.current
    LaunchedEffect(rest?.endsAt, rest?.pausedRemaining) {
        if (rest == null || rest.pausedRemaining != null) return@LaunchedEffect
        val wait = rest.endsAt - System.currentTimeMillis()
        if (wait < -5_000) return@LaunchedEffect // ended long ago (app reopened later)
        delay(wait.coerceAtLeast(0))
        // The notification alarm stays quiet while the app is visible, so this is the only cue then.
        if (com.thehhr.form.AppVisibility.visible) com.thehhr.form.Haptics.restEnd(context, vm.data.prefs.restSound)
        // A finished set timer checks its set. Only while visible: in the background the alarm must do it,
        // or completing here would reschedule the alarm and swallow the "Set done" alert.
        if (rest.type == "set" && com.thehhr.form.AppVisibility.visible) vm.completeSetTimer(rest.endsAt)
    }
}

@Composable
private fun ExerciseCard(
    vm: FormStore, nav: Nav, w: ActiveWorkout, item: RoutineItem, added: Boolean,
    open: Boolean, isTarget: Boolean, onToggleOpen: () -> Unit, modifier: Modifier = Modifier,
) {
    val id = item.exerciseId
    val ex = vm.exercise(id)
    val rows = w.rows(id)
    val skipped = id in w.skipped
    val complete = w.complete(id)
    val units = vm.data.prefs.units
    Card(
        modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.clickable(onClick = onToggleOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseImage(ex, size = if (open) 72.dp else 52.dp, animated = open)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                if (isTarget) Text("Up next", style = MaterialTheme.typography.labelMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                Text(exTitle(ex), style = if (open) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = when {
                    skipped -> "Skipped"
                    complete -> "Done"
                    else -> "${rows.count { it.done }} of ${rows.size} sets" + if (added) " · added" else ""
                }
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = if (complete) FormColors.Positive else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (complete) Box(
                Modifier.size(32.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(FormColors.Positive),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Check, "Done", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.surface) }
        }
        AnimatedVisibility(open && !skipped) {
            Column(Modifier.padding(start = 10.dp, end = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetHeader(item, units.weight, units.distance)
                rows.forEachIndexed { i, row -> SetRowView(vm, id, i, row, item, units.weight, units.distance) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.addSet(id) }) { Icon(Icons.Rounded.Add, null); Text("Set") }
                    TextButton(onClick = { vm.removeSet(id) }, enabled = rows.size > 1 && rows.any { !it.done }) { Icon(Icons.Rounded.Remove, null); Text("Set") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { nav.push(Route.Library(Pick.Replace(id))) }) { Icon(Icons.Rounded.SwapHoriz, "Replace exercise") }
                }
            }
        }
        if (open || skipped) Row(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp)) {
            TextButton(onClick = { vm.toggleSkip(id) }) { Text(if (skipped) "Unskip" else "Skip") }
            TextButton(onClick = { nav.push(Route.Detail(id)) }) { Text("Details") }
            Spacer(Modifier.weight(1f))
            if (!isTarget && !complete && !skipped) TextButton(onClick = { vm.jumpTo(id) }) { Text("Do next") }
        }
    }
}

@Composable
private fun SetRowView(vm: FormStore, exId: String, index: Int, row: SetRow, item: RoutineItem, wUnit: String, dUnit: String) {
    fun set(r: SetRow) = vm.updateRow(exId, index, r)
    val view = LocalView.current
    val rest = vm.workout?.rest
    val timing = rest?.type == "set" && rest.exerciseId == exId && rest.setIndex == index
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (item.timed) {
            val sec = item.unitOrDefault == "sec"
            StepperField(row.duration, if (sec) "seconds" else "minutes", if (sec) 5.0 else 1.0, 0.0..(if (sec) 3600.0 else 600.0),
                { set(row.copy(duration = it)) }, Modifier.weight(1f), enabled = !timing)
            if (item.distance) StepperField(row.distance, dUnit, if (dUnit == "mi") 0.25 else 0.5, 0.0..500.0, { set(row.copy(distance = it)) }, Modifier.weight(1f))
        } else {
            StepperField(row.reps.toDouble(), "reps", 1.0, 1.0..100.0, { set(row.copy(reps = it.toInt())) }, Modifier.weight(1f))
        }
        if (item.weighted && !(item.timed && item.distance)) {
            StepperField(row.weight ?: 0.0, wUnit, if (wUnit == "lb") 5.0 else 2.5, 0.0..2000.0, { set(row.copy(weight = it)) }, Modifier.weight(1f))
        }
        // One button per set: shows the set number, ▶ to start a timed set's countdown, ✓ once done.
        FilledIconToggleButton(
            checked = row.done || timing,
            onCheckedChange = {
                when {
                    row.done -> vm.toggleRow(exId, index)
                    timing -> { com.thehhr.form.Haptics.setDone(view); vm.completeSetTimer() }
                    item.timed -> vm.startSetTimer(exId, index)
                    else -> { com.thehhr.form.Haptics.setDone(view); vm.toggleRow(exId, index) }
                }
            },
            shapes = IconButtonDefaults.toggleableShapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            when {
                row.done -> Icon(Icons.Rounded.Check, "Set ${index + 1} done")
                timing -> Icon(Icons.Rounded.Check, "Finish set ${index + 1} now")
                item.timed -> Icon(Icons.Rounded.PlayArrow, "Start timer for set ${index + 1}")
                else -> Text("${index + 1}", style = MaterialTheme.typography.titleMediumEmphasized,
                    modifier = Modifier.semantics { contentDescription = "Mark set ${index + 1} done" })
            }
        }
    }
}

/** Unit labels once per card, aligned over the stepper columns. */
@Composable
private fun SetHeader(item: RoutineItem, wUnit: String, dUnit: String) {
    val labels = buildList {
        if (item.timed) { add(if (item.unitOrDefault == "sec") "sec" else "min"); if (item.distance) add(dUnit) } else add("reps")
        if (item.weighted && !(item.timed && item.distance)) add(wUnit)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEach { Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        Spacer(Modifier.width(IconButtonDefaults.mediumContainerSize().width))
    }
}
