package com.mobile.trackerapp.nativeui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.viewinterop.AndroidView
import com.mobile.trackerapp.R
import com.mobile.trackerapp.databinding.NativeActionRowBinding
import com.mobile.trackerapp.databinding.NativeScreenShellBinding
import com.mobile.trackerapp.databinding.NativeTextCardBinding
import com.mobile.trackerapp.databinding.NativeToggleRowBinding



@Composable
fun LumoraXmlScreen(
    selectedTab: String? = null,
    onNavigate: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    content: (NativeScreenShellBinding) -> Unit,
) {
    val hasBottomNav = selectedTab != null
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ComposeColor(0xFF081020),
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (hasBottomNav && selectedTab != null) {
              //  BottomNav(selectedTab, onNavigate)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ComposeColor(0xFF081020))
                .statusBarsPadding()
                .then(
                    if (!hasBottomNav) Modifier.navigationBarsPadding() else Modifier
                )
                .padding(padding)
        ) {
            AndroidView(
                factory = { context ->
                    // Inflate once — store the typed binding in the tag so
                    // update() can retrieve it without re-binding every frame.
                    val binding = NativeScreenShellBinding.inflate(LayoutInflater.from(context))
                    binding.scroll.apply {
                        isFillViewport = true
                        isVerticalScrollBarEnabled = false
                        overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                    }
                    binding.root.tag = binding
                    binding.root
                },
                update = { root ->
                    // Retrieve the cached binding — no re-inflation on recompose.
                    val binding = root.tag as NativeScreenShellBinding
                    content(binding)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun NativeScreenShellBinding.setupTopBar(
    titleText: String,
    subtitleText: String? = null,
    onBack: (() -> Unit)? = null,
    @DrawableRes actionIconRes: Int? = null,
    onAction: (() -> Unit)? = null,
    creditsText: String? = null,
    onCredits: (() -> Unit)? = null,
) {
    title.text = titleText
    subtitle.text = subtitleText.orEmpty()
    subtitle.visibility = if (subtitleText.isNullOrBlank()) View.GONE else View.VISIBLE
    backButton.visibility = if (onBack == null) View.GONE else View.VISIBLE
    backButton.setOnClickListener { onBack?.invoke() }
    creditsChip.visibility = if (creditsText == null) View.GONE else View.VISIBLE
    creditsChip.text = creditsText.orEmpty()
    creditsChip.setOnClickListener { onCredits?.invoke() }
    actionButton.visibility = if (actionIconRes == null) View.GONE else View.VISIBLE
    actionIconRes?.let { actionIcon.setImageResource(it) }
    actionButton.setOnClickListener { onAction?.invoke() }
}

fun NativeScreenShellBinding.resetContent() {
    content.removeAllViews()
}

fun LinearLayout.addSectionTitle(text: String): TextView {
    return TextView(context).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        val top = if (childCount == 0) 0 else dp(22)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, top, 0, dp(10))
        }
        addView(this)
    }
}

fun LinearLayout.addTextCard(title: String, subtitle: String? = null, onClick: (() -> Unit)? = null): NativeTextCardBinding {
    val binding = NativeTextCardBinding.inflate(LayoutInflater.from(context), this, false)
    binding.title.text = title
    binding.subtitle.text = subtitle.orEmpty()
    binding.subtitle.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
    binding.root.isClickable = onClick != null
    binding.root.isFocusable = onClick != null
    binding.root.setOnClickListener { onClick?.invoke() }
    addWithTopMargin(binding.root, if (childCount == 0) 0 else 10)
    return binding
}

fun LinearLayout.addActionRow(
    title: String,
    subtitle: String? = null,
    @DrawableRes icon: Int = R.drawable.ic_lumora_settings,
    onClick: (() -> Unit)? = null,
): NativeActionRowBinding {
    val binding = NativeActionRowBinding.inflate(LayoutInflater.from(context), this, false)
    binding.title.text = title
    binding.subtitle.text = subtitle.orEmpty()
    binding.subtitle.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
    binding.icon.setImageResource(icon)
    binding.chevron.visibility = if (onClick == null) View.GONE else View.VISIBLE
    binding.root.isClickable = onClick != null
    binding.root.isFocusable = onClick != null
    binding.root.setOnClickListener { onClick?.invoke() }
    addWithTopMargin(binding.root, if (childCount == 0) 0 else 10)
    return binding
}

fun LinearLayout.addToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
): NativeToggleRowBinding {
    val binding = NativeToggleRowBinding.inflate(LayoutInflater.from(context), this, false)
    binding.title.text = title
    binding.subtitle.text = subtitle.orEmpty()
    binding.subtitle.visibility = if (subtitle.isNullOrBlank()) View.GONE else View.VISIBLE
    binding.toggle.setOnCheckedChangeListener(null)
    binding.toggle.isChecked = checked
    binding.toggle.setOnCheckedChangeListener { _, value -> onChanged(value) }
    addWithTopMargin(binding.root, if (childCount == 0) 0 else 10)
    return binding
}

fun LinearLayout.addPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit): TextView {
    return TextView(context).apply {
        this.text = text
        isEnabled = enabled
        isClickable = true
        isFocusable = true
        gravity = Gravity.CENTER
        setTextColor(Color.rgb(8, 16, 32))
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        setBackgroundResource(R.drawable.bg_common_action_lime)
        alpha = if (enabled) 1f else 0.45f
        setOnClickListener { if (isEnabled) onClick() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(52)
        ).apply {
            setMargins(0, if (this@addPrimaryButton.childCount == 0) 0 else dp(16), 0, 0)
        }
        addView(this)
    }
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun View.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

private fun LinearLayout.addWithTopMargin(view: View, marginTopDp: Int) {
    addView(
        view,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, dp(marginTopDp), 0, 0)
        }
    )
}
