package local.agenteye.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import local.agenteye.R
import local.agenteye.camera.Lens
import local.agenteye.ui.pairing.PairingCard
import local.agenteye.ui.pairing.PairingState
import local.agenteye.ui.theme.AgentEyeElevation
import local.agenteye.ui.theme.AgentEyePreviewBackground
import local.agenteye.ui.theme.AgentEyeSpacing
import local.agenteye.ui.theme.AgentEyeTheme

@Composable
fun CameraScreen(
    state: CameraState,
    pairing: PairingState,
    onToggle: () -> Unit,
    onSwitch: () -> Unit,
    onSettings: () -> Unit,
    onCopy: () -> Unit,
    onRenew: () -> Unit,
    preview: @Composable (Modifier) -> Unit
) {
    BoxWithConstraints(
        Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding().padding(
            horizontal = AgentEyeSpacing.screen,
            vertical = AgentEyeSpacing.compact
        )
    ) {
        val layout = cameraLayout(maxWidth, maxHeight, LocalDensity.current.fontScale)
        // Stable bars keep controls off the frame. Ticks/status do not resize the preview.
        val frame = Modifier.fillMaxSize().padding(
            top = layout.topHeight + AgentEyeSpacing.section,
            end = if (layout.wide) layout.panelWidth + AgentEyeSpacing.section else 0.dp,
            bottom = if (layout.wide) 0.dp else layout.panelHeight + AgentEyeSpacing.section
        ).clip(MaterialTheme.shapes.medium).background(Color.Black)
        preview(frame)
        if (!state.running) {
            Box(
                frame.background(AgentEyePreviewBackground),
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
        Surface(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(layout.topHeight),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = AgentEyeElevation.container
        ) {
            Row(
                Modifier.padding(
                    start = AgentEyeSpacing.content,
                    end = AgentEyeSpacing.compact
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(state.status.text),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = onSettings) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings)
                    )
                }
            }
        }
        val panel = if (layout.wide) {
            Modifier.align(Alignment.CenterEnd).width(layout.panelWidth).fillMaxSize().padding(
                top = layout.topHeight + AgentEyeSpacing.section
            )
        } else {
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(layout.panelHeight)
        }
        Column(panel, verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.section)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                PairingCard(pairing, state.address, onCopy, onRenew)
            }
            CameraControls(state, onToggle, onSwitch)
        }
    }
}

@Preview(widthDp = 393, heightDp = 852)
@Preview(widthDp = 852, heightDp = 393)
@Preview(widthDp = 320, heightDp = 640, fontScale = 1.6f)
@Composable
private fun CameraFixture() {
    AgentEyeTheme {
        CameraScreen(
            CameraState(
                true,
                CameraStatus.Ready,
                "http://192.168.1.10:8765",
                canSwitch = true,
                lens = Lens.BACK
            ),
            PairingState("001234", 91),
            {
            },
            {},
            {},
            {},
            {}
        ) { modifier ->
            Box(modifier.background(Color.DarkGray))
        }
    }
}
