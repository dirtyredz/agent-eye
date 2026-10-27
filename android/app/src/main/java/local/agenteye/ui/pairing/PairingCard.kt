package local.agenteye.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import local.agenteye.R

@Composable
fun PairingCard(state: PairingState, address: String?, onCopy: () -> Unit, onRenew: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.pairing_code), style = MaterialTheme.typography.labelLarge)
            Text(
                state.digits ?: "------",
                style = MaterialTheme.typography.headlineLarge,
                fontFamily = FontFamily.Monospace
            )
            // No live-region semantics: TalkBack must not announce every tick.
            Text(
                if (state.live) {
                    stringResource(
                        R.string.code_expiry,
                        state.seconds / 60,
                        state.seconds % 60
                    )
                } else {
                    stringResource(R.string.pairing_idle)
                },
                style = MaterialTheme.typography.bodySmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onCopy, enabled = state.live) {
                    Text(stringResource(R.string.copy))
                }
                TextButton(onClick = onRenew, enabled = state.live) {
                    Text(stringResource(R.string.new_code))
                }
            }
            SelectionContainer {
                Text(
                    address ?: stringResource(R.string.wifi_guidance),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
