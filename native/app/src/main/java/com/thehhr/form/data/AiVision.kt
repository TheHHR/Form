package com.thehhr.form.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Can the chosen model read images, and the meal-photo plumbing. */
object AiVision {
    /** OpenRouter model id -> takes images, from its public models list (fetched once per process). */
    private val openRouter = mutableStateMapOf<String, Boolean>()
    @Volatile private var fetching = false

    /**
     * true / false when known, null when unsure (custom endpoints, unknown names): the camera then stays enabled
     * and the provider's own error explains a refusal. shortcut: name heuristics outside OpenRouter, upgrade if a
     * provider publishes modalities.
     */
    fun supports(cfg: AiConfig): Boolean? {
        val m = cfg.model.lowercase()
        return when (cfg.provider) {
            "gemini" -> true // every current Gemini model is multimodal
            "deepseek" -> false
            "openrouter" -> { fetchOpenRouter(); openRouter[cfg.model] ?: byName(m) }
            "openai" -> m.startsWith("gpt-4o") || m.startsWith("gpt-4.1") || m.startsWith("gpt-5") || m.startsWith("o3") || m.startsWith("o4") || m.startsWith("chatgpt-4o")
            "mistral" -> m.contains("pixtral") || m.contains("medium") || m.contains("small-3") || m.contains("small-latest")
            "xai" -> m.contains("vision") || m.startsWith("grok-4") || m.startsWith("grok-2-vision")
            else -> byName(m)
        }
    }

    private fun byName(m: String): Boolean? = when {
        listOf("vision", "-vl", "vl-", "gpt-4o", "gpt-4.1", "gpt-5", "gemini", "claude", "pixtral", "llava", "llama-4", "grok-4", "qwen2.5-vl", "gemma-3").any { m.contains(it) } -> true
        listOf("deepseek", "coder", "embed", "whisper", "tts").any { m.contains(it) } -> false
        else -> null
    }

    private fun fetchOpenRouter() {
        if (fetching || openRouter.isNotEmpty()) return
        fetching = true
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val conn = URL("https://openrouter.ai/api/v1/models").openConnection() as HttpURLConnection
                conn.connectTimeout = 10_000; conn.readTimeout = 15_000
                val data = JSONObject(conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }).getJSONArray("data")
                val out = HashMap<String, Boolean>()
                for (i in 0 until data.length()) {
                    val o = data.getJSONObject(i)
                    val inputs = o.optJSONObject("architecture")?.optJSONArray("input_modalities")
                    out[o.getString("id")] = inputs != null && (0 until inputs.length()).any { inputs.optString(it) == "image" }
                }
                kotlinx.coroutines.withContext(Dispatchers.Main) { openRouter.putAll(out) }
            }
            fetching = false
        }
    }

    fun photoUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "meal").apply { mkdirs() }
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(dir, "meal.jpg"))
    }

    /** Downscales to 1024 px on the long side, JPEG 85: small enough for any provider's request limit. */
    fun loadJpeg(ctx: Context, uri: Uri, max: Int = 1024): ByteArray? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= max) sample *= 2
        val raw = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val scale = max.toFloat() / maxOf(raw.width, raw.height)
        val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }.getOrNull()
}
