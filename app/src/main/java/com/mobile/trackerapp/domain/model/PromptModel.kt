package com.mobile.trackerapp.domain.model

data class PromptModel(
    val id: String,
    val title: String,
    val prompt: String,
    val category: String,
    val mediaType: String,
    val mediaUrl: String?
)
