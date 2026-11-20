@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thehhr.form.AppViewModel
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
fun FuelTab(vm: AppViewModel) {
    var date by rememberSaveable { mutableStateOf(vm.todayKey) }
    var logging by remember { mutableStateOf(false) }
    val day = vm.data.history[date] ?: DayLog()
    val targets = Fuel.effective(vm.data.profile, vm.data.prefs.units)
    val cals = day.meals.sumOf { it.cals }
    val p = day.meals.sumOf { it.p }; val c = day.meals.sumOf { it.c }; val f = day.meals.sumOf { it.f }
    val isToday = date == vm.todayKey
    val ld = LocalDate.parse(date)

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = ld.minusDays(1).toString() }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous day") }
                Text(if (isToday) "Today" else ld.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                    Modifier.weight(1f).clickable { date = vm.todayKey }, style = MaterialTheme.typography.titleMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                IconButton(onClick = { date = ld.plusDays(1).toString() }, enabled = !isToday) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next day") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularWavyProgressIndicator(
                            progress = { (cals / targets.cals).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.size(128.dp),
                            stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f * androidx.compose.ui.platform.LocalDensity.current.density, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                            trackStroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 12f * androidx.compose.ui.platform.LocalDensity.current.density, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(jsNum(jsRound(cals).toDouble()), style = MaterialTheme.typography.headlineMediumEmphasized)
                            Text("of ${jsRound(targets.cals)} kcal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Macro("Protein", p, targets.p, MaterialTheme.colorScheme.primary)
                        Macro("Carbs", c, targets.c, FormColors.Carbs)
                        Macro("Fat", f, targets.f, FormColors.Fats)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = FormColors.Carbs)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${jsRound(day.water)} / ${jsRound(targets.water)} ml", style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(progress = { (day.water / targets.water).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 6.dp), color = FormColors.Carbs)
                    }
                    TextButton(onClick = { vm.addWater(date, -250.0) }, enabled = day.water > 0) { Text("−250") }
                    FilledTonalButton(onClick = { vm.addWater(date, 250.0) }) { Text("+250") }
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
                    GroupedItem(
                        i, meals.size,
                        supporting = { Text("${jsNum(m.p)}p · ${jsNum(m.c)}c · ${jsNum(m.f)}f") },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${jsRound(m.cals)}", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { vm.deleteMealEntry(date, m.id) }) { Icon(Icons.Rounded.Close, "Remove ${m.name}") }
                            }
                        },
                    ) { Text(m.name) }
                }
            }
        }
        if (day.meals.isEmpty()) item { EmptyNote("Nothing logged yet") }
    }
    if (logging) LogMealSheet(vm, date, day) { logging = false }
}

@Composable
private fun Macro(label: String, value: Double, target: Double, color: Color) {
    Column {
        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge); Text("${jsRound(value)} / ${jsRound(target)} g", style = MaterialTheme.typography.labelMedium) }
        LinearWavyProgressIndicator(progress = { (value / target.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 6.dp), color = color)
    }
}

@Composable
private fun LogMealSheet(vm: AppViewModel, date: String, day: DayLog, onClose: () -> Unit) {
    var slot by remember { mutableStateOf(Fuel.SLOTS.firstOrNull { s -> day.meals.none { it.category == s } } ?: "Snacks") }
    var mode by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Meal?>(null) }
    var grams by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var mp by remember { mutableStateOf("") }; var mc by remember { mutableStateOf("") }; var mf by remember { mutableStateOf("") }
    fun n(s: String) = jsNumber(s).takeIf { it.isFinite() && it >= 0 } ?: 0.0
    val num = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Log meal", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Fuel.SLOTS.forEach { s -> FilterChip(slot == s, { slot = s }, label = { Text(s) }) }
            }
            SecondaryTabRow(selectedTabIndex = mode) {
                Tab(mode == 0, { mode = 0 }, text = { Text("From library") })
                Tab(mode == 1, { mode = 1 }, text = { Text("Manual") })
            }
            if (mode == 0) {
                val meal = picked
                if (meal == null) {
                    OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), label = { Text("Search meals") }, singleLine = true)
                    val list = vm.data.meals.filter { it.name.contains(query.trim(), true) }
                        .sortedWith(compareByDescending<Meal> { it.liked }.thenBy { it.name.lowercase() })
                    if (list.isEmpty()) EmptyNote("No meals. Add some in Plans → Meals.")
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(list, key = { it.id }) { m ->
                            ListItem(headlineContent = { Text(m.name) }, supportingContent = { Text("${jsNum(m.cals100)} kcal / 100 g") },
                                modifier = Modifier.clickable { picked = m; grams = jsNum(m.defaultGrams) })
                        }
                    }
                } else {
                    Text(meal.name, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(grams, { grams = it }, label = { Text("Grams") }, singleLine = true, keyboardOptions = num)
                    val e = Fuel.entryFor(meal, n(grams), slot, "")
                    Text("${jsRound(e.cals)} kcal · ${jsNum(e.p)}p · ${jsNum(e.c)}c · ${jsNum(e.f)}f", color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { picked = null }) { Text("Back") }
                        Button(enabled = n(grams) > 0, onClick = { vm.logMeal(date, Fuel.entryFor(meal, n(grams), slot, vm.newMealEntryId())); onClose() }) { Text("Add") }
                    }
                }
            } else {
                OutlinedTextField(name, { name = it.take(40) }, Modifier.fillMaxWidth(), label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(mp, { mp = it }, Modifier.weight(1f), label = { Text("Protein g") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(mc, { mc = it }, Modifier.weight(1f), label = { Text("Carbs g") }, singleLine = true, keyboardOptions = num)
                    OutlinedTextField(mf, { mf = it }, Modifier.weight(1f), label = { Text("Fat g") }, singleLine = true, keyboardOptions = num)
                }
                val kcal = Fuel.kcal(n(mp), n(mc), n(mf))
                Text("${jsNum(kcal)} kcal", color = MaterialTheme.colorScheme.primary)
                Button(enabled = name.isNotBlank(), onClick = {
                    vm.logMeal(date, MealEntry(vm.newMealEntryId(), name.trim(), kcal, n(mp), n(mc), n(mf), slot)); onClose()
                }) { Text("Add") }
            }
        }
    }
}
