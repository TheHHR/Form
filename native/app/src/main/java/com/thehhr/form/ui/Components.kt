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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.material3.TopAppBar
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
        modifier = base.background(androidx.compose.ui.graphics.Color.White),
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
    // Single row: the title shares the line with back/actions, no tall empty header.
    val nav: @Composable () -> Unit = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } }
    val colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer)
    val titleText: @Composable () -> Unit = { Text(title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLargeEmphasized) }
    if (subtitle != null) TopAppBar(title = titleText, subtitle = { Text(subtitle, maxLines = 1) }, navigationIcon = nav, actions = actions, scrollBehavior = scroll, colors = colors)
    else TopAppBar(title = titleText, navigationIcon = nav, actions = actions, scrollBehavior = scroll, colors = colors)
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
                // Tighter than the default so four options (e.g. meal slots) fit a 360 dp phone without truncating.
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) { Text(label, maxLines = 1, softWrap = false) }
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
    val colors = if (LocalInSheet.current) ListItemDefaults.segmentedColors() else ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
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
 * − value + in one pill for set rows and the log form: tap ± to nudge, or tap the number and type.
 * Typed text commits on IME done or focus loss; a ± tap while typing builds on the typed value.
 * The unit is announced to screen readers; callers show it visually in a column header.
 */
@Composable
fun StepperField(
    value: Double,
    unit: String,
    step: Double,
    range: ClosedFloatingPointRange<Double>,
    onCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var text by remember { mutableStateOf(num(value)) }
    LaunchedEffect(value) { if (jsNumber(text) != value) text = num(value) }
    val focus = LocalFocusManager.current
    fun typedOr(fallback: Double) = jsNumber(text).takeIf { it.isFinite() && it in range } ?: fallback
    fun commit() {
        val n = jsNumber(text)
        if (n.isFinite() && n in range) { if (n != value) onCommit(n) } else text = num(value)
    }
    fun nudge(delta: Double) {
        val next = (typedOr(value) + delta).coerceIn(range)
        text = num(next)
        onCommit(next)
    }
    Row(
        modifier.height(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(Icons.Rounded.Remove, "Decrease $unit", enabled && typedOr(value) > range.start) { nudge(-step) }
        BasicTextField(
            value = text, onValueChange = { text = it.take(6) }, enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.titleMediumEmphasized.merge(TextStyle(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { commit(); focus.clearFocus() }),
            modifier = Modifier.weight(1f).onFocusChanged { if (!it.isFocused) commit() }.semantics { contentDescription = "$unit ${num(value)}" },
        )
        StepButton(Icons.Rounded.Add, "Increase $unit", enabled && typedOr(value) < range.endInclusive) { nudge(step) }
    }
}

/** Full-height (48 dp) tap target inside the stepper pill; 40 dp wide so two steppers fit a 360 dp phone. */
@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.width(40.dp).fillMaxHeight().clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
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

/** App-wide date style: "Tue, Aug 11", with ", 2025" only when it isn't the current year. */
fun displayDate(iso: String): String = runCatching {
    val d = java.time.LocalDate.parse(iso)
    val pattern = if (d.year == java.time.LocalDate.now().year) "EEE, MMM d" else "EEE, MMM d, yyyy"
    d.format(java.time.format.DateTimeFormatter.ofPattern(pattern, java.util.Locale.ENGLISH))
}.getOrDefault(iso)

/**
 * Expressive filter pill: filled tonal, fully rounded, no outline; selected = accent fill with a check.
 * Same parameter order as FilterChip so call sites read the same.
 */
@Composable
fun Pill(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    androidx.compose.material3.FilterChip(
        selected = selected, onClick = onClick, label = label, modifier = modifier.height(32.dp),
        shape = CircleShape, border = null,
        leadingIcon = leadingIcon ?: if (selected) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurface,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** Non-interactive tonal badge (exercise muscles, equipment, …). */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    androidx.compose.material3.Surface(modifier, shape = CircleShape, color = if (accent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (accent) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge)
    }
}

/** ‹ label › navigation for days/weeks/months, with Expressive tonal icon buttons. */
@Composable
fun PeriodNav(label: String, what: String, onPrev: () -> Unit, onNext: () -> Unit, nextEnabled: Boolean, onLabel: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.FilledTonalIconButton(onClick = onPrev, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous $what")
        }
        Text(label, (if (onLabel != null) Modifier.weight(1f).clickable(onClick = onLabel) else Modifier.weight(1f)),
            style = MaterialTheme.typography.titleMediumEmphasized, textAlign = TextAlign.Center)
        androidx.compose.material3.FilledTonalIconButton(onClick = onNext, enabled = nextEnabled, shapes = IconButtonDefaults.shapes()) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next $what")
        }
    }
}

/** True inside a [FormSheet]: grouped rows switch to the sheet-friendly default colours (as the Activity level sheet). */
val LocalInSheet = androidx.compose.runtime.staticCompositionLocalOf { false }

/** The app's one bottom sheet: always fully expanded, same colours, motion and drag handle everywhere. */
@Composable
fun FormSheet(onDismissRequest: () -> Unit, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) { androidx.compose.runtime.CompositionLocalProvider(LocalInSheet provides true) { content() } }
}
