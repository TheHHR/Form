@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.ui.layout.layout

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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.thehhr.form.FormStore
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
fun LibraryScreen(vm: FormStore, nav: Nav, pick: Pick?, f: LibraryFilters) {
    var query by f::query
    var category by f::category
    var likedOnly by f::likedOnly
    var loggedOnly by f::loggedOnly
    var target by f::target
    var equipment by f::equipment
    var tag by f::tag
    var creating by remember { mutableStateOf(false) }
    val d = vm.data
    val logged = remember(d.logs) { d.logs.map { it.exerciseId }.toSet() }
    val tags = vm.allTags
    val list = remember(query, category, target, equipment, likedOnly, loggedOnly, tag, d.liked, logged, d.customExercises, d.exerciseTags) {
        vm.catalog.all.filter { e ->
            (category.isEmpty() || e.category == category) && (target.isEmpty() || e.target == target) &&
                (equipment.isEmpty() || e.equipment == equipment) &&
                (!likedOnly || e.id in d.liked) && (!loggedOnly || e.id in logged) &&
                (tag.isEmpty() || d.exerciseTags[e.id].orEmpty().any { it.equals(tag, true) }) &&
                matches(e, query, d.exerciseTags[e.id].orEmpty())
        }.sortedBy { it.name.lowercase() }
    }
    // Exercises already in the routine / session: shown greyed with a check, not added twice.
    val already: Set<String> = when (pick) {
        is Pick.ToRoutine -> d.routines.firstOrNull { it.id == pick.routineId }?.items?.map { it.exerciseId }?.toSet().orEmpty()
        Pick.Extra, is Pick.Replace -> vm.workout?.let { w -> d.routines.firstOrNull { it.id == w.routineId }?.let { r ->
            com.thehhr.form.data.WorkoutLogic.entries(w, r).map { it.item.exerciseId }.toSet() } }.orEmpty()
        null -> emptySet()
    }
    val title = when (pick) {
        is Pick.ToRoutine -> "Add to ${d.routines.firstOrNull { it.id == pick.routineId }?.name ?: "routine"}"
        Pick.Extra -> "Add to workout"
        is Pick.Replace -> "Replace exercise"
        null -> "Exercises"
    }
    fun choose(e: com.thehhr.form.data.Exercise) {
        when (pick) {
            is Pick.ToRoutine -> { vm.addToRoutine(pick.routineId, e.id); vm.message = "Added ${exTitle(e)}" }
            Pick.Extra -> { vm.addExtra(e.id); nav.pop() }
            is Pick.Replace -> { vm.replaceExercise(pick.fromId, e.id); nav.pop() }
            null -> nav.push(Route.Detail(e.id))
        }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            FormTopBar(title, scroll, subtitle = (pick as? Pick.Replace)?.let { exTitle(vm.exercise(it.fromId)) } ?: "${list.size} exercises", onBack = nav::pop) {
                IconButton(onClick = { creating = true }) { Icon(Icons.Rounded.Add, "Create custom exercise") }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
            item {
                TextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }) else null,
                    placeholder = { Text("Search name, muscle or tag", maxLines = 1) },
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = TextFieldDefaults.colors(focusedIndicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f),
                        unfocusedIndicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0f)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                // Three scrollable rows like the original app: toggles + category, muscle, equipment (+ tags when any exist).
                // The selected pill leads its row so the active filter is always visible.
                val muscles = remember { vm.catalog.builtIn.map { it.target }.distinct().sorted() }
                Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillRow {
                        if (d.liked.isNotEmpty()) Pill(likedOnly, { likedOnly = !likedOnly }, { Text("Liked") },
                            leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp)) })
                        if (d.logs.isNotEmpty()) Pill(loggedOnly, { loggedOnly = !loggedOnly }, { Text("Logged") })
                        selectedFirst(Exercise.CATEGORIES, category).forEach { c ->
                            Pill(category == c, { category = if (category == c) "" else c }, { Text(jsTitle(c)) })
                        }
                    }
                    PillRow {
                        selectedFirst(muscles, target).forEach { m -> Pill(target == m, { target = if (target == m) "" else m }, { Text(jsTitle(m)) }) }
                    }
                    PillRow {
                        selectedFirst(vm.catalog.equipmentOptions, equipment).forEach { q -> Pill(equipment == q, { equipment = if (equipment == q) "" else q }, { Text(jsTitle(q)) }) }
                    }
                    if (tags.isNotEmpty()) PillRow {
                        selectedFirst(tags, tag).forEach { t -> Pill(tag.equals(t, true), { tag = if (tag.equals(t, true)) "" else t }, { Text("#$t") }) }
                    }
                }
            }
            if (list.isEmpty()) item { EmptyNote("No exercises match") }
            itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                val added = e.id in already
                GroupedItem(
                    i, list.size, modifier = Modifier.alpha(if (added) 0.5f else 1f)
                        .semantics { if (added) stateDescription = if (pick is Pick.ToRoutine) "Already in routine" else "Already in workout" },
                    onClick = { if (added) vm.message = "${exTitle(e)} is already ${if (pick is Pick.ToRoutine) "in this routine" else "in this workout"}" else choose(e) },
                    leading = { ExerciseImage(e, size = 56.dp) },
                    supporting = { Text(if (added) "Added" else jsTitle("${e.target} · ${e.equipment}") + if (e.custom) " · custom" else "") },
                    trailing = {
                        if (added) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                        else LikeButton(e.id in d.liked) { vm.toggleLike(e.id) }
                    },
                ) { Text(exTitle(e), style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
    if (creating) CustomExerciseDialog(vm, null) { created ->
        creating = false
        if (created != null) { if (pick != null) choose(created) else nav.push(Route.Detail(created.id)) }
    }
}

/** Create (existing == null) or edit a custom exercise; the web app's custom-exercise form. */
@Composable
fun CustomExerciseDialog(vm: FormStore, existing: Exercise?, onDone: (Exercise?) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var category by remember { mutableStateOf(existing?.category ?: "chest") }
    var equipment by remember { mutableStateOf(existing?.equipment ?: "body weight") }
    var target by remember { mutableStateOf(existing?.target?.takeIf { it != existing.category }.orEmpty()) }
    var description by remember { mutableStateOf(existing?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        icon = { Icon(if (existing == null) Icons.Rounded.Add else Icons.Rounded.Edit, null) },
        title = { Text(if (existing == null) "New exercise" else "Edit exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.OutlinedTextField(name, { name = it.take(80) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                MenuField("Body part", jsTitle(category), Exercise.CATEGORIES.map { it to jsTitle(it) }) { category = it }
                MenuField("Equipment", jsTitle(equipment), vm.catalog.equipmentOptions.map { it to jsTitle(it) }) { equipment = it }
                val muscles = remember { listOf("" to "Same as body part") + vm.catalog.builtIn.map { it.target }.distinct().sorted().map { it to jsTitle(it) } }
                MenuField("Target muscle", if (target.isEmpty()) "Same as body part" else jsTitle(target), muscles) { target = it }
                androidx.compose.material3.OutlinedTextField(description, { description = it.take(300) }, Modifier.fillMaxWidth(), label = { Text("Notes (optional)") }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                if (existing == null) vm.addCustomExercise(name, category, equipment, target, description)?.let(onDone)
                else if (vm.updateCustomExercise(existing.id, name, category, equipment, target, description)) onDone(vm.exercise(existing.id))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("Cancel") } },
    )
}

/** Read-only text field that opens a menu of options. */
@Composable
fun MenuField(label: String, value: String, options: List<Pair<String, String>>, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    androidx.compose.material3.ExposedDropdownMenuBox(open, { open = it }) {
        androidx.compose.material3.OutlinedTextField(value, {}, Modifier.fillMaxWidth().menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            readOnly = true, singleLine = true, label = { Text(label) },
            trailingIcon = { androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(open) })
        DropdownMenu(open, { open = false }, Modifier.exposedDropdownSize()) {
            options.forEach { (v, l) -> DropdownMenuItem(text = { Text(l) }, onClick = { onPick(v); open = false }) }
        }
    }
}

@Composable
fun LogRow(vm: FormStore, log: ProgressLog, index: Int, count: Int, showName: Boolean = true, onClick: (() -> Unit)? = null) {
    var confirm by remember { mutableStateOf(false) }
    GroupedItem(
        index, count, onClick = onClick,
        leading = if (showName) ({ ExerciseImage(vm.exercise(log.exerciseId), size = 56.dp) }) else null,
        supporting = { Text(WorkoutLogic.formatLog(log, vm.data.prefs.units) + if (log.notes.isNotEmpty()) " · ${log.notes}" else "") },
        trailing = { IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.Delete, "Delete log") } },
    ) { Text(if (showName) exTitle(vm.exercise(log.exerciseId)) else displayDate(log.date), style = MaterialTheme.typography.titleSmall) }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Delete this log?") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.deleteLog(log.id) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
}


/** One horizontally scrolling row of pills. */
@Composable
private fun PillRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    // Bleed past the list's 16 dp side padding so pills scroll in from the screen edge.
    Row(
        Modifier.layout { m, c ->
            val bleed = 16.dp.roundToPx()
            val p = m.measure(c.copy(maxWidth = c.maxWidth + bleed * 2, minWidth = 0))
            layout(c.maxWidth, p.height) { p.place(-bleed, 0) }
        }.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), content = content,
    )
}

private fun selectedFirst(options: List<String>, selected: String) =
    if (selected.isEmpty()) options else listOf(selected) + (options - selected)
