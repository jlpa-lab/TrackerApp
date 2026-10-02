package com.mobile.trackerapp.splash

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.deep.lumoraai.feature.splash.SplashViewModel

@Composable
fun SplashRoute(
    onNext: () -> Unit,
    isUninstallFlow: Boolean = false,
    viewModel: SplashViewModel = viewModel()
) {
    SplashScreen(isReady = viewModel.isReady, onNext = onNext, isUninstallFlow = isUninstallFlow)
}
