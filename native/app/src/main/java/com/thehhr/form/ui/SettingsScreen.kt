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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.thehhr.form.HealthSync
import com.thehhr.form.Reminders
import kotlinx.coroutines.launch
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
import com.thehhr.form.FormStore
import com.thehhr.form.BuildConfig
import com.thehhr.form.data.Fuel
import com.thehhr.form.data.jsRound

@Composable
fun SettingsScreen(vm: FormStore, nav: Nav) {
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
            item { Spacer(Modifier.height(12.dp)) }
            item { SwitchItem(0, 4, "Rest timer", "Countdown after each set", prefs.restEnabled) { vm.setPrefs(prefs.copy(restEnabled = it)) } }
            item { SwitchItem(1, 4, "Rest sound", "Beep when rest ends", prefs.restSound) { vm.setPrefs(prefs.copy(restSound = it)) } }
            item { ValueItem(2, 4, "Rest between sets", "${jsRound(prefs.restBetweenSets)} s", prefs.restBetweenSets, 30.0..180.0) { vm.setPrefs(prefs.copy(restBetweenSets = it)) } }
            item { ValueItem(3, 4, "Rest between exercises", "${jsRound(prefs.restBetweenExercises)} s", prefs.restBetweenExercises, 30.0..180.0) { vm.setPrefs(prefs.copy(restBetweenExercises = it)) } }

            item { SectionTitle("Reminders") }
            item { ReminderSettings(vm) }

            item { SectionTitle("Health Connect") }
            item { HealthSettings(vm) }

            item { SectionTitle("Body") }
            item { ChoiceGroup(listOf("m" to "Male", "f" to "Female"), if (profile.sex == "f") "f" else "m", { vm.setProfile(profile.copy(sex = it)) }, Modifier.padding(bottom = 12.dp)) }
            val hUnit = if (prefs.units.height == "ftin") "in" else "cm"
            val w = prefs.units.weight
            item { ValueItem(0, 7, "Age", num(profile.age), profile.age, 10.0..110.0) { vm.setProfile(profile.copy(age = it)) } }
            item { ValueItem(1, 7, "Height", "${num(profile.heightCm)} $hUnit", profile.heightCm, if (hUnit == "in") 20.0..120.0 else 50.0..300.0) { vm.setProfile(profile.copy(heightCm = it)) } }
            item { ValueItem(2, 7, "Current weight", "${num(profile.currentWeight)} $w", profile.currentWeight, 20.0..1100.0) { vm.setProfile(profile.copy(currentWeight = it)) } }
            item { ValueItem(3, 7, "Goal weight", "${num(profile.goalWeight)} $w", profile.goalWeight, 20.0..1100.0) { vm.setProfile(profile.copy(goalWeight = it)) } }
            item { OptionItem(4, 7, "Activity level", ACTIVITY, profile.activity) { vm.setProfile(profile.copy(activity = it)) } }
            item { OptionItem(5, 7, "Calorie goal", STRATEGY, profile.strategy) { vm.setProfile(profile.copy(strategy = it)) } }
            item { OptionItem(6, 7, "Protein target", PROTEIN, profile.proteinRate) { vm.setProfile(profile.copy(proteinRate = it)) } }

            item { SectionTitle("Daily targets") }
            val calc = Fuel.calculated(profile, prefs.units)
            val eff = Fuel.effective(profile, prefs.units)
            val targets = listOf("cals" to "Calories" to "kcal", "p" to "Protein" to "g", "c" to "Carbs" to "g", "f" to "Fat" to "g", "water" to "Water" to "ml")
            targets.forEachIndexed { i, (kn, unit) ->
                val (key, name) = kn
                item { TargetItem(vm, i, targets.size, key, name, unit, eff[key], calc[key]) }
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

// The original app's choices (www/index.html).
private val ACTIVITY = listOf(
    1.2 to ("Sedentary" to "Desk job, no workouts, <5,000 steps/day"),
    1.375 to ("Light" to "Desk job + gym 1–3 days/wk or ~7,000 steps"),
    1.55 to ("Moderate" to "Desk job + gym 3–5 days/wk or ~10,000 steps"),
    1.725 to ("Heavy" to "Active job + gym 5–6 days/wk or ~13,000 steps"),
    1.9 to ("Athlete / Extreme" to "Physical job + hard daily training"),
)
private val STRATEGY = listOf(
    -500.0 to ("Cut" to "−500 kcal · ~0.5 kg loss/wk"),
    -250.0 to ("Mild cut" to "−250 kcal · ~0.25 kg loss/wk"),
    0.0 to ("Maintain" to "0 kcal · weight maintenance"),
    250.0 to ("Lean bulk" to "+250 kcal · clean muscle gain"),
    500.0 to ("Aggressive bulk" to "+500 kcal · maximum growth"),
)
private val PROTEIN = listOf(
    2.0 to ("2.0 g/kg · Muscle growth" to "Optimal hypertrophy"),
    2.2 to ("2.2 g/kg · Fat loss / cut" to "Preserves muscle in a deficit"),
    1.8 to ("1.8 g/kg · Maintenance" to "Balanced fitness"),
    1.6 to ("1.6 g/kg · Baseline / endurance" to "General health"),
)

/** Grouped row that opens a sheet of choices. A vault value outside the list shows as "Custom" and is left alone. */
@Composable
private fun OptionItem(index: Int, count: Int, title: String, options: List<Pair<Double, Pair<String, String>>>, value: Double, onPick: (Double) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == value }?.second?.first ?: "Custom (${num(value)})"
    GroupedItem(index, count, onClick = { open = true }, supporting = { Text(current) }) { Text(title) }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.padding(start = 4.dp, bottom = 12.dp))
            options.forEachIndexed { i, (v, labels) ->
                val selected = v == value
                androidx.compose.material3.SegmentedListItem(
                    selected = selected, onClick = { onPick(v); open = false },
                    shapes = androidx.compose.material3.ListItemDefaults.segmentedShapes(i, options.size),
                    modifier = Modifier.padding(bottom = if (i < options.lastIndex) androidx.compose.material3.ListItemDefaults.SegmentedGap else 0.dp),
                    supportingContent = { Text(labels.second) },
                    trailingContent = { if (selected) Icon(Icons.Rounded.Check, "Selected") },
                ) { Text(labels.first) }
            }
        }
    }
}

/** Daily target row: calculated (Auto) unless overridden (Custom). Tap to set; "Use calculated" clears the override. */
@Composable
private fun TargetItem(vm: FormStore, index: Int, count: Int, key: String, name: String, unit: String, value: Double, calculated: Double) {
    var editing by remember { mutableStateOf(false) }
    val custom = vm.data.profile.overrides[key] != null
    GroupedItem(index, count, onClick = { editing = true }, supporting = { Text(if (custom) "Custom · calculated ${jsRound(calculated)} $unit" else "Auto") },
        trailing = { Text("${jsRound(value)} $unit", style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary) }) { Text(name) }
    if (editing) {
        val range = Fuel.LIMITS.getValue(key)
        var text by remember { mutableStateOf(jsRound(value).toString()) }
        val n = com.thehhr.form.data.jsNumber(text)
        val ok = n.isFinite() && n in range
        fun set(v: Double?) {
            val p = vm.data.profile
            vm.setProfile(p.copy(overrides = if (v == null) p.overrides - key else p.overrides + (key to jsRound(v).toDouble())))
            editing = false
        }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("$name target") },
            text = {
                androidx.compose.material3.OutlinedTextField(text, { text = it }, singleLine = true, isError = !ok, suffix = { Text(unit) },
                    supportingText = { Text("Calculated: ${jsRound(calculated)} $unit") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
            },
            confirmButton = { TextButton(onClick = { set(n) }, enabled = ok) { Text("Set") } },
            dismissButton = { TextButton(onClick = { set(null) }) { Text("Use calculated") } },
        )
    }
}

@Composable
private fun ReminderSettings(vm: FormStore) {
    val ctx = LocalContext.current
    var enabled by remember { mutableStateOf(vm.device.reminderEnabled) }
    var minutes by remember { mutableIntStateOf(vm.device.reminderMinutes) }
    var picking by remember { mutableStateOf(false) }
    fun apply() = Reminders.schedule(ctx, vm.device)
    SwitchItem(0, 2, "Workout reminder", "On days with a scheduled routine, if you haven't trained yet", enabled) {
        enabled = it; vm.device.reminderEnabled = it; apply()
    }
    GroupedItem(1, 2, onClick = { picking = true },
        trailing = { Text("%02d:%02d".format(minutes / 60, minutes % 60), style = MaterialTheme.typography.titleMediumEmphasized, color = MaterialTheme.colorScheme.primary) }) {
        Text("Reminder time")
    }
    if (picking) {
        val state = rememberTimePickerState(minutes / 60, minutes % 60, android.text.format.DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = { TextButton(onClick = {
                minutes = state.hour * 60 + state.minute; vm.device.reminderMinutes = minutes; apply(); picking = false
            }) { Text("Set") } },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun HealthSettings(vm: FormStore) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(vm.device.healthEnabled) }
    val available = remember { HealthSync.available(ctx) }
    val request = rememberLauncherForActivityResult(HealthSync.permissionContract()) { granted ->
        val ok = granted.containsAll(HealthSync.permissions)
        enabled = ok; vm.device.healthEnabled = ok
        vm.message = if (ok) "Health Connect connected" else "Health Connect permissions not granted"
    }
    if (!available) {
        GroupedItem(0, 1, supporting = { Text("Install or update Health Connect to sync workouts and meals.") }) { Text("Health Connect not available") }
        return
    }
    SwitchItem(0, 2, "Sync to Health Connect", "Write finished workouts and logged meals", enabled) { on ->
        if (!on) { enabled = false; vm.device.healthEnabled = false }
        else scope.launch {
            if (HealthSync.granted(ctx).containsAll(HealthSync.permissions)) { enabled = true; vm.device.healthEnabled = true }
            else request.launch(HealthSync.permissions)
        }
    }
    GroupedItem(1, 2, onClick = {
        scope.launch {
            val kg = HealthSync.latestWeightKg(ctx)
            if (kg == null) { vm.message = "No weight found in Health Connect"; return@launch }
            // Profile weights are stored in the display unit.
            val value = if (vm.data.prefs.units.weight == "lb") kg * com.thehhr.form.data.Fuel.LB_PER_KG else kg
            val rounded = Math.round(value * 10) / 10.0
            vm.setProfile(vm.data.profile.copy(currentWeight = rounded))
            vm.message = "Weight set to ${num(rounded)} ${vm.data.prefs.units.weight}"
        }
    }, supporting = { Text("Use your latest weight as current weight") }) { Text("Import weight") }
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
