package com.harmen.pafta.ui.chrome

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmen.pafta.R
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.DoorSwing
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.project.ShapeDimension
import com.harmen.pafta.project.ZonePlan
import com.harmen.pafta.units.formatArea
import com.harmen.pafta.ui.adi
import com.harmen.pafta.ui.state.MaterialSwatch
import com.harmen.pafta.ui.state.PropertyRow
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.metrics

/**
 * The right-hand inspector.
 *
 * Section headings are written in square brackets — `[Layers Palette]` — which
 * is the drawing-sheet convention the design calls for, and it distinguishes a
 * panel heading from a label inside the panel without needing a second type
 * size or a rule.
 */
@Composable
public fun RightPanel(
    layers: List<LayerState>,
    materials: List<MaterialSwatch>,
    properties: List<PropertyRow>,
    @StringRes selectionTitle: Int?,
    activeAnnotationTool: AnnotationKind?,
    onLayerVisibilityToggled: (String, Boolean) -> Unit,
    onLayerOpacityChanged: (String, Double) -> Unit,
    onMaterialSelected: (String) -> Unit,
    onAnnotationToolSelected: (AnnotationKind) -> Unit,
    modifier: Modifier = Modifier,
    /** The selected shape, so its measurements can be corrected by typing. */
    selectedShapeId: String? = null,
    /** Which measurements it has, and what they currently are, in millimetres. */
    selectedDimensions: Map<ShapeDimension, Double> = emptyMap(),
    onSelectedDimensionChanged: (ShapeDimension, Double) -> Unit = { _, _ -> },
    /** Makes a second component from the selected one, to be edited from there. */
    onDuplicateSelected: () -> Unit = {},
    /** The selected room, when one is selected: its name, and what it measures. */
    selectedZone: ZonePlan? = null,
    onSelectedNameChanged: (String) -> Unit = {},
    /** Which way the selected door opens; null when what is selected is not a door. */
    selectedSwing: DoorSwing? = null,
    onSwingSelected: (DoorSwing) -> Unit = {},
) {
    Column(
        modifier = modifier
            .width(metrics.rightPanelWidth)
            .fillMaxHeight()
            .background(HarmenColours.Panel)
            .verticalScroll(rememberScrollState())
            .padding(vertical = metrics.gutterTight),
    ) {
        Section(R.string.panel_layers) {
            if (layers.isEmpty()) {
                EmptyNote(R.string.panel_empty_layers)
            } else {
                for (layer in layers) {
                    LayerRow(
                        layer = layer,
                        onVisibilityToggled = { onLayerVisibilityToggled(layer.id, it) },
                        onOpacityChanged = { onLayerOpacityChanged(layer.id, it) },
                    )
                }
            }
        }

        Section(R.string.panel_materials) {
            if (materials.isEmpty()) {
                EmptyNote(R.string.panel_empty_materials)
            } else {
                for (material in materials) {
                    MaterialRow(material) { onMaterialSelected(material.id) }
                }
            }
        }

        Section(R.string.panel_properties) {
            if (properties.isEmpty()) {
                EmptyNote(R.string.panel_empty_properties)
            } else {
                selectionTitle?.let {
                    Text(
                        text = stringResource(it),
                        style = HarmenType.Body,
                        color = HarmenColours.Text,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                for (row in properties) {
                    PropertyTableRow(row)
                }
            }
        }

        if (selectedShapeId != null && (selectedDimensions.isNotEmpty() || selectedZone != null)) {
            Section(R.string.panel_selected) {
                for ((which, value) in selectedDimensions) {
                    MeasurementField(
                        key = selectedShapeId + which.name,
                        label = which.label,
                        millimetres = value,
                        onChanged = { onSelectedDimensionChanged(which, it) },
                    )
                }

                if (selectedZone != null) {
                    NameField(
                        key = selectedShapeId,
                        name = selectedZone.name,
                        onNameChanged = onSelectedNameChanged,
                    )
                    // Read-only on purpose: a room's area is what the walls
                    // leave, and typing over it would be typing over the plan.
                    PropertyTableRow(
                        PropertyRow(
                            R.string.zone_area,
                            if (selectedZone.isOpen) {
                                stringResource(R.string.zone_not_closed)
                            } else {
                                formatArea(selectedZone.areaMm2)
                            },
                            numeric = !selectedZone.isOpen,
                        ),
                    )
                }

                if (selectedSwing != null) {
                    SwingChooser(selectedSwing, onSwingSelected)
                }

                PanelButton(R.string.action_duplicate, onDuplicateSelected)
            }
        }

        Section(R.string.panel_annotations) {
            for (kind in ANNOTATION_TOOLS) {
                AnnotationToolRow(
                    kind = kind,
                    selected = kind == activeAnnotationTool,
                    onClick = { onAnnotationToolSelected(kind) },
                )
            }
        }
    }
}

/**
 * One of the selected shape's measurements, as a number the user can retype.
 *
 * This is what turns a rough drag into a measured plan: the wall is drawn by
 * hand, then told that it is 3600mm long, 100mm thick and 2800mm tall. The
 * field shows plain millimetres with no unit inside it, because a keyboard on a
 * tablet makes `3600` easy and `3600mm` a fight.
 */
@Composable
private fun MeasurementField(
    key: String,
    @StringRes label: Int,
    millimetres: Double,
    onChanged: (Double) -> Unit,
) {
    // Re-seeded whenever the selection or the value changes, so the field always
    // shows the shape the user is actually looking at.
    var text by remember(key, millimetres) { mutableStateOf(Math.round(millimetres).toString()) }
    val commit = {
        text.trim().replace(',', '.').toDoubleOrNull()?.let { if (it > 0) onChanged(it) }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(label),
            style = HarmenType.PropertyKey,
            color = HarmenColours.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .width(92.dp)
                .clip(RoundedCornerShape(metrics.cornerRadius))
                .background(HarmenColours.PanelRaised)
                .drawBehind {
                    drawRect(color = HarmenColours.Accent, style = Stroke(width = 1.dp.toPx()))
                }
                .padding(horizontal = 6.dp, vertical = 7.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { entered -> text = entered.filter { it.isDigit() || it == '.' || it == ',' } },
                singleLine = true,
                textStyle = HarmenType.Numeric.copy(
                    color = HarmenColours.Text,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(HarmenColours.Accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.unit_millimetre),
            style = HarmenType.PropertyKey,
            color = HarmenColours.TextFaint,
        )
    }
}

/**
 * The selected room's name.
 *
 * A plain text field rather than a number one: a room is called `Salon`, and a
 * keyboard that only offers digits would make that impossible to type.
 */
@Composable
private fun NameField(key: String, name: String, onNameChanged: (String) -> Unit) {
    var text by remember(key, name) { mutableStateOf(name) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.panel_zone_name),
            style = HarmenType.PropertyKey,
            color = HarmenColours.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .width(132.dp)
                .clip(RoundedCornerShape(metrics.cornerRadius))
                .background(HarmenColours.PanelRaised)
                .drawBehind {
                    drawRect(color = HarmenColours.Accent, style = Stroke(width = 1.dp.toPx()))
                }
                .padding(horizontal = 6.dp, vertical = 7.dp),
        ) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = HarmenType.Body.copy(
                    color = HarmenColours.Text,
                    textAlign = TextAlign.End,
                ),
                cursorBrush = SolidColor(HarmenColours.Accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onNameChanged(text.trim()) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * Which jamb the door hangs on and which way it opens.
 *
 * Four buttons rather than two settings, because this is something an architect
 * decides by looking at the plan, and four things to look at beats two things
 * to reason about.
 */
@Composable
private fun SwingChooser(selected: DoorSwing, onSelected: (DoorSwing) -> Unit) {
    Text(
        text = stringResource(R.string.swing_title),
        style = HarmenType.PropertyKey,
        color = HarmenColours.TextMuted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
    )
    Column(Modifier.fillMaxWidth()) {
        for (row in DoorSwing.entries.chunked(2)) {
            Row(Modifier.fillMaxWidth()) {
                for (swing in row) {
                    Text(
                        text = stringResource(swing.label),
                        style = HarmenType.PropertyKey,
                        color = if (swing == selected) HarmenColours.Text else HarmenColours.TextMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp, vertical = 3.dp)
                            .clip(RoundedCornerShape(metrics.cornerRadius))
                            .background(
                                if (swing == selected) {
                                    HarmenColours.SelectedWash
                                } else {
                                    HarmenColours.PanelRaised
                                },
                            )
                            .clickable(role = Role.Button) { onSelected(swing) }
                            .height(44.dp)
                            .padding(vertical = 13.dp),
                    )
                }
            }
        }
    }
}

/** The Turkish name of a swing direction. */
private val DoorSwing.label: Int
    @StringRes get() = when (this) {
        DoorSwing.LEFT_IN -> R.string.swing_left_in
        DoorSwing.LEFT_OUT -> R.string.swing_left_out
        DoorSwing.RIGHT_IN -> R.string.swing_right_in
        DoorSwing.RIGHT_OUT -> R.string.swing_right_out
    }

/** The Turkish word for a measurement. Never a literal in code. */
private val ShapeDimension.label: Int
    @StringRes get() = when (this) {
        ShapeDimension.LENGTH -> R.string.dimension_length
        ShapeDimension.THICKNESS -> R.string.dimension_thickness
        ShapeDimension.HEIGHT -> R.string.dimension_height
        ShapeDimension.WIDTH -> R.string.dimension_width
        ShapeDimension.DEPTH -> R.string.dimension_depth
        ShapeDimension.DIAMETER -> R.string.dimension_diameter
        ShapeDimension.SILL -> R.string.dimension_sill
    }

/** A full-width action inside a panel section. */
@Composable
private fun PanelButton(@StringRes label: Int, onClick: () -> Unit) {
    Text(
        text = stringResource(label),
        style = HarmenType.PropertyKey,
        color = HarmenColours.Accent,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(HarmenColours.PanelRaised)
            .clickable(role = Role.Button, onClick = onClick)
            // A finger needs 44dp; the row above it is a text field and the two
            // must not be mistaken for each other.
            .height(44.dp)
            .padding(vertical = 13.dp),
    )
}

/** A bracketed panel section. */
@Composable
private fun Section(@StringRes title: Int, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = metrics.gutter)) {
        Text(
            // The bracketed heading is the drawing-sheet convention the design
            // asks for; only the word inside is translated.
            text = "[${stringResource(title)}]",
            style = HarmenType.SectionTitle,
            color = HarmenColours.TextMuted,
            modifier = Modifier.padding(horizontal = metrics.gutter, vertical = 7.dp),
        )
        Column(Modifier.fillMaxWidth().padding(horizontal = metrics.gutterTight)) { content() }
    }
}

@Composable
private fun EmptyNote(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = HarmenType.PropertyKey,
        color = HarmenColours.TextFaint,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

/**
 * A layer row: colour chip, name, opacity percentage.
 *
 * Tapping the row toggles visibility; a hidden layer keeps its percentage on
 * screen but drops to the faint text colour, so the user can see what a hidden
 * layer would come back as.
 */
@Composable
private fun LayerRow(
    layer: LayerState,
    onVisibilityToggled: (Boolean) -> Unit,
    onOpacityChanged: (Double) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .clickable(role = Role.Checkbox) { onVisibilityToggled(!layer.visible) }
            .padding(horizontal = 4.dp, vertical = 5.dp),
    ) {
        ColourChip(layer.colour, dimmed = !layer.visible)
        Spacer(Modifier.width(8.dp))
        Text(
            text = layer.name,
            style = HarmenType.Body,
            color = if (layer.visible) HarmenColours.Text else HarmenColours.TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.layer_opacity_percent, layer.opacityPercent),
            style = HarmenType.Numeric,
            color = if (layer.visible) HarmenColours.TextMuted else HarmenColours.TextFaint,
        )
    }
    OpacityTrack(
        opacity = layer.opacity,
        enabled = layer.visible,
        onOpacityChanged = onOpacityChanged,
    )
}

/**
 * A 9dp square in the layer's colour.
 *
 * A layer with no colour override shows the accent outline rather than a filled
 * chip, so "inherits from the file" is visibly different from "set to copper".
 */
@Composable
private fun ColourChip(colour: String?, dimmed: Boolean) {
    val parsed = parseHexColour(colour)
    Box(
        Modifier
            .size(9.dp)
            .background(
                if (parsed != null) {
                    if (dimmed) parsed.copy(alpha = 0.35f) else parsed
                } else {
                    Color.Transparent
                },
            )
            .drawBehind {
                if (parsed != null) return@drawBehind
                drawRect(
                    color = if (dimmed) HarmenColours.TextFaint else HarmenColours.Accent,
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
    )
}

/**
 * A thin opacity track under each layer.
 *
 * It is a tap target rather than a Material slider: at this row height a slider
 * thumb would dominate the panel, and layer opacity is set coarsely in practice.
 */
@Composable
private fun OpacityTrack(
    opacity: Double,
    enabled: Boolean,
    onOpacityChanged: (Double) -> Unit,
) {
    val steps = listOf(0.25, 0.5, 0.75, 1.0)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 21.dp, end = 4.dp, bottom = 6.dp),
    ) {
        for (step in steps) {
            val filled = enabled && opacity >= step - 0.001
            Box(
                Modifier
                    .weight(1f)
                    .height(2.dp)
                    .background(
                        when {
                            filled -> HarmenColours.Accent
                            enabled -> HarmenColours.Hairline
                            else -> HarmenColours.Hairline.copy(alpha = 0.5f)
                        },
                    )
                    .clickable(enabled = enabled, role = Role.Button) { onOpacityChanged(step) },
            )
        }
    }
}

/** A material swatch row: colour square plus name. */
@Composable
private fun MaterialRow(material: MaterialSwatch, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(if (material.selected) HarmenColours.SelectedWash else Color.Transparent)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 5.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(parseHexColour(material.colour) ?: HarmenColours.PanelRaised)
                .drawBehind {
                    drawRect(
                        color = if (material.selected) HarmenColours.Accent else HarmenColours.Hairline,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                },
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(material.name),
            style = HarmenType.Body,
            // The swatch outline carries the selection in bronze; the name
            // stays ivory, because bronze text at this size is out of bounds.
            color = HarmenColours.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One `key  value` line of the properties table. */
@Composable
private fun PropertyTableRow(row: PropertyRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
    ) {
        Text(
            text = stringResource(row.key),
            style = HarmenType.PropertyKey,
            color = HarmenColours.TextMuted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = row.value,
            style = if (row.numeric) HarmenType.Numeric else HarmenType.Body,
            color = HarmenColours.Text,
            maxLines = 1,
        )
    }
}

@Composable
private fun AnnotationToolRow(kind: AnnotationKind, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) HarmenColours.Accent else HarmenColours.TextMuted
    val label = kind.adi()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(if (selected) HarmenColours.SelectedWash else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Icon(
            imageVector = kind.icon(),
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = HarmenType.Body, color = tint)
    }
}

/** The annotation tools offered in the panel, in design order. */
private val ANNOTATION_TOOLS = listOf(
    AnnotationKind.TEXT,
    AnnotationKind.CALLOUT,
    AnnotationKind.STAMP,
    AnnotationKind.COMMENT,
    AnnotationKind.ARROW,
    AnnotationKind.PIN,
    AnnotationKind.DIMENSION,
)

private fun AnnotationKind.icon(): ImageVector = when (this) {
    AnnotationKind.TEXT -> Icons.Outlined.TextFields
    AnnotationKind.ARROW -> Icons.Outlined.NorthEast
    AnnotationKind.PIN -> Icons.Outlined.Place
    AnnotationKind.DIMENSION -> Icons.Outlined.Straighten
    AnnotationKind.CALLOUT -> Icons.Outlined.ChatBubbleOutline
    AnnotationKind.STAMP -> Icons.Outlined.VerifiedUser
    AnnotationKind.COMMENT -> Icons.Outlined.Comment
}

/**
 * Parses `#RRGGBB` / `#AARRGGBB`. Returns null for anything else so the caller
 * can fall back rather than showing a wrong colour.
 */
internal fun parseHexColour(hex: String?): Color? {
    val text = hex?.trim()?.removePrefix("#") ?: return null
    return when (text.length) {
        6 -> text.toLongOrNull(16)?.let { Color(it or 0xFF000000L) }
        8 -> text.toLongOrNull(16)?.let { Color(it) }
        else -> null
    }
}
