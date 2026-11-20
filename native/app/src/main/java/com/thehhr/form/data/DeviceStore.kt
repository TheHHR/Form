package com.thehhr.form.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Device-only state (never in the vault): the in-progress workout. */
class DeviceStore(context: Context) {
    private val prefs = context.getSharedPreferences("form_device", Context.MODE_PRIVATE)

    // Device-only settings: kept out of config.md so the vault stays byte-compatible with the web app.
    var reminderEnabled: Boolean
        get() = prefs.getBoolean("reminder_enabled", false)
        set(v) = prefs.edit().putBoolean("reminder_enabled", v).apply()
    /** Minutes after midnight. */
    var reminderMinutes: Int
        get() = prefs.getInt("reminder_minutes", 18 * 60)
        set(v) = prefs.edit().putInt("reminder_minutes", v).apply()
    var healthEnabled: Boolean
        get() = prefs.getBoolean("health_enabled", false)
        set(v) = prefs.edit().putBoolean("health_enabled", v).apply()
    /** AI provider + keys (device-only; allowBackup=false keeps them out of cloud backups). */
    var aiConfig: AiConfig
        get() = AiConfig.fromJson(prefs.getString("ai_config", null))
        set(v) = prefs.edit().putString("ai_config", v.toJson()).apply()
    var notificationsAsked: Boolean
        get() = prefs.getBoolean("notifications_asked", false)
        set(v) = prefs.edit().putBoolean("notifications_asked", v).apply()

    fun saveWorkout(w: ActiveWorkout?) {
        prefs.edit().apply { if (w == null) remove("active_workout") else putString("active_workout", encode(w).toString()) }.apply()
    }

    fun loadWorkout(): ActiveWorkout? = prefs.getString("active_workout", null)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() }

    private fun encode(w: ActiveWorkout) = JSONObject().apply {
        put("date", w.date); put("routineId", w.routineId); put("routineName", w.routineName)
        put("startedAt", w.startedAt); put("paused", w.paused); w.pausedAt?.let { put("pausedAt", it) }; put("pausedAccum", w.pausedAccum)
        put("sets", JSONObject().apply {
            w.sets.forEach { (id, rows) ->
                put(id, JSONArray().apply {
                    rows.forEach { r -> put(JSONObject().apply { put("reps", r.reps); r.weight?.let { put("weight", it) }; put("duration", r.duration); put("distance", r.distance); put("done", r.done) }) }
                })
            }
        })
        put("skipped", JSONArray(w.skipped.toList()))
        put("extras", JSONArray().apply { w.extras.forEach { put(item(it)) } })
        put("replacements", JSONObject(w.replacements as Map<*, *>))
        w.jumpTo?.let { put("jumpTo", it) }
        w.rest?.let { r ->
            put("rest", JSONObject().apply {
                put("endsAt", r.endsAt); put("total", r.total); put("type", r.type); r.exerciseId?.let { put("exerciseId", it) }
                put("warmup", r.warmup); r.pausedRemaining?.let { put("pausedRemaining", it) }; r.setIndex?.let { put("setIndex", it) }
            })
        }
    }

    private fun item(i: RoutineItem) = JSONObject().apply {
        put("exerciseId", i.exerciseId); put("sets", i.sets); put("reps", i.reps); put("timed", i.timed)
        i.unit?.let { put("unit", it) }; put("unweighted", i.unweighted); put("distance", i.distance)
    }

    private fun decode(o: JSONObject): ActiveWorkout {
        val sets = mutableMapOf<String, List<SetRow>>()
        o.optJSONObject("sets")?.let { s ->
            s.keys().forEach { id ->
                val arr = s.getJSONArray(id)
                sets[id] = List(arr.length()) { i ->
                    val r = arr.getJSONObject(i)
                    SetRow(r.optInt("reps", 10), if (r.has("weight")) r.getDouble("weight") else null, r.optDouble("duration", 0.0), r.optDouble("distance", 0.0), r.optBoolean("done"))
                }
            }
        }
        val extrasArr = o.optJSONArray("extras") ?: JSONArray()
        val extras = List(extrasArr.length()) { i ->
            val e = extrasArr.getJSONObject(i)
            RoutineItem(e.getString("exerciseId"), e.optInt("sets", 3), e.optInt("reps", 10), e.optBoolean("timed"), e.optString("unit").ifEmpty { null }, e.optBoolean("unweighted"), e.optBoolean("distance"))
        }
        val rep = o.optJSONObject("replacements")
        val skippedArr = o.optJSONArray("skipped") ?: JSONArray()
        val rest = o.optJSONObject("rest")?.let { r ->
            Rest(r.getLong("endsAt"), r.getInt("total"), r.optString("type", "sets"), r.optString("exerciseId").ifEmpty { null }, r.optBoolean("warmup"),
                if (r.has("pausedRemaining")) r.getLong("pausedRemaining") else null,
                if (r.has("setIndex")) r.getInt("setIndex") else null)
        }
        return ActiveWorkout(
            date = o.getString("date"), routineId = o.getString("routineId"), routineName = o.optString("routineName"),
            startedAt = o.getLong("startedAt"), paused = o.optBoolean("paused"),
            pausedAt = if (o.has("pausedAt")) o.getLong("pausedAt") else null, pausedAccum = o.optLong("pausedAccum"),
            sets = sets, skipped = List(skippedArr.length()) { skippedArr.getString(it) }.toSet(), extras = extras,
            replacements = rep?.keys()?.asSequence()?.associateWith { rep.getString(it) } ?: emptyMap(),
            jumpTo = o.optString("jumpTo").ifEmpty { null }, rest = rest,
        )
    }
}
