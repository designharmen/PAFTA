package com.harmen.pafta

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.harmen.pafta.ui.PaftaScreen
import com.harmen.pafta.ui.state.EditorState
import com.harmen.pafta.ui.state.EditorViewModel
import com.harmen.pafta.ui.theme.PaftaTheme
import com.harmen.pafta.ui.viewport.SamplePlan

/**
 * The single activity.
 *
 * PAFTA opens straight onto the editor with the sample plan loaded. A file
 * manager and a real import path arrive in the next phase; until then this is
 * what makes the chrome reviewable on a device.
 */
public class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PaftaTheme {
                val vm: EditorViewModel = viewModel { EditorViewModel(firstRunState()) }
                val state by vm.state.collectAsStateWithLifecycle()

                PaftaScreen(
                    state = state,
                    viewModel = vm,
                    drawing = SamplePlan.drawing,
                    roomLabels = SamplePlan.roomLabels,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                )
            }
        }
    }
}

private fun firstRunState(): EditorState = EditorState(
    projectName = "Harmen Residence – Type A",
    unitLabel = "Unit 101 – Lvl 2",
    layers = SamplePlan.layers,
    materials = SamplePlan.materials,
    properties = SamplePlan.properties,
    selectionTitle = SamplePlan.SELECTION_TITLE,
    measurements = SamplePlan.measurements,
    lastText = "LIVING",
)
