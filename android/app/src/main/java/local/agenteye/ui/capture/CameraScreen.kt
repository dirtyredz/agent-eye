package local.agenteye.ui.capture

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import local.agenteye.R
import local.agenteye.camera.Lens
import local.agenteye.ui.pairing.PairingState
import local.agenteye.ui.theme.AgentEyeElevation
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
        Column(Modifier.fillMaxSize()) {
            StatusBar(state, onSettings, Modifier.fillMaxWidth().height(layout.topHeight))
            Spacer(Modifier.height(AgentEyeSpacing.section))
            CameraBody(
                layout = layout,
                state = state,
                pairing = pairing,
                onToggle = onToggle,
                onSwitch = onSwitch,
                onCopy = onCopy,
                onRenew = onRenew,
                preview = preview,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun StatusBar(state: CameraState, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier,
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
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
            }
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
            {},
            {},
            {},
            {},
            {}
        ) { modifier ->
            Box(modifier.background(Color.DarkGray))
        }
    }
}
