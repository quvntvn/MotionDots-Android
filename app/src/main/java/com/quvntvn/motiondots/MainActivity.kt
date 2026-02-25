package com.example.motiondots

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.motiondots.data.OverlayMode
import com.example.motiondots.data.SettingsDataStore
import com.example.motiondots.overlay.OverlayService
import com.example.motiondots.ui.components.SettingSlider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MotionDotsScreen()
            }
        }
    }
}

@Composable
private fun MotionDotsScreen() {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()

    val settings by settingsDataStore.settingsFlow.collectAsState(initial = com.example.motiondots.data.OverlaySettings())
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    LaunchedEffect(Unit) { canDraw = Settings.canDrawOverlays(context) }

    LaunchedEffect(settings.autoStartOverlay, canDraw) {
        if (settings.autoStartOverlay && canDraw) {
            context.startService(Intent(context, OverlayService::class.java))
        }
    }

    DisposableEffect(Unit) {
        onDispose { canDraw = Settings.canDrawOverlays(context) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "MOTIONDOTS", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "This visual cue system helps your brain anticipate vehicle motion.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(text = "Overlay permission: ${if (canDraw) "Granted" else "Required"}")

        Button(onClick = {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
            )
        }) {
            Text("Open overlay settings")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = canDraw, onClick = {
                context.startService(Intent(context, OverlayService::class.java))
            }) {
                Text("Start overlay")
            }

            Button(onClick = {
                context.stopService(Intent(context, OverlayService::class.java))
            }) {
                Text("Stop overlay")
            }
        }

        Text(text = "Visual mode", style = MaterialTheme.typography.titleMedium)
        OverlayMode.entries.forEach { mode ->
            val enabledForTier = when (mode) {
                OverlayMode.CLASSIC_DOTS, OverlayMode.DISABLED -> true
                OverlayMode.EDGE_DOTS, OverlayMode.HORIZON -> settings.isPremium
            }

            Row(modifier = Modifier.fillMaxWidth()) {
                RadioButton(
                    selected = settings.selectedMode == mode,
                    enabled = enabledForTier,
                    onClick = {
                        scope.launch {
                            settingsDataStore.setSelectedMode(mode)
                        }
                    },
                )
                Text(
                    text = mode.name.replace('_', ' '),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        SettingSlider(
            title = "Intensity",
            valueLabel = settings.intensity.toInt().toString(),
            value = settings.intensity,
            onValueChange = { value -> scope.launch { settingsDataStore.setIntensity(value) } },
            valueRange = 0f..10f,
            steps = 9,
            enabled = settings.isPremium || settings.selectedMode == OverlayMode.CLASSIC_DOTS,
        )

        SettingSlider(
            title = "Opacity",
            valueLabel = "${(settings.opacity * 100).toInt()}%",
            value = settings.opacity,
            onValueChange = { value -> scope.launch { settingsDataStore.setOpacity(value) } },
            valueRange = 0f..1f,
            steps = 19,
            enabled = settings.isPremium,
        )

        SettingSlider(
            title = "Dot count",
            valueLabel = settings.dotCount.toString(),
            value = settings.dotCount.toFloat(),
            onValueChange = { value -> scope.launch { settingsDataStore.setDotCount(value.toInt()) } },
            valueRange = 10f..100f,
            steps = 89,
            enabled = settings.isPremium,
        )

        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = "Auto-start overlay")
            Switch(
                checked = settings.autoStartOverlay,
                onCheckedChange = { checked -> scope.launch { settingsDataStore.setAutoStartOverlay(checked) } },
            )
        }
    }
}
