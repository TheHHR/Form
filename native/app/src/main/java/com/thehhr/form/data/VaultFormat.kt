package com.thehhr.form.data

import java.text.Collator
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Parsers and writers for the 5 vault files, ported from www/app.js (3.6.3).
 * Writers must stay byte-identical to the web app; deliberate deviations are marked "fix:".
 */
class VaultFormat(private val catalog: Catalog) {

    private fun valid(id: String) = catalog.isValid(id)

    // ---------------------------------------------------------------- shared

    fun readLines(text: String?): List<String> =
        (text ?: "").replace("\r\n", "\n").replace("\r", "\n").split("\n").filter { line ->
            val t = line.trim()
            t.isNotEmpty() && !(t.startsWith("<!--") && t.endsWith("-->")) && !t.startsWith("//")
        }

    private fun cleanId(raw: String) = raw.replace(Regex("[^A-Za-z0-9_-]"), "").take(64)

    private val heading = Regex("^(#{1,6})\\s+(.+)$")
    private val idLine = Regex("^id:\\s*(\\S+)$", RegexOption.IGNORE_CASE)
    private val likedLine = Regex("^liked:\\s*true", RegexOption.IGNORE_CASE)
    private val likedBare = Regex("^liked$", RegexOption.IGNORE_CASE)
    private fun isLiked(line: String) = likedLine.containsMatchIn(line) || likedBare.matches(line)

    // ---------------------------------------------------------------- routines

    private val routineItemLine = Regex("^(\\d{4,5}|c-[\\w-]+)\\s+(\\d+)\\s*\\*\\s*(\\d+)(?:\\s+(\\S.*))?$")

    fun parseRoutines(text: String?, stamp: Long = System.currentTimeMillis()): List<Routine> {
        val routines = mutableListOf<Routine>()
        var current: Routine? = null
        var seen = mutableSetOf<String>()
        fun commit() { current?.let { c -> routines[routines.lastIndex] = c } }
        for (raw in readLines(text)) {
            var line = raw.trim()
            val h = heading.find(line)
            if (h != null) {
                commit()
                val nameText = h.groupValues[2].trim()
                if (h.groupValues[1].length == 1 && nameText.equals("routines", true)) { current = null; continue }
                current = Routine(
                    id = "r-$stamp-${routines.size}",
                    name = sanitizeVaultName(nameText).take(Limits.ROUTINE_NAME).ifEmpty { "Routine ${routines.size + 1}" },
                )
                routines.add(current)
                seen = mutableSetOf()
                continue
            }
            line = stripImportDash(line)
            if (isLiked(line)) { current = current?.copy(liked = true); commit(); continue }
            val idm = idLine.find(line)
            if (idm != null && current != null) {
                val clean = cleanId(idm.groupValues[1])
                if (clean.isNotEmpty() && routines.none { it.id == clean }) { current = current.copy(id = clean); commit() }
                continue
            }
            val ex = routineItemLine.find(line.removePrefix("#").trim())
            if (ex != null && current != null) {
                val id = ex.groupValues[1]
                if (!valid(id) || id in seen) continue
                seen.add(id)
                val tail = ex.groupValues[4]
                val timed = Regex("\\btimed\\b", RegexOption.IGNORE_CASE).containsMatchIn(tail)
                val unit = when {
                    Regex("\\bsec\\b", RegexOption.IGNORE_CASE).containsMatchIn(tail) -> "sec"
                    Regex("\\bmin\\b", RegexOption.IGNORE_CASE).containsMatchIn(tail) -> "min"
                    else -> null
                }
                val ss = Regex("\\bss(\\d+)(\\d)\\b", RegexOption.IGNORE_CASE).find(tail)
                // fix: reps cap matches the editor (100 for reps, 60 for timed); the web parser capped both at 60.
                val repsMax = if (timed) Limits.TIMED_REPS else Limits.REPS
                val item = RoutineItem(
                    exerciseId = id,
                    sets = vClamp(ex.groupValues[2], 1.0, Limits.SETS.toDouble(), 3.0).toInt(),
                    reps = vClamp(ex.groupValues[3], 0.0, repsMax.toDouble(), 3.0).toInt(),
                    timed = timed,
                    unit = unit,
                    unweighted = Regex("\\bunweighted\\b", RegexOption.IGNORE_CASE).containsMatchIn(tail),
                    distance = timed && Regex("\\bdistance\\b", RegexOption.IGNORE_CASE).containsMatchIn(tail),
                    superset = ss?.groupValues?.get(1),
                )
                current = current.copy(items = current.items + item)
                commit()
            }
        }
        return routines.map(::sanitizeSupersets)
    }


    /**
     * Routines from [parsed] that aren't already in [existing], compared as written text without id/liked lines, so
     * superset tokens (renumbered ss11, ss12… on export) and ids don't make a re-import look new.
     */
    fun newRoutines(existing: List<Routine>, parsed: List<Routine>): List<Routine> {
        fun sig(r: Routine) = writeRoutines(listOf(r.copy(id = "x", liked = false))).lines().filterNot { it.startsWith("- id:") || it == "- liked" }.joinToString("\n")
        val have = existing.map(::sig).toMutableSet()
        return parsed.filter { have.add(sig(it)) }
    }
    fun sanitizeSupersets(r: Routine): Routine {
        val counts = r.items.mapNotNull { it.superset }.groupingBy { it }.eachCount()
        return r.copy(items = r.items.map { if (it.superset != null && counts[it.superset] != 2) it.copy(superset = null) else it })
    }

    fun writeRoutines(routines: List<Routine>): String {
        val lines = mutableListOf("# Routines", "")
        for (r in routines) {
            val labels = supersetLabels(r)
            lines.add("## ${sanitizeVaultName(r.name)}")
            if (r.id.isNotEmpty()) lines.add("- id: ${r.id}")
            if (r.liked) lines.add("- liked")
            r.items.forEachIndexed { i, item ->
                val sb = StringBuilder("${item.exerciseId} ${item.sets} * ${item.reps}")
                if (item.timed) sb.append(if (item.unit == "min") " timed min" else " timed")
                if (item.unweighted) sb.append(" unweighted")
                if (item.distance && item.timed) sb.append(" distance")
                labels[i]?.let { sb.append(" ").append(it) }
                lines.add(sb.toString())
            }
            lines.add("")
        }
        return lines.joinToString("\n").trim() + "\n"
    }

    private fun supersetLabels(r: Routine): Map<Int, String> {
        val groups = LinkedHashMap<String, Int>()
        val seen = HashMap<String, Int>()
        val out = HashMap<Int, String>()
        r.items.forEachIndexed { i, item ->
            val token = item.superset ?: return@forEachIndexed
            val group = groups.getOrPut(token) { groups.size + 1 }
            val member = (seen[token] ?: 0) + 1
            seen[token] = member
            out[i] = "ss$group$member"
        }
        return out
    }

    // ---------------------------------------------------------------- meals

    private val per100 = Regex("^Per100g\\s+(\\d+(?:\\.\\d+)?)\\s*cal\\s+(\\d+(?:\\.\\d+)?)\\s*pro\\s+(\\d+(?:\\.\\d+)?)\\s*carb\\s+(\\d+(?:\\.\\d+)?)\\s*fat$", RegexOption.IGNORE_CASE)
    private val mealHeader = Regex("^(.+)\\s*\\(\\s*(\\d+(?:\\.\\d+)?)\\s*g\\s*\\)$", RegexOption.IGNORE_CASE)

    fun parseMeals(text: String?, stamp: Long = System.currentTimeMillis()): List<Meal> {
        val meals = mutableListOf<Meal>()
        var idx = -1
        for (raw in readLines(text)) {
            var line = raw.trim()
            if (Regex("^#{1,6}\\s").containsMatchIn(line) && !Regex("^# Meal Library", RegexOption.IGNORE_CASE).containsMatchIn(line)) idx = -1
            val hm = mealHeader.find(line)
            if (hm != null) {
                meals.add(Meal(
                    id = "m-$stamp-${meals.size}",
                    name = sanitizeVaultName(hm.groupValues[1]).take(Limits.ROUTINE_NAME).ifEmpty { "Meal" },
                    defaultGrams = vClamp(hm.groupValues[2], 1.0, 5000.0, 100.0),
                ))
                idx = meals.lastIndex
                continue
            }
            line = stripImportDash(line)
            if (isLiked(line)) { if (idx >= 0) meals[idx] = meals[idx].copy(liked = true); continue }
            val idm = idLine.find(line)
            if (idm != null && idx >= 0) {
                val clean = cleanId(idm.groupValues[1])
                if (clean.isNotEmpty() && meals.none { it.id == clean }) meals[idx] = meals[idx].copy(id = clean)
                continue
            }
            val mm = per100.find(line)
            if (mm != null && idx >= 0) {
                meals[idx] = meals[idx].copy(
                    cals100 = jsRound(jsNumber(mm.groupValues[1])).toDouble(),
                    p100 = vClamp(mm.groupValues[2], 0.0, 999.0, 0.0),
                    c100 = vClamp(mm.groupValues[3], 0.0, 999.0, 0.0),
                    f100 = vClamp(mm.groupValues[4], 0.0, 999.0, 0.0),
                )
            }
        }
        return meals.filter { it.name.isNotEmpty() }
    }

    fun writeMeals(meals: List<Meal>): String {
        val lines = mutableListOf("# Meal Library", "")
        for (m in meals) {
            if (m.id == "custom") continue
            lines.add("${sanitizeVaultName(m.name)} (${jsNum(if (m.defaultGrams == 0.0) 100.0 else m.defaultGrams)}g)")
            lines.add("Per100g ${jsNum(m.cals100)}cal ${jsNum(m.p100)}pro ${jsNum(m.c100)}carb ${jsNum(m.f100)}fat")
            if (m.id.isNotEmpty()) lines.add("- id: ${m.id}")
            if (m.liked) lines.add("- liked")
            lines.add("")
        }
        return lines.joinToString("\n").trim() + "\n"
    }

    // ---------------------------------------------------------------- training logs

    /** Pre-normalization log, mirroring the loose object the web parser builds. */
    data class RawLog(
        val exerciseId: String,
        val date: String?,
        val sets: String? = null,
        val reps: String? = null,
        val weight: String? = null,
        val setWeights: List<Double>? = null,
        val setReps: List<Double>? = null,
        val intervals: String? = null,
        val setDurations: List<Double>? = null,
        val setDistances: List<Double>? = null,
        val duration: String? = null,
        val distance: String? = null,
        val durUnit: String? = null,
        val notes: String? = null,
        val id: String? = null,
        val createdAt: Long? = null,
    )

    private fun toList(raw: String?): List<String>? =
        if (raw.isNullOrEmpty()) null else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private fun numList(raw: String?) = toList(raw)?.map(::jsNumber)

    private fun durationValue(v: Double): Double? = if (v > 0) round4(min(Limits.DURATION, v)) else null
    private fun distanceValue(v: Double): Double? = if (v > 0) round1(min(Limits.DISTANCE, v)) else null
    private fun nz(v: Double) = if (v.isNaN()) 0.0 else v

    fun normalizeLog(log: RawLog, index: Int): ProgressLog? {
        val exerciseId = log.exerciseId.removePrefix("#")
        if (!valid(exerciseId) || !isValidDate(log.date)) return null
        val idTs = Regex("\\d{10,}").find(log.id ?: "")?.value?.toLongOrNull()
        val ts = (log.createdAt ?: idTs)?.takeIf { it != 0L } ?: (System.currentTimeMillis() + index)
        val session = Regex("^(\\d{10,})-aw-").find(log.id ?: "")?.groupValues?.get(1)
        val setWeights = log.setWeights?.map { min(Limits.WEIGHT, max(0.0, nz(it))) }?.take(Limits.SETS)?.takeIf { it.isNotEmpty() }
        val id = log.id?.takeIf { it.isNotEmpty() } ?: ts.toString()
        val notes = (log.notes ?: "").take(Limits.NOTES)

        // timed?
        val durations = log.setDurations?.map { round4(jsClamp(nz(it), 0.0, Limits.DURATION)) }?.take(Limits.SETS)?.takeIf { it.isNotEmpty() }
        val distances = log.setDistances?.map { round1(min(Limits.DISTANCE, max(0.0, nz(it)))) }?.take(Limits.SETS)?.takeIf { it.isNotEmpty() }
        val flatDur = durationValue(jsNumber(log.duration))
        val flatDist = distanceValue(jsNumber(log.distance))
        val intervalN = jsNumber(log.intervals)
        val looksTimed = intervalN > 0 || durations != null || distances != null ||
            ((flatDur != null || flatDist != null) && log.setReps == null && log.setWeights == null)
        if (looksTimed) {
            val intervalsRaw = if (intervalN > 0) intervalN else (durations?.size ?: distances?.size ?: 1).toDouble()
            val intervals = jsClamp(intervalsRaw, 1.0, Limits.SETS.toDouble()).toInt()
            val durList = (durations ?: listOfNotNull(flatDur)).take(intervals)
            val distList = (distances ?: listOfNotNull(flatDist)).take(intervals)
            return ProgressLog(
                id = id, exerciseId = exerciseId, date = log.date!!, sessionId = session,
                intervals = intervals,
                durUnit = log.durUnit?.takeIf { it == "sec" || it == "min" },
                setDurations = durList.takeIf { l -> l.any { it > 0 } },
                setDistances = distList.takeIf { l -> l.any { it > 0 } },
                weight = setWeights?.lastOrNull(),
                setWeights = setWeights,
                notes = notes, createdAt = ts,
            )
        }
        val sets = jsClamp(log.sets, 1.0, Limits.SETS.toDouble()).toInt()
        val setCount = setWeights?.size ?: sets
        val setReps = log.setReps?.map { jsClamp(it, 1.0, Limits.REPS.toDouble()).roundToIntSafe() }?.take(setCount)?.takeIf { it.isNotEmpty() }
            ?: if (setCount > 0) List(setCount) { jsClamp(log.reps, 1.0, Limits.REPS.toDouble()).roundToIntSafe() } else null
        return ProgressLog(
            id = id, exerciseId = exerciseId, date = log.date!!, sessionId = session,
            sets = sets,
            reps = jsClamp(log.reps, 1.0, Limits.REPS.toDouble()).roundToIntSafe(),
            weight = if (log.weight.isNullOrEmpty()) null else min(Limits.WEIGHT, max(0.0, nz(jsNumber(log.weight)))),
            setWeights = setWeights, setReps = setReps,
            notes = notes, createdAt = ts,
        )
    }

    private val blockKey = Regex("^([A-Za-z]+)\\s*(?:\\(([^)]*)\\))?\\s*:\\s*(.*)$")

    fun parseTrainingLogs(text: String?): List<ProgressLog> {
        val logs = mutableListOf<ProgressLog>()
        var currentDate: String? = null
        var block: MutableMap<String, String>? = null

        fun flush() {
            val b = block ?: return
            block = null
            val sec = b["durationsUnit"] == "sec"
            val setDurations = toList(b["durationsRaw"])?.map {
                val n = jsNumber(it); if (n.isNaN() || n.isInfinite()) 0.0 else round4(if (sec) n / 60 else n)
            } ?: numList(b["duration"])
            val repsList = toList(b["reps"])
            normalizeLog(RawLog(
                exerciseId = b["exerciseId"] ?: "",
                date = b["date"] ?: currentDate,
                sets = b["sets"],
                reps = repsList?.firstOrNull() ?: b["reps"],
                weight = if (b["weightsRaw"] != null) null else b["weight"],
                setWeights = numList(b["weightsRaw"]) ?: numList(b["setweights"]),
                setReps = numList(b["setreps"]) ?: repsList?.map(::jsNumber),
                intervals = b["int"] ?: b["intervals"] ?: b["interval"],
                setDurations = setDurations,
                setDistances = numList(b["distancesRaw"]) ?: numList(b["distance"]),
                duration = b["duration"],
                distance = b["distance"],
                durUnit = if (setDurations != null) b["durationsUnit"] else null,
                notes = b["notes"],
                id = b["id"],
            ), logs.size)?.let(logs::add)
        }

        for (raw in readLines(text)) {
            var line = raw.trim()
            if (Regex("^#\\s+Training Log", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
            val h = Regex("^#{1,6}\\s+(.+)$").find(line)
            if (h != null) {
                flush()
                val ht = h.groupValues[1].trim()
                val iso = Regex("^(\\d{4}-\\d{2}-\\d{2})").find(ht)?.groupValues?.get(1)
                currentDate = parseVaultDateHeading(ht) ?: iso?.takeIf(::isValidDate)
                continue
            }
            line = stripImportDash(line)
            if (currentDate == null || line.isEmpty()) continue
            if (Regex("^water:", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
            val km = blockKey.find(line)
            if (km != null && km.groupValues[1].lowercase() == "exercise") {
                flush()
                block = mutableMapOf("exercise" to km.groupValues[3].trim())
                continue
            }
            val b = block
            if (b != null) {
                if (km == null) { flush(); parseLegacyLine(line, currentDate, logs); continue }
                val key = km.groupValues[1].lowercase()
                val unit = km.groupValues[2].trim().lowercase()
                val value = km.groupValues[3].trim()
                when {
                    key == "dur" -> { b["durationsRaw"] = value; b["durationsUnit"] = if (unit == "sec") "sec" else "min" }
                    key == "dist" -> b["distancesRaw"] = value
                    key == "weight" && (unit == "kg" || unit == "lb") -> b["weightsRaw"] = value
                    key == "exerciseid" -> b["exerciseId"] = value
                    else -> b[key] = value
                }
                continue
            }
            parseLegacyLine(line, currentDate, logs)
        }
        flush()
        // Collapse duplicate ids, keeping the newest createdAt (first position wins).
        val byId = LinkedHashMap<String, ProgressLog>()
        for (log in logs) {
            val existing = byId[log.id]
            if (existing == null || log.createdAt > existing.createdAt) byId[log.id] = log
        }
        return byId.values.toList()
    }

    /** Pre-block single-line format: `0025 | 3 sets | 8, 8, 6 reps | 60 kg | notes: ... | id: ...`. */
    private fun parseLegacyLine(line: String, date: String?, logs: MutableList<ProgressLog>) {
        val parts = line.split("|").map { it.trim() }
        if (parts.size < 2) return
        val exerciseId = parts[0].removePrefix("#").trim()
        if (!valid(exerciseId)) return
        val id = Regex("id:\\s*([^\\s|]+)\\s*$", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1) ?: "log-$date-${logs.size}"
        val notes = Regex("notes:\\s*(.*?)(?:\\s*\\|\\s*id:|$)", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)?.take(Limits.NOTES)?.trim() ?: ""
        val createdAt = Regex("\\d{10,}").find(id)?.value?.toLongOrNull() ?: System.currentTimeMillis()
        val notesIdx = Regex("notes:", RegexOption.IGNORE_CASE).find(line)?.range?.first ?: -1
        val dataLine = if (notesIdx >= 0) line.substring(0, notesIdx) else line
        val dataParts = dataLine.split("|").map { it.trim() }
        val raw = if (Regex("\\bintervals?\\b", RegexOption.IGNORE_CASE).containsMatchIn(dataLine) ||
            Regex("\\bmin\\b", RegexOption.IGNORE_CASE).containsMatchIn(dataParts.drop(1).joinToString(" "))) {
            val interval = Regex("(\\d+)\\s*intervals?").find(dataLine)?.groupValues?.get(1)
            val durMin = Regex("([\\d.,\\s]+)\\s*min").find(dataLine)
            val durSec = Regex("([\\d.,\\s]+)\\s*sec", RegexOption.IGNORE_CASE).find(dataLine)
            var durUnit: String? = null
            val durations = when {
                durMin != null -> durMin.groupValues[1].split(",").map { vClamp(it.trim(), 0.0, Limits.DURATION, 0.0) }.filter { it > 0 }
                durSec != null -> { durUnit = "sec"; durSec.groupValues[1].split(",").map { round4(vClamp(it.trim(), 0.0, Limits.DURATION * 60, 0.0) / 60) }.filter { it > 0 } }
                else -> null
            }
            val dist = Regex("([\\d.,\\s]+)\\s*(km|mi)").find(dataLine)?.groupValues?.get(1)
                ?.split(",")?.map { vClamp(it.trim(), 0.0, Limits.DISTANCE, 0.0) }?.filter { it > 0 }
            RawLog(exerciseId, date, intervals = jsNum(vClamp(interval ?: "1", 1.0, Limits.SETS.toDouble(), 1.0)),
                setDurations = durations, setDistances = dist, durUnit = durUnit, notes = notes, id = id, createdAt = createdAt)
        } else {
            val sets = Regex("(\\d+)\\s*sets?").find(dataLine)?.groupValues?.get(1)
            val repsPart = dataParts.firstOrNull { Regex("reps?", RegexOption.IGNORE_CASE).containsMatchIn(it) }
            val setReps = repsPart?.replaceFirst(Regex("reps?", RegexOption.IGNORE_CASE), "")?.trim()
                ?.split(",")?.map { vClamp(it.trim(), 1.0, Limits.REPS.toDouble(), 10.0) }
            val reps = if (repsPart == null) Regex("(\\d+)\\s*x\\s*(\\d+)").find(dataLine)?.groupValues?.get(2) ?: "10" else null
            val weightPart = dataParts.firstOrNull { it.contains("kg", true) }
            val weights = weightPart?.replaceFirst(Regex("kg", RegexOption.IGNORE_CASE), "")?.trim()?.takeIf { it.isNotEmpty() }
                ?.split(",")?.mapNotNull { s -> jsNumber(s.trim()).takeIf { it.isFinite() && it > 0 }?.let { min(Limits.WEIGHT, it) } }
            RawLog(exerciseId, date, sets = jsNum(vClamp(sets ?: "1", 1.0, Limits.SETS.toDouble(), 3.0)),
                reps = reps?.let { jsNum(vClamp(it, 1.0, Limits.REPS.toDouble(), 10.0)) },
                setReps = setReps, setWeights = weights, notes = notes, id = id, createdAt = createdAt)
        }
        normalizeLog(raw, logs.size)?.let(logs::add)
    }

    fun writeTrainingLogs(logs: List<ProgressLog>, units: Units): String {
        val lines = mutableListOf("# Training Log", "")
        val sanitize = { v: String -> v.replace("|", "/").replace(Regex("\\s+"), " ").trim() }
        val byDate = LinkedHashMap<String, MutableList<ProgressLog>>()
        for (log in logs) if (isValidDate(log.date)) byDate.getOrPut(log.date) { mutableListOf() }.add(log)
        for (date in byDate.keys.sorted()) {
            lines.add("## ${vaultDateHeading(date)}")
            for (log in byDate[date]!!) {
                val ex = catalog.byId(log.exerciseId)
                val fields = mutableListOf(
                    "exercise: ${jsonQuote(jsTitle(ex?.name?.ifEmpty { null } ?: "Unknown exercise"))}",
                    "exerciseId: #${log.exerciseId}",
                )
                if (log.timed) {
                    val durUnit = log.durUnit?.takeIf { it == "sec" || it == "min" } ?: if (ex?.timedCardio == true) "min" else "sec"
                    val scale = if (durUnit == "sec") 60 else 1
                    val durs = (log.setDurations ?: emptyList()).map { round2(nz(it) * scale) }.filter { it > 0 }
                    fields.add("int: ${log.intervals?.takeIf { it != 0 } ?: durs.size.takeIf { it != 0 } ?: 1}")
                    if (durs.isNotEmpty()) fields.add("dur($durUnit): ${durs.joinToString(", ") { jsNum(it) }}")
                    val dists = (log.setDistances ?: emptyList()).map { round2(nz(it)) }.filter { it > 0 }
                    if (dists.isNotEmpty()) fields.add("dist(${units.distance}): ${dists.joinToString(", ") { jsNum(it) }}")
                    // fix: the web writer dropped weights on timed logs (weighted carries); its parser reads this line.
                    val ws = (log.setWeights ?: emptyList()).map { round1(nz(it)) }.filter { it > 0 }
                    if (ws.isNotEmpty()) fields.add("weight(${units.weight}): ${ws.joinToString(", ") { jsNum(it) }}")
                } else {
                    fields.add("sets: ${(log.sets ?: 1).coerceIn(1, Limits.SETS)}")
                    val ws = (log.setWeights ?: emptyList()).map { round1(nz(it)) }.filter { it > 0 }
                    if (ws.isNotEmpty()) fields.add("weight(${units.weight}): ${ws.joinToString(", ") { jsNum(it) }}")
                    val reps = (log.setReps?.takeIf { it.isNotEmpty() } ?: listOf(log.reps ?: 1)).map { it.coerceIn(1, Limits.REPS) }
                    fields.add("reps: ${reps.joinToString(", ")}")
                }
                val notes = sanitize(log.notes)
                if (notes.isNotEmpty()) fields.add("notes: $notes")
                fields.add("id: ${log.id}")
                lines.add(fields.mapIndexed { i, f -> if (i == 0) "- $f" else "\t- $f" }.joinToString("\n"))
                lines.add("")
            }
        }
        return lines.joinToString("\n").trim() + "\n"
    }

    // ---------------------------------------------------------------- nutrition diary

    private val boldMeal = Regex("^(.*?)\\s+(\\d+)\\s+kcal\\s+·\\s+\\*\\*([\\d.]+)p\\s+·\\s+([\\d.]+)c\\s+·\\s+([\\d.]+)f\\*\\*\\s*$", RegexOption.IGNORE_CASE)
    private val pipeMeal = Regex("^(.*?)\\s*\\|\\s*(\\d+)\\s*kcal\\s*\\|\\s*([\\d.]+)\\s*p\\s*·\\s*([\\d.]+)\\s*c\\s*·\\s*([\\d.]+)\\s*f(?:\\s*\\|\\s*id:\\s*(\\S+))?$", RegexOption.IGNORE_CASE)

    private var idSeq = 0
    /** fix: entries without an id used to all get id 0 (deleting one deleted them all). */
    fun freshMealId(): String = (System.currentTimeMillis() * 1000 + (idSeq++ % 1000)).toString()

    fun mealEntry(name: String, cals: String, p: String, c: String, f: String, category: String, id: String?): MealEntry? {
        val n = sanitizeVaultName(name).take(Limits.ROUTINE_NAME).ifEmpty { "Meal" }
        return MealEntry(
            id = id?.takeIf { it.isNotEmpty() && jsNumber(it).isFinite() } ?: freshMealId(),
            name = n,
            cals = jsRound(jsClamp(cals, 0.0, 100000.0)).toDouble(),
            p = round1(jsClamp(p, 0.0, 100000.0)),
            c = round1(jsClamp(c, 0.0, 100000.0)),
            f = round1(jsClamp(f, 0.0, 100000.0)),
            category = category.trim().ifEmpty { "Other" },
        )
    }

    fun parseNutritionDiary(text: String?): Map<String, DayLog> {
        val history = LinkedHashMap<String, Pair<Double, MutableList<MealEntry>>>()
        var date: String? = null
        var category: String? = null
        var lastIdx = -1
        for (raw in readLines(text)) {
            var line = raw.trim()
            if (Regex("^# Nutrition Diary", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
            val h = Regex("^#{1,6}\\s+(.+)$").find(line)
            if (h != null) {
                lastIdx = -1
                val ht = h.groupValues[1].trim()
                val iso = Regex("^(\\d{4}-\\d{2}-\\d{2})").find(ht)?.groupValues?.get(1)
                val vd = parseVaultDateHeading(ht) ?: iso?.takeIf(::isValidDate)
                if (vd != null) {
                    date = vd; category = null
                    history.getOrPut(vd) { 0.0 to mutableListOf() }
                    continue
                }
                if (date != null && iso == null) category = jsTitle(ht.replace(Regex("^#+\\s*"), ""))
                continue
            }
            val d = date ?: continue
            line = stripImportDash(line)
            val day = history[d]!!
            val wm = Regex("^water:\\s*(\\d+(?:\\.\\d+)?)\\s*ml", RegexOption.IGNORE_CASE).find(line)
            if (wm != null) { history[d] = vClamp(wm.groupValues[1], 0.0, 50000.0, 0.0) to day.second; continue }
            val idm = Regex("^id\\s*:\\s*(\\S+)\\s*$", RegexOption.IGNORE_CASE).find(line)
            if (idm != null) {
                if (lastIdx >= 0) day.second[lastIdx] = day.second[lastIdx].copy(id = idm.groupValues[1])
                continue
            }
            val bm = boldMeal.find(line)
            val pm = if (bm == null) pipeMeal.find(line) else null
            val m = bm ?: pm ?: continue
            mealEntry(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4], m.groupValues[5],
                category ?: "Snacks", if (pm != null) pm.groupValues[6] else null)?.let {
                day.second.add(it); lastIdx = day.second.lastIndex
            }
        }
        return history.mapValues { (_, v) -> DayLog(v.first, v.second.toList()) }
    }

    fun writeNutritionDiary(history: Map<String, DayLog>): String {
        val lines = mutableListOf("# Nutrition Diary", "")
        val known = listOf("Breakfast", "Lunch", "Dinner", "Snacks", "Other")
        for (date in history.keys.sorted()) {
            val day = history[date] ?: continue
            val water = if (day.water > 0) jsRound(day.water) else 0
            if (water == 0L && day.meals.isEmpty()) continue
            lines.add("## ${vaultDateHeading(date)}")
            if (water != 0L) lines.add("water: $water ml")
            // fix: categories outside the known five were silently dropped by the web writer.
            val cats = known + day.meals.map { it.category.ifEmpty { "Snacks" } }.distinct().filter { it !in known }
            for (cat in cats) {
                val meals = day.meals.filter { it.category.ifEmpty { "Snacks" } == cat }
                if (meals.isEmpty()) continue
                lines.add("### $cat")
                for (m in meals) {
                    lines.add("- ${sanitizeVaultName(m.name)} ${jsRound(m.cals)} kcal · **${jsNum(round1(m.p))}p · ${jsNum(round1(m.c))}c · ${jsNum(round1(m.f))}f**")
                    lines.add("\t- id: ${m.id}")
                }
            }
            lines.add("")
        }
        return lines.joinToString("\n").trim() + "\n"
    }

    // ---------------------------------------------------------------- config

    data class ParsedConfig(
        val profile: Map<String, Any> = emptyMap(),
        val overrides: Map<String, Double> = emptyMap(),
        val scheduleNames: Map<Int, String> = emptyMap(),
        val liked: List<String> = emptyList(),
        val prefs: Map<String, Any> = emptyMap(),
        val customExercises: List<Map<String, String>> = emptyList(),
        val exerciseTags: Map<String, List<String>> = emptyMap(),
    )

    private fun truthy(raw: String) = Regex("^(true|yes|1|on)", RegexOption.IGNORE_CASE).containsMatchIn(raw)

    fun parseConfig(text: String?): ParsedConfig {
        val profile = mutableMapOf<String, Any>()
        val overrides = mutableMapOf<String, Double>()
        val schedule = mutableMapOf<Int, String>()
        var liked = emptyList<String>()
        val prefs = mutableMapOf<String, Any>()
        val customs = mutableListOf<MutableMap<String, String>>()
        val tagsByTag = LinkedHashMap<String, MutableList<String>>()
        var section = ""
        var currentTag: String? = null
        val pillModes = listOf("default", "pin", "hidden")
        val hosts = listOf("routine", "category", "target", "equipment")
        for (raw in readLines(text)) {
            val line = stripImportDash(raw.trim())
            val sm = Regex("^#{1,6}\\s+(.+)$").find(line)
            if (sm != null) { section = sm.groupValues[1].trim().lowercase(); if (section != "exercise tags") currentTag = null; continue }
            if (Regex("^key:\\s*", RegexOption.IGNORE_CASE).containsMatchIn(line)) continue
            if (section == "exercise tags") {
                if (Regex("^id\\s*:").containsMatchIn(line) || Regex("^tags\\s*:").containsMatchIn(line)) { currentTag = null; continue }
                val tag = currentTag
                if (Regex("^[\\d,\\s]+$").matches(line) && tag != null) {
                    for (part in line.split(",")) {
                        val id = normalizeTagId(part)
                        if (id.isEmpty()) continue
                        val list = tagsByTag.getOrPut(tag) { mutableListOf() }
                        if (id !in list) list.add(id)
                    }
                    continue
                }
                if (line.isNotEmpty()) {
                    currentTag = sanitizeTagName(line)
                    if (currentTag.isNotEmpty()) tagsByTag.getOrPut(currentTag) { mutableListOf() }
                }
                continue
            }
            val kv = Regex("^([A-Za-z][\\w-]*)\\s*:\\s*(.*)$").find(line) ?: continue
            val key = kv.groupValues[1]
            val v = kv.groupValues[2].trim()
            when (section) {
                "profile" -> when (key) {
                    "age" -> profile["age"] = vClamp(v, 10.0, 110.0, 22.0)
                    "sex" -> profile["sex"] = if (Regex("^[mf]", RegexOption.IGNORE_CASE).containsMatchIn(v)) v.trim().lowercase().take(1) else "m"
                    "height" -> profile["heightCm"] = vClamp(v, 50.0, 300.0, 178.0)
                    "current-weight" -> profile["currentWeight"] = vClamp(v, 20.0, 500.0, 75.0)
                    "start-weight" -> profile["startWeight"] = vClamp(v, 20.0, 500.0, 75.0)
                    "goal-weight" -> profile["goalWeight"] = vClamp(v, 20.0, 500.0, 78.0)
                    "activity" -> profile["activity"] = vClamp(v, 1.0, 3.0, 1.55)
                    "strategy" -> profile["strategy"] = vClamp(v, -1000.0, 1000.0, 250.0)
                    "protein-rate" -> profile["proteinRate"] = vClamp(v, 0.5, 5.0, 2.0)
                }
                "targets", "macro overrides" -> when (key) {
                    "calories", "cals" -> vClampOrNull(v, 500.0, 10000.0)?.let { overrides["cals"] = it }
                    "protein", "p" -> vClampOrNull(v, 0.0, 1000.0)?.let { overrides["p"] = it }
                    "carbs", "c" -> vClampOrNull(v, 0.0, 1000.0)?.let { overrides["c"] = it }
                    "fats", "f" -> vClampOrNull(v, 0.0, 1000.0)?.let { overrides["f"] = it }
                    "water" -> vClampOrNull(v, 500.0, 10000.0)?.let { overrides["water"] = it }
                }
                "weekly schedule" -> {
                    val day = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat").indexOf(key)
                    if (day >= 0) schedule[day] = v
                }
                "custom exercises" -> when (key) {
                    "name" -> customs.add(mutableMapOf("name" to v))
                    "id", "category", "target", "equipment", "description" ->
                        customs.lastOrNull()?.put(key, if (key == "description") v.replace(" / ", "\n") else v)
                }
                "preferences" -> when (key) {
                    "accent" -> prefs["accent"] = if (v in ACCENTS) v else "blue"
                    "liked" -> liked = v.split(",").map { it.trim().removePrefix("#") }.filter(::valid)
                    "week-start" -> prefs["weekStart"] = jsNumber(v).let { if (it == 0.0 || it == 1.0 || it == 6.0) it.toInt() else 1 }
                    "default-view" -> prefs["defaultView"] = if (v in listOf("week", "month", "all")) v else "week"
                    "workout-reminder" -> prefs["workoutReminder"] = truthy(v)
                    "rest-enabled" -> prefs["restEnabled"] = truthy(v)
                    // fix: native allows rest up to 300 s; the web app capped it at 180 (it clamps 300 back to 180 if reopened there).
                    "rest-between-sets" -> prefs["restBetweenSets"] = vClamp(v, 30.0, 300.0, 60.0)
                    "rest-between-exercises" -> prefs["restBetweenExercises"] = vClamp(v, 30.0, 300.0, 90.0)
                    "rest-sound" -> prefs["restSound"] = truthy(v)
                    "pill-routine" -> prefs["pillRoutine"] = if (v in pillModes) v else "default"
                    "pill-category" -> prefs["pillCategory"] = if (v in pillModes) v else "default"
                    "pill-target" -> prefs["pillTarget"] = if (v in pillModes) v else "default"
                    "pill-equipment" -> prefs["pillEquipment"] = if (v in pillModes) v else "default"
                    "pill-tags-host" -> prefs["pillTagsHost"] = if (v in hosts) v else "equipment"
                    "pill-toggles" -> prefs["pillToggles"] = if (v in hosts) v else "equipment"
                    "tab-labels" -> prefs["tabLabels"] = truthy(v)
                    "units" -> {
                        val p = v.split(",").map { it.trim().lowercase() }
                        prefs["units"] = Units.normalize(p.getOrNull(0), p.getOrNull(1), p.getOrNull(2))
                    }
                }
            }
        }
        val exerciseTags = LinkedHashMap<String, MutableList<String>>()
        for ((tag, ids) in tagsByTag) {
            val clean = sanitizeTagName(tag)
            if (clean.isEmpty()) continue
            for (id in ids) {
                val list = exerciseTags.getOrPut(id) { mutableListOf() }
                if (list.none { it.equals(clean, true) }) list.add(clean)
            }
        }
        return ParsedConfig(profile, overrides, schedule, liked, prefs, customs, exerciseTags)
    }

    fun writeConfig(d: VaultData): String {
        val p = d.profile
        val ov = p.overrides
        val pr = d.prefs
        val lines = mutableListOf("# Config", "", "## Profile")
        lines += "age: ${jsNum(p.age)}"
        lines += "sex: ${p.sex.ifEmpty { "m" }}"
        lines += "height: ${jsNum(p.heightCm)}"
        lines += "current-weight: ${jsNum(p.currentWeight)}"
        lines += "start-weight: ${jsNum(p.startWeight)}"
        lines += "goal-weight: ${jsNum(p.goalWeight)}"
        lines += "activity: ${jsNum(p.activity)}"
        lines += "strategy: ${jsNum(p.strategy)}"
        lines += "protein-rate: ${jsNum(p.proteinRate)}"
        lines += listOf("", "## Targets")
        ov["cals"]?.let { lines += "calories: ${jsNum(it)}" }
        ov["p"]?.let { lines += "protein: ${jsNum(it)}" }
        ov["c"]?.let { lines += "carbs: ${jsNum(it)}" }
        ov["f"]?.let { lines += "fats: ${jsNum(it)}" }
        ov["water"]?.let { lines += "water: ${jsNum(it)}" }
        lines += listOf("", "## Weekly Schedule")
        val dayKeys = listOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        for (i in 0..6) {
            val rid = d.schedule[i]
            if (!rid.isNullOrEmpty()) lines += "${dayKeys[i]}: ${d.routines.firstOrNull { it.id == rid }?.name ?: rid}"
        }
        lines += listOf("", "## Preferences")
        lines += "accent: ${pr.accent}"
        lines += "liked: ${d.liked.sorted().joinToString(", ")}"
        lines += "week-start: ${pr.weekStart}"
        lines += "default-view: ${pr.defaultView}"
        lines += "workout-reminder: ${pr.workoutReminder}"
        lines += "rest-enabled: ${pr.restEnabled}"
        lines += "rest-between-sets: ${jsNum(pr.restBetweenSets)}"
        lines += "rest-between-exercises: ${jsNum(pr.restBetweenExercises)}"
        lines += "rest-sound: ${pr.restSound}"
        lines += "pill-routine: ${pr.pillRoutine}"
        lines += "pill-category: ${pr.pillCategory}"
        lines += "pill-target: ${pr.pillTarget}"
        lines += "pill-equipment: ${pr.pillEquipment}"
        lines += "pill-tags-host: ${pr.pillTagsHost}"
        lines += "tab-labels: ${pr.tabLabels}"
        lines += "units: ${pr.units.weight}, ${pr.units.distance}, ${pr.units.height}"
        lines += "pill-toggles: ${pr.pillToggles}"
        lines += listOf("", "## Custom Exercises")
        for (e in d.customExercises) {
            lines += "- name: ${e.name.replace("\n", " ").trim()}"
            lines += "  id: ${e.id}"
            lines += "  category: ${e.category}"
            lines += "  target: ${e.target.ifEmpty { e.category }}"
            lines += "  equipment: ${e.equipment}"
            if (e.description.isNotEmpty()) lines += "  description: ${e.description.replace(Regex("\n+"), " / ").trim()}"
        }
        lines += listOf("", "## Exercise Tags")
        val byName = LinkedHashMap<String, Pair<String, LinkedHashSet<String>>>()
        for ((exerciseId, tags) in d.exerciseTags) for (tag in tags) {
            byName.getOrPut(tag.lowercase()) { tag to LinkedHashSet() }.second.add(exerciseId)
        }
        val collator = Collator.getInstance(Locale.ROOT).apply { strength = Collator.PRIMARY }
        for ((label, ids) in byName.values.sortedWith { a, b -> collator.compare(a.first, b.first) }) {
            lines += "- $label"
            val sorted = ids.map(::normalizeTagId).distinct().sortedWith(::compareTagIds)
            if (sorted.isNotEmpty()) lines += "\t- ${sorted.joinToString(", ")}"
        }
        return lines.joinToString("\n") + "\n"
    }

    /** Applies a parsed config.md onto a fresh state (the web app's buildDefaultState + applyConfigToState). */
    fun applyConfig(cfg: ParsedConfig, base: VaultData): VaultData {
        val pp = cfg.profile
        val defaultProfile = Profile()
        var profile = defaultProfile.copy(
            age = pp["age"] as? Double ?: defaultProfile.age,
            sex = pp["sex"] as? String ?: defaultProfile.sex,
            heightCm = pp["heightCm"] as? Double ?: defaultProfile.heightCm,
            currentWeight = pp["currentWeight"] as? Double ?: defaultProfile.currentWeight,
            activity = pp["activity"] as? Double ?: defaultProfile.activity,
            strategy = pp["strategy"] as? Double ?: defaultProfile.strategy,
            proteinRate = pp["proteinRate"] as? Double ?: defaultProfile.proteinRate,
            overrides = cfg.overrides,
        )
        profile = profile.copy(
            startWeight = pp["startWeight"] as? Double ?: defaultProfile.startWeight,
            goalWeight = pp["goalWeight"] as? Double ?: defaultProfile.goalWeight,
        )
        val q = cfg.prefs
        val dp = Prefs()
        val prefs = dp.copy(
            accent = q["accent"] as? String ?: dp.accent,
            weekStart = q["weekStart"] as? Int ?: dp.weekStart,
            defaultView = q["defaultView"] as? String ?: dp.defaultView,
            workoutReminder = q["workoutReminder"] as? Boolean ?: dp.workoutReminder,
            restEnabled = q["restEnabled"] as? Boolean ?: dp.restEnabled,
            restBetweenSets = q["restBetweenSets"] as? Double ?: dp.restBetweenSets,
            restBetweenExercises = q["restBetweenExercises"] as? Double ?: dp.restBetweenExercises,
            restSound = q["restSound"] as? Boolean ?: dp.restSound,
            pillRoutine = q["pillRoutine"] as? String ?: dp.pillRoutine,
            pillCategory = q["pillCategory"] as? String ?: dp.pillCategory,
            pillTarget = q["pillTarget"] as? String ?: dp.pillTarget,
            pillEquipment = q["pillEquipment"] as? String ?: dp.pillEquipment,
            pillTagsHost = q["pillTagsHost"] as? String ?: dp.pillTagsHost,
            pillToggles = q["pillToggles"] as? String ?: dp.pillToggles,
            tabLabels = q["tabLabels"] as? Boolean ?: dp.tabLabels,
            units = q["units"] as? Units ?: dp.units,
        )
        val nameToId = base.routines.associate { it.name to it.id }
        val schedule = if (cfg.scheduleNames.isEmpty()) emptyMap()
        else (0..6).mapNotNull { i -> cfg.scheduleNames[i]?.let { n -> nameToId[n]?.let { i to it } } }.toMap()
        val tags = cfg.exerciseTags.mapValues { (_, v) -> v.take(Limits.TAGS_PER_EXERCISE) }
        return base.copy(profile = profile, prefs = prefs, schedule = schedule, liked = cfg.liked.toSet(), exerciseTags = tags)
    }

    companion object {
        val ACCENTS = listOf("lime", "cyan", "red", "blue", "green", "orange", "purple", "pink")

        fun normalizeTagId(id: String): String {
            val s = id.trim()
            return if (Regex("^\\d+$").matches(s) && s.length < 4) s.padStart(4, '0') else s
        }

        fun compareTagIds(a: String, b: String): Int {
            val an = a.toLongOrNull(); val bn = b.toLongOrNull()
            if (an != null && bn != null && Regex("^\\d+$").matches(a) && Regex("^\\d+$").matches(b)) return an.compareTo(bn)
            return a.compareTo(b)
        }

        fun sanitizeTagName(value: String?): String =
            (value ?: "").replace(Regex("[,|#]"), " ").replace(Regex("\\s+"), " ").trim().take(Limits.TAG_LENGTH)
    }
}
