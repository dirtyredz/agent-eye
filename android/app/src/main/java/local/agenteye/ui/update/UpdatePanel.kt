package local.agenteye.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import local.agenteye.R

@Composable
fun UpdatePanel(view: UpdateView, onAction: (UpdateAction) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.updates), style = MaterialTheme.typography.titleLarge)
            Text(view.version)
            Text(view.status)
            view.progress?.let {
                LinearProgressIndicator(progress = {
                    it / 100f
                }, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.download_percent, it))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                view.primary?.let { button ->
                    Button(onClick = { onAction(button.action) }) { Text(button.label) }
                }
                view.secondary?.let { button ->
                    TextButton(onClick = { onAction(button.action) }) { Text(button.label) }
                }
            }
            Text(
                stringResource(R.string.update_disclosure),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
