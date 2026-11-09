package com.woodlands.mobile

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import org.json.JSONException
import org.json.JSONObject
import kotlin.concurrent.thread

private const val HANDOFF_CODE_LENGTH = 10
private const val WEB_CODE_LENGTH = 8

private fun cleanCode(raw: String?): String =
    raw.orEmpty().uppercase().filter { it.isLetterOrDigit() }

private fun MainActivity.codeField(label: String, hint: String, maxLength: Int): EditText =
    field(label, hint).apply {
        inputType = InputType.TYPE_CLASS_TEXT or
                InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or
                InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        filters = arrayOf(InputFilter.LengthFilter(maxLength))
        typeface = Typeface.MONOSPACE
        textSize = 18f
    }

internal fun MainActivity.redeemHandoffCode(code: String, onFinished: (Boolean) -> Unit = {}) {
    SyncManager.redeemHandoffCode(code) { result ->
        result
            .onSuccess {
                onFinished(true)
                signIn(it)
            }
            .onFailure {
                onFinished(false)
                toast(it.message ?: "Sign-in failed")
            }
    }
}

internal fun MainActivity.handleHandoffIntent(incoming: Intent?) {
    val launchIntent = incoming ?: return
    val data = launchIntent.data ?: return

    if (data.scheme != "woodlands" || data.host != "handoff") return

    val code = cleanCode(data.getQueryParameter("code"))

    launchIntent.data = null

    if (code.length != HANDOFF_CODE_LENGTH) {
        toast("That sign-in link is not valid.")
        return
    }

    val signedIn = currentUser()

    if (signedIn == null) {
        redeemHandoffCode(code)
        return
    }

    AlertDialog.Builder(this)
        .setTitle("Switch account?")
        .setMessage("You are signed in as ${signedIn.fullName}. Continue to sign in with the code from the website instead?")
        .setNegativeButton("Cancel", null)
        .setPositiveButton("Continue") { _, _ -> redeemHandoffCode(code) }
        .show()
}

internal fun MainActivity.codeSignInScreen() {
    pageIntro(
        "Sign in with a code",
        "Use the code shown on the Woodlands website after you sign in there."
    )

    val code = codeField("Code from the website", "XXXXX-XXXXX", 12)
    content.addView(code, marginParams(16, 8, 16, 8))

    val go = button("Sign in", blue, Color.WHITE)

    go.setOnClickListener {
        val cleaned = cleanCode(code.text?.toString())

        if (cleaned.length != HANDOFF_CODE_LENGTH) {
            toast("Enter the 10-character code from the website")
            return@setOnClickListener
        }

        go.isEnabled = false
        go.text = "Signing in…"

        redeemHandoffCode(cleaned) { success ->
            if (!success) {
                go.isEnabled = true
                go.text = "Sign in"
            }
        }
    }

    content.addView(go, marginParams(16, 14, 16, 8))

    content.addView(
        tv(
            "On the website, open Continue on Phone after signing in. You can scan the QR code with your phone's camera, or type the code here. Each code works once and expires after a minute.",
            12,
            muted
        ).apply { setPadding(dp(16), dp(8), dp(16), dp(8)) }
    )

    content.addView(
        outlineButton("Back to normal login", blue).apply {
            setOnClickListener { showScreen("login") }
        },
        marginParams(16, 12, 16, 24)
    )
}

internal fun MainActivity.approveWebScreen() {
    val me = currentUser()

    if (me == null) {
        showScreen("login")
        return
    }

    pageIntro(
        "Sign in on web",
        "Approve a sign-in that you started on the Woodlands website."
    )

    content.addView(
        tv(
            "Only approve a sign-in if you started it yourself on the website. If someone else asked you for this code, do not approve it.",
            12,
            red
        ).apply { setPadding(dp(16), dp(4), dp(16), dp(8)) }
    )

    val code = codeField("Code shown on the website", "XXXX-XXXX", 9)
    content.addView(code, marginParams(16, 8, 16, 8))

    val resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    val lookup = button("Continue", blue, Color.WHITE)

    lookup.setOnClickListener {
        val cleaned = cleanCode(code.text?.toString())

        if (cleaned.length != WEB_CODE_LENGTH) {
            toast("Enter the 8-character code shown on the website")
            return@setOnClickListener
        }

        val userId = session.userId
        val token = session.token

        lookup.isEnabled = false
        resultBox.removeAllViews()

        thread {
            val response = ApiClient.post(
                "/api/auth/handoff/inspect",
                JSONObject().put("user_code", cleaned).toString()
            )

            runOnUiThread {
                lookup.isEnabled = true

                if (currentScreen != "approveWeb") return@runOnUiThread
                if (session.userId != userId || session.token != token) return@runOnUiThread

                when (response) {
                    is ApiResult.Success -> showWebApproval(resultBox, cleaned, response.body)
                    is ApiResult.Failure -> toast(response.message)
                    is ApiResult.Offline -> toast("You're offline. Connect to the internet to approve a sign-in.")
                }
            }
        }
    }

    content.addView(lookup, marginParams(16, 14, 16, 8))
    content.addView(resultBox, marginParams(16, 8, 16, 8))
}

private fun MainActivity.showWebApproval(box: LinearLayout, userCode: String, body: String) {
    val info = try {
        JSONObject(body)
    } catch (e: JSONException) {
        toast("Unexpected response from the server.")
        return
    }

    val device = info.optString("device").ifBlank { "A web browser" }
    val network = info.optString("ip").ifBlank { "unknown" }
    val seconds = info.optInt("requested_seconds_ago", 0)

    box.removeAllViews()

    val c = card().apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }

    c.addView(tv("A sign-in is waiting", 15, blue).apply { setTypeface(typeface, Typeface.BOLD) })
    c.addView(tv("Browser: $device", 13, text).apply { setPadding(0, dp(8), 0, 0) })
    c.addView(tv("Network: $network", 13, text))
    c.addView(tv("Requested $seconds seconds ago", 12, muted))

    c.addView(
        tv(
            "Approving signs the website in as ${currentUser()?.fullName ?: "you"}. Only approve if you recognise this browser.",
            12,
            muted
        ).apply { setPadding(0, dp(10), 0, 0) }
    )

    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, dp(12), 0, 0)
    }

    row.addView(
        button("Approve", blue, Color.WHITE).apply {
            setOnClickListener { sendWebDecision(box, userCode, true) }
        },
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(0, 0, dp(8), 0) }
    )

    row.addView(
        outlineButton("Deny", red).apply {
            setOnClickListener { sendWebDecision(box, userCode, false) }
        },
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    )

    c.addView(row)
    box.addView(c)
}

private fun MainActivity.sendWebDecision(box: LinearLayout, userCode: String, approve: Boolean) {
    val userId = session.userId
    val token = session.token

    box.removeAllViews()
    box.addView(tv("Working…", 13, muted))

    thread {
        val response = ApiClient.post(
            "/api/auth/handoff/approve",
            JSONObject().put("user_code", userCode).put("approve", approve).toString()
        )

        runOnUiThread {
            if (currentScreen != "approveWeb") return@runOnUiThread
            if (session.userId != userId || session.token != token) return@runOnUiThread

            box.removeAllViews()

            when (response) {
                is ApiResult.Success -> {
                    box.addView(
                        tv(
                            if (approve) "Approved. The website is signing you in now." else "Denied. The website was not signed in.",
                            14,
                            if (approve) green else muted
                        ).apply { setTypeface(typeface, Typeface.BOLD) }
                    )
                }

                is ApiResult.Failure -> box.addView(tv(response.message, 13, red))

                is ApiResult.Offline -> box.addView(
                    tv("You're offline. Nothing was approved.", 13, red)
                )
            }
        }
    }
}
