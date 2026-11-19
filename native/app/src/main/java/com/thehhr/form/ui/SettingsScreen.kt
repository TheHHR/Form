@file:OptIn(ExperimentalMaterial3Api::class)

package com.thehhr.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.thehhr.form.AppViewModel
import com.thehhr.form.BuildConfig
import com.thehhr.form.data.Fuel
import com.thehhr.form.data.Units
import com.thehhr.form.data.jsNum
import com.thehhr.form.data.jsNumber
import com.thehhr.form.data.jsRound

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Nav) {
    val prefs = vm.data.prefs
    val profile = vm.data.profile
    Scaffold(topBar = { BackBar("Settings", nav) }) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { SectionTitle("Appearance") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Accents.forEach { (name, colors) ->
                        val selected = prefs.accent == name
                        Box(
                            Modifier.size(36.dp).background(colors.first, CircleShape)
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { vm.setPrefs(prefs.copy(accent = name)) }
                                .semantics { contentDescription = "Accent $name" + if (selected) ", selected" else "" },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = colors.second) }
                    }
                }
            }

            item { SectionTitle("Units") }
            item {
                if (vm.unitsLocked) Text("Units are locked once you have logs (stored values are not converted).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Segmented(listOf("kg", "lb"), prefs.units.weight, !vm.unitsLocked) { vm.setPrefs(prefs.copy(units = prefs.units.copy(weight = it))) }
            }
            item { Segmented(listOf("km", "mi"), prefs.units.distance, !vm.unitsLocked) { vm.setPrefs(prefs.copy(units = prefs.units.copy(distance = it))) } }
            item { Segmented(listOf("cm", "ftin"), prefs.units.height, !vm.unitsLocked) { vm.setPrefs(prefs.copy(units = prefs.units.copy(height = it))) } }

            item { SectionTitle("Training") }
            item {
                Text("Week starts on", style = MaterialTheme.typography.labelLarge)
                val opts = mapOf(1 to "Monday", 6 to "Saturday", 0 to "Sunday")
                Segmented(opts.values.toList(), opts[prefs.weekStart] ?: "Monday", true) { label ->
                    vm.setPrefs(prefs.copy(weekStart = opts.entries.first { it.value == label }.key))
                }
            }
            item { SwitchRow("Rest timer", prefs.restEnabled) { vm.setPrefs(prefs.copy(restEnabled = it)) } }
            item { SwitchRow("Rest sound", prefs.restSound) { vm.setPrefs(prefs.copy(restSound = it)) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stepper("between sets", "${jsRound(prefs.restBetweenSets)}s",
                        { vm.setPrefs(prefs.copy(restBetweenSets = (prefs.restBetweenSets - 15).coerceAtLeast(30.0))) },
                        { vm.setPrefs(prefs.copy(restBetweenSets = (prefs.restBetweenSets + 15).coerceAtMost(180.0))) })
                    Stepper("between exercises", "${jsRound(prefs.restBetweenExercises)}s",
                        { vm.setPrefs(prefs.copy(restBetweenExercises = (prefs.restBetweenExercises - 15).coerceAtLeast(30.0))) },
                        { vm.setPrefs(prefs.copy(restBetweenExercises = (prefs.restBetweenExercises + 15).coerceAtMost(180.0))) })
                }
            }

            item { SectionTitle("Body & targets") }
            item {
                Segmented(listOf("Male", "Female"), if (profile.sex == "f") "Female" else "Male", true) {
                    vm.setProfile(profile.copy(sex = if (it == "Female") "f" else "m"))
                }
            }
            item {
                val hUnit = if (prefs.units.height == "ftin") "in" else "cm"
                NumberRow("Age", profile.age, 10.0..110.0) { vm.setProfile(profile.copy(age = it)) }
                NumberRow("Height ($hUnit)", profile.heightCm, if (hUnit == "in") 20.0..120.0 else 50.0..300.0) { vm.setProfile(profile.copy(heightCm = it)) }
                NumberRow("Current weight (${prefs.units.weight})", profile.currentWeight, 20.0..1100.0) { vm.setProfile(profile.copy(currentWeight = it)) }
                NumberRow("Goal weight (${prefs.units.weight})", profile.goalWeight, 20.0..1100.0) { vm.setProfile(profile.copy(goalWeight = it)) }
                NumberRow("Activity (1.2–1.9)", profile.activity, 1.0..3.0) { vm.setProfile(profile.copy(activity = it)) }
                NumberRow("Calorie strategy (kcal ±)", profile.strategy, -1000.0..1000.0) { vm.setProfile(profile.copy(strategy = it)) }
                NumberRow("Protein (g per kg)", profile.proteinRate, 0.5..5.0) { vm.setProfile(profile.copy(proteinRate = it)) }
            }
            item {
                val t = Fuel.effective(profile, prefs.units)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Daily targets", style = MaterialTheme.typography.labelLarge)
                        Text("${jsRound(t.cals)} kcal · ${jsRound(t.p)}p · ${jsRound(t.c)}c · ${jsRound(t.f)}f · ${jsRound(t.water)} ml water")
                        if (profile.overrides.isNotEmpty()) TextButton(onClick = { vm.setProfile(profile.copy(overrides = emptyMap())) }) { Text("Reset custom targets") }
                    }
                }
            }

            item { SectionTitle("Data") }
            item {
                ListItem(headlineContent = { Text("Vault folder") }, supportingContent = { Text(vm.folderName.ifEmpty { "—" }) },
                    trailingContent = { FolderPicker(vm) { launch -> FilledTonalButton(onClick = launch) { Text("Change") } } })
            }
            item {
                Text("Form ${BuildConfig.VERSION_NAME} · native", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}

@Composable
private fun Segmented(options: List<String>, selected: String, enabled: Boolean, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, o ->
            SegmentedButton(selected = selected == o, onClick = { onSelect(o) }, enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(i, options.size)) { Text(if (o == "ftin") "ft/in" else o) }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

/** Number field that commits on valid input within range. */
@Composable
private fun NumberRow(label: String, value: Double, range: ClosedFloatingPointRange<Double>, onCommit: (Double) -> Unit) {
    var text by remember { mutableStateOf(jsNum(value)) }
    // Follow outside changes without clobbering what's being typed ("1." stays "1.").
    androidx.compose.runtime.LaunchedEffect(value) { if (jsNumber(text) != value) text = jsNum(value) }
    val n = jsNumber(text)
    val valid = n.isFinite() && n in range
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; val v = jsNumber(it); if (v.isFinite() && v in range) onCommit(v) },
        label = { Text(label) }, singleLine = true, isError = !valid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}
