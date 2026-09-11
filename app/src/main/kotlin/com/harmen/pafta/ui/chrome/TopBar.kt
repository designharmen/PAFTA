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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmen.pafta.R
import com.harmen.pafta.ui.state.EditMode
import com.harmen.pafta.ui.state.TopMenu
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
    onMenu: (TopMenu) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    /** Shows the unsaved-work dot next to the project name. */
    dirty: Boolean = false,
) {
    Column(modifier.fillMaxWidth().background(HarmenColours.Panel)) {
        IdentityRow(projectName, dirty, onShare, onMenu)
        HairlineDivider()
        TabRow(
            activeTab = activeTab,
            editMode = editMode,
            onTabSelected = onTabSelected,
            onEditModeSelected = onEditModeSelected,
            onBack = onBack,
            canUndo = canUndo,
            canRedo = canRedo,
            onUndo = onUndo,
            onRedo = onRedo,
        )
        HairlineDivider()
    }
}

@Composable
private fun IdentityRow(
    projectName: String,
    dirty: Boolean,
    onShare: () -> Unit,
    onMenu: (TopMenu) -> Unit,
) {
    val unsavedDescription = stringResource(R.string.unsaved_changes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.topBarRowHeight)
            .padding(horizontal = metrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(letter = "P")
        Spacer(Modifier.width(metrics.gutter))
        MenuLabel(stringResource(R.string.menu_file)) { onMenu(TopMenu.FILE) }
        Spacer(Modifier.width(metrics.gutter))
        MenuLabel(stringResource(R.string.menu_edit)) { onMenu(TopMenu.EDIT) }

        // The title takes the centre by weight, so it stays centred whatever the
        // menus and the action on either side measure.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = metrics.gutter),
            ) {
                Text(
                    text = projectName,
                    style = HarmenType.ProjectTitle,
                    color = HarmenColours.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Unsaved work is a single accent dot: the palette allows no
                // second colour, and a word here would compete with the title.
                if (dirty) {
                    Spacer(Modifier.width(7.dp))
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(HarmenColours.Accent)
                            .semantics {
                                contentDescription = unsavedDescription
                            },
                    )
                }
            }
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
            text = stringResource(R.string.action_share),
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
    onBack: (() -> Unit)?,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.topBarTabRowHeight)
            .padding(horizontal = metrics.gutter),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The route back to the library sits in the second row so the first row
        // keeps its specified composition.
        if (onBack != null) {
            BackToLibrary(onBack)
            Spacer(Modifier.width(metrics.gutterTight))
        }

        for (mode in EditMode.entries) {
            ModeLabel(
                text = stringResource(mode.label),
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
                        label = stringResource(tab.label),
                        selected = tab == activeTab,
                        onClick = { onTabSelected(tab) },
                    )
                }
            }
        }

        HistoryButton(Icons.Outlined.Undo, stringResource(R.string.action_undo), canUndo, onUndo)
        HistoryButton(Icons.Outlined.Redo, stringResource(R.string.action_redo), canRedo, onRedo)
    }
}

@Composable
private fun BackToLibrary(onBack: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .clickable(role = Role.Button, onClick = onBack)
            .padding(end = 6.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.ChevronLeft,
            contentDescription = stringResource(R.string.nav_back_to_projects),
            tint = HarmenColours.TextMuted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.nav_projects),
            style = HarmenType.MenuCaps,
            color = HarmenColours.TextMuted,
        )
    }
}

/** Undo and redo. Disabled goes faint rather than disappearing. */
@Composable
private fun HistoryButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = if (enabled) HarmenColours.Text else HarmenColours.TextFaint,
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(6.dp),
    )
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
            .padding(start = 10.dp, end = 10.dp, top = 7.dp, bottom = 9.dp),
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
