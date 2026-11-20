package com.thehhr.form

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.WeightRecord
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
}
