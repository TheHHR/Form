@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.material3.CardDefaults

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Close
import androidx.compose.animation.animateContentSize
import com.thehhr.form.data.isSkipPlaceholder
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.rounded.PhotoCamera
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thehhr.form.FormStore
import com.thehhr.form.data.DayLog
import com.thehhr.form.data.Fuel
import com.thehhr.form.data.Meal
import com.thehhr.form.data.MealEntry
import com.thehhr.form.data.jsNum
import com.thehhr.form.data.jsNumber
import com.thehhr.form.data.jsRound
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun FuelTab(vm: FormStore) {
    var date by rememberSaveable { mutableStateOf(vm.todayKey) }
    var logging by remember { mutableStateOf(false) }
    LaunchedEffect(vm.pendingDestination) { if (vm.pendingDestination == "Fuel:log") { vm.pendingDestination = null; date = vm.todayKey; logging = true } }
    val day = vm.data.history[date] ?: DayLog()
    val targets = Fuel.effective(vm.data.profile, vm.data.prefs.units)
    val cals = day.meals.sumOf { it.cals }
    val p = day.meals.sumOf { it.p }; val c = day.meals.sumOf { it.c }; val f = day.meals.sumOf { it.f }
    val isToday = date == vm.todayKey
    val ld = LocalDate.parse(date)

    // No spacedBy: grouped meal rows bring their own 2 dp gap; other items pad themselves.
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
        item {
            PeriodNav(if (isToday) "Today" else displayDate(date), "day", { date = ld.minusDays(1).toString() },
                { date = ld.plusDays(1).toString() }, !isToday, onLabel = { date = vm.todayKey })
        }
        item {
            // Hero: calories left, with the ring; macros and water as matching tonal tiles below.
            val left = targets.cals - cals
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Calories", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                        Text("${jsRound(kotlin.math.abs(left))}", style = MaterialTheme.typography.displaySmallEmphasized)
                        Text(if (left >= 0) "kcal left of ${jsRound(targets.cals)}" else "kcal over ${jsRound(targets.cals)}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(progress = eased((cals / targets.cals).toFloat()).let { v -> { v } }, modifier = Modifier.size(96.dp))
                        Text("${jsRound(cals / targets.cals * 100)}%", style = MaterialTheme.typography.titleMediumEmphasized)
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Macro("Protein", p, targets.p, MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                Macro("Carbs", c, targets.c, FormColors.Carbs, Modifier.weight(1f))
                Macro("Fat", f, targets.f, FormColors.Fats, Modifier.weight(1f))
            }
        }
        item {
            Card(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp), shape = MaterialTheme.shapes.extraLarge,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(androidx.compose.material3.MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center) { Icon(Icons.Rounded.LocalDrink, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Water", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                            Text("${jsRound(day.water)} / ${jsRound(targets.water)} ml", style = MaterialTheme.typography.titleMediumEmphasized)
                        }
                        androidx.compose.material3.FilledTonalIconButton(onClick = { vm.addWater(date, -250.0) }, enabled = day.water > 0,
                            shapes = androidx.compose.material3.IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Remove, "Remove 250 ml") }
                        Spacer(Modifier.width(8.dp))
                        androidx.compose.material3.FilledIconButton(onClick = { vm.addWater(date, 250.0) },
                            shapes = androidx.compose.material3.IconButtonDefaults.shapes()) { Icon(Icons.Rounded.Add, "Add 250 ml") }
                    }
                    LinearWavyProgressIndicator(progress = eased((day.water / targets.water).toFloat()).let { v -> { v } }, Modifier.fillMaxWidth())
                }
            }
        }
        item {
            Button(
                onClick = { logging = true }, shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight),
            ) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(8.dp)); Text("Log meal", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight)) }
        }
        val cats = Fuel.SLOTS + day.meals.map { it.category }.distinct().filter { it !in Fuel.SLOTS }
        cats.forEach { cat ->
            val meals = day.meals.filter { it.category == cat }
            if (meals.isNotEmpty()) {
                item(key = "h-$cat") { SectionTitle("$cat · ${jsRound(meals.sumOf { it.cals })} kcal") }
                itemsIndexed(meals, key = { _, m -> "m-${m.id}-${m.name}" }) { i, m ->
                    val skipped = m.isSkipPlaceholder
                    GroupedItem(
                        i, meals.size, modifier = Modifier.animateItem(),
                        supporting = if (skipped) null else ({ Text("${jsNum(m.p)}p · ${jsNum(m.c)}c · ${jsNum(m.f)}f") }),
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!skipped) Text("${jsRound(m.cals)}", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { vm.deleteMealEntry(date, m.id) }) { Icon(Icons.Rounded.Close, "Remove ${m.name}") }
                            }
                        },
                    ) { Text(m.name, color = if (skipped) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified) }
                }
            }
        }
        if (day.meals.isEmpty()) item { EmptyNote("Nothing logged yet") }
    }
    if (logging) LogMealSheet(vm, date, day) { logging = false }
}

@Composable
private fun Macro(label: String, value: Double, target: Double, color: Color, modifier: Modifier) {
    Card(modifier, shape = MaterialTheme.shapes.large, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized, color = color)
            Text("${jsRound(value)}", style = MaterialTheme.typography.titleLargeEmphasized)
            Text("of ${jsRound(target)} g", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearWavyProgressIndicator(progress = eased((value / target.coerceAtLeast(1.0)).toFloat()).let { v -> { v } }, Modifier.fillMaxWidth(), color = color)
        }
    }
}

@Composable
private fun LogMealSheet(vm: FormStore, date: String, day: DayLog, onClose: () -> Unit) {
    var slot by remember { mutableStateOf(Fuel.SLOTS.firstOrNull { s -> day.meals.none { it.category == s } } ?: "Snacks") }
    var mode by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Meal?>(null) }
    var grams by remember { mutableStateOf(100.0) }
    var name by remember { mutableStateOf("") }
    var mp by remember { mutableStateOf(0.0) }; var mc by remember { mutableStateOf(0.0) }; var mf by remember { mutableStateOf(0.0) }
    val addLabel = "Add to $slot"
    FormSheet(onDismissRequest = onClose) {
        // Height follows the content with the theme's spring, so switching tabs grows/shrinks the sheet instead of jumping.
        Column(Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()).imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("Log meal", Modifier.weight(1f), style = MaterialTheme.typography.titleMediumEmphasized)
                Text("Hold a meal to skip it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val view = androidx.compose.ui.platform.LocalView.current
            // Web parity: holding Breakfast / Lunch / Dinner logs a zero "Skipped" entry for that slot.
            ChoiceGroup(Fuel.SLOTS.map { it to it }, slot, { slot = it }, longPressLabel = "Skip this meal", onLongPress = { s ->
                if (s == "Snacks") return@ChoiceGroup
                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                vm.skipMeal(date, s); onClose()
            })
            if (mode == 0) {
                val meal = picked
                if (meal == null) {
                    androidx.compose.material3.TextField(
                        query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                        leadingIcon = { Icon(androidx.compose.material.icons.Icons.Rounded.Search, null) },
                        placeholder = { Text("Search meals") }, shape = MaterialTheme.shapes.extraLarge,
                        colors = androidx.compose.material3.TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                    )
                    val list = vm.data.meals.filter { it.name.contains(query.trim(), true) }
                        .sortedWith(compareByDescending<Meal> { it.liked }.thenBy { it.name.lowercase() })
                    if (list.isEmpty()) EmptyNote("No meals yet. Tap Add to log one by hand, or save some in Plans → Meals.")
                    // Library first; a FAB opens manual entry (name + macros) for anything not in the library.
                    Box(Modifier.fillMaxWidth()) {
                    LazyColumn(Modifier.heightIn(max = 360.dp), contentPadding = PaddingValues(bottom = 72.dp)) {
                        itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
                            GroupedItem(i, list.size, onClick = { picked = m; grams = m.defaultGrams },
                                supporting = { Text("${jsNum(m.defaultGrams)} g serving") },
                                trailing = { Text("${jsNum(m.cals100)}", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary) },
                            ) { Text(m.name) }
                        }
                    }
                    androidx.compose.material3.ExtendedFloatingActionButton(onClick = { mode = 1 }, icon = { Icon(Icons.Rounded.Add, null) },
                        text = { Text("Add") }, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
                    }
                } else {
                    val e = Fuel.entryFor(meal, grams, slot, "")
                    androidx.compose.material3.Card(shape = MaterialTheme.shapes.extraLarge,
                        colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(meal.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2)
                            Text("${jsRound(e.cals)} kcal", style = MaterialTheme.typography.headlineMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                            Text("${jsNum(e.p)} g protein · ${jsNum(e.c)} g carbs · ${jsNum(e.f)} g fat", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("Grams", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
                    StepperField(grams, "grams", 10.0, 1.0..5000.0, { grams = it }, Modifier.fillMaxWidth())
                    Button(onClick = { vm.logMeal(date, Fuel.entryFor(meal, grams, slot, vm.newMealEntryId())); onClose() },
                        shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)) {
                        Text(addLabel, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                    }
                    TextButton(onClick = { picked = null }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Choose another meal") }
                }
            } else {
                OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Protein g", "Carbs g", "Fat g").forEach {
                        Text(it, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepperField(mp, "protein grams", 5.0, 0.0..1000.0, { mp = it }, Modifier.weight(1f))
                    StepperField(mc, "carb grams", 5.0, 0.0..1000.0, { mc = it }, Modifier.weight(1f))
                    StepperField(mf, "fat grams", 1.0, 0.0..1000.0, { mf = it }, Modifier.weight(1f))
                }
                val kcal = Fuel.kcal(mp, mc, mf)
                Text("${jsNum(kcal)} kcal", style = MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.primary)
                Button(enabled = name.isNotBlank(), onClick = {
                    vm.logMeal(date, MealEntry(vm.newMealEntryId(), name.trim(), kcal, mp, mc, mf, slot)); onClose()
                }, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(ButtonDefaults.MediumContainerHeight)) {
                    Text(addLabel, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
                TextButton(onClick = { mode = 0 }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Back to library") }
            }
        }
    }
}

