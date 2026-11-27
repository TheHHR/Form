package com.thehhr.form

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.FilledButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.thehhr.form.data.DayLog
import com.thehhr.form.data.Fuel
import com.thehhr.form.data.WorkoutLogic
import com.thehhr.form.data.jsRound
import com.thehhr.form.data.today
import com.thehhr.form.ui.Accents
import com.thehhr.form.ui.Complement
import com.thehhr.form.ui.darkScheme
import com.thehhr.form.ui.exTitle
import com.thehhr.form.ui.lightScheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.time.LocalDate

/** What the widget shows; read once per render from the store. */
private data class WidgetData(
    val accent: String,
    val training: Boolean,
    val title: String,
    val subtitle: String,
    val canStart: Boolean,
    val kcal: Double, val kcalTarget: Double,
    val protein: Double, val proteinTarget: Double,
    val weekSessions: Int, val weekSets: Int,
    val routines: List<Pair<String, String>>,
)

/** Responsive "Today" widget: small = today + calories, medium adds protein and a button, large adds the week and routines. */
class FormGlance : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(120.dp, 120.dp), DpSize(250.dp, 120.dp), DpSize(250.dp, 260.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = FormStore.get(context).whenReady { snapshot() }
        provideContent {
            val accent = data?.accent ?: FormStore.get(context).device.lastAccent
            val (p, onP) = Accents[accent] ?: Accents.getValue("lime")
            val t = Accents.getValue(Complement[accent] ?: "cyan").first
            // Wallpaper colours on Android 12+, Form's accent before that.
            val colors = if (android.os.Build.VERSION.SDK_INT >= 31) GlanceTheme.colors else ColorProviders(light = lightScheme(p, t), dark = darkScheme(p, onP, t))
            GlanceTheme(colors) { WidgetContent(context, data) }
        }
    }

    private fun FormStore.snapshot(): WidgetData {
        val d = data
        val day = d.history[today()] ?: DayLog()
        val t = Fuel.effective(d.profile, d.prefs.units)
        val w = workout
        val routine = todaysRoutine()
        val now = LocalDate.now()
        val back = (now.dayOfWeek.value % 7 - d.prefs.weekStart + 7) % 7
        val weekFrom = now.minusDays(back.toLong()).toString()
        val week = d.logs.filter { it.date >= weekFrom && it.date <= now.toString() }
        val title: String; val subtitle: String
        if (w != null) {
            val r = d.routines.firstOrNull { it.id == w.routineId }
            val next = r?.let { WorkoutLogic.nextTarget(w, it) }
            title = w.routineName
            subtitle = next?.let { exTitle(exercise(it.exerciseId)) } ?: "All done · tap to finish"
        } else {
            title = routine?.name ?: "Rest day"
            subtitle = routine?.let { "${it.items.size} exercises" } ?: "Nothing scheduled"
        }
        return WidgetData(
            accent = d.prefs.accent, training = w != null, title = title, subtitle = subtitle,
            canStart = w == null && routine != null && routine.items.isNotEmpty(),
            kcal = day.meals.sumOf { it.cals }, kcalTarget = t.cals, protein = day.meals.sumOf { it.p }, proteinTarget = t.p,
            weekSessions = week.map { it.date }.distinct().size,
            weekSets = week.sumOf { if (it.timed) it.intervals ?: 0 else it.sets ?: 0 },
            routines = (d.routines.filter { it.liked }.ifEmpty { d.routines }).filter { it.items.isNotEmpty() }.take(3).map { it.id to it.name },
        )
    }
}

private fun open(ctx: Context, action: String? = null, routineId: String? = null) = actionStartActivity(
    Intent(ctx, MainActivity::class.java).setAction(action ?: Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        .apply { routineId?.let { putExtra(MainActivity.EXTRA_ROUTINE, it) } },
)

@Composable
private fun WidgetContent(ctx: Context, d: WidgetData?) {
    val c = GlanceTheme.colors
    val size = LocalSize.current
    Scaffold(backgroundColor = c.widgetBackground, modifier = GlanceModifier.clickable(open(ctx, MainActivity.ACTION_OPEN_WORKOUT)),
        horizontalPadding = 16.dp) {
        if (d == null) {
            Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Open Form to choose your folder", style = TextStyle(color = c.onSurface, fontSize = 14.sp))
            }
            return@Scaffold
        }
        Column(GlanceModifier.fillMaxSize().padding(vertical = 14.dp)) {
            if (size.width < 250.dp) {
                Today(ctx, d, button = false)
                Spacer(GlanceModifier.defaultWeight())
                Meter("kcal", d.kcal, d.kcalTarget)
            } else {
                Row(GlanceModifier.fillMaxWidth()) {
                    Column(GlanceModifier.defaultWeight()) { Today(ctx, d, button = true) }
                    Spacer(GlanceModifier.width(16.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Meter("kcal", d.kcal, d.kcalTarget)
                        Spacer(GlanceModifier.height(10.dp))
                        Meter("g protein", d.protein, d.proteinTarget)
                    }
                }
                if (size.height >= 260.dp) {
                    Spacer(GlanceModifier.height(14.dp))
                    Text("This week · ${d.weekSessions} session${if (d.weekSessions == 1) "" else "s"} · ${d.weekSets} sets",
                        style = TextStyle(color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.height(8.dp))
                    d.routines.forEach { (id, name) ->
                        Row(GlanceModifier.fillMaxWidth().padding(bottom = 6.dp).background(c.secondaryContainer).cornerRadius(16.dp)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .clickable(open(ctx, if (d.training) MainActivity.ACTION_OPEN_WORKOUT else MainActivity.ACTION_START_ROUTINE, id))
                            .semantics { contentDescription = if (d.training) "Open workout" else "Start $name" },
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(name, GlanceModifier.defaultWeight(), style = TextStyle(color = c.onSecondaryContainer, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                            Text(if (d.training) "" else "Start", style = TextStyle(color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Today(ctx: Context, d: WidgetData, button: Boolean) {
    val c = GlanceTheme.colors
    Text(if (d.training) "Training now" else "Today", style = TextStyle(color = c.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold))
    Text(d.title, style = TextStyle(color = c.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold), maxLines = 2)
    Text(d.subtitle, style = TextStyle(color = c.onSurfaceVariant, fontSize = 13.sp), maxLines = 1)
    if (button && (d.training || d.canStart)) {
        Spacer(GlanceModifier.height(10.dp))
        FilledButton(if (d.training) "Open" else "Start", onClick = open(ctx, if (d.training) MainActivity.ACTION_OPEN_WORKOUT else MainActivity.ACTION_START_TODAY))
    }
}

@Composable
private fun Meter(unit: String, value: Double, target: Double) {
    val c = GlanceTheme.colors
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${jsRound(value)}", style = TextStyle(color = c.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold))
        Text(" / ${jsRound(target)} $unit", style = TextStyle(color = c.onSurfaceVariant, fontSize = 12.sp))
    }
    Spacer(GlanceModifier.height(4.dp))
    LinearProgressIndicator((value / target.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f), GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
        color = c.primary, backgroundColor = c.secondaryContainer)
}

/** Keeps the receiver class name and provider XML of the old RemoteViews widget, so placed widgets keep working. */
class FormWidget : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FormGlance()

    companion object {
        private var pending: Job? = null

        /** Debounced: store edits come in bursts; one render a second later is plenty. Cheap when no widget is placed. */
        fun updateAll(ctx: Context) {
            val store = FormStore.get(ctx)
            pending?.cancel()
            pending = store.launch {
                delay(1_000)
                if (GlanceAppWidgetManager(ctx).getGlanceIds(FormGlance::class.java).isNotEmpty()) FormGlance().updateAll(ctx)
            }
        }
    }
}
