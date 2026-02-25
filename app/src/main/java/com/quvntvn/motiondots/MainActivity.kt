package com.quvntvn.motiondots

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quvntvn.motiondots.data.OverlayMode
import com.quvntvn.motiondots.data.OverlaySettings
import com.quvntvn.motiondots.data.SettingsDataStore
import com.quvntvn.motiondots.overlay.OverlayService
import com.quvntvn.motiondots.ui.components.SettingSlider
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MotionDotsApp()
            }
        }
    }
}

private object AppRoute {
    const val Onboarding = "onboarding"
    const val Main = "main"
}

@Composable
private fun MotionDotsApp() {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val hasOnboarded by produceState<Boolean?>(initialValue = null, settingsDataStore) {
        settingsDataStore.hasOnboardedFlow.collect { value = it }
    }

    if (hasOnboarded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (hasOnboarded == true) AppRoute.Main else AppRoute.Onboarding,
    ) {
        composable(AppRoute.Onboarding) {
            OnboardingScreen(
                onComplete = { mode, startOverlay ->
                    scope.launch {
                        settingsDataStore.setSelectedMode(mode)
                        settingsDataStore.setHasOnboarded(true)
                    }
                    if (startOverlay) {
                        context.startService(Intent(context, OverlayService::class.java))
                    }
                    navController.navigate(AppRoute.Main) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(AppRoute.Main) {
            MainScreen(settingsDataStore = settingsDataStore)
        }
    }
}

private enum class OnboardingStep {
    INTRO,
    PERMISSION,
    MODE,
}

@Composable
private fun OnboardingScreen(
    onComplete: (OverlayMode, Boolean) -> Unit,
) {
    val context = LocalContext.current
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var selectedMode by rememberSaveable { mutableStateOf(OverlayMode.CLASSIC_DOTS) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) {
        canDraw = Settings.canDrawOverlays(context)
    }

    val steps = remember(canDraw) {
        buildList {
            add(OnboardingStep.INTRO)
            if (!canDraw) add(OnboardingStep.PERMISSION)
            add(OnboardingStep.MODE)
        }
    }

    LaunchedEffect(steps.size) {
        if (stepIndex > steps.lastIndex) {
            stepIndex = steps.lastIndex
        }
    }

    val currentStep = steps[stepIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "Welcome to MotionDots",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Step ${stepIndex + 1} of ${steps.size}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        when (currentStep) {
            OnboardingStep.INTRO -> IntroStep()
            OnboardingStep.PERMISSION -> PermissionStep(
                onOpenPermission = {
                    permissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                },
            )
            OnboardingStep.MODE -> ModeSelectionStep(
                selectedMode = selectedMode,
                onModeSelected = { selectedMode = it },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (stepIndex > 0) {
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = { stepIndex -= 1 },
                ) {
                    Text("Back")
                }
            }

            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    if (currentStep == OnboardingStep.MODE) {
                        onComplete(selectedMode, canDraw)
                    } else {
                        stepIndex += 1
                    }
                },
            ) {
                Text(if (currentStep == OnboardingStep.MODE) "Start Overlay" else "Continue")
            }
        }
    }
}

@Composable
private fun IntroStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "MotionDots adds subtle visual cues to help your body anticipate movement and reduce motion discomfort.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Illustration Placeholder",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PermissionStep(onOpenPermission: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Overlay permission lets MotionDots draw indicators on top of your current apps while you travel.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onOpenPermission) {
            Text("Allow Overlay Permission")
        }
    }
}

@Composable
private fun ModeSelectionStep(
    selectedMode: OverlayMode,
    onModeSelected: (OverlayMode) -> Unit,
) {
    val modes = listOf(OverlayMode.CLASSIC_DOTS, OverlayMode.EDGE_DOTS, OverlayMode.HORIZON)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Select your preferred cue style",
            style = MaterialTheme.typography.bodyLarge,
        )
        modes.forEach { mode ->
            val label = when (mode) {
                OverlayMode.CLASSIC_DOTS -> "Dots"
                OverlayMode.EDGE_DOTS -> "Edge"
                OverlayMode.HORIZON -> "Horizon"
                OverlayMode.DISABLED -> "Disabled"
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onModeSelected(mode) }
                    .border(
                        width = if (selectedMode == mode) 2.dp else 1.dp,
                        color = if (selectedMode == mode) MaterialTheme.colorScheme.primary else Color.Transparent,
                        shape = RoundedCornerShape(18.dp),
                    ),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selectedMode == mode, onClick = { onModeSelected(mode) })
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun MainScreen(settingsDataStore: SettingsDataStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by settingsDataStore.settingsFlow.collectAsState(initial = OverlaySettings())
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
