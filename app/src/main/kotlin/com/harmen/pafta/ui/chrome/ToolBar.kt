package com.harmen.pafta.ui.chrome

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material.icons.outlined.Chair
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.CallMerge
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Crop169
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Details
import androidx.compose.material.icons.outlined.DoorFront
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.GridOn
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.RoundedCorner
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.SquareFoot
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Texture
import androidx.compose.material.icons.outlined.ViewColumn
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
import com.harmen.pafta.project.SlabKind
import com.harmen.pafta.project.WallMaterial
import com.harmen.pafta.ui.adi
import com.harmen.pafta.ui.state.PAIRED_TOOLS
import com.harmen.pafta.ui.state.TOOLBAR_TOOLS
import com.harmen.pafta.ui.state.Tool
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.metrics
import com.harmen.pafta.units.formatLength

/**
 * The drawing and editing tools, along the top.
 *
 * They sit above the sheet because that is what they act on: pick something,
 * draw a line, round a corner, cut one thing back to another, measure. The
 * things a building is *made* of — walls, doors, windows — are not here; they
 * have their own column down the right-hand side.
 *
 * The row scrolls sideways rather than wrapping onto a second line: a tool that
 * moves to a different place depending on how wide the tablet is held is a tool
 * the user has to hunt for every time.
 */
@Composable
public fun DrawingToolBar(
    activeTool: Tool,
    onToolSelected: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HarmenColours.Panel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = metrics.gutterTight, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (tool in TOOLBAR_TOOLS) {
            ToolButton(
                tool = tool,
                selected = tool == activeTool,
                compact = compact,
                onClick = { onToolSelected(tool) },
            )
        }
    }
}

/**
 * What the tool in hand needs told before it can work, in one strip.
 *
 * There is one of these and not one per tool: a wall's thickness, a door's
 * width and a fillet's radius are all "the setting for what I am holding", and
 * putting them in the same place means the user learns where to look once.
 *
 * When the tool has nothing to set, nothing is drawn at all — an empty grey
 * strip taking up the top of the sheet would be worse than no strip.
 */
@Composable
public fun ToolOptionsBar(
    activeTool: Tool,
    modifier: Modifier = Modifier,
    wallThicknessMm: Double = 200.0,
    onWallThicknessSelected: (Double) -> Unit = {},
    wallMaterial: WallMaterial = WallMaterial.BRICK,
    onWallMaterialSelected: (WallMaterial) -> Unit = {},
    doorWidthMm: Double = 900.0,
    windowWidthMm: Double = 1200.0,
    onOpeningWidthSelected: (OpeningKind, Double) -> Unit = { _, _ -> },
    /** Whether a two-shape tool already has its first shape. */
    hasFirstPick: Boolean = false,
    filletRadiusMm: Double = 300.0,
    onFilletRadiusSelected: (Double) -> Unit = {},
    chamferMm: Double = 300.0,
    onChamferSizeSelected: (Double) -> Unit = {},
    measureMode: MeasurementKind = MeasurementKind.DISTANCE,
    /** How many points the measurement in progress already has. */
    pendingPickCount: Int = 0,
    onMeasureModeSelected: (MeasurementKind) -> Unit = {},
    onFinishMeasurement: () -> Unit = {},
    onUndoPick: () -> Unit = {},
    onCancelMeasurement: () -> Unit = {},
    selectedShapeId: String? = null,
    onDeleteSelected: () -> Unit = {},
    onFinishChain: () -> Unit = {},
    columnSizeMm: Double = 300.0,
    onColumnSizeSelected: (Double) -> Unit = {},
    columnRound: Boolean = false,
    onColumnRoundSelected: (Boolean) -> Unit = {},
    beamWidthMm: Double = 250.0,
    onBeamWidthSelected: (Double) -> Unit = {},
    slabThicknessMm: Double = 150.0,
    onSlabThicknessSelected: (Double) -> Unit = {},
    slabKind: SlabKind = SlabKind.FLOOR,
    onSlabKindSelected: (SlabKind) -> Unit = {},
) {
    val chainable = activeTool in CHAINABLE && pendingPickCount > 0
    val hasSomethingToSay = when (activeTool) {
        Tool.WALL -> true
        Tool.DOOR, Tool.WINDOW -> true
        Tool.ZONE, Tool.SLAB, Tool.COLUMN, Tool.BEAM -> true
        Tool.MEASURE -> true
        Tool.SELECT -> selectedShapeId != null
        in PAIRED_TOOLS -> true
        else -> chainable
    }
    if (!hasSomethingToSay) return

    Column(modifier.fillMaxWidth()) {
        HairlineDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(HarmenColours.Panel)
                .horizontalScroll(rememberScrollState())
                .heightIn(min = 40.dp)
                .padding(horizontal = metrics.gutter, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (activeTool) {
                Tool.WALL -> {
                    OptionGroup(R.string.option_thickness) {
                        for (thickness in WALL_THICKNESSES_MM) {
                            OptionChip(
                                text = formatLength(thickness),
                                selected = thickness == wallThicknessMm,
                                onClick = { onWallThicknessSelected(thickness) },
                            )
                        }
                    }
                    // Material is chosen before drawing, because it decides
                    // which layer the wall lands on — and a wall cannot change
                    // layer after the fact without breaking what was grouped.
                    OptionGroup(R.string.option_material) {
                        for (material in WallMaterial.entries) {
                            OptionChip(
                                text = material.adi(),
                                selected = material == wallMaterial,
                                onClick = { onWallMaterialSelected(material) },
                            )
                        }
                    }
                }

                Tool.DOOR, Tool.WINDOW -> {
                    val kind =
                        if (activeTool == Tool.DOOR) OpeningKind.DOOR else OpeningKind.WINDOW
                    val chosen = if (kind == OpeningKind.DOOR) doorWidthMm else windowWidthMm
                    val widths =
                        if (kind == OpeningKind.DOOR) DOOR_WIDTHS_MM else WINDOW_WIDTHS_MM
                    OptionGroup(R.string.option_width) {
                        for (width in widths) {
                            OptionChip(
                                text = formatLength(width),
                                selected = width == chosen,
                                onClick = { onOpeningWidthSelected(kind, width) },
                            )
                        }
                    }
                    BarNote(R.string.hint_place_on_wall)
                }

                Tool.ZONE -> BarNote(R.string.hint_place_in_room)

                Tool.SLAB -> {
                    OptionGroup(R.string.option_slab_thickness) {
                        for (thickness in SLAB_THICKNESSES_MM) {
                            OptionChip(
                                text = formatLength(thickness),
                                selected = thickness == slabThicknessMm,
                                onClick = { onSlabThicknessSelected(thickness) },
                            )
                        }
                    }
                    // A flat roof is the same object at a different level, so it
                    // is the same tool with a different answer — not a seventh
                    // button down the right-hand side saying nearly the same word.
                    OptionGroup(R.string.option_slab_kind) {
                        OptionChip(
                            text = stringResource(R.string.slab_floor),
                            selected = slabKind == SlabKind.FLOOR,
                            onClick = { onSlabKindSelected(SlabKind.FLOOR) },
                        )
                        OptionChip(
                            text = stringResource(R.string.slab_roof),
                            selected = slabKind == SlabKind.ROOF,
                            onClick = { onSlabKindSelected(SlabKind.ROOF) },
                        )
                    }
                    BarNote(R.string.hint_place_slab)
                }

                Tool.COLUMN -> {
                    OptionGroup(R.string.option_column_size) {
                        for (size in COLUMN_SIZES_MM) {
                            OptionChip(
                                text = formatLength(size),
                                selected = size == columnSizeMm,
                                onClick = { onColumnSizeSelected(size) },
                            )
                        }
                    }
                    OptionGroup(R.string.option_column_shape) {
                        OptionChip(
                            text = stringResource(R.string.column_square),
                            selected = !columnRound,
                            onClick = { onColumnRoundSelected(false) },
                        )
                        OptionChip(
                            text = stringResource(R.string.column_round),
                            selected = columnRound,
                            onClick = { onColumnRoundSelected(true) },
                        )
                    }
                    BarNote(R.string.hint_place_column)
                }

                Tool.BEAM -> {
                    OptionGroup(R.string.option_beam_width) {
                        for (width in BEAM_WIDTHS_MM) {
                            OptionChip(
                                text = formatLength(width),
                                selected = width == beamWidthMm,
                                onClick = { onBeamWidthSelected(width) },
                            )
                        }
                    }
                    BarNote(R.string.hint_draw_beam)
                }

                Tool.FILLET -> {
                    OptionGroup(R.string.option_radius) {
                        for (radius in CORNER_SIZES_MM) {
                            OptionChip(
                                text = formatLength(radius),
                                selected = radius == filletRadiusMm,
                                onClick = { onFilletRadiusSelected(radius) },
                            )
                        }
                    }
                    BarNote(activeTool.hint(hasFirstPick))
                }

                Tool.CHAMFER -> {
                    OptionGroup(R.string.option_chamfer) {
                        for (size in CORNER_SIZES_MM) {
                            OptionChip(
                                text = formatLength(size),
                                selected = size == chamferMm,
                                onClick = { onChamferSizeSelected(size) },
                            )
                        }
                    }
                    BarNote(activeTool.hint(hasFirstPick))
                }

                Tool.TRIM, Tool.EXTEND, Tool.MIRROR, Tool.JOIN ->
                    BarNote(activeTool.hint(hasFirstPick))

                Tool.MEASURE -> MeasureOptions(
                    mode = measureMode,
                    pendingPickCount = pendingPickCount,
                    onModeSelected = onMeasureModeSelected,
                    onFinish = onFinishMeasurement,
                    onUndoPick = onUndoPick,
                    onCancel = onCancelMeasurement,
                )

                Tool.SELECT -> if (selectedShapeId != null) {
                    OptionChip(
                        text = stringResource(R.string.action_delete_shape),
                        selected = false,
                        onClick = onDeleteSelected,
                    )
                }

                else -> Unit
            }

            // A run of walls has to be able to stop. Switching tool ends it too,
            // but that is not something to make the user discover.
            if (chainable) {
                Spacer(Modifier.width(metrics.gutter))
                OptionChip(
                    text = stringResource(R.string.action_finish_chain),
                    selected = false,
                    onClick = onFinishChain,
                )
            }
        }
    }
}

/**
 * What the Ölç tool is taking, and how to close it.
 *
 * A distance finishes itself on the second tap; an area and an angle cannot —
 * an area has no fixed number of corners — so those need a way to say "that is
 * all", and every mode needs a way to take back a mis-tap without starting
 * over.
 */
@Composable
private fun MeasureOptions(
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

    OptionGroup(R.string.option_measure) {
        for ((kind, label) in modes) {
            OptionChip(
                text = stringResource(label),
                selected = kind == mode,
                onClick = { onModeSelected(kind) },
            )
        }
    }

    if (pendingPickCount > 0) {
        Spacer(Modifier.width(metrics.gutter))
        // An area needs three corners before it is an area; an angle needs
        // three points. Offering "finish" earlier would offer nothing.
        val canFinish = when (mode) {
            MeasurementKind.AREA -> pendingPickCount >= 3
            MeasurementKind.POLYLINE -> pendingPickCount >= 2
            else -> false
        }
        if (canFinish) {
            OptionChip(stringResource(R.string.measure_finish), selected = false, onClick = onFinish)
        }
        OptionChip(stringResource(R.string.measure_undo_pick), selected = false, onClick = onUndoPick)
        OptionChip(stringResource(R.string.measure_cancel), selected = false, onClick = onCancel)
    }
}

/** A named set of choices in the settings strip: the word, then the buttons. */
@Composable
private fun OptionGroup(@StringRes label: Int, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(end = metrics.gutter),
    ) {
        Text(
            text = stringResource(label),
            style = HarmenType.PropertyKey,
            color = HarmenColours.TextFaint,
            maxLines = 1,
            modifier = Modifier.padding(end = 2.dp),
        )
        content()
    }
}

/**
 * One tool: an icon with its name under it.
 *
 * A tool whose phase has not landed yet is drawn faint and does not respond, so
 * the interface never promises something it cannot do.
 */
@Composable
internal fun ToolButton(
    tool: Tool,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bronze is allowed on the icon — the guideline names icons alongside lines
    // and rules — but never on the caption beneath it, which is far under the
    // 24px floor for bronze text.
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
    val label = stringResource(tool.label)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(if (selected) HarmenColours.SelectedWash else Color.Transparent)
            .clickable(enabled = tool.ready, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
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

/** A small button in the settings strip. */
@Composable
internal fun OptionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(if (selected) HarmenColours.SelectedWash else HarmenColours.PanelRaised)
            .drawBehind {
                if (!selected) return@drawBehind
                drawRect(color = HarmenColours.Accent, style = Stroke(width = 1.dp.toPx()))
            }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            style = HarmenType.ToolLabel,
            color = if (selected) HarmenColours.Text else HarmenColours.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A line of guidance in the settings strip.
 *
 * Placing needs a target, and a tool that waits silently for a tap somewhere
 * particular is a tool that does nothing when you tap the wrong place.
 */
@Composable
private fun BarNote(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = HarmenType.Status,
        color = HarmenColours.TextFaint,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/**
 * Icons are drawn from the Material set rather than a bespoke pack: they match
 * the thin-stroke look the palette asks for, and they keep the tools legible at
 * 18dp on a phone.
 */
internal fun Tool.icon(): ImageVector = when (this) {
    Tool.SELECT -> Icons.Outlined.NearMe
    Tool.WALL -> Icons.Outlined.Straighten
    Tool.LINE -> Icons.Outlined.ShowChart
    Tool.RECTANGLE -> Icons.Outlined.Crop169
    Tool.CIRCLE -> Icons.Outlined.Circle
    Tool.DOOR -> Icons.Outlined.DoorFront
    Tool.WINDOW -> Icons.Outlined.Window
    Tool.ZONE -> Icons.Outlined.Dashboard
    Tool.FILLET -> Icons.Outlined.RoundedCorner
    Tool.CHAMFER -> Icons.Outlined.Details
    Tool.TRIM -> Icons.Outlined.ContentCut
    Tool.EXTEND -> Icons.Outlined.OpenInFull
    Tool.MIRROR -> Icons.Outlined.Flip
    Tool.JOIN -> Icons.Outlined.CallMerge
    Tool.MEASURE -> Icons.Outlined.SquareFoot
    Tool.GRID -> Icons.Outlined.GridOn
    Tool.ARC -> Icons.Outlined.Architecture
    Tool.HATCH -> Icons.Outlined.Texture
    Tool.TEXT -> Icons.Outlined.TextFields
    Tool.SLAB -> Icons.Outlined.Layers
    Tool.COLUMN -> Icons.Outlined.ViewColumn
    Tool.BEAM -> Icons.Outlined.HorizontalRule
    Tool.FURNITURE -> Icons.Outlined.Chair
}

/**
 * What a two-shape tool is waiting for.
 *
 * Worded for the tool rather than in general, because "tap the second one"
 * tells you nothing about which one is which, and for trim and extend the two
 * are not interchangeable: one cuts and one is cut.
 */
@StringRes
private fun Tool.hint(hasFirstPick: Boolean): Int = when (this) {
    Tool.TRIM ->
        if (hasFirstPick) R.string.hint_trim_second else R.string.hint_trim_first
    Tool.EXTEND ->
        if (hasFirstPick) R.string.hint_extend_second else R.string.hint_extend_first
    Tool.MIRROR ->
        if (hasFirstPick) R.string.hint_mirror_second else R.string.hint_mirror_first
    Tool.JOIN ->
        if (hasFirstPick) R.string.hint_join_second else R.string.hint_join_first
    else ->
        if (hasFirstPick) R.string.hint_pick_second else R.string.hint_pick_first
}

/** Tools that keep going from where the last shape ended. */
private val CHAINABLE = setOf(Tool.WALL, Tool.LINE)

/** The wall thicknesses a plan is actually drawn with, in millimetres. */
private val WALL_THICKNESSES_MM = listOf(100.0, 200.0, 300.0)

/** Corner sizes, for rounding off and for cutting off. */
private val CORNER_SIZES_MM = listOf(100.0, 200.0, 300.0, 500.0)

/** Door leaf widths as they are actually ordered. */
private val DOOR_WIDTHS_MM = listOf(700.0, 800.0, 900.0, 1000.0)

/** Window widths, the same way. */
private val WINDOW_WIDTHS_MM = listOf(600.0, 900.0, 1200.0, 1800.0)

/** Column sizes as a concrete frame is actually poured. */
private val COLUMN_SIZES_MM = listOf(250.0, 300.0, 400.0, 500.0)

/** Beam widths, which follow the columns they sit on. */
private val BEAM_WIDTHS_MM = listOf(200.0, 250.0, 300.0, 400.0)

/** Slab thicknesses for a domestic reinforced concrete floor. */
private val SLAB_THICKNESSES_MM = listOf(120.0, 150.0, 180.0, 200.0)
