package local.agenteye.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import local.agenteye.R
import local.agenteye.ui.pairing.PairingState
import local.agenteye.ui.theme.AgentEyePreviewBackground
import local.agenteye.ui.theme.AgentEyeSpacing

@Composable
internal fun CameraBody(
    layout: CameraLayout,
    state: CameraState,
    pairing: PairingState,
    onToggle: () -> Unit,
    onSwitch: () -> Unit,
    onCopy: () -> Unit,
    onRenew: () -> Unit,
    preview: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    val gap = with(LocalDensity.current) { AgentEyeSpacing.section.roundToPx() }
    val panelWidth = with(LocalDensity.current) { layout.panelWidth.roundToPx() }
    Layout(
        modifier = modifier,
        content = {
            Box(Modifier.fillMaxSize()) {
                preview(Modifier.fillMaxSize())
                if (!state.running) CameraIdlePlaceholder()
            }
            CameraPanel(state, pairing, onToggle, onSwitch, onCopy, onRenew)
        }
    ) { measurables, constraints ->
        val panelConstraints = if (layout.wide) {
            constraints.copy(minWidth = panelWidth, maxWidth = panelWidth, minHeight = 0)
        } else {
            constraints.copy(minHeight = 0)
        }
        val panel = measurables[1].measure(panelConstraints)
        val geometry = cameraBodyGeometry(
            BodyMeasure(
                width = constraints.maxWidth,
                height = constraints.maxHeight,
                panelWidth = panel.width,
                panelHeight = panel.height,
                gap = gap,
                wide = layout.wide
            )
        )
        val frame = measurables[0].measure(
            Constraints.fixed(geometry.previewWidth, geometry.previewHeight)
        )
        layout(constraints.maxWidth, constraints.maxHeight) {
            frame.placeRelative(0, 0)
            panel.placeRelative(geometry.panelX, geometry.panelY)
        }
    }
}

@Composable
private fun CameraIdlePlaceholder() {
    Box(
        Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium)
            .background(AgentEyePreviewBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.camera_idle),
            // One-off edge buffer for centered placeholder copy, not layout rhythm.
            Modifier.padding(24.dp),
            color = Color.White
        )
    }
}
