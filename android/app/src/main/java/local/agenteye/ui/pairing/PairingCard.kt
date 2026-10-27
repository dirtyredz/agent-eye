package local.agenteye.ui.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import local.agenteye.R
import local.agenteye.ui.theme.AgentEyeElevation
import local.agenteye.ui.theme.AgentEyeSpacing

@Composable
fun PairingCard(state: PairingState, address: String?, onCopy: () -> Unit, onRenew: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        tonalElevation = AgentEyeElevation.container
    ) {
        Column(
            Modifier.fillMaxWidth().padding(
                horizontal = AgentEyeSpacing.content,
                vertical = AgentEyeSpacing.compact
            ),
            verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.compact)
        ) {
            Text(stringResource(R.string.pairing_code), style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.compact),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        state.digits ?: "------",
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = FontFamily.Monospace
                    )
                    if (state.live) {
                        // No live-region semantics: TalkBack must not announce every tick.
                        Text(
                            stringResource(
                                R.string.code_countdown,
                                state.seconds / 60,
                                state.seconds % 60
                            ),
                            Modifier.padding(start = AgentEyeSpacing.compact),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
                Row {
                    TextButton(
                        onClick = onCopy,
                        enabled = state.live,
                        modifier = Modifier.sizeIn(
                            minWidth = AgentEyeSpacing.touchTarget,
                            minHeight = AgentEyeSpacing.touchTarget
                        ),
                        contentPadding = PaddingValues(horizontal = AgentEyeSpacing.compact)
                    ) {
                        Text(stringResource(R.string.copy))
                    }
                    IconButton(
                        onClick = onRenew,
                        enabled = state.live,
                        modifier = Modifier.sizeIn(
                            minWidth = AgentEyeSpacing.touchTarget,
                            minHeight = AgentEyeSpacing.touchTarget
                        )
                    ) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.new_code))
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AgentEyeSpacing.compact),
                verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.compact),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(
                        if (state.live) R.string.pairing_single_use else R.string.pairing_idle
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                SelectionContainer {
                    Text(
                        address ?: stringResource(R.string.wifi_guidance),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
