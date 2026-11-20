@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thehhr.form.AppViewModel
import com.thehhr.form.BuildConfig
import com.thehhr.form.data.Fuel
import com.thehhr.form.data.jsRound

@Composable
fun SettingsScreen(vm: AppViewModel, nav: Nav) {
    val prefs = vm.data.prefs
    val profile = vm.data.profile
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { FormTopBar("Settings", scroll, onBack = nav::pop) },
    ) { pad ->
        LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(16.dp, 0.dp, 16.dp, 32.dp)) {
            item { SectionTitle("Appearance") }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Accents.forEach { (name, colors) ->
                        val selected = prefs.accent == name
                        Box(
                            Modifier.size(38.dp)
                                .clip((if (selected) MaterialShapes.Cookie9Sided else MaterialShapes.Circle).toShape())
                                .background(colors.first)
                                .clickable { vm.setPrefs(prefs.copy(accent = name)) }
                                .semantics { role = Role.RadioButton; contentDescription = "Accent $name" + if (selected) ", selected" else "" },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = colors.second) }
                    }
                }
            }

            item { SectionTitle("Units") }
            item {
                val u = prefs.units
                // Disabled toggle groups hide the selection, so a locked choice is shown as a plain row.
                if (vm.unitsLocked) GroupedItem(0, 1, supporting = { Text("Locked once you have logs: stored values are not converted.") }) {
                    Text("${u.weight} · ${u.distance} · ${if (u.height == "ftin") "ft / in" else "cm"}", style = MaterialTheme.typography.titleMediumEmphasized)
                } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceGroup(listOf("kg" to "kg", "lb" to "lb"), u.weight, { vm.setPrefs(prefs.copy(units = u.copy(weight = it))) })
                    ChoiceGroup(listOf("km" to "km", "mi" to "mi"), u.distance, { vm.setPrefs(prefs.copy(units = u.copy(distance = it))) })
                    ChoiceGroup(listOf("cm" to "cm", "ftin" to "ft / in"), u.height, { vm.setPrefs(prefs.copy(units = u.copy(height = it))) })
                }
            }

            item { SectionTitle("Training") }
            item {
                Text("Week starts on", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
                ChoiceGroup(listOf(1 to "Mon", 6 to "Sat", 0 to "Sun"), prefs.weekStart, { vm.setPrefs(prefs.copy(weekStart = it)) })
            }
            item { Box(Modifier.padding(top = 12.dp)) }
            item { SwitchItem(0, 4, "Rest timer", "Countdown after each set", prefs.restEnabled) { vm.setPrefs(prefs.copy(restEnabled = it)) } }
            item { SwitchItem(1, 4, "Rest sound", "Beep when rest ends", prefs.restSound) { vm.setPrefs(prefs.copy(restSound = it)) } }
            item { ValueItem(2, 4, "Rest between sets", "${jsRound(prefs.restBetweenSets)} s", prefs.restBetweenSets, 30.0..180.0) { vm.setPrefs(prefs.copy(restBetweenSets = it)) } }
            item { ValueItem(3, 4, "Rest between exercises", "${jsRound(prefs.restBetweenExercises)} s", prefs.restBetweenExercises, 30.0..180.0) { vm.setPrefs(prefs.copy(restBetweenExercises = it)) } }

            item { SectionTitle("Body") }
            item { ChoiceGroup(listOf("m" to "Male", "f" to "Female"), if (profile.sex == "f") "f" else "m", { vm.setProfile(profile.copy(sex = it)) }, Modifier.padding(bottom = 12.dp)) }
            val hUnit = if (prefs.units.height == "ftin") "in" else "cm"
            val w = prefs.units.weight
            item { ValueItem(0, 7, "Age", num(profile.age), profile.age, 10.0..110.0) { vm.setProfile(profile.copy(age = it)) } }
            item { ValueItem(1, 7, "Height", "${num(profile.heightCm)} $hUnit", profile.heightCm, if (hUnit == "in") 20.0..120.0 else 50.0..300.0) { vm.setProfile(profile.copy(heightCm = it)) } }
            item { ValueItem(2, 7, "Current weight", "${num(profile.currentWeight)} $w", profile.currentWeight, 20.0..1100.0) { vm.setProfile(profile.copy(currentWeight = it)) } }
            item { ValueItem(3, 7, "Goal weight", "${num(profile.goalWeight)} $w", profile.goalWeight, 20.0..1100.0) { vm.setProfile(profile.copy(goalWeight = it)) } }
            item { ValueItem(4, 7, "Activity level", num(profile.activity), profile.activity, 1.0..3.0) { vm.setProfile(profile.copy(activity = it)) } }
            item { ValueItem(5, 7, "Calorie strategy", "${if (profile.strategy > 0) "+" else ""}${jsRound(profile.strategy)} kcal", profile.strategy, -1000.0..1000.0) { vm.setProfile(profile.copy(strategy = it)) } }
            item { ValueItem(6, 7, "Protein", "${num(profile.proteinRate)} g per kg", profile.proteinRate, 0.5..5.0) { vm.setProfile(profile.copy(proteinRate = it)) } }
            item {
                val t = Fuel.effective(profile, prefs.units)
                Card(Modifier.fillMaxWidth().padding(top = 12.dp), shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Daily targets", style = MaterialTheme.typography.labelLargeEmphasized, color = MaterialTheme.colorScheme.primary)
                        Text("${jsRound(t.cals)} kcal", style = MaterialTheme.typography.headlineMediumEmphasized)
                        Text("${jsRound(t.p)} g protein · ${jsRound(t.c)} g carbs · ${jsRound(t.f)} g fat · ${jsRound(t.water)} ml water",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (profile.overrides.isNotEmpty()) TextButton(onClick = { vm.setProfile(profile.copy(overrides = emptyMap())) }) { Text("Reset custom targets") }
                    }
                }
            }

            item { SectionTitle("Data") }
            item {
                GroupedItem(0, 2, supporting = { Text(vm.folderName.ifEmpty { "—" }) },
                    trailing = { FolderPicker(vm) { launch -> FilledTonalButton(onClick = launch) { Text("Change") } } }) { Text("Vault folder") }
            }
            item { GroupedItem(1, 2, supporting = { Text("Native · Kotlin + Compose") }) { Text("Form ${BuildConfig.VERSION_NAME}") } }
        }
    }
}

@Composable
private fun SwitchItem(index: Int, count: Int, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    GroupedItem(index, count, onClick = { onChange(!checked) }, supporting = { Text(subtitle) },
        trailing = { Switch(checked, onCheckedChange = null) }) { Text(title) }
}

/** Grouped row showing a value; tap to type a new one. */
@Composable
private fun ValueItem(index: Int, count: Int, title: String, display: String, value: Double, range: ClosedFloatingPointRange<Double>, onSet: (Double) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    GroupedItem(index, count, onClick = { editing = true },
        trailing = { Text(display, style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary) }) { Text(title) }
    if (editing) NumberDialog(title, value, range, { editing = false }, onSet)
}
