package local.agenteye.ui.privacy

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import local.agenteye.R

@Composable
fun PrivacyPolicyDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    val policy =
        remember(context) {
            context.assets.open("PRIVACY.md").bufferedReader().use { it.readText() }
        }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.privacy_policy)) },
        text = {
            SelectionContainer { Text(policy, Modifier.verticalScroll(rememberScrollState())) }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.close)) } }
    )
}
