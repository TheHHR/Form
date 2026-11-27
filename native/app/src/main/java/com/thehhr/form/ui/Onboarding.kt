@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thehhr.form.FormStore
import com.thehhr.form.HealthSync
import kotlinx.coroutines.launch

/**
 * First run: what Form is, an optional Health Connect step, then the folder. Returning users (or ones who
 * skipped) go straight to the folder step, which is the only one Form can't work without.
 */
@Composable
fun OnboardingScreen(vm: FormStore, error: String? = null) {
    var step by rememberSaveable { mutableIntStateOf(if (vm.device.onboarded || error != null) 2 else 0) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val fade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            StepDots(step, 3, Modifier.weight(1f))
            if (step < 2) TextButton(onClick = { vm.device.onboarded = true; step = 2 }) { Text("Skip") }
        }
        AnimatedContent(step, Modifier.weight(1f), transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(spatial) { it / 3 * dir } + fadeIn(fade)) togetherWith (slideOutHorizontally(spatial) { -it / 3 * dir } + fadeOut(fade))
        }, label = "onboarding") { s ->
            when (s) {
                0 -> IntroStep { step = 1 }
                1 -> HealthStep(vm) { vm.device.onboarded = true; step = 2 }
                else -> FolderStep(vm, error)
            }
        }
    }
}

/** Page dots: the current one stretches into a pill. */
@Composable
private fun StepDots(current: Int, count: Int, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "Step ${current + 1} of $count" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 28.dp else 8.dp, MaterialTheme.motionScheme.fastSpatialSpec(), label = "dot")
            Box(Modifier.size(w, 8.dp).clip(CircleShape).background(
                if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest))
        }
    }
}

@Composable
private fun Hero(icon: ImageVector, shape: androidx.graphics.shapes.RoundedPolygon) {
    Box(Modifier.size(120.dp).clip(shape.toShape()).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun StepPage(content: @Composable () -> Unit, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally) { content() }
        Spacer(Modifier.height(16.dp))
        actions()
    }
}

@Composable
private fun BigButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Button(onClick = onClick, enabled = enabled, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().heightIn(min = ButtonDefaults.MediumContainerHeight),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
        Text(text, style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IntroStep(onNext: () -> Unit) = StepPage({
    Hero(Icons.Rounded.FitnessCenter, MaterialShapes.Cookie9Sided)
    Text("Welcome to Form", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
    Text("A training and nutrition log that stays on your phone.", style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Feature(Icons.Rounded.Timer, "Train", "Routines, a live workout with rest timers, and your records.")
    Feature(Icons.Rounded.Restaurant, "Eat", "Meals, macros and water against targets made for you.")
    Feature(Icons.Rounded.Folder, "Own your data", "Everything is 5 plain text files in a folder you choose. No account, no server.")
}) { BigButton("Get started", onNext) }

@Composable
private fun HealthStep(vm: FormStore, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val available = remember { HealthSync.available(ctx) }
    var importProfile by remember { mutableStateOf(true) }
    var sync by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val request = rememberLauncherForActivityResult(HealthSync.permissionContract()) { granted ->
        // Partial grants are fine: each feature only turns on if its own permissions came back.
        val canImport = importProfile && granted.any { it in HealthSync.profilePermissions }
        val canSync = sync && granted.containsAll(HealthSync.permissions)
        vm.device.healthImportPending = canImport
        vm.device.healthEnabled = canSync
        status = when {
            canImport || canSync -> null
            else -> "No permissions granted. You can connect later in Settings."
        }
        if (canImport || canSync) onDone()
    }
    StepPage({
        Hero(Icons.Rounded.Favorite, MaterialShapes.Clover4Leaf)
        Text("Health Connect", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
        if (!available) {
            Text("Health Connect isn't available on this phone. You can set it up later from Settings.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            return@StepPage
        }
        Text("Optional. Form only asks for what you switch on, and you can change it any time in Settings or Health Connect.",
            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Column(Modifier.fillMaxWidth()) {
            OnboardingSwitch(0, 2, "Import my weight and height", "Reads your latest weight and height once, to fill your profile and targets.", importProfile) { importProfile = it }
            OnboardingSwitch(1, 2, "Sync workouts and meals", "Writes finished workouts and logged meals, and reads your weight.", sync) { sync = it }
        }
        status?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
    }) {
        if (available) BigButton(if (importProfile || sync) "Connect" else "Continue", {
            val wanted = (if (importProfile) HealthSync.profilePermissions else emptySet()) + (if (sync) HealthSync.permissions else emptySet())
            if (wanted.isEmpty()) { onDone(); return@BigButton }
            scope.launch {
                val have = HealthSync.granted(ctx)
                if (have.containsAll(wanted)) { vm.device.healthImportPending = importProfile; vm.device.healthEnabled = sync; onDone() }
                else request.launch(wanted)
            }
        })
        TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(if (available) "Not now" else "Continue") }
    }
}

@Composable
private fun OnboardingSwitch(index: Int, count: Int, title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    GroupedItem(index, count, onClick = { onChange(!checked) }, supporting = { Text(body) },
        trailing = { androidx.compose.material3.Switch(checked, onCheckedChange = null) }) { Text(title) }
}

@Composable
private fun FolderStep(vm: FormStore, error: String?) = StepPage({
    Hero(Icons.Rounded.Folder, MaterialShapes.Cookie9Sided)
    Text("Choose your Form folder", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
    Text(
        "Form keeps everything in 5 plain .md files in a folder you pick: routines, meals, training logs, " +
            "nutrition diary and settings. Pick your existing folder to keep your data, or an empty one to start fresh.",
        textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (error != null) Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
}) {
    FolderPicker(vm) { launch -> BigButton("Pick folder", launch) }
}
