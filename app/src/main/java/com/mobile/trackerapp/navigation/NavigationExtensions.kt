package com.mobile.trackerapp.navigation

import androidx.navigation.NavHostController

fun NavHostController.goTo(route: String) {
    val destination = when (route) {
        else -> route
    }
    val currentBase = currentDestination?.route?.substringBefore("?")
    if (!destination.contains("?") && currentBase == destination) return
    navigate(destination) { launchSingleTop = true }
}

/** Opens a post-auth destination without retaining splash/onboarding behind it. */
fun NavHostController.goToRoot(route: String) {
    navigate(route) {
        popUpTo(Screen.Splash.route) { inclusive = true }
        launchSingleTop = true
    }
}
fun Screen.nextScreen(): Screen {
    val index = navigationSequence.indexOfFirst { it.route == route }
    return navigationSequence[(index + 1).coerceAtMost(navigationSequence.lastIndex)]
}
