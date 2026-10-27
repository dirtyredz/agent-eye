package local.agenteye.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import local.agenteye.R
import local.agenteye.ui.privacy.PrivacyPolicyDialog
import local.agenteye.ui.theme.AgentEyeSpacing
import local.agenteye.ui.theme.AgentEyeTheme
import local.agenteye.ui.theme.SettingsBody
import local.agenteye.ui.theme.SettingsCardTitle
import local.agenteye.ui.update.UpdateAction
import local.agenteye.ui.update.UpdatePanel
import local.agenteye.ui.update.UpdateView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(view: UpdateView, onBack: () -> Unit, onAction: (UpdateAction) -> Unit) {
    var privacy by rememberSaveable { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(stringResource(R.string.settings)) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        stringResource(R.string.back_to_camera)
                    )
                }
            }
        )
    }) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(
                    max = 640.dp
                ).fillMaxWidth().verticalScroll(rememberScrollState()).padding(
                    AgentEyeSpacing.screen
                ),
                verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.section)
            ) {
                UpdatePanel(view, onAction)
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(AgentEyeSpacing.screen),
                        verticalArrangement = Arrangement.spacedBy(AgentEyeSpacing.section)
                    ) {
                        SettingsCardTitle(stringResource(R.string.about))
                        SettingsBody(stringResource(R.string.usage_guidance))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        TextButton(
                            onClick = { privacy = true },
                            modifier = Modifier.fillMaxWidth().heightIn(
                                min = AgentEyeSpacing.touchTarget
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text(
                                stringResource(R.string.privacy_policy),
                                Modifier.fillMaxWidth(),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
    if (privacy) PrivacyPolicyDialog { privacy = false }
}

@Preview(widthDp = 393, heightDp = 852)
@Preview(widthDp = 852, heightDp = 393, fontScale = 1.5f)
@Composable
private fun SettingsFixture() {
    AgentEyeTheme {
        SettingsScreen(
            UpdateView(
                "Installed: 1.3.0 (5)",
                "Updates are checked only when you tap Check for updates.",
                null,
                null,
                null
            ),
            {
            },
            {}
        )
    }
}
