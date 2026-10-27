package local.agenteye.ui.capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import local.agenteye.R
import local.agenteye.camera.Lens

@Composable
fun CameraControls(state: CameraState, onToggle: () -> Unit, onSwitch: () -> Unit) {
    Surface {
        FlowRow(
            Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onToggle,
                enabled = state.running || state.canRequestStart,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(if (state.running) R.string.stop else R.string.start))
            }
            val lens = stringResource(
                when (state.lens) {
                    Lens.BACK -> R.string.lens_back
                    Lens.FRONT -> R.string.lens_front
                    Lens.EXTERNAL -> R.string.lens_external
                    null -> R.string.lens_unknown
                }
            )
            OutlinedButton(
                onClick = onSwitch,
                enabled = state.canSwitch,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.switch_camera, lens))
            }
        }
    }
}
