package com.woodlands.mobile

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.View
import android.widget.LinearLayout

/** Help hub: tutorial, FAQs and branch-manager contact details. */
internal fun MainActivity.helpScreen() {
    pageIntro("Help", "Find guidance and the right person to contact.")

    val completedAt = tutorialCompletedAt()
    val tutorialSummary = if (completedAt > 0L) {
        "Restart the interactive app tour. Completed ${formatTutorialDate(completedAt)}."
    } else {
        "Take the interactive tour of the app."
    }

    val items = listOf(
        Triple("Tutorial", tutorialSummary, "tutorial"),
        Triple("FAQs", "Tips and answers about products, services and branches.", "faqs"),
        Triple("Contact Manager", "View branch manager names, account emails, phone numbers and branch locations.", "contactManager")
    )

    items.forEach { (heading, subtitle, destination) ->
        val item = card().apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            setOnClickListener { showScreen(destination) }
        }
        item.addView(tv(heading, 17, blue).apply { setTypeface(typeface, Typeface.BOLD) })
        item.addView(tv(subtitle, 12, muted).apply { setPadding(0, dp(4), 0, 0) })
        content.addView(item, marginParams(16, 6, 16, 6))
    }
}

/** Lists active manager accounts from the app's locally cached users table. Public manager directory grouped per branch; admins are listed only for managers and admins. */
internal fun MainActivity.contactManagerScreen() {
    pageIntro("Contact Manager", "Contact your branch manager.")

    val showAdmins = currentUser()?.let { Roles.isStaff(it.role) } == true
    val contacts = db.loadContacts().filter { it.active }
    val managers = contacts.filter { Roles.isManager(it.role) }
    val admins = if (showAdmins) contacts.filter { it.role == Roles.ADMIN } else emptyList()

    if (managers.isEmpty() && admins.isEmpty()) {
        content.addView(tv(
            "No contact details are currently available on this device. Connect to the internet and sync the app, then try again.",
            13, muted
        ).apply { setPadding(dp(16), dp(12), dp(16), dp(12)) })
        content.addView(outlineButton("Refresh contacts", blue).apply {
            setOnClickListener {
                SyncManager.syncAll()
                toast("Sync requested. Reopen Contact Manager after syncing.")
            }
        }, marginParams(16, 6, 16, 12))
        return
    }

    val branches = db.loadBranches()
    managers
        .groupBy { (it.branch?.takeIf { b -> b.isNotBlank() } ?: Roles.branchFor(it.role)).orEmpty() }
        .toSortedMap(String.CASE_INSENSITIVE_ORDER)
        .forEach { (branchName, people) ->
            val b = branches.firstOrNull { it.name.equals(branchName, ignoreCase = true) }
            val place = b?.address?.takeIf { it.isNotBlank() } ?: b?.region?.takeIf { it.isNotBlank() }
            contactGroup(branchName.ifBlank { "Other" } + " Branch", place, people)
        }

    if (admins.isNotEmpty()) {
        contactGroup("Admin", "System administrators (staff only)", admins)
    }

    content.addView(tv(
        "Contact details are synced to this device from active staff accounts.",
        11, muted
    ).apply { setPadding(dp(16), dp(10), dp(16), dp(20)) })
}

private fun MainActivity.contactGroup(title: String, subtitle: String?, people: List<AppUser>) {
    val group = card().apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(14), dp(16), dp(14))
    }
    group.addView(tv(title, 18, blue).apply { setTypeface(typeface, Typeface.BOLD) })
    subtitle?.let {
        group.addView(tv(it, 12, muted).apply { setPadding(0, dp(2), 0, dp(6)) })
    }

    people.forEachIndexed { i, p ->
        if (i > 0) {
            group.addView(View(this).apply { setBackgroundColor(Color.parseColor("#E5E7EB")) },
                LinearLayout.LayoutParams(-1, dp(1)).apply { setMargins(0, dp(10), 0, dp(10)) })
        } else {
            group.addView(View(this), LinearLayout.LayoutParams(-1, dp(6)))
        }
        group.addView(tv(p.fullName, 16, text).apply { setTypeface(typeface, Typeface.BOLD) })
        group.addView(tv(Roles.label(p.role), 12, red).apply { setPadding(0, dp(2), 0, dp(6)) })
        group.addView(tv("Email: ${p.email}", 13, text))
        p.phone?.takeIf { it.isNotBlank() }?.let {
            group.addView(tv("Phone: $it", 13, text).apply { setPadding(0, dp(4), 0, 0) })
        }
        group.addView(outlineButton("Email ${p.fullName}", blue).apply {
            setOnClickListener {
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${Uri.encode(p.email)}"))
                try {
                    startActivity(intent)
                } catch (e: android.content.ActivityNotFoundException) {
                    toast("No email app is available on this device")
                }
            }
        }, marginParams(0, 10, 0, 0))
    }

    content.addView(group, marginParams(16, 6, 16, 6))
}
