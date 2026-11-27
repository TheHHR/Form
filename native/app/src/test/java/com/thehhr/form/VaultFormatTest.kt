package com.thehhr.form

import com.thehhr.form.data.Catalog
import com.thehhr.form.data.VaultData
import com.thehhr.form.data.VaultFormat
import com.thehhr.form.data.jsNum
import com.thehhr.form.data.jsTitle
import com.thehhr.form.data.jsonQuote
import com.thehhr.form.data.vaultDateHeading
import com.thehhr.form.data.parseVaultDateHeading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The native writers must produce exactly what the web app (www/app.js) produces.
 * Expected files in vault-web-out/ come from `node tools/web-vault-roundtrip.js` run on vault/.
 */
class VaultFormatTest {
    private val catalog = Catalog.parse(File("../../www/data/exercises.js").readText())
    private val format = VaultFormat(catalog)
    private fun fixture(name: String) = javaClass.getResource("/vault/$name.md")!!.readText()
    private fun web(name: String) = javaClass.getResource("/vault-web-out/$name.md")!!.readText()

    private fun load(): VaultData {
        val cfg = format.parseConfig(fixture("config"))
        val customs = catalog.registerCustoms(cfg.customExercises)
        val base = VaultData(
            routines = format.parseRoutines(fixture("routines")),
            customExercises = customs,
        )
        return format.applyConfig(cfg, base)
    }

    @Test fun routinesMatchWeb() {
        assertEquals(web("routines"), format.writeRoutines(load().routines))
    }

    @Test fun mealsMatchWeb() {
        assertEquals(web("meals"), format.writeMeals(format.parseMeals(fixture("meals"))))
    }

    @Test fun trainingLogsMatchWeb() {
        val d = load()
        assertEquals(web("training_logs"), format.writeTrainingLogs(format.parseTrainingLogs(fixture("training_logs")), d.prefs.units))
    }

    @Test fun diaryMatchesWeb() {
        assertEquals(web("nutrition_diary"), format.writeNutritionDiary(format.parseNutritionDiary(fixture("nutrition_diary"))))
    }

    @Test fun configMatchesWeb() {
        assertEquals(web("config"), format.writeConfig(load()))
    }

    @Test fun writersAreStable() {
        val d = load()
        val routines = format.writeRoutines(d.routines)
        assertEquals(routines, format.writeRoutines(format.parseRoutines(routines)))
        val logs = format.writeTrainingLogs(format.parseTrainingLogs(fixture("training_logs")), d.prefs.units)
        assertEquals(logs, format.writeTrainingLogs(format.parseTrainingLogs(logs), d.prefs.units))
        val diary = format.writeNutritionDiary(format.parseNutritionDiary(fixture("nutrition_diary")))
        assertEquals(diary, format.writeNutritionDiary(format.parseNutritionDiary(diary)))
    }

    // ---- deliberate fixes over the web app

    @Test fun mealsWithoutIdGetDistinctIds() {
        val text = "## Mon, Jan 5, 2026\n- A 10 kcal · **1p · 1c · 1f**\n- B 20 kcal · **2p · 2c · 2f**\n"
        val meals = format.parseNutritionDiary(text).values.single().meals
        assertEquals(2, meals.size)
        assertNotEquals(meals[0].id, meals[1].id)
        assertTrue(meals.none { it.id == "0" })
    }

    @Test fun unknownDiaryCategoryIsKept() {
        val text = "## Mon, Jan 5, 2026\n### Pre-workout\n- Banana 100 kcal · **1p · 25c · 0f**\n\t- id: 5\n"
        val out = format.writeNutritionDiary(format.parseNutritionDiary(text))
        assertTrue(out, out.contains("### Pre-Workout\n- Banana 100 kcal"))
    }

    @Test fun routineRepsAbove60Survive() {
        val text = "## R\n- id: r1\n0025 3 * 80\n"
        assertEquals(80, format.parseRoutines(text).single().items.single().reps)
    }

    @Test fun timedLogWeightsSurvive() {
        val text = "## Mon, Jan 5, 2026\n- exercise: \"x\"\n\t- exerciseId: #0405\n\t- int: 2\n\t- dur(sec): 30, 40\n\t- weight(kg): 10, 12.5\n\t- id: 1767600000001\n"
        val log = format.parseTrainingLogs(text).single()
        assertEquals(listOf(10.0, 12.5), log.setWeights)
        assertTrue(format.writeTrainingLogs(listOf(log), com.thehhr.form.data.Units()).contains("weight(kg): 10, 12.5"))
    }

    @Test fun customIdRegexIsAnchored() {
        val e = catalog.normalizeCustom(mapOf("name" to "X", "category" to "back", "id" to "c-1\" onfocus=\"x"))!!
        assertTrue(e.id.matches(Regex("^\\d{5}$")))
    }

    // ---- JS formatting helpers

    @Test fun jsHelpers() {
        assertEquals("75", jsNum(75.0))
        assertEquals("62.5", jsNum(62.5))
        assertEquals("0.0005", jsNum(0.0005))
        assertEquals("-300", jsNum(-300.0))
        assertEquals("Barbell Bench Press", jsTitle("barbell bench press"))
        assertEquals("3/4 Sit-Up", jsTitle("3/4 sit-up"))
        assertEquals("\"a \\\"b\\\"\"", jsonQuote("a \"b\""))
        assertEquals("Mon, Jan 5, 2026", vaultDateHeading("2026-01-05"))
        assertEquals("2026-01-05", parseVaultDateHeading("Mon, Jan 5, 2026"))
    }

    /** Importing exported routines again (supersets renumbered to ss11, ss12 on export) adds nothing; a changed one is new. */
    @Test fun reimportAddsNoDuplicates() {
        val existing = format.parseRoutines(fixture("routines"))
        val exported = format.parseRoutines(format.writeRoutines(existing))
        assertEquals(0, format.newRoutines(existing, exported).size)
        val changed = exported.first().let { it.copy(items = it.items.map { i -> i.copy(sets = i.sets + 1) }) }
        assertEquals(1, format.newRoutines(existing, listOf(changed)).size)
    }
}
