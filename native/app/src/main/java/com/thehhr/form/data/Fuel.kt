package com.thehhr.form.data

import kotlin.math.max

data class Targets(val cals: Double, val p: Double, val c: Double, val f: Double, val water: Double) {
    operator fun get(key: String) = when (key) { "cals" -> cals; "p" -> p; "c" -> c; "f" -> f; else -> water }
}

/** Ported from www/app.js calculatedFuelTargets / effectiveFuelTargets. */
object Fuel {
    const val LB_PER_KG = 2.20462
    const val CM_PER_IN = 2.54
    val KEYS = listOf("cals", "p", "c", "f", "water")
    val LIMITS = mapOf("cals" to 500.0..10000.0, "p" to 0.0..1000.0, "c" to 0.0..1000.0, "f" to 0.0..1000.0, "water" to 500.0..10000.0)
    val SLOTS = listOf("Breakfast", "Lunch", "Dinner", "Snacks")

    fun weightKg(p: Profile, u: Units) = if (u.weight == "lb") p.currentWeight / LB_PER_KG else p.currentWeight

    fun calculated(p: Profile, u: Units): Targets {
        val kg = weightKg(p, u)
        val cm = if (u.height == "ftin") p.heightCm * CM_PER_IN else p.heightCm
        val bmr = 10 * kg + 6.25 * cm - 5 * p.age + if (p.sex == "m") 5 else -161
        val tdee = jsRound(bmr * p.activity)
        val target = max(1200, jsRound(tdee + p.strategy).toInt())
        val pg = jsRound(kg * p.proteinRate)
        val fg = jsRound(target * 0.25 / 9)
        val cg = jsRound(max(0.0, target - (pg * 4.0 + fg * 9.0)) / 4)
        val cals = pg * 4 + cg * 4 + fg * 9
        val water = jsRound(max(2000.0, kg * if (p.activity >= 1.5) 45 else 35))
        return Targets(cals.toDouble(), pg.toDouble(), cg.toDouble(), fg.toDouble(), water.toDouble())
    }

    fun effective(p: Profile, u: Units): Targets {
        val c = calculated(p, u)
        fun pick(k: String) = p.overrides[k]?.coerceIn(LIMITS[k]!!) ?: c[k]
        return Targets(pick("cals"), pick("p"), pick("c"), pick("f"), pick("water"))
    }

    fun kcal(p: Double, c: Double, f: Double) = jsRound(p * 4 + c * 4 + f * 9).toDouble()

    fun entryFor(meal: Meal, grams: Double, slot: String, id: String) = MealEntry(
        id = id, name = meal.name.take(Limits.ROUTINE_NAME),
        cals = jsRound(meal.cals100 * grams / 100).toDouble(),
        p = round1(meal.p100 * grams / 100), c = round1(meal.c100 * grams / 100), f = round1(meal.f100 * grams / 100),
        category = slot,
    )
}
