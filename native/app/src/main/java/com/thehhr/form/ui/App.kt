@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package com.thehhr.form.ui

import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.automirrored.rounded.MenuBook
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
    // Holds its own filter state so search survives opening an exercise and coming back.
    data class Library(val pick: Pick? = null, val filters: LibraryFilters = LibraryFilters()) : Route
    data class Detail(val id: String) : Route
    data class RoutineEdit(val id: String) : Route
    data object Settings : Route
}

enum class Tab(val label: String, val icon: ImageVector) {
    Progress("Progress", Icons.Rounded.Insights),
    Fuel("Fuel", Icons.Rounded.Restaurant),
    Workout("Workout", Icons.Rounded.FitnessCenter),
    Plans("Plans", Icons.Rounded.CalendarMonth),
    Ai("AI", Icons.Rounded.AutoAwesome),

}

/** Simple back stack of full-screen routes above the tabs. */
class Nav {
    val stack = mutableStateListOf<Route>()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    /** Switches the bottom-nav tab (set by the main screen). */
    var selectTab: (Tab) -> Unit = {}
}

@Composable
fun FormApp(vm: FormStore) {
    val dark = when (vm.themeMode) { "light" -> false; "dark" -> true; else -> androidx.compose.foundation.isSystemInDarkTheme() }
    // Status/nav bar icons follow the in-app theme, which may differ from the system one.
    val activity = androidx.activity.compose.LocalActivity.current as? androidx.activity.ComponentActivity
    LaunchedEffect(dark) {
        val t = android.graphics.Color.TRANSPARENT
        activity?.enableEdgeToEdge(
            if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
            if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
        )
    }
    // Until the vault loads, use the accent remembered on this device (not the lime default).
    FormTheme(accent = if (vm.status == Status.Ready) vm.data.prefs.accent else vm.device.lastAccent, dark = dark, dynamic = vm.dynamicColor) {
        // Surface so the background colour animates with the theme too.
        androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            // Each slot renders its own state, so an outgoing slot never builds a second MainScreen.
            Crossfade(vm.status, animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "status") { s ->
                when (s) {
                    Status.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator(Modifier.size(64.dp)) }
                    Status.NeedsFolder -> OnboardingScreen(vm)
                    is Status.Error -> OnboardingScreen(vm, error = s.message)
                    Status.Ready -> MainScreen(vm)
                }
            }
        }
    }
}

@Composable
fun FolderPicker(vm: FormStore, content: @Composable (launch: () -> Unit) -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::onFolderPicked) }
    content { launcher.launch(null) }
}

@Composable
private fun MainScreen(vm: FormStore) {
    var tab by rememberSaveable { mutableStateOf(Tab.Workout) }
    val nav = vm.nav // process-level, so rotation or activity recreation keeps the open screen (and library search)
    nav.selectTab = { tab = it }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }
    // Notification, shortcut, tile and widget entry points land on their tab, above any pushed screen.
    LaunchedEffect(vm.pendingDestination) {
        val dest = vm.pendingDestination ?: return@LaunchedEffect
        Tab.entries.firstOrNull { it.name == dest.substringBefore(':') }?.let { nav.stack.clear(); tab = it }
        if (!dest.contains(':')) vm.pendingDestination = null // "Fuel:log" is consumed by the Fuel tab
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
                    is Route.Library -> LibraryScreen(vm, nav, top.pick, top.filters)
                    is Route.Detail -> ExerciseDetailScreen(vm, nav, top.id)
                    is Route.RoutineEdit -> RoutineEditorScreen(vm, nav, top.id)
                    Route.Settings -> SettingsScreen(vm, nav)
                }
                SwipeSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
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
                scroll = scroll,
                actions = {
                    IconButton(onClick = { nav.push(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
                },
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
        snackbarHost = { SwipeSnackbarHost(snackbar) },
    ) { pad ->
        Crossfade(tab, Modifier.padding(pad).fillMaxSize(), animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(), label = "tab") { t ->
            when (t) {
                Tab.Workout -> WorkoutTab(vm, nav)
                Tab.Plans -> PlansTab(vm, nav)
                Tab.Ai -> AiTab(vm, nav)
                Tab.Fuel -> FuelTab(vm)
                Tab.Progress -> ProgressTab(vm, nav)
            }
        }
    }
}

/** Exercise library search + filters, kept per library route (not per composition). */
class LibraryFilters {
    var query by mutableStateOf("")
    var category by mutableStateOf("")
    var target by mutableStateOf("")
    var equipment by mutableStateOf("")
    var likedOnly by mutableStateOf(false)
    var loggedOnly by mutableStateOf(false)
    var tag by mutableStateOf("")
}

/** Snackbar host whose snackbars can be swiped away sideways. */
@Composable
private fun SwipeSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data ->
        androidx.compose.runtime.key(data) {
            androidx.compose.material3.SwipeToDismissBox(
                androidx.compose.material3.rememberSwipeToDismissBoxState(), backgroundContent = {},
                onDismiss = { data.dismiss() },
            ) { androidx.compose.material3.Snackbar(data) }
        }
    }
}
