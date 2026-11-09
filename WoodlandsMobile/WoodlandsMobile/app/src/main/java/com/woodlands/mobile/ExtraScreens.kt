package com.woodlands.mobile

/*
 * Mobile versions of the screens/cards that the web app + Node API already support:
 *
 *  connectionBanner / syncIssuesScreen        local outbox state
 *  notificationsScreen                        GET /api/notifications, PATCH .../read, PATCH .../read-all, DELETE .../:id
 *  emailChangeScreen                          POST /api/email-change-requests (admin)
 *                                             GET  /api/email-change-requests/me
 *                                             POST /api/email-change-requests/:id/decision
 *  servicesScreen / manageServicesScreen /    GET/POST/PUT/DELETE /api/services
 *  serviceFormScreen
 *  productDeletionRequestsScreen              GET /api/product-deletion-requests
 *                                             POST /api/product-deletion-requests/:id/approve|reject
 *  requestProductDeletion (managers)          POST /api/products/:id/deletion-requests
 *  addDeletionCard                            POST /api/deletion-requests, GET same, PUT .../:id/cancel
 *  addRecoveryCard / addRestoreCard           GET /api/account-archive/available,
 *                                             POST /api/account-archive/request-code, POST .../restore
 */

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

//  helpers 

/** Run a network call off the UI thread, then hand the result back on the UI thread. */
private fun MainActivity.background(work: () -> ApiResult, done: (ApiResult) -> Unit) {
    Thread {
        val result = work()
        runOnUiThread { if (!isFinishing && !isDestroyed) done(result) }
    }.start()
}

private fun ApiResult.failText(): String? = when (this) {
    is ApiResult.Success -> null
    is ApiResult.Failure -> message
    is ApiResult.Offline -> "You're offline. Connect to the internet and try again."
}

private fun MainActivity.note(s: String, color: Int = muted): TextView =
    tv(s, 13, color).apply { setPadding(dp(16), dp(10), dp(16), dp(10)) }

private fun MainActivity.confirm(title: String, message: String, positive: String, onYes: () -> Unit) {
    AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(positive) { _, _ -> onYes() }
        .show()
}

private fun shortDate(iso: String?): String {
    val ms = parseMillis(iso)
    return if (ms <= 0) "" else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
}

private fun MainActivity.signedInOrLogin(): AppUser? {
    val me = currentUser()
    if (me == null) showScreen("login")
    return me
}

private fun MainActivity.adminOrHome(): AppUser? {
    val me = currentUser()
    if (me == null || me.role != Roles.ADMIN) { showScreen("home"); return null }
    return me
}

private fun MainActivity.parseArray(body: String): JSONArray =
    try { JSONArray(body) } catch (e: Exception) { JSONArray() }

private fun MainActivity.cardBox(): LinearLayout = card().apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(14), dp(12), dp(14), dp(12))
}

//  sync status 

/** Strip shown under the header when changes are waiting to sync or failed to sync. */
internal fun MainActivity.connectionBanner(): View? {
    val failed = db.failedOpCount()
    val pending = db.pendingOpCount()
    if (failed == 0 && pending == 0) return null

    val message = if (failed > 0) "$failed change(s) could not be synced. Tap to review."
    else "$pending change(s) waiting to sync."

    return tv(message, 12, Color.WHITE).apply {
        gravity = Gravity.CENTER
        setPadding(dp(16), dp(8), dp(16), dp(8))
        setBackgroundColor(if (failed > 0) red else blue)
        isClickable = true
        setOnClickListener { showScreen("syncIssues") }
    }
}

internal fun MainActivity.syncIssuesScreen() {
    pageIntro("Sync issues", "Changes that could not be sent to the Woodlands server.")
    para("${db.pendingOpCount()} change(s) waiting to sync.")

    val failed = db.failedOps()
    if (failed.isEmpty()) {
        para("No failed changes.")
        return
    }

    failed.forEach { op ->
        val c = cardBox()
        val title = "${op.method} ${op.path}".trim().ifBlank { op.kind }
        c.addView(tv(title, 13, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv(op.lastError ?: "Unknown error", 12, red).apply { setPadding(0, dp(4), 0, dp(8)) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(
            button("Retry", blue, Color.WHITE).apply {
                setOnClickListener { db.retryOp(op.id); toast("Retrying…"); showScreen("syncIssues") }
            },
            marginParams(0, 0, 8, 0).apply { width = LinearLayout.LayoutParams.WRAP_CONTENT }
        )
        row.addView(outlineButton("Discard", red).apply {
            setOnClickListener { db.removeOp(op.id); showScreen("syncIssues") }
        })
        c.addView(row)
        content.addView(c, marginParams(16, 5, 16, 5))
    }
}

//  notifications 

internal fun MainActivity.notificationsScreen() {
    val me = signedInOrLogin() ?: return
    pageIntro("Notifications", "Updates about your account, quotes and requests.")

    val items = db.loadNotifications()
    if (items.isEmpty()) {
        para("You have no notifications.")
        return
    }

    if (items.any { !it.read }) {
        content.addView(
            outlineButton("Mark all as read", blue).apply {
                setOnClickListener { db.markAllNotificationsRead(); showScreen("notifications") }
            },
            marginParams(16, 10, 16, 4)
        )
    }

    val fmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
    items.forEach { n ->
        val c = cardBox()
        c.addView(tv((if (n.read) "" else "● ") + n.title, 14, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        if (n.message.isNotBlank()) c.addView(tv(n.message, 13, text).apply { setPadding(0, dp(4), 0, 0) })
        if (n.createdAt > 0) c.addView(tv(fmt.format(Date(n.createdAt)), 11, muted).apply { setPadding(0, dp(6), 0, 0) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val target = when (n.kind) {
            "product_deletion_request" -> if (me.role == Roles.ADMIN) "productDeletionRequests" else null
            "email_change_requested" -> "emailChange"
            else -> null
        }
        if (target != null) {
            row.addView(
                button("Review", blue, Color.WHITE).apply {
                    setOnClickListener { db.markNotificationRead(n); showScreen(target) }
                },
                marginParams(0, 0, 8, 0).apply { width = LinearLayout.LayoutParams.WRAP_CONTENT }
            )
        }
        if (!n.read) {
            row.addView(
                outlineButton("Mark read", blue).apply {
                    setOnClickListener { db.markNotificationRead(n); showScreen("notifications") }
                },
                marginParams(0, 0, 8, 0).apply { width = LinearLayout.LayoutParams.WRAP_CONTENT }
            )
        }
        row.addView(outlineButton("Dismiss", red).apply {
            setOnClickListener { db.dismissNotification(n); showScreen("notifications") }
        })
        c.addView(row)
        content.addView(c, marginParams(16, 5, 16, 5))
    }
}

//  email change 

internal fun MainActivity.emailChangeScreen() {
    val me = signedInOrLogin() ?: return
    pageIntro(
        "Email change request",
        "An administrator can request a new email address for your account. You must approve it here."
    )

    // ---- the signed-in user's own pending request ----
    sectionTitle("Your pending request", "")
    val mine = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(mine)
    mine.addView(note("Checking…"))

    background({ ApiClient.get("/api/email-change-requests/me") }) { r ->
        if (currentScreen != "emailChange") return@background
        mine.removeAllViews()
        val err = r.failText()
        if (err != null) { mine.addView(note(err, red)); return@background }

        val body = (r as ApiResult.Success).body.trim()
        val req = if (body.isBlank() || body == "null") null else try { JSONObject(body) } catch (e: Exception) { null }
        if (req == null) {
            mine.addView(note("You have no pending email change request."))
            return@background
        }

        val id = req.optString("id")
        val c = cardBox()
        c.addView(tv("Change email address", 15, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv("From: ${req.optString("current_email")}", 13, text).apply { setPadding(0, dp(6), 0, 0) })
        c.addView(tv("To: ${req.optString("requested_email")}", 13, text))
        c.addView(tv("Requested ${shortDate(req.str("created_at"))}", 11, muted).apply { setPadding(0, dp(4), 0, 0) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        row.addView(
            button("Accept", blue, Color.WHITE).apply {
                setOnClickListener {
                    confirm(
                        "Change your email?",
                        "Your account email will become ${req.optString("requested_email")}. Use it the next time you sign in.",
                        "Accept"
                    ) { decideEmailChange(id, "accept") }
                }
            },
            marginParams(0, 0, 8, 0).apply { width = LinearLayout.LayoutParams.WRAP_CONTENT }
        )
        row.addView(outlineButton("Reject", red).apply { setOnClickListener { decideEmailChange(id, "reject") } })
        c.addView(row)
        mine.addView(c, marginParams(16, 5, 16, 5))
    }

    // ---- admin: start a request for someone else ----
    if (me.role != Roles.ADMIN) return

    sectionTitle("Request a change for a user", "The user's email only changes after they approve it.")
    val users = db.loadUsers().sortedBy { it.fullName.lowercase() }
    if (users.isEmpty()) {
        content.addView(note("No users loaded yet. Pull down to refresh."))
        return
    }
    val labels = users.map { "${it.fullName} (${it.email})" }
    val picker = spinnerField("User", labels)
    val newEmail = field("New email address", "new.address@example.com").apply {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
    }
    content.addView(picker, marginParams(16, 6, 16, 6))
    content.addView(newEmail, marginParams(16, 6, 16, 6))
    content.addView(button("Send request", red, Color.WHITE).apply {
        setOnClickListener {
            val email = newEmail.text?.toString()?.trim().orEmpty().lowercase()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                toast("Enter a valid email address"); return@setOnClickListener
            }
            val user = users.getOrNull(labels.indexOf(spinnerValue(picker))) ?: run { toast("Pick a user"); return@setOnClickListener }
            val body = JSONObject().put("user_id", user.id).put("requested_email", email).toString()
            toast("Sending…")
            background({ ApiClient.post("/api/email-change-requests", body) }) { r ->
                val err = r.failText()
                if (err != null) toast(err)
                else { toast("Request sent. ${user.fullName} must approve it."); showScreen("emailChange") }
            }
        }
    }, marginParams(16, 8, 16, 20))
}

private fun MainActivity.decideEmailChange(id: String, decision: String) {
    toast("Working…")
    val body = JSONObject().put("decision", decision).toString()
    background({ ApiClient.post("/api/email-change-requests/$id/decision", body) }) { r ->
        val err = r.failText()
        if (err != null) { toast(err); return@background }
        toast(if (decision == "accept") "Email address changed" else "Request rejected")
        SyncManager.syncAll()
        showScreen("emailChange")
    }
}

//  services 

internal fun MainActivity.servicesScreen() {
    val me = currentUser()
    pageIntro("Our Services", "What Woodlands can build and fit for you.")

    if (me?.role == Roles.ADMIN) {
        content.addView(
            button("Manage services", blue, Color.WHITE).apply { setOnClickListener { showScreen("manageServices") } },
            marginParams(16, 10, 16, 4)
        )
    }

    val items = db.loadServices().filter { it.active }
    if (items.isEmpty()) {
        para("No services are available right now.")
        return
    }
    items.forEach { s ->
        val c = cardBox()
        c.addView(tv(s.name, 15, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        if (s.description.isNotBlank()) c.addView(tv(s.description, 13, muted).apply { setPadding(0, dp(4), 0, 0) })
        content.addView(c, marginParams(16, 5, 16, 5))
    }
}

internal fun MainActivity.manageServicesScreen() {
    adminOrHome() ?: return
    pageIntro("Manage Services", "Add, edit or remove the services shown to customers.")
    content.addView(
        button("+ Add Service", red, Color.WHITE).apply { setOnClickListener { editServiceId = null; showScreen("serviceForm") } },
        marginParams(16, 10, 16, 10)
    )

    val items = db.loadServices()
    if (items.isEmpty()) para("No services yet.")
    items.forEach { s ->
        val c = cardBox()
        c.addView(tv(s.name, 14, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv(if (s.active) "Active" else "Hidden", 11, if (s.active) green else muted))
        if (s.description.isNotBlank()) c.addView(tv(s.description, 12, muted).apply { setPadding(0, dp(4), 0, 0) })

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        row.addView(button("Edit", blue, Color.WHITE).apply {
            setOnClickListener { editServiceId = s.id.toInt(); showScreen("serviceForm") }
        }, marginParams(0, 0, 8, 0))
        row.addView(button("Delete", red, Color.WHITE).apply {
            setOnClickListener {
                confirm("Delete service?", "“${s.name}” will be removed for everyone.", "Delete") {
                    db.deleteService(s.id); toast("Service deleted"); showScreen("manageServices")
                }
            }
        })
        c.addView(row)
        content.addView(c, marginParams(16, 5, 16, 5))
    }
}

internal fun MainActivity.serviceFormScreen() {
    adminOrHome() ?: return
    val existing = editServiceId?.let { id -> db.loadServices().firstOrNull { it.id == id.toLong() } }
    pageIntro(if (existing == null) "Add Service" else "Edit Service", "")

    val name = field("Name", "e.g. CNC board cutting").apply { if (existing != null) setText(existing.name) }
    val description = field("Description", "What the service includes", true, required = false)
        .apply { if (existing != null) setText(existing.description) }
    val active = CheckBox(this).apply {
        text = "Visible to customers"
        isChecked = existing?.active ?: true
        setTextColor(this@serviceFormScreen.text)
    }
    content.addView(name, marginParams(16, 6, 16, 6))
    content.addView(description, marginParams(16, 6, 16, 6))
    content.addView(active, marginParams(16, 6, 16, 6))

    content.addView(button(if (existing == null) "Create Service" else "Save Changes", red, Color.WHITE).apply {
        setOnClickListener {
            val n = name.text?.toString()?.trim().orEmpty()
            val d = description.text?.toString()?.trim().orEmpty()
            if (n.isBlank()) { toast("Name is required"); return@setOnClickListener }
            if (n.length > 120) { toast("Name must be 120 characters or fewer"); return@setOnClickListener }
            if (d.length > 1000) { toast("Description must be 1000 characters or fewer"); return@setOnClickListener }
            db.saveService(existing?.id ?: 0L, n, d, existing?.image.orEmpty(), active.isChecked, existing == null)
            toast("Saved"); editServiceId = null; showScreen("manageServices")
        }
    }, marginParams(16, 10, 16, 8))
    content.addView(outlineButton("Cancel", blue).apply {
        setOnClickListener { editServiceId = null; showScreen("manageServices") }
    }, marginParams(16, 0, 16, 20))
}

//  product deletion requests 

internal fun MainActivity.productDeletionRequestsScreen() {
    val me = currentUser()
    if (me == null || !Roles.isStaff(me.role)) { showScreen("home"); return }
    val isAdmin = me.role == Roles.ADMIN
    pageIntro(
        "Product deletions",
        if (isAdmin) "Approve or reject branch managers' requests to remove products."
        else "Products you have asked an administrator to remove."
    )

    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(list)
    list.addView(note("Loading requests…"))

    background({ ApiClient.get("/api/product-deletion-requests") }) { r ->
        if (currentScreen != "productDeletionRequests") return@background
        list.removeAllViews()
        val err = r.failText()
        if (err != null) { list.addView(note(err, red)); return@background }

        val arr = parseArray((r as ApiResult.Success).body)
        if (arr.length() == 0) { list.addView(note("No deletion requests.")); return@background }

        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val status = o.optString("status")
            val title = o.str("product_title") ?: "Product ${o.optString("product_id")}"

            val c = cardBox()
            c.addView(tv(title, 15, blue).apply { setTypeface(typeface, Typeface.BOLD) })
            val who = o.str("requested_by_name")?.takeIf { it.isNotBlank() } ?: "Unknown"
            c.addView(tv("Requested by $who · ${shortDate(o.str("created_at"))}", 11, muted))
            c.addView(tv(status, 11, if (status == "Pending") red else muted).apply { setPadding(0, dp(2), 0, 0) })
            o.str("reason")?.takeIf { it.isNotBlank() }?.let {
                c.addView(tv("Reason: $it", 13, text).apply { setPadding(0, dp(8), 0, 0) })
            }
            o.str("admin_note")?.takeIf { it.isNotBlank() }?.let {
                c.addView(tv("Admin note: $it", 12, muted).apply { setPadding(0, dp(4), 0, 0) })
            }

            if (isAdmin && status == "Pending") {
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
                row.addView(
                    button("Approve & delete", red, Color.WHITE).apply {
                        setOnClickListener {
                            confirm("Delete this product?", "“$title” will be permanently removed from the catalogue.", "Delete product") {
                                decideProductDeletion(id, true, null)
                            }
                        }
                    },
                    marginParams(0, 0, 8, 0).apply { width = LinearLayout.LayoutParams.WRAP_CONTENT }
                )
                row.addView(outlineButton("Reject", blue).apply {
                    setOnClickListener { askRejectNote(title) { note -> decideProductDeletion(id, false, note) } }
                })
                c.addView(row)
            }
            list.addView(c, marginParams(16, 5, 16, 5))
        }
    }
}

private fun MainActivity.askRejectNote(title: String, onDone: (String) -> Unit) {
    val input = EditText(this).apply { hint = "Reason (optional)"; setPadding(dp(20), dp(12), dp(20), dp(12)) }
    AlertDialog.Builder(this)
        .setTitle("Reject deletion of “$title”?")
        .setView(input)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Reject") { _, _ -> onDone(input.text?.toString()?.trim().orEmpty()) }
        .show()
}

private fun MainActivity.decideProductDeletion(id: String, approve: Boolean, adminNote: String?) {
    toast("Working…")
    val body = JSONObject().apply { if (!adminNote.isNullOrBlank()) put("admin_note", adminNote) }.toString()
    val action = if (approve) "approve" else "reject"
    background({ ApiClient.post("/api/product-deletion-requests/$id/$action", body) }) { r ->
        val err = r.failText()
        if (err != null) toast(err)
        else {
            toast(if (approve) "Product deleted" else "Request rejected")
            if (approve) SyncManager.syncAll()
        }
        showScreen("productDeletionRequests")
    }
}

/** Managers can't delete shared products; they send a request to the admins instead. */
internal fun MainActivity.requestProductDeletion(productId: Int, title: String) {
    val remoteId = db.productRemoteId(productId)
    if (remoteId.isNullOrBlank()) { toast("This product hasn't synced yet. Try again shortly."); return }

    val input = EditText(this).apply { hint = "Why should it be removed? (optional)"; setPadding(dp(20), dp(12), dp(20), dp(12)) }
    AlertDialog.Builder(this)
        .setTitle("Request deletion of “$title”?")
        .setMessage("An administrator must approve this before the product is removed.")
        .setView(input)
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Send request") { _, _ ->
            val body = JSONObject().put("reason", input.text?.toString()?.trim().orEmpty()).toString()
            background({ ApiClient.post("/api/products/$remoteId/deletion-requests", body) }) { r ->
                val err = r.failText()
                if (err != null) toast(err)
                else { toast("Request sent to the administrators"); showScreen("productDeletionRequests") }
            }
        }
        .show()
}

//  account cards 

/** Profile screen: ask for the account to be deleted (admin approves), or cancel a pending request. */
internal fun MainActivity.addDeletionCard(me: AppUser) {
    if (me.role == Roles.ADMIN) return

    sectionTitle("Delete my account", "Sends a request to an administrator.")
    val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(holder, marginParams(16, 4, 16, 14))
    holder.addView(note("Checking for an existing request…"))

    background({ ApiClient.get("/api/deletion-requests") }) { r ->
        if (currentScreen != "profile") return@background
        holder.removeAllViews()
        val err = r.failText()
        if (err != null) { holder.addView(note(err, red)); return@background }

        val pending = parseArray((r as ApiResult.Success).body).let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.firstOrNull { it.optString("status") == "Pending" }
        }

        val c = cardBox()
        if (pending != null) {
            c.addView(tv("Deletion request pending", 14, blue).apply { setTypeface(typeface, Typeface.BOLD) })
            c.addView(tv("Sent ${shortDate(pending.str("created_at"))}. An administrator will review it.", 12, muted).apply { setPadding(0, dp(4), 0, dp(8)) })
            c.addView(outlineButton("Cancel request", red).apply {
                setOnClickListener {
                    background({ ApiClient.put("/api/deletion-requests/${pending.optString("id")}/cancel", "{}") }) { res ->
                        val e = res.failText()
                        toast(e ?: "Request cancelled"); showScreen("profile")
                    }
                }
            })
        } else {
            c.addView(tv("Your account will be removed once an administrator approves the request.", 12, muted))
            val keep = CheckBox(this).apply {
                text = "Keep my saved quote data so I can restore it later"
                setTextColor(this@addDeletionCard.text)
            }
            val message = field("Message", "Anything you'd like us to know", true, required = false)
            val password = passwordField("Your password")
            c.addView(keep, marginParams(0, 8, 0, 4))
            c.addView(message, marginParams(0, 4, 0, 4))
            c.addView(password, marginParams(0, 4, 0, 8))
            c.addView(button("Request account deletion", red, Color.WHITE).apply {
                setOnClickListener {
                    val pw = password.text?.toString().orEmpty()
                    if (pw.isBlank()) { toast("Enter your password to confirm"); return@setOnClickListener }
                    confirm("Request deletion?", "An administrator will review your request.", "Send request") {
                        toast("Sending…")
                        val login = JSONObject().put("email", me.email).put("password", pw).toString()
                        val request = JSONObject()
                            .put("keep_data", keep.isChecked)
                            .put("message", message.text?.toString()?.trim().orEmpty())
                            .toString()
                        background({
                            // Same check the website does: confirm the password first.
                            when (val check = ApiClient.post("/api/auth/login", login)) {
                                is ApiResult.Success -> ApiClient.post("/api/deletion-requests", request)
                                is ApiResult.Failure ->
                                    if (check.code == 401) ApiResult.Failure(401, "The password you entered is incorrect.") else check
                                is ApiResult.Offline -> check
                            }
                        }) { res ->
                            val e = res.failText()
                            if (e != null) toast(e) else { toast("Deletion request sent"); showScreen("profile") }
                        }
                    }
                }
            })
        }
        holder.addView(c)
    }
}

/** Settings screen: email a one-time code to recover data saved from a previously deleted account. */
internal fun MainActivity.addRecoveryCard(me: AppUser) {
    val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(holder, marginParams(16, 6, 16, 6))

    background({ ApiClient.get("/api/account-archive/available") }) { r ->
        if (currentScreen != "settings") return@background
        val available = (r as? ApiResult.Success)?.body
            ?.let { try { JSONObject(it).optBoolean("available") } catch (e: Exception) { false } } ?: false
        if (!available) return@background

        val c = cardBox()
        c.addView(tv("Saved data found", 15, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        c.addView(tv("We kept your quote data from a previous account. We'll email a six-digit code to ${me.email}.", 12, muted).apply { setPadding(0, dp(4), 0, dp(8)) })
        c.addView(button("Email me a recovery code", blue, Color.WHITE).apply {
            setOnClickListener {
                toast("Sending…")
                background({ ApiClient.post("/api/account-archive/request-code", "{}") }) { res ->
                    toast(res.failText() ?: "Code sent. Check your email.")
                }
            }
        })
        holder.addView(c)
    }
}

/** Settings screen: enter the emailed code to restore the saved quotes/phone number. */
internal fun MainActivity.addRestoreCard(me: AppUser) {
    val holder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    content.addView(holder, marginParams(16, 6, 16, 14))

    background({ ApiClient.get("/api/account-archive/available") }) { r ->
        if (currentScreen != "settings") return@background
        val available = (r as? ApiResult.Success)?.body
            ?.let { try { JSONObject(it).optBoolean("available") } catch (e: Exception) { false } } ?: false
        if (!available) return@background

        val c = cardBox()
        c.addView(tv("Restore saved data", 15, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        val code = field("Recovery code", "6-digit code").apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        c.addView(code, marginParams(0, 8, 0, 8))
        c.addView(button("Restore my data", blue, Color.WHITE).apply {
            setOnClickListener {
                val digits = code.text?.toString()?.trim().orEmpty()
                if (!Regex("^\\d{6}$").matches(digits)) { toast("Enter the six-digit code"); return@setOnClickListener }
                toast("Restoring…")
                background({ ApiClient.post("/api/account-archive/restore", JSONObject().put("code", digits).toString()) }) { res ->
                    val e = res.failText()
                    if (e != null) { toast(e); return@background }
                    val restored = try { JSONObject((res as ApiResult.Success).body).optJSONObject("restored") } catch (x: Exception) { null }
                    val quotes = restored?.optInt("quotes") ?: 0
                    toast(if (quotes > 0) "Restored $quotes quote(s)" else "Data restored")
                    SyncManager.syncAll()
                    showScreen("settings")
                }
            }
        })
        holder.addView(c)
    }
}
