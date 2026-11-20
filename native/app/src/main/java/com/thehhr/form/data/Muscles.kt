package com.thehhr.form.data

/** Exercise target/secondary names -> MuscleMapJS slugs. Ported from www/app.js (TARGET_TO_MUSCLE, SECONDARY_MUSCLE_TO_MAP). */
object Muscles {
    val TARGET = mapOf(
        "abs" to "abs", "quads" to "quadriceps", "lats" to "upper-back", "calves" to "calves", "pectorals" to "chest",
        "glutes" to "gluteal", "hamstrings" to "hamstring", "adductors" to "adductors", "triceps" to "triceps",
        "spine" to "lower-back", "upper back" to "upper-back", "biceps" to "biceps", "delts" to "deltoids",
        "forearms" to "forearm", "traps" to "trapezius", "serratus anterior" to "obliques", "abductors" to "gluteal",
        "levator scapulae" to "neck",
    )
    val SECONDARY = mapOf(
        "shoulders" to "deltoids", "rear deltoids" to "deltoids", "deltoids" to "deltoids", "rotator cuff" to "rotator-cuff",
        "trapezius" to "trapezius", "traps" to "trapezius", "rhomboids" to "rhomboids", "upper back" to "upper-back",
        "back" to "upper-back", "latissimus dorsi" to "upper-back", "lats" to "upper-back", "chest" to "chest",
        "upper chest" to "chest", "biceps" to "biceps", "brachialis" to "biceps", "triceps" to "triceps", "forearms" to "forearm",
        "wrist flexors" to "forearm", "wrist extensors" to "forearm", "wrists" to "forearm", "grip muscles" to "forearm",
        "hands" to "forearm", "core" to "abs", "abdominals" to "abs", "lower abs" to "abs", "obliques" to "obliques",
        "hip flexors" to "quadriceps", "groin" to "adductors", "inner thighs" to "adductors", "quadriceps" to "quadriceps",
        "hamstrings" to "hamstring", "glutes" to "gluteal", "calves" to "calves", "soleus" to "calves", "shins" to "tibialis",
        "ankles" to "ankles", "ankle stabilizers" to "ankles", "feet" to "feet", "sternocleidomastoid" to "neck",
        "lower back" to "lower-back",
    )
    /** Slugs the web mapping uses that the body data has no paths for. */
    private val NO_PATHS = mapOf("rotator-cuff" to "deltoids", "rhomboids" to "upper-back")

    /** Sub-groups that overlay their parent; drawn only when highlighted themselves. */
    val HIDDEN_SUBGROUPS = setOf(
        "upper-chest", "lower-chest", "inner-quad", "outer-quad", "hip-flexors", "upper-abs", "lower-abs",
        "front-deltoid", "rear-deltoid", "upper-trapezius", "lower-trapezius", "serratus",
    )

    fun slug(raw: String?): String? = raw?.let { NO_PATHS[it] ?: it }

    /** Slug -> intensity (1 = target, 0.45 = secondary) for one exercise. */
    fun forExercise(e: Exercise): Map<String, Float> {
        val out = linkedMapOf<String, Float>()
        e.secondary.mapNotNull { slug(SECONDARY[it.trim().lowercase()]) }.forEach { out[it] = 0.45f }
        slug(TARGET[e.target.trim().lowercase()])?.let { out[it] = 1f }
        return out
    }

    /** Slug -> 0..1 load from logs (target counts full sets, secondary half), normalised to the busiest muscle. */
    fun heat(logs: List<ProgressLog>, exercise: (String) -> Exercise?): Map<String, Float> {
        val load = HashMap<String, Double>()
        for (log in logs) {
            val e = exercise(log.exerciseId) ?: continue
            val sets = (if (log.timed) log.intervals else log.sets)?.toDouble() ?: 1.0
            forExercise(e).forEach { (m, w) -> load[m] = (load[m] ?: 0.0) + sets * (if (w >= 1f) 1.0 else 0.5) }
        }
        val max = load.values.maxOrNull()?.takeIf { it > 0 } ?: return emptyMap()
        return load.mapValues { (_, v) -> (v / max).toFloat().coerceIn(0.15f, 1f) }
    }
}
