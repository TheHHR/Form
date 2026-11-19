package com.thehhr.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.thehhr.form.data.Exercise
import com.thehhr.form.data.jsNum
import com.thehhr.form.data.jsTitle

/** Whether the APK bundles exercise videos (prod does, beta doesn't). Set once at start-up. */
object Media { var hasVideos = false }

fun assetUri(path: String) = "file:///android_asset/$path"

@Composable
fun ExerciseImage(exercise: Exercise?, modifier: Modifier = Modifier, animated: Boolean = false, size: Dp? = null) {
    val path = when {
        exercise == null -> ""
        animated && Media.hasVideos && exercise.gif.isNotEmpty() -> exercise.gif
        else -> exercise.image
    }
    val base = (if (size != null) modifier.size(size) else modifier).clip(RoundedCornerShape(16.dp))
    if (path.isEmpty()) {
        Box(base.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FitnessCenter, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    // The exercise stills are drawn on white.
    val m = base.background(Color16)
    SubcomposeAsyncImage(
        model = assetUri(path),
        contentDescription = exercise?.let { "Demonstration of ${it.name}" },
        contentScale = ContentScale.Crop,
        modifier = m,
        error = { Box(Modifier, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.FitnessCenter, null) } },
    )
}

private val Color16 = androidx.compose.ui.graphics.Color(0xFFF2F2EE)

@Composable
fun Stepper(
    label: String,
    value: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Remove, "Decrease $label", Modifier.size(18.dp)) }
        Column(Modifier.widthIn(min = 44.dp).semantics(mergeDescendants = true) { contentDescription = "$label $value" }, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Add, "Increase $label", Modifier.size(18.dp)) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyNote(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
}

fun exTitle(e: Exercise?) = jsTitle(e?.name ?: "Unknown exercise")
fun num(v: Double) = jsNum(Math.round(v * 10) / 10.0)
fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
