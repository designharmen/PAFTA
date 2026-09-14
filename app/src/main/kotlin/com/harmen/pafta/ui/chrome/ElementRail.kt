package com.harmen.pafta.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.harmen.pafta.R
import com.harmen.pafta.ui.state.ELEMENT_TOOLS
import com.harmen.pafta.ui.state.Tool
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.metrics

/**
 * The building elements, down the right-hand edge.
 *
 * Wall, door, window, room, floor, furniture: the things a building is made of,
 * as opposed to the lines and corners it is drawn with. They carry thickness,
 * height and material, and they are what the layer panel counts — so they get
 * their own column, next to the panel that describes them.
 *
 * Döşeme and Mobilya are shown faint and do not respond: they are the next
 * phase, and a button that answers a tap with nothing is worse than a button
 * that says plainly it is not ready.
 */
@Composable
public fun ElementRail(
    activeTool: Tool,
    onToolSelected: (Tool) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** Whether the layer and properties panel is open beside this rail. */
    panelOpen: Boolean = false,
    onTogglePanel: () -> Unit = {},
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
        PanelHandle(open = panelOpen, onClick = onTogglePanel)
        HairlineDivider(Modifier.padding(vertical = 4.dp))

        if (!compact) {
            Text(
                text = stringResource(R.string.rail_elements),
                style = HarmenType.Status,
                color = HarmenColours.TextFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
            )
        }

        for (tool in ELEMENT_TOOLS) {
            ToolButton(
                tool = tool,
                selected = tool == activeTool,
                compact = compact,
                onClick = { onToolSelected(tool) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/**
 * The handle that opens and shuts the panel.
 *
 * It points the way the panel will go: a chevron towards the sheet while it is
 * shut and it is about to come out, and back towards the edge while it is open
 * and about to go away.
 */
@Composable
private fun PanelHandle(open: Boolean, onClick: () -> Unit) {
    val description = stringResource(if (open) R.string.panel_close else R.string.panel_open)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(if (open) HarmenColours.SelectedWash else HarmenColours.PanelRaised)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
    ) {
        Icon(
            imageVector = if (open) Icons.Outlined.ChevronRight else Icons.Outlined.ChevronLeft,
            contentDescription = description,
            tint = if (open) HarmenColours.Accent else HarmenColours.TextMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = stringResource(R.string.tool_layers),
            style = HarmenType.ToolLabel,
            color = if (open) HarmenColours.Text else HarmenColours.TextMuted,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
