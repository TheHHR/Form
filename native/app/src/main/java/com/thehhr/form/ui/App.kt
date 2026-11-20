@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
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
import com.thehhr.form.FormStore
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
fun FormApp(vm: FormStore) {
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
fun FolderPicker(vm: FormStore, content: @Composable (launch: () -> Unit) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::onFolderPicked) }
    content { launcher.launch(null) }
}

@Composable
private fun WelcomeScreen(vm: FormStore, error: String? = null) {
    Scaffold { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(132.dp).clip(MaterialShapes.Cookie9Sided.toShape()).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Folder, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary) }
            Text("Choose your Form folder", style = MaterialTheme.typography.headlineMediumEmphasized, textAlign = TextAlign.Center)
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
            FolderPicker(vm) { launch ->
                Button(onClick = launch, shapes = ButtonDefaults.shapes(), modifier = Modifier.heightIn(min = ButtonDefaults.MediumContainerHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MediumContainerHeight)) {
                    Text("Pick folder", style = ButtonDefaults.textStyleFor(ButtonDefaults.MediumContainerHeight))
                }
            }
        }
    }
}

@Composable
private fun MainScreen(vm: FormStore) {
    var tab by rememberSaveable { mutableStateOf(Tab.Workout) }
    val nav = remember { Nav() }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    BackHandler(enabled = nav.stack.isNotEmpty()) { nav.pop() }

    // Pushed screens slide over the tabs with the theme's expressive spring.
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    AnimatedContent(
        targetState = nav.stack.lastOrNull(),
        transitionSpec = {
            // Push keeps the old screen on the stack (or comes from the tabs); pop removes it.
            val forward = targetState != null && (initialState == null || initialState in nav.stack)
            val dir = if (forward) 1 else -1
            (slideInHorizontally(spatial) { it / 4 * dir } + fadeIn(effects)) togetherWith (slideOutHorizontally(spatial) { -it / 4 * dir } + fadeOut(effects))
        },
        label = "route",
    ) { top ->
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
        } else Tabs(vm, nav, tab, { tab = it }, snackbar)
    }
}

@Composable
private fun Tabs(vm: FormStore, nav: Nav, tab: Tab, onTab: (Tab) -> Unit, snackbar: SnackbarHostState) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val w = vm.workout
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            FormTopBar(
                title = if (tab == Tab.Workout && w != null) w.routineName else tab.label,
                subtitle = if (tab == Tab.Workout && w != null) "Workout in progress" else null,
                scroll = scroll,
                actions = { IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") } },
            )
        },
        bottomBar = {
            ShortNavigationBar {
                Tab.entries.forEach { t ->
                    ShortNavigationBarItem(
                        selected = tab == t, onClick = { onTab(t) },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Crossfade(tab, Modifier.padding(pad).fillMaxSize(), animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tab") { t ->
            when (t) {
                Tab.Workout -> WorkoutTab(vm, nav)
                Tab.Plans -> PlansTab(vm, nav)
                Tab.Fuel -> FuelTab(vm)
                Tab.Progress -> ProgressTab(vm, nav)
            }
        }
    }
}
