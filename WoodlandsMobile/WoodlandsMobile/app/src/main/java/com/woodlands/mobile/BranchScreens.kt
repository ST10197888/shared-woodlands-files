package com.woodlands.mobile

import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView

private fun branchDefaultKey(name: String): String = when (name) {
    "Soweto" -> "kitchen_12"
    "Roodepoort" -> "kitchen_6"
    "Randfontein" -> "kitchen_3"
    else -> "tv_9"
}

private fun MainActivity.showBranchImage(view: ImageView, b: Branch) {
    val value = b.image
    if (value.startsWith("local:") || value.startsWith("http")) {
        ImageLoader.load(this, view, value, imageRes(branchDefaultKey(b.name)))
    } else {
        view.tag = null
        view.setImageResource(imageRes(if (value.isNotBlank()) value else branchDefaultKey(b.name)))
    }
}

private fun MainActivity.branchBadge(label: String): View =
    tv(label, 9, blue).apply {
        setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.08f
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = bg(tan, Color.TRANSPARENT, 6)
    }

private fun MainActivity.branchPhoto(b: Branch, mine: Boolean, height: Int): View {
    val box = FrameLayout(this)
    val photo = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    showBranchImage(photo, b)
    box.addView(photo, FrameLayout.LayoutParams(-1, -1))
    if (mine) box.addView(branchBadge("YOUR BRANCH"), FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply { setMargins(dp(10), dp(10), 0, 0) })
    box.layoutParams = LinearLayout.LayoutParams(-1, dp(height))
    return box
}

private fun MainActivity.branchCard(b: Branch, mine: Boolean): View {
    val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    box.background = bg(Color.WHITE, if (mine) tan else lineColor, 16)
    box.clipToOutline = true
    box.outlineProvider = ViewOutlineProvider.BACKGROUND
    box.elevation = dp(2).toFloat()
    box.addView(branchPhoto(b, mine, 150))
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(14)) }
    body.addView(tv("${b.name} Branch", 18, blue).apply { setTypeface(Typeface.SERIF, Typeface.BOLD) })
    body.addView(tv("${b.region} · ${b.hours}", 12, muted).apply { setPadding(0, dp(3), 0, 0) })
    body.addView(tv("View details →", 12, red).apply { setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(8), 0, 0) })
    box.addView(body)
    box.setOnClickListener { showBranchDialog(b, mine) }
    return box
}

internal fun MainActivity.branchListScreen() {
    val me = currentUser()
    val admin = me?.role == Roles.ADMIN
    val mine = me?.let { Roles.branchFor(it.role) }
    pageIntro("Our Branches", "Find the branch closest to your project.")
    if (mine != null) {
        content.addView(tv("You manage the $mine branch.", 13, blue).apply {
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = bg(greenBg, Color.rgb(205, 218, 203), 12)
        }, marginParams(16, 14, 16, 4))
    } else if (admin) {
        content.addView(tv("Tap a branch to see its details. Admins can edit from the details window.", 12, muted).apply { setPadding(dp(16), dp(14), dp(16), dp(2)) })
    }
    val branches = db.loadBranches()
    if (branches.isEmpty()) {
        content.addView(tv("No branches loaded yet. Connect to the internet and open this page again.", 13, muted).apply { setPadding(dp(16), dp(20), dp(16), dp(20)) })
    }
    branches.forEach { b -> content.addView(branchCard(b, b.name.equals(mine, true)), marginParams(16, 8, 16, 8)) }
}

private fun MainActivity.detailRow(parent: LinearLayout, label: String, value: String) {
    parent.addView(tv(label.uppercase(), 10, muted).apply { setTypeface(typeface, Typeface.BOLD); letterSpacing = 0.08f; setPadding(0, dp(12), 0, 0) })
    parent.addView(tv(value.ifBlank { "—" }, 14, blue).apply { setPadding(0, dp(2), 0, 0) })
}

internal fun MainActivity.showBranchDialog(b: Branch, mine: Boolean) {
    val me = currentUser()
    val admin = me?.role == Roles.ADMIN
    val staff = me != null && Roles.isStaff(me.role)
    val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    root.background = bg(Color.WHITE, Color.TRANSPARENT, 18)
    root.clipToOutline = true
    root.outlineProvider = ViewOutlineProvider.BACKGROUND
    root.addView(branchPhoto(b, mine, 170))

    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16)) }
    body.addView(tv("${b.name} Branch", 22, blue).apply { setTypeface(Typeface.SERIF, Typeface.BOLD) })
    if (mine) body.addView(tv("This is the branch you manage.", 12, red).apply { setTypeface(typeface, Typeface.BOLD); setPadding(0, dp(2), 0, 0) })
    detailRow(body, "Region", b.region)
    detailRow(body, "Address", b.address)
    detailRow(body, "Phone", b.phone)
    detailRow(body, "Opening hours", b.hours)
    detailRow(body, "About", b.notes)

    val call = button("Call", blue, Color.WHITE)
    val maps = button("Open Maps", red, Color.WHITE)
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(18), 0, 0) }
    row.addView(call, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(8), 0) })
    row.addView(maps, LinearLayout.LayoutParams(0, -2, 1f))
    body.addView(row)

    val third = when {
        admin -> button("Edit branch", blue, Color.WHITE)
        !staff -> button("Request a Quote", blue, Color.WHITE)
        else -> null
    }
    if (third != null) body.addView(third, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    val close = outlineButton("Close", blue)
    body.addView(close, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    root.addView(body)

    val dialog = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(root) }).create()
    dialog.window?.setBackgroundDrawable(InsetDrawable(ColorDrawable(Color.TRANSPARENT), dp(18)))

    call.setOnClickListener {
        val digits = b.phone.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } < 9) toast("Phone: ${b.phone}")
        else startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$digits")))
    }
    maps.setOnClickListener { openMaps(b.address.ifBlank { "${b.name} Branch, Gauteng" }) }
    third?.setOnClickListener {
        dialog.dismiss()
        if (admin) { editBranchId = b.id; showScreen("branchForm") } else showScreen("quote")
    }
    close.setOnClickListener { dialog.dismiss() }
    dialog.show()
}

internal fun MainActivity.branchFormScreen() {
    val me = currentUser()
    if (me?.role != Roles.ADMIN) { showScreen("branches"); return }
    val existing = editBranchId?.let { id -> db.loadBranches().firstOrNull { it.id == id } }
    if (existing == null) { showScreen("branches"); return }
    pageIntro("Edit ${existing.name} Branch", "Changes are saved on this device and sent to the Woodlands database.")
    val name = field("Branch name", "Branch name").apply { setText(existing.name); isEnabled = false; alpha = 0.6f }
    val region = field("Region", "Region").apply { setText(existing.region) }
    val address = field("Address", "e.g. 12 Main Rd, Randfontein, Gauteng", required = false).apply { setText(existing.address) }
    val phone = field("Phone", "Phone number").apply { setText(existing.phone) }
    val hours = field("Opening hours", "e.g. Mon-Fri: 8am-5pm").apply { setText(existing.hours) }
    val notes = field("About", "Short description", multi = true, required = false).apply { setText(existing.notes) }
    var pickedPath: String? = null
    val preview = ImageView(this).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        background = bg(Color.rgb(230, 227, 219), Color.TRANSPARENT, 12)
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
    }
    showBranchImage(preview, existing)
    val photoStatus = tv("Current photo", 11, muted).apply { setPadding(0, dp(6), 0, 0) }
    val choose = outlineButton("Choose photo from device", blue)
    choose.setOnClickListener {
        pickImage { uri ->
            val path = prepareImage(uri, "branch_${existing.id}_${System.currentTimeMillis()}")
            if (path == null) {
                toast("Couldn't read that image")
            } else {
                pickedPath = path
                preview.tag = null
                preview.setImageBitmap(BitmapFactory.decodeFile(path))
                photoStatus.text = "New photo selected. It is saved when you press Save Changes."
            }
        }
    }
    val photoBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    photoBox.addView(tv("Branch photo", 11, blue).apply { setPadding(dp(2), 0, dp(2), dp(4)) })
    photoBox.addView(preview, LinearLayout.LayoutParams(dp(180), dp(110)))
    photoBox.addView(photoStatus)
    photoBox.addView(choose, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
    listOf(name, region, address, phone, hours, notes).forEach { content.addView(it, marginParams(16, 6, 16, 6)) }
    content.addView(tv("This address is what the Open Maps button searches for. Use the full street address for the best result.", 11, muted).apply { setPadding(dp(16), dp(0), dp(16), dp(4)) })
    content.addView(tv("The branch name can't be changed here because quotes and staff accounts refer to it.", 11, muted).apply { setPadding(dp(16), dp(4), dp(16), dp(4)) })
    content.addView(button("Save Changes", blue, Color.WHITE).apply {
        setOnClickListener {
            if (region.text.isNullOrBlank()) { toast("Region is required"); return@setOnClickListener }
            val newImage = pickedPath?.let { "local:$it" } ?: existing.image
            db.saveBranch(existing.copy(
                region = region.text.toString().trim(),
                address = address.text.toString().trim(),
                phone = phone.text.toString().trim(),
                hours = hours.text.toString().trim(),
                notes = notes.text.toString().trim(),
                image = newImage
            ))
            toast("Branch saved")
            editBranchId = null
            showScreen("branches")
        }
    }, marginParams(16, 14, 16, 6))
    content.addView(outlineButton("Cancel", blue).apply { setOnClickListener { editBranchId = null; showScreen("branches") } }, marginParams(16, 0, 16, 24))
}