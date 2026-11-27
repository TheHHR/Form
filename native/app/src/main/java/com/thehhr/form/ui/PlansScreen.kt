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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
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
import com.thehhr.form.FormStore
import com.thehhr.form.data.Fuel
import com.thehhr.form.data.Meal
import com.thehhr.form.data.Routine
import com.thehhr.form.data.RoutineItem
import com.thehhr.form.data.jsNum
import com.thehhr.form.data.jsNumber

@Composable
fun PlansTab(vm: FormStore, nav: Nav) {
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
private fun RoutineList(vm: FormStore, nav: Nav) {
    var creating by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val routines = vm.data.routines
    androidx.activity.compose.BackHandler(selecting) { selecting = false; picked = emptySet() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, if (selecting) 96.dp else 24.dp)) {
            item { ListHeader("New routine", { creating = true }, { importing = true }, selecting) { selecting = it; picked = emptySet() } }
            if (routines.isEmpty()) item {
                OutlinedButton(onClick = vm::loadDefaultRoutines, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Load Push / Pull / Legs") }
            }
            itemsIndexed(routines, key = { _, r -> r.id }) { i, r ->
                val on = r.id in picked
                GroupedItem(
                    i, routines.size, modifier = Modifier.animateItem(),
                    onClick = { if (selecting) picked = if (on) picked - r.id else picked + r.id else nav.push(Route.RoutineEdit(r.id)) },
                    leading = { if (selecting) androidx.compose.material3.Checkbox(on, null) else RoutineBadge(r, i) },
                    supporting = {
                        Text(r.items.take(4).joinToString { exTitle(vm.exercise(it.exerciseId)) } + if (r.items.size > 4) "…" else "",
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    trailing = { if (!selecting) LikeButton(r.liked) { vm.saveRoutine(r.copy(liked = !r.liked)) } },
                ) { Text(r.name, style = MaterialTheme.typography.titleMedium) }
            }
        }
        androidx.compose.animation.AnimatedVisibility(selecting, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()) {
            ShareBar(picked.size, routines.size, { picked = if (picked.size == routines.size) emptySet() else routines.map { it.id }.toSet() }) {
                shareText(ctx, if (picked.size == 1) routines.first { it.id in picked }.name else "Form routines", vm.routinesText(picked))
                selecting = false; picked = emptySet()
            }
        }
    }
    if (creating) NameDialog("New routine", "", onDone = { name -> creating = false; if (name != null) nav.push(Route.RoutineEdit(vm.newRoutine(name).id)) })
    if (importing) ImportDialog("routines", "## Push\n0025 3 * 8\n0314 4 * 12", { importing = false }) { t, replace -> vm.importRoutines(t, replace) }
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
private fun WeekSchedule(vm: FormStore) {
    val start = vm.data.prefs.weekStart
    val todayDow = java.time.LocalDate.now().dayOfWeek.value % 7
    val days = (0..6).map { (start + it) % 7 }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp)) {
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
private fun MealLibrary(vm: FormStore) {
    var editing by remember { mutableStateOf<Meal?>(null) }
    var importing by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf(setOf<String>()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val meals = vm.data.meals.sortedWith(compareByDescending<Meal> { it.liked }.thenBy { it.name.lowercase() })
    androidx.activity.compose.BackHandler(selecting) { selecting = false; picked = emptySet() }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, if (selecting) 96.dp else 24.dp)) {
            item {
                ListHeader("New meal", { editing = Meal(id = "custom_${System.currentTimeMillis()}", name = "") }, { importing = true }, selecting) {
                    selecting = it; picked = emptySet()
                }
            }
            if (meals.isEmpty()) item { OutlinedButton(onClick = vm::loadDefaultMeals, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text("Load common foods") } }
            itemsIndexed(meals, key = { _, m -> m.id }) { i, m ->
                val on = m.id in picked
                GroupedItem(
                    i, meals.size, modifier = Modifier.animateItem(),
                    onClick = { if (selecting) picked = if (on) picked - m.id else picked + m.id else editing = m },
                    leading = if (selecting) ({ androidx.compose.material3.Checkbox(on, null) }) else null,
                    supporting = { Text("${jsNum(m.p100)}p · ${jsNum(m.c100)}c · ${jsNum(m.f100)}f per 100 g · ${jsNum(m.defaultGrams)} g serving") },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(jsNum(m.cals100), style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                            if (!selecting) LikeButton(m.liked) { vm.saveMeal(m.copy(liked = !m.liked)) }
                        }
                    },
                ) { Text(m.name, style = MaterialTheme.typography.titleMedium) }
            }
        }
        androidx.compose.animation.AnimatedVisibility(selecting, Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            enter = androidx.compose.animation.slideInVertically { it } + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically { it } + androidx.compose.animation.fadeOut()) {
            ShareBar(picked.size, meals.size, { picked = if (picked.size == meals.size) emptySet() else meals.map { it.id }.toSet() }) {
                shareText(ctx, if (picked.size == 1) meals.first { it.id in picked }.name else "Form meals", vm.mealsText(picked))
                selecting = false; picked = emptySet()
            }
        }
    }
    editing?.let { m -> MealEditor(vm, m) { editing = null } }
    if (importing) ImportDialog("meals", "Chicken Breast (150g)\nPer100g 165cal 31pro 0carb 3.6fat", { importing = false }) { t, replace -> vm.importMeals(t, replace) }
}

@Composable
private fun MealEditor(vm: FormStore, meal: Meal, onClose: () -> Unit) {
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
fun RoutineEditorScreen(vm: FormStore, nav: Nav, id: String) {
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
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            ExerciseImage(ex, size = 56.dp)
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
                        // Two steppers share the row edge to edge (no dead space after them).
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Stepper("sets", item.sets.toDouble(), 1.0, 1.0..20.0, { v -> editItem(i) { it.copy(sets = v.toInt()) } }, Modifier.weight(1f))
                            if (item.timed) {
                                val sec = item.unitOrDefault == "sec"
                                Stepper(if (sec) "sec" else "min", item.reps.toDouble(), if (sec) 5.0 else 1.0, 0.0..60.0, { v -> editItem(i) { it.copy(reps = v.toInt()) } }, Modifier.weight(1f))
                            } else {
                                Stepper("reps", item.reps.toDouble(), 1.0, 0.0..100.0, { v -> editItem(i) { it.copy(reps = v.toInt()) } }, Modifier.weight(1f))
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill(item.timed, { editItem(i) { it.copy(timed = !it.timed, reps = it.reps.coerceAtMost(60), distance = false) } }, label = { Text("Timed") })
                            if (item.timed) Pill(item.unit == "min", { editItem(i) { it.copy(unit = if (it.unit == "min") null else "min") } }, label = { Text("Minutes") })
                            Pill(item.weighted, { editItem(i) { it.copy(unweighted = !it.unweighted) } }, label = { Text("Weight") })
                            if (item.timed) Pill(item.distance, { editItem(i) { it.copy(distance = !it.distance) } }, label = { Text("Distance") })
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

/** Paste routines or meals in Form's text format, then Add (keep yours) or Replace (swap the list). */
@Composable
fun ImportDialog(what: String, example: String, onDismiss: () -> Unit, onImport: (text: String, replace: Boolean) -> Unit) {
    var text by remember { mutableStateOf("") }
    var confirmReplace by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Download, null) },
        title = { Text("Import $what") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Paste $what exported from Form (or written in its format).", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it.take(100_000) }, Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 320.dp),
                    placeholder = { Text(example, style = MaterialTheme.typography.bodySmall) })
                if (confirmReplace) Text("Replace deletes your current $what. Tap Replace again to confirm.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { onImport(text, false); onDismiss() }, enabled = text.isNotBlank()) { Text("Add") } },
        dismissButton = {
            Row {
                TextButton(onClick = { if (confirmReplace) { onImport(text, true); onDismiss() } else confirmReplace = true }, enabled = text.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Replace") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Opens the Android share sheet with plain text. */
fun shareText(ctx: android.content.Context, title: String, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
        .putExtra(android.content.Intent.EXTRA_SUBJECT, title).putExtra(android.content.Intent.EXTRA_TEXT, text)
    ctx.startActivity(android.content.Intent.createChooser(send, title))
}

/** Header for Routines / Meals: new, import, and a select-to-share mode. */
@Composable
private fun ListHeader(newLabel: String, onNew: () -> Unit, onImport: () -> Unit, selecting: Boolean, onSelecting: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = onNew, shapes = ButtonDefaults.shapes(), modifier = Modifier.weight(1f)) {
            Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text(newLabel)
        }
        androidx.compose.material3.FilledTonalIconButton(onClick = onImport, shapes = androidx.compose.material3.IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Download, "Import")
        }
        androidx.compose.material3.FilledTonalIconToggleButton(checked = selecting, onCheckedChange = onSelecting,
            shapes = androidx.compose.material3.IconButtonDefaults.toggleableShapes()) {
            Icon(Icons.Rounded.Share, if (selecting) "Stop selecting" else "Select to share")
        }
    }
}

/** Bottom action while selecting: share the picked items as text. */
@Composable
private fun ShareBar(count: Int, all: Int, onAll: () -> Unit, modifier: Modifier = Modifier, onShare: () -> Unit) {
    androidx.compose.material3.Surface(modifier, shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.primaryContainer, shadowElevation = 6.dp) {
        Row(Modifier.padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onAll) { Text(if (count == all) "Clear" else "Select all") }
            Text("$count selected", Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.labelLarge)
            androidx.compose.material3.Button(onClick = onShare, enabled = count > 0, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Share")
            }
        }
    }
}
