package local.agenteye.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import local.agenteye.R
import local.agenteye.ui.theme.AgentEyeSpacing
import local.agenteye.ui.theme.SettingsBody
import local.agenteye.ui.theme.SettingsCardTitle

@Composable
fun UpdatePanel(view: UpdateView, onAction: (UpdateAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(AgentEyeSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.section)
        ) {
            SettingsCardTitle(stringResource(R.string.updates))
            SettingsBody(view.version)
            SettingsBody(view.status)
            view.progress?.let {
                LinearProgressIndicator(progress = {
                    it / 100f
                }, modifier = Modifier.fillMaxWidth())
                SettingsBody(stringResource(R.string.download_percent, it))
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AgentEyeSpacing.section),
                verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.compact)
            ) {
                view.primary?.let { button ->
                    Button(onClick = { onAction(button.action) }) { Text(button.label) }
                }
                view.secondary?.let { button ->
                    TextButton(onClick = { onAction(button.action) }) { Text(button.label) }
                }
            }
            SettingsBody(stringResource(R.string.update_disclosure))
        }
    }
}
