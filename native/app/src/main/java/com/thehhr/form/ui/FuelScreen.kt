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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.thehhr.form.data.FoodLookup
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
    val day = vm.data.history[date] ?: DayLog()
    val targets = Fuel.effective(vm.data.profile, vm.data.prefs.units)
    val cals = day.meals.sumOf { it.cals }
    val p = day.meals.sumOf { it.p }; val c = day.meals.sumOf { it.c }; val f = day.meals.sumOf { it.f }
    val isToday = date == vm.todayKey
    val ld = LocalDate.parse(date)

    // No spacedBy: grouped meal rows bring their own 2 dp gap; other items pad themselves.
    LazyColumn(contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 24.dp)) {
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
            Card(Modifier.fillMaxWidth().padding(vertical = 12.dp), shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WaterDrop, null, tint = FormColors.Carbs)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${jsRound(day.water)} / ${jsRound(targets.water)} ml", style = MaterialTheme.typography.titleSmall)
                        LinearProgressIndicator(progress = { (day.water / targets.water).toFloat().coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 6.dp), color = FormColors.Carbs)
                    }
                    TextButton(onClick = { vm.addWater(date, -250.0) }, enabled = day.water > 0) { Text("−250") }
                    FilledTonalButton(onClick = { vm.addWater(date, 250.0) }, shapes = ButtonDefaults.shapes()) { Text("+250") }
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
private fun LogMealSheet(vm: FormStore, date: String, day: DayLog, onClose: () -> Unit) {
    var slot by remember { mutableStateOf(Fuel.SLOTS.firstOrNull { s -> day.meals.none { it.category == s } } ?: "Snacks") }
    var mode by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<Meal?>(null) }
    var grams by remember { mutableStateOf(100.0) }
    var name by remember { mutableStateOf("") }
    var mp by remember { mutableStateOf(0.0) }; var mc by remember { mutableStateOf(0.0) }; var mf by remember { mutableStateOf(0.0) }
    val addLabel = "Add to $slot"
    ModalBottomSheet(onDismissRequest = onClose, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Log meal", style = MaterialTheme.typography.titleMediumEmphasized)
            ChoiceGroup(Fuel.SLOTS.map { it to it }, slot, { slot = it })
            ChoiceGroup(listOf(0 to "Library", 1 to "Manual", 2 to "Barcode"), mode, { mode = it })
            if (mode == 2) {
                BarcodePane(vm) { meal, serving -> picked = meal; grams = serving; mode = 0 }
            } else if (mode == 0) {
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
                    if (list.isEmpty()) EmptyNote("No meals yet. Add some in Plans → Meals, or try Manual or Barcode.")
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        itemsIndexed(list, key = { _, m -> m.id }) { i, m ->
                            GroupedItem(i, list.size, onClick = { picked = m; grams = m.defaultGrams },
                                supporting = { Text("${jsNum(m.defaultGrams)} g serving") },
                                trailing = { Text("${jsNum(m.cals100)}", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary) },
                            ) { Text(m.name) }
                        }
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
            }
        }
    }
}

/** Scan with Google's code scanner (no camera permission needed) or type the code; looks it up on Open Food Facts. */
@Composable
private fun BarcodePane(vm: FormStore, onFound: (Meal, Double) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun lookup(c: String) {
        if (!FoodLookup.validBarcode(c)) { error = "Barcodes are 8–14 digits"; return }
        busy = true; error = null
        scope.launch {
            val found = withContext(Dispatchers.IO) { FoodLookup.lookup(c) }
            busy = false
            if (found == null) { error = "Not found on Open Food Facts (or offline). Add it manually instead."; return@launch }
            // Keep the user's edits if this barcode was saved before.
            val meal = vm.data.meals.firstOrNull { it.id == found.meal.id } ?: found.meal.also(vm::saveMeal)
            onFound(meal, found.servingGrams)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick = {
                error = null
                val options = GmsBarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E)
                    .enableAutoZoom().build()
                GmsBarcodeScanning.getClient(ctx, options).startScan()
                    .addOnSuccessListener { b -> b.rawValue?.let { code = it; lookup(it) } }
                    .addOnFailureListener { error = "Scanner unavailable (needs Google Play services). Type the code below." }
            },
            enabled = !busy, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth(),
        ) { Icon(Icons.Rounded.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scan barcode") }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(code, { code = it.filter(Char::isDigit).take(14) }, Modifier.weight(1f), label = { Text("Or type the code") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            FilledTonalButton(onClick = { lookup(code) }, enabled = !busy && code.isNotEmpty(), shapes = ButtonDefaults.shapes()) { Text("Look up") }
        }
        if (busy) LoadingIndicator(Modifier.align(Alignment.CenterHorizontally))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("Food data © Open Food Facts contributors (ODbL).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
