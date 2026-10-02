package com.deep.lumoraai.feature.splash

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

class SplashViewModel(
//    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {
    var isReady: Boolean by mutableStateOf(false)
        private set

    init {
        isReady = true
        viewModelScope.launch {
//            if (FirebaseAuth.getInstance().currentUser != null) {
//                authRepository.syncCurrentUser()
//            }
        }
    }
}
