package com.harmen.pafta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harmen.pafta.R
import com.harmen.pafta.project.LayerState
import com.harmen.pafta.ui.chrome.HairlineDivider
import com.harmen.pafta.ui.chrome.PaftaTopBar
import com.harmen.pafta.ui.chrome.VerticalHairline
import com.harmen.pafta.ui.state.ModelState
import com.harmen.pafta.ui.state.ModelViewModel
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.metrics
import com.harmen.pafta.ui.viewport.ModelViewport

/**
 * A project that holds somebody else's 3D model, rather than a drawing.
 *
 * Nothing on this screen edits anything, and that is the whole shape of it: an
 * imported model is a reference. You turn it round, you look at it, you hide
 * the parts in the way. PAFTA never rewrites the file it was given, and a model
 * is the clearest case of that rule there is — so there are no tools here, and
 * no row of buttons that would answer a tap with nothing.
 */
@Composable
public fun ModelScreen(
    state: ModelState,
    viewModel: ModelViewModel,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val document = state.document
    // Shown only when the model has parts worth separating: a single-part model
    // has nothing to choose between, so the panel would be a list of one.
    val hasParts = state.layers.size > 1
    var panelOpen by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(HarmenColours.Ground)) {
        PaftaTopBar(
            projectName = state.projectName,
            onHome = onBack,
            // No plan to switch to, so no switch: a control with one working
            // half is worse than no control.
            canShowPlan = false,
        )

        Row(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.weight(1f).fillMaxHeight()) {
                ModelViewport(
                    faces = remember(document) { document?.faces.orEmpty() },
                    layers = state.layers,
                )

                when {
                    state.loading -> ModelNote(
                        text = stringResource(R.string.model_loading),
                        modifier = Modifier.align(Alignment.Center),
                    )

                    document == null -> Unit

                    document.isTruncated -> ModelNote(
                        text = stringResource(
                            R.string.model_truncated,
                            document.facesInFile,
                            document.faces.size,
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                    )

                    else -> ModelNote(
                        text = stringResource(R.string.model_hint),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    )
                }
            }

            if (hasParts) {
                if (panelOpen) {
                    VerticalHairline(Modifier.fillMaxHeight())
                    PartsPanel(
                        layers = state.layers,
                        onVisibilityToggled = viewModel::setLayerVisible,
                    )
                }
                VerticalHairline(Modifier.fillMaxHeight())
                PartsHandle(open = panelOpen) { panelOpen = !panelOpen }
            }
        }
        HairlineDivider()
    }
}

/** The model's parts, each with an eye to turn it off by. */
@Composable
private fun PartsPanel(layers: List<LayerState>, onVisibilityToggled: (String, Boolean) -> Unit) {
    Column(
        modifier = Modifier
            .width(metrics.rightPanelWidth)
            .fillMaxHeight()
            .background(HarmenColours.Panel)
            .verticalScroll(rememberScrollState())
            .padding(vertical = metrics.gutterTight, horizontal = metrics.gutterTight),
    ) {
        Text(
            text = "[${stringResource(R.string.model_parts)}]",
            style = HarmenType.SectionTitle,
            color = HarmenColours.TextMuted,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        for (layer in layers) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(metrics.cornerRadius))
                    .clickable(role = Role.Switch) {
                        onVisibilityToggled(layer.id, !layer.visible)
                    }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
            ) {
                Icon(
                    imageVector = if (layer.visible) {
                        Icons.Outlined.Visibility
                    } else {
                        Icons.Outlined.VisibilityOff
                    },
                    contentDescription = null,
                    tint = if (layer.visible) HarmenColours.Text else HarmenColours.TextFaint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = layer.name,
                    style = HarmenType.PropertyKey,
                    color = if (layer.visible) HarmenColours.Text else HarmenColours.TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The narrow column that opens and shuts the parts list. */
@Composable
private fun PartsHandle(open: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(metrics.toolRailWidth)
            .fillMaxHeight()
            .background(HarmenColours.Panel)
            .padding(vertical = metrics.gutterTight),
    ) {
        Text(
            text = stringResource(R.string.model_parts),
            style = HarmenType.ToolLabel,
            color = if (open) HarmenColours.Text else HarmenColours.TextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(metrics.cornerRadius))
                .background(
                    if (open) HarmenColours.SelectedWash else HarmenColours.PanelRaised,
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = 10.dp, horizontal = 2.dp),
        )
    }
}

/** A quiet line over the model: what to do, or what could not be shown. */
@Composable
private fun ModelNote(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = HarmenType.Status,
        color = HarmenColours.TextMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(max = 480.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(HarmenColours.Panel.copy(alpha = 0.85f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
