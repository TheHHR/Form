@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.thehhr.form.AppViewModel
import com.thehhr.form.Status

sealed interface Pick {
    data class ToRoutine(val routineId: String) : Pick
    data object Extra : Pick
    data class Replace(val fromId: String) : Pick
}

sealed interface Route {
    data class Library(val pick: Pick? = null) : Route
    data class Detail(val id: String) : Route
    data class RoutineEdit(val id: String) : Route
    data object Settings : Route
}

enum class Tab(val label: String, val icon: ImageVector) {
    Workout("Workout", Icons.Rounded.FitnessCenter),
    Plans("Plans", Icons.Rounded.CalendarMonth),
    Fuel("Fuel", Icons.Rounded.Restaurant),
    Progress("Progress", Icons.Rounded.Insights),
}

/** Simple back stack of full-screen routes above the tabs. */
class Nav {
    val stack = mutableStateListOf<Route>()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}

@Composable
fun FormApp(vm: AppViewModel) {
    FormTheme(accent = vm.data.prefs.accent) {
        when (val s = vm.status) {
            Status.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
            Status.NeedsFolder -> WelcomeScreen(vm)
            is Status.Error -> WelcomeScreen(vm, error = s.message)
            Status.Ready -> MainScreen(vm)
        }
    }
}

@Composable
fun FolderPicker(vm: AppViewModel, content: @Composable (launch: () -> Unit) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::onFolderPicked) }
    content { launcher.launch(null) }
}

@Composable
private fun WelcomeScreen(vm: AppViewModel, error: String? = null) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Rounded.Folder, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Choose your Form folder", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                "Form keeps everything in 5 plain .md files in a folder you pick: routines, meals, training logs, " +
                    "nutrition diary and settings. Pick your existing folder to keep your data, or an empty one to start fresh.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Beta: point this at a COPY of your folder until the native app is proven.",
                textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
            )
            if (error != null) Text(error, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            FolderPicker(vm) { launch -> Button(onClick = launch) { Text("Pick folder") } }
        }
    }
}

@Composable
private fun MainScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Workout) }
    val nav = remember { Nav() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }

    val top = nav.stack.lastOrNull()
    if (top != null) {
        Box(Modifier.fillMaxSize()) {
            when (top) {
                is Route.Library -> LibraryScreen(vm, nav, top.pick)
                is Route.Detail -> ExerciseDetailScreen(vm, nav, top.id)
                is Route.RoutineEdit -> RoutineEditorScreen(vm, nav, top.id)
                Route.Settings -> SettingsScreen(vm, nav)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (tab == Tab.Workout && vm.workout != null) vm.workout!!.routineName else tab.label) },
                actions = { IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") } },
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                Tab.Workout -> WorkoutTab(vm, nav)
                Tab.Plans -> PlansTab(vm, nav)
                Tab.Fuel -> FuelTab(vm)
                Tab.Progress -> ProgressTab(vm, nav)
            }
        }
    }
}
