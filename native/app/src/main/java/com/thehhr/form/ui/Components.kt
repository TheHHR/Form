@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package com.thehhr.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.thehhr.form.data.Exercise
import com.thehhr.form.data.jsNum
import com.thehhr.form.data.jsNumber
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
    val base = (if (size != null) modifier.size(size) else modifier).clip(MaterialTheme.shapes.medium)
    if (path.isEmpty()) {
        Box(base.background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FitnessCenter, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    SubcomposeAsyncImage(
        model = assetUri(path),
        contentDescription = exercise?.let { "Demonstration of ${it.name}" },
        contentScale = ContentScale.Crop,
        // The exercise stills are drawn on white.
        modifier = base.background(androidx.compose.ui.graphics.Color(0xFFF2F2EE)),
        error = { Box(Modifier, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.FitnessCenter, null) } },
    )
}

/** Collapsing expressive top app bar. Pair with `Modifier.nestedScroll(scroll.nestedScrollConnection)` on the Scaffold. */
@Composable
fun FormTopBar(
    title: String,
    scroll: TopAppBarScrollBehavior,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    MediumFlexibleTopAppBar(
        title = { Text(title, maxLines = 1) },
        subtitle = subtitle?.let { { Text(it, maxLines = 1) } },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = actions,
        scrollBehavior = scroll,
        colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
    )
}

/** Single-choice connected button group (replaces segmented buttons in M3 Expressive). */
@Composable
fun <T> ChoiceGroup(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        options.forEachIndexed { i, (value, label) ->
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { onSelect(value) },
                enabled = enabled,
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) { Text(label, maxLines = 1) }
        }
    }
}

/** One row of a grouped (segmented) list: rounded group ends, small gaps between rows. */
@Composable
fun GroupedItem(
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    supporting: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    headline: @Composable () -> Unit,
) {
    val shapes = ListItemDefaults.segmentedShapes(index, count)
    val m = modifier.padding(bottom = if (index < count - 1) ListItemDefaults.SegmentedGap else 0.dp)
    // Rows sit on surfaceContainer so the group reads as one block against the near-black background.
    val colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    if (onClick != null) SegmentedListItem(onClick = onClick, shapes = shapes, modifier = m, leadingContent = leading,
        supportingContent = supporting, trailingContent = trailing, colors = colors, content = headline)
    else SegmentedListItem(shapes = shapes, modifier = m, leadingContent = leading, supportingContent = supporting,
        trailingContent = trailing, colors = colors, content = headline)
}

/** − value + in one pill. Tap the value to type it. Buttons keep 48 dp touch targets. */
@Composable
fun Stepper(
    label: String,
    value: Double,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    onChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var typing by remember { mutableStateOf(false) }
    Row(
        modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.start, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Remove, "Decrease $label")
        }
        Column(
            Modifier.widthIn(min = 52.dp).clickable { typing = true }.semantics(mergeDescendants = true) { contentDescription = "$label ${num(value)}, tap to type" },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(num(value), style = MaterialTheme.typography.titleMediumEmphasized, textAlign = TextAlign.Center)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.endInclusive, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.Rounded.Add, "Increase $label")
        }
    }
    if (typing) NumberDialog(label, value, range, { typing = false }) { onChange(it) }
}

@Composable
fun NumberDialog(label: String, value: Double, range: ClosedFloatingPointRange<Double>, onDismiss: () -> Unit, onSet: (Double) -> Unit) {
    var text by remember { mutableStateOf(num(value)) }
    val n = jsNumber(text)
    val ok = n.isFinite() && n in range
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(jsTitle(label)) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, isError = !ok,
                supportingText = { Text("${num(range.start)}–${num(range.endInclusive)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ok) { onSet(n); onDismiss() } }))
        },
        confirmButton = { TextButton(onClick = { onSet(n); onDismiss() }, enabled = ok) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Inline numeric field for set rows: tap, type, done. Commits valid values on IME done or focus loss.
 * Large emphasized digits in a pill so it reads at arm's length in the gym.
 */
@Composable
fun NumberField(value: Double, unit: String, range: ClosedFloatingPointRange<Double>, onCommit: (Double) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    var text by remember { mutableStateOf(num(value)) }
    LaunchedEffect(value) { if (jsNumber(text) != value) text = num(value) }
    val focus = LocalFocusManager.current
    fun commit() {
        val n = jsNumber(text)
        if (n.isFinite() && n in range) onCommit(n) else text = num(value)
    }
    val color = MaterialTheme.colorScheme.onSurface
    Row(
        modifier.heightIn(min = 48.dp).clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = text, onValueChange = { text = it.take(7) }, enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.titleMediumEmphasized.merge(TextStyle(color = color, textAlign = TextAlign.End)),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(); focus.clearFocus() }),
            modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) commit() }.semantics { contentDescription = unit },
        )
        Text(" $unit", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
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
