package com.harmen.pafta.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmen.pafta.ui.state.EditMode
import com.harmen.pafta.ui.state.ViewTab
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.metrics

/**
 * The two-row application bar.
 *
 * Row one carries identity and document-level actions; row two carries the
 * mode and the layer tab group. Splitting them is what keeps the project name
 * centred and legible on a tablet without the tabs crowding it.
 */
@Composable
public fun PaftaTopBar(
    projectName: String,
    activeTab: ViewTab,
    editMode: EditMode,
    onTabSelected: (ViewTab) -> Unit,
    onEditModeSelected: (EditMode) -> Unit,
    onShare: () -> Unit,
    onMenu: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(HarmenColours.Panel)) {
        IdentityRow(projectName, onShare, onMenu)
        HairlineDivider()
        TabRow(activeTab, editMode, onTabSelected, onEditModeSelected)
        HairlineDivider()
    }
}

@Composable
private fun IdentityRow(
    projectName: String,
    onShare: () -> Unit,
    onMenu: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.topBarRowHeight)
            .padding(horizontal = metrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(letter = "P")
        Spacer(Modifier.width(metrics.gutter))
        MenuLabel("FILE") { onMenu("FILE") }
        Spacer(Modifier.width(metrics.gutter))
        MenuLabel("EDIT") { onMenu("EDIT") }

        // The title takes the centre by weight, so it stays centred whatever the
        // menus and the action on either side measure.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = projectName,
                style = HarmenType.ProjectTitle,
                color = HarmenColours.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = metrics.gutter),
            )
        }

        ShareButton(onShare)
    }
}

/**
 * The logo: a square, thin-bordered box holding a single letter.
 *
 * `P` for PAFTA. The box is the brand mark, the letter is the product, so the
 * two are drawn separately rather than baked into an image asset.
 */
@Composable
public fun Monogram(letter: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(metrics.monogram)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(HarmenColours.Ground)
            .drawBehind {
                drawRect(
                    color = HarmenColours.Accent,
                    style = Stroke(width = 1.dp.toPx()),
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            style = HarmenType.ProjectTitle,
            color = HarmenColours.Text,
        )
    }
}

@Composable
private fun MenuLabel(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = HarmenType.MenuCaps,
        color = HarmenColours.TextMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
private fun ShareButton(onShare: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .drawBehind {
                drawRect(
                    color = HarmenColours.Hairline,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            .clickable(role = Role.Button, onClick = onShare)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = "SHARE",
            style = HarmenType.MenuCaps,
            color = HarmenColours.Text,
        )
    }
}

@Composable
private fun TabRow(
    activeTab: ViewTab,
    editMode: EditMode,
    onTabSelected: (ViewTab) -> Unit,
    onEditModeSelected: (EditMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.topBarTabRowHeight)
            .padding(horizontal = metrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (mode in EditMode.entries) {
            ModeLabel(
                text = mode.label,
                selected = mode == editMode,
                onClick = { onEditModeSelected(mode) },
            )
            Spacer(Modifier.width(metrics.gutterTight))
        }

        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (tab in ViewTab.entries) {
                    Tab(
                        label = tab.label,
                        selected = tab == activeTab,
                        onClick = { onTabSelected(tab) },
                    )
                }
            }
        }

        // Balances the mode labels so the tab group reads as centred.
        Spacer(Modifier.width(40.dp))
    }
}

@Composable
private fun ModeLabel(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = HarmenType.MenuCaps,
        color = if (selected) HarmenColours.Text else HarmenColours.TextFaint,
        modifier = Modifier
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

/**
 * A tab in the layer group.
 *
 * The active tab gets both an accent wash and a 2dp accent underline — the wash
 * alone is too quiet against the panel, and the underline alone reads as a
 * progress bar.
 */
@Composable
private fun Tab(label: String, selected: Boolean, onClick: () -> Unit) {
    val underline = 2.dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .background(if (selected) HarmenColours.AccentWash else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .drawBehind {
                if (!selected) return@drawBehind
                val thickness = underline.toPx()
                drawRect(
                    color = HarmenColours.Accent,
                    topLeft = Offset(0f, size.height - thickness),
                    size = androidx.compose.ui.geometry.Size(size.width, thickness),
                )
            }
            .padding(horizontal = 10.dp, top = 7.dp, bottom = 9.dp),
    ) {
        Text(
            text = label,
            style = HarmenType.Tab,
            color = if (selected) HarmenColours.Accent else HarmenColours.TextMuted,
        )
    }
}

/** A one-pixel rule in the panel's border colour. */
@Composable
public fun HairlineDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(metrics.hairline)
            .background(HarmenColours.Hairline),
    )
}

/** A vertical one-pixel rule, for the column edges. */
@Composable
public fun VerticalHairline(modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(metrics.hairline)
            .background(HarmenColours.Hairline),
    )
}
