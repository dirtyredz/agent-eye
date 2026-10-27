package local.agenteye.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import local.agenteye.ui.pairing.PairingCard
import local.agenteye.ui.pairing.PairingState
import local.agenteye.ui.theme.AgentEyeElevation
import local.agenteye.ui.theme.AgentEyeSpacing

@Composable
internal fun CameraPanel(
    state: CameraState,
    pairing: PairingState,
    onToggle: () -> Unit,
    onSwitch: () -> Unit,
    onCopy: () -> Unit,
    onRenew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = AgentEyeElevation.container
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                AgentEyeSpacing.content
            ),
            verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.compact)
        ) {
            PairingCard(pairing, state.address, onCopy, onRenew)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            CameraControls(state, onToggle, onSwitch)
        }
    }
}
