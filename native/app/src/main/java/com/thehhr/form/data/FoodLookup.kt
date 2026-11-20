package com.thehhr.form.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Barcode -> food via Open Food Facts (public, no key). Responses are untrusted: everything is clamped. */
object FoodLookup {
    /** Meal plus a serving size suggestion (grams). */
    data class Found(val meal: Meal, val servingGrams: Double)

    fun validBarcode(code: String) = Regex("^\\d{8,14}$").matches(code.trim())

    /** Blocking; call off the main thread. Null when unknown or offline. */
    fun lookup(code: String): Found? {
        if (!validBarcode(code)) return null
        val url = URL("https://world.openfoodfacts.org/api/v2/product/${code.trim()}.json?fields=product_name,brands,nutriments,serving_quantity")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            // Generic client name only (no personal contact details), per OFF's request to identify apps.
            conn.setRequestProperty("User-Agent", "Form/4.0 (Android)")
            if (conn.responseCode != 200) return null
            parse(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }, code.trim())
        } catch (_: Exception) { null } finally { conn.disconnect() }
    }

    /** Pure mapping, unit-tested. */
    fun parse(json: String, code: String): Found? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optInt("status", 0) != 1) return null
        val p = root.optJSONObject("product") ?: return null
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        fun num(key: String) = n.optDouble(key, Double.NaN).takeIf { it.isFinite() && it >= 0 }
        val prot = (num("proteins_100g") ?: 0.0).coerceAtMost(100.0)
        val carb = (num("carbohydrates_100g") ?: 0.0).coerceAtMost(100.0)
        val fat = (num("fat_100g") ?: 0.0).coerceAtMost(100.0)
        // energy_100g is kJ when the kcal field is missing.
        val kcal = (num("energy-kcal_100g") ?: num("energy_100g")?.let { it / 4.184 } ?: Fuel.kcal(prot, carb, fat)).coerceAtMost(999.0)
        val brand = p.optString("brands").split(",").firstOrNull()?.trim().orEmpty()
        val rawName = p.optString("product_name").trim().ifEmpty { "Barcode $code" }
        val name = sanitizeVaultName(if (brand.isNotEmpty() && !rawName.contains(brand, true)) "$rawName, $brand" else rawName)
            .take(Limits.ROUTINE_NAME).ifEmpty { "Barcode $code" }
        val serving = p.optDouble("serving_quantity", Double.NaN).takeIf { it.isFinite() && it in 1.0..2000.0 } ?: 100.0
        return Found(
            Meal(id = "bc-$code", name = name, defaultGrams = round1(serving), p100 = round1(prot), c100 = round1(carb), f100 = round1(fat),
                cals100 = jsRound(kcal).toDouble()),
            round1(serving),
        )
    }
}
