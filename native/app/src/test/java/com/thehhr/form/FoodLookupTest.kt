package com.thehhr.form

import com.thehhr.form.data.FoodLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FoodLookupTest {
    @Test fun mapsOpenFoodFactsProduct() {
        val json = """{"status":1,"product":{"product_name":"Greek Yogurt 2%","brands":"Fage, Total",
            "serving_quantity":170,"nutriments":{"energy-kcal_100g":73,"proteins_100g":10.3,"carbohydrates_100g":3.94,"fat_100g":2}}}"""
        val f = FoodLookup.parse(json, "5201054017036")!!
        assertEquals("Greek Yogurt 2%, Fage", f.meal.name)
        assertEquals(73.0, f.meal.cals100, 0.0)
        assertEquals(10.3, f.meal.p100, 0.0)
        assertEquals(3.9, f.meal.c100, 0.0)
        assertEquals(170.0, f.servingGrams, 0.0)
        assertEquals("bc-5201054017036", f.meal.id)
    }

    @Test fun kilojoulesFallbackAndClamps() {
        val json = """{"status":1,"product":{"product_name":"## Weird\nName","nutriments":{"energy_100g":418.4,"proteins_100g":250,"fat_100g":-3}}}"""
        val f = FoodLookup.parse(json, "12345678")!!
        assertEquals(100.0, f.meal.cals100, 0.0)
        assertEquals(100.0, f.meal.p100, 0.0) // clamped
        assertEquals(0.0, f.meal.f100, 0.0)
        assertFalse(f.meal.name.startsWith("#")) // vault-safe
        assertEquals(100.0, f.servingGrams, 0.0)
    }

    @Test fun unknownProduct() = assertNull(FoodLookup.parse("""{"status":0}""", "12345678"))
    @Test fun rejectsNonBarcodes() = assertFalse(FoodLookup.validBarcode("12ab"))
}
