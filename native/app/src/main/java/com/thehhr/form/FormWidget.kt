package com.thehhr.form

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.thehhr.form.data.DayLog
import com.thehhr.form.data.Fuel
import com.thehhr.form.data.jsRound
import com.thehhr.form.data.today
import com.thehhr.form.ui.Accents
import kotlinx.coroutines.launch

/** "Today" home-screen widget: scheduled routine (or live workout) and calories/macros against targets. */
class FormWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = FormStore.get(ctx)
        val pending = goAsync()
        store.launch {
            try { store.whenReady { render(ctx, this) } } finally { pending.finish() }
        }
    }

    companion object {
        /** Cheap no-op when no widget is placed. */
        fun updateAll(ctx: Context) {
            val ids = AppWidgetManager.getInstance(ctx).getAppWidgetIds(ComponentName(ctx, FormWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, FormStore.get(ctx))
        }

        private fun render(ctx: Context, store: FormStore) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, FormWidget::class.java))
            if (ids.isEmpty()) return
            val d = store.data
            val accent = (Accents[d.prefs.accent] ?: Accents.getValue("lime")).first.toArgb()
            val day = d.history[today()] ?: DayLog()
            val t = Fuel.effective(d.profile, d.prefs.units)
            val cals = day.meals.sumOf { it.cals }
            val w = store.workout
            val routine = store.todaysRoutine()
            val v = RemoteViews(ctx.packageName, R.layout.widget_today).apply {
                setTextViewText(R.id.w_label, if (w != null) "Training now" else "Today")
                setTextColor(R.id.w_label, accent)
                setTextViewText(R.id.w_title, w?.routineName ?: routine?.name ?: "Rest day")
                setTextViewText(R.id.w_kcal, "${jsRound(cals)} / ${jsRound(t.cals)} kcal")
                setTextViewText(R.id.w_macros, "P ${jsRound(day.meals.sumOf { it.p })}/${jsRound(t.p)} · C ${jsRound(day.meals.sumOf { it.c })}/${jsRound(t.c)} · F ${jsRound(day.meals.sumOf { it.f })}/${jsRound(t.f)} · ${jsRound(day.water)} ml")
                setProgressBar(R.id.w_progress, 1000, ((cals / t.cals).coerceIn(0.0, 1.0) * 1000).toInt(), false)
                if (Build.VERSION.SDK_INT >= 31) setColorStateList(R.id.w_progress, "setProgressTintList", ColorStateList.valueOf(accent))
                setOnClickPendingIntent(R.id.w_root, RestNotifier.openApp(ctx))
                setOnClickPendingIntent(R.id.w_start,
                    RestNotifier.openApp(ctx, if (w == null && routine != null) MainActivity.ACTION_START_TODAY else null))
                setTextViewText(R.id.w_start, if (w != null) "Open" else if (routine != null) "Start" else "Open")
            }
            manager.updateAppWidget(ids, v)
        }
    }
}
