package com.thehhr.form.data

object Limits {
    const val ROUTINE_NAME = 40
    const val SETS = 20
    const val REPS = 100
    const val TIMED_REPS = 60
    const val WEIGHT = 2000.0
    const val DURATION = 600.0
    const val DISTANCE = 500.0
    const val NOTES = 160
    const val TAGS_PER_EXERCISE = 12
    const val TAG_LENGTH = 24
}

data class Exercise(
    val id: String,
    val name: String,
    val category: String,
    val equipment: String,
    val target: String,
    val muscleGroup: String = "",
    val secondary: List<String> = emptyList(),
    val instructions: List<String> = emptyList(),
    val image: String = "",
    val gif: String = "",
    val custom: Boolean = false,
    val description: String = "",
) {
    /** Cardio that is tracked by time/distance rather than reps (treadmill, bike...). */
    val timedCardio: Boolean
        get() = category == "cardio" && equipment != "body weight" &&
            equipment.trim().lowercase() !in CARDIO_WEIGHTED_EQUIPMENT

    companion object {
        val CARDIO_WEIGHTED_EQUIPMENT = setOf(
            "dumbbell", "barbell", "ez barbell", "smith machine", "kettlebell", "medicine ball",
            "weighted", "sled machine", "band", "resistance band",
        )
        val CATEGORIES = listOf(
            "waist", "upper legs", "back", "lower legs", "chest", "upper arms", "cardio",
            "shoulders", "lower arms", "neck",
        )
    }
}

data class RoutineItem(
    val exerciseId: String,
    val sets: Int = 3,
    val reps: Int = 10,
    val timed: Boolean = false,
    /** "sec" or "min" for timed items; null means the default (sec). */
    val unit: String? = null,
    val unweighted: Boolean = false,
    val distance: Boolean = false,
    val superset: String? = null,
) {
    val unitOrDefault: String get() = if (unit == "min") "min" else "sec"
    val weighted: Boolean get() = !unweighted
}

data class Routine(
    val id: String,
    val name: String,
    val liked: Boolean = false,
    val items: List<RoutineItem> = emptyList(),
)

data class ProgressLog(
    val id: String,
    val exerciseId: String,
    val date: String,
    val sets: Int? = null,
    val reps: Int? = null,
    val weight: Double? = null,
    val setReps: List<Int>? = null,
    val setWeights: List<Double>? = null,
    val intervals: Int? = null,
    val durUnit: String? = null,
    /** Always minutes, whatever the display unit. */
    val setDurations: List<Double>? = null,
    val setDistances: List<Double>? = null,
    val notes: String = "",
    val createdAt: Long = 0,
    val sessionId: String? = null,
) {
    val timed: Boolean get() = (intervals ?: 0) > 0 || setDurations != null || setDistances != null
}

data class Meal(
    val id: String,
    val name: String,
    val defaultGrams: Double = 100.0,
    val p100: Double = 0.0,
    val c100: Double = 0.0,
    val f100: Double = 0.0,
    val cals100: Double = 0.0,
    val liked: Boolean = false,
)

data class MealEntry(
    val id: String,
    val name: String,
    val cals: Double,
    val p: Double,
    val c: Double,
    val f: Double,
    val category: String,
)

data class DayLog(val water: Double = 0.0, val meals: List<MealEntry> = emptyList())

data class Profile(
    val age: Double = 22.0,
    val sex: String = "m",
    /** Inches when units.height == "ftin" (the web app stores display units). */
    val heightCm: Double = 178.0,
    /** Display unit (kg or lb), despite the name. */
    val currentWeight: Double = 75.0,
    val startWeight: Double = 75.0,
    val goalWeight: Double = 75.0,
    val activity: Double = 1.55,
    val strategy: Double = 250.0,
    val proteinRate: Double = 2.0,
    /** Keys: cals, p, c, f, water. */
    val overrides: Map<String, Double> = emptyMap(),
)

data class Units(val weight: String = "kg", val distance: String = "km", val height: String = "cm") {
    companion object {
        fun normalize(weight: String?, distance: String?, height: String?) = Units(
            weight = if (weight in listOf("kg", "lb")) weight!! else "kg",
            distance = if (distance in listOf("km", "mi")) distance!! else "km",
            height = if (height in listOf("cm", "ftin")) height!! else "cm",
        )
    }
}

data class Prefs(
    val accent: String = "lime",
    val weekStart: Int = 1,
    val defaultView: String = "week",
    val workoutReminder: Boolean = true,
    val restEnabled: Boolean = true,
    val restBetweenSets: Double = 60.0,
    val restBetweenExercises: Double = 90.0,
    val restSound: Boolean = true,
    // Pill-row layout of the web exercise grid. Not used natively, but round-tripped so the
    // web app keeps its layout if the user goes back.
    val pillRoutine: String = "pin",
    val pillCategory: String = "default",
    val pillTarget: String = "default",
    val pillEquipment: String = "default",
    val pillTagsHost: String = "equipment",
    val pillToggles: String = "equipment",
    val tabLabels: Boolean = true,
    val units: Units = Units(),
)

/** Everything that lives in the 5 vault files. */
data class VaultData(
    val routines: List<Routine> = emptyList(),
    val logs: List<ProgressLog> = emptyList(),
    val meals: List<Meal> = emptyList(),
    val history: Map<String, DayLog> = emptyMap(),
    val profile: Profile = Profile(),
    /** Day of week (0 = Sunday) -> routine id. */
    val schedule: Map<Int, String> = emptyMap(),
    val liked: Set<String> = emptySet(),
    val prefs: Prefs = Prefs(),
    val customExercises: List<Exercise> = emptyList(),
    /** Exercise id -> tags. */
    val exerciseTags: Map<String, List<String>> = emptyMap(),
)

/** The web app's "skip meal" placeholder: a zero entry named "Skipped" in a slot. */
const val SKIPPED = "Skipped"
val MealEntry.isSkipPlaceholder: Boolean get() = name == SKIPPED && cals == 0.0 && p == 0.0 && c == 0.0 && f == 0.0
