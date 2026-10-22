package com.mobile.trackerapp.ui.permission

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.mobile.trackerapp.R
import com.mobile.trackerapp.app.ResumeAdsEntryRule

/**
 * Shared foreground-location permission flow used by all location features.
 *
 * The explanatory dialogs are deliberately modal: the user must either start
 * the Android permission request or acknowledge that the feature cannot run.
 */
class LocationPermissionFlow(
    private val activity: ComponentActivity,
    private val onGranted: () -> Unit,
    private val onDenied: () -> Unit = {}
) {
    private var accessDialog: Dialog? = null
    private var notGrantedDialog: Dialog? = null
    private var settingsOpened = false

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Android reports the result for the foreground permission here. The
        // context check also accepts an already-granted coarse permission.
        if (granted || hasForegroundLocation()) {
            onGranted()
        } else {
            showNotGrantedDialog()
        }
    }

    fun request() {
        if (hasForegroundLocation()) {
            onGranted()
            return
        }
        if (accessDialog?.isShowing == true || notGrantedDialog?.isShowing == true) return
        showAccessDialog()
    }

    /** Call from the host Activity's onResume(). */
    fun onHostResume() {
        if (!settingsOpened) return
        settingsOpened = false
        // Usually the process observer consumes this suppression while the
        // app is stopped. Consume it here as a fallback for devices that keep
        // the app process started while Settings is displayed.
        ResumeAdsEntryRule.consumeResumeSuppression()
        if (hasForegroundLocation()) onGranted() else activity.finish()
    }

    fun dismiss() {
        accessDialog?.dismiss()
        notGrantedDialog?.dismiss()
        accessDialog = null
        notGrantedDialog = null
    }

    private fun hasForegroundLocation(): Boolean =
        ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun showAccessDialog() {
        val dialog = createDialog()
        accessDialog = dialog
        val icon = dialog.findViewById<ImageView>(R.id.permission_icon)
        val title = dialog.findViewById<TextView>(R.id.permission_title)
        val message = dialog.findViewById<TextView>(R.id.permission_message)
        val primary = dialog.findViewById<TextView>(R.id.permission_primary)
        val secondary = dialog.findViewById<TextView>(R.id.permission_secondary)

        icon.setImageResource(R.drawable.ic_permission_location)
        title.setText(R.string.location_permission_title)
        message.setText(R.string.location_permission_message)
        primary.setText(R.string.location_permission_allow_access)
        secondary.visibility = View.VISIBLE
        secondary.setText(R.string.common_not_now)
        primary.setOnClickListener {
            dialog.dismiss()
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        secondary.setOnClickListener {
            dialog.dismiss()
            showNotGrantedDialog()
        }
        dialog.show()
    }

    private fun showNotGrantedDialog() {
        onDenied()
        if (notGrantedDialog?.isShowing == true) return

        val dialog = createDialog()
        notGrantedDialog = dialog
        dialog.findViewById<ImageView>(R.id.permission_icon)
            .setImageResource(R.drawable.ic_permission_location_denied)
        dialog.findViewById<TextView>(R.id.permission_title)
            .setText(R.string.location_permission_not_granted_title)
        dialog.findViewById<TextView>(R.id.permission_message)
            .setText(R.string.location_permission_not_granted_message)
        dialog.findViewById<TextView>(R.id.permission_primary).apply {
            setText(R.string.location_permission_open_settings)
            setOnClickListener {
                dialog.dismiss()
                settingsOpened = true
                ResumeAdsEntryRule.suppressNextResume()
                activity.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${activity.packageName}")
                    )
                )
            }
        }
        dialog.findViewById<TextView>(R.id.permission_secondary).visibility = View.GONE
        dialog.show()
    }

    private fun createDialog(): Dialog {
        val dialog = Dialog(activity)
        dialog.setContentView(R.layout.dialog_location_permission)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnKeyListener { _, keyCode, event ->
            keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP
        }
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes = attributes.apply { dimAmount = 0.52f }
            setLayout(
                (activity.resources.displayMetrics.widthPixels * 0.88f).toInt(),
                WindowManager.LayoutParams.WRAP_CONTENT
            )
        }
        return dialog
    }
}
