package com.thehhr.form

import com.thehhr.form.data.Catalog
import com.thehhr.form.data.Muscles
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every muscle the mapping can produce must exist in the body data, or highlights silently vanish. */
class MusclesTest {
    private val json = JSONObject(File("src/main/assets/musclemap.json").readText())

    private fun slugs(gender: String): Set<String> = listOf("front", "back").flatMap { side ->
        val parts = json.getJSONObject("$gender-$side").getJSONArray("parts")
        (0 until parts.length()).map { parts.getJSONObject(it).getString("slug") }
    }.toSet()

    @Test fun everyMappedMuscleHasPaths() {
        val mapped = (Muscles.TARGET.values + Muscles.SECONDARY.values).mapNotNull(Muscles::slug).toSet()
        for (g in listOf("male", "female")) {
            val missing = mapped - slugs(g)
            assertTrue("$g missing $missing", missing.isEmpty())
        }
    }

    @Test fun catalogTargetsAllMap() {
        val catalog = Catalog.parse(File("../../www/data/exercises.js").readText())
        // Cardio machines target the "cardiovascular system": no muscle to light up (same as the web app).
        val unmapped = catalog.builtIn.map { it.target.lowercase() }.distinct().filter { it !in Muscles.TARGET && it != "cardiovascular system" }
        assertTrue("targets without a muscle: $unmapped", unmapped.isEmpty())
        val e = catalog.builtIn.first { it.id == "0025" } // barbell bench press
        assertTrue(Muscles.forExercise(e)["chest"] == 1f)
    }
}
