package com.thehhr.form.data

import kotlin.math.max
import kotlin.math.min

data class SetRow(
    val reps: Int = 10,
    val weight: Double? = null,
    /** Seconds for sec-unit items, minutes for min-unit items. */
    val duration: Double = 0.0,
    val distance: Double = 0.0,
    val done: Boolean = false,
)

data class Rest(
    val endsAt: Long,
    val total: Int,
    /** "sets" or "exercise". */
    val type: String,
    val exerciseId: String?,
    val warmup: Boolean = false,
    /** Millis left while paused; null when running. */
    val pausedRemaining: Long? = null,
    /** Set timer (type "set"): which row of [exerciseId] completes when it ends. */
    val setIndex: Int? = null,
)

data class ActiveWorkout(
    val date: String,
    val routineId: String,
    val routineName: String,
    val startedAt: Long,
    val paused: Boolean = false,
    val pausedAt: Long? = null,
    val pausedAccum: Long = 0,
    val sets: Map<String, List<SetRow>> = emptyMap(),
    val skipped: Set<String> = emptySet(),
    val extras: List<RoutineItem> = emptyList(),
    /** Original exercise id -> replacement id. */
    val replacements: Map<String, String> = emptyMap(),
    val jumpTo: String? = null,
    val rest: Rest? = null,
    /** When rest was skipped: the set clock counts up from here (after a finished rest it counts from rest.endsAt). */
    val setClockFrom: Long? = null,
) {
    val token: String get() = startedAt.toString()

    fun elapsedMs(now: Long = System.currentTimeMillis()): Long =
        max(0, now - startedAt - pausedAccum - if (paused && pausedAt != null) now - pausedAt else 0)

    fun rows(id: String) = sets[id] ?: emptyList()
    fun complete(id: String) = rows(id).let { it.isNotEmpty() && it.all(SetRow::done) }
}

/** Pure workout rules, ported from www/app.js (awSessionEntries, seedAwSets, syncAwLog, awRestDecision...). */
object WorkoutLogic {
    /**
     * Lower bound for a routine's length in whole minutes: rest between sets and exercises (when rest is on, a superset
     * resting once per round) plus the time of timed sets. Lifting time itself is unknown, so the UI says "Over N mins".
     */
    fun minMinutes(routine: Routine, prefs: Prefs): Int {
        val groups = routine.items.withIndex().groupBy { it.value.superset ?: "#${it.index}" }.values
        val rest = if (!prefs.restEnabled) 0.0 else
            groups.sumOf { g -> (g.maxOf { it.value.sets } - 1).coerceAtLeast(0) * prefs.restBetweenSets } +
                (groups.size - 1).coerceAtLeast(0) * prefs.restBetweenExercises
        val work = routine.items.filter { it.timed }.sumOf { it.sets * it.reps * (if (it.unitOrDefault == "min") 60.0 else 1.0) }
        return ((rest + work) / 60).toInt()
    }


    data class Entry(val item: RoutineItem, val added: Boolean)

    fun entries(s: ActiveWorkout, routine: Routine): List<Entry> {
        fun eff(item: RoutineItem) = s.replacements[item.exerciseId]?.let { item.copy(exerciseId = it) } ?: item
        val out = routine.items.map { Entry(eff(it), false) }.toMutableList()
        val seen = out.map { it.item.exerciseId }.toMutableSet()
        for (extra in s.extras) {
            val item = eff(extra)
            if (seen.add(item.exerciseId)) out += Entry(item, true)
        }
        return out
    }

    /** The routine as this session runs it (replacements + extras applied). */
    fun sessionRoutine(s: ActiveWorkout, routine: Routine) = routine.copy(items = entries(s, routine).map { it.item })

    private fun sorted(logs: List<ProgressLog>) = logs.sortedWith(compareByDescending<ProgressLog> { it.date }.thenByDescending { it.createdAt })

    private fun trackedWeight(log: ProgressLog) =
        log.setWeights?.any { it > 0 } == true || (log.setDurations == null && log.setDistances == null && (log.weight ?: 0.0) > 0)

    fun lastWeight(logs: List<ProgressLog>, id: String, includeTimed: Boolean = false): Double {
        val latest = sorted(logs.filter { it.exerciseId == id && (includeTimed || !it.timed) && trackedWeight(it) }).firstOrNull() ?: return 0.0
        val value = latest.setWeights?.filter { it > 0 }?.lastOrNull() ?: latest.weight ?: 0.0
        return round1(value).coerceIn(0.0, Limits.WEIGHT)
    }

    fun lastDistance(logs: List<ProgressLog>, id: String): Double? {
        for (log in sorted(logs.filter { it.exerciseId == id && it.timed })) {
            val v = log.setDistances?.lastOrNull() ?: continue
            if (v > 0) return round1(v.coerceIn(0.0, Limits.DISTANCE))
        }
        return null
    }

    fun seed(item: RoutineItem, logs: List<ProgressLog>): List<SetRow> {
        val count = item.sets.coerceIn(1, Limits.SETS)
        if (item.timed) {
            val sec = item.unitOrDefault == "sec"
            val unitLog = sorted(logs.filter { it.exerciseId == item.exerciseId && it.timed && it.durUnit == (if (sec) "sec" else "min") }).firstOrNull()
            val last = unitLog?.setDurations?.lastOrNull()
            val duration = when {
                last == null -> item.reps.toDouble()
                sec -> jsRound(last.coerceIn(0.0, 1.0) * 60).toDouble()
                else -> jsRound(last).toDouble().coerceIn(0.0, 60.0)
            }
            val dist = if (item.distance) lastDistance(logs, item.exerciseId) ?: 0.0 else 0.0
            val weight = if (item.weighted) lastWeight(logs, item.exerciseId, true) else null
            return List(count) { SetRow(duration = duration, distance = dist, weight = weight) }
        }
        val weight = if (item.weighted) lastWeight(logs, item.exerciseId) else null
        return List(count) { SetRow(reps = item.reps, weight = weight) }
    }

    fun start(routine: Routine, logs: List<ProgressLog>, now: Long = System.currentTimeMillis()) = ActiveWorkout(
        date = today(), routineId = routine.id, routineName = routine.name, startedAt = now,
        sets = routine.items.associate { it.exerciseId to seed(it, logs) },
    )

    /** Keeps `sets` in step with the session items (after add/replace/remove). */
    fun syncSets(s: ActiveWorkout, routine: Routine, logs: List<ProgressLog>): ActiveWorkout {
        val items = entries(s, routine).map { it.item }
        val ids = items.map { it.exerciseId }.toSet()
        val sets = items.associate { it.exerciseId to (s.sets[it.exerciseId] ?: seed(it, logs)) }
        val jump = s.jumpTo?.takeIf { it in ids && !(sets[it]?.let { r -> r.isNotEmpty() && r.all(SetRow::done) } ?: false) && it !in s.skipped }
        return s.copy(sets = sets, skipped = s.skipped.filter { it in ids }.toSet(), jumpTo = jump)
    }


    /**
     * Sets row [index] to [row] and copies whatever value changed (reps, weight, time, distance) to the later sets
     * that aren't done yet, so editing set 1 sets up the rest. Done sets and the done flag itself are never copied.
     */
    fun carryForward(rows: List<SetRow>, index: Int, row: SetRow): List<SetRow> {
        val before = rows.getOrNull(index) ?: return rows
        return rows.mapIndexed { i, r ->
            when {
                i == index -> row
                i < index || r.done -> r
                else -> r.copy(
                    reps = if (row.reps != before.reps) row.reps else r.reps,
                    weight = if (row.weight != before.weight) row.weight else r.weight,
                    duration = if (row.duration != before.duration) row.duration else r.duration,
                    distance = if (row.distance != before.distance) row.distance else r.distance,
                )
            }
        }
    }
    fun awLogId(s: ActiveWorkout, exerciseId: String) = "${s.token}-aw-$exerciseId"

    /** Rewrites this session's log for one exercise from its checked sets (syncAwLog). */
    fun syncLog(logs: List<ProgressLog>, s: ActiveWorkout, item: RoutineItem): List<ProgressLog> {
        val id = awLogId(s, item.exerciseId)
        val rest = logs.filter { !(it.sessionId == s.token && it.exerciseId == item.exerciseId) && it.id != id }
        val checked = s.rows(item.exerciseId).filter { it.done }.take(Limits.SETS)
        if (checked.isEmpty()) return rest
        val weights = checked.map { round1((it.weight ?: 0.0).coerceIn(0.0, Limits.WEIGHT)) }
        val tracked = weights.filter { it > 0 }
        val now = System.currentTimeMillis()
        if (item.timed) {
            val sec = item.unitOrDefault == "sec"
            val durs = checked.map { r -> val raw = r.duration.coerceIn(0.0, Limits.DURATION); round4(if (sec) raw / 60 else raw).coerceIn(0.0, Limits.DURATION) }
            val dists = if (item.distance) checked.map { round1(it.distance.coerceIn(0.0, Limits.DISTANCE)) } else emptyList()
            val hasDur = durs.any { it > 0 }
            val hasDist = dists.any { it > 0 }
            if (!hasDur && !hasDist) return rest
            return rest + ProgressLog(
                id = id, exerciseId = item.exerciseId, date = s.date, intervals = checked.size,
                weight = tracked.lastOrNull(), durUnit = if (sec) "sec" else "min",
                setDurations = durs.takeIf { hasDur }, setDistances = dists.takeIf { hasDist },
                setWeights = weights.takeIf { tracked.isNotEmpty() }, createdAt = now, sessionId = s.token,
            )
        }
        val reps = checked.map { it.reps.coerceIn(1, Limits.REPS) }
        return rest + ProgressLog(
            id = id, exerciseId = item.exerciseId, date = s.date, sets = checked.size, reps = reps.first(),
            weight = tracked.lastOrNull(), setReps = reps, setWeights = weights, createdAt = now, sessionId = s.token,
        )
    }

    /** Removes every log this session wrote (discard). */
    fun discardLogs(logs: List<ProgressLog>, s: ActiveWorkout) =
        logs.filter { it.sessionId != s.token && !it.id.startsWith("${s.token}-aw-") }

    /** The exercise whose next set is up: the jump target, else the first not-done, not-skipped one. */
    fun nextTarget(s: ActiveWorkout, routine: Routine): RoutineItem? {
        val items = entries(s, routine).map { it.item }
        val jump = s.jumpTo?.takeIf { it !in s.skipped && !s.complete(it) }
        return items.firstOrNull { it.exerciseId == jump } ?: items.firstOrNull { it.exerciseId !in s.skipped && !s.complete(it.exerciseId) }
    }

    enum class RestKind { BETWEEN_SETS, BETWEEN_EXERCISES, NONE }

    fun restDecision(s: ActiveWorkout, routine: Routine, item: RoutineItem): RestKind {
        val items = entries(s, routine).map { it.item }
        val myPending = s.rows(item.exerciseId).any { !it.done }
        fun nextUp() = items.any { it.exerciseId != item.exerciseId && it.exerciseId !in s.skipped && !s.complete(it.exerciseId) }
        val partner = item.superset?.let { tok -> items.firstOrNull { it !== item && it.exerciseId != item.exerciseId && it.superset == tok } }
        if (partner == null || partner.exerciseId in s.skipped || s.complete(partner.exerciseId)) {
            if (myPending) return RestKind.BETWEEN_SETS
            return if (nextUp()) RestKind.BETWEEN_EXERCISES else RestKind.NONE
        }
        val firstIsMe = items.indexOfFirst { it.exerciseId == item.exerciseId } < items.indexOfFirst { it.exerciseId == partner.exerciseId }
        if (firstIsMe) {
            if (s.rows(partner.exerciseId).any { !it.done }) return RestKind.NONE
            if (myPending) return RestKind.BETWEEN_SETS
            return if (nextUp()) RestKind.BETWEEN_EXERCISES else RestKind.NONE
        }
        if (myPending || s.rows(partner.exerciseId).any { !it.done }) return RestKind.BETWEEN_SETS
        return if (nextUp()) RestKind.BETWEEN_EXERCISES else RestKind.NONE
    }

    fun record(logs: List<ProgressLog>, id: String): Double? =
        logs.filter { it.exerciseId == id && !it.timed }
            .flatMap { l -> l.setWeights?.filter { it > 0 }?.takeIf { it.isNotEmpty() } ?: listOfNotNull(l.weight?.takeIf { it > 0 }) }
            .maxOrNull()

    fun formatLog(log: ProgressLog, units: Units): String {
        fun range(values: List<Double>, suffix: String): String {
            val lo = values.min(); val hi = values.max()
            return if (lo == hi) "${jsNum(lo)}$suffix" else "${jsNum(lo)}-${jsNum(hi)}$suffix"
        }
        if (log.timed) {
            val parts = mutableListOf<String>()
            val intervals = log.intervals ?: max(log.setDurations?.size ?: 0, log.setDistances?.size ?: 0)
            if (intervals > 0) parts += "$intervals ${if (intervals == 1) "interval" else "intervals"}"
            val dur = log.setDurations?.sum() ?: 0.0
            if (dur > 0) parts += if (log.durUnit == "sec" || dur < 1) "${jsRound(dur * 60)} sec" else "${jsRound(dur)} min"
            val dist = round1(log.setDistances?.sum() ?: 0.0)
            if (dist > 0) parts += "${jsNum(dist)} ${units.distance}"
            val w = log.setWeights?.filter { it > 0 }.orEmpty()
            if (w.isNotEmpty()) parts += range(w, " ${units.weight}")
            return parts.joinToString(" · ")
        }
        val w = log.setWeights?.filter { it > 0 }?.takeIf { it.isNotEmpty() } ?: listOfNotNull(log.weight?.takeIf { it > 0 })
        val wt = if (w.isNotEmpty()) " × ${range(w, " ${units.weight}")}" else ""
        val reps = log.setReps?.takeIf { it.isNotEmpty() }?.map { it.toDouble() }
        val sets = "${log.sets} set${if (log.sets == 1) "" else "s"}"
        return if (reps != null) "$sets × ${range(reps, " reps")}$wt" else "$sets × ${log.reps} reps$wt"
    }

    /** Training volume (kg·reps) of a strength log. */
    fun volume(log: ProgressLog): Double {
        if (log.timed) return 0.0
        val reps = log.setReps ?: List(log.sets ?: 0) { log.reps ?: 0 }
        val ws = log.setWeights
        return reps.indices.sumOf { i -> reps[i] * (ws?.getOrNull(i) ?: log.weight ?: 0.0) }
    }

    fun clampWeight(v: Double) = min(Limits.WEIGHT, max(0.0, v))
}
