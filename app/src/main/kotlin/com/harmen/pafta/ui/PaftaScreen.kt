package com.harmen.pafta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.ui.chrome.HairlineDivider
import com.harmen.pafta.ui.chrome.PaftaTopBar
import com.harmen.pafta.ui.chrome.RightPanel
import com.harmen.pafta.ui.chrome.ToolRail
import com.harmen.pafta.ui.chrome.VerticalHairline
import com.harmen.pafta.ui.state.EditorState
import com.harmen.pafta.ui.state.EditorViewModel
import com.harmen.pafta.ui.state.TopMenu
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.LocalCompactLayout
import com.harmen.pafta.ui.viewport.PlanViewport
import com.harmen.pafta.ui.viewport.RoomLabel

/**
 * The editor screen: top bar, tool rail, viewport, inspector.
 *
 * The three-column layout is the tablet case. Below [COMPACT_WIDTH] the rail
 * narrows to icons only and the inspector collapses off-screen, which keeps the
 * drawing — the thing the user came for — from being squeezed into a gutter on a
 * phone.
 */
@Composable
public fun PaftaScreen(
    state: EditorState,
    viewModel: EditorViewModel,
    drawing: DxfDrawing,
    roomLabels: List<RoomLabel> = emptyList(),
    onBack: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onMenu: (TopMenu) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize().background(HarmenColours.Ground)) {
        val compact = maxWidth < COMPACT_WIDTH
        val showInspector = maxWidth >= INSPECTOR_WIDTH

        CompositionLocalProvider(LocalCompactLayout provides compact) {
            Column(Modifier.fillMaxSize()) {
                PaftaTopBar(
                    projectName = state.projectName,
                    activeTab = state.activeTab,
                    editMode = state.editMode,
                    onTabSelected = viewModel::selectTab,
                    onEditModeSelected = viewModel::selectEditMode,
                    onShare = onShare,
                    onMenu = onMenu,
                    onBack = onBack,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    dirty = state.dirty,
                )

                Row(Modifier.fillMaxWidth().weight(1f)) {
                    ToolRail(
                        activeTool = state.activeTool,
                        dimensionPresets = state.dimensionPresets,
                        selectedPreset = state.selectedPreset,
                        lastText = state.lastText,
                        onToolSelected = viewModel::selectTool,
                        onPresetSelected = { viewModel.selectDimensionPreset(it) },
                        compact = compact,
                    )
                    VerticalHairline(Modifier.fillMaxHeight())

                    PlanViewport(
                        drawing = drawing,
                        layers = state.layers,
                        measurements = state.measurements,
                        roomLabels = roomLabels,
                        display = state.display,
                        unitLabel = state.unitLabel,
                        gridVisible = state.gridVisible,
                        gridSpacingMm = state.gridSpacingMm,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )

                    if (showInspector) {
                        VerticalHairline(Modifier.fillMaxHeight())
                        RightPanel(
                            layers = state.layers,
                            materials = state.materials,
                            properties = state.properties,
                            selectionTitle = state.selectionTitle,
                            activeAnnotationTool = state.annotationTool,
                            onLayerVisibilityToggled = viewModel::setLayerVisible,
                            onLayerOpacityChanged = viewModel::setLayerOpacity,
                            onMaterialSelected = viewModel::selectMaterial,
                            onAnnotationToolSelected = { viewModel.selectAnnotationTool(it) },
                        )
                    }
                }
                HairlineDivider()
            }
        }
    }
}

/** Below this width the tool rail drops its captions. */
private val COMPACT_WIDTH = 720.dp

/** Below this width the inspector is hidden rather than squeezed. */
private val INSPECTOR_WIDTH = 600.dp
