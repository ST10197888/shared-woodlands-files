package com.woodlands.mobile

import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun MainActivity.requireStaff(): AppUser? {
    val u = currentUser()
    if (u == null || !Roles.isStaff(u.role)) { showScreen("home"); return null }
    return u
}

private fun MainActivity.requireAdmin(): AppUser? {
    val u = currentUser()
    if (u == null || u.role != Roles.ADMIN) { showScreen("home"); return null }
    return u
}

internal fun MainActivity.deletionRequestsScreen() {
    val me = requireAdmin() ?: return
    pageIntro("Deletion Requests", "Approve or reject account deletion requests.")
    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(list)
    list.addView(tv("Loading requests…", 13, muted).apply { setPadding(dp(16), dp(12), dp(16), dp(12)) })

    Thread {
        val r = ApiClient.get("/api/deletion-requests?viewer=" + java.net.URLEncoder.encode(me.id, "UTF-8"))
        runOnUiThread {
            if (currentScreen != "deletionRequests") return@runOnUiThread
            list.removeAllViews()
            when (r) {
                is ApiResult.Offline -> list.addView(tv("You're offline. Connect to the internet to see requests.", 13, muted).apply { setPadding(dp(16), dp(12), dp(16), dp(12)) })
                is ApiResult.Failure -> list.addView(tv(r.message, 13, red).apply { setPadding(dp(16), dp(12), dp(16), dp(12)) })
                is ApiResult.Success -> {
                    val arr = try { org.json.JSONArray(r.body) } catch (e: Exception) { org.json.JSONArray() }
                    if (arr.length() == 0) {
                        list.addView(tv("No deletion requests.", 13, muted).apply { setPadding(dp(16), dp(12), dp(16), dp(12)) })
                    }
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val id = o.optString("id")
                        val st = o.optString("status")
                        val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
                        c.addView(tv(o.optString("full_name"), 15, blue).apply { setTypeface(typeface, Typeface.BOLD) })
                        c.addView(tv("${o.optString("email")} · ${Roles.label(o.optString("role"))}", 11, muted))
                        c.addView(tv("Requested ${o.optString("created_at").take(10)} · $st", 11, if (st == "Pending") red else muted)
                            .apply { setPadding(0, dp(2), 0, 0) })
                        o.optString("message").takeIf { it.isNotBlank() && it != "null" }?.let {
                            c.addView(tv("Message: $it", 13, text).apply { setPadding(0, dp(8), 0, 0) })
                        }
                        if (st == "Pending") {
                            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
                            row.addView(button("Approve & delete", red, Color.WHITE).apply {
                                setOnClickListener {
                                    android.app.AlertDialog.Builder(this@deletionRequestsScreen)
                                        .setTitle("Delete this account?")
                                        .setMessage("${o.optString("full_name")}'s account will be permanently deleted. This cannot be undone.")
                                        .setNegativeButton("Cancel", null)
                                        .setPositiveButton("Delete account") { _, _ -> decideDeletion(id, true, me.id) }
                                        .show()
                                }
                            }, marginParams(0, 0, 8, 0).apply { width = LinearLayout.LayoutParams.WRAP_CONTENT })
                            row.addView(outlineButton("Reject", blue).apply {
                                setOnClickListener { decideDeletion(id, false, me.id) }
                            })
                            c.addView(row)
                        }
                        list.addView(c, marginParams(16, 5, 16, 5))
                    }
                }
            }
        }
    }.start()
}

private fun MainActivity.decideDeletion(id: String, approve: Boolean, adminId: String) {
    toast("Working…")
    Thread {
        // API: POST /api/deletion-requests/:id/approve, PUT /api/deletion-requests/:id/reject
        val r = if (approve) ApiClient.post("/api/deletion-requests/$id/approve", "{}")
        else ApiClient.put("/api/deletion-requests/$id/reject", "{}")
        runOnUiThread {
            when (r) {
                is ApiResult.Success -> {
                    toast(if (approve) "Account deleted" else "Request rejected")
                    if (approve) SyncManager.syncAll()
                    showScreen("deletionRequests")
                }
                is ApiResult.Failure -> toast(r.message)
                is ApiResult.Offline -> toast("You're offline. Nothing was changed.")
            }
        }
    }.start()
}

private fun MainActivity.statusColors(status: String): Pair<Int, Int> = when (status) {
    "Completed" -> greenBg to green
    "In Progress" -> lightBlueBg to blue
    "Cancelled" -> Color.rgb(253, 235, 235) to red
    else -> Color.rgb(255, 247, 230) to Color.rgb(180, 120, 20)
}

private fun MainActivity.statCard(label: String, value: String) {
    val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)) }
    c.addView(tv(value, 24, blue).apply { setTypeface(typeface, Typeface.BOLD) })
    c.addView(tv(label, 12, muted))
    content.addView(c, marginParams(16, 6, 16, 6))
}

internal fun MainActivity.dashboardScreen() {
    val me = requireStaff() ?: return
    val isAdmin = me.role == Roles.ADMIN
    val branch = Roles.branchFor(me.role)
    pageIntro("Dashboard", "Welcome back, ${me.fullName}")
    val quotes = db.loadQuotes()

    if (isAdmin) {
        val total = quotes.size
        val customers = quotes.map { it.email.lowercase() }.distinct().size
        val completed = quotes.count { it.status == "Completed" }
        val rate = if (total == 0) 0 else (completed * 100 / total)
        statCard("Total Quotes", total.toString())
        statCard("Total Customers", customers.toString())
        statCard("Completed Jobs", completed.toString())
        statCard("Completion Rate", "$rate%")
        sectionTitle("Branches", "")
        listOf("Soweto", "Roodepoort", "Randfontein").forEach { b ->
            val bq = quotes.filter { it.branch == b }
            val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
            c.addView(tv(b, 14, blue).apply { setTypeface(typeface, Typeface.BOLD) })
            c.addView(tv("${bq.size} quotes · ${bq.count { it.status == "Completed" }} completed", 12, muted))
            content.addView(c, marginParams(16, 4, 16, 4))
        }
        sectionTitle("Manage", "Admin-only tools, same permissions as the website")
        content.addView(button("Manage Users", blue, Color.WHITE).apply { setOnClickListener { showScreen("users") } }, marginParams(16, 6, 16, 6))
        content.addView(button("Manage Products", blue, Color.WHITE).apply { setOnClickListener { showScreen("manageProducts") } }, marginParams(16, 0, 16, 6))
        content.addView(button("Manage Testimonials", blue, Color.WHITE).apply { setOnClickListener { showScreen("manageTestimonials") } }, marginParams(16, 0, 16, 6))
        content.addView(button("Manage FAQs", blue, Color.WHITE).apply { setOnClickListener { showScreen("manageFaqs") } }, marginParams(16, 0, 16, 6))
    } else if (branch != null) {
        val bq = quotes.filter { it.branch == branch }
        statCard("Pending", bq.count { it.status == "Pending" }.toString())
        statCard("In Progress", bq.count { it.status == "In Progress" }.toString())
        statCard("Completed", bq.count { it.status == "Completed" }.toString())
        sectionTitle("Needs attention", "Pending quotes for $branch")
        val pending = bq.filter { it.status == "Pending" }
        if (pending.isEmpty()) content.addView(tv("Nothing pending right now.", 12, muted).apply { setPadding(dp(16), dp(4), dp(16), dp(10)) })
        pending.take(5).forEach { q -> content.addView(tv("• ${q.firstName} ${q.lastName} — ${q.service}", 13, text).apply { setPadding(dp(16), dp(4), dp(16), dp(4)) }) }
        sectionTitle("Manage", "")
        content.addView(button("Manage Products", blue, Color.WHITE).apply { setOnClickListener { showScreen("manageProducts") } }, marginParams(16, 6, 16, 6))
    }
    content.addView(outlineButton("View All Quotes", blue).apply { setOnClickListener { showScreen("quotes") } }, marginParams(16, 10, 16, 24))
}

internal fun MainActivity.quotesScreen() {
    val me = currentUser()
    if (me == null) { showScreen("login"); return }
    val isAdmin = me.role == Roles.ADMIN
    val myBranch = Roles.branchFor(me.role)
    pageIntro(
        if (Roles.isStaff(me.role)) "Quotes" else "My Quotes",
        if (isAdmin) "All quote requests across every branch" else if (myBranch != null) "Quote requests for the $myBranch branch" else "Your submitted quote requests"
    )
    val all = db.loadQuotes()
    val list = when {
        isAdmin -> all
        myBranch != null -> all.filter { it.branch == myBranch }
        else -> all.filter { it.email.equals(me.email, ignoreCase = true) }
    }
    if (list.isEmpty()) content.addView(tv(if (Roles.isStaff(me.role)) "No quotes yet." else "You haven't submitted a quote request yet.", 13, muted).apply { setPadding(dp(16), dp(20), dp(16), dp(20)) })
    list.forEach { q ->
        val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(tv(q.quoteCode.ifBlank { "Quote #${q.id}" }, 13, blue).apply { setTypeface(typeface, Typeface.BOLD) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val (bgC, fgC) = statusColors(q.status)
        top.addView(tv(q.status, 10, fgC).apply { setPadding(dp(8), dp(3), dp(8), dp(3)); background = bg(bgC, Color.TRANSPARENT, 10) })
        c.addView(top)
        c.addView(tv("${q.firstName} ${q.lastName} · ${q.service}", 13, text).apply { setPadding(0, dp(6), 0, 0) })
        c.addView(tv("${q.branch} branch · ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(q.createdAt))}", 11, muted))
        val quoteValue = q.value
        if (!quoteValue.isNullOrBlank()) c.addView(tv(quoteValue, 13, blue).apply { setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(4), 0, 0) })
        if (Roles.isStaff(me.role)) {
            val statusRow = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            val inner = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
            listOf("Pending", "In Progress", "Completed", "Cancelled").forEach { s ->
                inner.addView(chip(s, s == q.status).apply { setOnClickListener { db.updateQuoteStatus(q.id, s); showScreen("quotes") } }, marginParams(0, 0, 6, 0))
            }
            statusRow.addView(inner)
            c.addView(statusRow)
        }
        content.addView(c, marginParams(16, 5, 16, 5))
    }
}

internal fun MainActivity.usersScreen() {
    requireAdmin() ?: return
    pageIntro("Manage Users", "Add, edit or deactivate staff and customer accounts.")
    content.addView(button("+ Add User", red, Color.WHITE).apply { setOnClickListener { editUserId = null; showScreen("userForm") } }, marginParams(16, 10, 16, 10))
    content.addView(outlineButton("Account deletion requests", red).apply { setOnClickListener { showScreen("deletionRequests") } }, marginParams(16, 0, 16, 10))
    db.loadUsers().forEach { u ->
        val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        c.addView(tv(u.fullName, 14, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv("${u.email} · ${Roles.label(u.role)}", 11, muted))
        if (!u.active) c.addView(tv("Inactive", 10, red).apply { setPadding(0, dp(2), 0, 0) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row.addView(button("Edit", blue, Color.WHITE).apply { setOnClickListener { editUserId = u.id; showScreen("userForm") } }, marginParams(0, 0, 8, 0))
        row.addView(button("Delete", red, Color.WHITE).apply {
            setOnClickListener {
                if (u.id == currentUser()?.id) { toast("You can't delete your own account"); return@setOnClickListener }
                db.deleteUser(u.id); toast("User deleted"); showScreen("users")
            }
        })
        c.addView(row)
        content.addView(c, marginParams(16, 5, 16, 5))
    }
}

internal fun MainActivity.userFormScreen() {
    requireAdmin() ?: return
    val existing = editUserId?.let { id -> db.loadUsers().firstOrNull { it.id == id } }
    pageIntro(if (existing == null) "Add User" else "Edit User", "")
    val name = field("Full Name", "e.g. Thabo Mokoena").apply { if (existing != null) setText(existing.fullName) }
    val email = field("Email Address", "you@example.com").apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        if (existing != null) { setText(existing.email); isEnabled = false; alpha = 0.6f }
    }
    val phone = field("Phone Number", "071 234 5678").apply { if (existing?.phone != null) setText(existing.phone) }
    val role = spinnerField("Role", Roles.ALL, existing?.role ?: Roles.CUSTOMER)
    val password = passwordField("Password (min 8 characters)")
    listOf(name, email, phone).forEach { content.addView(it, marginParams(16, 6, 16, 6)) }
    content.addView(role, marginParams(16, 6, 16, 6))
    if (existing == null) content.addView(password, marginParams(16, 6, 16, 6))
    else content.addView(tv("Email and password can't be changed from the app.", 11, muted).apply { setPadding(dp(16), dp(4), dp(16), dp(4)) })
    val activeCheck = CheckBox(this).apply { text = "Active"; isChecked = existing?.active ?: true }
    content.addView(activeCheck, marginParams(16, 8, 16, 0))
    content.addView(button(if (existing == null) "Create User" else "Save Changes", red, Color.WHITE).apply {
        setOnClickListener {
            val fullName = name.text.toString().trim()
            val emailValue = email.text.toString().trim()
            val phoneValue = phone.text.toString().trim().ifBlank { null }
            val roleValue = spinnerValue(role)
            val branch = Roles.branchFor(roleValue)
            if (fullName.isBlank() || emailValue.isBlank()) { toast("Name and email are required"); return@setOnClickListener }
            if (existing == null) {
                val pwd = password.text.toString()
                if (pwd.length < 8) { toast("Password must be at least 8 characters"); return@setOnClickListener }
                val newId = db.createUser(fullName, emailValue, phoneValue, pwd, roleValue, branch)
                if (newId == null) { toast("A user with that email already exists"); return@setOnClickListener }
            } else {
                db.updateUserAdmin(existing.id, fullName, emailValue, phoneValue, roleValue, branch, activeCheck.isChecked)
            }
            toast("Saved"); editUserId = null; showScreen("users")
        }
    }, marginParams(16, 10, 16, 24))
}
internal fun MainActivity.manageProductsScreen() {
    val me = requireStaff() ?: return
    pageIntro("Products", "Product CRUD is available to admins and branch managers.")
    content.addView(button("+ Add Product", blue, Color.WHITE).apply { setOnClickListener { editProductId = 0; showScreen("productForm") } }, marginParams(16, 10, 16, 10))
    val categories = listOf("Kitchen Units", "TV Stands", "Built-In Cupboards", "Cutting & Edging")
    val products = db.loadProducts()
    categories.forEach { cat ->
        val catProducts = products.filter { it.category == cat }
        if (catProducts.isNotEmpty()) {
            sectionTitle(cat, "")
            catProducts.forEach { p ->
                val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
                c.addView(tv(p.title, 14, blue).apply { setTypeface(typeface, Typeface.BOLD) })
                c.addView(tv("ID: ${p.id} · ${p.price}", 11, muted))
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
                row.addView(button("Edit", blue, Color.WHITE).apply { setOnClickListener { editProductId = p.id; showScreen("productForm") } }, marginParams(0, 0, 8, 0))
                row.addView(button(if (me.role == Roles.ADMIN) "Delete" else "Request deletion", red, Color.WHITE).apply {
                    setOnClickListener {
                        if (me.role == Roles.ADMIN) { db.deleteProduct(p.id); toast("Product deleted"); showScreen("manageProducts") }
                        else requestProductDeletion(p.id, p.title)
                    }
                })
                c.addView(row)
                content.addView(c, marginParams(16, 5, 16, 5))
            }
        }
    }
}

internal fun MainActivity.productFormScreen() {
    requireStaff() ?: return
    val existing = if (editProductId > 0) db.loadProducts().firstOrNull { it.id == editProductId } else null
    val isEditing = existing != null

    pageIntro(if (isEditing) "Edit Product" else "Add Product", "Fill in the details below the same way a customer will see them on the product page.")

    val categories = listOf("Kitchen Units", "TV Stands", "Built-In Cupboards", "Cutting & Edging")
    val presetTags = listOf("Popular", "New", "Sale", "Limited")
    val currentTag = existing?.tag ?: ""
    val isPresetTag = currentTag.isEmpty() || currentTag in presetTags

    val summaryTitle = tv("Untitled product", 13, blue).apply { setTypeface(typeface, Typeface.BOLD) }
    val summaryCategory = tv("No category selected", 13, blue)
    val summary = card().apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = bg(Color.rgb(244, 248, 243), Color.TRANSPARENT, 8)
        val lbl = tv(if (isEditing) "Editing" else "Creating", 11, blue).apply { setTypeface(typeface, Typeface.BOLD); setPadding(0, 0, dp(8), 0) }
        val row = LinearLayout(this@productFormScreen).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(lbl)
        row.addView(summaryTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(tv(" · ", 11, blue))
        row.addView(summaryCategory)
        addView(row)
    }
    content.addView(summary, marginParams(16, 6, 16, 10))

    val title = field("Title", "e.g. Modern Kitchen Suite").apply { if (existing != null) setText(existing.title) }
    sectionHeader("Basics")
    content.addView(title, marginParams(16, 4, 16, 6))
    title.setOnFocusChangeListener { _, _ -> syncProductSummary(title, summaryTitle, summaryCategory) }

    var selectedCategory = existing?.category
    val categoryButtons = mutableListOf<android.widget.Button>()
    val categoryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), 0, dp(16), 0) }
    categories.forEach { cat ->
        val btn = button(cat, if (existing?.category == cat) blue else Color.rgb(238, 244, 236), if (existing?.category == cat) Color.WHITE else blue).apply {
            setOnClickListener {
                categoryButtons.forEach { it.setBackgroundColor(Color.rgb(238, 244, 236)); it.setTextColor(blue) }
                setBackgroundColor(blue)
                setTextColor(Color.WHITE)
                selectedCategory = cat
                syncProductSummary(title, summaryTitle, summaryCategory, cat)
            }
        }
        categoryButtons.add(btn)
        categoryRow.addView(btn, marginParams(0, 0, 8, 0))
    }
    content.addView(categoryRow, marginParams(0, 0, 0, 6))

    val tagline = field("Tagline", "e.g. Sleek lines. Enduring quality.").apply { if (existing != null) setText(existing.tagline) }
    content.addView(tagline, marginParams(16, 0, 16, 6))

    val description = field("Description", "Full description", true).apply { if (existing != null) setText(existing.description); minLines = 4 }
    content.addView(description, marginParams(16, 0, 16, 10))

    sectionHeader("Images")
    content.addView(tv("Paste URLs or pick from device. First is main; max 5.", 11, muted).apply { setPadding(dp(16), 0, dp(16), dp(6)) })

    val imageInputs = mutableListOf<android.widget.EditText>()
    val imageContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
    (existing?.gallery?.take(5) ?: listOf("")).forEach { url ->
        val imageInput = field("Image URL", if (imageInputs.isEmpty()) "Main image" else "Photo").apply { if (url.isNotBlank()) setText(url) }
        imageInputs.add(imageInput)
        imageContainer.addView(imageInput, marginParams(0, 0, 0, 6))
    }
    content.addView(imageContainer, marginParams(0, 0, 0, 6))

    content.addView(button("+ Add image (max 5)", blue, Color.WHITE).apply {
        setOnClickListener {
            if (imageInputs.size < 5) {
                val imageInput = field("Image URL", "Photo")
                imageInputs.add(imageInput)
                imageContainer.addView(imageInput, marginParams(0, 0, 0, 6))
            } else toast("Maximum 5 images reached")
        }
    }, marginParams(16, 0, 16, 10))

    sectionHeader("Details")
    val leadTime = field("Lead Time", "e.g. 2–4 weeks").apply { if (existing != null) setText(existing.lead) }
    content.addView(leadTime, marginParams(16, 4, 16, 6))

    val tagButtonsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(16), 0, dp(16), 0); gravity = Gravity.CENTER_VERTICAL }
    val tagButtons = mutableListOf<android.widget.Button>()
    var selectedTagValue: String? = existing?.tag
    val noneBtn = button("None", if (selectedTagValue.isNullOrBlank()) blue else Color.rgb(238, 244, 236), if (selectedTagValue.isNullOrBlank()) Color.WHITE else blue).apply {
        setOnClickListener {
            tagButtons.forEach { it.setBackgroundColor(Color.rgb(238, 244, 236)); it.setTextColor(blue) }
            setBackgroundColor(blue)
            setTextColor(Color.WHITE)
            selectedTagValue = null
        }
    }
    tagButtons.add(noneBtn)
    tagButtonsRow.addView(noneBtn, marginParams(0, 0, 8, 0))

    presetTags.forEach { tag ->
        val btn = button(tag, if (selectedTagValue == tag) blue else Color.rgb(238, 244, 236), if (selectedTagValue == tag) Color.WHITE else blue).apply {
            setOnClickListener {
                tagButtons.forEach { it.setBackgroundColor(Color.rgb(238, 244, 236)); it.setTextColor(blue) }
                setBackgroundColor(blue)
                setTextColor(Color.WHITE)
                selectedTagValue = tag
            }
        }
        tagButtons.add(btn)
        tagButtonsRow.addView(btn, marginParams(0, 0, 8, 0))
    }
    content.addView(tagButtonsRow, marginParams(0, 0, 0, 6))

    val priceRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    var isFromPrice = existing?.let { it.price.startsWith("From ") } ?: false
    val fromBtn = button("From", if (isFromPrice) blue else Color.rgb(238, 244, 236), if (isFromPrice) Color.WHITE else blue).apply {
        setOnClickListener {
            isFromPrice = !isFromPrice
            setBackgroundColor(if (isFromPrice) blue else Color.rgb(238, 244, 236))
            setTextColor(if (isFromPrice) Color.WHITE else blue)
        }
    }
    priceRow.addView(fromBtn, marginParams(16, 0, 8, 0))
    val price = field("Price", "e.g. R8,500").apply { if (existing != null) setText(existing.price.removePrefix("From ")) }
    priceRow.addView(price, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(0); marginEnd = dp(16) })
    content.addView(priceRow, marginParams(0, 0, 0, 10))

    sectionHeader("Features")
    content.addView(tv("Short bullet points shown on the product page.", 11, muted).apply { setPadding(dp(16), 0, dp(16), dp(6)) })
    val featuresInput = field("Features", "One per line", true).apply {
        if (existing != null) setText(existing.features.joinToString("\n"))
        minLines = 3
    }
    content.addView(featuresInput, marginParams(16, 0, 16, 10))

    sectionHeader("Finishes")
    content.addView(tv("Colour or material options customers can choose from.", 11, muted).apply { setPadding(dp(16), 0, dp(16), dp(6)) })
    val finishesInput = field("Finishes", "One per line", true).apply {
        if (existing != null) setText(existing.finishes.joinToString("\n"))
        minLines = 3
    }
    content.addView(finishesInput, marginParams(16, 0, 16, 24))

    val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    buttonRow.addView(button(if (isEditing) "Save Changes" else "Create Product", blue, Color.WHITE).apply {
        setOnClickListener {
            val titleText = title.text.toString().trim()
            if (titleText.isEmpty() || selectedCategory == null) { toast("Title and category are required"); return@setOnClickListener }

            val galleryUrls = imageInputs.mapNotNull { it.text.toString().trim().takeIf { it.isNotEmpty() } }.take(5)
            if (galleryUrls.isEmpty()) { toast("At least one image is required"); return@setOnClickListener }

            val priceText = price.text.toString().trim()
            val finalPrice = if (isFromPrice && priceText.isNotEmpty()) "From $priceText" else priceText
            if (finalPrice.isEmpty()) { toast("Price is required"); return@setOnClickListener }

            val p = Product(
                if (isEditing) existing!!.id else 0,
                selectedCategory!!,
                titleText,
                tagline.text.toString().trim(),
                description.text.toString().trim(),
                galleryUrls.first(),
                galleryUrls,
                featuresInput.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                finishesInput.text.toString().split("\n").map { it.trim() }.filter { it.isNotEmpty() },
                leadTime.text.toString().trim(),
                selectedTagValue,
                finalPrice
            )
            db.saveProduct(p, !isEditing)
            toast("Product ${if (isEditing) "updated" else "created"}")
            editProductId = 0
            showScreen("manageProducts")
        }
    }, marginParams(16, 0, 8, 24))

    buttonRow.addView(button("Cancel", Color.rgb(238, 244, 236), blue).apply { setOnClickListener { editProductId = 0; showScreen("manageProducts") } }, marginParams(0, 0, 16, 24))
    content.addView(buttonRow)
}

private fun syncProductSummary(titleField: android.widget.EditText, summaryTitle: android.widget.TextView, summaryCategory: android.widget.TextView, category: String? = null) {
    val title = titleField.text.toString().trim().takeIf { it.isNotEmpty() } ?: "Untitled product"
    summaryTitle.text = title
    if (category != null) summaryCategory.text = category
}

private fun MainActivity.sectionHeader(title: String) {
    content.addView(tv(title.uppercase(), 11, muted).apply { setTypeface(typeface, Typeface.BOLD); setPadding(dp(16), dp(10), dp(16), dp(4)) })
}

internal fun MainActivity.manageTestimonialsScreen() {
    requireAdmin() ?: return
    pageIntro("Manage Testimonials", "Add, edit or remove customer testimonials.")
    content.addView(button("+ Add Testimonial", red, Color.WHITE).apply { setOnClickListener { editTestimonialId = null; showScreen("testimonialForm") } }, marginParams(16, 10, 16, 10))
    db.loadTestimonials().forEach { t ->
        val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        c.addView(tv(t.name, 14, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv("${t.role} · ${t.location} · ${t.rating}★", 11, muted))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row.addView(button("Edit", blue, Color.WHITE).apply { setOnClickListener { editTestimonialId = t.id; showScreen("testimonialForm") } }, marginParams(0, 0, 8, 0))
        row.addView(button("Delete", red, Color.WHITE).apply { setOnClickListener { db.deleteTestimonial(t.id); toast("Testimonial deleted"); showScreen("manageTestimonials") } })
        c.addView(row)
        content.addView(c, marginParams(16, 5, 16, 5))
    }
}

internal fun MainActivity.testimonialFormScreen() {
    requireAdmin() ?: return
    val existing = editTestimonialId?.let { id -> db.loadTestimonials().firstOrNull { it.id == id } }
    pageIntro(if (existing == null) "Add Testimonial" else "Edit Testimonial", "")
    val name = field("Name", "Customer name").apply { if (existing != null) setText(existing.name) }
    val role = field("Role", "e.g. Homeowner").apply { if (existing != null) setText(existing.role) }
    val location = field("Location", "e.g. Soweto").apply { if (existing != null) setText(existing.location) }
    val rating = spinnerField("Rating", listOf("1", "2", "3", "4", "5"), (existing?.rating ?: 5).toString())
    val review = field("Review", "What did they say?", true).apply { if (existing != null) setText(existing.review) }
    val project = field("Project", "e.g. Modern Kitchen Suite").apply { if (existing != null) setText(existing.project) }
    listOf(name, role, location, rating, review, project).forEach { content.addView(it, marginParams(16, 6, 16, 6)) }
    content.addView(button(if (existing == null) "Create Testimonial" else "Save Changes", red, Color.WHITE).apply {
        setOnClickListener {
            if (name.text.isNullOrBlank() || review.text.isNullOrBlank()) { toast("Name and review are required"); return@setOnClickListener }
            val t = Testimonial(existing?.id ?: 0, name.text.toString(), role.text.toString(), location.text.toString(), spinnerValue(rating).toIntOrNull() ?: 5, review.text.toString(), project.text.toString())
            db.saveTestimonial(t, existing == null)
            toast("Saved"); editTestimonialId = null; showScreen("manageTestimonials")
        }
    }, marginParams(16, 10, 16, 24))
}

internal fun MainActivity.manageFaqsScreen() {
    requireAdmin() ?: return
    pageIntro("Manage FAQs", "Add, edit or remove frequently asked questions.")
    content.addView(button("+ Add FAQ", red, Color.WHITE).apply { setOnClickListener { editFaqId = null; showScreen("faqForm") } }, marginParams(16, 10, 16, 10))
    db.loadFaqs().forEach { f ->
        val c = card().apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)) }
        c.addView(tv(f.question, 14, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv(f.category, 11, muted))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row.addView(button("Edit", blue, Color.WHITE).apply { setOnClickListener { editFaqId = f.id; showScreen("faqForm") } }, marginParams(0, 0, 8, 0))
        row.addView(button("Delete", red, Color.WHITE).apply { setOnClickListener { db.deleteFaq(f.id); toast("FAQ deleted"); showScreen("manageFaqs") } })
        c.addView(row)
        content.addView(c, marginParams(16, 5, 16, 5))
    }
}

internal fun MainActivity.faqFormScreen() {
    requireAdmin() ?: return
    val existing = editFaqId?.let { id -> db.loadFaqs().firstOrNull { it.id == id } }
    pageIntro(if (existing == null) "Add FAQ" else "Edit FAQ", "")
    val category = spinnerField("Category", listOf("Materials", "Process", "Services", "Installation", "Trade", "Branches", "Warranty"), existing?.category)
    val question = field("Question", "Question text", true).apply { if (existing != null) setText(existing.question) }
    val answer = field("Answer", "Answer text", true).apply { if (existing != null) setText(existing.answer) }
    listOf(category, question, answer).forEach { content.addView(it, marginParams(16, 6, 16, 6)) }
    content.addView(button(if (existing == null) "Create FAQ" else "Save Changes", red, Color.WHITE).apply {
        setOnClickListener {
            if (question.text.isNullOrBlank() || answer.text.isNullOrBlank()) { toast("Question and answer are required"); return@setOnClickListener }
            val f = Faq(existing?.id ?: 0, spinnerValue(category), question.text.toString(), answer.text.toString())
            db.saveFaq(f, existing == null)
            toast("Saved"); editFaqId = null; showScreen("manageFaqs")
        }
    }, marginParams(16, 10, 16, 24))
}