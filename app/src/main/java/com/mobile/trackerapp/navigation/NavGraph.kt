package com.mobile.trackerapp.navigation

import android.app.Activity
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.google.firebase.auth.FirebaseAuth
import com.mobile.trackerapp.language.LanguageRoute
import com.mobile.trackerapp.splash.SplashRoute
import com.mobile.trackerapp.utils.OnboardingPreferences

@Composable
fun NavGraph(
    modifier: Modifier = Modifier,
    notificationRoute: String? = null,
    onNotificationRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    fun next(screen: Screen) = { navController.goTo(screen.nextScreen().route) }

    // Hoist a SINGLE persistent banner to the navigation root so it survives tab
    // switches and is never recreated. Only show it on the 5 primary tabs.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var lastRootBackPressAt by remember { mutableLongStateOf(0L) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    val rootRoutes = setOf(
        Screen.Auth.route,
        Screen.Home.route,
    )

    LaunchedEffect(currentRoute) {
        if (currentRoute !in rootRoutes) {
            showExitConfirmation = false
            lastRootBackPressAt = 0L
        }
    }

    BackHandler(enabled = currentRoute in rootRoutes && !showExitConfirmation) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRootBackPressAt <= 2_000L) {
            showExitConfirmation = true
        } else {
            lastRootBackPressAt = now
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
    if (showExitConfirmation) {
        AlertDialog(
            onDismissRequest = { showExitConfirmation = false },
            title = { Text("Exit Lumora AI?") },
            text = { Text("Do you want to exit the app?") },
            dismissButton = {
                TextButton(onClick = { showExitConfirmation = false }) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(onClick = { (context as? Activity)?.finish() }) { Text("Exit") }
            },
        )
    }

    LaunchedEffect(notificationRoute, currentRoute) {
        val route = notificationRoute?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        if (currentRoute == null || currentRoute == Screen.Splash.route) return@LaunchedEffect
        if (route == Screen.UninstallConfirm.route) {
            navController.goTo(Screen.Splash.route)
            return@LaunchedEffect
        }
        navController.goTo(route)
        onNotificationRouteConsumed()
    }

    val showBanner = currentRoute != null && (
        currentRoute == "home" ||
        currentRoute == "templates" ||
        currentRoute == "aitools" ||
        currentRoute == "history" ||
        currentRoute == "profile" ||
        currentRoute.startsWith("createhub")
    )

    Column(modifier = modifier.fillMaxSize().background(Color(0xFF081020))) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        modifier = Modifier.weight(1f),
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        composable(Screen.Splash.route) {
            SplashRoute(
                isUninstallFlow = notificationRoute == Screen.UninstallConfirm.route,
                onNext = {
                    val user = FirebaseAuth.getInstance().currentUser
                    val pendingRoute = notificationRoute?.takeIf { it.isNotBlank() }
                    val target = pendingRoute ?: if (user != null && OnboardingPreferences.isCompleted(context)) {
                        Screen.Home.route
                    } else {
                        Screen.Language.route
                    }
                    if (target == Screen.Home.route) navController.goToRoot(target) else navController.goTo(target)
                    if (pendingRoute != null) onNotificationRouteConsumed()
                }
            )
        }
        composable(
            route = Screen.Language.route + "?source={source}",
            arguments = listOf(
                navArgument("source") {
                    type = NavType.StringType
                    defaultValue = "onboarding"
                }
            )
        ) { backStackEntry ->
            val source = backStackEntry.arguments?.getString("source")
            LanguageRoute(
                source = source,
                onNext = {
                    if (source == "settings" || source == "profile") {
                        navController.popBackStack()
                    } else {
                        navController.goTo(Screen.Onboarding.route)
                    }
                }
            )
        }
        composable(Screen.Onboarding.route) {
//            OnboardingRoute(
//                onNext = {
//                    coroutineScope.launch {
//                        if (FirebaseAuth.getInstance().currentUser == null) {
//                            if (GuestIdentity.isTrialExhausted(context)) {
//                                navController.goTo(Screen.Auth.route)
//                                return@launch
//                            } else {
//                                GuestIdentity.markTrialStarted(context)
//                                AuthRepository().loginAnonymouslyAndSync()
//                            }
//                        }
//                        OnboardingPreferences.markCompleted(context)
//                        navController.goToRoot(Screen.Home.route)
//                    }
//                },
//            )
        }
    }
        if (showBanner) {
            // Banner sits flush directly under the nav row (the nav bar no longer
            // adds its own bottom inset on primary tabs). The system-nav inset is
            // applied BELOW the banner so there's no black gap between them.
            com.mobile.trackerapp.ads.banner.PersistentBannerSlot(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF11192B)),
            )
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF11192B))
                    .windowInsetsBottomHeight(
                        WindowInsets.navigationBars
                    ),
            )
        }
    } // end Column
}
