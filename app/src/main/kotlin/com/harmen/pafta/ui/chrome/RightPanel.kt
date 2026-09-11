package com.harmen.pafta.ui.chrome

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.harmen.pafta.R
import com.harmen.pafta.project.AnnotationKind
import com.harmen.pafta.project.LayerState
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
            .background(if (material.selected) HarmenColours.AccentWash else Color.Transparent)
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
            color = if (material.selected) HarmenColours.Accent else HarmenColours.Text,
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
            .background(if (selected) HarmenColours.AccentWash else Color.Transparent)
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
