package com.mobile.trackerapp.language

import android.view.LayoutInflater
import android.view.View
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.delay
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.mobile.trackerapp.ads.AdPlacement
import com.mobile.trackerapp.ads.PlacementNativeAd
import com.mobile.trackerapp.databinding.LanguageItemBinding
import com.mobile.trackerapp.databinding.LanguageScreenBinding

private const val DONE_UNLOCK_DELAY_MS = 3_000L

@Composable
fun LanguageScreen(
    uiState: LanguageUiState,
    onLanguageSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCode = (uiState as? LanguageUiState.Success)?.selectedLanguageCode.orEmpty()
    var unlockedCode by remember { mutableStateOf("") }
    var submitted by remember(selectedCode) { mutableStateOf(false) }
    LaunchedEffect(selectedCode) {
        unlockedCode = ""
        if (selectedCode.isNotBlank()) {
            delay(DONE_UNLOCK_DELAY_MS)
            unlockedCode = selectedCode
        }
    }
    val doneEnabled = selectedCode.isNotBlank() && unlockedCode == selectedCode && !submitted
    Column(modifier.fillMaxSize().systemBarsPadding()) {
        AndroidView(
            factory = { context -> LanguageScreenBinding.inflate(LayoutInflater.from(context)).root },
            update = { root ->
                val binding = LanguageScreenBinding.bind(root)
                binding.doneButton.isEnabled = doneEnabled
                binding.doneButton.setOnClickListener {
                    if (doneEnabled && !submitted) {
                        submitted = true
                        onDone()
                    }
                }
                when (uiState) {
                    LanguageUiState.Loading -> { binding.loading.visibility = View.VISIBLE; binding.languageScroll.visibility = View.GONE }
                    is LanguageUiState.Success -> bindLanguages(binding, uiState, onLanguageSelected)
                }
            }, modifier = Modifier.fillMaxWidth().weight(1f)
        )
        PlacementNativeAd(placement = AdPlacement.NATIVE_LANGUAGE)
    }
}

private fun bindLanguages(binding: LanguageScreenBinding, state: LanguageUiState.Success, onSelected: (String) -> Unit) {
    binding.loading.visibility = View.GONE
    binding.languageScroll.visibility = View.VISIBLE
    binding.languageList.removeAllViews()
    state.languages.forEach { language ->
        val row = LanguageItemBinding.inflate(LayoutInflater.from(binding.root.context), binding.languageList, false)
        val selected = language.code == state.selectedLanguageCode
        row.root.isSelected = selected
        row.radioInner.visibility = if (selected) View.VISIBLE else View.GONE
        row.flag.text = language.flagEmoji
        row.name.text = language.name
        row.root.contentDescription = "${language.name}${if (selected) ", selected" else ""}"
        row.root.setOnClickListener { onSelected(language.code) }
        binding.languageList.addView(row.root)
    }
}
