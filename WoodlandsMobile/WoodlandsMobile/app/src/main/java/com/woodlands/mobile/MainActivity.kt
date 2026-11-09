package com.woodlands.mobile

import com.woodlands.mobile.TutorialDemo.TutorialDemoController
import com.woodlands.mobile.TutorialDemo.TutorialDemoData
import com.woodlands.mobile.TutorialDemo.tutorialDemoScreen
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.view.WindowInsets
import android.view.WindowInsetsController



class MainActivity : Activity() {
    internal lateinit var db: LocalDb
    internal lateinit var session: Session
    internal lateinit var pageContainer: FrameLayout
    internal lateinit var root: LinearLayout
    internal lateinit var content: LinearLayout
    internal lateinit var bottom: LinearLayout
    internal var currentScreen = "home"
    internal var galleryFilter = "All"
    internal var sidebarOpen = false

    internal var selectedProduct: Int? = null

    // Tutorial Demo Mode state
    internal var tutorialDemoActive = false
    internal var tutorialStepIndex = 0
    internal var tutorialDemoPage = "gallery"
    internal var topInset = 0
    internal var bottomInset = 0
    // form-in-progress state used by the admin/manager management screens
    internal var editUserId: String? = null
    internal var editBranchId: Int? = null
    internal var editProductId: Int = 0
    internal var editTestimonialId: Int? = null
    internal var editFaqId: Int? = null
    internal var editServiceId: Int? = null

    internal val blue = Color.rgb(22, 50, 28)
    internal val red = Color.rgb(128, 80, 37)
    internal val navy = Color.rgb(29, 69, 37)
    internal val cream = Color.rgb(251, 250, 247)
    internal val text = Color.rgb(35, 33, 30)
    internal val muted = Color.rgb(95, 98, 93)
    internal val green = Color.rgb(46, 107, 51)
    internal val greenBg = Color.rgb(238, 244, 236)
    internal val lightBlueBg = Color.rgb(238, 244, 236)
    internal val tan = Color.rgb(201, 160, 99)
    internal val lineColor = Color.rgb(231, 227, 219)

    internal var pullStartedAt = 0L

    internal var loaderView: ImageView? = null
    internal var loaderSpin: android.animation.ObjectAnimator? = null

    internal fun showLoader() {
        if (loaderView != null) return
        val v = ImageView(this).apply {
            setImageResource(R.drawable.app_logo_transparent)
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = bg(Color.WHITE, lineColor, 26)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(6).toFloat()
        }
        pageContainer.addView(v, FrameLayout.LayoutParams(dp(52), dp(52), Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = topInset + dp(78) })
        loaderSpin = android.animation.ObjectAnimator.ofFloat(v, View.ROTATION, 0f, 360f).apply {
            duration = 900
            repeatCount = android.animation.ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
            start()
        }
        loaderView = v
    }

    internal fun hideLoader() {
        val v = loaderView ?: return
        loaderSpin?.cancel()
        loaderSpin = null
        pageContainer.removeView(v)
        loaderView = null
    }

    internal fun stopPull() {
        val wait = 900L - (System.currentTimeMillis() - pullStartedAt)
        if (wait > 0) pageContainer.postDelayed({ hideLoader() }, wait) else hideLoader()
    }

    /** Screens that fall "under" the More tab so the bottom nav highlights the right icon. */
    private val moreFamily = setOf(
        "more", "tutorial", "help", "contactManager",
        "about", "testimonials", "faqs", "contact",
        "login", "register", "profile", "settings",
        "dashboard", "quotes", "users", "userForm",
        "manageProducts", "productForm", "manageTestimonials",
        "testimonialForm", "manageFaqs", "faqForm",
        "syncIssues", "notifications", "emailChange", "services",
        "manageServices", "serviceForm", "productDeletionRequests",
        "deletionRequests", "status", "codeSignIn", "approveWeb"
    )

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = LocalDb.shared(this)
        session = Session(this)

        // Handle expired or rejected API sessions.
// Only clear the session if the rejected token is still the active token.
        ApiClient.onUnauthorized = { rejectedToken ->
            runOnUiThread {
                if (
                    !isFinishing &&
                    !isDestroyed &&
                    session.isLoggedIn &&
                    session.token == rejectedToken
                ) {
                    session.clear()
                    toast("Your session has expired. Please sign in again.")
                    showScreen("login")
                }
            }
        }

        // Restore the saved JWT before any authenticated API requests.
        session.restoreToken()
        window.statusBarColor = blue
        pageContainer = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        pageContainer.setOnApplyWindowInsetsListener { _, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            topInset = bars.top
            bottomInset = bars.bottom
            if (::root.isInitialized) applyEdgeGaps()
            insets
        }
        setContentView(pageContainer)
        setStatusIcons(dark = true)
        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )
        // First installation or start Welcom if tutorial not yet completed
        if (!tutorialCompleted()) {
            showScreen("welcome")
        } else {
            showScreen("home")
        }

        handleHandoffIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleHandoffIntent(intent)
    }

    /** 15dp boundary between app content and system status and navigation bars. */
    internal fun applyEdgeGaps() {
        root.setPadding(0, topInset + dp(15), 0, bottomInset + dp(5))
    }

    internal fun setStatusIcons(dark: Boolean) {
        window.insetsController?.setSystemBarsAppearance(
            if (dark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
        )
    }

    override fun onDestroy() {
        ApiClient.onUnauthorized = null
        super.onDestroy()
    }

    internal var imagePickCallback: ((Uri) -> Unit)? = null

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 7001 && resultCode == RESULT_OK) data?.data?.let { imagePickCallback?.invoke(it) }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (sidebarOpen) {
            closeSidebar()
            return
        }

        if (currentScreen == "welcome") {
            if (tutorialCompleted()) {
                showScreen("more")
            } else {
                toast("Please continue to the tutorial first.")
            }
            return
        }

        if (currentScreen == "tutorialDemo") {
            returnToTutorial()
            return
        }
        if (currentScreen == "tutorial") {
            if (tutorialCompleted()) {
                showScreen("help")
            } else {
                toast("Please complete the tutorial before leaving.")
            }
            return
        }

        if (currentScreen == "help" || currentScreen == "contactManager") {
            showScreen("more")
            return
        }

        if (selectedProduct != null) {
            selectedProduct = null
            showScreen("gallery")
            return
        }

        if (currentScreen == "branchForm") {
            showScreen("branches")
            return
        }

        if (currentScreen in setOf(
                "login", "register", "profile", "settings",
                "dashboard", "quotes", "users", "userForm",
                "manageProducts", "productForm", "manageTestimonials",
                "testimonialForm", "manageFaqs", "faqForm","syncIssues"
            )) {
            showScreen("more")
            return
        }

        if (currentScreen in LEGAL_SCREENS) {
            showScreen("legal")
            return
        }

        if (currentScreen == "legal") {
            showScreen("more")
            return
        }

        if (currentScreen !in listOf("home", "gallery", "quote", "branches", "more")) {
            showScreen("home")
            return
        }

        if (currentScreen != "home") {
            showScreen("home")
        } else {
            super.onBackPressed()
        }
    }

    internal fun currentUser(): AppUser? = session.userId?.let { db.findUserById(it) }

    // Tutorial completion tracking
    private val tutorialPrefs by lazy {
        getSharedPreferences("woodlands_tutorial", MODE_PRIVATE)
    }

    internal fun tutorialCompleted(): Boolean {
        return tutorialPrefs.getBoolean("completed", false)
    }

    internal fun tutorialCompletedAt(): Long {
        return tutorialPrefs.getLong("completed_at", 0L)
    }

    internal fun markTutorialCompleted() {
        val completedAt = System.currentTimeMillis()

        tutorialPrefs.edit()
            .putBoolean("completed", true)
            .putLong("completed_at", completedAt)
            .apply()
    }

    internal fun formatTutorialDate(timestamp: Long): String {
        if (timestamp <= 0L) return "Not completed"

        return java.text.SimpleDateFormat(
            "dd MMMM yyyy 'at' HH:mm",
            java.util.Locale.getDefault()
        ).format(java.util.Date(timestamp))
    }

    // Start a fresh demo session
    internal fun startTutorialDemo() {
        TutorialDemoData.clear()
        tutorialDemoActive = true
        tutorialStepIndex = 0
        tutorialDemoPage = "gallery"
        showScreen("tutorial")
    }

    // Open a temporary practice screen
    internal fun openTutorialDemo(page: String) {
        if (!tutorialDemoActive) return
        tutorialDemoPage = page
        showScreen("tutorialDemo")
    }

    // Return to the same tutorial step without resetting progress
    internal fun returnToTutorial() {
        showScreen("tutorial")
    }

    // Discard temporary tutorial information
    internal fun clearTutorialDemo() {
        TutorialDemoData.clear()
        tutorialDemoActive = false
        tutorialStepIndex = 0
        tutorialDemoPage = "gallery"
    }

    internal fun showScreen(screen: String) {

        // Start a fresh demo session whenever Tutorial is opened from elsewhere
        if (screen == "tutorial" && !tutorialDemoActive) {
            TutorialDemoData.clear()
            tutorialDemoActive = true
            tutorialStepIndex = 0
            tutorialDemoPage = "gallery"
        }

        if (sidebarOpen) closeSidebarImmediate()
        currentScreen = screen
        if (screen in listOf(
                "home", "gallery", "branches", "more",
                "tutorial", "help", "contactManager",
                "about", "testimonials", "faqs", "contact"
            )) selectedProduct = null
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        applyEdgeGaps()
        pageContainer.removeAllViews()
        pageContainer.addView(root, FrameLayout.LayoutParams(-1, -1))
        loaderView?.let { pageContainer.addView(it, it.layoutParams) }
        if (screen !in listOf("welcome")) {
            root.addView(header())
            connectionBanner()?.let { root.addView(it) }
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.WHITE) }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(content) }
        val refresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            addView(scroll)
            setColorSchemeColors(Color.parseColor("#2E6B33"))

            if (screen != "tutorialDemo") {
                setOnRefreshListener {
                    isRefreshing = false
                    pullStartedAt = System.currentTimeMillis()
                    showLoader()
                    SyncManager.onFinished = { stopPull() }
                    SyncManager.syncAll()

                    val started = pullStartedAt
                    pageContainer.postDelayed({
                        if (pullStartedAt == started) stopPull()
                    }, 20000)
                }
            }
        }
        root.addView(refresh, LinearLayout.LayoutParams(-1, 0, 1f))
        bottom = bottomNav()

        if (screen !in listOf("welcome", "tutorialDemo")) {
            root.addView(bottom)
        }
        when (screen) {
            "tutorial" -> tutorialScreen()
            "home" -> homeScreen()
            "gallery" -> galleryScreen()
            "quote" -> quoteScreen()
            "branches" -> branchListScreen()
            "branchForm" -> branchFormScreen()
            "more" -> moreScreen()
            "welcome" -> welcomeScreen()
            "tutorialDemo" -> tutorialDemoScreen()
            "help" -> helpScreen()
            "contactManager" -> contactManagerScreen()
            "about" -> aboutScreen()
            "testimonials" -> testimonialsScreen()
            "faqs" -> faqScreen()
            "legal" -> legalHubScreen()
            "privacy", "terms", "refunds", "cookies" -> legalDocScreen(screen)
            "contact" -> contactScreen()
            "product" -> if (selectedProduct != null) productScreen(selectedProduct!!) else showScreen("home")
            "login" -> loginScreen()
            "register" -> registerScreen()
            "profile" -> profileScreen()
            "settings" -> settingsScreen()
            "status" -> statusScreen()
            "syncIssues" -> syncIssuesScreen()
            "dashboard" -> dashboardScreen()
            "quotes" -> quotesScreen()
            "users" -> usersScreen()
            "deletionRequests" -> deletionRequestsScreen()
            "userForm" -> userFormScreen()
            "manageProducts" -> manageProductsScreen()
            "productForm" -> productFormScreen()
            "manageTestimonials" -> manageTestimonialsScreen()
            "testimonialForm" -> testimonialFormScreen()
            "manageFaqs" -> manageFaqsScreen()
            "faqForm" -> faqFormScreen()
            "notifications" -> notificationsScreen()
            "emailChange" -> emailChangeScreen()
            "codeSignIn" -> codeSignInScreen()
            "approveWeb" -> approveWebScreen()
            "services" -> servicesScreen()
            "manageServices" -> manageServicesScreen()
            "serviceForm" -> serviceFormScreen()
            "productDeletionRequests" -> productDeletionRequestsScreen()
        }
        bottom.visibility = if (screen in listOf("home", "gallery", "quote", "branches", "more")) View.VISIBLE else View.GONE
    }

    //  chrome: header / sidebar trigger / bottom nav

    private fun header(): View {
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(6), dp(8), dp(12), dp(8)); setBackgroundColor(Color.WHITE); elevation = 3f }
        if (currentScreen !in listOf("welcome", "tutorial", "tutorialDemo")) {
            val menu = TextView(this).apply {
                text = "☰"
                textSize = 22f
                setTextColor(blue)
                gravity = Gravity.CENTER
                background = rippleBg(Color.WHITE, lineColor, 12, 45)
                isClickable = true
                setOnClickListener { openSidebar() }
            }

            bar.addView(menu, LinearLayout.LayoutParams(dp(42), dp(42)).apply {
                setMargins(dp(6), 0, 0, 0)
            })
        }
        val logo = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; isClickable = true; setOnClickListener { showScreen("home") } }
        logo.addView(ImageView(this).apply { setImageResource(R.drawable.app_logo_transparent); scaleType = ImageView.ScaleType.FIT_CENTER }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(0, 0, dp(8), 0) })
        val logoText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        logoText.addView(tv("Woodlands", 19, blue).apply { setTypeface(Typeface.SERIF, Typeface.BOLD); gravity = Gravity.CENTER })
        logoText.addView(tv("DESIGNER BOARDS", 9, red).apply { setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.2f; gravity = Gravity.CENTER })
        logo.addView(logoText)
        bar.addView(logo, LinearLayout.LayoutParams(0, dp(46), 1f))
        val me = currentUser()
        if (me != null) {
            val unread = db.unreadNotificationCount()

            val bell = TextView(this).apply {
                text = if (unread > 0) "🔔 $unread" else "🔔"
                textSize = 13f
                setTextColor(blue)
                gravity = Gravity.CENTER
                background = rippleBg(Color.WHITE, lineColor, 18, 45)
                isClickable = true
                setPadding(dp(8), 0, dp(8), 0)
                setOnClickListener { showScreen("notifications") }
            }

            bar.addView(bell, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(36)).apply { setMargins(0, 0, dp(8), 0) })
        }

        if (me != null) {
            val avatar = TextView(this).apply { text = initialsOf(me.fullName); textSize = 12f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); background = rippleBg(blue, Color.TRANSPARENT, 18, 60); isClickable = true; setOnClickListener { showScreen("profile") } }
            bar.addView(avatar, LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(0, 0, dp(8), 0) })
        }
        if (currentScreen !in listOf("welcome", "tutorial", "tutorialDemo") &&
            (me == null || !Roles.isStaff(me.role))) {
            val quote = button("Quote", red, Color.WHITE).apply { setOnClickListener { showScreen("quote") } }
            bar.addView(quote, LinearLayout.LayoutParams(dp(82), dp(42)))
        }
        return bar
    }

    private fun bottomNav(): LinearLayout {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Color.WHITE); elevation = 8f }
        val items = listOf("⌂" to "Home", "▦" to "Gallery", (if (staffUser()) "▤" to "Quotes" else "✎" to "Quote"), "⌖" to "Branches", "☰" to "More")
        items.forEach { (icon, label) ->
            val isActive = active(label)
            val cell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(6)) }
            val pill = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(14), dp(4), dp(14), dp(3)) }
            if (isActive) pill.background = bg(lightBlueBg, Color.TRANSPARENT, 14)
            pill.addView(tv(icon, 19, if (isActive) blue else muted).apply { gravity = Gravity.CENTER })
            pill.addView(tv(label, 10, if (isActive) blue else muted).apply { gravity = Gravity.CENTER; setPadding(0, dp(2), 0, 0); if (isActive) setTypeface(typeface, Typeface.BOLD) })
            cell.addView(pill)
            cell.background = rippleBg(Color.WHITE, Color.TRANSPARENT, 0, 35)
            cell.isClickable = true
            cell.setOnClickListener { when (label) { "Home" -> showScreen("home"); "Gallery" -> showScreen("gallery"); "Quote" -> showScreen("quote"); "Quotes" -> showScreen("quotes"); "Branches" -> showScreen("branches"); else -> showScreen("more") } }
            nav.addView(cell, LinearLayout.LayoutParams(0, dp(60), 1f))
        }
        return nav
    }
    private fun staffUser() = currentUser()?.let { Roles.isStaff(it.role) } == true
    private fun active(label: String) = when (label) { "Home" -> currentScreen == "home"; "Gallery" -> currentScreen == "gallery" || currentScreen == "product"; "Quote" -> currentScreen == "quote"; "Quotes" -> currentScreen == "quotes"; "Branches" -> currentScreen == "branches" || currentScreen == "branchForm"; else -> currentScreen in moreFamily && !(staffUser() && currentScreen == "quotes") }
    //  home

    private fun homeScreen() {
        heroCarousel()
        categoryGrid()
        featuredStrip()
        bisonBanner()
        testimonialStrip()
        cta("Need something custom?", "All our products can be tailored to your space.", "Request a Quote") { showScreen("quote") }
        homeFooter()
    }
    //  gallery / product / quote / branches

    private fun galleryScreen() {
        pageIntro("Services & Gallery", "Browse the catalogue and filter products by category.")
        val filters = horizontalChips(listOf("All", "Kitchen Units", "TV Stands", "Built-In Cupboards", "Cutting & Edging"), galleryFilter) { galleryFilter = it; showScreen("gallery") }
        content.addView(filters)
        val list = if (galleryFilter == "All") db.loadProducts() else db.loadProducts().filter { it.category == galleryFilter }
        content.addView(tv("Showing ${list.size} product${if (list.size == 1) "" else "s"}", 12, muted).apply { setPadding(dp(16), dp(8), dp(16), dp(8)) })
        productList(list)
        cta("Don't see exactly what you need?", "All our products are fully custom.", "Request a Custom Quote") { showScreen("quote") }
    }

    private fun productScreen(id: Int) {
        val p = db.loadProducts().firstOrNull { it.id == id } ?: run { showScreen("gallery"); return }
        pageIntro(p.title, p.category)
        val img = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setProductImage(this, p.image) }
        content.addView(img, sizeMarginParams(-1, dp(220), 12, 0, 12, 0))
        content.addView(tv(p.tagline, 16, red).apply { setTypeface(typeface, Typeface.BOLD); setPadding(dp(16), dp(14), dp(16), dp(4)) })
        content.addView(tv(p.description, 13, muted).apply { setPadding(dp(16), 0, dp(16), dp(12)) })
        infoRow("Price", p.price); infoRow("Lead time", p.lead)
        sectionTitle("Features", "")
        p.features.forEach { bullet(it) }
        sectionTitle("Finish options", "")
        val finish = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), 0, dp(16), dp(8)) }
        p.finishes.forEach { finish.addView(chip(it), marginParams(0, 0, 6, 0)) }
        val finishScroll = HorizontalScrollView(this).apply { addView(finish) }; content.addView(finishScroll)
        cta("Interested in this product?", "Start a quote with the product pre-selected.", "Request Quote for ${p.title}") { selectedProduct = p.id; showScreen("quote") }
        sectionTitle("Related products", "")
        productList(db.loadProducts().filter { it.category == p.category && it.id != p.id }.take(3))
    }

    private fun quoteScreen() {
        pageIntro("Request a Free Quote", "Tell us about your project and we'll take it from there.")
        val me = currentUser()
        val selected = selectedProduct?.let { id -> db.loadProducts().firstOrNull { it.id == id } }
        if (selected != null) { val cardV = card().apply { setPadding(dp(14), dp(12), dp(14), dp(12)) }; cardV.addView(tv("Product selected", 11, red)); cardV.addView(tv(selected.title, 16, blue).apply { setTypeface(typeface, Typeface.BOLD) }); content.addView(cardV, marginParams(16, 0, 16, 12)) }
        val nameParts = me?.fullName?.trim()?.split(" ", limit = 2)
        val first = field("First Name", "e.g. Thabo").apply { if (nameParts != null) setText(nameParts.getOrNull(0).orEmpty()) }
        val last = field("Last Name", "e.g. Mokoena").apply { if (nameParts != null) setText(nameParts.getOrNull(1).orEmpty()) }
        val email = field("Email Address", "you@example.com").apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS; if (me != null) setText(me.email) }
        val phone = field("Phone Number", "071 234 5678", required = false).apply { if (me?.phone != null) setText(me.phone) }
        val branch = spinnerField("Nearest Branch", db.loadBranches().map { it.name })
        val service = spinnerField("Service Required", listOf("Kitchen Units", "TV Stands", "Built-In Cupboards", "Cutting & Edging", "General Enquiry"))
        val msg = field("Project Description", "Describe your project, measurements, finish preferences, etc.", true)
        val privacy = CheckBox(this).apply { text = "I have read the privacy notice. This prototype saves quote details on this device."; setTextColor(Color.rgb(26, 26, 26)) }
        listOf(first, last, email, phone, branch, service, msg).forEach { content.addView(it, marginParams(16, 6, 16, 6)) }
        content.addView(outlineButton("Read privacy notice", blue).apply { setOnClickListener { showPrivacyNotice() } }, marginParams(16, 6, 16, 4))
        content.addView(privacy, marginParams(16, 6, 16, 6))
        content.addView(button("Submit Request", red, Color.WHITE).apply {
            setOnClickListener {
                if (first.text.isNullOrBlank() || last.text.isNullOrBlank() || email.text.isNullOrBlank() || msg.text.isNullOrBlank()) { toast("Please complete all required fields"); return@setOnClickListener }
                if (!privacy.isChecked) { toast("Please read and acknowledge the privacy notice"); return@setOnClickListener }
                db.submitQuote(first.text.toString(), last.text.toString(), email.text.toString(), phone.text.toString(), spinnerValue(branch), spinnerValue(service), msg.text.toString(), selected?.id?.toString())
                selectedProduct = null; confirmation("Quote request received", "Thanks ${first.text}. Your request has been saved locally on this device and will show up under My Quotes.")
            }
        }, marginParams(16, 12, 16, 20))
    }

    internal fun showPrivacyNotice() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Privacy notice")
            .setMessage("This prototype stores account, quote and contact details in the app's private database on this device and the shared online database. Requests are sent to the database then shared to the website by this mobile app. Quote and contact forms use your name, email, branch, service and message; phone number is optional. To delete your account and your quote requests, use Profile > Delete my account. An administrator must approve the request before anything is removed. For access, correction or deletion requests about the website or other systems, email info@woodlandsdb.co.za. That address must be confirmed by the business before launch.")
            .setPositiveButton("Close", null)
            .show()
    }

    private fun branchesScreen() {
        pageIntro("Our Branches", "Find the branch closest to your project.")
        db.loadBranches().forEach { b ->
            val cardV = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)) }
            cardV.addView(tv("⌖  ${b.name} Branch", 18, blue).apply { setTypeface(typeface, Typeface.BOLD) })
            cardV.addView(tv(b.notes, 12, muted).apply { setPadding(0, dp(4), 0, dp(4)) })
            cardV.addView(tv("${b.region} · ${b.hours}", 12, muted))
            val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            actions.addView(button("Call", blue, Color.WHITE).apply { setOnClickListener { toast("Branch phone: ${b.phone}") } }, marginParams(0, 0, 8, 0))
            actions.addView(button("Open Maps", red, Color.WHITE).apply { setOnClickListener { openMaps("${b.name} Branch, Gauteng") } }, marginParams(0, 0, 8, 0))
            actions.addView(button("Quote", blue, Color.WHITE).apply { setOnClickListener { showScreen("quote") } })
            cardV.addView(actions); content.addView(cardV, marginParams(12, 6, 12, 6))
        }
        content.addView(tv("The current prototype intentionally keeps the branch phone number as 011 XXX XXXX, matching the website seed data.", 11, muted).apply { setPadding(dp(16), dp(10), dp(16), dp(20)) })
    }

    //  more (account hub)

    private fun moreScreen() {
        pageIntro("More", "Your account and information")
        val me = currentUser()
        if (me == null) {
            sectionTitle("Account", "Log in for order history and, for staff accounts, dashboard tools")
            content.addView(button("Login", blue, Color.WHITE).apply { setOnClickListener { showScreen("login") } }, marginParams(16, 6, 16, 6))
            content.addView(outlineButton("Register", blue).apply { setOnClickListener { showScreen("register") } }, marginParams(16, 0, 16, 12))
        } else {
            val cardV = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply { text = initialsOf(me.fullName); textSize = 15f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); background = bg(blue, Color.TRANSPARENT, 22) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
            info.addView(tv(me.fullName, 16, blue).apply { setTypeface(typeface, Typeface.BOLD) })
            info.addView(tv(Roles.label(me.role), 12, muted))
            row.addView(info)
            cardV.addView(row)
            content.addView(cardV, marginParams(16, 6, 16, 10))
            if (Roles.isStaff(me.role)) content.addView(button("Dashboard", blue, Color.WHITE).apply { setOnClickListener { showScreen("dashboard") } }, marginParams(16, 6, 16, 6))
            content.addView(outlineButton(if (Roles.isStaff(me.role)) "Quotes" else "My Quotes", blue).apply { setOnClickListener { showScreen("quotes") } }, marginParams(16, 0, 16, 6))
            content.addView(outlineButton("Profile", blue).apply { setOnClickListener { showScreen("profile") } }, marginParams(16, 0, 16, 6))
            content.addView(outlineButton("Settings", blue).apply { setOnClickListener { showScreen("settings") } }, marginParams(16, 0, 16, 6))
            val unreadCount = db.unreadNotificationCount()
            content.addView(outlineButton(if (unreadCount > 0) "Notifications ($unreadCount unread)" else "Notifications", blue).apply { setOnClickListener { showScreen("notifications") } }, marginParams(16, 0, 16, 6))
            content.addView(outlineButton("Email change request", blue).apply { setOnClickListener { showScreen("emailChange") } }, marginParams(16, 0, 16, 6))
            content.addView(outlineButton("Sign in on web", blue).apply { setOnClickListener { showScreen("approveWeb") } }, marginParams(16, 0, 16, 6))
            content.addView(outlineButton("Logout", red).apply { setOnClickListener { SyncManager.invalidateAuthAttempts(); session.clear(); toast("Signed out"); showScreen("home") } }, marginParams(16, 0, 16, 12))
        }
        // HELP SECTION
        sectionTitle("Help", "Tutorials, answers and branch support")

        val helpCard = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                showScreen("help")
            }
        }

        helpCard.addView(
            tv("Help Centre", 17, blue).apply {
                setTypeface(typeface, Typeface.BOLD)
            }
        )

        helpCard.addView(
            tv(
                "Access the tutorial, frequently asked questions and branch manager contact details.",
                12,
                muted
            ).apply {
                setPadding(0, dp(4), 0, 0)
            }
        )

        content.addView(helpCard, marginParams(16, 6, 16, 12))

// OTHER INFORMATION
        sectionTitle("Information", "")

        listOf(
            "About Us" to "Learn about Woodlands Designer Boards",
            "Services" to "See what we offer",
            "Testimonials" to "Read customer feedback",
            "Contact Us" to "Send a message or start a quote",
            "Legal Information" to "Privacy, terms, refunds and cookies",
            "Connection Status" to "Check the app's connection status",
            "Sync Issues" to "View and retry failed operations stored on this device"
        ).forEach { (title, sub) ->

            val c = card().apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))

                setOnClickListener {
                    when (title) {
                        "About Us" -> showScreen("about")
                        "Services" -> showScreen("services")
                        "Testimonials" -> showScreen("testimonials")
                        "Legal Information" -> showScreen("legal")
                        "Connection Status" -> showScreen("status")
                        "Sync Issues" -> showScreen("syncIssues")
                        else -> showScreen("contact")
                    }
                }
            }

            c.addView(
                tv(title, 17, blue).apply {
                    setTypeface(typeface, Typeface.BOLD)
                }
            )

            c.addView(
                tv(sub, 12, muted).apply {
                    setPadding(0, dp(4), 0, 0)
                }
            )

            content.addView(c, marginParams(16, 6, 16, 6))
        }

        footerNote()
    }


    internal fun signIn(user: AppUser) {
        session.userId = user.id
        toast("Signed in as ${user.fullName}")
        showScreen(if (Roles.isStaff(user.role)) "dashboard" else "home")
    }

    //  about / testimonials / faqs / contact

    private fun aboutScreen() {
        pageIntro("About Woodlands Designer Boards", "Explore our custom furniture and cabinetry services for homes, designers, and contractors across Gauteng.")
        image(R.drawable.kitchen_12)
        sectionTitle("Custom Spaces Across Gauteng", "Our Story")
        para("Woodlands Designer Boards creates custom furniture and cabinetry for residential and trade projects. We help clients plan their spaces, choose suitable materials, and shape a design around their needs.")
        para("Our range includes board materials and made-to-measure units. Get in touch to discuss your project, material options, measurements, and installation requirements.")
        featureCard("Materials for your project", "Ask us about available board and finish options.")
        sectionTitle("Why Choose Us", "")
        featureCard("PG Bison Certified", "Authorised partner of SA's leading board manufacturer — every piece uses genuine PG Bison materials.")
        featureCard("CNC Precision", "Tolerances of ±0.5mm. What you design is exactly what gets built.")
        featureCard("All Client Types", "Homeowners, interior designers, and building contractors — we serve every scale of project.")
        featureCard("3 Gauteng Branches", "Soweto, Roodepoort, and Randfontein — always close to your project site.")
        sectionTitle("Our Three Branches", "")
        featureCard("⌖ Soweto Branch", "Serving customers in the Soweto area.")
        featureCard("⌖ Roodepoort Branch", "Serving customers in the Roodepoort area.")
        featureCard("⌖ Randfontein Branch", "Serving customers in the Randfontein area.")
        cta("Ready to Work With Us?", "Get a free quote — we'll measure, design, and install everything.", "Get a Free Quote →") { showScreen("quote") }
    }

    private fun testimonialsScreen() {
        pageIntro("Client feedback", "What our customers say about Woodlands Designer Boards.")

        val testimonialsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        content.addView(testimonialsContainer)

        fun renderTestimonials() {
            testimonialsContainer.removeAllViews()

            val list = db.loadTestimonials()

            if (list.isEmpty()) {
                testimonialsContainer.addView(
                    card().apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(dp(16), dp(16), dp(16), dp(16))

                        addView(
                            tv(
                                "No testimonials are available right now.",
                                14,
                                text
                            )
                        )

                        addView(
                            tv(
                                "Connect to the internet and try again.",
                                12,
                                muted
                            ).apply {
                                setPadding(0, dp(6), 0, 0)
                            }
                        )
                    },
                    marginParams(16, 10, 16, 10)
                )

                return
            }

            list.forEach { testimonial ->
                val cardView = card().apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(16), dp(15), dp(16), dp(15))
                }

                cardView.addView(
                    tv(
                        testimonial.name,
                        17,
                        blue
                    ).apply {
                        setTypeface(typeface, Typeface.BOLD)
                    }
                )

                val metaParts = mutableListOf<String>()

                if (testimonial.role.isNotBlank()) {
                    metaParts.add(testimonial.role)
                }

                if (testimonial.location.isNotBlank()) {
                    metaParts.add(testimonial.location)
                }

                if (metaParts.isNotEmpty()) {
                    cardView.addView(
                        tv(
                            metaParts.joinToString(" · "),
                            11,
                            muted
                        ).apply {
                            setPadding(0, dp(3), 0, 0)
                        }
                    )
                }

                val rating = testimonial.rating.coerceIn(1, 5)

                cardView.addView(
                    tv(
                        "★".repeat(rating) + "☆".repeat(5 - rating),
                        16,
                        tan
                    ).apply {
                        setTypeface(typeface, Typeface.BOLD)
                        setPadding(0, dp(8), 0, 0)
                    }
                )

                if (testimonial.review.isNotBlank()) {
                    cardView.addView(
                        tv(
                            "“${testimonial.review}”",
                            14,
                            text
                        ).apply {
                            setPadding(0, dp(10), 0, 0)
                            setLineSpacing(dp(2).toFloat(), 1f)
                        }
                    )
                }

                if (testimonial.project.isNotBlank()) {
                    cardView.addView(
                        tv(
                            testimonial.project,
                            11,
                            muted
                        ).apply {
                            setTypeface(typeface, Typeface.BOLD)
                            setPadding(0, dp(10), 0, 0)
                        }
                    )
                }

                testimonialsContainer.addView(
                    cardView,
                    marginParams(16, 6, 16, 6)
                )
            }
        }

        renderTestimonials()
        SyncManager.syncAll()

        cta(
            "Have a project in mind?",
            "Send a quote request to get started.",
            "Request a Quote"
        ) {
            showScreen("quote")
        }
    }

    private fun faqScreen() {
        pageIntro("Frequently Asked Questions", "Filter questions by category and tap to expand an answer.")
        var filter = "All"
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val chips = horizontalChips(listOf("All", "Materials", "Process", "Services", "Installation", "Trade", "Branches", "Warranty"), filter) { new -> filter = new; container.removeAllViews(); renderFaqs(container, new) }
        content.addView(chips); content.addView(container); renderFaqs(container, filter)
        cta("Still have questions?", "Drop us a message.", "Contact Us") { showScreen("contact") }
    }
    private fun renderFaqs(container: LinearLayout, filter: String) {
        val list = db.loadFaqs().filter { filter == "All" || it.category == filter }; list.forEach { f ->
            val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
            val q = tv("▸ ${f.question}", 14, blue).apply { setTypeface(typeface, Typeface.BOLD) }; val a = tv(f.answer, 12, muted).apply { visibility = View.GONE; setPadding(dp(4), dp(10), dp(4), 0) }
            c.isClickable = true
            c.setOnClickListener { a.visibility = if (a.visibility == View.VISIBLE) View.GONE else View.VISIBLE; q.text = if (a.visibility == View.VISIBLE) "▾ ${f.question}" else "▸ ${f.question}" }
            c.addView(q); c.addView(tv(f.category, 10, red)); c.addView(a); container.addView(c, marginParams(12, 4, 12, 4))
        }
    }

    private fun contactScreen() {
        pageIntro("Contact Us", "Send a message to Woodlands Designer Boards.")
        sectionTitle("Branch contacts", "Our current prototype has three branches")
        db.loadBranches().forEach { b -> content.addView(tv("${b.name}: ${b.phone} · ${b.hours}", 12, muted).apply { setPadding(dp(16), dp(3), dp(16), dp(3)) }) }
        sectionTitle("Message", "We'll use the same branch and service options as the quote flow.")
        val me = currentUser()
        val nameParts = me?.fullName?.trim()?.split(" ", limit = 2)
        val first = field("First Name", "e.g. Thabo").apply { if (nameParts != null) setText(nameParts.getOrNull(0).orEmpty()) }
        val last = field("Last Name", "e.g. Mokoena").apply { if (nameParts != null) setText(nameParts.getOrNull(1).orEmpty()) }
        val email = field("Email Address", "you@example.com").apply { if (me != null) setText(me.email) }
        val phone = field("Phone Number", "071 234 5678", required = false).apply { if (me?.phone != null) setText(me.phone) }
        val branch = spinnerField("Nearest Branch", db.loadBranches().map { it.name })
        val service = spinnerField("Service Required", listOf("Kitchen Units", "TV Stands", "Built-In Cupboards", "Cutting & Edging", "General Enquiry"))
        val msg = field("Message", "How can we help?", true)
        val privacy = CheckBox(this).apply { text = "I have read the privacy notice. This prototype saves message details on this device."; setTextColor(Color.rgb(26, 26, 26)) }
        listOf(first, last, email, phone, branch, service, msg).forEach { content.addView(it, marginParams(16, 6, 16, 6)) }
        content.addView(outlineButton("Read privacy notice", blue).apply { setOnClickListener { showPrivacyNotice() } }, marginParams(16, 6, 16, 4))
        content.addView(privacy, marginParams(16, 6, 16, 6))
        content.addView(button("Send Message", red, Color.WHITE).apply {
            setOnClickListener {
                if (first.text.isNullOrBlank() || last.text.isNullOrBlank() || email.text.isNullOrBlank() || msg.text.isNullOrBlank()) { toast("Please complete all required fields"); return@setOnClickListener }
                if (!privacy.isChecked) { toast("Please read and acknowledge the privacy notice"); return@setOnClickListener }
                db.writableDatabase.execSQL("INSERT INTO contact_submissions(first_name,last_name,email,phone,branch,service,message,created_at) VALUES(?,?,?,?,?,?,?,?)", arrayOf<Any?>(first.text.toString(), last.text.toString(), email.text.toString(), phone.text.toString(), spinnerValue(branch), spinnerValue(service), msg.text.toString(), System.currentTimeMillis()))
                confirmation("Message saved", "Your message has been saved locally in this prototype.")
            }
        }, marginParams(16, 12, 16, 20))
    }

    //  shared UI building blocks
    private fun productList(products: List<Product>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        products.forEach { p ->
            val c = card().apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(8), dp(8), dp(8)); setOnClickListener { selectedProduct = p.id; showScreen("product") } }
            val im = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP; setProductImage(this, p.image) }; c.addView(im, LinearLayout.LayoutParams(dp(112), dp(104)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }; info.addView(tv(p.category.uppercase(), 10, red)); info.addView(tv(p.title, 15, blue).apply { setTypeface(typeface, Typeface.BOLD) }); info.addView(tv(p.tagline, 11, muted)); info.addView(tv(p.price, 13, blue).apply { setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(5), 0, 0) }); c.addView(info, LinearLayout.LayoutParams(0, dp(104), 1f)); row.addView(c, marginParams(12, 6, 12, 6))
        }; content.addView(row)
    }

    internal fun testimonialCard(t: Testimonial) { val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)) }; c.addView(tv("★★★★★".replaceRange(t.rating, 5, ""), 16, red)); c.addView(tv("\"${t.review}\"", 13, muted).apply { setPadding(0, dp(7), 0, dp(9)) }); c.addView(tv(t.name, 13, blue).apply { setTypeface(typeface, Typeface.BOLD) }); c.addView(tv("${t.role} · ${t.location}", 11, muted)); c.addView(chip(t.project)); content.addView(c, marginParams(12, 5, 12, 5)) }
    internal fun featureCard(title: String, desc: String) { val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(14), dp(14), dp(14)) }; c.addView(tv(title, 15, blue).apply { setTypeface(typeface, Typeface.BOLD) }); c.addView(tv(desc, 12, muted).apply { setPadding(0, dp(4), 0, 0) }); content.addView(c, marginParams(12, 5, 12, 5)) }
    internal fun statsStrip() { val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(10), dp(8), dp(10)) }; listOf("15+" to "Years", "500+" to "Projects", "3" to "Branches", "100%" to "PG Bison").forEach { (a, b) -> val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }; c.addView(tv(a, 20, red).apply { setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER }); c.addView(tv(b, 10, muted).apply { gravity = Gravity.CENTER }); row.addView(c, LinearLayout.LayoutParams(0, dp(70), 1f)) }; content.addView(row) }
    internal fun sectionTitle(title: String, sub: String) { val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(22), dp(16), dp(8)) }; box.addView(tv(title, 22, blue).apply { setTypeface(Typeface.SERIF, Typeface.BOLD) }); if (sub.isNotBlank()) box.addView(tv(sub, 12, muted).apply { setPadding(0, dp(3), 0, 0) }); content.addView(box) }
    internal fun pageIntro(title: String, sub: String) { val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(22), dp(16), dp(20)); setBackgroundColor(blue) }; b.addView(tv(title, 26, Color.WHITE).apply { setTypeface(Typeface.SERIF, Typeface.BOLD) }); if (sub.isNotBlank()) b.addView(tv(sub, 12, Color.rgb(224, 231, 223)).apply { setPadding(0, dp(5), 0, 0) }); content.addView(b); content.addView(View(this).apply { setBackgroundColor(tan) }, LinearLayout.LayoutParams(-1, dp(3))) }
    internal fun para(s: String) { content.addView(tv(s, 13, muted).apply { setPadding(dp(16), dp(4), dp(16), dp(7)) }) }
    internal fun bullet(s: String) { content.addView(tv("✓  $s", 13, muted).apply { setPadding(dp(20), dp(4), dp(16), dp(4)) }) }
    internal fun infoRow(a: String, b: String) { val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), dp(4), dp(16), dp(4)) }; row.addView(tv(a, 12, muted), LinearLayout.LayoutParams(0, dp(28), 1f)); row.addView(tv(b, 13, blue).apply { setTypeface(typeface, Typeface.BOLD); gravity = Gravity.END }, LinearLayout.LayoutParams(0, dp(28), 1f)); content.addView(row) }
    internal fun cta(title: String, sub: String, label: String, onClick: () -> Unit) { val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), dp(20)); background = bg(blue, Color.TRANSPARENT, 16) }; c.addView(tv(title, 20, Color.WHITE).apply { setTypeface(Typeface.SERIF, Typeface.BOLD); gravity = Gravity.CENTER }); c.addView(tv(sub, 12, Color.rgb(224, 231, 223)).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(12)) }); c.addView(button(label, tan, blue).apply { setOnClickListener { onClick() } }); content.addView(c, marginParams(12, 16, 12, 16)) }
    internal fun footerNote() { content.addView(tv("WOODLANDS DESIGNER BOARDS\nPremium custom-built units using PG Bison materials.\nSoweto · Roodepoort · Randfontein", 11, muted).apply { gravity = Gravity.CENTER; setPadding(dp(16), dp(16), dp(16), dp(24)) }) }
    internal fun image(res: Int) { content.addView(ImageView(this).apply { setImageResource(res); scaleType = ImageView.ScaleType.CENTER_CROP }, sizeMarginParams(-1, dp(180), 16, 10, 16, 10)) }

    internal fun horizontalChips(items: List<String>, selected: String, onPick: (String) -> Unit): HorizontalScrollView { val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(12), dp(5), dp(12), dp(8)) }; items.forEach { i -> row.addView(chip(i, i == selected).apply { setOnClickListener { onPick(i) } }, marginParams(0, 0, 7, 0)) }; return HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(row) } }
    internal fun chip(label: String, selected: Boolean = true): TextView = tv(label, 11, if (selected) Color.WHITE else blue).apply { setPadding(dp(12), dp(7), dp(12), dp(7)); background = rippleBg(if (selected) blue else Color.WHITE, if (selected) blue else Color.LTGRAY, 18, if (selected) 70 else 35) }
    internal fun chip(label: String): TextView = chip(label, false)
    internal fun card(): LinearLayout = LinearLayout(this).apply { background = rippleBg(Color.WHITE, lineColor, 16, 30); elevation = 3f }
    internal fun button(label: String, bgColor: Int, fg: Int): Button = Button(this).apply { text = label; textSize = 13f; setTextColor(fg); setAllCaps(false); setTypeface(typeface, Typeface.BOLD); background = rippleBg(bgColor, bgColor, 10, 100); minHeight = dp(44); minimumHeight = dp(44); stateListAnimator = null }
    internal fun outlineButton(label: String, color: Int): Button = Button(this).apply { text = label; textSize = 13f; setTextColor(color); setAllCaps(false); setTypeface(typeface, Typeface.BOLD); background = rippleBg(Color.WHITE, color, 10, 40); minHeight = dp(44); minimumHeight = dp(44); stateListAnimator = null }
    internal fun tv(s: String, size: Float, color: Int) = TextView(this).apply { text = s; textSize = size; setTextColor(color); includeFontPadding = true }
    internal fun tv(s: String, size: Int, color: Int) = tv(s, size.toFloat(), color)
    internal fun field(label: String, hint: String, multi: Boolean = false, required: Boolean = true): EditText = EditText(this).apply { this.hint = if (required) "$label *" else "$label (optional)"; textSize = 14f; setTextColor(this@MainActivity.text); setHintTextColor(muted); background = bg(Color.WHITE, Color.rgb(201, 201, 193), 10); setPadding(dp(12), dp(11), dp(12), dp(11)); if (multi) { minLines = 4; gravity = Gravity.TOP; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE } else { inputType = InputType.TYPE_CLASS_TEXT } }
    internal fun passwordField(label: String): EditText = field(label, label).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
    internal fun spinnerField(label: String, items: List<String>, preselect: String? = null): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(tv("$label *", 11, text).apply { setPadding(dp(2), 0, dp(2), dp(4)) })
        val spinner = Spinner(this).apply { adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items); tag = "spinner:$label"; background = bg(Color.WHITE, Color.rgb(201, 201, 193), 10); if (preselect != null) { val idx = items.indexOf(preselect); if (idx >= 0) setSelection(idx) } }
        box.addView(spinner, LinearLayout.LayoutParams(-1, dp(48)))
        return box
    }

    internal fun spinnerValue(container: ViewGroup): String { val s = container.getChildAt(1) as Spinner; return s.selectedItem?.toString().orEmpty() }
    internal fun confirmation(title: String, msg: String) { content.removeAllViews(); pageIntro(title, msg); content.addView(button("Back to Home", red, Color.WHITE).apply { setOnClickListener { showScreen("home") } }, marginParams(16, 20, 16, 10)); content.addView(button("Browse Gallery", blue, Color.WHITE).apply { setOnClickListener { showScreen("gallery") } }, marginParams(16, 6, 16, 10)); bottom.visibility = View.GONE }
    internal fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    internal fun openMaps(query: String) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(query)))) }
    internal fun marginParams(l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT).also { it.setMargins(dp(l), dp(t), dp(r), dp(b)) }
    internal fun sizeMarginParams(width: Int, height: Int, l: Int, t: Int, r: Int, b: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(if (width < 0) -1 else dp(width), height).also { it.setMargins(dp(l), dp(t), dp(r), dp(b)) }
    internal fun bg(fill: Int, stroke: Int, radius: Int) = GradientDrawable().apply { setColor(fill); if (stroke != Color.TRANSPARENT) setStroke(dp(1), stroke); cornerRadius = dp(radius).toFloat() }

    /** A pressable fill that visibly darkens while touched — the app-wide "button feels tappable" treatment. */
    internal fun rippleBg(fill: Int, stroke: Int, radius: Int, rippleAlpha: Int = 60): Drawable {
        val base = GradientDrawable().apply { setColor(fill); if (stroke != Color.TRANSPARENT && stroke != fill) setStroke(dp(1), stroke); cornerRadius = dp(radius).toFloat() }
        val mask = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(radius).toFloat() }
        val overlay = if (isLightColor(fill)) Color.argb(rippleAlpha, 0, 0, 0) else Color.argb((rippleAlpha * 1.3f).toInt().coerceAtMost(160), 0, 0, 0)
        return RippleDrawable(ColorStateList.valueOf(overlay), base, mask)
    }
    private fun isLightColor(color: Int): Boolean { val luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255; return luminance > 0.55 }
    internal fun darkenColor(color: Int, factor: Float): Int { val r = (Color.red(color) * factor).toInt().coerceIn(0, 255); val g = (Color.green(color) * factor).toInt().coerceIn(0, 255); val bl = (Color.blue(color) * factor).toInt().coerceIn(0, 255); return Color.argb(Color.alpha(color), r, g, bl) }
    internal fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    internal fun initialsOf(name: String): String { val parts = name.trim().split(" ").filter { it.isNotBlank() }; return if (parts.isEmpty()) "U" else parts.take(2).joinToString("") { it.first().uppercase() } }

    internal fun imageRes(key: String): Int = when (key) { "kitchen_12" -> R.drawable.kitchen_12; "kitchen_7" -> R.drawable.kitchen_7; "kitchen_10" -> R.drawable.kitchen_10; "kitchen_6" -> R.drawable.kitchen_6; "kitchen_9" -> R.drawable.kitchen_9; "kitchen_11" -> R.drawable.kitchen_11; "kitchen_4" -> R.drawable.kitchen_4; "kitchen_3" -> R.drawable.kitchen_3; "kitchen_2" -> R.drawable.kitchen_2; "tv_1" -> R.drawable.tv_1; "tv_2" -> R.drawable.tv_2; "tv_4" -> R.drawable.tv_4; "tv_8" -> R.drawable.tv_8; "tv_9" -> R.drawable.tv_9; "tv_10" -> R.drawable.tv_10; "tv_5" -> R.drawable.tv_5; else -> R.drawable.kitchen_12 }
    internal val productImageKeys = listOf("kitchen_12", "kitchen_7", "kitchen_10", "kitchen_6", "kitchen_9", "kitchen_11", "kitchen_4", "kitchen_3", "kitchen_2", "tv_1", "tv_2", "tv_4", "tv_8", "tv_9", "tv_10", "tv_5")
}