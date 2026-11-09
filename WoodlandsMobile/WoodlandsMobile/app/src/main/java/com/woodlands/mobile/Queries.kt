package com.woodlands.mobile

import android.content.ContentValues
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlin.random.Random

internal const val OP_HTTP = "http"
internal const val OP_PROMOTE = "promote_user"

internal const val OP_BRANCH_IMAGE = "branch_image"

internal data class PendingOp(
    val id: Long,
    val kind: String,
    val method: String,
    val path: String,
    val body: String?,
    val attempts: Int,
    /** Account that queued the operation, or null for public operations (register, quotes). */
    val ownerId: String? = null,
    val lastError: String? = null
)

private val kitchenUnitName = Regex("^kitchen unit (\\d+)$", RegexOption.IGNORE_CASE)
private val kitchenName = Regex("^kitchen (\\d+)$", RegexOption.IGNORE_CASE)
private val tvName = Regex("^floating tv-(\\d+)$", RegexOption.IGNORE_CASE)
private val imageKeyPattern = Regex("^(kitchen|tv)_(\\d+)$")

internal fun imageKey(path: String): String {
    if (!path.contains('/')) return path
    val name = path.substringAfterLast('/').substringBeforeLast('.')
    kitchenUnitName.matchEntire(name)?.let { return "kitchen_${it.groupValues[1]}" }
    kitchenName.matchEntire(name)?.let { return "kitchen_${it.groupValues[1]}" }
    tvName.matchEntire(name)?.let { return "tv_${it.groupValues[1]}" }
    return path
}

internal fun remoteImagePath(key: String): String {
    val match = imageKeyPattern.matchEntire(key) ?: return key
    val number = match.groupValues[2]
    return when {
        match.groupValues[1] == "tv" && number == "9" -> "/images/products/Floating Tv-9.jpeg"
        match.groupValues[1] == "tv" -> "/images/products/Floating TV-$number.jpeg"
        number == "2" -> "/images/products/Kitchen 2.jpeg"
        else -> "/images/products/Kitchen Unit $number.jpeg"
    }
}

internal fun JSONObject.str(name: String): String? = if (isNull(name)) null else optString(name)

internal fun JSONObject.list(name: String): List<String> {
    val arr = when (val raw = opt(name)) {
        is JSONArray -> raw
        is String -> try { JSONArray(raw) } catch (e: JSONException) { JSONArray() }
        else -> JSONArray()
    }
    return (0 until arr.length()).map { arr.optString(it) }
}

internal fun parseMillis(value: String?): Long {
    if (value.isNullOrBlank()) return System.currentTimeMillis()
    return try {
        OffsetDateTime.parse(value).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            LocalDateTime.parse(value).toInstant(ZoneOffset.UTC).toEpochMilli()
        } catch (e2: Exception) {
            System.currentTimeMillis()
        }
    }
}

private fun Cursor.str(name: String): String = getString(getColumnIndexOrThrow(name)) ?: ""
private fun Cursor.strOrNull(name: String): String? = getString(getColumnIndexOrThrow(name))
private fun Cursor.intOf(name: String): Int = getInt(getColumnIndexOrThrow(name))
private fun Cursor.longOf(name: String): Long = getLong(getColumnIndexOrThrow(name))

private fun LocalDb.remoteIdOf(table: String, id: Long): String? =
    readableDatabase.rawQuery("SELECT remote_id FROM $table WHERE id=?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
    }

private fun isPublicOp(method: String, path: String): Boolean =
    path.startsWith("/api/auth/") ||
            (method == "POST" && path.startsWith("/api/contact"))

private fun LocalDb.enqueue(method: String, path: String, body: String?, ref: String?, kind: String = OP_HTTP) {
    val owner = if (isPublicOp(method, path)) null else Session(appContext).userId
    val v = ContentValues().apply {
        put("owner_id", owner)
        put("kind", kind)
        put("method", method)
        put("path", path)
        put("body", body)
        put("local_ref", ref)
        put("attempts", 0)
    }
    writableDatabase.insert("outbox", null, v)
    SyncManager.syncAll()
}

private fun LocalDb.patchPendingCreate(ref: String, body: String) {
    writableDatabase.update("outbox", ContentValues().apply { put("body", body) }, "local_ref=?", arrayOf(ref))
}

private fun LocalDb.saveSynced(table: String, id: Long, isNew: Boolean, values: ContentValues, apiPath: String, body: String, newRemoteId: String? = null) {
    val db = writableDatabase
    if (isNew) {
        if (newRemoteId != null) values.put("remote_id", newRemoteId)
        val rowId = db.insert(table, null, values)
        enqueue("POST", apiPath, body, "$table:$rowId")
        return
    }
    db.update(table, values, "id=?", arrayOf(id.toString()))
    val remote = remoteIdOf(table, id)
    if (remote != null) enqueue("PUT", "$apiPath/$remote", body, null)
    else patchPendingCreate("$table:$id", body)
}

private fun LocalDb.deleteSynced(table: String, id: Long, apiPath: String) {
    val remote = remoteIdOf(table, id)
    writableDatabase.delete(table, "id=?", arrayOf(id.toString()))
    if (remote != null) enqueue("DELETE", "$apiPath/$remote", null, null)
    else writableDatabase.delete("outbox", "local_ref=?", arrayOf("$table:$id"))
}

internal fun LocalDb.pendingOps(): List<PendingOp> {
    val out = mutableListOf<PendingOp>()
    readableDatabase.query("outbox", null, "COALESCE(failed,0)=0", null, null, null, "id").use { c ->
        while (c.moveToNext()) out += c.toPendingOp()
    }
    return out
}

private fun Cursor.toPendingOp() = PendingOp(
    longOf("id"), str("kind"), str("method"), str("path"), strOrNull("body"), intOf("attempts"),
    strOrNull("owner_id"), strOrNull("last_error")
)

/** Operations that the server rejected permanently or that ran out of retries. */
internal fun LocalDb.failedOps(): List<PendingOp> {
    val out = mutableListOf<PendingOp>()
    readableDatabase.query("outbox", null, "failed=1", null, null, null, "id").use { c ->
        while (c.moveToNext()) out += c.toPendingOp()
    }
    return out
}

internal fun LocalDb.pendingOpCount(): Int {
    val userId = Session(appContext).userId ?: return 0

    return DatabaseUtils.queryNumEntries(
        readableDatabase,
        "outbox",
        "COALESCE(failed,0)=0 AND owner_id=?",
        arrayOf(userId)
    ).toInt()
}

internal fun LocalDb.failedOpCount(): Int {
    val userId = Session(appContext).userId ?: return 0

    return DatabaseUtils.queryNumEntries(
        readableDatabase,
        "outbox",
        "failed=1 AND owner_id=?",
        arrayOf(userId)
    ).toInt()
}

internal fun LocalDb.markOpFailed(id: Long, error: String) {
    writableDatabase.execSQL("UPDATE outbox SET failed=1, last_error=? WHERE id=?", arrayOf<Any?>(error, id))
}

/** Put a failed operation back in the queue for another try. */
internal fun LocalDb.retryOp(id: Long) {
    writableDatabase.execSQL("UPDATE outbox SET failed=0, attempts=0, last_error=NULL WHERE id=?", arrayOf<Any?>(id))
    SyncManager.syncAll()
}

// ---- notifications ----

internal data class NotificationRow(val id: Long, val remoteId: String, val title: String, val message: String, val kind: String, val read: Boolean, val createdAt: Long)

/** Convert the /api/notifications array into rows for replaceNotifications(). */
internal fun notificationValues(array: JSONArray): List<ContentValues> =
    (0 until array.length()).mapNotNull { array.optJSONObject(it) }
        .filter { it.optString("id").isNotBlank() }
        .map { n ->
            ContentValues().apply {
                put("remote_id", n.optString("id"))
                put("title", n.str("title") ?: n.str("subject") ?: "")
                put("message", n.str("message") ?: n.str("body") ?: n.str("text") ?: "")
                put("kind", n.str("type") ?: n.str("kind") ?: "")
                val read = if (n.has("is_read")) n.optBoolean("is_read") else n.optBoolean("read", false)
                put("is_read", if (read) 1 else 0)
                put("created_at", parseMillis(n.str("created_at")))
            }
        }

internal fun LocalDb.replaceNotifications(ownerId: String, rows: List<ContentValues>) {
    rows.forEach { it.put("owner_id", ownerId) }
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.delete("notifications", null, null)
        rows.forEach { db.insertWithOnConflict("notifications", null, it, SQLiteDatabase.CONFLICT_REPLACE) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

internal fun LocalDb.unreadNotificationCount(): Int =
    DatabaseUtils.queryNumEntries(readableDatabase, "notifications", "is_read=0").toInt()

internal fun LocalDb.loadNotifications(): List<NotificationRow> {
    val out = mutableListOf<NotificationRow>()
    readableDatabase.query("notifications", null, null, null, null, null, "created_at DESC").use { c ->
        while (c.moveToNext()) out += NotificationRow(c.longOf("id"), c.str("remote_id"), c.str("title"), c.str("message"), c.str("kind"), c.intOf("is_read") == 1, c.longOf("created_at"))
    }
    return out
}

/** Mark one notification read locally and queue PATCH /api/notifications/{id}/read. */
internal fun LocalDb.markNotificationRead(n: NotificationRow) {
    writableDatabase.execSQL("UPDATE notifications SET is_read=1 WHERE id=?", arrayOf<Any?>(n.id))
    if (n.remoteId.isNotBlank()) enqueue("PATCH", "/api/notifications/${n.remoteId}/read", null, null)
}

internal fun LocalDb.markAllNotificationsRead() {
    writableDatabase.execSQL("UPDATE notifications SET is_read=1")
    enqueue("PATCH", "/api/notifications/read-all", null, null)
}

internal fun LocalDb.dismissNotification(n: NotificationRow) {
    writableDatabase.delete("notifications", "id=?", arrayOf(n.id.toString()))
    if (n.remoteId.isNotBlank()) enqueue("DELETE", "/api/notifications/${n.remoteId}", null, null)
}

// ---- services ----

internal data class ServiceRow(val id: Long, val remoteId: String, val name: String, val description: String, val image: String, val active: Boolean)

internal fun LocalDb.loadServices(): List<ServiceRow> {
    val out = mutableListOf<ServiceRow>()
    readableDatabase.query("services", null, null, null, null, null, "name").use { c ->
        while (c.moveToNext()) out += ServiceRow(c.longOf("id"), c.str("remote_id"), c.str("name"), c.str("description"), c.str("image"), c.intOf("active") == 1)
    }
    return out
}

internal fun LocalDb.removeOp(id: Long) {
    writableDatabase.delete("outbox", "id=?", arrayOf(id.toString()))
}

internal fun LocalDb.bumpAttempts(id: Long) {
    writableDatabase.execSQL("UPDATE outbox SET attempts = attempts + 1 WHERE id=?", arrayOf<Any?>(id))
}

internal fun LocalDb.reconcile(table: String, rows: List<ContentValues>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val keep = HashSet<String>()
        rows.forEach { v ->
            val remoteId = v.getAsString("remote_id")
            keep += remoteId
            if (db.update(table, v, "remote_id=?", arrayOf(remoteId)) == 0) db.insert(table, null, v)
        }
        val stale = mutableListOf<String>()
        db.rawQuery("SELECT id, remote_id FROM $table", null).use { c ->
            while (c.moveToNext()) {
                if (c.isNull(1) || c.getString(1) !in keep) stale += c.getString(0)
            }
        }
        stale.forEach { db.delete(table, "id=?", arrayOf(it)) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

internal fun userValues(o: JSONObject): ContentValues = ContentValues().apply {
    put("id", o.optString("id"))
    put("full_name", o.str("full_name") ?: "")
    put("email", (o.str("email") ?: "").lowercase())
    put("phone", o.str("phone"))
    put("role", o.str("role") ?: Roles.CUSTOMER)
    put("branch", o.str("branch"))
    put("active", if (o.optBoolean("active", true)) 1 else 0)
    put("created_at", parseMillis(o.str("created_at")))
}

internal fun LocalDb.upsertUser(o: JSONObject) {
    writableDatabase.insertWithOnConflict("users", null, userValues(o), SQLiteDatabase.CONFLICT_REPLACE)
}

internal fun LocalDb.reconcileUsers(rows: List<ContentValues>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        val keep = HashSet<String>()
        rows.forEach { v ->
            keep += v.getAsString("id")
            db.insertWithOnConflict("users", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
        val stale = mutableListOf<String>()
        db.rawQuery("SELECT id FROM users", null).use { c ->
            while (c.moveToNext()) if (c.getString(0) !in keep) stale += c.getString(0)
        }
        stale.forEach { db.delete("users", "id=?", arrayOf(it)) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

internal fun LocalDb.clearPrivateData() {
    val db = writableDatabase
    db.delete("users", null, null)
    db.delete("quote_requests", null, null)
    db.delete("notifications", null, null)
    db.delete("contacts", "role=?", arrayOf(Roles.ADMIN))
}

internal fun LocalDb.loadContacts(): List<AppUser> {
    val out = mutableListOf<AppUser>()
    readableDatabase.query("contacts", null, null, null, null, null, "full_name").use { c ->
        while (c.moveToNext()) out += c.toUser()
    }
    return out
}

internal fun LocalDb.reconcileContacts(rows: List<ContentValues>) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.delete("contacts", null, null)
        rows.forEach { db.insertWithOnConflict("contacts", null, it, SQLiteDatabase.CONFLICT_REPLACE) }
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

internal fun LocalDb.loadProducts(): List<Product> {
    val out = mutableListOf<Product>()
    readableDatabase.query("products", null, null, null, null, null, "category,title").use { c ->
        while (c.moveToNext()) out += Product(
            c.intOf("id"),
            c.str("category"),
            c.str("title"),
            c.str("tagline"),
            c.str("description"),
            imageKey(c.str("image")),
            c.str("gallery").split("|").filter { it.isNotBlank() }.map { imageKey(it) },
            c.str("features").split("|").filter { it.isNotBlank() },
            c.str("finishes").split("|").filter { it.isNotBlank() },
            c.str("lead_time"),
            c.strOrNull("tag"),
            c.str("price")
        )
    }
    return out
}

internal fun LocalDb.loadTestimonials(): List<Testimonial> {
    val out = mutableListOf<Testimonial>()
    readableDatabase.query("testimonials", null, null, null, null, null, "id").use { c ->
        while (c.moveToNext()) out += Testimonial(c.intOf("id"), c.str("name"), c.str("role"), c.str("location"), c.intOf("rating"), c.str("review"), c.str("project"))
    }
    return out
}

internal fun LocalDb.loadFaqs(): List<Faq> {
    val out = mutableListOf<Faq>()
    readableDatabase.query("faqs", null, null, null, null, null, "category,id").use { c ->
        while (c.moveToNext()) out += Faq(c.intOf("id"), c.str("category"), c.str("question"), c.str("answer"))
    }
    return out
}

internal fun LocalDb.loadBranches(): List<Branch> {
    val out = mutableListOf<Branch>()
    readableDatabase.query("branches", null, null, null, null, null, "name").use { c ->
        while (c.moveToNext()) out += Branch(c.intOf("id"), c.str("name"), c.str("region"), c.str("phone"), c.str("hours"), c.str("notes"), c.str("image"), c.str("address"))
    }
    return out
}

internal fun LocalDb.saveBranch(b: Branch) {
    val isLocalImage = b.image.startsWith("local:")
    val v = ContentValues().apply {
        put("region", b.region)
        put("address", b.address)
        put("phone", b.phone)
        put("hours", b.hours)
        put("notes", b.notes)
        put("image", b.image)
    }
    val body = JSONObject()
        .put("region", b.region)
        .put("address", b.address)
        .put("phone", b.phone)
        .put("hours", b.hours)
        .put("notes", b.notes)
    if (!isLocalImage) body.put("image", b.image)
    saveSynced("branches", b.id.toLong(), false, v, "/api/branches", body.toString())
    if (isLocalImage) {
        val remote = remoteIdOf("branches", b.id.toLong())
        if (remote != null) enqueue("PUT", "/api/branches/$remote/image", b.image.removePrefix("local:"), null, OP_BRANCH_IMAGE)
    }
}

internal fun LocalDb.setBranchImage(remoteId: String, url: String) {
    writableDatabase.update("branches", ContentValues().apply { put("image", url) }, "remote_id=?", arrayOf(remoteId))
}

private fun Cursor.toUser(): AppUser = AppUser(
    str("id"), str("full_name"), str("email"), strOrNull("phone"), str("role"), strOrNull("branch"),
    intOf("active") == 1, longOf("created_at")
)

internal fun LocalDb.loadUsers(): List<AppUser> {
    val out = mutableListOf<AppUser>()
    readableDatabase.query("users", null, null, null, null, null, "full_name").use { c ->
        while (c.moveToNext()) out += c.toUser()
    }
    return out
}

internal fun LocalDb.findUserByEmail(email: String): AppUser? =
    readableDatabase.query("users", null, "lower(email)=?", arrayOf(email.trim().lowercase()), null, null, null).use { c ->
        if (c.moveToFirst()) c.toUser() else null
    }

internal fun LocalDb.findUserById(id: String): AppUser? =
    readableDatabase.query("users", null, "id=?", arrayOf(id), null, null, null).use { c ->
        if (c.moveToFirst()) c.toUser() else null
    }

internal fun LocalDb.createUser(fullName: String, email: String, phone: String?, password: String, role: String, branch: String?): String? {
    if (findUserByEmail(email) != null) return null
    val cleanEmail = email.trim().lowercase()
    val tempId = UUID.randomUUID().toString()
    val v = ContentValues().apply {
        put("id", tempId)
        put("full_name", fullName)
        put("email", cleanEmail)
        put("phone", phone)
        put("role", role)
        put("branch", branch)
        put("active", 1)
        put("created_at", System.currentTimeMillis())
    }
    writableDatabase.insert("users", null, v)
    val register = JSONObject()
        .put("fullName", fullName)
        .put("email", cleanEmail)
        .put("password", password)
        .put("phone", phone ?: "")
    enqueue("POST", "/api/auth/register", register.toString(), null)
    val promote = JSONObject()
        .put("email", cleanEmail)
        .put("full_name", fullName)
        .put("phone", phone ?: "")
        .put("role", role)
        .put("branch", branch ?: JSONObject.NULL)
        .put("active", true)
    enqueue("PUT", "", promote.toString(), null, OP_PROMOTE)
    return tempId
}

internal fun LocalDb.updateUserProfile(id: String, fullName: String, phone: String?) {
    val v = ContentValues().apply { put("full_name", fullName); put("phone", phone) }
    writableDatabase.update("users", v, "id=?", arrayOf(id))
    val body = JSONObject().put("full_name", fullName).put("phone", phone ?: "")
    enqueue("PUT", "/api/app-users/$id", body.toString(), null)
}

internal fun LocalDb.updateUserAdmin(id: String, fullName: String, email: String, phone: String?, role: String, branch: String?, active: Boolean) {
    val v = ContentValues().apply {
        put("full_name", fullName)
        put("email", email.trim().lowercase())
        put("phone", phone)
        put("role", role)
        put("branch", branch)
        put("active", if (active) 1 else 0)
    }
    writableDatabase.update("users", v, "id=?", arrayOf(id))
    val body = JSONObject()
        .put("full_name", fullName)
        .put("phone", phone ?: "")
        .put("role", role)
        .put("branch", branch ?: JSONObject.NULL)
        .put("active", active)
    enqueue("PUT", "/api/app-users/$id", body.toString(), null)
}

internal fun LocalDb.deleteUser(id: String) {
    writableDatabase.delete("users", "id=?", arrayOf(id))
    enqueue("DELETE", "/api/app-users/$id", null, null)
}

internal fun LocalDb.deleteUserData(id: String, email: String) {
    val db = writableDatabase
    db.beginTransaction()
    try {
        db.delete("quote_requests", "lower(email)=lower(?)", arrayOf(email))
        db.delete("contact_submissions", "lower(email)=lower(?)", arrayOf(email))
        db.delete("users", "id=?", arrayOf(id))
        db.setTransactionSuccessful()
    } finally {
        db.endTransaction()
    }
}

private fun Cursor.toQuote(): QuoteRow = QuoteRow(
    longOf("id"),
    str("quote_code"),
    str("first_name"),
    str("last_name"),
    str("email"),
    str("phone"),
    str("branch"),
    str("service"),
    str("message"),
    strOrNull("product_id"),
    longOf("created_at"),
    strOrNull("status") ?: "Pending",
    strOrNull("value")
)

internal fun LocalDb.loadQuotes(): List<QuoteRow> {
    val out = mutableListOf<QuoteRow>()
    readableDatabase.query("quote_requests", null, null, null, null, null, "created_at DESC").use { c ->
        while (c.moveToNext()) out += c.toQuote()
    }
    return out
}

internal fun LocalDb.submitQuote(firstName: String, lastName: String, email: String, phone: String, branch: String, service: String, message: String, productId: String?) {
    val code = "QT-" + (10000 + Random.nextInt(90000))
    val remoteProduct = productId?.toLongOrNull()?.let { remoteIdOf("products", it) }
    val v = ContentValues().apply {
        put("quote_code", code)
        put("first_name", firstName)
        put("last_name", lastName)
        put("email", email)
        put("phone", phone)
        put("branch", branch)
        put("service", service)
        put("message", message)
        put("product_id", remoteProduct)
        put("created_at", System.currentTimeMillis())
        put("status", "Pending")
        put("value", null as String?)
    }
    val rowId = writableDatabase.insert("quote_requests", null, v)
    val body = JSONObject()
        .put("quote_code", code)
        .put("first_name", firstName)
        .put("last_name", lastName)
        .put("email", email)
        .put("phone", phone)
        .put("branch", branch)
        .put("service", service)
        .put("message", message)
        .put("status", "Pending")
    if (remoteProduct != null) body.put("product_id", remoteProduct)
    enqueue("POST", "/api/quote-requests", body.toString(), "quote_requests:$rowId")
}

internal fun LocalDb.updateQuoteStatus(id: Long, status: String) {
    writableDatabase.update("quote_requests", ContentValues().apply { put("status", status) }, "id=?", arrayOf(id.toString()))
    val remote = remoteIdOf("quote_requests", id) ?: return
    enqueue("PUT", "/api/quote-requests/$remote", JSONObject().put("status", status).toString(), null)
}

private fun LocalDb.newProductSlug(title: String): String {
    val base = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "product" }
    val taken = DatabaseUtils.queryNumEntries(readableDatabase, "products", "remote_id=?", arrayOf(base)) > 0
    return if (taken) base + "-" + (System.currentTimeMillis() % 10000) else base
}

internal fun LocalDb.saveProduct(p: Product, isNew: Boolean) {
    val image = remoteImagePath(p.image)
    val gallery = p.gallery.map { remoteImagePath(it) }
    val isFrom = p.price.startsWith("From ")
    val price = p.price.removePrefix("From ").trim()
    val v = ContentValues().apply {
        put("category", p.category)
        put("title", p.title)
        put("tagline", p.tagline)
        put("description", p.description)
        put("image", image)
        put("gallery", gallery.joinToString("|"))
        put("features", p.features.joinToString("|"))
        put("finishes", p.finishes.joinToString("|"))
        put("lead_time", p.lead)
        put("tag", p.tag)
        put("price", p.price)
    }
    val body = JSONObject()
        .put("category", p.category)
        .put("title", p.title)
        .put("tagline", p.tagline)
        .put("description", p.description)
        .put("image", image)
        .put("gallery", JSONArray(gallery).toString())
        .put("features", JSONArray(p.features).toString())
        .put("finishes", JSONArray(p.finishes).toString())
        .put("lead_time", p.lead)
        .put("tag", p.tag ?: JSONObject.NULL)
        .put("price", price)
        .put("is_from_price", isFrom)
    val slug = if (isNew) newProductSlug(p.title) else null
    if (slug != null) body.put("id", slug)
    saveSynced("products", p.id.toLong(), isNew, v, "/api/products", body.toString(), slug)
}

internal fun LocalDb.deleteProduct(id: Int) {
    deleteSynced("products", id.toLong(), "/api/products")
}

internal fun LocalDb.saveTestimonial(t: Testimonial, isNew: Boolean) {
    val v = ContentValues().apply {
        put("name", t.name)
        put("role", t.role)
        put("location", t.location)
        put("rating", t.rating)
        put("review", t.review)
        put("project", t.project)
    }
    val body = JSONObject()
        .put("name", t.name)
        .put("role", t.role)
        .put("location", t.location)
        .put("rating", t.rating)
        .put("review", t.review)
        .put("project", t.project)
    saveSynced("testimonials", t.id.toLong(), isNew, v, "/api/testimonials", body.toString())
}

internal fun LocalDb.deleteTestimonial(id: Int) {
    deleteSynced("testimonials", id.toLong(), "/api/testimonials")
}

internal fun LocalDb.saveFaq(f: Faq, isNew: Boolean) {
    val v = ContentValues().apply {
        put("category", f.category)
        put("question", f.question)
        put("answer", f.answer)
    }
    val body = JSONObject()
        .put("category", f.category)
        .put("question", f.question)
        .put("answer", f.answer)
    saveSynced("faqs", f.id.toLong(), isNew, v, "/api/faqs", body.toString())
}

internal fun LocalDb.deleteFaq(id: Int) {
    deleteSynced("faqs", id.toLong(), "/api/faqs")
}


internal fun LocalDb.saveService(id: Long, name: String, description: String, image: String, active: Boolean, isNew: Boolean) {
    val v = ContentValues().apply {
        put("name", name)
        put("description", description)
        put("image", image)
        put("active", if (active) 1 else 0)
    }
    val body = JSONObject()
        .put("name", name)
        .put("description", description)
        .put("image", image)
        .put("is_active", active)
    saveSynced("services", id, isNew, v, "/api/services", body.toString())
}

internal fun LocalDb.deleteService(id: Long) {
    deleteSynced("services", id, "/api/services")
}

/** Server id of a product row (null if it has not synced yet). */
internal fun LocalDb.productRemoteId(id: Int): String? = remoteIdOf("products", id.toLong())
