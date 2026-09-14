package com.harmen.pafta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.harmen.pafta.R
import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.ui.chrome.HairlineDivider
import com.harmen.pafta.ui.chrome.PaftaTopBar
import com.harmen.pafta.project.DrawnShape
import com.harmen.pafta.project.OpeningKind
import com.harmen.pafta.project.dimensions
import com.harmen.pafta.project.openingPlans
import com.harmen.pafta.project.zonePlans
import com.harmen.pafta.project.lengthMm
import com.harmen.pafta.project.wallBands
import com.harmen.pafta.units.AreaUnit
import com.harmen.pafta.units.formatArea
import com.harmen.pafta.units.formatLength
import com.harmen.pafta.ui.chrome.RightPanel
import com.harmen.pafta.ui.chrome.ToolRail
import com.harmen.pafta.ui.chrome.VerticalHairline
import com.harmen.pafta.ui.state.DRAWING_TOOLS
import com.harmen.pafta.ui.state.EditorState
import com.harmen.pafta.ui.state.EditorViewModel
import com.harmen.pafta.ui.state.TopMenu
import com.harmen.pafta.ui.state.UiError
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
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
    /** What the opening view is framed around; the drawing's own extent if null. */
    fitBounds: Aabb? = null,
    /** Something the editor has to tell the user, or null. */
    error: UiError? = null,
    onDismissError: () -> Unit = {},
    roomLabels: List<RoomLabel> = emptyList(),
    onBack: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onMenu: (TopMenu) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Turkish words the view model needs but must never contain: a literal in
    // Kotlin is how English reaches a Turkish screen.
    val newRoomName = stringResource(R.string.zone_default_name)
    val openRoomNote = stringResource(R.string.zone_not_closed)
    LaunchedEffect(newRoomName) { viewModel.defaultZoneName = newRoomName }

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
                        lastText = state.lastText,
                        onToolSelected = viewModel::selectTool,
                        compact = compact,
                        measureMode = state.measureMode,
                        pendingPickCount = state.pendingPicks.size,
                        onMeasureModeSelected = viewModel::selectMeasureMode,
                        onFinishMeasurement = viewModel::finishMeasurement,
                        onUndoPick = viewModel::undoPick,
                        onCancelMeasurement = viewModel::cancelMeasurement,
                        wallThicknessMm = state.wallThicknessMm,
                        onWallThicknessSelected = viewModel::selectWallThickness,
                        wallMaterial = state.wallMaterial,
                        onWallMaterialSelected = viewModel::selectWallMaterial,
                        selectedShapeId = state.selectedShapeId,
                        onDeleteSelected = viewModel::deleteSelected,
                        onFinishChain = viewModel::finishChain,
                        doorWidthMm = state.doorWidthMm,
                        windowWidthMm = state.windowWidthMm,
                        onOpeningWidthSelected = viewModel::setOpeningWidth,
                        hasFirstPick = state.firstPickId != null,
                        filletRadiusMm = state.filletRadiusMm,
                        onFilletRadiusSelected = viewModel::setFilletRadius,
                        chamferMm = state.chamferMm,
                        onChamferSizeSelected = viewModel::setChamferSize,
                    )
                    VerticalHairline(Modifier.fillMaxHeight())

                    // Worked out once per change and used by both the drawing and
                    // the panel, so the two can never disagree about a room.
                    val zones = remember(state.shapes) { state.shapes.zonePlans() }

                    PlanViewport(
                        drawing = drawing,
                        layers = state.layers,
                        measurements = state.measurements,
                        roomLabels = roomLabels,
                        display = state.display,
                        unitLabel = state.unitLabel,
                        gridVisible = state.gridVisible,
                        gridSpacingMm = state.gridSpacingMm,
                        fitBounds = fitBounds,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        pendingPicks = state.pendingPicks,
                        onPick = viewModel::onCanvasPick,
                        drawEnabled = state.activeTool in DRAWING_TOOLS,
                        onDrawBegin = viewModel::beginDrag,
                        onDrawMove = viewModel::updateDrag,
                        onDrawEnd = viewModel::endDrag,
                        onDrawCancel = viewModel::cancelDrag,
                        onGrab = viewModel::beginMove,
                        onMoveTo = viewModel::updateMove,
                        onMoveEnd = viewModel::endMove,
                        preview = state.preview?.toEntities().orEmpty(),
                        previewLabel = state.preview?.lengthMm?.let {
                            formatLength(it, state.display.lengthFormat)
                        },
                        // Walls go through their own list because they are
                        // filled bands with closed corners, not plain outlines.
                        drawn = remember(state.shapes) {
                            state.shapes
                                .filterNot { it is DrawnShape.Wall }
                                .flatMap { it.toEntities() }
                        },
                        walls = remember(state.shapes) { state.shapes.wallBands() },
                        openings = remember(state.shapes) { state.shapes.openingPlans() },
                        zones = zones,
                        zoneLabel = { plan ->
                            buildList {
                                if (plan.name.isNotBlank()) add(plan.name)
                                add(
                                    if (plan.isOpen) {
                                        openRoomNote
                                    } else {
                                        formatArea(plan.areaMm2, AreaUnit.SQUARE_METRE, decimals = 2)
                                    },
                                )
                            }
                        },
                        // The shape a two-shape tool is holding is shown picked
                        // out just as a selection is: otherwise the first tap
                        // of the pair looks like nothing happened.
                        highlightedWallId = state.firstPickId ?: state.selectedShapeId,
                        highlighted = state.shapes
                            .firstOrNull {
                                it.id == (state.firstPickId ?: state.selectedShapeId) &&
                                    it !is DrawnShape.Wall
                            }
                            ?.toEntities()
                            .orEmpty(),
                        snapAt = state.snapAt,
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
                            selectedShapeId = state.selectedShapeId,
                            selectedDimensions = state.shapes
                                .firstOrNull { it.id == state.selectedShapeId }
                                ?.dimensions()
                                .orEmpty(),
                            onSelectedDimensionChanged = viewModel::setSelectedDimension,
                            onDuplicateSelected = viewModel::duplicateSelected,
                            selectedZone = zones.firstOrNull { it.id == state.selectedShapeId },
                            onSelectedNameChanged = viewModel::setSelectedName,
                            selectedSwing = (
                                state.shapes.firstOrNull { it.id == state.selectedShapeId }
                                    as? DrawnShape.Opening
                                )?.takeIf { it.kind == OpeningKind.DOOR }?.swing,
                            onSwingSelected = viewModel::setDoorSwing,
                            canOffset = state.shapes
                                .firstOrNull { it.id == state.selectedShapeId }
                                ?.let { it is DrawnShape.Wall || it is DrawnShape.Line }
                                ?: false,
                            onOffset = viewModel::offsetSelected,
                            onTurn = viewModel::turnSelected,
                        )
                    }
                }
                HairlineDivider()
            }

            // Over the plan rather than beside it: the answer to "why did
            // nothing happen when I tapped there" has to appear where the user
            // was looking, which is the drawing.
            if (error != null) {
                EditorNote(
                    text = error.mesaj(),
                    onDismiss = onDismissError,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                )
            }
        }
    }
}

/**
 * What the editor has to say, said over the drawing.
 *
 * It stays until it is tapped rather than fading on a timer: the sentences here
 * tell the user what to do differently, and one that disappears while it is
 * being read is worse than none.
 */
@Composable
private fun EditorNote(text: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(HarmenColours.PanelRaised)
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Text(
            text = text,
            style = HarmenType.Body,
            color = HarmenColours.Text,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.action_dismiss_note),
            style = HarmenType.PropertyKey,
            color = HarmenColours.Accent,
        )
    }
}

/** Below this width the tool rail drops its captions. */
private val COMPACT_WIDTH = 720.dp

/** Below this width the inspector is hidden rather than squeezed. */
private val INSPECTOR_WIDTH = 600.dp
