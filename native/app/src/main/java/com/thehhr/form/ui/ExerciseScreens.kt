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
    val d = vm.data
    val logged = remember(d.logs) { d.logs.map { it.exerciseId }.toSet() }
    val list = remember(query, category, target, equipment, likedOnly, loggedOnly, d.liked, logged, d.customExercises, d.exerciseTags) {
        vm.catalog.all.filter { e ->
            (category.isEmpty() || e.category == category) && (target.isEmpty() || e.target == target) &&
                (equipment.isEmpty() || e.equipment == equipment) &&
                (!likedOnly || e.id in d.liked) && (!loggedOnly || e.id in logged) &&
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
                // Three scrollable rows like the original app: toggles + category, muscle, equipment.
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
fun LogRow(vm: FormStore, log: ProgressLog, index: Int, count: Int, showName: Boolean = true) {
    var confirm by remember { mutableStateOf(false) }
    GroupedItem(
        index, count,
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
