
package com.woodlands.mobile

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Base64
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File

object SyncManager {
    private const val TAG = "WoodlandsSync"
    private const val MAX_ATTEMPTS = 5

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences

    @Volatile
    private var ready = false

    private val scope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val rerun = AtomicBoolean(false)
    private val authAttemptSequence = AtomicLong(0)
    @Volatile
    var onChanged: ((Boolean) -> Unit)? = null

    @Volatile
    var onFinished: (() -> Unit)? = null

    private class PushOutcome(
        val allDone: Boolean,
        val processed: Int
    )

    fun init(context: Context) {
        appContext = context.applicationContext

        prefs = appContext.getSharedPreferences(
            "woodlands_sync",
            Context.MODE_PRIVATE
        )

        ready = true
    }

    fun forgetPrivateHashes() {
        if (!ready) return

        prefs.edit()
            .remove("h_users")
            .remove("h_quotes")
            .remove("h_notifications")
            .apply()
    }

    fun invalidateAuthAttempts() {
        authAttemptSequence.incrementAndGet()
    }

    fun syncAll() {
        if (!ready) return

        if (!running.compareAndSet(false, true)) {
            rerun.set(true)
            return
        }

        scope.launch {
            try {
                do {
                    rerun.set(false)
                    runSync()
                } while (rerun.get())
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed", e)
            } finally {
                running.set(false)
                mainHandler.post { onFinished?.invoke() }

                // If another sync was requested while this one was finishing,
                // start another pass instead of leaving that request pending.
                if (rerun.getAndSet(false)) {
                    syncAll()
                }
            }
        }
    }

    fun login(
        email: String,
        password: String,
        onResult: (Result<AppUser>) -> Unit
    ) {
        val attempt = authAttemptSequence.incrementAndGet()

        scope.launch {
            val result = doLogin(email, password, attempt)

            mainHandler.post {
                if (authAttemptSequence.get() == attempt) {
                    onResult(result)
                }
            }
        }
    }

    fun register(
        fullName: String,
        email: String,
        phone: String?,
        password: String,
        onResult: (Result<AppUser>) -> Unit
    ) {
        val attempt = authAttemptSequence.incrementAndGet()

        scope.launch {
            val body = JSONObject()
                .put("fullName", fullName.trim())
                .put("email", email.trim())
                .put("password", password)
                .put("phone", phone ?: "")

            val result: Result<AppUser> =
                when (
                    val response = ApiClient.post(
                        "/api/auth/register",
                        body.toString()
                    )
                ) {
                    is ApiResult.Success ->
                        doLogin(email, password, attempt)

                    is ApiResult.Failure ->
                        Result.failure(Exception(response.message))

                    is ApiResult.Offline ->
                        Result.failure(
                            Exception(
                                "You're offline. Connect to the internet to register."
                            )
                        )
                }

            mainHandler.post {
                if (authAttemptSequence.get() == attempt) {
                    onResult(result)
                }
            }
        }
    }

    private fun doLogin(
        email: String,
        password: String,
        attempt: Long
    ): Result<AppUser> {
        val body = JSONObject()
            .put("email", email.trim())
            .put("password", password)

        return when (
            val response = ApiClient.post(
                "/api/auth/login",
                body.toString()
            )
        ) {
            is ApiResult.Success -> {
                completeSignIn(response.body, attempt)
            }

            is ApiResult.Failure ->
                Result.failure(Exception(response.message))

            is ApiResult.Offline ->
                Result.failure(
                    Exception(
                        "You're offline. Connect to the internet to sign in."
                    )
                )
        }
    }

    private fun completeSignIn(responseBody: String, attempt: Long): Result<AppUser> {
        return try {
            val json = JSONObject(responseBody)
            val profile = json.getJSONObject("user")
            val token = json.optString("token").trim()
            val incomingUserId = profile.optString("id").trim()

            if (token.isBlank()) {
                return Result.failure(
                    Exception(
                        "The server did not return an authentication token."
                    )
                )
            }

            if (incomingUserId.isBlank()) {
                return Result.failure(
                    Exception(
                        "The server did not return a valid user ID."
                    )
                )
            }

            if (authAttemptSequence.get() != attempt) {
                return Result.failure(
                    Exception("This sign-in was replaced by a newer one.")
                )
            }

            val session = Session(appContext)
            val db = LocalDb.shared(appContext)

            // Clear private cached data when signing in as a
            // different account, or when starting a new session.
            // The outbox is deliberately preserved.
            if (session.userId != incomingUserId) {
                session.clear()
            }

            // Save the new account profile after clearing the
            // previous account's private data.
            db.upsertUser(profile)

            val user = db.findUserById(incomingUserId)

            if (authAttemptSequence.get() != attempt) {
                return Result.failure(
                    Exception("This sign-in was replaced by a newer one.")
                )
            }

            if (user == null) {
                Result.failure(
                    Exception("Could not read your profile.")
                )
            } else {
                session.userId = user.id
                session.token = token

                syncAll()

                Result.success(user)
            }
        } catch (e: JSONException) {
            Result.failure(
                Exception(
                    "Unexpected response from the server."
                )
            )
        } catch (e: Exception) {
            Result.failure(
                Exception(
                    e.message ?: "Sign-in failed."
                )
            )
        }
    }

    fun redeemHandoffCode(
        code: String,
        onResult: (Result<AppUser>) -> Unit
    ) {
        val attempt = authAttemptSequence.incrementAndGet()

        scope.launch {
            val body = JSONObject().put("code", code.trim()).toString()

            val result: Result<AppUser> =
                when (val response = ApiClient.post("/api/auth/handoff/redeem", body)) {
                    is ApiResult.Success ->
                        completeSignIn(response.body, attempt)

                    is ApiResult.Failure ->
                        Result.failure(Exception(response.message))

                    is ApiResult.Offline ->
                        Result.failure(
                            Exception("You're offline. Connect to the internet to sign in.")
                        )
                }

            mainHandler.post {
                if (authAttemptSequence.get() == attempt) {
                    onResult(result)
                }
            }
        }
    }

    fun isOnline(): Boolean {
        if (!ready) return false

        return when (
            ApiClient.get("/api/branches")
        ) {
            is ApiResult.Success -> true
            is ApiResult.Failure -> true
            is ApiResult.Offline -> false
        }
    }

    internal fun checkConnection(
        syncAfter: Boolean,
        onResult: (ConnectionStatus) -> Unit
    ) {
        scope.launch {
            val result = ApiClient.get("/api/branches")

            val status = when (result) {
                is ApiResult.Success ->
                    ConnectionStatus(
                        online = true,
                        database = true,
                        detail = "The app can reach the Woodlands server and database."
                    )

                is ApiResult.Failure ->
                    ConnectionStatus(
                        online = true,
                        database = false,
                        detail = "The phone is online, but the Woodlands server returned HTTP ${result.code}."
                    )

                is ApiResult.Offline ->
                    ConnectionStatus(
                        online = false,
                        database = false,
                        detail = "The phone cannot reach the Woodlands server."
                    )
            }

            val withTarget = status.copy(target = ApiClient.activeTarget)

            mainHandler.post {
                onResult(withTarget)
            }

            if (
                syncAfter &&
                status.online &&
                status.database
            ) {
                syncAll()
            }
        }
    }

    internal fun checkApi(
        onResult: (List<EndpointStatus>) -> Unit
    ) {
        scope.launch {
            val targets = listOf(
                "Products" to "/api/products",
                "Branches" to "/api/branches",
                "FAQs" to "/api/faqs",
                "Testimonials" to "/api/testimonials",
                "Quote requests" to "/api/quote-requests?email=status-check",
                "Users" to "/api/app-users?email=status-check"
            )

            val output = mutableListOf<EndpointStatus>()
            var serverDown = !isOnline()

            for ((name, path) in targets) {
                if (serverDown) {
                    output += EndpointStatus(
                        name,
                        false,
                        false,
                        "Offline"
                    )
                    continue
                }

                when (val result = ApiClient.get(path)) {
                    is ApiResult.Success ->
                        output += EndpointStatus(
                            name,
                            true,
                            true,
                            "Online"
                        )

                    is ApiResult.Failure ->
                        output += EndpointStatus(
                            name,
                            false,
                            true,
                            "Error ${result.code}"
                        )

                    is ApiResult.Offline -> {
                        serverDown = true

                        output += EndpointStatus(
                            name,
                            false,
                            false,
                            "Offline"
                        )
                    }
                }
            }

            mainHandler.post {
                onResult(output)
            }
        }
    }

    private fun pullContacts(db: LocalDb): Boolean {
        val session = Session(appContext)

        val userId = session.userId
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val sessionToken = session.token
            ?.takeIf { it.isNotBlank() }
            ?: return false

        fun isOriginalSession(): Boolean {
            return session.userId == userId &&
                    session.token == sessionToken &&
                    session.isLoggedIn
        }

        val me = db.findUserById(userId)
            ?: return false

        if (!isOriginalSession()) return false

        val seeAdmins =
            Roles.isStaff(me.role) &&
                    me.role == Roles.ADMIN

        val path = if (seeAdmins) {
            "/api/contacts?viewer=" + enc(me.id)
        } else {
            "/api/contacts"
        }

        val result = fetchArray(path) ?: return false

        // Do not apply contacts returned for an older account session.
        if (!isOriginalSession()) return false

        val (array, body) = result

        val rows = objects(array)
            .map { userValues(it) }
            .filter {
                it.getAsInteger("active") == 1
            }
            .filter { values ->
                val role = values.getAsString("role")

                Roles.isManager(role) ||
                        (seeAdmins && role == Roles.ADMIN)
            }

        if (!isOriginalSession()) return false

        db.reconcileContacts(rows)

        if (!isOriginalSession()) return false

        return changedSince(
            "h_contacts",
            body + seeAdmins
        )
    }

    private fun runSync() {
        val db = LocalDb.shared(appContext)
        val session = Session(appContext)

        // Capture the session that started this sync cycle.
        val startingUserId = session.userId
        val startingToken = session.token

        fun isOriginalSession(): Boolean {
            return session.userId == startingUserId &&
                    session.token == startingToken
        }

        val firstLoad =
            db.loadBranches().isEmpty() &&
                    db.loadProducts().isEmpty()

        // Push queued operations first.
        val outcome = push(db)

        if (!isOriginalSession()) {
            Log.i(
                TAG,
                "Stopping sync cycle because the active account changed."
            )
            return
        }

        if (!outcome.allDone) {
            Log.e(
                TAG,
                "SYNC STOPPED BEFORE DOWNLOADS. Processed: ${outcome.processed}"
            )
            if (outcome.processed > 0) {
                publishChange(firstLoad)
            }

            return
        }

        // Download shared data, checking the session between requests.
        var changed = false

        if (pullProducts(db)) {
            changed = true
        }

        if (!isOriginalSession()) return

        if (pullTestimonials(db)) {
            changed = true
        }

        if (!isOriginalSession()) return

        if (pullFaqs(db)) {
            changed = true
        }

        if (!isOriginalSession()) return

        if (pullBranches(db)) {
            changed = true
        }

        if (!isOriginalSession()) return

        if (pullServices(db)) {
            changed = true
        }

        if (!isOriginalSession()) return

        // These functions perform their own session and ownership checks.
        if (pullAccountData(db)) {
            changed = true
        }

        if (!isOriginalSession()) return

        if (pullContacts(db)) {
            changed = true
        }

        if (!isOriginalSession()) return

        if (pullNotifications(db)) {
            changed = true
        }

        if (!isOriginalSession()) return

        if (outcome.processed > 0 || changed) {
            publishChange(firstLoad)
        }
    }

    private fun publishChange(firstLoad: Boolean) {
        mainHandler.post {
            onChanged?.invoke(firstLoad)
        }
    }

    /*
     * Push queued local changes.
     *
     * Successful requests are removed.
     * Permanent HTTP errors are saved as failed operations.
     * Temporary errors are retried up to MAX_ATTEMPTS.
     * Offline requests remain queued.
     *
     * Failed operations do not block unrelated operations.
     */


    private fun push(db: LocalDb): PushOutcome {
        var processed = 0

        val session = Session(appContext)

        for (op in db.pendingOps()) {

            // Capture the account that owns this sync attempt.
            val startingUserId = session.userId

            // Account-specific operations must belong to the active account.

            if (
                op.ownerId != null &&
                op.ownerId != startingUserId
            ) {
                Log.w(
                    TAG,
                    "Paused operation ${op.id}: its owner does not match the signed-in account. Skipping it and continuing sync."
                )

                // Keep this operation in the outbox. Do not send it using another account. Continue processing and allow the current account to sync.
                continue
            }


            // Do not execute an account-specific operation without an account.
            if (
                op.ownerId != null &&
                (startingUserId.isNullOrBlank() || session.token.isNullOrBlank())
            ) {
                return PushOutcome(false, processed)
            }

            // Check again immediately before execution.
            if (session.userId != startingUserId) {
                Log.w(
                    TAG,
                    "Paused operation ${op.id}: the active account changed before execution."
                )

                return PushOutcome(false, processed)
            }

            val result = execute(op)

            // The account may have changed while the network request was running.
            // Never modify the outbox operation after such a change.
            if (session.userId != startingUserId) {
                Log.w(
                    TAG,
                    "Paused operation ${op.id}: the active account changed during execution."
                )

                return PushOutcome(false, processed)
            }

            // Check ownership once more before changing the queued operation.
            if (
                op.ownerId != null &&
                op.ownerId != session.userId
            ) {
                Log.w(
                    TAG,
                    "Paused operation ${op.id}: ownership changed before processing the response."
                )

                return PushOutcome(false, processed)
            }

            when {
                result is ApiResult.Success -> {
                    db.removeOp(op.id)
                    processed++
                }

                result is ApiResult.Offline -> {
                    // Keep the operation queued while the server is unreachable.
                    return PushOutcome(false, processed)
                }

                result is ApiResult.Failure &&
                        result.code in 400..499 &&
                        result.code != 408 &&
                        result.code != 429 -> {

                    val error =
                        "HTTP ${result.code}: ${result.message}"

                    Log.w(
                        TAG,
                        "Sync operation failed permanently: ${op.method} ${op.path}: $error"
                    )

                    db.markOpFailed(op.id, error)
                    processed++
                }

                else -> {
                    val nextAttempt = op.attempts + 1

                    if (nextAttempt >= MAX_ATTEMPTS) {
                        val error = when (result) {
                            is ApiResult.Failure ->
                                "HTTP ${result.code}: ${result.message}. Maximum retry attempts reached."

                            else ->
                                "Maximum retry attempts reached."
                        }

                        Log.w(
                            TAG,
                            "Sync operation failed after $nextAttempt attempts: ${op.method} ${op.path}"
                        )

                        db.markOpFailed(op.id, error)
                        processed++
                    } else {
                        db.bumpAttempts(op.id)
                        processed++

                        return PushOutcome(false, processed)
                    }
                }
            }
        }

        return PushOutcome(true, processed)
    }


    private fun execute(op: PendingOp): ApiResult {

        // Final ownership check before an operation reaches ApiClient.
        val currentUserId = Session(appContext).userId

        if (
            op.ownerId != null &&
            op.ownerId != currentUserId
        ) {
            Log.w(
                TAG,
                "Blocked operation ${op.id}: account ownership mismatch."
            )

            return blockedOffline("The operation was paused because the signed-in account changed.")
        }

        return when (op.kind) {
            OP_PROMOTE -> promote(op)
            OP_BRANCH_IMAGE -> uploadBranchImage(op)

            else -> when (op.method) {
                "POST" ->
                    ApiClient.post(
                        op.path,
                        op.body ?: "{}"
                    )

                "PUT" ->
                    ApiClient.put(
                        op.path,
                        op.body ?: "{}"
                    )

                "PATCH" ->
                    ApiClient.patch(
                        op.path,
                        op.body ?: "{}"
                    )

                "DELETE" ->
                    ApiClient.delete(op.path)

                else ->
                    ApiClient.get(op.path)
            }
        }
    }


    private fun uploadBranchImage(
        op: PendingOp
    ): ApiResult {
        val session = Session(appContext)

        val startingUserId = session.userId
        val startingToken = session.token

        // Branch image uploads are account-specific.
        if (
            op.ownerId.isNullOrBlank() ||
            startingUserId.isNullOrBlank() ||
            startingToken.isNullOrBlank() ||
            op.ownerId != startingUserId
        ) {
            Log.w(
                TAG,
                "Blocked branch image upload ${op.id}: account ownership mismatch."
            )

            return blockedOffline("The operation was paused because the signed-in account changed.")
        }

        fun isOriginalSession(): Boolean {
            return session.isLoggedIn &&
                    session.userId == startingUserId &&
                    session.token == startingToken &&
                    session.userId == op.ownerId
        }

        if (!isOriginalSession()) {
            return blockedOffline("The operation was paused because the signed-in account changed.")
        }

        val file = File(op.body ?: "")

        if (!file.exists()) {
            return ApiResult.Failure(
                404,
                "Image file is missing."
            )
        }

        val payload = JSONObject()
            .put("contentType", "image/jpeg")
            .put(
                "data",
                Base64.encodeToString(
                    file.readBytes(),
                    Base64.NO_WRAP
                )
            )

        if (!isOriginalSession()) {
            return blockedOffline("The operation was paused because the signed-in account changed.")
        }

        val result = ApiClient.put(
            op.path,
            payload.toString()
        )

        // Do not update local data if the session changed during upload.
        if (!isOriginalSession()) {
            Log.w(
                TAG,
                "Skipped local branch image update ${op.id}: account changed during upload."
            )

            return blockedOffline("The operation was paused because the signed-in account changed.")
        }

        if (result is ApiResult.Success) {
            val url = try {
                JSONObject(result.body)
                    .optString("image")
            } catch (e: JSONException) {
                ""
            }

            val remoteId = op.path
                .removePrefix("/api/branches/")
                .removeSuffix("/image")

            if (url.isNotBlank() && isOriginalSession()) {
                LocalDb.shared(appContext)
                    .setBranchImage(remoteId, url)
            }
        }

        return result
    }

    private fun promote(op: PendingOp): ApiResult {
        val session = Session(appContext)

        // Capture the account and token that started this operation.
        val startingUserId = session.userId
        val startingToken = session.token

        fun isOriginalOwner(): Boolean {
            // Public operations do not have an account owner.
            if (op.ownerId == null) return true

            return session.isLoggedIn &&
                    session.userId == op.ownerId &&
                    session.userId == startingUserId &&
                    session.token == startingToken
        }

        // Do not start an account-specific operation under another account.
        if (!isOriginalOwner()) {
            Log.w(
                TAG,
                "Blocked promotion ${op.id}: account ownership mismatch."
            )
            return blockedOffline("The operation was paused because the signed-in account changed.")
        }

        val body = JSONObject(op.body ?: "{}")
        val email = body.optString("email").trim()

        if (email.isBlank()) {
            return ApiResult.Failure(
                400,
                "A user email address is required."
            )
        }

        val lookup = ApiClient.get(
            "/api/app-users?email=" + enc(email)
        )

        // Do not send the second request if the account changed.
        if (!isOriginalOwner()) {
            Log.w(
                TAG,
                "Stopped promotion ${op.id}: account changed during user lookup."
            )
            return blockedOffline("The operation was paused because the signed-in account changed.")
        }

        if (lookup !is ApiResult.Success) {
            return lookup
        }

        val rows = try {
            JSONArray(lookup.body)
        } catch (e: JSONException) {
            return ApiResult.Failure(
                502,
                "The server returned an invalid user list."
            )
        }

        val id = rows
            .optJSONObject(0)
            ?.optString("id")
            .orEmpty()
            .trim()

        if (id.isBlank()) {
            return ApiResult.Failure(
                404,
                "User not found."
            )
        }

        body.remove("email")

        // Final ownership check immediately before changing the user's role.
        if (!isOriginalOwner()) {
            Log.w(
                TAG,
                "Stopped promotion ${op.id}: account changed before role update."
            )
            return blockedOffline("The operation was paused because the signed-in account changed.")
        }

        return ApiClient.put(
            "/api/app-users/$id",
            body.toString()
        )
    }

    /**
     * Result for an operation that must not run right now (e.g. the signed-in account changed).
     * It is reported as Offline so push() leaves the operation queued instead of failing it.
     */
    private fun blockedOffline(message: String): ApiResult =
        ApiResult.Offline(IOException(message))

    private fun enc(value: String): String {
        return URLEncoder.encode(
            value,
            "UTF-8"
        )
    }


    private fun fetchArray(
        path: String
    ): Pair<JSONArray, String>? {
        return when (val result = ApiClient.get(path)) {
            is ApiResult.Success -> {
                try {
                    Pair(
                        JSONArray(result.body),
                        result.body
                    )
                } catch (e: JSONException) {
                    Log.e(
                        TAG,
                        "Invalid JSON returned by $path",
                        e
                    )
                    null
                }
            }

            is ApiResult.Failure -> {
                Log.e(
                    TAG,
                    "API request failed: $path | HTTP ${result.code} | ${result.message}"
                )
                null
            }

            is ApiResult.Offline -> {
                Log.e(
                    TAG,
                    "API request offline: $path"
                )
                null
            }
        }
    }

    private fun objects(
        array: JSONArray
    ): List<JSONObject> {
        return (0 until array.length())
            .mapNotNull {
                array.optJSONObject(it)
            }
            .filter {
                it.optString("id").isNotBlank()
            }
    }

    private fun changedSince(
        key: String,
        body: String
    ): Boolean {
        val hash = body.hashCode().toString()

        if (prefs.getString(key, null) == hash) {
            return false
        }

        prefs.edit()
            .putString(key, hash)
            .apply()

        return true
    }

    private fun pullProducts(db: LocalDb): Boolean {
        val (array, body) =
            fetchArray("/api/products") ?: return false

        val rows = objects(array).map { product ->
            val price = product.str("price") ?: ""

            ContentValues().apply {
                put("remote_id", product.optString("id"))
                put("category", product.str("category") ?: "")
                put("title", product.str("title") ?: "")
                put("tagline", product.str("tagline") ?: "")
                put("description", product.str("description") ?: "")
                put("image", product.str("image") ?: "")

                put(
                    "gallery",
                    product.list("gallery").joinToString("|")
                )

                put(
                    "features",
                    product.list("features").joinToString("|")
                )

                put(
                    "finishes",
                    product.list("finishes").joinToString("|")
                )

                put("lead_time", product.str("lead_time") ?: "")
                put("tag", product.str("tag"))

                put(
                    "price",
                    if (
                        product.optBoolean("is_from_price", false) &&
                        price.isNotBlank()
                    ) {
                        "From $price"
                    } else {
                        price
                    }
                )
            }
        }

        db.reconcile(
            "products",
            rows
        )

        return changedSince(
            "h_products",
            body
        )
    }

    private fun pullTestimonials(db: LocalDb): Boolean {
        val (array, body) =
            fetchArray("/api/testimonials") ?: return false

        val rows = objects(array).map { testimonial ->
            ContentValues().apply {
                put("remote_id", testimonial.optString("id"))
                put("name", testimonial.str("name") ?: "")
                put("role", testimonial.str("role") ?: "")
                put("location", testimonial.str("location") ?: "")
                put("rating", testimonial.optInt("rating", 5))
                put("review", testimonial.str("review") ?: "")
                put("project", testimonial.str("project") ?: "")
            }
        }

        db.reconcile(
            "testimonials",
            rows
        )

        return changedSince(
            "h_testimonials",
            body
        )
    }

    private fun pullFaqs(db: LocalDb): Boolean {
        val (array, body) =
            fetchArray("/api/faqs") ?: return false

        val rows = objects(array).map { faq ->
            ContentValues().apply {
                put("remote_id", faq.optString("id"))
                put("category", faq.str("category") ?: "")
                put("question", faq.str("question") ?: "")
                put("answer", faq.str("answer") ?: "")
            }
        }

        db.reconcile(
            "faqs",
            rows
        )

        return changedSince(
            "h_faqs",
            body
        )
    }

    private fun pullBranches(db: LocalDb): Boolean {
        val (array, body) =
            fetchArray("/api/branches") ?: return false

        val rows = objects(array).map { branch ->
            ContentValues().apply {
                put("remote_id", branch.optString("id"))
                put("name", branch.str("name") ?: "")
                put("region", branch.str("region") ?: "")

                if (
                    branch.has("address") &&
                    !branch.isNull("address")
                ) {
                    put(
                        "address",
                        branch.optString("address")
                    )
                }

                put("phone", branch.str("phone") ?: "")
                put("hours", branch.str("hours") ?: "")
                put("notes", branch.str("notes") ?: "")
                put("image", branch.str("image") ?: "")
            }
        }

        db.reconcile(
            "branches",
            rows
        )

        return changedSince(
            "h_branches",
            body
        )
    }

    private fun pullServices(db: LocalDb): Boolean {
        val (array, body) =
            fetchArray("/api/services") ?: return false

        val rows = objects(array).map { service ->
            ContentValues().apply {
                put("remote_id", service.optString("id"))
                put("name", service.str("name") ?: "")
                put("description", service.str("description") ?: "")
                put("image", service.str("image") ?: "")
                put(
                    "active",
                    if (service.optBoolean("is_active", true)) 1 else 0
                )
            }
        }

        db.reconcile(
            "services",
            rows
        )

        return changedSince(
            "h_services",
            body
        )
    }

    private fun pullNotifications(db: LocalDb): Boolean {
        val session = Session(appContext)

        val userId = session.userId
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val sessionToken = session.token
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val result = fetchArray("/api/notifications") ?: return false

        if (
            session.userId != userId ||
            session.token != sessionToken
        ) {
            return false
        }

        val (array, body) = result

        db.replaceNotifications(
            userId,
            notificationValues(array)
        )

        return changedSince("h_notifications", body)
    }

    private fun pullAccountData(db: LocalDb): Boolean {
        val session = Session(appContext)

        val userId = session.userId
            ?.takeIf { it.isNotBlank() }
            ?: return false

        val sessionToken = session.token
            ?.takeIf { it.isNotBlank() }
            ?: return false

        // Every private-data update must still belong to this exact session.
        fun isOriginalSession(): Boolean {
            return session.userId == userId &&
                    session.token == sessionToken &&
                    session.isLoggedIn
        }

        val me = db.findUserById(userId)
            ?: return false

        if (!isOriginalSession()) return false

        val isAdmin = me.role == Roles.ADMIN
        var changed = false

        if (isAdmin) {
            val usersResult = fetchArray("/api/app-users")

            // Do not save a response belonging to an older session.
            if (!isOriginalSession()) return false

            if (usersResult != null) {
                val (array, body) = usersResult

                db.reconcileUsers(
                    objects(array).map {
                        userValues(it)
                    }
                )

                if (!isOriginalSession()) return false

                if (changedSince("h_users", body)) {
                    changed = true
                }
            }
        } else {
            val mine = fetchArray(
                "/api/app-users?email=" + enc(me.email)
            )

            if (!isOriginalSession()) return false

            if (mine != null) {
                val (array, body) = mine

                val row = objects(array).firstOrNull {
                    it.optString("id") == userId
                }

                if (row != null) {
                    db.upsertUser(row)

                    if (!isOriginalSession()) return false

                    if (changedSince("h_users", body)) {
                        changed = true
                    }
                } else {
                    // Only clear the session if it is still the session
                    // that made this request.
                    if (isOriginalSession()) {
                        session.clear()
                    }

                    return true
                }
            }
        }

        if (!isOriginalSession()) return false

        val branch = Roles.branchFor(me.role)

        val quotePath = when {
            isAdmin ->
                "/api/quote-requests"

            branch != null ->
                "/api/quote-requests?branch=" + enc(branch)

            else ->
                "/api/quote-requests?email=" + enc(me.email)
        }

        val quotesResult = fetchArray(quotePath)

        // A different account may have signed in while quotes were loading.
        if (!isOriginalSession()) return false

        if (quotesResult != null) {
            val (array, body) = quotesResult

            val rows = objects(array).map { quote ->
                ContentValues().apply {
                    put("remote_id", quote.optString("id"))
                    put("quote_code", quote.str("quote_code") ?: "")
                    put("first_name", quote.str("first_name") ?: "")
                    put("last_name", quote.str("last_name") ?: "")
                    put("email", quote.str("email") ?: "")
                    put("phone", quote.str("phone") ?: "")
                    put("branch", quote.str("branch") ?: "")
                    put("service", quote.str("service") ?: "")
                    put("message", quote.str("message") ?: "")
                    put("product_id", quote.str("product_id"))

                    put(
                        "created_at",
                        parseMillis(quote.str("created_at"))
                    )

                    put(
                        "status",
                        quote.str("status") ?: "Pending"
                    )

                    put("value", quote.str("value"))
                }
            }

            if (!isOriginalSession()) return false

            db.reconcile(
                "quote_requests",
                rows
            )

            if (!isOriginalSession()) return false

            if (changedSince("h_quotes", body)) {
                changed = true
            }
        }

        return isOriginalSession() && changed
    }
}
