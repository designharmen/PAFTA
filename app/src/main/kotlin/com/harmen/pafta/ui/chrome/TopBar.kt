package com.harmen.pafta.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmen.pafta.R
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.metrics

/**
 * The identity row: whose project this is, and the two buttons that undo it.
 *
 * It used to carry a DOSYA menu, a DÜZENLE menu, a PAYLAŞ button, a mode
 * switch and five tabs. Not one of them changed anything on the drawing — they
 * only set a value nothing read — so they were five ways to tap and see
 * nothing happen. They are gone, and the row beneath now carries the drawing
 * tools instead.
 */
@Composable
public fun PaftaTopBar(
    projectName: String,
    modifier: Modifier = Modifier,
    /** Tapping the P goes back to the project list. */
    onHome: (() -> Unit)? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false,
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    /** Shows the unsaved-work dot next to the project name. */
    dirty: Boolean = false,
    /** Whether the 3D view is showing instead of the plan. */
    showingModel: Boolean = false,
    onShowModel: (Boolean) -> Unit = {},
    /**
     * Whether there is a plan to switch to at all.
     *
     * A project holding somebody else's model has no plan, so the switch would
     * be a control with one working half — which is the kind of thing this
     * interface is not allowed to show.
     */
    canShowPlan: Boolean = true,
) {
    val unsavedDescription = stringResource(R.string.unsaved_changes)
    val homeDescription = stringResource(R.string.nav_back_to_projects)

    Column(modifier.fillMaxWidth().background(HarmenColours.Panel)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.topBarRowHeight)
                .padding(horizontal = metrics.gutter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The logo is the way home. One mark, one job: there is no second
            // "‹ PROJELER" beside it saying the same thing in words.
            Monogram(
                letter = "P",
                modifier = if (onHome == null) {
                    Modifier
                } else {
                    Modifier
                        .clickable(role = Role.Button, onClick = onHome)
                        .semantics { contentDescription = homeDescription }
                },
            )

            // The title takes the centre by weight, so it stays centred whatever
            // the logo and the two buttons on either side measure.
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
                                .semantics { contentDescription = unsavedDescription },
                        )
                    }
                }
            }

            // Plan or building. Two words rather than an icon, because an
            // icon for "3D" is a cube and a cube is also a room, a box and a
            // block — and this is the one switch on the screen that changes
            // everything below it.
            if (canShowPlan) {
                ViewSwitch(showingModel = showingModel, onShowModel = onShowModel)
                Spacer(Modifier.width(metrics.gutterTight))
            }

            HistoryButton(
                icon = Icons.Outlined.Undo,
                description = stringResource(R.string.action_undo),
                enabled = canUndo,
                onClick = onUndo,
            )
            HistoryButton(
                icon = Icons.Outlined.Redo,
                description = stringResource(R.string.action_redo),
                enabled = canRedo,
                onClick = onRedo,
            )
        }
        HairlineDivider()
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

/** Plan on the left, the building on the right; the one showing is lit. */
@Composable
private fun ViewSwitch(showingModel: Boolean, onShowModel: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(HarmenColours.PanelRaised),
    ) {
        ViewSwitchHalf(
            text = stringResource(R.string.view_plan),
            selected = !showingModel,
            onClick = { onShowModel(false) },
        )
        ViewSwitchHalf(
            text = stringResource(R.string.view_model),
            selected = showingModel,
            onClick = { onShowModel(true) },
        )
    }
}

@Composable
private fun ViewSwitchHalf(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        style = HarmenType.MenuCaps,
        color = if (selected) HarmenColours.Text else HarmenColours.TextMuted,
        modifier = Modifier
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .background(if (selected) HarmenColours.SelectedWash else Color.Transparent)
            .drawBehind {
                if (!selected) return@drawBehind
                drawRect(color = HarmenColours.Accent, style = Stroke(width = 1.dp.toPx()))
            }
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
    )
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
            .size(34.dp)
            .clip(RoundedCornerShape(metrics.cornerRadius))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(7.dp),
    )
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
