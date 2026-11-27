package com.thehhr.form

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import com.thehhr.form.data.ActiveWorkout
import com.thehhr.form.data.MealEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Optional Health Connect sync (off by default): writes finished workouts and logged meals, reads the
 * latest body weight. Records carry our ids as clientRecordId, so re-writes update instead of duplicating.
 * shortcut: deleting a training log in Form doesn't remove the session from Health Connect; add if testers ask.
 */
object HealthSync {
    val permissions = setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
    )
    /** Read-only, for filling the profile (onboarding / Settings import). */
    val profilePermissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class),
    )

    fun available(ctx: Context) = HealthConnectClient.getSdkStatus(ctx) == HealthConnectClient.SDK_AVAILABLE
    fun permissionContract() = PermissionController.createRequestPermissionResultContract()
    private fun client(ctx: Context) = HealthConnectClient.getOrCreate(ctx)

    suspend fun granted(ctx: Context): Set<String> =
        if (!available(ctx)) emptySet() else runCatching { client(ctx).permissionController.getGrantedPermissions() }.getOrDefault(emptySet())

    private fun run(store: FormStore, block: suspend (HealthConnectClient) -> Unit) {
        if (!store.device.healthEnabled || !available(store.app)) return
        store.launch { withContext(Dispatchers.IO) { runCatching { block(client(store.app)) } } }
    }

    fun writeWorkout(store: FormStore, w: ActiveWorkout, end: Long) = run(store) { c ->
        val start = Instant.ofEpochMilli(w.startedAt)
        val stop = Instant.ofEpochMilli(maxOf(end, w.startedAt + 60_000))
        c.insertRecords(listOf(ExerciseSessionRecord(
            startTime = start, startZoneOffset = null, endTime = stop, endZoneOffset = null,
            metadata = Metadata.manualEntry(clientRecordId = "form-session-${w.token}"),
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            title = w.routineName,
        )))
    }

    /** Diary entries have no time of day: today's use "now", past days a fixed time per meal slot. */
    fun mealTime(date: String, category: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Instant {
        val d = LocalDate.parse(date)
        if (d == LocalDate.now(zone)) return now
        val t = when (category) { "Breakfast" -> LocalTime.of(8, 0); "Lunch" -> LocalTime.of(13, 0); "Dinner" -> LocalTime.of(19, 0); else -> LocalTime.of(16, 0) }
        return d.atTime(t).atZone(zone).toInstant()
    }

    fun writeMeal(store: FormStore, date: String, m: MealEntry) = run(store) { c ->
        val start = mealTime(date, m.category)
        c.insertRecords(listOf(NutritionRecord(
            startTime = start, startZoneOffset = null, endTime = start.plusSeconds(60), endZoneOffset = null,
            metadata = Metadata.manualEntry(clientRecordId = "form-meal-${m.id}"),
            name = m.name,
            energy = Energy.kilocalories(m.cals.coerceIn(0.0, 100000.0)),
            protein = Mass.grams(m.p.coerceIn(0.0, 100000.0)),
            totalCarbohydrate = Mass.grams(m.c.coerceIn(0.0, 100000.0)),
            totalFat = Mass.grams(m.f.coerceIn(0.0, 100000.0)),
            mealType = when (m.category) {
                "Breakfast" -> MealType.MEAL_TYPE_BREAKFAST; "Lunch" -> MealType.MEAL_TYPE_LUNCH
                "Dinner" -> MealType.MEAL_TYPE_DINNER; "Snacks" -> MealType.MEAL_TYPE_SNACK; else -> MealType.MEAL_TYPE_UNKNOWN
            },
        )))
    }

    fun deleteMeal(store: FormStore, id: String) = run(store) { c ->
        c.deleteRecords(NutritionRecord::class, recordIdsList = emptyList(), clientRecordIdsList = listOf("form-meal-$id"))
    }

    /** Latest body weight in kg, or null. */
    suspend fun latestWeightKg(ctx: Context): Double? = withContext(Dispatchers.IO) {
        runCatching {
            client(ctx).readRecords(ReadRecordsRequest(WeightRecord::class, TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1))
                .records.firstOrNull()?.weight?.inKilograms
        }.getOrNull()
    }

    /** Latest height in metres, or null. */
    suspend fun latestHeightM(ctx: Context): Double? = withContext(Dispatchers.IO) {
        runCatching {
            client(ctx).readRecords(ReadRecordsRequest(HeightRecord::class, TimeRangeFilter.before(Instant.now()), ascendingOrder = false, pageSize = 1))
                .records.firstOrNull()?.height?.inMeters
        }.getOrNull()
    }

    /**
     * Fills current weight and height from Health Connect, only where permission was granted and a value exists.
     * Overwrites the two profile fields in place (no new records), in the profile's display units. Returns what was set.
     */
    suspend fun importProfile(store: FormStore): String {
        val ctx = store.app
        if (!available(ctx)) return "Health Connect isn't available"
        val granted = granted(ctx)
        val kg = if (HealthPermission.getReadPermission(WeightRecord::class) in granted) latestWeightKg(ctx)?.takeIf { it in 20.0..400.0 } else null
        val m = if (HealthPermission.getReadPermission(HeightRecord::class) in granted) latestHeightM(ctx)?.takeIf { it in 0.9..2.6 } else null
        if (kg == null && m == null) return if (granted.none { it in profilePermissions }) "Health Connect access not granted" else "No weight or height found in Health Connect"
        val units = store.data.prefs.units
        var p = store.data.profile
        val parts = mutableListOf<String>()
        if (kg != null) {
            val w = Math.round((if (units.weight == "lb") kg * com.thehhr.form.data.Fuel.LB_PER_KG else kg) * 10) / 10.0
            p = p.copy(currentWeight = w); parts += "weight ${com.thehhr.form.ui.num(w)} ${units.weight}"
        }
        if (m != null) {
            // heightCm holds inches when the height unit is ft/in (the web app stores display units).
            val h = if (units.height == "ftin") Math.round(m * 100 / 2.54 * 10) / 10.0 else Math.round(m * 1000) / 10.0
            p = p.copy(heightCm = h); parts += if (units.height == "ftin") "height ${(h / 12).toInt()}′${Math.round(h % 12)}″" else "height ${com.thehhr.form.ui.num(h)} cm"
        }
        store.setProfile(p)
        return "Imported " + parts.joinToString(" and ") + " from Health Connect"
    }
}
