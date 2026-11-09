package com.woodlands.mobile

import android.graphics.Color
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.ConnectivityManager
import android.net.Network

internal data class ConnectionStatus(val online: Boolean, val database: Boolean, val detail: String, val target: String = "")

internal data class EndpointStatus(val name: String, val ok: Boolean, val reachable: Boolean, val detail: String)

private class StatusRow(val view: LinearLayout, val dot: View, val value: TextView)

private fun bindRow(root: View, id: Int, label: String): StatusRow {
    val row = root.findViewById<LinearLayout>(id)
    row.findViewById<TextView>(R.id.status_label).text = label
    return StatusRow(row, row.findViewById<View>(R.id.status_dot), row.findViewById<TextView>(R.id.status_value))
}

private fun MainActivity.paintStatus(row: StatusRow, state: Boolean?, on: String, off: String) {
    val color = when (state) {
        true -> green
        false -> Color.rgb(176, 58, 46)
        null -> Color.rgb(160, 160, 152)
    }
    row.dot.background = bg(color, Color.TRANSPARENT, 5)
    row.value.text = when (state) {
        true -> on
        false -> off
        null -> "Checking…"
    }
    row.value.setTextColor(color)
}

private fun MainActivity.pendingText(): String {
    val n = db.pendingOps().size
    return if (n == 0) "All changes are synced." else "$n change${if (n == 1) "" else "s"} waiting to upload."
}

private fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())

internal fun MainActivity.connectionsSection() {
    val root = layoutInflater.inflate(R.layout.view_connections, content, false)
    val database = bindRow(root, R.id.row_database, "Database connection status")
    val online = bindRow(root, R.id.row_online, "Application online status")
    val pending = root.findViewById<TextView>(R.id.connections_pending)
    val detail = root.findViewById<TextView>(R.id.connections_detail)
    val refresh = root.findViewById<Button>(R.id.connections_refresh)
    pending.text = pendingText()

    fun run(sync: Boolean) {
        paintStatus(database, null, "", "")
        paintStatus(online, null, "", "")
        refresh.isEnabled = false
        refresh.text = "Checking…"
        SyncManager.checkConnection(sync) { s ->
            paintStatus(database, s.database, "Connected", "Not connected")
            paintStatus(online, s.online, "Online", "Offline")
            pending.text = pendingText()
            detail.text = s.detail + " Last checked " + now() + "."
            refresh.isEnabled = true
            refresh.text = "Refresh"
        }
    }

    refresh.setOnClickListener { run(true) }
    val cm = getSystemService(ConnectivityManager::class.java)
    val watcher = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { root.post { run(false) } }
        override fun onLost(network: Network) { root.post { run(false) } }
    }
    root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) { cm.registerDefaultNetworkCallback(watcher) }
        override fun onViewDetachedFromWindow(v: View) {
            try { cm.unregisterNetworkCallback(watcher) } catch (e: Exception) { }
        }
    })
    content.addView(root)
    run(false)
}

internal fun MainActivity.apiStatusSection() {
    val root = layoutInflater.inflate(R.layout.view_api_status, content, false)
    val server = bindRow(root, R.id.row_api_server, "Connection to Server")
    val rows = listOf(
        bindRow(root, R.id.row_api_products, "Products"),
        bindRow(root, R.id.row_api_branches, "Branches"),
        bindRow(root, R.id.row_api_faqs, "FAQs"),
        bindRow(root, R.id.row_api_testimonials, "Testimonials"),
        bindRow(root, R.id.row_api_quotes, "Quote requests"),
        bindRow(root, R.id.row_api_users, "Account Users")
    )
    val summary = root.findViewById<TextView>(R.id.api_summary)
    val check = root.findViewById<Button>(R.id.api_check)
    server.value.text = "Not checked"
    rows.forEach { it.value.text = "Not checked" }

    check.setOnClickListener {
        paintStatus(server, null, "", "")
        rows.forEach { paintStatus(it, null, "", "") }
        check.isEnabled = false
        check.text = "Checking… please wait"
        SyncManager.checkApi { list ->
            val reachable = list.any { it.reachable }
            val okCount = list.count { it.ok }
            paintStatus(server, reachable, "Online", "Offline")
            list.forEachIndexed { i, e -> paintStatus(rows[i], e.ok, "Online", e.detail) }
            val msg = when {
                okCount == list.size -> "All ${list.size} services are online."
                !reachable -> "The Woodlands server can't be reached, so everything is offline."
                okCount == 0 -> "The server is running but it can't read the database."
                else -> "$okCount of ${list.size} services are online."
            }
            summary.text = msg + " Last checked " + now() + "."
            check.isEnabled = true
            check.text = "Check Connection status"
        }
    }
    content.addView(root)
}

internal fun MainActivity.statusScreen() {
    pageIntro("Connection status", "Check the app's connection to the Woodlands service.")
    connectionsSection()
    apiStatusSection()
}