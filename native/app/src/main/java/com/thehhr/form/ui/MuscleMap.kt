package com.thehhr.form.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thehhr.form.data.Muscles
import com.thehhr.form.data.jsTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One side of a body: view box (originX, originY, width, height) and its muscle paths. */
class BodySide(val viewBox: FloatArray, val parts: List<Pair<String, Path>>)

/** MuscleMapJS body data (MIT), parsed once per process off the main thread. */
object MuscleMapData {
    @Volatile private var cache: Map<String, BodySide>? = null

    suspend fun load(ctx: Context): Map<String, BodySide> = cache ?: withContext(Dispatchers.Default) {
        cache ?: run {
            val root = JSONObject(ctx.assets.open("musclemap.json").bufferedReader().readText())
            root.keys().asSequence().associateWith { key ->
                val o = root.getJSONObject(key)
                val vb = o.getJSONArray("viewBox").let { a -> FloatArray(4) { a.getDouble(it).toFloat() } }
                val parts = o.getJSONArray("parts")
                BodySide(vb, (0 until parts.length()).flatMap { i ->
                    val p = parts.getJSONObject(i)
                    val slug = p.getString("slug")
                    val paths = p.getJSONArray("paths")
                    (0 until paths.length()).map { slug to PathParser().parsePathString(paths.getString(it)).toPath() }
                })
            }.also { cache = it }
        }
    }
}

/**
 * Front and back body side by side; [heat] maps muscle slug -> 0..1 (0 = untouched).
 * Female figure when [sex] is "f".
 */
@Composable
fun MuscleMap(heat: Map<String, Float>, sex: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var data by remember { mutableStateOf<Map<String, BodySide>?>(null) }
    LaunchedEffect(Unit) { data = MuscleMapData.load(ctx) }
    val gender = if (sex == "f") "female" else "male"
    // Untrained muscles: a step lighter than the surfaceContainer card the map sits on.
    val base = MaterialTheme.colorScheme.outlineVariant
    val hot = MaterialTheme.colorScheme.primary
    val label = if (heat.isEmpty()) "No muscles highlighted"
    else "Muscles worked: " + heat.entries.sortedByDescending { it.value }.joinToString { jsTitle(it.key.replace('-', ' ')) }
    Row(modifier.fillMaxWidth().semantics { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (side in listOf("front", "back")) {
            val body = data?.get("$gender-$side")
            val vb = body?.viewBox ?: floatArrayOf(0f, 0f, 727f, 1280f)
            Canvas(Modifier.weight(1f).aspectRatio(vb[2] / vb[3])) {
                if (body == null) return@Canvas
                val scale = minOf(size.width / vb[2], size.height / vb[3])
                withTransform({
                    translate((size.width - vb[2] * scale) / 2, (size.height - vb[3] * scale) / 2)
                    scale(scale, scale, pivot = androidx.compose.ui.geometry.Offset.Zero)
                }) {
                    // Back views live at an x/y offset inside the original SVG; shift them to the origin.
                    translate(-vb[0], -vb[1]) {
                        for ((slug, path) in body.parts) {
                            val h = heat[slug]
                            if (slug in Muscles.HIDDEN_SUBGROUPS && h == null) continue
                            drawPath(path, if (h == null) base else lerp(base, hot, h.coerceIn(0f, 1f)))
                        }
                    }
                }
            }
        }
    }
}
