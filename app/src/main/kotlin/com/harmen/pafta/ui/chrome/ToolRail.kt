package com.harmen.pafta.ui.chrome

import androidx.annotation.StringRes
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
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.Window
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
import com.harmen.pafta.project.OpeningKind
import com.harmen.pafta.project.WallMaterial
import com.harmen.pafta.ui.adi
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
    lastText: String,
    onToolSelected: (Tool) -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
    measureMode: MeasurementKind = MeasurementKind.DISTANCE,
    /** How many points the measurement in progress already has. */
    pendingPickCount: Int = 0,
    onMeasureModeSelected: (MeasurementKind) -> Unit = {},
    onFinishMeasurement: () -> Unit = {},
    onUndoPick: () -> Unit = {},
    onCancelMeasurement: () -> Unit = {},
    wallThicknessMm: Double = 200.0,
    onWallThicknessSelected: (Double) -> Unit = {},
    wallMaterial: WallMaterial = WallMaterial.BRICK,
    onWallMaterialSelected: (WallMaterial) -> Unit = {},
    selectedShapeId: String? = null,
    onDeleteSelected: () -> Unit = {},
    onFinishChain: () -> Unit = {},
    doorWidthMm: Double = 900.0,
    windowWidthMm: Double = 1200.0,
    onOpeningWidthSelected: (OpeningKind, Double) -> Unit = { _, _ -> },
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

            if (tool == Tool.TEXT && activeTool == Tool.TEXT && lastText.isNotBlank()) {
                LastTextPreview(lastText)
            }
            // A wall has to be told how thick it is before it can be drawn;
            // these are the thicknesses a plan is actually drawn with.
            if (tool == Tool.WALL && activeTool == Tool.WALL && !compact) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                ) {
                    for (thickness in WALL_THICKNESSES_MM) {
                        RailChip(
                            text = formatLength(thickness),
                            selected = thickness == wallThicknessMm,
                            onClick = { onWallThicknessSelected(thickness) },
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    // Material is chosen before drawing too, because it decides
                    // which layer the wall lands on — and a wall cannot change
                    // layer after the fact without breaking what was grouped.
                    for (material in WallMaterial.entries) {
                        RailChip(
                            text = material.adi(),
                            selected = material == wallMaterial,
                            onClick = { onWallMaterialSelected(material) },
                        )
                    }
                }
            }
            // An opening is placed, not drawn, so the one thing it needs before
            // it is placed is how wide it is — and the one thing the user needs
            // is to be told where to tap.
            if (tool == activeTool && tool in OPENING_TOOLS && !compact) {
                val kind = if (tool == Tool.DOOR) OpeningKind.DOOR else OpeningKind.WINDOW
                val chosen = if (kind == OpeningKind.DOOR) doorWidthMm else windowWidthMm
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                ) {
                    for (width in if (kind == OpeningKind.DOOR) DOOR_WIDTHS_MM else WINDOW_WIDTHS_MM) {
                        RailChip(
                            text = formatLength(width),
                            selected = width == chosen,
                            onClick = { onOpeningWidthSelected(kind, width) },
                        )
                    }
                    RailNote(R.string.hint_place_on_wall)
                }
            }
            // The room tool has nothing to set, only somewhere to tap.
            if (tool == Tool.ZONE && activeTool == Tool.ZONE && !compact) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                ) {
                    RailNote(R.string.hint_place_in_room)
                }
            }
            // A run of walls has to be able to stop. Switching tool ends it too,
            // but that is not something to make the user discover.
            if (tool == activeTool && tool in CHAINABLE && !compact && pendingPickCount > 0) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                ) {
                    RailChip(
                        text = stringResource(R.string.action_finish_chain),
                        selected = false,
                        onClick = onFinishChain,
                    )
                }
            }
            if (tool == Tool.SELECT && activeTool == Tool.SELECT && !compact &&
                selectedShapeId != null
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
                ) {
                    RailChip(
                        text = stringResource(R.string.action_delete_shape),
                        selected = false,
                        onClick = onDeleteSelected,
                    )
                }
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
    Tool.WALL -> Icons.Outlined.Straighten
    Tool.LINE -> Icons.Outlined.ShowChart
    Tool.RECTANGLE -> Icons.Outlined.Crop169
    Tool.CIRCLE -> Icons.Outlined.Circle
    Tool.DOOR -> Icons.Outlined.DoorFront
    Tool.WINDOW -> Icons.Outlined.Window
    Tool.ZONE -> Icons.Outlined.Dashboard
    Tool.MEASURE -> Icons.Outlined.SquareFoot
    Tool.GRID -> Icons.Outlined.GridOn
    Tool.ARC -> Icons.Outlined.Architecture
    Tool.HATCH -> Icons.Outlined.Texture
    Tool.TEXT -> Icons.Outlined.TextFields
    Tool.PALETTE -> Icons.Outlined.Palette
    Tool.LAYERS -> Icons.Outlined.Layers
}

/** Tools that keep going from where the last shape ended. */
private val CHAINABLE = setOf(Tool.WALL, Tool.LINE)

/** The wall thicknesses a plan is actually drawn with, in millimetres. */
private val WALL_THICKNESSES_MM = listOf(100.0, 200.0, 300.0)

/** Tools that put an opening in a wall. */
private val OPENING_TOOLS = setOf(Tool.DOOR, Tool.WINDOW)

/** Door leaf widths as they are actually ordered. */
private val DOOR_WIDTHS_MM = listOf(700.0, 800.0, 900.0, 1000.0)

/** Window widths, the same way. */
private val WINDOW_WIDTHS_MM = listOf(600.0, 900.0, 1200.0, 1800.0)

/**
 * A line of guidance under a tool.
 *
 * Placing needs a target, and a tool that waits silently for a tap somewhere
 * particular is a tool that does nothing when you tap the wrong place.
 */
@Composable
private fun RailNote(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = HarmenType.Status,
        color = HarmenColours.TextFaint,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 5.dp),
    )
}
