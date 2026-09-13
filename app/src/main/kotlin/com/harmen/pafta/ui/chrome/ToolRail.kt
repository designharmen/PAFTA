package com.harmen.pafta.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmen.pafta.R
import com.harmen.pafta.measure.MeasurementKind
import com.harmen.pafta.ui.state.Tool
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.metrics
import com.harmen.pafta.units.formatLength

/**
 * The left tool rail: icon plus caption, one column.
 *
 * Two tools expand inline when they are active, because both need a value
 * before they can do anything: `Dimensions` offers its length presets, and
 * `Text` shows the last string typed so it can be stamped again without
 * re-entering it.
 */
@Composable
public fun ToolRail(
    activeTool: Tool,
    dimensionPresets: List<Double>,
    selectedPreset: Double?,
    lastText: String,
    onToolSelected: (Tool) -> Unit,
    onPresetSelected: (Double) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    measureMode: MeasurementKind = MeasurementKind.DISTANCE,
    /** How many points the measurement in progress already has. */
    pendingPickCount: Int = 0,
    onMeasureModeSelected: (MeasurementKind) -> Unit = {},
    onFinishMeasurement: () -> Unit = {},
    onUndoPick: () -> Unit = {},
    onCancelMeasurement: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .width(if (compact) metrics.toolRailWidthCompact else metrics.toolRailWidth)
            .fillMaxHeight()
            .background(HarmenColours.Panel)
            .verticalScroll(rememberScrollState())
            .padding(vertical = metrics.gutterTight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (tool in Tool.entries) {
            ToolButton(
                tool = tool,
                selected = tool == activeTool,
                compact = compact,
                onClick = { onToolSelected(tool) },
            )

            if (tool == Tool.DIMENSIONS && activeTool == Tool.DIMENSIONS) {
                PresetList(dimensionPresets, selectedPreset, onPresetSelected)
            }
            if (tool == Tool.TEXT && activeTool == Tool.TEXT && lastText.isNotBlank()) {
                LastTextPreview(lastText)
            }
            if (tool == Tool.MEASURE && activeTool == Tool.MEASURE && !compact) {
                MeasurePanel(
                    mode = measureMode,
                    pendingPickCount = pendingPickCount,
                    onModeSelected = onMeasureModeSelected,
                    onFinish = onFinishMeasurement,
                    onUndoPick = onUndoPick,
                    onCancel = onCancelMeasurement,
                )
            }
        }
    }
}

@Composable
private fun ToolButton(
    tool: Tool,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    // Bronze is allowed on the icon — the guideline names icons alongside lines
    // and rules — but never on the caption beneath it, which is far under the
    // 24px floor for bronze text.
    //
    // A tool whose phase has not landed yet is drawn faint and does not respond,
    // so the rail never promises something it cannot do.
    val tint = when {
        !tool.ready -> HarmenColours.TextFaint
        selected -> HarmenColours.Accent
        else -> HarmenColours.TextMuted
    }
    val labelColour = when {
        !tool.ready -> HarmenColours.TextFaint
        selected -> HarmenColours.Text
        else -> HarmenColours.TextMuted
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(if (selected) HarmenColours.SelectedWash else Color.Transparent)
            .clickable(enabled = tool.ready, role = Role.Tab, onClick = onClick)
            .padding(vertical = 7.dp),
    ) {
        val label = stringResource(tool.label)
        Icon(
            imageVector = tool.icon(),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(if (compact) 18.dp else 20.dp),
        )
        if (!compact) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = HarmenType.ToolLabel,
                color = labelColour,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * What the Ölç tool is taking, and how to close it.
 *
 * A distance finishes itself on the second tap; an area and an angle cannot —
 * an area has no fixed number of corners — so those need a way to say "that is
 * all", and every mode needs a way to take back a mis-tap without starting
 * over. Putting all of it under the tool keeps the canvas free of chrome.
 */
@Composable
private fun MeasurePanel(
    mode: MeasurementKind,
    pendingPickCount: Int,
    onModeSelected: (MeasurementKind) -> Unit,
    onFinish: () -> Unit,
    onUndoPick: () -> Unit,
    onCancel: () -> Unit,
) {
    val modes = listOf(
        MeasurementKind.DISTANCE to R.string.measure_distance,
        MeasurementKind.AREA to R.string.measure_area,
        MeasurementKind.ANGLE to R.string.measure_angle,
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        for ((kind, label) in modes) {
            RailChip(
                text = stringResource(label),
                selected = kind == mode,
                onClick = { onModeSelected(kind) },
            )
        }

        if (pendingPickCount > 0) {
            Spacer(Modifier.height(3.dp))
            // An area needs three corners before it is an area; an angle needs
            // three points. Offering "finish" earlier would offer nothing.
            val canFinish = when (mode) {
                MeasurementKind.AREA -> pendingPickCount >= 3
                MeasurementKind.POLYLINE -> pendingPickCount >= 2
                else -> false
            }
            if (canFinish) {
                RailChip(text = stringResource(R.string.measure_finish), selected = false, onClick = onFinish)
            }
            RailChip(text = stringResource(R.string.measure_undo_pick), selected = false, onClick = onUndoPick)
            RailChip(text = stringResource(R.string.measure_cancel), selected = false, onClick = onCancel)
        }
    }
}

/** A small full-width button in the rail, matching the preset buttons. */
@Composable
private fun RailChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(if (selected) HarmenColours.SelectedWash else HarmenColours.PanelRaised)
            .drawBehind {
                if (!selected) return@drawBehind
                drawRect(color = HarmenColours.Accent, style = Stroke(width = 1.dp.toPx()))
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 2.dp),
    ) {
        Text(
            text = text,
            style = HarmenType.ToolLabel,
            color = if (selected) HarmenColours.Text else HarmenColours.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The `3100mm` / `4500mm` / `4800mm` quick-dimension buttons. */
@Composable
private fun PresetList(
    presets: List<Double>,
    selected: Double?,
    onPresetSelected: (Double) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 4.dp),
    ) {
        for (preset in presets) {
            val isSelected = selected == preset
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(metrics.cornerRadius))
                    .background(if (isSelected) HarmenColours.SelectedWash else HarmenColours.PanelRaised)
                    .drawBehind {
                        if (!isSelected) return@drawBehind
                        drawRect(
                            color = HarmenColours.Accent,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                    .clickable(role = Role.Button) { onPresetSelected(preset) }
                    .padding(vertical = 5.dp, horizontal = 2.dp),
            ) {
                Text(
                    text = formatLength(preset),
                    style = HarmenType.Numeric,
                    color = if (isSelected) HarmenColours.Text else HarmenColours.TextMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Shows the most recent annotation string under the Text tool. */
@Composable
private fun LastTextPreview(lastText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 5.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(HarmenColours.PanelRaised)
            .padding(horizontal = 4.dp, vertical = 5.dp),
    ) {
        Text(
            text = lastText,
            style = HarmenType.ToolLabel,
            color = HarmenColours.TextFaint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Icons are drawn from the Material set rather than a bespoke pack: they match
 * the thin-stroke look the palette asks for, and they keep the rail legible at
 * 18dp on a phone.
 */
private fun Tool.icon(): ImageVector = when (this) {
    Tool.SELECT -> Icons.Outlined.NearMe
    Tool.PENCIL -> Icons.Outlined.Create
    Tool.LINE -> Icons.Outlined.ShowChart
    Tool.ARC -> Icons.Outlined.Architecture
    Tool.DIM -> Icons.Outlined.Straighten
    Tool.DIMENSIONS -> Icons.Outlined.FormatListNumbered
    Tool.HATCH -> Icons.Outlined.Texture
    Tool.TEXT -> Icons.Outlined.TextFields
    Tool.GRID -> Icons.Outlined.GridOn
    Tool.MEASURE -> Icons.Outlined.SquareFoot
    Tool.PALETTE -> Icons.Outlined.Palette
    Tool.LAYERS -> Icons.Outlined.Layers
}
