package com.thehhr.form.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Bring-your-own-key AI settings. Device-only (never in the vault), as in the web app. */
data class AiConfig(
    val provider: String = "",
    val keys: Map<String, String> = emptyMap(),
    val models: Map<String, String> = emptyMap(),
    val customBaseUrl: String = "",
) {
    val key: String get() = keys[provider].orEmpty().trim()
    val model: String get() = models[provider].orEmpty().trim()
    val configured: Boolean get() = when (provider) {
        "" -> false
        "custom" -> customBaseUrl.isNotBlank() && model.isNotEmpty()
        else -> key.isNotEmpty() && model.isNotEmpty()
    }

    fun toJson() = JSONObject().put("provider", provider).put("keys", JSONObject(keys as Map<*, *>))
        .put("models", JSONObject(models as Map<*, *>)).put("customBaseUrl", customBaseUrl).toString()

    companion object {
        val PROVIDERS = linkedMapOf(
            "openrouter" to ("OpenRouter" to "https://openrouter.ai/api/v1/chat/completions"),
            "openai" to ("OpenAI" to "https://api.openai.com/v1/chat/completions"),
            "gemini" to ("Gemini" to "https://generativelanguage.googleapis.com/v1beta/models"),
            "deepseek" to ("DeepSeek" to "https://api.deepseek.com/chat/completions"),
            "mistral" to ("Mistral" to "https://api.mistral.ai/v1/chat/completions"),
            "xai" to ("xAI" to "https://api.x.ai/v1/chat/completions"),
            "opencode" to ("OpenCode" to "https://opencode.ai/zen/v1/chat/completions"),
            "custom" to ("Custom" to ""),
        )

        fun fromJson(s: String?): AiConfig = runCatching {
            val o = JSONObject(s ?: return AiConfig())
            fun map(name: String) = o.optJSONObject(name)?.let { j -> j.keys().asSequence().associateWith { j.optString(it) } } ?: emptyMap()
            AiConfig(o.optString("provider").takeIf { it in PROVIDERS }.orEmpty(), map("keys"), map("models"), o.optString("customBaseUrl"))
        }.getOrDefault(AiConfig())
    }
}

class AiException(message: String) : Exception(message)

/** One non-streaming chat call. Blocking: run on IO. The key is only ever sent to the chosen provider. */
object AiClient {
    private fun errorFor(status: Int) = when {
        status == 401 || status == 403 -> "Invalid or unauthorized API key"
        status == 402 -> "Insufficient credits for this model"
        status == 404 -> "Model or endpoint not found — check the model name"
        status == 429 -> "Rate limited — try again later or pick another model"
        status >= 500 -> "Provider server error — try again later"
        else -> "Request failed (HTTP $status)"
    }

    fun complete(cfg: AiConfig, system: String, prompt: String, maxTokens: Int, timeoutMs: Int, image: ByteArray? = null): String {
        val b64 = image?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) }
        val geminiParts = JSONArray().put(JSONObject().put("text", prompt)).apply {
            if (b64 != null) put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", b64)))
        }
        // OpenAI-compatible multimodal content: text part plus a data-URI image part.
        val userContent: Any = if (b64 == null) prompt else JSONArray().put(JSONObject().put("type", "text").put("text", prompt))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,$b64")))
        if (!cfg.configured) throw AiException("Set up a provider in the AI tab first")
        val gemini = cfg.provider == "gemini"
        val url = when (cfg.provider) {
            "gemini" -> "${AiConfig.PROVIDERS.getValue("gemini").second}/${java.net.URLEncoder.encode(cfg.model, "UTF-8")}:generateContent"
            "custom" -> cfg.customBaseUrl.trim().trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" }
            else -> AiConfig.PROVIDERS.getValue(cfg.provider).second
        }
        if (!url.startsWith("https://")) throw AiException("The provider URL must start with https://")
        val body = if (gemini) JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", geminiParts)))
            .put("generationConfig", JSONObject().put("temperature", 0.5).put("maxOutputTokens", maxTokens))
        else JSONObject().put("model", cfg.model)
            .put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", system)).put(JSONObject().put("role", "user").put("content", userContent)))
            .put("temperature", 0.5)
            .put(if (cfg.provider == "openai") "max_completion_tokens" else "max_tokens", maxTokens)
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = timeoutMs
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            if (gemini) conn.setRequestProperty("x-goog-api-key", cfg.key)
            else if (cfg.key.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer ${cfg.key}")
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val status = conn.responseCode
            val text = (if (status in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status !in 200..299) {
                val detail = runCatching {
                    val j = JSONObject(text)
                    (j.optJSONObject("error")?.optString("message") ?: j.optString("message")).trim().take(200)
                }.getOrNull().orEmpty()
                throw AiException(if (detail.isNotEmpty()) "${errorFor(status)} — $detail" else errorFor(status))
            }
            return extract(text, gemini).trim().ifEmpty { throw AiException("The model returned an empty response — try another model") }
        } catch (e: java.net.SocketTimeoutException) {
            throw AiException("The model took too long — try again or pick a faster model")
        } catch (e: java.io.IOException) {
            throw AiException("Network error — check your connection")
        } finally { conn.disconnect() }
    }

    private fun parts(v: Any?): String = when (v) {
        is String -> v
        is JSONArray -> (0 until v.length()).joinToString("") { i -> v.optJSONObject(i)?.optString("text").orEmpty() }
        else -> ""
    }

    fun extract(json: String, gemini: Boolean): String = runCatching {
        val o = JSONObject(json)
        if (gemini) parts(o.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").opt("parts"))
        else parts(o.getJSONArray("choices").getJSONObject(0).getJSONObject("message").opt("content"))
    }.getOrDefault("")
}

/** Validates model output (untrusted): everything is clamped, ids checked against the catalog. */
object AiParse {
    private fun jsonObject(raw: String, what: String): JSONObject {
        val t = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val s = t.indexOf('{'); val e = t.lastIndexOf('}')
        if (s < 0 || e <= s) throw AiException("The model returned an invalid $what")
        return runCatching { JSONObject(t.substring(s, e + 1)) }.getOrElse { throw AiException("The model returned an invalid $what") }
    }

    private fun num(o: JSONObject, vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { k -> o.optDouble(k, Double.NaN).takeIf { it.isFinite() } }

    data class MealEstimate(val meal: Meal, val portion: Double, val notes: List<String>)

    fun meal(raw: String, description: String): MealEstimate {
        val v = jsonObject(raw, "estimate")
        if (v.optString("status").trim().lowercase() == "insufficient_information")
            throw AiException("Not enough detail — add ingredients and portions")
        val t = v.optJSONObject("totals") ?: v
        val p = num(t, "proteinPer100g", "proteinG", "protein")?.coerceIn(0.0, 100.0)
        val c = num(t, "carbsPer100g", "carbsG", "carbs")?.coerceIn(0.0, 100.0)
        val f = num(t, "fatPer100g", "fatG", "fat")?.coerceIn(0.0, 100.0)
        if (p == null || c == null || f == null) throw AiException("The model did not return complete macros")
        val kcal = (num(t, "caloriesPer100g", "calories")?.coerceIn(0.0, 950.0) ?: Fuel.kcal(p, c, f))
        val name = sanitizeVaultName(v.optString("name").ifBlank { description }).take(Limits.ROUTINE_NAME).ifEmpty { "Meal" }
        val portion = num(v, "portionGrams")?.takeIf { it in 1.0..5000.0 } ?: num(t, "portionGrams")?.takeIf { it in 1.0..5000.0 }
        val notes = (v.optJSONArray("assumptions")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty() +
            v.optJSONArray("warnings")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty())
            .map { it.trim().take(240) }.filter { it.isNotEmpty() }.take(4) +
            listOfNotNull(if (portion == null) "Portion weight was not provided; using 100 g" else null)
        return MealEstimate(
            Meal(id = "ai-${System.currentTimeMillis()}", name = name, defaultGrams = round1(portion ?: 100.0),
                p100 = round1(p), c100 = round1(c), f100 = round1(f), cals100 = jsRound(kcal).toDouble()),
            round1(portion ?: 100.0), notes,
        )
    }

    /** Routines from the model; seconds above 60 become minute units (the vault caps timed values at 60). */
    fun routines(raw: String, catalog: Catalog, stamp: Long = System.currentTimeMillis()): List<Routine> {
        val arr = jsonObject(raw, "routine plan").optJSONArray("routines") ?: throw AiException("The model returned no routines")
        val out = mutableListOf<Routine>()
        for (i in 0 until minOf(arr.length(), 6)) {
            val r = arr.optJSONObject(i) ?: continue
            val items = r.optJSONArray("items") ?: continue
            val seen = mutableSetOf<String>()
            val list = (0 until items.length()).mapNotNull { j ->
                val it = items.optJSONObject(j) ?: return@mapNotNull null
                val id = it.optString("id").trim().removePrefix("#")
                if (!catalog.isValid(id) || !seen.add(id)) return@mapNotNull null
                val sets = it.optDouble("sets", 3.0).let { s -> if (s.isFinite()) jsRound(s).toInt() else 3 }.coerceIn(1, Limits.SETS)
                if (it.optBoolean("timed")) {
                    val secs = it.optDouble("seconds", 30.0).let { s -> if (s.isFinite()) s else 30.0 }.coerceIn(5.0, 3600.0)
                    if (secs <= 60) RoutineItem(id, sets, jsRound(secs).toInt(), timed = true)
                    else RoutineItem(id, sets, jsRound(secs / 60).toInt().coerceIn(1, Limits.TIMED_REPS), timed = true, unit = "min")
                } else {
                    val reps = it.optDouble("reps", 10.0).let { x -> if (x.isFinite()) jsRound(x).toInt() else 10 }.coerceIn(1, Limits.TIMED_REPS)
                    RoutineItem(id, sets, reps)
                }
            }.take(10)
            if (list.isEmpty()) continue
            val name = sanitizeVaultName(r.optString("name")).take(Limits.ROUTINE_NAME).ifEmpty { "AI routine ${out.size + 1}" }
            out += Routine(id = "r-$stamp-$i", name = name, items = list)
        }
        if (out.isEmpty()) throw AiException("The model returned no usable routines — try rephrasing")
        return out
    }

    /** Up to [max] catalog entries for the routine prompt, focused on the request's words when possible. */
    fun catalogFor(request: String, catalog: Catalog, preferred: Set<String>, max: Int = 300): String {
        val tokens = request.lowercase().split(Regex("[^a-z]+")).filter { it.length >= 3 }
        val all = catalog.all
        fun hay(e: Exercise) = "${e.name} ${e.target} ${e.category} ${e.equipment}".lowercase()
        val focused = all.filter { e -> tokens.any { t -> hay(e).contains(t.removeSuffix("s")) } }
        val pool = (if (focused.size >= 40) focused else all).sortedByDescending { it.id in preferred }
        return pool.take(max).joinToString("\n") { "${it.id}|${it.name}|${it.target}|${it.equipment}" }
    }

    /** Compact per-exercise summary of a period (aiCompactExercises in the web app). */
    fun summary(logs: List<ProgressLog>, exercise: (String) -> Exercise?, units: Units): JSONArray {
        val rows = LinkedHashMap<String, JSONObject>()
        for (log in logs) {
            val e = exercise(log.exerciseId)
            val name = e?.name ?: "Unknown exercise"
            val o = rows.getOrPut(name) { JSONObject().put("name", name).put("bodyPart", e?.category ?: "other").put("sets", 0) }
            o.put("sets", o.getInt("sets") + (if (log.timed) log.intervals ?: 0 else log.sets ?: 0))
            if (log.timed) {
                o.put("minutes", round1(o.optDouble("minutes", 0.0) + (log.setDurations?.sum() ?: 0.0)))
                o.put("distance", round1(o.optDouble("distance", 0.0) + (log.setDistances?.sum() ?: 0.0)))
            } else {
                val top = (log.setWeights?.filter { it > 0 }?.maxOrNull() ?: log.weight ?: 0.0)
                if (top > o.optDouble("topWeight", 0.0)) o.put("topWeight", top)
                val best = log.setReps?.maxOrNull() ?: log.reps ?: 0
                if (best > o.optInt("bestReps", 0)) o.put("bestReps", best)
            }
            if (log.notes.isNotBlank()) o.put("note", log.notes.take(160))
        }
        rows.values.forEach { o -> if (o.has("topWeight")) o.put("topWeight", "${jsNum(o.getDouble("topWeight"))} ${units.weight}") }
        return JSONArray(rows.values.take(40))
    }
}
