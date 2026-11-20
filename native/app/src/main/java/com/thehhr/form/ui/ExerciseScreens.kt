@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.thehhr.form.AppViewModel
import com.thehhr.form.data.Exercise
import com.thehhr.form.data.ProgressLog
import com.thehhr.form.data.WorkoutLogic
import com.thehhr.form.data.jsTitle

/** The web app's search: every token must match; "curls" also matches "curl". `#0025` searches ids. */
fun matches(e: Exercise, query: String, tags: List<String>): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return true
    if (q.startsWith("#")) return e.id.startsWith(q.drop(1).trim())
    val hay = (listOf(e.name, e.category, e.target, e.equipment, e.muscleGroup) + e.secondary + tags).joinToString(" ").lowercase()
    return q.split(Regex("\\s+")).all { t -> hay.contains(t) || (t.length >= 3 && t.endsWith("s") && hay.contains(t.dropLast(1))) }
}

@Composable
fun LikeButton(liked: Boolean, onToggle: () -> Unit) {
    IconToggleButton(checked = liked, onCheckedChange = { onToggle() }) {
        Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like",
            tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LibraryScreen(vm: AppViewModel, nav: Nav, pick: Pick?) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var likedOnly by rememberSaveable { mutableStateOf(false) }
    val d = vm.data
    val list = remember(query, category, likedOnly, d.liked, d.customExercises, d.exerciseTags) {
        vm.catalog.all.filter { e ->
            (category.isEmpty() || e.category == category) && (!likedOnly || e.id in d.liked) &&
                matches(e, query, d.exerciseTags[e.id].orEmpty())
        }.sortedBy { it.name.lowercase() }
    }
    val title = when (pick) {
        is Pick.ToRoutine -> "Add to ${d.routines.firstOrNull { it.id == pick.routineId }?.name ?: "routine"}"
        Pick.Extra -> "Add to workout"
        is Pick.Replace -> "Replace exercise"
        null -> "Exercises"
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { FormTopBar(title, scroll, subtitle = (pick as? Pick.Replace)?.let { exTitle(vm.exercise(it.fromId)) } ?: "${list.size} exercises", onBack = nav::pop) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    placeholder = { Text("Name, muscle, equipment or #id") },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = likedOnly, onClick = { likedOnly = !likedOnly }, label = { Text("Liked") },
                        leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp)) })
                    Exercise.CATEGORIES.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = if (category == c) "" else c }, label = { Text(jsTitle(c)) })
                    }
                }
            }
            if (list.isEmpty()) item { EmptyNote("No exercises match") }
            itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                GroupedItem(
                    i, list.size,
                    onClick = {
                        when (pick) {
                            is Pick.ToRoutine -> { vm.addToRoutine(pick.routineId, e.id); vm.message = "Added ${exTitle(e)}" }
                            Pick.Extra -> { vm.addExtra(e.id); nav.pop() }
                            is Pick.Replace -> { vm.replaceExercise(pick.fromId, e.id); nav.pop() }
                            null -> nav.push(Route.Detail(e.id))
                        }
                    },
                    leading = { ExerciseImage(e, size = 56.dp) },
                    supporting = { Text(jsTitle("${e.target} · ${e.equipment}")) },
                    trailing = { LikeButton(e.id in d.liked) { vm.toggleLike(e.id) } },
                ) { Text(exTitle(e), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

@Composable
fun ExerciseDetailScreen(vm: AppViewModel, nav: Nav, id: String) {
    val e = vm.exercise(id)
    val d = vm.data
    val units = d.prefs.units
    val logs = d.logs.filter { it.exerciseId == id }.sortedWith(compareByDescending<ProgressLog> { it.date }.thenByDescending { it.createdAt })
    val pr = WorkoutLogic.record(d.logs, id)
    var routineMenu by remember { mutableStateOf(false) }
    var logDialog by remember { mutableStateOf(false) }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            FormTopBar(exTitle(e), scroll, subtitle = e?.let { jsTitle("${it.target} · ${it.equipment}") }, onBack = nav::pop) {
                LikeButton(id in d.liked) { vm.toggleLike(id) }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { ExerciseImage(e, Modifier.fillMaxWidth().aspectRatio(1f).clip(MaterialTheme.shapes.extraLarge), animated = true) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOfNotNull(e?.category, e?.muscleGroup?.takeIf { it.isNotEmpty() }).plus(e?.secondary.orEmpty()).distinct()
                        .forEach { SuggestionChip(onClick = {}, label = { Text(jsTitle(it)) }) }
                }
            }
            if (pr != null) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer),
                    shape = MaterialTheme.shapes.large) {
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
                    FilledTonalButton(onClick = { logDialog = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) { Text("Log progress") }
                }
            }
            if (e?.description?.isNotEmpty() == true) item { Text(e.description) }
            if (e?.instructions?.isNotEmpty() == true) {
                item { SectionTitle("How to") }
                itemsIndexed(e.instructions) { i, step ->
                    Row(verticalAlignment = Alignment.Top) {
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
            if (e != null && !e.custom) item {
                Text("© Gym visual — gymvisual.com", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
    if (logDialog && e != null) LogDialog(vm, e) { logDialog = false }
}

@Composable
fun LogRow(vm: AppViewModel, log: ProgressLog, index: Int, count: Int, showName: Boolean = true) {
    var confirm by remember { mutableStateOf(false) }
    GroupedItem(
        index, count,
        supporting = { Text(WorkoutLogic.formatLog(log, vm.data.prefs.units) + if (log.notes.isNotEmpty()) " · ${log.notes}" else "") },
        trailing = { IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, "Delete log") } },
    ) { Text(if (showName) exTitle(vm.exercise(log.exerciseId)) else log.date, style = MaterialTheme.typography.titleSmall) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Delete this log?") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.deleteLog(log.id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

/** Manual log for today (strength: sets × reps @ weight; timed cardio: minutes). */
@Composable
private fun LogDialog(vm: AppViewModel, e: Exercise, onClose: () -> Unit) {
    val units = vm.data.prefs.units
    var sets by remember { mutableIntStateOf(3) }
    var reps by remember { mutableIntStateOf(10) }
    var weight by remember { mutableDoubleStateOf(WorkoutLogic.lastWeight(vm.data.logs, e.id)) }
    var minutes by remember { mutableDoubleStateOf(20.0) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Log ${exTitle(e)}") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (e.timedCardio) {
                    Stepper("min", minutes, 1.0, 1.0..600.0, { minutes = it })
                } else {
                    Stepper("sets", sets.toDouble(), 1.0, 1.0..20.0, { sets = it.toInt() })
                    Stepper("reps", reps.toDouble(), 1.0, 1.0..100.0, { reps = it.toInt() })
                    Stepper(units.weight, weight, if (units.weight == "lb") 5.0 else 2.5, 0.0..2000.0, { weight = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val now = System.currentTimeMillis()
                vm.addLog(
                    if (e.timedCardio) ProgressLog(id = now.toString(), exerciseId = e.id, date = vm.todayKey, intervals = 1, durUnit = "min",
                        setDurations = listOf(minutes), createdAt = now)
                    else ProgressLog(id = now.toString(), exerciseId = e.id, date = vm.todayKey, sets = sets, reps = reps,
                        weight = weight.takeIf { it > 0 }, setReps = List(sets) { reps }, setWeights = List(sets) { weight }, createdAt = now)
                )
                onClose()
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
