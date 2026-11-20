package com.thehhr.form

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.thehhr.form.data.ActiveWorkout
import com.thehhr.form.data.Catalog
import com.thehhr.form.data.DayLog
import com.thehhr.form.data.Defaults
import com.thehhr.form.data.DeviceStore
import com.thehhr.form.data.Exercise
import com.thehhr.form.data.Meal
import com.thehhr.form.data.MealEntry
import com.thehhr.form.data.Prefs
import com.thehhr.form.data.Profile
import com.thehhr.form.data.ProgressLog
import com.thehhr.form.data.Rest
import com.thehhr.form.data.Routine
import com.thehhr.form.data.RoutineItem
import com.thehhr.form.data.SafVault
import com.thehhr.form.data.SetRow
import com.thehhr.form.data.VaultData
import com.thehhr.form.data.VaultFormat
import com.thehhr.form.data.WorkoutLogic
import com.thehhr.form.data.today
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

enum class VaultFile(val fileName: String) {
    Routines("routines.md"), Meals("meals.md"), TrainingLogs("training_logs.md"),
    NutritionDiary("nutrition_diary.md"), Config("config.md"),
}

sealed interface Status {
    data object Loading : Status
    data object NeedsFolder : Status
    data object Ready : Status
    data class Error(val message: String) : Status
}

/**
 * App state and the single vault writer. One instance per process (see [get]): the UI, notification
 * actions, reminders, the tile and the widget all share it, so there is never a second writer on the folder.
 * All mutations run on the main thread.
 */
class FormStore private constructor(val app: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val vault = SafVault(app)
    val device = DeviceStore(app)
    lateinit var catalog: Catalog; private set
    lateinit var format: VaultFormat; private set

    var status by mutableStateOf<Status>(Status.Loading); private set
    var data by mutableStateOf(VaultData()); private set
    // Restored straight away so rest +30s / skip from the notification work before the vault loads.
    var workout by mutableStateOf(device.loadWorkout()?.takeIf { System.currentTimeMillis() - it.startedAt < 18 * 3600_000L }); private set
    var message by mutableStateOf<String?>(null)
    val folderName: String get() = vault.folderName()

    /** Completes once the first load attempt ends (check [status] for the result). */
    private val ready = CompletableDeferred<Unit>()

    /** For receivers/tiles: waits for the vault, runs [block] on main, then writes immediately. */
    suspend fun <T> whenReady(block: FormStore.() -> T): T? {
        withTimeoutOrNull(8_000) { ready.await() } ?: return null
        if (status != Status.Ready) return null
        val r = withContext(Dispatchers.Main.immediate) { block() }
        flushNow()
        return r
    }

    fun launch(block: suspend CoroutineScope.() -> Unit) = scope.launch(block = block)

    companion object {
        @Volatile private var instance: FormStore? = null
        fun get(context: Context): FormStore = instance ?: synchronized(this) {
            instance ?: FormStore(context.applicationContext as Application).also { instance = it }
        }
    }

    /** What we last read from / wrote to each file; used to skip no-op writes and detect outside edits. */
    private val onDisk = mutableMapOf<VaultFile, String?>()
    private val pending = mutableMapOf<VaultFile, String>()
    private val backedUp = mutableSetOf<VaultFile>()
    private val writeLock = Mutex()
    private var flushJob: Job? = null

    init {
        scope.launch {
            catalog = withContext(Dispatchers.IO) {
                Catalog.parse(app.assets.open("data/exercises.js").bufferedReader().readText())
            }
            format = VaultFormat(catalog)
            if (vault.hasGrant()) load() else status = Status.NeedsFolder
            ready.complete(Unit)
            Reminders.schedule(app, device)
        }
    }

    /** Everything outside the app UI that mirrors state: ongoing workout notification + rest alarm, home widget. */
    private fun syncOutside() {
        RestNotifier.sync(app, this)
        FormWidget.updateAll(app)
    }

    fun exercise(id: String): Exercise? = catalog.byId(id)

    // ------------------------------------------------------------------ vault I/O

    fun onFolderPicked(uri: Uri) {
        scope.launch {
            flushNow()
            runCatching { vault.adopt(uri) }.onFailure { status = Status.Error("Couldn't get access: ${it.message}"); return@launch }
            onDisk.clear(); backedUp.clear()
            load()
        }
    }

    private suspend fun load() {
        status = Status.Loading
        try {
            val texts = withContext(Dispatchers.IO) { VaultFile.entries.associateWith { vault.read(it.fileName) } }
            if (texts.values.all { it == null }) {
                data = VaultData(routines = Defaults.routines, meals = Defaults.meals)
                catalog.setCustoms(emptyList())
                VaultFile.entries.forEach { onDisk[it] = null; save(it) }
                message = "Created a new vault in ${vault.folderName()}"
            } else {
                data = parse(texts)
                onDisk.putAll(texts)
                val missing = texts.filterValues { it == null }.keys
                if (missing.isNotEmpty()) message = "Missing: ${missing.joinToString { it.fileName }}. Check your vault folder."
            }
            status = Status.Ready
            device.lastAccent = data.prefs.accent
            putWorkout(workout?.takeIf { w -> data.routines.any { it.id == w.routineId } && recent(w) })
            completeExpiredSetTimer()
        } catch (e: Exception) {
            status = if (e.message == "permission_lost") Status.NeedsFolder else Status.Error(e.message ?: "Vault load failed")
        }
    }

    /** fix: the web app dropped a workout once the date changed (23:40 -> 00:10 lost it). Keep it for 18 h. */
    private fun recent(w: ActiveWorkout) = System.currentTimeMillis() - w.startedAt < 18 * 3600_000L

    private fun parse(texts: Map<VaultFile, String?>): VaultData {
        val cfg = format.parseConfig(texts[VaultFile.Config])
        val customs = catalog.registerCustoms(cfg.customExercises)
        val meals = format.parseMeals(texts[VaultFile.Meals])
        val base = VaultData(
            routines = format.parseRoutines(texts[VaultFile.Routines]),
            logs = format.parseTrainingLogs(texts[VaultFile.TrainingLogs]),
            meals = meals,
            history = format.parseNutritionDiary(texts[VaultFile.NutritionDiary]),
            customExercises = customs,
        )
        return format.applyConfig(cfg, base)
    }

    private fun serialize(file: VaultFile, d: VaultData): String = when (file) {
        VaultFile.Routines -> format.writeRoutines(d.routines)
        VaultFile.Meals -> format.writeMeals(d.meals)
        VaultFile.TrainingLogs -> format.writeTrainingLogs(d.logs, d.prefs.units)
        VaultFile.NutritionDiary -> format.writeNutritionDiary(d.history)
        VaultFile.Config -> format.writeConfig(d)
    }

    private fun save(vararg files: VaultFile) {
        for (f in files) {
            val text = serialize(f, data)
            if (text == onDisk[f] && f !in pending) continue
            pending[f] = text
        }
        if (flushJob?.isActive != true) flushJob = scope.launch { delay(300); flushNow() }
    }

    /** Writes the latest content of every pending file. A save that arrives mid-write is picked up by the loop. */
    suspend fun flushNow() = writeLock.withLock {
        while (pending.isNotEmpty()) {
            val (file, text) = pending.entries.first()
            pending.remove(file)
            try {
                withContext(Dispatchers.IO) {
                    if (file !in backedUp) { onDisk[file]?.let { vault.backup(file.fileName, it) }; backedUp += file }
                    vault.write(file.fileName, text)
                }
                onDisk[file] = text
            } catch (e: Exception) {
                pending.putIfAbsent(file, text)
                message = "Couldn't save ${file.fileName}: ${e.message}"
                if (e.message == "permission_lost") status = Status.NeedsFolder
                return@withLock
            }
        }
    }

    fun onPause() { scope.launch { flushNow() } }

    /** Picks up edits made outside the app (another device syncing the folder, a text editor). */
    fun onResume() {
        if (status == Status.Ready) completeExpiredSetTimer()
        if (status != Status.Ready || pending.isNotEmpty()) return
        scope.launch {
            val texts = runCatching { withContext(Dispatchers.IO) { VaultFile.entries.associateWith { vault.read(it.fileName) } } }.getOrNull() ?: return@launch
            if (pending.isEmpty() && texts.any { (f, t) -> t != null && t != onDisk[f] }) {
                data = parse(texts); onDisk.putAll(texts)
                message = "Vault reloaded (changed outside the app)"
                syncOutside()
            }
        }
    }

    private fun update(vararg files: VaultFile, transform: (VaultData) -> VaultData) {
        data = transform(data)
        save(*files)
        syncOutside()
    }

    // ------------------------------------------------------------------ exercises

    fun toggleLike(id: String) = update(VaultFile.Config) { it.copy(liked = if (id in it.liked) it.liked - id else it.liked + id) }

    fun addCustomExercise(name: String, category: String, equipment: String, target: String, description: String): Exercise? {
        if (data.customExercises.any { it.name.equals(name.trim(), true) }) { message = "An exercise with that name exists"; return null }
        val e = catalog.normalizeCustom(mapOf("name" to name, "category" to category, "equipment" to equipment, "target" to target, "description" to description)) ?: return null
        val list = data.customExercises + e
        catalog.setCustoms(list)
        update(VaultFile.Config) { it.copy(customExercises = list) }
        return e
    }

    // ------------------------------------------------------------------ routines & schedule

    fun saveRoutine(r: Routine) = update(VaultFile.Routines, VaultFile.Config) { d ->
        val clean = format.sanitizeSupersets(r.copy(name = r.name.trim().take(40).ifEmpty { "Routine" }))
        d.copy(routines = if (d.routines.any { it.id == r.id }) d.routines.map { if (it.id == r.id) clean else it } else d.routines + clean)
    }

    fun newRoutine(name: String): Routine {
        val r = Routine(id = "r-${System.currentTimeMillis()}", name = name.trim().take(40).ifEmpty { "Routine" })
        saveRoutine(r)
        return r
    }

    fun deleteRoutine(id: String) {
        if (workout?.routineId == id) discardWorkout()
        update(VaultFile.Routines, VaultFile.Config) { d ->
            d.copy(routines = d.routines.filter { it.id != id }, schedule = d.schedule.filterValues { it != id })
        }
    }

    fun addToRoutine(routineId: String, exerciseId: String) = update(VaultFile.Routines) { d ->
        d.copy(routines = d.routines.map { r ->
            if (r.id != routineId || r.items.any { it.exerciseId == exerciseId }) r
            else r.copy(items = r.items + RoutineItem(exerciseId, timed = exercise(exerciseId)?.timedCardio == true,
                reps = if (exercise(exerciseId)?.timedCardio == true) 30 else 10))
        })
    }

    fun setSchedule(day: Int, routineId: String?) = update(VaultFile.Config) { d ->
        d.copy(schedule = if (routineId == null) d.schedule - day else d.schedule + (day to routineId))
    }

    fun loadDefaultRoutines() = update(VaultFile.Routines) { d ->
        d.copy(routines = d.routines + Defaults.routines.filter { def -> d.routines.none { it.id == def.id } })
    }

    // ------------------------------------------------------------------ logs

    fun addLog(log: ProgressLog) = update(VaultFile.TrainingLogs) { it.copy(logs = it.logs + log) }
    fun deleteLog(id: String) = update(VaultFile.TrainingLogs) { it.copy(logs = it.logs.filter { l -> l.id != id }) }

    // ------------------------------------------------------------------ meals & diary

    fun saveMeal(m: Meal) = update(VaultFile.Meals) { d ->
        d.copy(meals = if (d.meals.any { it.id == m.id }) d.meals.map { if (it.id == m.id) m else it } else d.meals + m)
    }
    fun deleteMeal(id: String) = update(VaultFile.Meals) { d -> d.copy(meals = d.meals.filter { it.id != id }) }
    fun loadDefaultMeals() = update(VaultFile.Meals) { d -> d.copy(meals = d.meals + Defaults.meals.filter { def -> d.meals.none { it.id == def.id } }) }

    private fun editDay(date: String, f: (DayLog) -> DayLog) = update(VaultFile.NutritionDiary) { d ->
        d.copy(history = d.history + (date to f(d.history[date] ?: DayLog())))
    }
    fun logMeal(date: String, entry: MealEntry) {
        editDay(date) { it.copy(meals = it.meals + entry) }
        HealthSync.writeMeal(this, date, entry)
    }
    fun deleteMealEntry(date: String, id: String) {
        editDay(date) { it.copy(meals = it.meals.filter { m -> m.id != id }) }
        HealthSync.deleteMeal(this, id)
    }
    fun addWater(date: String, ml: Double) = editDay(date) { it.copy(water = (it.water + ml).coerceIn(0.0, 50000.0)) }
    fun newMealEntryId() = format.freshMealId()

    // ------------------------------------------------------------------ settings

    fun setProfile(p: Profile) = update(VaultFile.Config) { it.copy(profile = p) }
    fun setPrefs(p: Prefs) {
        val unitsChanged = p.units != data.prefs.units
        update(VaultFile.Config) { it.copy(prefs = p) }
        device.lastAccent = p.accent
        if (unitsChanged) save(VaultFile.TrainingLogs)
    }
    val unitsLocked: Boolean get() = data.logs.isNotEmpty()

    // ------------------------------------------------------------------ active workout

    private fun routineOf(w: ActiveWorkout) = data.routines.firstOrNull { it.id == w.routineId }

    private fun putWorkout(w: ActiveWorkout?) { workout = w; device.saveWorkout(w); syncOutside() }

    /** Exercises already celebrated this session, so a PR buzzes once, not on every heavier set. */
    private val celebrated = mutableSetOf<String>()

    /** A set heavier than every earlier log (this session's own log excluded) is a PR. First-ever logs aren't. */
    private fun checkPr(w: ActiveWorkout, exerciseId: String, row: SetRow) {
        val weight = row.weight ?: return
        if (weight <= 0) return
        val key = "${w.token}-$exerciseId"
        if (key in celebrated) return
        val before = WorkoutLogic.record(data.logs.filter { it.id != WorkoutLogic.awLogId(w, exerciseId) }, exerciseId) ?: return
        if (weight > before) {
            celebrated += key
            Haptics.pr(app)
            message = "New PR: ${com.thehhr.form.ui.num(weight)} ${data.prefs.units.weight} · ${com.thehhr.form.ui.exTitle(exercise(exerciseId))}"
        }
    }

    fun startWorkout(routine: Routine) {
        if (workout != null || routine.items.isEmpty()) return
        val w = WorkoutLogic.start(routine, data.logs)
        // 10-minute warm-up countdown, as in the web app
        putWorkout(w.copy(rest = Rest(System.currentTimeMillis() + 600_000, 600, "sets", routine.items.first().exerciseId, warmup = true)))
    }

    private fun editWorkout(f: (ActiveWorkout, Routine) -> ActiveWorkout) {
        val w = workout ?: return
        val r = routineOf(w) ?: return
        putWorkout(WorkoutLogic.syncSets(f(w, r), r, data.logs))
    }

    private fun syncLog(exerciseId: String) {
        val w = workout ?: return
        val r = routineOf(w) ?: return
        val item = WorkoutLogic.entries(w, r).firstOrNull { it.item.exerciseId == exerciseId }?.item ?: return
        update(VaultFile.TrainingLogs) { it.copy(logs = WorkoutLogic.syncLog(it.logs, w, item)) }
    }

    fun updateRow(exerciseId: String, index: Int, row: SetRow) {
        val before = workout?.rows(exerciseId)?.getOrNull(index) ?: return
        editWorkout { w, _ -> w.copy(sets = w.sets + (exerciseId to w.rows(exerciseId).mapIndexed { i, r -> if (i == index) row else r })) }
        if (before.done || row.done) syncLog(exerciseId)
        if (!before.done && row.done) {
            workout?.let { checkPr(it, exerciseId, row) }
            afterSetDone(exerciseId)
        }
    }

    fun toggleRow(exerciseId: String, index: Int) {
        val row = workout?.rows(exerciseId)?.getOrNull(index) ?: return
        updateRow(exerciseId, index, row.copy(done = !row.done))
    }

    /** Big "log next set" action: checks the next pending set of the active exercise. */
    fun logNextSet() {
        val w = workout ?: return
        val r = routineOf(w) ?: return
        val target = WorkoutLogic.nextTarget(w, r) ?: run { message = "All done!"; return }
        val index = w.rows(target.exerciseId).indexOfFirst { !it.done }
        if (index >= 0) toggleRow(target.exerciseId, index)
    }

    private fun afterSetDone(exerciseId: String) {
        val w = workout ?: return
        val r = routineOf(w) ?: return
        val session = WorkoutLogic.sessionRoutine(w, r)
        val item = session.items.firstOrNull { it.exerciseId == exerciseId } ?: return
        var next = w
        if (w.complete(exerciseId) && w.jumpTo == exerciseId) next = next.copy(jumpTo = null)
        val p = data.prefs
        next = if (!p.restEnabled) next.copy(rest = null) else when (WorkoutLogic.restDecision(next, r, item)) {
            WorkoutLogic.RestKind.BETWEEN_SETS -> next.copy(rest = rest(p.restBetweenSets.toInt(), "sets", exerciseId))
            WorkoutLogic.RestKind.BETWEEN_EXERCISES -> next.copy(rest = rest(p.restBetweenExercises.toInt(), "exercise", exerciseId))
            WorkoutLogic.RestKind.NONE -> next.copy(rest = null)
        }
        putWorkout(next)
        if (WorkoutLogic.nextTarget(next, r) == null) message = "All done!"
    }

    private fun rest(seconds: Int, type: String, exerciseId: String) = Rest(System.currentTimeMillis() + seconds * 1000L, seconds, type, exerciseId)

    fun addSet(exerciseId: String) = editWorkout { w, _ ->
        val rows = w.rows(exerciseId)
        if (rows.size >= 20) w else w.copy(sets = w.sets + (exerciseId to rows + (rows.lastOrNull()?.copy(done = false) ?: SetRow())))
    }

    /** fix: removes the last *pending* set; the web app removed the last row even if it was already logged. */
    fun removeSet(exerciseId: String) = editWorkout { w, _ ->
        val rows = w.rows(exerciseId)
        val idx = rows.indexOfLast { !it.done }
        if (idx < 0 || rows.size <= 1) w else w.copy(sets = w.sets + (exerciseId to rows.filterIndexed { i, _ -> i != idx }))
    }

    fun toggleSkip(exerciseId: String) = editWorkout { w, _ ->
        w.copy(skipped = if (exerciseId in w.skipped) w.skipped - exerciseId else w.skipped + exerciseId)
    }

    fun jumpTo(exerciseId: String) = editWorkout { w, _ -> w.copy(jumpTo = if (w.jumpTo == exerciseId) null else exerciseId) }

    fun addExtra(exerciseId: String) {
        val ex = exercise(exerciseId) ?: return
        editWorkout { w, _ -> w.copy(extras = w.extras + RoutineItem(exerciseId, timed = ex.timedCardio, reps = if (ex.timedCardio) 30 else 10)) }
    }

    /** fix: replacing also drops the replaced exercise's logs from this session (the web app left them behind). */
    fun replaceExercise(fromId: String, toId: String) {
        val w = workout ?: return
        val r = routineOf(w) ?: return
        val original = r.items.firstOrNull { (w.replacements[it.exerciseId] ?: it.exerciseId) == fromId }?.exerciseId ?: fromId
        update(VaultFile.TrainingLogs) { d -> d.copy(logs = d.logs.filter { it.id != WorkoutLogic.awLogId(w, fromId) }) }
        editWorkout { s, _ ->
            val extras = s.extras.map { if (it.exerciseId == fromId) it.copy(exerciseId = toId) else it }
            val reps = if (r.items.any { it.exerciseId == original }) s.replacements + (original to toId) else s.replacements
            s.copy(replacements = reps, extras = extras, sets = s.sets - fromId)
        }
    }

    fun togglePause() {
        val w = workout ?: return
        val now = System.currentTimeMillis()
        putWorkout(if (w.paused) {
            val gap = now - (w.pausedAt ?: now)
            w.copy(paused = false, pausedAt = null, pausedAccum = w.pausedAccum + gap,
                rest = w.rest?.let { r -> r.pausedRemaining?.let { r.copy(endsAt = now + it, pausedRemaining = null) } ?: r })
        } else {
            w.copy(paused = true, pausedAt = now, rest = w.rest?.let { r -> r.copy(pausedRemaining = (r.endsAt - now).coerceAtLeast(0)) })
        })
    }

    fun extendRest(seconds: Int) {
        val w = workout ?: return
        val r = w.rest ?: return
        val now = System.currentTimeMillis()
        putWorkout(w.copy(rest = r.copy(endsAt = maxOf(r.endsAt, now) + seconds * 1000L, total = r.total + seconds)))
    }

    fun skipRest() { workout?.let { putWorkout(it.copy(rest = null)) } }

    /** Starts a countdown for a timed set; when it ends the set is checked (see [completeSetTimer]). */
    fun startSetTimer(exerciseId: String, index: Int) {
        val w = workout ?: return
        val r = routineOf(w) ?: return
        val item = WorkoutLogic.entries(w, r).firstOrNull { it.item.exerciseId == exerciseId }?.item ?: return
        val row = w.rows(exerciseId).getOrNull(index)?.takeIf { !it.done } ?: return
        val seconds = (if (item.unitOrDefault == "min") row.duration * 60 else row.duration).toInt()
        if (seconds <= 0) { message = "Set a duration first"; return }
        if (w.paused) { message = "Resume the workout first"; return }
        putWorkout(w.copy(rest = Rest(System.currentTimeMillis() + seconds * 1000L, seconds, "set", exerciseId, setIndex = index)))
    }

    /**
     * Checks the set a set-timer was counting. Idempotent and never un-checks: the in-app cue and the
     * background alarm can both fire. [endsAt] (when given) must match the running timer.
     */
    fun completeSetTimer(endsAt: Long? = null) {
        val w = workout ?: return
        val rest = w.rest?.takeIf { it.type == "set" && (endsAt == null || it.endsAt == endsAt) } ?: return
        val ex = rest.exerciseId ?: return
        val idx = rest.setIndex ?: return
        val row = w.rows(ex).getOrNull(idx)
        if (row == null || row.done) { putWorkout(w.copy(rest = null)); return }
        putWorkout(w.copy(rest = null))
        updateRow(ex, idx, row.copy(done = true)) // logs the set and starts the normal rest
    }

    /** A set timer that ran out while nothing was listening (inexact alarm, app killed) completes now. */
    fun completeExpiredSetTimer() {
        val rest = workout?.rest ?: return
        if (rest.type == "set" && rest.pausedRemaining == null && rest.endsAt <= System.currentTimeMillis()) completeSetTimer(rest.endsAt)
    }

    fun finishWorkout(): Pair<Int, Int>? {
        val w = workout ?: return null
        val r = routineOf(w)
        var done = 0; var total = 0
        if (r != null) {
            val items = WorkoutLogic.entries(w, r).map { it.item }.filter { it.exerciseId !in w.skipped }
            total = items.size
            done = items.count { w.complete(it.exerciseId) }
            update(VaultFile.TrainingLogs) { d -> d.copy(logs = items.fold(d.logs) { acc, item -> WorkoutLogic.syncLog(acc, w, item) }) }
            if (done > 0) HealthSync.writeWorkout(this, w, System.currentTimeMillis())
        }
        putWorkout(null)
        return done to total
    }

    /** Shortcut / reminder action. */
    fun startToday(): Boolean {
        if (workout != null) return true
        val r = todaysRoutine() ?: return false
        startWorkout(r)
        return true
    }

    fun discardWorkout() {
        val w = workout ?: return
        update(VaultFile.TrainingLogs) { d -> d.copy(logs = WorkoutLogic.discardLogs(d.logs, w)) }
        putWorkout(null)
    }

    fun todaysRoutine(): Routine? {
        val dow = java.time.LocalDate.now().dayOfWeek.value % 7
        return data.schedule[dow]?.let { id -> data.routines.firstOrNull { it.id == id } }
    }

    val todayKey: String get() = today()
}
