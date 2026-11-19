@file:OptIn(ExperimentalMaterial3Api::class)

package com.thehhr.form.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
fun BackBar(title: String, nav: Nav, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = { IconButton(onClick = nav::pop) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = { actions() },
    )
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
        }.sortedBy { it.name }
    }
    val title = when (pick) {
        is Pick.ToRoutine -> "Add to ${d.routines.firstOrNull { it.id == pick.routineId }?.name ?: "routine"}"
        Pick.Extra -> "Add to workout"
        is Pick.Replace -> "Replace ${exTitle(vm.exercise(pick.fromId))}"
        null -> "Exercises"
    }
    Scaffold(topBar = { BackBar(title, nav) }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search name, muscle, equipment or #id") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = likedOnly, onClick = { likedOnly = !likedOnly }, label = { Text("Liked") },
                    leadingIcon = { Icon(Icons.Rounded.Favorite, null) })
                Exercise.CATEGORIES.forEach { c ->
                    FilterChip(selected = category == c, onClick = { category = if (category == c) "" else c }, label = { Text(jsTitle(c)) })
                }
            }
            Text("${list.size} exercises", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(list, key = { it.id }) { e ->
                    ListItem(
                        headlineContent = { Text(exTitle(e)) },
                        supportingContent = { Text(jsTitle("${e.target} · ${e.equipment}")) },
                        leadingContent = { ExerciseImage(e, size = 56.dp) },
                        trailingContent = {
                            IconButton(onClick = { vm.toggleLike(e.id) }) {
                                Icon(if (e.id in d.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                    if (e.id in d.liked) "Unlike" else "Like",
                                    tint = if (e.id in d.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable {
                            when (pick) {
                                is Pick.ToRoutine -> { vm.addToRoutine(pick.routineId, e.id); vm.message = "Added ${exTitle(e)}" }
                                Pick.Extra -> { vm.addExtra(e.id); nav.pop() }
                                is Pick.Replace -> { vm.replaceExercise(pick.fromId, e.id); nav.pop() }
                                null -> nav.push(Route.Detail(e.id))
                            }
                        },
                    )
                }
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
    val liked = id in d.liked
    Scaffold(topBar = {
        BackBar(exTitle(e), nav) {
            IconButton(onClick = { vm.toggleLike(id) }) {
                Icon(if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (liked) "Unlike" else "Like",
                    tint = if (liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { ExerciseImage(e, Modifier.fillMaxWidth().aspectRatio(1f), animated = true) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOfNotNull(e?.target, e?.equipment, e?.category).distinct().forEach { AssistChip(onClick = {}, label = { Text(jsTitle(it)) }) }
                }
            }
            if (pr != null) item { Text("PR: ${num(pr)} ${units.weight}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column {
                        OutlinedButton(onClick = { routineMenu = true }) { Text("Add to routine") }
                        DropdownMenu(expanded = routineMenu, onDismissRequest = { routineMenu = false }) {
                            if (d.routines.isEmpty()) DropdownMenuItem(text = { Text("No routines yet") }, onClick = { routineMenu = false })
                            d.routines.forEach { r ->
                                DropdownMenuItem(text = { Text(r.name) }, onClick = {
                                    routineMenu = false; vm.addToRoutine(r.id, id); vm.message = "Added to ${r.name}"
                                })
                            }
                        }
                    }
                    FilledTonalButton(onClick = { logDialog = true }) { Text("Log progress") }
                }
            }
            if (e?.description?.isNotEmpty() == true) item { Text(e.description) }
            if (e?.instructions?.isNotEmpty() == true) {
                item { SectionTitle("How to") }
                items(e.instructions.withIndex().toList()) { (i, step) ->
                    Row { Text("${i + 1}.", Modifier.width(24.dp), fontWeight = FontWeight.SemiBold); Text(step) }
                }
            }
            item { SectionTitle("History") }
            if (logs.isEmpty()) item { EmptyNote("No logs yet") }
            items(logs, key = { it.id }) { log -> LogRow(vm, log, showName = false) }
            if (e != null && !e.custom) item {
                Text("© Gym visual — gymvisual.com", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
    if (logDialog && e != null) LogDialog(vm, e) { logDialog = false }
}

@Composable
fun LogRow(vm: AppViewModel, log: ProgressLog, showName: Boolean = true) {
    var confirm by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(if (showName) exTitle(vm.exercise(log.exerciseId)) else log.date) },
        supportingContent = {
            Text(WorkoutLogic.formatLog(log, vm.data.prefs.units) + if (log.notes.isNotEmpty()) " · ${log.notes}" else "")
        },
        trailingContent = { IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, "Delete log") } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete this log?") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.deleteLog(log.id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}

/** Manual log for today (strength: sets × reps @ weight; timed cardio: intervals × minutes). */
@Composable
private fun LogDialog(vm: AppViewModel, e: Exercise, onClose: () -> Unit) {
    val units = vm.data.prefs.units
    var sets by remember { mutableStateOf(3) }
    var reps by remember { mutableStateOf(10) }
    var weight by remember { mutableStateOf(WorkoutLogic.lastWeight(vm.data.logs, e.id)) }
    var minutes by remember { mutableStateOf(20.0) }
    val wStep = if (units.weight == "lb") 5.0 else 2.5
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Log ${exTitle(e)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (e.timedCardio) {
                    Stepper("min", num(minutes), { minutes = (minutes - 1).coerceAtLeast(1.0) }, { minutes = (minutes + 1).coerceAtMost(600.0) })
                } else {
                    Stepper("sets", "$sets", { sets = (sets - 1).coerceAtLeast(1) }, { sets = (sets + 1).coerceAtMost(20) })
                    Stepper("reps", "$reps", { reps = (reps - 1).coerceAtLeast(1) }, { reps = (reps + 1).coerceAtMost(100) })
                    Stepper(units.weight, num(weight), { weight = (weight - wStep).coerceAtLeast(0.0) }, { weight = (weight + wStep).coerceAtMost(2000.0) })
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
