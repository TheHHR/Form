package com.thehhr.form

import com.thehhr.form.data.AiClient
import com.thehhr.form.data.AiException
import com.thehhr.form.data.AiParse
import com.thehhr.form.data.Catalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Model output is untrusted: these guard what can reach the vault. */
class AiParseTest {
    private val catalog = Catalog.parse(File("../../www/data/exercises.js").readText())

    @Test fun mealFromFencedJsonIsClamped() {
        val raw = "```json\n{\"status\":\"ok\",\"name\":\"## Chicken & rice\\nbowl\",\"portionGrams\":450,\"caloriesPer100g\":2000,\"proteinPer100g\":12.34,\"carbsPer100g\":150,\"fatPer100g\":-4}\n```"
        val m = AiParse.meal(raw, "chicken rice")
        assertEquals("Chicken & rice bowl", m.meal.name)
        assertEquals(950.0, m.meal.cals100, 0.0)
        assertEquals(12.3, m.meal.p100, 0.0)
        assertEquals(100.0, m.meal.c100, 0.0)
        assertEquals(0.0, m.meal.f100, 0.0)
        assertEquals(450.0, m.portion, 0.0)
    }

    @Test(expected = AiException::class) fun insufficientMeal() { AiParse.meal("{\"status\":\"insufficient_information\"}", "x") }

    @Test fun routinesDropBadIdsAndDuplicates() {
        val raw = """{"routines":[{"name":"Push","items":[
            {"id":"0025","sets":30,"reps":8},{"id":"0025","sets":3,"reps":8},{"id":"nope","sets":3,"reps":8},
            {"id":"#0405","sets":2,"timed":true,"seconds":45},{"id":"2141","sets":1,"timed":true,"seconds":1500}]},
            {"name":"Empty","items":[{"id":"bad"}]}]}"""
        val r = AiParse.routines(raw, catalog)
        assertEquals(1, r.size)
        val items = r.single().items
        assertEquals(listOf("0025", "0405", "2141"), items.map { it.exerciseId })
        assertEquals(20, items[0].sets) // clamped
        assertTrue(items[1].timed && items[1].unit == null && items[1].reps == 45)
        assertEquals("min", items[2].unit) // 1500 s does not fit the 60 cap in seconds
        assertEquals(25, items[2].reps)
    }

    @Test fun extractsOpenAiAndGeminiText() {
        assertEquals("hi", AiClient.extract("""{"choices":[{"message":{"content":"hi"}}]}""", gemini = false))
        assertEquals("ab", AiClient.extract("""{"candidates":[{"content":{"parts":[{"text":"a"},{"text":"b"}]}}]}""", gemini = true))
        assertEquals("", AiClient.extract("not json", gemini = false))
    }

    @Test fun catalogSubsetIsBounded() {
        val lines = AiParse.catalogFor("dumbbell chest", catalog, emptySet()).lines()
        assertTrue(lines.size <= 300)
        assertFalse(lines.any { !it.contains("|") })
    }
}
