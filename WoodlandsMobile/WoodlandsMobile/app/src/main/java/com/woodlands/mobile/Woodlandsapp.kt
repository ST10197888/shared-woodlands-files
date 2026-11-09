package com.woodlands.mobile

import android.app.Activity
import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle

class WoodlandsApp : Application() {
    private var current: MainActivity? = null

    private val busyScreens = setOf(
        "quote", "contact", "login", "register", "profile", "settings",
        "userForm", "productForm", "testimonialForm", "faqForm", "serviceForm",
        "codeSignIn", "approveWeb"
    )

    override fun onCreate() {
        super.onCreate()
        SyncManager.init(this)

        SyncManager.onChanged = { firstLoad ->
            val activity = current
            if (activity != null && !activity.isFinishing && (firstLoad || activity.currentScreen !in busyScreens)) {
                activity.showScreen(activity.currentScreen)
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                if (activity is MainActivity) {
                    current = activity
                    SyncManager.syncAll()
                }
            }

            override fun onActivityPaused(activity: Activity) {
                if (current === activity) current = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })

        val connectivity = getSystemService(ConnectivityManager::class.java)
        connectivity.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                SyncManager.syncAll()
            }
        })
    }
}