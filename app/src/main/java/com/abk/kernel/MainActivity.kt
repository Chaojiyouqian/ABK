package com.abk.kernel

import android.Manifest
import android.content.Context
import com.abk.kernel.utils.LocaleHelper
import com.abk.kernel.utils.findActivity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHostState
import top.yukonga.miuix.kmp.basic.NavigationBar as MiuixNavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem as MiuixNavigationBarItem
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abk.kernel.ui.components.AppBackgroundHost
import com.abk.kernel.ui.components.AbkSnackbarHost
import com.abk.kernel.ui.components.animateBottomNavForChildPage
import com.abk.kernel.ui.components.showAbkSnackbar
import com.abk.kernel.extensions.AbkExtensionBootstrapActivity
import com.abk.kernel.ui.screens.BuildScreen
import com.abk.kernel.ui.screens.FlashScreen
import com.abk.kernel.ui.screens.InstalledModulesScreen
import com.abk.kernel.ui.screens.ModuleRepositoryScreen
import com.abk.kernel.ui.screens.OobeScreen
import com.abk.kernel.ui.screens.RootAuthorizationScreen
import com.abk.kernel.ui.screens.RuntimeHomeScreen
import com.abk.kernel.ui.screens.SettingsScreen
import com.abk.kernel.ui.screens.StatusScreen
import com.abk.kernel.miuix.component.AbkMiuixSnackbarHost
import com.abk.kernel.miuix.component.showAbkMiuixSnackbar
import com.abk.kernel.miuix.theme.AbkMiuixTheme
import com.abk.kernel.miuix.component.FloatingTabItem
import com.abk.kernel.miuix.component.MiuixFloatingBottomBar
import com.abk.kernel.miuix.util.BlurredBar
import com.abk.kernel.miuix.util.rememberBlurBackdrop
import com.abk.kernel.ui.theme.AbkTheme
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color
import com.abk.kernel.ui.theme.LocalUiSurfaceAlpha
import com.abk.kernel.ui.theme.appPageBackgroundColor
import com.abk.kernel.ui.theme.uiSurfaceColor
import com.abk.kernel.miuix.viewmodel.MiuixSettingsViewModel
import com.abk.kernel.viewmodel.MainViewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import com.abk.kernel.miuix.animation.predictiveback.MiuixDefaultPredictiveBackHandler
import com.abk.kernel.miuix.animation.predictiveback.NonePredictiveBackHandler
import com.abk.kernel.miuix.animation.predictiveback.invokePopTransitionSpec
import com.abk.kernel.miuix.animation.predictiveback.invokePredictivePopTransitionSpec
import com.abk.kernel.miuix.animation.predictiveback.invokeTransitionSpec
import com.abk.kernel.ui.navigation3.LocalNavigator
import com.abk.kernel.ui.navigation3.Route
import com.abk.kernel.ui.navigation3.rememberNavigator

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase))
    }

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private var pendingModuleInstallUri by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingModuleInstallUri = extractModuleInstallUri(intent)?.toString()

        setContent {
            val vm: MainViewModel = viewModel()
            val state by vm.uiState.collectAsState()
            val miuixVm: MiuixSettingsViewModel = viewModel()
            val miuixState by miuixVm.state.collectAsState()
            var extensionBootstrapIssued by rememberSaveable { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                vm.checkRoot()
            }

            LaunchedEffect(state.termsAccepted) {
                if (state.termsAccepted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            LaunchedEffect(state.termsAccepted, state.oobeCompleted) {
                if (state.termsAccepted && !state.oobeCompleted) {
                    vm.maybeShowInitialOobe()
                }
            }

            LaunchedEffect(state.termsAccepted, state.showOobe, extensionBootstrapIssued) {
                if (state.termsAccepted && !state.showOobe && !extensionBootstrapIssued) {
                    extensionBootstrapIssued = true
                    startActivity(
                        Intent(this@MainActivity, AbkExtensionBootstrapActivity::class.java).apply {
                            putExtra("boot_action", "foreground")
                        }
                    )
                }
            }

            val themeContent: @Composable () -> Unit = {
                AppBackgroundHost(
                    backgroundUri = state.customBackgroundUri,
                    backgroundEnabled = state.backgroundImageEnabled,
                    uiSurfaceAlpha = state.uiSurfaceAlpha
                ) {
                    when {
                        !state.termsLoaded -> Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = androidx.compose.material3.MaterialTheme.colorScheme.surface
                        ) {}
                        !state.termsAccepted -> TermsAgreementDialog(
                            onAccept = vm::acceptTerms,
                            onDecline = { finishAffinity() }
                        )
                        else -> Box(modifier = Modifier.fillMaxSize()) {
                            AbkMainScaffold(
                                vm = vm,
                                miuixVm = miuixVm,
                                pendingModuleInstallUri = pendingModuleInstallUri,
                                onModuleInstallUriConsumed = { pendingModuleInstallUri = null }
                            )
                            if (state.showSyncPrompt && !state.showOobe) {
                                SyncPromptDialog(
                                    behindBy = state.behindBy,
                                    onSync = vm::syncFork,
                                    onDismiss = vm::dismissSyncPrompt
                                )
                            }
                            if (state.showOobe) {
                                CompositionLocalProvider(LocalUiSurfaceAlpha provides 1f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
                                            .zIndex(4f)
                                    ) {
                                        OobeScreen(vm)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            when (state.uiStyle) {
                "miuix" -> AbkMiuixTheme(
                    themeMode = state.themeMode,
                    dynamicColorEnabled = miuixState.miuixDynamicColorEnabled,
                    customThemeColorArgb = miuixState.miuixThemeColorArgb,
                    colorStyleName = miuixState.miuixColorStyle,
                    colorSpecName = miuixState.miuixColorSpec,
                    content = themeContent
                )
                else -> AbkTheme(
                    themeMode = state.themeMode,
                    dynamicColorEnabled = state.dynamicColorEnabled,
                    customThemeColorArgb = state.customThemeColorArgb,
                    customAccentColorArgb = state.customAccentColorArgb,
                    content = themeContent
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingModuleInstallUri = extractModuleInstallUri(intent)?.toString()
    }
}

@Composable
private fun SyncPromptDialog(
    behindBy: Int,
    onSync: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.sync_title)) },
        text = {
            Text(
                "${stringResource(R.string.sync_desc)}\n\n" +
                    stringResource(R.string.sync_behind_commits, behindBy)
            )
        },
        confirmButton = {
            Button(onClick = onSync) {
                Text(stringResource(R.string.sync_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.skip))
            }
        }
    )
}

@Composable
private fun TermsAgreementDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val scrollState = rememberScrollState()
    val canAccept by remember {
        derivedStateOf { scrollState.maxValue == 0 || scrollState.value >= scrollState.maxValue }
    }

    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = stringResource(R.string.terms_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TermsText(stringResource(R.string.terms_version))
                TermsText(stringResource(R.string.terms_effective_date))
                TermsText(stringResource(R.string.terms_intro))

                TermsSection(
                    stringResource(R.string.terms_section_usage),
                    stringResource(R.string.terms_usage_1),
                    stringResource(R.string.terms_usage_2)
                )
                TermsSection(
                    stringResource(R.string.terms_section_risk),
                    stringResource(R.string.terms_risk_1),
                    stringResource(R.string.terms_risk_2),
                    stringResource(R.string.terms_risk_3)
                )
                TermsSection(
                    stringResource(R.string.terms_section_legal),
                    stringResource(R.string.terms_legal_1),
                    stringResource(R.string.terms_legal_2),
                    stringResource(R.string.terms_legal_3)
                )
                TermsSection(
                    stringResource(R.string.terms_section_third_party),
                    stringResource(R.string.terms_third_party_1),
                    stringResource(R.string.terms_third_party_2),
                    stringResource(R.string.terms_third_party_3)
                )
                TermsSection(
                    stringResource(R.string.terms_section_privacy),
                    stringResource(R.string.terms_privacy_1),
                    stringResource(R.string.terms_privacy_2),
                    stringResource(R.string.terms_privacy_3)
                )
                TermsSection(
                    stringResource(R.string.terms_section_disclaimer),
                    stringResource(R.string.terms_disclaimer_1),
                    stringResource(R.string.terms_disclaimer_2),
                    stringResource(R.string.terms_disclaimer_3)
                )
                TermsText(stringResource(R.string.terms_accept_hint))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.terms_decline))
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                enabled = canAccept
            ) {
                Text(
                    if (canAccept) {
                        stringResource(R.string.terms_accept)
                    } else {
                        stringResource(R.string.terms_scroll_bottom)
                    }
                )
            }
        }
    )
}

@Composable
private fun TermsSection(title: String, vararg paragraphs: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
    paragraphs.forEach { paragraph ->
        TermsText(paragraph)
    }
}

@Composable
private fun TermsText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

private enum class AbkTab(@StringRes val labelRes: Int) {
    Status(R.string.nav_status),
    Build(R.string.nav_build),
    Modules(R.string.nav_modules),
    Flash(R.string.nav_flash),
    RuntimeHome(R.string.nav_home),
    InstalledModules(R.string.nav_installed_modules),
    RootAuth(R.string.nav_root_auth),
    Settings(R.string.nav_settings)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AbkMainScaffold(
    vm: MainViewModel,
    miuixVm: MiuixSettingsViewModel,
    pendingModuleInstallUri: String? = null,
    onModuleInstallUriConsumed: () -> Unit = {}
) {
    val state by vm.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val navigator = rememberNavigator()
    val navIsOnSubPage = navigator.backStackSize() > 1

    LaunchedEffect(Unit) {
        vm.markMainUiEntered()
    }

    var selectedTab by rememberSaveable { mutableStateOf(AbkTab.Status) }
    var flashDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var settingsChildPageVisible by rememberSaveable { mutableStateOf(false) }
    var buildPlanPageVisible by rememberSaveable { mutableStateOf(false) }
    var moduleRepositoryPageVisible by rememberSaveable { mutableStateOf(false) }
    var rootAuthDetailPageVisible by rememberSaveable { mutableStateOf(false) }
    var managerPatchPageVisible by rememberSaveable { mutableStateOf(false) }
    var lastBackAt by remember { mutableStateOf(0L) }
    val runtimeNativeManagerActive = state.hasNativeManagerPermission
    val visibleTabs = remember(state.runtimeNavigationEnabled, state.rootGranted, runtimeNativeManagerActive) {
        if (state.runtimeNavigationEnabled) {
            buildList {
                add(AbkTab.RuntimeHome)
                if (state.rootGranted) add(AbkTab.InstalledModules)
                add(AbkTab.Modules)
                if (runtimeNativeManagerActive) add(AbkTab.RootAuth)
                add(AbkTab.Settings)
            }
        } else {
            listOf(AbkTab.Status, AbkTab.Build, AbkTab.Modules, AbkTab.Flash, AbkTab.Settings)
        }
    }
    val activeTab = if (selectedTab in visibleTabs) selectedTab else visibleTabs.first()
    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    val contentPadding = PaddingValues(
        bottom = with(density) { bottomBarHeightPx.toDp() }
    )
    val childPageVisible = when (activeTab) {
        AbkTab.Build -> navIsOnSubPage || buildPlanPageVisible
        AbkTab.Modules -> navIsOnSubPage
        AbkTab.Flash -> flashDetailPageVisible
        AbkTab.Settings -> navIsOnSubPage || settingsChildPageVisible
        AbkTab.RootAuth -> rootAuthDetailPageVisible
        AbkTab.RuntimeHome -> managerPatchPageVisible
        else -> false
    }
    // Mutable state captured by closures; reassigned inside NavDisplay setup so any
    // downstream composable (e.g., bottom bar graphicsLayer) can read the latest
    // gesture state and recompose when it changes.
    var gestureState: NavigationEventState<SceneInfo<NavKey>>? by remember {
        mutableStateOf<NavigationEventState<SceneInfo<NavKey>>?>(null)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val miuixSnackbarHostState = remember {
        top.yukonga.miuix.kmp.basic.SnackbarHostState()
    }

    LaunchedEffect(state.snackbarMessage, state.snackbarLongDuration, state.error) {
        when (val snackbar = state.snackbarMessage) {
            null -> {
                val error = state.error ?: return@LaunchedEffect
                if (state.uiStyle == "miuix") {
                    miuixSnackbarHostState.showAbkMiuixSnackbar(message = error, longDuration = true)
                } else {
                    snackbarHostState.showAbkSnackbar(message = error, longDuration = true)
                }
                vm.clearError()
            }
            else -> {
                if (state.uiStyle == "miuix") {
                    miuixSnackbarHostState.showAbkMiuixSnackbar(
                        message = snackbar,
                        longDuration = state.snackbarLongDuration,
                    )
                } else {
                    snackbarHostState.showAbkSnackbar(
                        message = snackbar,
                        longDuration = state.snackbarLongDuration,
                    )
                }
                vm.clearSnackbar()
                if (state.error != null) vm.clearError()
            }
        }
    }

    LaunchedEffect(pendingModuleInstallUri) {
        if (!pendingModuleInstallUri.isNullOrBlank()) {
            if (!state.runtimeNavigationEnabled) vm.setRuntimeNavigationEnabled(true)
            selectedTab = AbkTab.InstalledModules
        }
    }

    LaunchedEffect(activeTab) {
        when (activeTab) {
            AbkTab.Build -> {
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.Flash -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
                // Flash NavHost is recreated on tab entry — clear stale saveable
                // state so the bottom bar does not hide until a detail opens.
                flashDetailPageVisible = false
            }
            AbkTab.Modules -> {
                buildPlanPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.Settings -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.RootAuth -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                managerPatchPageVisible = false
            }
            AbkTab.RuntimeHome -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
            }
            else -> {
                buildPlanPageVisible = false
                moduleRepositoryPageVisible = false
                flashDetailPageVisible = false
                settingsChildPageVisible = false
                rootAuthDetailPageVisible = false
                managerPatchPageVisible = false
            }
        }
    }

    LaunchedEffect(visibleTabs, selectedTab, state.runtimeNavigationEnabled) {
        if (selectedTab !in visibleTabs) {
            selectedTab = if (state.runtimeNavigationEnabled) AbkTab.RuntimeHome else AbkTab.Status
        }
    }

    fun handleTopLevelBack() {
        val now = System.currentTimeMillis()
        if (now - lastBackAt <= EXIT_BACK_INTERVAL_MS) {
            context.findActivity()?.finish()
        } else {
            lastBackAt = now
            Toast.makeText(context, context.getString(R.string.press_again_exit), Toast.LENGTH_SHORT).show()
        }
    }

    if (!childPageVisible) {
        BackHandler(onBack = ::handleTopLevelBack)
    }

    val miuixMode = state.uiStyle == "miuix"
    val surfaceColor = if (miuixMode) MiuixTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
    val floatingGlassBackdrop = rememberLayerBackdrop {
        drawRect(surfaceColor)
        drawContent()
    }
    val blurEnabledForGlass = miuixMode && state.miuixFloatingBottomBarEnabled && state.miuixLiquidGlassEnabled
    val blurBackdrop = rememberBlurBackdrop(state.miuixBlurEnabled, surfaceColor)

    // Bar slide offset (0f = visible, -1f = hidden left). Single LaunchedEffect drives it:
    //   gesture in progress   → snapTo(-[1–progress]) to follow finger
    //   gesture ends       → 200ms animate to final state (smooth finish)
    //   regular push/pop  → 300ms slide animation
    val barSlideOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val lastGestureProgress = remember { mutableStateOf(0f) }
    val predictiveBackProgress by remember {
        androidx.compose.runtime.derivedStateOf {
            val inProgress = gestureState?.transitionState
                as? androidx.navigationevent.NavigationEventTransitionState.InProgress
            if (inProgress?.direction == androidx.navigationevent.NavigationEventTransitionState.TRANSITIONING_BACK) {
                inProgress.latestEvent.progress
            } else 0f
        }
    }
    LaunchedEffect(childPageVisible, predictiveBackProgress) {
        if (predictiveBackProgress > 0f) {
            barSlideOffset.snapTo(-(1f - predictiveBackProgress))
            lastGestureProgress.value = predictiveBackProgress
        } else {
            val target = if (childPageVisible) -1f else 0f
            if (miuixMode) {
                // MIUIX mode: animate the transition
                val fromGesture = lastGestureProgress.value > 0f
                barSlideOffset.animateTo(
                    targetValue = target,
                    animationSpec = androidx.compose.animation.core.tween(
                        durationMillis = if (fromGesture) 200 else 300,
                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                    )
                )
                if (fromGesture) lastGestureProgress.value = 0f
            } else {
                // MD3 mode: snap immediately without animation
                barSlideOffset.snapTo(target)
                lastGestureProgress.value = 0f
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(appPageBackgroundColor(uiSurfaceColor(MaterialTheme.colorScheme.surface)))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .zIndex(2f)
                .graphicsLayer {
                    translationX = size.width * barSlideOffset.value
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeightPx = it.height }
            ) {
            when {
                miuixMode && state.miuixFloatingBottomBarEnabled -> {
                    MiuixFloatingBottomBar(
                        modifier = Modifier.align(Alignment.Center),
                        items = visibleTabs.map { tab ->
                            FloatingTabItem(
                                label = tab.displayLabel(state.rootGranted),
                                icon = when (tab) {
                                    AbkTab.Status -> Icons.Default.Home
                                    AbkTab.Build -> Icons.Default.RocketLaunch
                                    AbkTab.Modules -> Icons.Default.LibraryBooks
                                    AbkTab.Flash -> if (state.rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen
                                    AbkTab.RuntimeHome -> Icons.Default.Memory
                                    AbkTab.InstalledModules -> Icons.Default.Extension
                                    AbkTab.RootAuth -> Icons.Default.AdminPanelSettings
                                    AbkTab.Settings -> Icons.Default.Settings
                                },
                                onClick = { if (!childPageVisible) selectedTab = tab },
                            )
                        },
                        selectedIndex = visibleTabs.indexOf(activeTab).coerceAtLeast(0),
                        backdrop = floatingGlassBackdrop,
                        isBlurEnabled = state.miuixLiquidGlassEnabled,
                        isLiquidGlassEnabled = state.miuixLiquidGlassEnabled,
                    )
                }
                miuixMode -> {
                    BlurredBar(blurBackdrop, surfaceColor) {
                        MiuixNavigationBar(
                            modifier = Modifier.fillMaxWidth(),
                            color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface
                        ) {
                            visibleTabs.forEach { tab ->
                                val tabIcon = when (tab) {
                                    AbkTab.Status -> Icons.Default.Home
                                    AbkTab.Build -> Icons.Default.RocketLaunch
                                    AbkTab.Modules -> Icons.Default.LibraryBooks
                                    AbkTab.Flash -> if (state.rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen
                                    AbkTab.RuntimeHome -> Icons.Default.Memory
                                    AbkTab.InstalledModules -> Icons.Default.Extension
                                    AbkTab.RootAuth -> Icons.Default.AdminPanelSettings
                                    AbkTab.Settings -> Icons.Default.Settings
                                }
                                MiuixNavigationBarItem(
                                    modifier = Modifier.weight(1f),
                                    selected = activeTab == tab,
                                    onClick = { if (!childPageVisible) selectedTab = tab },
                                    enabled = !childPageVisible,
                                    icon = tabIcon,
                                    label = tab.displayLabel(state.rootGranted)
                                )
                            }
                        }
                    }
                }
                else -> {
                    BlurredBar(blurBackdrop, surfaceColor) {
                        NavigationBar(
                            containerColor = if (blurBackdrop != null) Color.Transparent else uiSurfaceColor(MaterialTheme.colorScheme.surfaceContainer),
                            tonalElevation = 0.dp
                        ) {
                            visibleTabs.forEach { tab ->
                                NavigationBarItem(
                                    selected = activeTab == tab,
                                    onClick = { if (!childPageVisible) selectedTab = tab },
                                    enabled = !childPageVisible,
                                    alwaysShowLabel = false,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    icon = {
                                        Icon(
                                            imageVector = when (tab) {
                                                AbkTab.Status -> Icons.Default.Home
                                                AbkTab.Build -> Icons.Default.RocketLaunch
                                                AbkTab.Modules -> Icons.Default.LibraryBooks
                                                AbkTab.Flash -> if (state.rootGranted) Icons.Default.FlashOn else Icons.Default.FolderOpen
                                                AbkTab.RuntimeHome -> Icons.Default.Memory
                                                AbkTab.InstalledModules -> Icons.Default.Extension
                                                AbkTab.RootAuth -> Icons.Default.AdminPanelSettings
                                                AbkTab.Settings -> Icons.Default.Settings
                                            },
                                            contentDescription = tab.displayLabel(state.rootGranted)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.displayLabel(state.rootGranted),
                                            maxLines = 2,
                                            softWrap = true,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
        }
        CompositionLocalProvider(LocalNavigator provides navigator) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            when {
                                blurEnabledForGlass -> Modifier.layerBackdrop(floatingGlassBackdrop)
                                blurBackdrop != null -> Modifier.layerBackdrop(blurBackdrop)
                                else -> Modifier
                            }
                        )
                ) {
                    // ReSukiSU-style scene state integration for predictive back.
                    // The handler is recreated on every state change (remember key), so
                    // downstream closures below always read the latest handler via this val.
                    val predictiveBackHandler = remember(state.miuixPredictiveBackEnabled) {
                        if (state.miuixPredictiveBackEnabled) MiuixDefaultPredictiveBackHandler()
                        else NonePredictiveBackHandler()
                    }
                    val navigationScope = rememberCoroutineScope()
                    val sceneBackgroundColor = if (state.uiStyle == "miuix") {
                        MiuixTheme.colorScheme.surface
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    }

                    val entries = rememberDecoratedNavEntries(
                        backStack = navigator.backStack,
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            NavEntryDecorator<NavKey>(
                                onPop = { key ->
                                    predictiveBackHandler.onPagePop(key, navigationScope)
                                },
                                decorate = { entry ->
                                    with(predictiveBackHandler) {
                                        Box(
                                            modifier = Modifier
                                                .predictiveBackAnnotation(
                                                    gestureState?.transitionState,
                                                    entry.contentKey,
                                                    navigator.current()
                                                )
                                                .background(sceneBackgroundColor)
                                        ) {
                                            entry.Content()
                                        }
                                    }
                                }
                            )
                        ),
                        entryProvider = entryProvider {
                            entry<Route.Main> {
                                if (state.uiStyle == "miuix") {
                                    val pagerState = rememberPagerState(
                                        initialPage = visibleTabs.indexOf(activeTab).coerceAtLeast(0),
                                        pageCount = { visibleTabs.size }
                                    )

                                    // Flag to suppress pagerState -> selectedTab sync
                                    // during programmatic scrolling (prevents feedback loop).
                                    var isProgrammaticScroll by remember { mutableStateOf(false) }

                                    // Sync pagerState -> selectedTab (only during user swipe)
                                    LaunchedEffect(pagerState.currentPage) {
                                        if (!isProgrammaticScroll &&
                                            pagerState.currentPage in visibleTabs.indices) {
                                            selectedTab = visibleTabs[pagerState.currentPage]
                                        }
                                    }

                                    // Sync selectedTab -> pagerState
                                    LaunchedEffect(activeTab) {
                                        val index = visibleTabs.indexOf(activeTab)
                                        if (index >= 0 && pagerState.currentPage != index) {
                                            isProgrammaticScroll = true
                                            try {
                                                pagerState.animateScrollToPage(index)
                                            } finally {
                                                isProgrammaticScroll = false
                                            }
                                        }
                                    }

                                    HorizontalPager(
                                        state = pagerState,
                                        modifier = Modifier.fillMaxSize(),
                                        beyondViewportPageCount = visibleTabs.size
                                    ) { page ->
                                        when (visibleTabs[page]) {
                                            AbkTab.Status -> com.abk.kernel.miuix.ui.screens.StatusScreenMiuix(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                runtimeNavigationEnabled = state.runtimeNavigationEnabled,
                                                onToggleRuntimeNavigation = { vm.setRuntimeNavigationEnabled(true) }
                                            )
                                            AbkTab.Build -> com.abk.kernel.miuix.ui.screens.BuildScreenMiuix(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onPlanPageVisibleChange = { buildPlanPageVisible = it },
                                                onNavigateToStatus = { selectedTab = AbkTab.Status }
                                            )
                                            AbkTab.Modules -> com.abk.kernel.miuix.ui.screens.ModuleRepositoryScreenMiuix(
                                                vm = vm,
                                                mode = if (state.runtimeNavigationEnabled) {
                                                    com.abk.kernel.ui.screens.ModuleRepositoryMode.RUNTIME_STANDARD
                                                } else {
                                                    com.abk.kernel.ui.screens.ModuleRepositoryMode.BUILD_ABK
                                                },
                                                outerPadding = contentPadding,
                                                onRepositoryPageVisibleChange = { moduleRepositoryPageVisible = it }
                                            )
                                            AbkTab.Flash -> FlashScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onDetailPageVisibleChange = { flashDetailPageVisible = it }
                                            )
                                            AbkTab.RuntimeHome -> RuntimeHomeScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onSwitchToClassic = { vm.setRuntimeNavigationEnabled(false) },
                                                onManagerPatchPageVisibleChange = { managerPatchPageVisible = it }
                                            )
                                            AbkTab.InstalledModules -> InstalledModulesScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                pendingModuleInstallUri = pendingModuleInstallUri,
                                                onPendingModuleInstallUriConsumed = onModuleInstallUriConsumed
                                            )
                                            AbkTab.RootAuth -> RootAuthorizationScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onDetailPageVisibleChange = { rootAuthDetailPageVisible = it }
                                            )
                                            AbkTab.Settings -> com.abk.kernel.miuix.ui.screens.SettingsScreenMiuix(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onOpenInstalledModules = {
                                                    if (!state.runtimeNavigationEnabled) vm.setRuntimeNavigationEnabled(true)
                                                    selectedTab = if (state.rootGranted) {
                                                        AbkTab.InstalledModules
                                                    } else {
                                                        AbkTab.RuntimeHome
                                                    }
                                                }
                                            )
                                        }
                                    }
                                } else {
                                    AnimatedContent(
                                        targetState = activeTab,
                                        transitionSpec = {
                                            val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                                            (
                                                fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                                                    slideInHorizontally(
                                                        animationSpec = motionScheme.defaultSpatialSpec()
                                                    ) { width -> direction * width / 4 }
                                                ) togetherWith (
                                                fadeOut(animationSpec = motionScheme.fastEffectsSpec()) +
                                                    slideOutHorizontally(
                                                        animationSpec = motionScheme.fastSpatialSpec()
                                                    ) { width -> -direction * width / 6 }
                                                )
                                        },
                                        label = "abk-tab"
                                    ) { tab ->
                                        when (tab) {
                                            AbkTab.Status -> StatusScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                runtimeNavigationEnabled = state.runtimeNavigationEnabled,
                                                onToggleRuntimeNavigation = { vm.setRuntimeNavigationEnabled(true) }
                                            )
                                            AbkTab.Build -> BuildScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onPlanPageVisibleChange = { buildPlanPageVisible = it },
                                                onNavigateToStatus = { selectedTab = AbkTab.Status }
                                            )
                                            AbkTab.Modules -> ModuleRepositoryScreen(
                                                vm = vm,
                                                mode = if (state.runtimeNavigationEnabled) {
                                                    com.abk.kernel.ui.screens.ModuleRepositoryMode.RUNTIME_STANDARD
                                                } else {
                                                    com.abk.kernel.ui.screens.ModuleRepositoryMode.BUILD_ABK
                                                },
                                                outerPadding = contentPadding,
                                                onRepositoryPageVisibleChange = { moduleRepositoryPageVisible = it }
                                            )
                                            AbkTab.Flash -> FlashScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onDetailPageVisibleChange = { flashDetailPageVisible = it }
                                            )
                                            AbkTab.RuntimeHome -> RuntimeHomeScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onSwitchToClassic = { vm.setRuntimeNavigationEnabled(false) },
                                                onManagerPatchPageVisibleChange = { managerPatchPageVisible = it }
                                            )
                                            AbkTab.InstalledModules -> InstalledModulesScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                pendingModuleInstallUri = pendingModuleInstallUri,
                                                onPendingModuleInstallUriConsumed = onModuleInstallUriConsumed
                                            )
                                            AbkTab.RootAuth -> RootAuthorizationScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onDetailPageVisibleChange = { rootAuthDetailPageVisible = it }
                                            )
                                            AbkTab.Settings -> SettingsScreen(
                                                vm = vm,
                                                outerPadding = contentPadding,
                                                onChildPageVisibleChange = { settingsChildPageVisible = it },
                                                onOpenInstalledModules = {
                                                    if (!state.runtimeNavigationEnabled) vm.setRuntimeNavigationEnabled(true)
                                                    selectedTab = if (state.rootGranted) {
                                                        AbkTab.InstalledModules
                                                    } else {
                                                        AbkTab.RuntimeHome
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            entry<Route.ThemeSettings> {
                                com.abk.kernel.miuix.ui.screens.ThemeSettingsScreenMiuix(
                                    vm = vm,
                                    miuixVm = miuixVm,
                                    onBack = { navigator.pop() }
                                )
                            }
                            entry<Route.AppProfileTemplates> {
                                // TODO(Phase A.2): Implement AppProfileTemplates screen
                            }
                            entry<Route.ManagerTools> {
                                // TODO(Phase A.2): Implement ManagerTools screen
                            }
                            entry<Route.About> {
                                com.abk.kernel.miuix.ui.screens.AboutScreenMiuix()
                            }
                            entry<Route.OpenSourceLicenses> {
                                com.abk.kernel.miuix.ui.screens.OpenSourceLicensesScreenMiuix()
                            }
                            entry<Route.ExtensionManager> {
                                com.abk.kernel.miuix.ui.screens.ExtensionManagerScreenMiuix()
                            }
                            entry<Route.BuildPlanLibrary> {
                                com.abk.kernel.miuix.ui.screens.BuildPlanLibraryScreenMiuix(vm = vm)
                            }
                            entry<Route.BuildQueue> {
                                com.abk.kernel.miuix.ui.screens.BuildQueueScreenMiuix(vm = vm)
                            }
                            entry<Route.BuildModuleRepoSettings> {
                                com.abk.kernel.miuix.ui.screens.BuildModuleRepoSettingsScreenMiuix(vm = vm)
                            }
                            entry<Route.RuntimeModuleRepoSettings> {
                                com.abk.kernel.miuix.ui.screens.RuntimeModuleRepoSettingsScreenMiuix(vm = vm)
                            }
                        }
                    )

                    val sceneState = rememberSceneState(
                        entries = entries,
                        sceneStrategies = listOf(SinglePaneSceneStrategy()),
                        onBack = { navigator.pop() }
                    )
                    gestureState = rememberNavigationEventState(
                        currentInfo = SceneInfo(sceneState.currentScene),
                        backInfo = sceneState.previousScenes.map { SceneInfo(it) }
                    )

                    NavigationBackHandler(
                        sceneState = sceneState,
                        state = gestureState!!,
                        onBack = { navigator.pop() }
                    )

                    NavDisplay(
                        sceneState = sceneState,
                        navigationEventState = gestureState!!,
                        transitionSpec = {
                            val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                            predictiveBackHandler.invokeTransitionSpec(scope)
                        },
                        popTransitionSpec = {
                            val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                            predictiveBackHandler.invokePopTransitionSpec(scope)
                        },
                        predictivePopTransitionSpec = { swipeEdge ->
                            val scope: AnimatedContentTransitionScope<Scene<NavKey>> = this
                            predictiveBackHandler.invokePredictivePopTransitionSpec(scope, swipeEdge)
                        },
                        transitionEffects = NavDisplayTransitionEffects.Default
                    )
                }
            }
        }
        val snackbarModifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(
                bottom = with(density) { if (childPageVisible) 0.dp else bottomBarHeightPx.toDp() } + 10.dp
            )
            .zIndex(4f)
        if (state.uiStyle == "miuix") {
            AbkMiuixSnackbarHost(
                hostState = miuixSnackbarHostState,
                modifier = snackbarModifier
            )
        } else {
            AbkSnackbarHost(
                hostState = snackbarHostState,
                modifier = snackbarModifier
            )
        }
    }
}

@Composable
private fun AbkTab.displayLabel(rootGranted: Boolean): String = when (this) {
    AbkTab.Flash -> stringResource(if (rootGranted) labelRes else R.string.nav_files)
    else -> stringResource(labelRes)
}

private fun extractModuleInstallUri(intent: Intent?): Uri? {
    if (intent == null) return null
    val uri = when (intent.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> intent.streamUri() ?: intent.firstClipUri()
        Intent.ACTION_SEND_MULTIPLE -> intent.streamUris().firstOrNull() ?: intent.firstClipUri()
        else -> null
    } ?: return null
    return uri.takeIf { isLikelyModuleZipIntent(intent.type, it) }
}

private fun Intent.streamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }

private fun Intent.streamUris(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }

private fun Intent.firstClipUri(): Uri? =
    clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

private fun isLikelyModuleZipIntent(mimeType: String?, uri: Uri): Boolean {
    val cleanMime = mimeType?.lowercase().orEmpty()
    val path = uri.toString().lowercase()
    return cleanMime in MODULE_ZIP_MIME_TYPES || path.endsWith(".zip")
}

private const val EXIT_BACK_INTERVAL_MS = 2_000L
private val MODULE_ZIP_MIME_TYPES = setOf(
    "application/zip",
    "application/x-zip",
    "application/x-zip-compressed",
    "application/octet-stream"
)
