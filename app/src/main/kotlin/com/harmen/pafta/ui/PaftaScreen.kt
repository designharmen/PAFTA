package com.harmen.pafta.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.harmen.pafta.R
import com.harmen.pafta.dxf.DxfDrawing
import com.harmen.pafta.geometry.Aabb
import com.harmen.pafta.project.DrawnShape
import com.harmen.pafta.project.OpeningKind
import com.harmen.pafta.project.dimensions
import com.harmen.pafta.project.isBand
import com.harmen.pafta.project.lengthMm
import com.harmen.pafta.project.openingPlans
import com.harmen.pafta.project.slabPlans
import com.harmen.pafta.project.wallBands
import com.harmen.pafta.project.zonePlans
import com.harmen.pafta.ui.chrome.DrawingToolBar
import com.harmen.pafta.ui.chrome.ElementRail
import com.harmen.pafta.ui.chrome.HairlineDivider
import com.harmen.pafta.ui.chrome.PaftaTopBar
import com.harmen.pafta.ui.chrome.RightPanel
import com.harmen.pafta.ui.chrome.ToolOptionsBar
import com.harmen.pafta.ui.chrome.VerticalHairline
import com.harmen.pafta.ui.state.DRAWING_TOOLS
import com.harmen.pafta.ui.state.EditorState
import com.harmen.pafta.ui.state.EditorViewModel
import com.harmen.pafta.ui.state.UiError
import com.harmen.pafta.ui.theme.HarmenColours
import com.harmen.pafta.ui.theme.HarmenType
import com.harmen.pafta.ui.theme.LocalCompactLayout
import com.harmen.pafta.ui.viewport.PlanViewport
import com.harmen.pafta.ui.viewport.RoomLabel
import com.harmen.pafta.units.AreaUnit
import com.harmen.pafta.units.formatArea
import com.harmen.pafta.units.formatLength

/**
 * The editor screen.
 *
 * Reading down: the project and its undo buttons, then the drawing and editing
 * tools, then whatever the tool in hand needs told. Reading across: the sheet
 * takes everything left over, the building elements stand down the right-hand
 * edge, and the layer panel slides out between them when it is asked for.
 *
 * The panel is shut to begin with and stays where the user last put it. It used
 * to be nailed to the screen, taking 232dp of a tablet away from the drawing
 * whether or not anything in it was wanted.
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
    modifier: Modifier = Modifier,
) {
    // Turkish words the view model needs but must never contain: a literal in
    // Kotlin is how English reaches a Turkish screen.
    val newRoomName = stringResource(R.string.zone_default_name)
    val openRoomNote = stringResource(R.string.zone_not_closed)
    LaunchedEffect(newRoomName) { viewModel.defaultZoneName = newRoomName }

    // Survives a rotation, so turning the tablet does not shut a panel the user
    // opened to type a wall length into.
    var panelOpen by rememberSaveable { mutableStateOf(false) }

    BoxWithConstraints(modifier.fillMaxSize().background(HarmenColours.Ground)) {
        val compact = maxWidth < COMPACT_WIDTH

        CompositionLocalProvider(LocalCompactLayout provides compact) {
            Column(Modifier.fillMaxSize()) {
                PaftaTopBar(
                    projectName = state.projectName,
                    onHome = onBack,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    dirty = state.dirty,
                )

                DrawingToolBar(
                    activeTool = state.activeTool,
                    onToolSelected = viewModel::selectTool,
                    compact = compact,
                )

                ToolOptionsBar(
                    activeTool = state.activeTool,
                    wallThicknessMm = state.wallThicknessMm,
                    onWallThicknessSelected = viewModel::selectWallThickness,
                    wallMaterial = state.wallMaterial,
                    onWallMaterialSelected = viewModel::selectWallMaterial,
                    doorWidthMm = state.doorWidthMm,
                    windowWidthMm = state.windowWidthMm,
                    onOpeningWidthSelected = viewModel::setOpeningWidth,
                    hasFirstPick = state.firstPickId != null,
                    filletRadiusMm = state.filletRadiusMm,
                    onFilletRadiusSelected = viewModel::setFilletRadius,
                    chamferMm = state.chamferMm,
                    onChamferSizeSelected = viewModel::setChamferSize,
                    measureMode = state.measureMode,
                    pendingPickCount = state.pendingPicks.size,
                    onMeasureModeSelected = viewModel::selectMeasureMode,
                    onFinishMeasurement = viewModel::finishMeasurement,
                    onUndoPick = viewModel::undoPick,
                    onCancelMeasurement = viewModel::cancelMeasurement,
                    selectedShapeId = state.selectedShapeId,
                    onDeleteSelected = viewModel::deleteSelected,
                    onFinishChain = viewModel::finishChain,
                    columnSizeMm = state.columnSizeMm,
                    onColumnSizeSelected = viewModel::selectColumnSize,
                    columnRound = state.columnRound,
                    onColumnRoundSelected = viewModel::selectColumnRound,
                    beamWidthMm = state.beamWidthMm,
                    onBeamWidthSelected = viewModel::selectBeamWidth,
                    slabThicknessMm = state.slabThicknessMm,
                    onSlabThicknessSelected = viewModel::selectSlabThickness,
                    slabKind = state.slabKind,
                    onSlabKindSelected = viewModel::selectSlabKind,
                    blockGroup = state.blockGroup,
                    onBlockGroupSelected = viewModel::selectBlockGroup,
                    block = state.block,
                    onBlockSelected = viewModel::selectBlock,
                    wallType = state.wallType,
                    onWallTypeSelected = viewModel::selectWallType,
                )
                HairlineDivider()

                Row(Modifier.fillMaxWidth().weight(1f)) {
                    // Worked out once per change and used by both the drawing and
                    // the panel, so the two can never disagree about a room.
                    val zones = remember(state.shapes) { state.shapes.zonePlans() }
                    // Slabs go first in the list, so they are drawn under
                    // everything: a floor covers the whole room, and one drawn
                    // over the room would hide it.
                    val slabs = remember(state.shapes) { state.shapes.slabPlans() }
                    // The Turkish is worked out here and not inside the label
                    // lambda: that lambda runs inside the canvas, where there is
                    // no composition and so no way to read `strings.xml`. Every
                    // word the plan shows has to be resolved before it gets
                    // there.
                    val slabLabels = mutableMapOf<String, List<String>>()
                    for (slab in state.shapes.filterIsInstance<DrawnShape.Slab>()) {
                        slabLabels[slab.id] = listOf(slab.kind.adi(), slab.finish.adi())
                    }

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
                        // Walls and rounded corners go through their own list
                        // because they are filled bands with closed corners,
                        // not plain outlines. Drawing one in both lists would
                        // put a pencil line up the middle of a solid wall.
                        drawn = remember(state.shapes) {
                            state.shapes
                                .filterNot { it.isBand() }
                                .flatMap { it.toEntities() }
                        },
                        walls = remember(state.shapes) { state.shapes.wallBands() },
                        openings = remember(state.shapes) { state.shapes.openingPlans() },
                        zones = slabs + zones,
                        // A slab says what it is and what it is covered with;
                        // the room above it says its name and how big it is. Two
                        // different questions, two answers, one outline.
                        zoneLabel = { plan ->
                            slabLabels[plan.id] ?: buildList {
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
                                    !it.isBand()
                            }
                            ?.toEntities()
                            .orEmpty(),
                        snapAt = state.snapAt,
                    )

                    if (panelOpen) {
                        Row(Modifier.fillMaxHeight()) {
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
                                onScale = viewModel::scaleSelected,
                                selectedSlab = state.shapes
                                    .firstOrNull { it.id == state.selectedShapeId }
                                        as? DrawnShape.Slab,
                                onSlabKindChanged = viewModel::setSelectedSlabKind,
                                onFinishChanged = viewModel::setSelectedFinish,
                                selectedColumn = state.shapes
                                    .firstOrNull { it.id == state.selectedShapeId }
                                        as? DrawnShape.Column,
                                onColumnRoundChanged = viewModel::setSelectedColumnRound,
                            )
                        }
                    }

                    VerticalHairline(Modifier.fillMaxHeight())
                    ElementRail(
                        activeTool = state.activeTool,
                        onToolSelected = viewModel::selectTool,
                        compact = compact,
                        panelOpen = panelOpen,
                        onTogglePanel = { panelOpen = !panelOpen },
                    )
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

/** Below this width the tools drop their captions. */
private val COMPACT_WIDTH = 720.dp
