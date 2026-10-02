package com.mobile.trackerapp.navigation

sealed class Screen(val route: String, val title: String) {
    data object Splash : Screen("splash", "Splash")
    data object Language : Screen("language", "Language")
    data object Onboarding : Screen("onboarding", "Onboarding")
    data object Auth : Screen("auth", "Auth")
    data object Home : Screen("home", "Home")
    data object UninstallConfirm : Screen("uninstall/confirm", "Confirm Uninstall")
    data object UninstallSurvey : Screen("uninstall/survey", "Uninstall Survey")

}

val navigationSequence = listOf(
    Screen.Splash,
    Screen.Language,
    Screen.Onboarding,
    Screen.Auth,
    Screen.Home,
    Screen.UninstallConfirm,
    Screen.UninstallSurvey
)
