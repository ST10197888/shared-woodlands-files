package com.woodlands.mobile

import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

private const val SIDEBAR_TAG = "woodlands_sidebar_panel"
private const val SCRIM_TAG = "woodlands_sidebar_scrim"

internal fun MainActivity.openSidebar() {
    if (sidebarOpen) return
    sidebarOpen = true
    setStatusIcons(dark = false)
    val panelWidth = (resources.displayMetrics.widthPixels * 0.82f).toInt().coerceAtMost(dp(300))

    val scrim = View(this).apply {
        setBackgroundColor(Color.BLACK); alpha = 0f; isClickable = true
        setOnClickListener { closeSidebar() }
        tag = SCRIM_TAG
    }
    val panel = buildSidebarPanel(panelWidth).apply { tag = SIDEBAR_TAG; translationX = -panelWidth.toFloat() }

    pageContainer.addView(scrim, FrameLayout.LayoutParams(-1, -1))
    pageContainer.addView(panel, FrameLayout.LayoutParams(panelWidth, -1))

    scrim.animate().alpha(0.45f).setDuration(220).start()
    panel.animate().translationX(0f).setDuration(220).start()
}

internal fun MainActivity.closeSidebar() {
    if (!sidebarOpen) return
    sidebarOpen = false
    setStatusIcons(dark = true)
    val panel = pageContainer.findViewWithTag<View>(SIDEBAR_TAG)
    val scrim = pageContainer.findViewWithTag<View>(SCRIM_TAG)
    val width = panel?.width ?: dp(280)
    panel?.animate()?.translationX(-width.toFloat())?.setDuration(190)?.withEndAction { pageContainer.removeView(panel) }?.start()
    scrim?.animate()?.alpha(0f)?.setDuration(190)?.withEndAction { pageContainer.removeView(scrim) }?.start()
}

/** Removes the drawer instantly (no animation) — used right before we rebuild the screen underneath it. */
internal fun MainActivity.closeSidebarImmediate() {
    sidebarOpen = false
    pageContainer.findViewWithTag<View>(SIDEBAR_TAG)?.let { pageContainer.removeView(it) }
    pageContainer.findViewWithTag<View>(SCRIM_TAG)?.let { pageContainer.removeView(it) }
}

private fun MainActivity.buildSidebarPanel(width: Int): LinearLayout {
    val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE); elevation = 16f }

    val headerBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), topInset + dp(15), dp(18), dp(16)); setBackgroundColor(blue) }
    val closeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
    closeRow.addView(TextView(this).apply { text = "✕"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = rippleBg(blue, Color.TRANSPARENT, 16, 60); isClickable = true; setOnClickListener { closeSidebar() } }, LinearLayout.LayoutParams(dp(32), dp(32)))
    headerBox.addView(closeRow)

    val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(6), 0, 0) }
    brandRow.addView(ImageView(this).apply { setImageResource(R.drawable.app_logo_transparent); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(64), dp(64)))
    val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
    brandText.addView(tv("WOODLANDS", 18, Color.WHITE).apply { setTypeface(typeface, Typeface.BOLD) })
    brandText.addView(tv("DESIGNER BOARDS", 12, Color.rgb(205, 218, 203)))
    val me = currentUser()
    if (me != null) {
        brandText.addView(tv(me.fullName, 13, Color.WHITE).apply { setPadding(0, dp(10), 0, 0); setTypeface(typeface, Typeface.BOLD) })
        brandText.addView(tv(Roles.label(me.role), 11, Color.rgb(205, 218, 203)))
    }
    brandRow.addView(brandText, LinearLayout.LayoutParams(0, -2, 1f))
    headerBox.addView(brandRow)
    panel.addView(headerBox)

    val scroll = ScrollView(this)
    val links = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(10), dp(8), bottomInset + dp(15)) }

    fun navItem(icon: String, label: String, screen: String) {
        val active = currentScreen == screen
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); isClickable = true }
        row.background = rippleBg(if (active) lightBlueBg else Color.WHITE, Color.TRANSPARENT, 10, 40)
        row.setOnClickListener { showScreen(screen) }
        row.addView(tv(icon, 16, if (active) blue else muted), LinearLayout.LayoutParams(dp(28), -2))
        row.addView(tv(label, 14, if (active) blue else text).apply { if (active) setTypeface(typeface, Typeface.BOLD) })
        links.addView(row, marginParams(6, 2, 6, 2))
    }

    navItem("⌂", "Home", "home")
    navItem("▦", "Products & Gallery", "gallery")
    navItem("ℹ", "About Us", "about")
    navItem("🛠", "Services", "services")
    navItem("★", "Testimonials", "testimonials")
    navItem("❔", "FAQs", "faqs")
    if (me == null || !Roles.isStaff(me.role)) navItem("✉", "Contact", "contact")
    navItem("⌖", "Branches", "branches")
    if (me == null || !Roles.isStaff(me.role)) navItem("✎", "Request a Quote", "quote")

    links.addView(View(this).apply { setBackgroundColor(Color.rgb(230, 230, 230)) }, LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(dp(14), dp(10), dp(14), dp(10)) })

    if (me != null) {
        if (Roles.isStaff(me.role)) navItem("📊", "Dashboard", "dashboard")
        if (Roles.isStaff(me.role)) navItem("🗑", "Product Deletions", "productDeletionRequests")
        navItem("🧾", if (Roles.isStaff(me.role)) "Quotes" else "My Quotes", "quotes")
        navItem("🔔", "Notifications", "notifications")
        navItem("🖥", "Sign in on web", "approveWeb")
        navItem("👤", "Profile", "profile")
        navItem("⚙", "Settings", "settings")
        val logout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(13), dp(14), dp(13)); isClickable = true; background = rippleBg(Color.WHITE, Color.TRANSPARENT, 10, 40) }
        logout.setOnClickListener { SyncManager.invalidateAuthAttempts(); session.clear(); toast("Signed out"); showScreen("home") }
        logout.addView(tv("⎋", 16, red), LinearLayout.LayoutParams(dp(28), -2))
        logout.addView(tv("Logout", 14, red))
        links.addView(logout, marginParams(6, 2, 6, 2))
    } else {
        navItem("🔑", "Login", "login")
        navItem("📝", "Register", "register")
    }

    scroll.addView(links)
    panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    return panel
}
    