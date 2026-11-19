@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thehhr.form.AppViewModel
import com.thehhr.form.data.ActiveWorkout
import com.thehhr.form.data.RoutineItem
import com.thehhr.form.data.SetRow
import com.thehhr.form.data.WorkoutLogic
import kotlinx.coroutines.delay

@Composable
fun WorkoutTab(vm: AppViewModel, nav: Nav) {
    val w = vm.workout
    if (w == null) StartWorkout(vm, nav) else ActiveWorkoutView(vm, nav, w)
}

@Composable
private fun StartWorkout(vm: AppViewModel, nav: Nav) {
    val today = vm.todaysRoutine()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Today", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (today != null) {
                        Text(today.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("${today.items.size} exercises", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { vm.startWorkout(today) }, enabled = today.items.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Start workout")
                        }
                    } else {
                        Text("Rest day", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("Nothing scheduled. Start any routine below, or set your week in Plans.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { nav.push(Route.Library()) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(8.dp)); Text("Browse exercises")
            }
        }
        item { SectionTitle("Routines") }
        if (vm.data.routines.isEmpty()) item { EmptyNote("No routines yet. Create one in Plans.") }
        items(vm.data.routines, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text("${r.items.size} exercises", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(onClick = { vm.startWorkout(r) }, enabled = r.items.isNotEmpty()) { Text("Start") }
                }
            }
        }
    }
}

@Composable
private fun ActiveWorkoutView(vm: AppViewModel, nav: Nav, w: ActiveWorkout) {
    val routine = vm.data.routines.firstOrNull { it.id == w.routineId } ?: return
    val entries = WorkoutLogic.entries(w, routine)
    val target = WorkoutLogic.nextTarget(w, routine)
    val counted = entries.filter { it.item.exerciseId !in w.skipped }
    val doneCount = counted.count { w.complete(it.item.exerciseId) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(250) } }
    RestCue(vm, w, now)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fmtTime(w.elapsedMs(now)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                Text("$doneCount of ${counted.size} exercises done" + if (w.paused) " · paused" else "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            FilledIconToggleButton(checked = w.paused, onCheckedChange = { vm.togglePause() }) {
                Icon(if (w.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (w.paused) "Resume" else "Pause")
            }
        }
        LinearWavyProgressIndicator(progress = { if (counted.isEmpty()) 0f else doneCount / counted.size.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        AnimatedVisibility(w.rest != null) { w.rest?.let { RestCard(vm, w, now) } }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.item.exerciseId }) { entry ->
                val id = entry.item.exerciseId
                val isTarget = target?.exerciseId == id
                ExerciseCard(
                    vm, nav, w, entry.item, entry.added,
                    open = isTarget || id in expanded,
                    isTarget = isTarget,
                    onToggleOpen = { expanded = if (id in expanded) expanded - id else expanded + id },
                )
            }
            item {
                OutlinedButton(onClick = { nav.push(Route.Library(Pick.Extra)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercise")
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { confirm = "discard" }, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Discard") }
                    FilledTonalButton(onClick = { confirm = "finish" }, modifier = Modifier.weight(1f)) { Text("Finish") }
                }
            }
        }
        Button(
            onClick = { vm.logNextSet() }, enabled = target != null && !w.paused,
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
        ) {
            Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp))
            Text(target?.let { "Log set · ${exTitle(vm.exercise(it.exerciseId))}" } ?: "All done", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }

    when (confirm) {
        "finish" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Finish workout?") },
            text = { Text("Checked sets are saved to your training log.") },
            confirmButton = { TextButton(onClick = {
                confirm = null
                vm.finishWorkout()?.let { (d, t) -> vm.message = "Workout complete $d/$t · ${fmtTime(w.elapsedMs())}" }
            }) { Text("Finish") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Keep going") } },
        )
        "discard" -> AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Discard workout?") },
            text = { Text("Removes every set logged in this session.") },
            confirmButton = { TextButton(onClick = { confirm = null; vm.discardWorkout(); vm.message = "Workout discarded" }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun RestCard(vm: AppViewModel, w: ActiveWorkout, now: Long) {
    val rest = w.rest ?: return
    val left = rest.pausedRemaining ?: (rest.endsAt - now)
    val over = left < 0
    Card(
        Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp),
        colors = CardDefaults.cardColors(containerColor = if (over) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularWavyProgressIndicator(progress = { if (over) 1f else (left / (rest.total * 1000f)).coerceIn(0f, 1f) }, modifier = Modifier.size(56.dp))
                Icon(Icons.Rounded.Timer, null, Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f)) {
                val label = when { rest.warmup -> "Warm-up"; rest.type == "exercise" -> "Next exercise"; else -> "Rest" }
                Text(if (over) "$label · over" else label, style = MaterialTheme.typography.labelLarge,
                    color = if (over) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
                Text((if (over) "+" else "") + fmtTime(kotlin.math.abs(left) + if (over) 0 else 999),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold,
                    color = if (over) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
            }
            TextButton(onClick = { vm.extendRest(30) }) { Text("+30s") }
            IconButton(onClick = { vm.skipRest() }) { Icon(Icons.Rounded.SkipNext, "Skip rest") }
        }
    }
}

/** Beep + vibrate once when the rest countdown reaches zero. */
@Composable
private fun RestCue(vm: AppViewModel, w: ActiveWorkout, now: Long) {
    val rest = w.rest
    val context = LocalContext.current
    LaunchedEffect(rest?.endsAt, rest?.pausedRemaining) {
        if (rest == null || rest.pausedRemaining != null) return@LaunchedEffect
        val wait = rest.endsAt - System.currentTimeMillis()
        if (wait < -5_000) return@LaunchedEffect // ended long ago (app reopened later)
        delay(wait.coerceAtLeast(0))
        restAlert(context, vm.data.prefs.restSound)
    }
}

private fun restAlert(context: Context, sound: Boolean) {
    runCatching {
        val vib = (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        vib.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 120, 180), -1))
    }
    if (sound) runCatching {
        val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
        tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 400)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 600)
    }
}

@Composable
private fun ExerciseCard(
    vm: AppViewModel, nav: Nav, w: ActiveWorkout, item: RoutineItem, added: Boolean,
    open: Boolean, isTarget: Boolean, onToggleOpen: () -> Unit,
) {
    val id = item.exerciseId
    val ex = vm.exercise(id)
    val rows = w.rows(id)
    val skipped = id in w.skipped
    val complete = w.complete(id)
    val units = vm.data.prefs.units
    Card(
        Modifier.fillMaxWidth().animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTarget) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.clickable(onClick = onToggleOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseImage(ex, size = 56.dp, animated = open)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(exTitle(ex), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val status = when {
                    skipped -> "Skipped"
                    complete -> "Done"
                    else -> "${rows.count { it.done }}/${rows.size} sets" + if (added) " · added" else ""
                }
                Text(status, style = MaterialTheme.typography.bodySmall,
                    color = if (complete) FormColors.Positive else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (complete) Icon(Icons.Rounded.Check, "Done", tint = FormColors.Positive)
        }
        AnimatedVisibility(open && !skipped) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { i, row -> SetRowView(vm, id, i, row, item, units.weight, units.distance) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { vm.addSet(id) }) { Icon(Icons.Rounded.Add, null); Text("Set") }
                    TextButton(onClick = { vm.removeSet(id) }, enabled = rows.size > 1 && rows.any { !it.done }) { Icon(Icons.Rounded.Remove, null); Text("Set") }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { nav.push(Route.Library(Pick.Replace(id))) }) { Icon(Icons.Rounded.SwapHoriz, "Replace exercise") }
                    if (!isTarget && !complete) TextButton(onClick = { vm.jumpTo(id) }) { Text("Do next") }
                }
            }
        }
        if (open || skipped) Row(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
            TextButton(onClick = { vm.toggleSkip(id) }) { Text(if (skipped) "Unskip" else "Skip") }
            TextButton(onClick = { nav.push(Route.Detail(id)) }) { Text("Details") }
        }
    }
}

@Composable
private fun SetRowView(vm: AppViewModel, exId: String, index: Int, row: SetRow, item: RoutineItem, wUnit: String, dUnit: String) {
    val wStep = if (wUnit == "lb") 5.0 else 2.5
    val dStep = if (dUnit == "mi") 0.25 else 0.5
    fun set(r: SetRow) = vm.updateRow(exId, index, r)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${index + 1}", Modifier.width(20.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (item.timed) {
                val sec = item.unitOrDefault == "sec"
                val step = if (sec) 5.0 else 1.0
                Stepper(if (sec) "sec" else "min", num(row.duration),
                    { set(row.copy(duration = (row.duration - step).coerceAtLeast(0.0))) },
                    { set(row.copy(duration = (row.duration + step).coerceAtMost(if (sec) 3600.0 else 600.0))) })
                if (item.distance) Stepper(dUnit, num(row.distance),
                    { set(row.copy(distance = (row.distance - dStep).coerceAtLeast(0.0))) },
                    { set(row.copy(distance = (row.distance + dStep).coerceAtMost(500.0))) })
            } else {
                Stepper("reps", "${row.reps}", { set(row.copy(reps = (row.reps - 1).coerceAtLeast(1))) }, { set(row.copy(reps = (row.reps + 1).coerceAtMost(100))) })
            }
            if (item.weighted && !(item.timed && item.distance)) Stepper(wUnit, num(row.weight ?: 0.0),
                { set(row.copy(weight = ((row.weight ?: 0.0) - wStep).coerceAtLeast(0.0))) },
                { set(row.copy(weight = ((row.weight ?: 0.0) + wStep).coerceAtMost(2000.0))) })
        }
        FilledIconToggleButton(checked = row.done, onCheckedChange = { vm.toggleRow(exId, index) }) {
            Icon(if (row.done) Icons.Rounded.Check else Icons.Rounded.Close, if (row.done) "Set ${index + 1} done" else "Mark set ${index + 1} done")
        }
    }
}
