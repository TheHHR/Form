@file:OptIn(ExperimentalMaterial3Api::class)

package com.thehhr.form.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thehhr.form.AppViewModel
import com.thehhr.form.data.Fuel
import com.thehhr.form.data.Meal
import com.thehhr.form.data.Routine
import com.thehhr.form.data.RoutineItem
import com.thehhr.form.data.jsNum
import com.thehhr.form.data.jsNumber

@Composable
fun PlansTab(vm: AppViewModel, nav: Nav) {
    var section by rememberSaveable { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            listOf("Routines", "Week", "Meals").forEachIndexed { i, label ->
                SegmentedButton(selected = section == i, onClick = { section = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
            }
        }
        when (section) {
            0 -> RoutineList(vm, nav)
            1 -> WeekSchedule(vm)
            else -> MealLibrary(vm)
        }
    }
}

@Composable
private fun RoutineList(vm: AppViewModel, nav: Nav) {
    var creating by remember { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilledTonalButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("New routine")
            }
        }
        if (vm.data.routines.isEmpty()) item {
            OutlinedButton(onClick = vm::loadDefaultRoutines, modifier = Modifier.fillMaxWidth()) { Text("Load Push / Pull / Legs") }
        }
        items(vm.data.routines, key = { it.id }) { r ->
            Card(Modifier.fillMaxWidth().clickable { nav.push(Route.RoutineEdit(r.id)) }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.name, style = MaterialTheme.typography.titleMedium)
                        Text(r.items.take(4).joinToString { exTitle(vm.exercise(it.exerciseId)) } + if (r.items.size > 4) "…" else "",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (r.liked) Icon(Icons.Rounded.Favorite, "Liked", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (creating) NameDialog("New routine", "", onDone = { name -> creating = false; if (name != null) nav.push(Route.RoutineEdit(vm.newRoutine(name).id)) })
}

@Composable
fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onDone(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

private val DAY_NAMES = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

@Composable
private fun WeekSchedule(vm: AppViewModel) {
    val start = vm.data.prefs.weekStart
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items((0..6).map { (start + it) % 7 }) { day ->
            var open by remember { mutableStateOf(false) }
            val routine = vm.data.schedule[day]?.let { id -> vm.data.routines.firstOrNull { it.id == id } }
            Card(Modifier.fillMaxWidth().clickable { open = true }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(DAY_NAMES[day], Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Text(routine?.name ?: "Rest", color = if (routine != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Rounded.KeyboardArrowDown, null)
                    DropdownMenu(open, { open = false }) {
                        DropdownMenuItem(text = { Text("Rest") }, onClick = { open = false; vm.setSchedule(day, null) })
                        vm.data.routines.forEach { r -> DropdownMenuItem(text = { Text(r.name) }, onClick = { open = false; vm.setSchedule(day, r.id) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MealLibrary(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Meal?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            FilledTonalButton(onClick = { editing = Meal(id = "custom_${System.currentTimeMillis()}", name = "") }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("New meal")
            }
        }
        if (vm.data.meals.isEmpty()) item { OutlinedButton(onClick = vm::loadDefaultMeals, modifier = Modifier.fillMaxWidth()) { Text("Load common foods") } }
        items(vm.data.meals.sortedWith(compareByDescending<Meal> { it.liked }.thenBy { it.name.lowercase() }), key = { it.id }) { m ->
            ListItem(
                headlineContent = { Text(m.name) },
                supportingContent = { Text("${jsNum(m.cals100)} kcal · ${jsNum(m.p100)}p ${jsNum(m.c100)}c ${jsNum(m.f100)}f per 100 g · default ${jsNum(m.defaultGrams)} g") },
                trailingContent = {
                    IconButton(onClick = { vm.saveMeal(m.copy(liked = !m.liked)) }) {
                        Icon(if (m.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (m.liked) "Unlike" else "Like",
                            tint = if (m.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier.clickable { editing = m },
            )
        }
    }
    editing?.let { m -> MealEditor(vm, m) { editing = null } }
}

@Composable
private fun MealEditor(vm: AppViewModel, meal: Meal, onClose: () -> Unit) {
    var name by remember { mutableStateOf(meal.name) }
    var grams by remember { mutableStateOf(jsNum(meal.defaultGrams)) }
    var p by remember { mutableStateOf(jsNum(meal.p100)) }
    var c by remember { mutableStateOf(jsNum(meal.c100)) }
    var f by remember { mutableStateOf(jsNum(meal.f100)) }
    val exists = vm.data.meals.any { it.id == meal.id }
    fun n(s: String) = jsNumber(s).takeIf { it.isFinite() && it >= 0 } ?: 0.0
    val kcal = Fuel.kcal(n(p), n(c), n(f))
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(if (exists) "Edit meal" else "New meal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Name") }, singleLine = true)
                val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                OutlinedTextField(grams, { grams = it }, label = { Text("Default portion (g)") }, singleLine = true, keyboardOptions = num)
                Text("Per 100 g", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p, { p = it }, Modifier.weight(1f), label = { Text("Protein") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(c, { c = it }, Modifier.weight(1f), label = { Text("Carbs") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(f, { f = it }, Modifier.weight(1f), label = { Text("Fat") }, singleLine = true, keyboardOptions = num)
                }
                Text("${jsNum(kcal)} kcal per 100 g", color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                vm.saveMeal(meal.copy(name = name.trim(), defaultGrams = n(grams).coerceIn(1.0, 5000.0),
                    p100 = n(p).coerceAtMost(999.0), c100 = n(c).coerceAtMost(999.0), f100 = n(f).coerceAtMost(999.0), cals100 = kcal))
                onClose()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { vm.deleteMeal(meal.id); onClose() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun RoutineEditorScreen(vm: AppViewModel, nav: Nav, id: String) {
    val routine = vm.data.routines.firstOrNull { it.id == id } ?: run { nav.pop(); return }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    fun edit(f: (Routine) -> Routine) = vm.saveRoutine(f(routine))
    fun editItem(i: Int, f: (RoutineItem) -> RoutineItem) = edit { r -> r.copy(items = r.items.mapIndexed { j, it -> if (j == i) f(it) else it }) }
    Scaffold(topBar = {
        BackBar(routine.name, nav) {
            IconButton(onClick = { edit { it.copy(liked = !it.liked) } }) {
                Icon(if (routine.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (routine.liked) "Unlike" else "Like",
                    tint = if (routine.liked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, "Delete routine") }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { renaming = true }) { Text("Rename") }
                    FilledTonalButton(onClick = { nav.push(Route.Library(Pick.ToRoutine(id))) }) {
                        Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Add exercises")
                    }
                }
            }
            if (routine.items.isEmpty()) item { EmptyNote("No exercises yet") }
            itemsIndexed(routine.items, key = { _, it -> it.exerciseId }) { i, item ->
                val ex = vm.exercise(item.exerciseId)
                val partnerIdx = item.superset?.let { tok -> routine.items.indexOfFirst { it.superset == tok && it.exerciseId != item.exerciseId } } ?: -1
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseImage(ex, size = 48.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(exTitle(ex), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (partnerIdx >= 0) Text("Superset with ${exTitle(vm.exercise(routine.items[partnerIdx].exerciseId))}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            IconButton(onClick = { edit { r -> r.copy(items = r.items.toMutableList().apply { add(i - 1, removeAt(i)) }) } }, enabled = i > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                            }
                            IconButton(onClick = { edit { r -> r.copy(items = r.items.toMutableList().apply { add(i + 1, removeAt(i)) }) } }, enabled = i < routine.items.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Stepper("sets", "${item.sets}", { editItem(i) { it.copy(sets = (it.sets - 1).coerceAtLeast(1)) } }, { editItem(i) { it.copy(sets = (it.sets + 1).coerceAtMost(20)) } })
                            if (item.timed) {
                                val sec = item.unitOrDefault == "sec"
                                Stepper(if (sec) "sec" else "min", "${item.reps}",
                                    { editItem(i) { it.copy(reps = (it.reps - if (sec) 5 else 1).coerceAtLeast(0)) } },
                                    { editItem(i) { it.copy(reps = (it.reps + if (sec) 5 else 1).coerceAtMost(60)) } })
                            } else {
                                Stepper("reps", "${item.reps}", { editItem(i) { it.copy(reps = (it.reps - 1).coerceAtLeast(0)) } }, { editItem(i) { it.copy(reps = (it.reps + 1).coerceAtMost(100)) } })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(item.timed, { editItem(i) { it.copy(timed = !it.timed, reps = it.reps.coerceAtMost(60), distance = false) } }, label = { Text("Timed") })
                            if (item.timed) FilterChip(item.unit == "min", { editItem(i) { it.copy(unit = if (it.unit == "min") null else "min") } }, label = { Text("Minutes") })
                            FilterChip(item.weighted, { editItem(i) { it.copy(unweighted = !it.unweighted) } }, label = { Text("Weight") })
                            if (item.timed) FilterChip(item.distance, { editItem(i) { it.copy(distance = !it.distance) } }, label = { Text("Distance") })
                        }
                        Row {
                            if (partnerIdx >= 0) TextButton(onClick = { edit { r -> r.copy(items = r.items.map { if (it.superset == item.superset) it.copy(superset = null) else it }) } }) {
                                Icon(Icons.Rounded.LinkOff, null); Spacer(Modifier.width(4.dp)); Text("Unlink")
                            } else if (i < routine.items.lastIndex) TextButton(onClick = {
                                val token = (System.currentTimeMillis() % 1_000_000).toString(36)
                                val next = routine.items[i + 1]
                                edit { r -> r.copy(items = r.items.map {
                                    when {
                                        it.exerciseId == item.exerciseId || it.exerciseId == next.exerciseId -> it.copy(superset = token)
                                        it.superset != null && (it.superset == item.superset || it.superset == next.superset) -> it.copy(superset = null)
                                        else -> it
                                    }
                                }) }
                            }) { Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(4.dp)); Text("Superset with next") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { edit { r -> r.copy(items = r.items.filterIndexed { j, _ -> j != i }) } }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
    if (renaming) NameDialog("Rename routine", routine.name) { n -> renaming = false; if (n != null) edit { it.copy(name = n) } }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete ${routine.name}?") },
        text = { Text("Logs from past workouts are kept.") },
        confirmButton = { TextButton(onClick = { deleting = false; nav.pop(); vm.deleteRoutine(id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
}
