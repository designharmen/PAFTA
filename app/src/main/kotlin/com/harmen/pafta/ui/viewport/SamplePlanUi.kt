package com.harmen.pafta.ui.viewport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.res.stringResource
import com.harmen.pafta.project.LayerState

/**
 * Resolves [SamplePlan]'s resource ids into Turkish text.
 *
 * The fixture itself holds no words, only ids, so the sample plan and the
 * interface can never disagree about what a room or a layer is called.
 */

/** The sample plan's room names, in Turkish. */
@Composable
@ReadOnlyComposable
public fun sampleRoomLabels(): List<RoomLabel> =
    SamplePlan.roomLabelIds.map { (nameRes, position) ->
        RoomLabel(stringResource(nameRes), position)
    }

/** The sample plan's layer palette, with Turkish display names. */
@Composable
@ReadOnlyComposable
public fun sampleLayers(): List<LayerState> =
    SamplePlan.layerIds.map { (layer, nameRes) -> layer.copy(name = stringResource(nameRes)) }
