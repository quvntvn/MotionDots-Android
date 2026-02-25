package com.quvntvn.motiondots

import android.app.ActivityManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.quvntvn.motiondots.data.DensityPreset
import com.quvntvn.motiondots.data.DotColor
import com.quvntvn.motiondots.data.IntensityPreset
import com.quvntvn.motiondots.data.OpacityPreset
import com.quvntvn.motiondots.data.OverlayMode
import com.quvntvn.motiondots.data.OverlaySettings
import com.quvntvn.motiondots.data.SettingsDataStore
import com.quvntvn.motiondots.data.SizePreset
import com.quvntvn.motiondots.overlay.OverlayService
import com.quvntvn.motiondots.ui.theme.MotionDotsTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MotionDotsTheme {
                MotionDotsApp()
            }
        }
    }
}

private object AppRoute {
    const val Onboarding = "onboarding"
    const val Main = "main"
    const val Preview = "preview"
}

private fun OverlayMode.isPremiumMode(): Boolean = this == OverlayMode.EDGE_DOTS || this == OverlayMode.HORIZON

@Composable
private fun MotionDotsApp() {
    val context = LocalContext.current
    val settingsDataStore = remember { SettingsDataStore(context) }
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()

    val hasOnboarded by produceState<Boolean?>(initialValue = null, settingsDataStore) {
        settingsDataStore.hasOnboardedFlow.collect { value = it }
    }
    val settings by settingsDataStore.settingsFlow.collectAsState(initial = OverlaySettings())
    val isPremium = BuildConfig.FORCE_PREMIUM || settings.isPremium

    if (hasOnboarded == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
            Text(text = stringResource(id = R.string.loading))
        }
        return
    }

    NavHost(
        navController = navController,
        startDestination = if (hasOnboarded == true) AppRoute.Main else AppRoute.Onboarding,
    ) {
        composable(AppRoute.Onboarding) {
            OnboardingScreen(
                isPremium = isPremium,
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
            MainScreen(
                settingsDataStore = settingsDataStore,
                onOpenPreview = { navController.navigate(AppRoute.Preview) },
            )
        }
        composable(AppRoute.Preview) {
            PreviewScreen(
                settingsDataStore = settingsDataStore,
                onBack = { navController.popBackStack() },
            )
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
    isPremium: Boolean,
    onComplete: (OverlayMode, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
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

    Scaffold(snackbarHost = { SnackbarHost(hostState = snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_welcome),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.onboarding_step, stepIndex + 1, steps.size),
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
                    isPremium = isPremium,
                    onLockedModeSelected = {
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.premium_feature)) }
                    },
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
                        Text(stringResource(R.string.back))
                    }
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        if (currentStep == OnboardingStep.MODE) {
                            val completedMode = selectedMode.takeIf { !it.isPremiumMode() || isPremium }
                                ?: OverlayMode.CLASSIC_DOTS
                            onComplete(completedMode, canDraw)
                        } else {
                            stepIndex += 1
                        }
                    },
                ) {
                    Text(
                        if (currentStep == OnboardingStep.MODE) {
                            stringResource(R.string.start_overlay)
                        } else {
                            stringResource(R.string.continue_label)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun IntroStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.intro_description),
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
                text = stringResource(R.string.illustration_placeholder),
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
            text = stringResource(R.string.permission_description),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onOpenPermission) {
            Text(stringResource(R.string.allow_overlay_permission))
        }
    }
}

@Composable
private fun ModeSelectionStep(
    selectedMode: OverlayMode,
    isPremium: Boolean,
    onLockedModeSelected: () -> Unit,
    onModeSelected: (OverlayMode) -> Unit,
) {
    val modes = listOf(OverlayMode.CLASSIC_DOTS, OverlayMode.EDGE_DOTS, OverlayMode.HORIZON)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.select_cue_style),
            style = MaterialTheme.typography.bodyLarge,
        )
        modes.forEach { mode ->
            val isPremiumMode = mode.isPremiumMode()
            val label = when (mode) {
                OverlayMode.CLASSIC_DOTS -> stringResource(R.string.mode_dots)
                OverlayMode.EDGE_DOTS -> stringResource(R.string.mode_edge)
                OverlayMode.HORIZON -> stringResource(R.string.mode_horizon)
                OverlayMode.DISABLED -> stringResource(R.string.mode_disabled)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isPremiumMode && !isPremium) {
                            onLockedModeSelected()
                        } else {
                            onModeSelected(mode)
                        }
                    }
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
                    RadioButton(
                        selected = selectedMode == mode,
                        onClick = {
                            if (isPremiumMode && !isPremium) {
                                onLockedModeSelected()
                            } else {
                                onModeSelected(mode)
                            }
                        },
                    )
                    Text(text = label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    settingsDataStore: SettingsDataStore,
    onOpenPreview: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }

    val settings by settingsDataStore.settingsFlow.collectAsState(initial = OverlaySettings())
    val isPremium = BuildConfig.FORCE_PREMIUM || settings.isPremium
    val activeMode = settings.selectedMode.takeIf { !it.isPremiumMode() || isPremium } ?: OverlayMode.CLASSIC_DOTS
    var canDraw by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var isOverlayRunning by remember { mutableStateOf(isOverlayServiceRunning(context)) }
    var logoTapCount by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        canDraw = Settings.canDrawOverlays(context)
        isOverlayRunning = isOverlayServiceRunning(context)
    }

    LaunchedEffect(settings.autoStartOverlay, canDraw) {
        if (settings.autoStartOverlay && canDraw) {
            context.startService(Intent(context, OverlayService::class.java))
            isOverlayRunning = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            canDraw = Settings.canDrawOverlays(context)
            isOverlayRunning = isOverlayServiceRunning(context)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = stringResource(R.string.app_logo),
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            logoTapCount += 1
                            if (logoTapCount >= 5) {
                                logoTapCount = 0
                                val nextPremium = !settings.isPremium
                                scope.launch {
                                    settingsDataStore.setIsPremium(nextPremium)
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            if (nextPremium) R.string.premium_enabled else R.string.premium_disabled,
                                        ),
                                    )
                                }
                            }
                        },
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = stringResource(R.string.main_tagline),
                style = MaterialTheme.typography.bodyMedium,
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.overlay_permission_status, if (canDraw) stringResource(R.string.status_granted) else stringResource(R.string.status_required)))
                    Text(stringResource(R.string.overlay_running_status, if (isOverlayRunning) stringResource(R.string.status_running) else stringResource(R.string.status_stopped)))
                    if (!canDraw) {
                        Button(onClick = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }) {
                            Text(stringResource(R.string.grant_overlay_permission))
                        }
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canDraw || isOverlayRunning,
                onClick = {
                    if (isOverlayRunning) {
                        context.stopService(Intent(context, OverlayService::class.java))
                        isOverlayRunning = false
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.overlay_stopped)) }
                    } else if (canDraw) {
                        context.startService(Intent(context, OverlayService::class.java))
                        isOverlayRunning = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.overlay_started)) }
                    }
                },
            ) {
                Text(if (isOverlayRunning) stringResource(R.string.stop_service) else stringResource(R.string.start_service))
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenPreview,
            ) {
                Text(stringResource(R.string.preview))
            }

            Text(text = stringResource(R.string.mode_label), style = MaterialTheme.typography.titleMedium)
            val modeOptions = listOf(OverlayMode.CLASSIC_DOTS, OverlayMode.EDGE_DOTS, OverlayMode.HORIZON)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                modeOptions.forEachIndexed { index, mode ->
                    val isPro = mode == OverlayMode.EDGE_DOTS || mode == OverlayMode.HORIZON
                    val enabled = !isPro || isPremium
                    val label = when (mode) {
                        OverlayMode.CLASSIC_DOTS -> stringResource(R.string.mode_classic)
                        OverlayMode.EDGE_DOTS -> stringResource(R.string.mode_edge)
                        OverlayMode.HORIZON -> stringResource(R.string.mode_horizon)
                        OverlayMode.DISABLED -> stringResource(R.string.mode_disabled)
                    }
                    SegmentedButton(
                        selected = activeMode == mode,
                        enabled = true,
                        onClick = {
                            if (enabled) {
                                if (activeMode != mode) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch { settingsDataStore.setSelectedMode(mode) }
                                }
                            } else {
                                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.premium_feature)) }
                            }
                        },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modeOptions.size),
                        label = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label)
                                if (isPro && !isPremium) {
                                    Badge { Text(stringResource(R.string.pro_badge)) }
                                }
                            }
                        },
                    )
                }
            }

            PresetGroup(
                title = stringResource(R.string.intensity),
                options = IntensityPreset.entries,
                selected = settings.intensityPreset,
                label = { preset -> Text(preset.name.lowercase().replaceFirstChar(Char::uppercase)) },
                onSelected = { preset -> scope.launch { settingsDataStore.setIntensityPreset(preset) } },
            )

            PresetGroup(
                title = stringResource(R.string.opacity),
                options = OpacityPreset.entries,
                selected = settings.opacityPreset,
                label = { preset ->
                    Text(
                        when (preset) {
                            OpacityPreset.SUBTLE -> stringResource(R.string.opacity_subtle)
                            OpacityPreset.BALANCED -> stringResource(R.string.opacity_balanced)
                            OpacityPreset.VISIBLE -> stringResource(R.string.opacity_visible)
                        },
                    )
                },
                onSelected = { preset -> scope.launch { settingsDataStore.setOpacityPreset(preset) } },
            )

            PresetGroup(
                title = stringResource(R.string.dot_color),
                options = DotColor.entries,
                selected = settings.dotColor,
                label = { preset ->
                    Text(if (preset == DotColor.WHITE) stringResource(R.string.color_white) else stringResource(R.string.color_black))
                },
                onSelected = { preset -> scope.launch { settingsDataStore.setDotColor(preset) } },
            )

            if (activeMode != OverlayMode.HORIZON) {
                PresetGroup(
                    title = stringResource(R.string.density),
                    options = DensityPreset.entries,
                    selected = settings.densityPreset,
                    label = { preset ->
                        Text(
                            when (preset) {
                                DensityPreset.LIGHT -> stringResource(R.string.density_light)
                                DensityPreset.STANDARD -> stringResource(R.string.density_standard)
                                DensityPreset.DENSE -> stringResource(R.string.density_dense)
                            },
                        )
                    },
                    onSelected = { preset -> scope.launch { settingsDataStore.setDensityPreset(preset) } },
                )

                PresetGroup(
                    title = stringResource(R.string.dot_size),
                    options = SizePreset.entries,
                    selected = settings.sizePreset,
                    label = { preset ->
                        Text(
                            when (preset) {
                                SizePreset.SMALL -> stringResource(R.string.size_small)
                                SizePreset.MEDIUM -> stringResource(R.string.size_medium)
                                SizePreset.LARGE -> stringResource(R.string.size_large)
                            },
                        )
                    },
                    onSelected = { preset -> scope.launch { settingsDataStore.setSizePreset(preset) } },
                )
            }

            Text(
                text = stringResource(R.string.changes_apply_instantly),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.auto_start_overlay))
                Switch(
                    checked = settings.autoStartOverlay,
                    onCheckedChange = { checked -> scope.launch { settingsDataStore.setAutoStartOverlay(checked) } },
                )
            }

            Text(
                text = stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PresetGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> Unit,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { onSelected(option) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    label = { label(option) },
                )
            }
        }
    }
}

@Composable
private fun PreviewScreen(
    settingsDataStore: SettingsDataStore,
    onBack: () -> Unit,
) {
    val settings by settingsDataStore.settingsFlow.collectAsState(initial = OverlaySettings())
    val isPremium = BuildConfig.FORCE_PREMIUM || settings.isPremium
    val activeMode = settings.selectedMode.takeIf { !it.isPremiumMode() || isPremium } ?: OverlayMode.CLASSIC_DOTS
    val motionOffset = rememberPreviewMotion(intensity = mapIntensityPreset(settings.intensityPreset))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.preview_subtitle))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                when (activeMode) {
                    OverlayMode.CLASSIC_DOTS, OverlayMode.EDGE_DOTS -> DotPreviewCanvas(
                        dotCount = mapDensityPreset(settings.densityPreset),
                        opacity = mapOpacityPreset(settings.opacityPreset),
                        intensity = mapIntensityPreset(settings.intensityPreset),
                        dotColor = settings.dotColor,
                        sizeScale = mapSizePreset(settings.sizePreset),
                        motionOffset = motionOffset,
                        edgeOnly = activeMode == OverlayMode.EDGE_DOTS,
                    )

                    OverlayMode.HORIZON -> HorizonPreviewCanvas(
                        opacity = mapOpacityPreset(settings.opacityPreset),
                        intensity = mapIntensityPreset(settings.intensityPreset),
                        dotColor = settings.dotColor,
                        motionOffset = motionOffset,
                    )
                    OverlayMode.DISABLED -> Unit
                }
            }
        }

        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.back_to_home))
        }
    }
}

@Composable
private fun DotPreviewCanvas(
    dotCount: Int,
    opacity: Float,
    intensity: Float,
    dotColor: DotColor,
    sizeScale: Float,
    motionOffset: Offset,
    edgeOnly: Boolean,
) {
    val dotColorValue = if (dotColor == DotColor.BLACK) Color.Black else Color.White
    val previewColor = dotColorValue.copy(alpha = opacity.coerceIn(0.1f, 1f))
    val points = remember(dotCount, edgeOnly) {
        val random = Random(dotCount * if (edgeOnly) 13 else 7)
        List(dotCount.coerceIn(10, 100)) {
            val normalizedX = if (edgeOnly) {
                if (random.nextBoolean()) {
                    random.nextFloat() * 0.2f
                } else {
                    1f - random.nextFloat() * 0.2f
                }
            } else {
                random.nextFloat()
            }
            val normalizedY = random.nextFloat()
            val radiusFactor = 0.6f + (random.nextFloat() * 0.8f)
            Triple(normalizedX, normalizedY, radiusFactor)
        }
    }

    val intensityScale = (intensity / 10f).coerceIn(0f, 1f)
    val translatedOffset = Offset(
        x = motionOffset.x * (16f + (intensityScale * 54f)),
        y = motionOffset.y * (16f + (intensityScale * 54f)),
    )

    Canvas(modifier = Modifier.fillMaxSize()) {
        val baseRadius = size.minDimension * 0.012f
        points.forEach { (xNorm, yNorm, radiusFactor) ->
            drawCircle(
                color = previewColor,
                radius = (baseRadius * radiusFactor * sizeScale).coerceAtLeast(1f),
                center = Offset(
                    x = (xNorm * size.width) + translatedOffset.x,
                    y = (yNorm * size.height) + translatedOffset.y,
                ),
            )
        }
    }
}

@Composable
private fun HorizonPreviewCanvas(
    opacity: Float,
    intensity: Float,
    dotColor: DotColor,
    motionOffset: Offset,
) {
    val base = if (dotColor == DotColor.BLACK) Color.Black else Color.White
    val lineColor = base.copy(alpha = opacity.coerceIn(0.1f, 1f))
    val intensityScale = (intensity / 10f).coerceIn(0f, 1f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerY = (size.height / 2f) + (motionOffset.y * (20f + (intensityScale * 110f)))
        val tiltDegrees = motionOffset.x * (5f + (intensityScale * 17f))

        rotate(degrees = tiltDegrees, pivot = Offset(size.width / 2f, centerY)) {
            drawLine(
                color = lineColor,
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun rememberPreviewMotion(intensity: Float): Offset {
    val context = LocalContext.current
    val intensityScale by rememberUpdatedState((intensity / 10f).coerceIn(0.12f, 1f))

    var sensorOffsetX by remember { mutableFloatStateOf(0f) }
    var sensorOffsetY by remember { mutableFloatStateOf(0f) }
    var sensorAvailable by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val sensorManager = context.getSystemService(SensorManager::class.java)
        val sensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (sensorManager == null || sensor == null) {
            sensorAvailable = false
            onDispose {}
        } else {
            sensorAvailable = true
            var filteredX = 0f
            var filteredY = 0f

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val values = event?.values ?: return
                    if (values.size < 2) return

                    val alpha = 0.18f
                    filteredX += alpha * (values[0] - filteredX)
                    filteredY += alpha * (values[1] - filteredY)

                    sensorOffsetX = (-filteredX * 0.08f).coerceIn(-1f, 1f)
                    sensorOffsetY = (filteredY * 0.08f).coerceIn(-1f, 1f)
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
            onDispose { sensorManager.unregisterListener(listener) }
        }
    }

    var demoPhase by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sensorAvailable) {
        if (sensorAvailable) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(16L)
            demoPhase += 0.03f
            if (demoPhase > (2f * PI.toFloat())) {
                demoPhase -= (2f * PI.toFloat())
            }
        }
    }

    return if (sensorAvailable) {
        Offset(
            x = sensorOffsetX * intensityScale,
            y = sensorOffsetY * intensityScale,
        )
    } else {
        Offset(
            x = sin(demoPhase) * 0.3f * intensityScale,
            y = sin(demoPhase * 0.6f) * 0.24f * intensityScale,
        )
    }
}

private fun mapIntensityPreset(preset: IntensityPreset): Float = when (preset) {
    IntensityPreset.LOW -> 3f
    IntensityPreset.NORMAL -> 6f
    IntensityPreset.HIGH -> 9f
}

private fun mapOpacityPreset(preset: OpacityPreset): Float = when (preset) {
    OpacityPreset.SUBTLE -> 0.14f
    OpacityPreset.BALANCED -> 0.24f
    OpacityPreset.VISIBLE -> 0.38f
}

private fun mapDensityPreset(preset: DensityPreset): Int = when (preset) {
    DensityPreset.LIGHT -> 24
    DensityPreset.STANDARD -> 48
    DensityPreset.DENSE -> 80
}

private fun mapSizePreset(preset: SizePreset): Float = when (preset) {
    SizePreset.SMALL -> 0.75f
    SizePreset.MEDIUM -> 1f
    SizePreset.LARGE -> 1.35f
}

private fun isOverlayServiceRunning(context: android.content.Context): Boolean {
    val activityManager = context.getSystemService(ActivityManager::class.java)
    @Suppress("DEPRECATION")
    return activityManager?.getRunningServices(Int.MAX_VALUE)
        ?.any { it.service.className == OverlayService::class.java.name } == true
}
