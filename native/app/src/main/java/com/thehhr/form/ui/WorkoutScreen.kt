@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.systemBars

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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
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
        item { SectionTitle("Liked routines", action = { TextButton(onClick = { nav.selectTab(Tab.Plans) }) { Text("All routines") } }) }
        if (routines.isEmpty()) item { EmptyNote("Like a routine (♥) in Plans to pin it here.") }
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
    // Only one card is open: the active exercise, or a finished/skipped one opened to look back at.
    var peek by remember { mutableStateOf<String?>(null) }
    var timerOpen by remember { mutableStateOf(false) }

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
            items(entries, key = { it.item.exerciseId }) { entry ->
                val id = entry.item.exerciseId
                val isTarget = target?.exerciseId == id
                ExerciseCard(
                    vm, nav, w, entry.item, entry.added,
                    open = if (peek != null) peek == id else isTarget, isTarget = isTarget,
                    onToggleOpen = {
                        com.thehhr.form.Haptics.tick(view)
                        // A pending exercise becomes the temporary active one; tapping it again (or finishing or
                        // skipping it) hands the turn back. Finished and skipped cards just open to look back at.
                        if (w.complete(id) || id in w.skipped) peek = if (peek == id) null else id
                        else { peek = null; if (!isTarget || w.jumpTo == id) vm.jumpTo(id) }
                    },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        WorkoutBar(vm, w, now, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            onAdd = { nav.push(Route.Library(Pick.Extra)) }, onFinish = { finishing = true }, onTimer = { timerOpen = true })
    }
    if (timerOpen) FullscreenTimer(vm, w, now, target?.exerciseId, onClose = { timerOpen = false })

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
            ExerciseImage(ex, size = 56.dp, animated = open)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
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
            TextButton(onClick = { vm.toggleSkip(id) }) { Text(if (skipped) "Restore" else "Skip") }
            TextButton(onClick = { nav.push(Route.Detail(id)) }) { Text("Details") }
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


/** What the workout clock shows right now: a rest / warm-up / set-timer countdown, the up-counting set clock, or the session. */
private class Clock(val label: String, val time: String, val counting: Boolean, val progress: Float?, val kind: String)

private fun clockOf(w: ActiveWorkout, now: Long): Clock {
    val rest = w.rest
    val left = rest?.let { it.pausedRemaining ?: (it.endsAt - now) }
    if (rest != null && left != null && left > 0) {
        val label = when { rest.type == "set" -> "Set ${(rest.setIndex ?: 0) + 1}"; rest.warmup -> "Warm-up"; else -> "Rest" }
        return Clock(label, fmtTime(left + 999), true, (left / (rest.total * 1000f)).coerceIn(0f, 1f), if (rest.type == "set") "set" else "rest")
    }
    // Up-counting set clock: from the end of a finished rest, or from when rest was skipped; a forgotten one stops showing.
    val from = (if (rest != null && rest.type != "set") rest.endsAt else w.setClockFrom)?.takeIf { now - it < 15 * 60_000 && !w.paused }
    if (from != null) return Clock("Set", fmtTime(now - from), false, null, "up")
    return Clock(if (w.paused) "Paused" else "Workout", fmtTime(w.elapsedMs(now)), false, null, "session")
}

/** Active-workout bar: add · pause · finish, then the clock. Tap the clock for the fullscreen timer. */
@Composable
private fun WorkoutBar(vm: FormStore, w: ActiveWorkout, now: Long, modifier: Modifier, onAdd: () -> Unit, onFinish: () -> Unit, onTimer: () -> Unit) {
    val clock = clockOf(w, now)
    androidx.compose.material3.Surface(modifier, shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shadowElevation = 6.dp) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAdd) { Icon(Icons.Rounded.Add, "Add exercise") }
            IconButton(onClick = vm::togglePause) {
                Icon(if (w.paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, if (w.paused) "Resume" else "Pause")
            }
            IconButton(onClick = onFinish) { Icon(Icons.Rounded.Flag, "Finish workout") }
            androidx.compose.material3.Surface(
                onClick = onTimer, shape = androidx.compose.foundation.shape.CircleShape,
                color = if (clock.counting) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (clock.counting) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp).heightIn(min = 48.dp)
                    .semantics { contentDescription = "${clock.label} ${clock.time}. Open fullscreen timer" },
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Timer, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(clock.label, style = MaterialTheme.typography.labelSmall)
                        Text(clock.time, style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
            }
        }
    }
}

/** Fullscreen rest / set timer: one big readable clock with the actions that matter at that moment. */
@Composable
private fun FullscreenTimer(vm: FormStore, w: ActiveWorkout, now: Long, targetId: String?, onClose: () -> Unit) {
    val clock = clockOf(w, now)
    val view = LocalView.current
    val target = targetId?.let { vm.exercise(it) }
    val rows = targetId?.let { w.rows(it) }.orEmpty()
    val restingFor = w.rest?.exerciseId?.takeIf { clock.kind == "set" }
    androidx.compose.ui.window.Dialog(onClose, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().padding(androidx.compose.foundation.layout.WindowInsets.systemBars.asPaddingValues()).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(w.routineName, Modifier.weight(1f), style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    androidx.compose.material3.FilledTonalIconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Close, "Close timer") }
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(300.dp), contentAlignment = Alignment.Center) {
                    val p = clock.progress
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = with(androidx.compose.ui.platform.LocalDensity.current) { 10.dp.toPx() },
                        cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    if (p != null) CircularWavyProgressIndicator(progress = { p }, Modifier.fillMaxSize(), stroke = stroke, trackStroke = stroke,
                        wavelength = 48.dp, amplitude = { 0.6f })
                    else androidx.compose.material3.CircularProgressIndicator(progress = { 1f }, Modifier.fillMaxSize(), strokeWidth = 10.dp,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(clock.label, style = MaterialTheme.typography.titleLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                        Text(clock.time, style = MaterialTheme.typography.displayLargeEmphasized.copy(fontSize = androidx.compose.ui.unit.TextUnit(76f, androidx.compose.ui.unit.TextUnitType.Sp)),
                            maxLines = 1, modifier = Modifier.semantics { contentDescription = "${clock.label} ${clock.time}" })
                        if (clock.kind == "up") Text("since rest ended", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(24.dp))
                val ex = restingFor?.let { vm.exercise(it) } ?: target
                if (ex != null) {
                    Text(if (clock.kind == "rest") "Next: ${exTitle(ex)}" else exTitle(ex), style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (restingFor == null && rows.isNotEmpty()) Text("Set ${(rows.count { it.done } + 1).coerceAtMost(rows.size)} of ${rows.size}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val big = Modifier.weight(1f).height(ButtonDefaults.MediumContainerHeight)
                    val bigPad = PaddingValues(horizontal = 16.dp)
                    val bigText = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)
                    when (clock.kind) {
                        "rest" -> {
                            FilledTonalButton(onClick = { com.thehhr.form.Haptics.tick(view); vm.extendRest(30) }, shapes = ButtonDefaults.shapes(), modifier = big,
                                contentPadding = bigPad) { Text("+30s", style = bigText) }
                            Button(onClick = { com.thehhr.form.Haptics.tick(view); vm.skipRest() }, shapes = ButtonDefaults.shapes(), modifier = big, contentPadding = bigPad) {
                                Icon(Icons.Rounded.SkipNext, null); Spacer(Modifier.width(8.dp)); Text("Skip", style = bigText)
                            }
                        }
                        "set" -> Button(onClick = { com.thehhr.form.Haptics.setDone(view); vm.completeSetTimer() }, shapes = ButtonDefaults.shapes(), modifier = big,
                            contentPadding = bigPad) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Finish set", style = bigText) }
                        else -> Button(onClick = { com.thehhr.form.Haptics.setDone(view); vm.logNextSet() }, enabled = targetId != null && !w.paused,
                            shapes = ButtonDefaults.shapes(), modifier = big, contentPadding = bigPad) {
                            Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text(if (targetId != null) "Log set" else "All done", style = bigText)
                        }
                    }
                }
            }
        }
    }
}
