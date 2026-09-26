package com.nufo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.ui.unit.dp
import com.nufo.app.ui.LocalNavScope
import com.nufo.app.ui.LocalSharedScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.SystemClock
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.navigation.NavBackStackEntry
import android.view.animation.PathInterpolator
import androidx.compose.runtime.mutableStateOf
import com.nufo.app.ui.theme.LocalAppReady
import com.nufo.app.ui.theme.Motion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nufo.app.ui.screens.HistoryScreen
import com.nufo.app.ui.screens.HomeScreen
import com.nufo.app.ui.screens.PhotoScreen
import com.nufo.app.ui.screens.ResultScreen
import com.nufo.app.ui.screens.ScannerScreen
import com.nufo.app.ui.screens.SearchScreen
import com.nufo.app.ui.screens.SettingsScreen
import com.nufo.app.ui.screens.WelcomeScreen
import com.nufo.app.data.ThemeMode
import com.nufo.app.ui.theme.LocalNufoColors
import com.nufo.app.ui.theme.NufoTheme
import com.nufo.app.ui.theme.nufoSpring

object Routes {
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val SCANNER = "scanner"
    const val PHOTO = "photo?source={source}"
    const val RESULT = "result"
    fun photo(source: String) = "photo?source=$source"
}

private data class Tab(val route: String, @param:androidx.annotation.StringRes val label: Int, val icon: ImageVector)
private val TABS = listOf(
    Tab(Routes.HOME, R.string.nav_scan, Icons.Outlined.QrCodeScanner),
    Tab(Routes.SEARCH, R.string.nav_search, Icons.Outlined.Search),
    Tab(Routes.HISTORY, R.string.nav_history, Icons.Outlined.History),
    Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
)

class MainActivity : ComponentActivity() {
    private val vm: NufoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Hold the splash until settings are read, so we know whether to show Welcome. History loads during the
        // opening beat (the database is warmed up in NufoApp), and its placeholders stay hidden until the hand-off.
        splash.setKeepOnScreenCondition { vm.settings.value == null }
        // A recreated activity (rotation, language switch) shows no splash, so its content is ready at once.
        val appReady = mutableStateOf(savedInstanceState != null)
        splash.setOnExitAnimationListener { provider ->
            // Let the 700 ms opening beat finish, then hand over: the mark leaves fast and small (it must never
            // hang over the first screen), the background dissolves, and the first screen plays its entrance in view.
            val beatLeft = (provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis - SystemClock.uptimeMillis())
                .coerceIn(0L, 700L)
            val easeOut = PathInterpolator(0.23f, 1f, 0.32f, 1f) // Motion.EaseOut
            provider.iconView.animate().scaleX(0.92f).scaleY(0.92f).alpha(0f)
                .setStartDelay(beatLeft).setDuration(Motion.FAST.toLong()).setInterpolator(easeOut).start()
            provider.view.animate().alpha(0f).setStartDelay(beatLeft).setDuration(Motion.BASE.toLong()).setInterpolator(easeOut)
                .withStartAction { appReady.value = true }
                .withEndAction { provider.remove() }.start()
        }
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            val s = settings ?: return@setContent
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val dark = when (s.theme) { ThemeMode.System -> systemDark; ThemeMode.Light -> false; ThemeMode.Dark -> true }
            // Status/navigation bar icons must follow the in-app theme, not only the system one.
            androidx.compose.runtime.LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(style, style)
            }
            NufoTheme(s.theme) {
                CompositionLocalProvider(LocalAppReady provides appReady.value) {
                    NufoNav(vm, startOnWelcome = !s.onboarded)
                    UpdatePrompt(vm)
                }
            }
        }
    }
}
@Composable
private fun UpdatePrompt(vm: NufoViewModel) {
    val update = vm.updatePrompt.collectAsStateWithLifecycle().value ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = vm::dismissUpdatePrompt,
        title = { Text(stringResource(R.string.s_update_title)) },
        text = { Text(stringResource(R.string.s_update_body, update.version)) },
        confirmButton = {
            androidx.compose.material3.TextButton({
                vm.dismissUpdatePrompt()
                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(update.url)))
            }) { Text(stringResource(R.string.s_update_download)) }
        },
        dismissButton = { androidx.compose.material3.TextButton(vm::dismissUpdatePrompt) { Text(stringResource(R.string.s_update_later)) } },
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun NufoNav(vm: NufoViewModel, startOnWelcome: Boolean) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = route in TABS.map { it.route }
    val online by vm.online.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Column {
                // Offline notice sits above the tabs and pads the screen, so it never hides list content.
                AnimatedVisibility(
                    visible = !online && showBar,
                    enter = expandVertically(nufoSpring()) + fadeIn(Motion.fadeInSpec()),
                    exit = shrinkVertically(nufoSpring()) + fadeOut(Motion.fadeOutSpec()),
                ) { OfflineBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                AnimatedVisibility(
                    visible = showBar,
                    enter = slideInVertically(nufoSpring()) { it } + fadeIn(Motion.fadeInSpec()),
                    exit = slideOutVertically(nufoSpring()) { it } + fadeOut(Motion.fadeOutSpec()),
                ) { BottomBar(nav, route) }
            }
        },
    ) { padding ->
      // Read live inside each destination: the graph is built once, while the bar may still be hidden.
      val bottomInset = androidx.compose.runtime.rememberUpdatedState(padding.calculateBottomPadding())
      Box(Modifier.fillMaxSize()) {
      SharedTransitionLayout {
      CompositionLocalProvider(LocalSharedScope provides this) {
        NavHost(
            nav,
            startDestination = if (startOnWelcome) Routes.WELCOME else Routes.HOME,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            // Every screen change uses one of two Motion patterns: peers (tabs, welcome to home) fade through;
            // everything else pushes, and back is the exact mirror of the push.
            enterTransition = { if (isPeerChange()) Motion.peerEnter() else Motion.pushEnter() },
            exitTransition = { if (isPeerChange()) Motion.peerExit() else Motion.pushExit() },
            popEnterTransition = { if (isPeerChange()) Motion.peerEnter() else Motion.popEnter() },
            popExitTransition = { if (isPeerChange()) Motion.peerExit() else Motion.popExit() },
        ) {
            composable(Routes.WELCOME) {
                WelcomeScreen(onStart = {
                    vm.finishOnboarding()
                    nav.navigate(Routes.HOME) { popUpTo(Routes.WELCOME) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                Box(Modifier.padding(bottom = bottomInset.value)) { CompositionLocalProvider(LocalNavScope provides this@composable) {
                    HomeScreen(
                        vm,
                        onScanBarcode = { nav.navigate(Routes.SCANNER) },
                        onTakePhoto = { nav.navigate(Routes.photo("camera")) },
                        onPickGallery = { nav.navigate(Routes.photo("gallery")) },
                        onSearch = { q -> if (q != null) vm.setQuery(q); nav.navigateTab(Routes.SEARCH) },
                        onOpen = { vm.openProduct(it); nav.navigate(Routes.RESULT) },
                    )
                } }
            }
            composable(Routes.SEARCH) {
                Box(Modifier.padding(bottom = bottomInset.value)) { CompositionLocalProvider(LocalNavScope provides this@composable) { SearchScreen(vm, onOpen = { vm.openHit(it); nav.navigate(Routes.RESULT) }, onBarcode = { vm.openBarcode(it); nav.navigate(Routes.RESULT) }) } }
            }
            composable(Routes.HISTORY) {
                Box(Modifier.padding(bottom = bottomInset.value)) { CompositionLocalProvider(LocalNavScope provides this@composable) {
                    HistoryScreen(vm, onOpen = { vm.openProduct(it); nav.navigate(Routes.RESULT) }, onScan = { nav.navigate(Routes.SCANNER) })
                } }
            }
            composable(Routes.SETTINGS) { Box(Modifier.padding(bottom = bottomInset.value)) { SettingsScreen(vm) } }
            composable(Routes.SCANNER) {
                ScannerScreen(
                    onBarcode = { code ->
                        vm.openBarcode(code)
                        nav.navigate(Routes.RESULT) { popUpTo(Routes.SCANNER) { inclusive = true } }
                    },
                    onGallery = { nav.navigate(Routes.photo("gallery")) { popUpTo(Routes.SCANNER) { inclusive = true } } },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(Routes.PHOTO) { e ->
                PhotoScreen(
                    source = e.arguments?.getString("source") ?: "camera",
                    onBarcode = { code ->
                        vm.openBarcode(code)
                        nav.navigate(Routes.RESULT) { popUpTo(Routes.PHOTO) { inclusive = true } }
                    },
                    onDish = { key, name, photo ->
                        if (vm.openDish(key, name, photo)) nav.navigate(Routes.RESULT) { popUpTo(Routes.PHOTO) { inclusive = true } }
                        else { vm.setQuery(name); nav.navigate(Routes.SEARCH) { popUpTo(Routes.HOME); launchSingleTop = true } }
                    },
                    onConfirm = { query ->
                        vm.setQuery(query)
                        nav.navigate(Routes.SEARCH) {
                            popUpTo(Routes.HOME)
                            launchSingleTop = true
                        }
                    },
                    onClose = { nav.popBackStack() },
                )
            }
            composable(Routes.RESULT) {
                CompositionLocalProvider(LocalNavScope provides this) {
                    ResultScreen(vm, onBack = { nav.popBackStack() }, onSearch = { q ->
                        vm.setQuery(q); nav.navigateTab(Routes.SEARCH)
                    })
                }
            }
        }
      }
      }
      }
    }
}

private val PEERS = TABS.map { it.route } + Routes.WELCOME

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isPeerChange() =
    initialState.destination.route in PEERS && targetState.destination.route in PEERS

private fun NavHostController.navigateTab(route: String) = navigate(route) {
    popUpTo(Routes.HOME) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun BottomBar(nav: NavHostController, current: String?) {
    NavigationBar(containerColor = LocalNufoColors.current.card, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
        TABS.forEach { tab ->
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { if (current != tab.route) nav.navigateTab(tab.route) },
                icon = { Icon(tab.icon, null) },
                label = { Text(androidx.compose.ui.res.stringResource(tab.label), maxLines = 1) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun OfflineBanner(modifier: Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.inverseSurface).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(androidx.compose.ui.res.stringResource(R.string.offline_banner), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface)
    }
}