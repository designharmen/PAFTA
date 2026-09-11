package com.harmen.pafta.ui.viewport

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.harmen.pafta.R
import com.harmen.pafta.measure.MeasurementDisplay
import com.harmen.pafta.ui.theme.PaftaTheme

/**
 * Renders [SamplePlan] in the IDE preview.
 *
 * This is how the viewport is reviewed against the design without a device, and
 * it is what keeps [SamplePlan] a live fixture rather than dead code now that
 * the app opens real imported drawings instead.
 */
@Preview(name = "Plan viewport — tablet", widthDp = 1024, heightDp = 768)
@Composable
private fun PlanViewportPreview() {
    PaftaTheme {
        PlanViewport(
            drawing = SamplePlan.drawing,
            layers = sampleLayers(),
            measurements = SamplePlan.measurements,
            roomLabels = sampleRoomLabels(),
            display = MeasurementDisplay(),
            unitLabel = stringResource(R.string.sample_unit_label),
            gridVisible = true,
            gridSpacingMm = 1_000.0,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Preview(name = "Plan viewport — phone", widthDp = 411, heightDp = 731)
@Composable
private fun PlanViewportPhonePreview() {
    PaftaTheme {
        PlanViewport(
            drawing = SamplePlan.drawing,
            layers = sampleLayers(),
            measurements = SamplePlan.measurements,
            roomLabels = sampleRoomLabels(),
            display = MeasurementDisplay(),
            unitLabel = stringResource(R.string.sample_unit_label),
            gridVisible = true,
            gridSpacingMm = 1_000.0,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
