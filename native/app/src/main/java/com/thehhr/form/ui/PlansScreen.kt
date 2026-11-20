@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    var section by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ChoiceGroup(listOf(0 to "Routines", 1 to "Week", 2 to "Meals"), section, { section = it }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
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
    val routines = vm.data.routines
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
        item {
            FilledTonalButton(onClick = { creating = true }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("New routine")
            }
        }
        if (routines.isEmpty()) item {
            OutlinedButton(onClick = vm::loadDefaultRoutines, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Load Push / Pull / Legs") }
        }
        itemsIndexed(routines, key = { _, r -> r.id }) { i, r ->
            GroupedItem(
                i, routines.size, onClick = { nav.push(Route.RoutineEdit(r.id)) },
                leading = { RoutineBadge(r, i) },
                supporting = {
                    Text(r.items.take(4).joinToString { exTitle(vm.exercise(it.exerciseId)) } + if (r.items.size > 4) "…" else "",
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                trailing = { LikeButton(r.liked) { vm.saveRoutine(r.copy(liked = !r.liked)) } },
            ) { Text(r.name, style = MaterialTheme.typography.titleMedium) }
        }
    }
    if (creating) NameDialog("New routine", "", onDone = { name -> creating = false; if (name != null) nav.push(Route.RoutineEdit(vm.newRoutine(name).id)) })
}

/** Each routine gets its own expressive shape so the list scans at a glance. */
@Composable
private fun RoutineBadge(r: Routine, index: Int) {
    val shapes = listOf(MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny, MaterialShapes.Pentagon, MaterialShapes.Gem, MaterialShapes.SoftBurst)
    Box(Modifier.size(44.dp).clip(shapes[index % shapes.size].toShape()).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text("${r.items.size}", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun NameDialog(title: String, initial: String, onDone: (String?) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        icon = { Icon(Icons.Rounded.Edit, null) },
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
    val todayDow = java.time.LocalDate.now().dayOfWeek.value % 7
    val days = (0..6).map { (start + it) % 7 }
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
        itemsIndexed(days) { i, day ->
            var open by remember { mutableStateOf(false) }
            val routine = vm.data.schedule[day]?.let { id -> vm.data.routines.firstOrNull { it.id == id } }
            Box {
                GroupedItem(
                    i, 7, onClick = { open = true },
                    supporting = { Text(routine?.name ?: "Rest", color = if (routine != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailing = { Icon(Icons.Rounded.UnfoldMore, "Change") },
                ) {
                    Text(DAY_NAMES[day] + if (day == todayDow) " · today" else "",
                        style = if (day == todayDow) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium)
                }
                DropdownMenu(open, { open = false }) {
                    DropdownMenuItem(text = { Text("Rest") }, onClick = { open = false; vm.setSchedule(day, null) })
                    vm.data.routines.forEach { r -> DropdownMenuItem(text = { Text(r.name) }, onClick = { open = false; vm.setSchedule(day, r.id) }) }
                }
            }
        }
    }
}

@Composable
private fun MealLibrary(vm: AppViewModel) {
    var editing by remember { mutableStateOf<Meal?>(null) }
    val meals = vm.data.meals.sortedWith(compareByDescending<Meal> { it.liked }.thenBy { it.name.lowercase() })
    LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
        item {
            FilledTonalButton(onClick = { editing = Meal(id = "custom_${System.currentTimeMillis()}", name = "") }, shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("New meal")
            }
        }
        if (meals.isEmpty()) item { OutlinedButton(onClick = vm::loadDefaultMeals, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Load common foods") } }
        itemsIndexed(meals, key = { _, m -> m.id }) { i, m ->
            GroupedItem(
                i, meals.size, onClick = { editing = m },
                supporting = { Text("${jsNum(m.p100)}p · ${jsNum(m.c100)}c · ${jsNum(m.f100)}f per 100 g · ${jsNum(m.defaultGrams)} g serving") },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(jsNum(m.cals100), style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                        LikeButton(m.liked) { vm.saveMeal(m.copy(liked = !m.liked)) }
                    }
                },
            ) { Text(m.name, style = MaterialTheme.typography.titleMedium) }
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
                OutlinedTextField(grams, { grams = it }, label = { Text("Serving (g)") }, singleLine = true, keyboardOptions = num)
                Text("Per 100 g", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p, { p = it }, Modifier.weight(1f), label = { Text("Protein") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(c, { c = it }, Modifier.weight(1f), label = { Text("Carbs") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(f, { f = it }, Modifier.weight(1f), label = { Text("Fat") }, singleLine = true, keyboardOptions = num)
                }
                Text("${jsNum(kcal)} kcal per 100 g", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
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
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            FormTopBar(routine.name, scroll, subtitle = "${routine.items.size} exercises", onBack = nav::pop) {
                LikeButton(routine.liked) { edit { it.copy(liked = !it.liked) } }
                IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, "Rename routine") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, "Delete routine") }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.push(Route.Library(Pick.ToRoutine(id))) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Add exercises") })
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (routine.items.isEmpty()) item { EmptyNote("No exercises yet") }
            itemsIndexed(routine.items, key = { _, it -> it.exerciseId }) { i, item ->
                val ex = vm.exercise(item.exerciseId)
                val partnerIdx = item.superset?.let { tok -> routine.items.indexOfFirst { it.superset == tok && it.exerciseId != item.exerciseId } } ?: -1
                Card(
                    Modifier.fillMaxWidth().animateItem(), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = if (partnerIdx >= 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ExerciseImage(ex, size = 52.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(exTitle(ex), style = MaterialTheme.typography.titleSmallEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (partnerIdx >= 0) Text("Superset with ${exTitle(vm.exercise(routine.items[partnerIdx].exerciseId))}",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            IconButton(onClick = { edit { r -> r.copy(items = r.items.toMutableList().apply { add(i - 1, removeAt(i)) }) } }, enabled = i > 0) {
                                Icon(Icons.Rounded.KeyboardArrowUp, "Move up")
                            }
                            IconButton(onClick = { edit { r -> r.copy(items = r.items.toMutableList().apply { add(i + 1, removeAt(i)) }) } }, enabled = i < routine.items.lastIndex) {
                                Icon(Icons.Rounded.KeyboardArrowDown, "Move down")
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stepper("sets", item.sets.toDouble(), 1.0, 1.0..20.0, { v -> editItem(i) { it.copy(sets = v.toInt()) } })
                            if (item.timed) {
                                val sec = item.unitOrDefault == "sec"
                                Stepper(if (sec) "sec" else "min", item.reps.toDouble(), if (sec) 5.0 else 1.0, 0.0..60.0, { v -> editItem(i) { it.copy(reps = v.toInt()) } })
                            } else {
                                Stepper("reps", item.reps.toDouble(), 1.0, 0.0..100.0, { v -> editItem(i) { it.copy(reps = v.toInt()) } })
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Delete ${routine.name}?") },
        text = { Text("Logs from past workouts are kept.") },
        confirmButton = { TextButton(onClick = { deleting = false; nav.pop(); vm.deleteRoutine(id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
    )
}
