package com.mobile.trackerapp.language

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.mobile.trackerapp.core.localization.LocaleManager
import com.mobile.trackerapp.language.model.LanguageModel

class LanguageViewModel(application: Application) : AndroidViewModel(application) {
    var uiState: LanguageUiState by mutableStateOf(LanguageUiState.Loading)
        private set

    init {
        loadLanguages()
    }

    private fun loadLanguages() {
        val languages = listOf(
            LanguageModel("en", "English", "🇺🇸"),
            LanguageModel("vi", "Tiếng Việt", "🇻🇳"),
            LanguageModel("es", "Español", "🇪🇸"),
            LanguageModel("fr", "Français", "🇫🇷"),
            LanguageModel("de", "Deutsch", "🇩🇪"),
            LanguageModel("it", "Italiano", "🇮🇹"),
            LanguageModel("pt", "Português", "🇧🇷"),
            LanguageModel("tr", "Türkçe", "🇹🇷"),
            LanguageModel("ar", "العربية", "🇸🇦"),
            LanguageModel("hi", "हिन्दी", "🇮🇳"),
            LanguageModel("ko", "한국어", "🇰🇷"),
            LanguageModel("zh", "中文", "🇨🇳")
        )
        // No language is preselected: the user must actively choose one. Done
        // stays disabled until a selection is made (and the required delay has
        // elapsed — enforced in the UI).
        uiState = LanguageUiState.Success(
            languages = languages,
            selectedLanguageCode = "",
            searchQuery = ""
        )
    }

    fun selectLanguage(code: String) {
        val currentState = uiState
        if (currentState is LanguageUiState.Success) {
            uiState = currentState.copy(selectedLanguageCode = code)
        }

    }

    fun persistSelection() {
        val state = uiState as? LanguageUiState.Success ?: return
        // Route through the single coordinator so SharedPreferences, DataStore,
        // and the per-app locale all agree by construction (Bug C).
        LocaleManager.setLocale(getApplication(), state.selectedLanguageCode)
    }

    fun updateSearchQuery(query: String) {
        val currentState = uiState
        if (currentState is LanguageUiState.Success) {
            uiState = currentState.copy(searchQuery = query)
        }
    }
}
