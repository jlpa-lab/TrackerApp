package com.mobile.trackerapp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.deep.lumoraai.core.network.hasInternetConnection
import com.mobile.trackerapp.ads.AdsConfigStore
import com.mobile.trackerapp.ads.AdsManager
import com.mobile.trackerapp.ads.AdsProvider
import com.mobile.trackerapp.core.localization.LocaleManager
import com.mobile.trackerapp.navigation.NavGraph
import com.mobile.trackerapp.navigation.Screen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private var notificationRoute by mutableStateOf<String?>(null)

    @Inject
    lateinit var adsManager: AdsManager

    @Inject
    lateinit var adsConfigStore: AdsConfigStore

    companion object {
        const val NOTIFICATION_ROUTE_EXTRA = "lumora_destination_route"
        const val ACTION_OPEN_HOME = "com.deep.lumoraai.action.OPEN_HOME"
        const val ACTION_OPEN_TEMPLATES = "com.deep.lumoraai.action.OPEN_TEMPLATES"
        const val ACTION_OPEN_UNINSTALL = "com.deep.lumoraai.action.OPEN_UNINSTALL"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        //setTheme(R.style.Theme_LumoraAI)
        super.onCreate(savedInstanceState)
        window.setBackgroundDrawable(ColorDrawable(Color.rgb(8, 16, 32)))
        window.statusBarColor = Color.rgb(8, 16, 32)
        window.navigationBarColor = Color.rgb(8, 16, 32)
        notificationRoute = destinationFromIntent(intent)

        setContent {
                AdsProvider(adsManager = adsManager, adsConfigStore = adsConfigStore) {
                    val context = LocalContext.current
                    // One long-lived banner_all AdView, created once and re-parented
                    // across primary tabs (never recreated/refreshed on tab switch).
                    val screenWidthDp = LocalConfiguration.current.screenWidthDp
                    val adsConfigVersion = adsConfigStore.version
                    val persistentBanner = remember(screenWidthDp, adsConfigVersion) {
                        com.mobile.trackerapp.ads.banner.PersistentBannerAd(
                            // AdView requires an Activity context to render; the app
                            // context makes the banner silently fail to display.
                            activityContext = this@MainActivity,
                            configStore = adsConfigStore,
                            widthDp = screenWidthDp,
                        )
                    }
                    androidx.compose.runtime.CompositionLocalProvider(
                        com.mobile.trackerapp.ads.banner.LocalPersistentBanner provides persistentBanner
                    ) {
                        var hasInternet by remember { mutableStateOf(true) }

                        LaunchedEffect(context) {
                            delay(500) // Brief initial check delay
                            hasInternet = context.hasInternetConnection()
                        }

                        DisposableEffect(context) {
                            val observer = LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    hasInternet = context.hasInternetConnection()
                                }
                            }
                            this@MainActivity.lifecycle.addObserver(observer)
                            onDispose { this@MainActivity.lifecycle.removeObserver(observer) }
                        }

                        DisposableEffect(context) {
                            val connectivityManager =
                                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                            val mainHandler = Handler(Looper.getMainLooper())
                            val callback = object : ConnectivityManager.NetworkCallback() {
                                override fun onAvailable(network: Network) {
                                    mainHandler.post {
                                        hasInternet = context.hasInternetConnection()
                                    }
                                }

                                override fun onLost(network: Network) {
                                    mainHandler.postDelayed({
                                        hasInternet = context.hasInternetConnection()
                                    }, 1000) // Give some time before checking
                                }

                                override fun onCapabilitiesChanged(
                                    network: Network,
                                    networkCapabilities: android.net.NetworkCapabilities
                                ) {
                                    mainHandler.post {
                                        hasInternet = context.hasInternetConnection()
                                    }
                                }
                            }

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                connectivityManager.registerDefaultNetworkCallback(callback)
                            }

                            onDispose {
                                runCatching { connectivityManager.unregisterNetworkCallback(callback) }
                            }
                        }

                        Box(Modifier.fillMaxSize()) {
                            NavGraph(
                                notificationRoute = notificationRoute,
                                onNotificationRouteConsumed = { notificationRoute = null }
                            )
                            if (!hasInternet) {
//                                NoInternetScreen(
//                                    onTurnOnNetwork = {
//                                        startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
//                                    },
//                                    onRetry = {
//                                        hasInternet = context.hasInternetConnection()
//                                    },
//                                    modifier = Modifier.zIndex(1f)
//                                )
                            }
                        }
                    }
                }
            }
    }

    override fun attachBaseContext(newBase: Context) {
        // Read the locale through the single coordinator precedence: the applied
        // per-app locale wins, falling back to the persisted SharedPreferences
        // value. This guarantees the first frame agrees with what the user last
        // selected across all persistence paths.
        val persisted = newBase.getSharedPreferences("lumora_settings", Context.MODE_PRIVATE)
            .getString("locale_code", "en") ?: "en"
        val code = LocaleManager.currentAppLocale(persisted)
        super.attachBaseContext(LocaleManager.apply(newBase, code))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationRoute = destinationFromIntent(intent)
    }

    private fun destinationFromIntent(intent: Intent?): String? {
        return when (intent?.action) {
//            Companion.ACTION_OPEN_HOME -> Screen.Home.route
//            Companion.ACTION_OPEN_TEMPLATES -> Screen.Templates.route
//            Companion.ACTION_OPEN_TEMPLATESN_UNINSTALL -> Screen.UninstallConfirm.route
            else -> intent?.getStringExtra(Companion.NOTIFICATION_ROUTE_EXTRA)
        }
    }
}