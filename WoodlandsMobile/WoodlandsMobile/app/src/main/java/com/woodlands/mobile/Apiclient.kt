package com.woodlands.mobile

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class ApiResult {
    data class Success(val body: String) : ApiResult()
    data class Failure(val code: Int, val message: String) : ApiResult()
    data class Offline(val cause: IOException) : ApiResult()
}

object ApiClient {

    /** JWT attached as "Authorization: Bearer ..." to every request except /api/auth */
    @Volatile
    var authToken: String? = null

    /** Called with the token that was rejected (HTTP 401) so the UI can sign the user out. */
    @Volatile
    var onUnauthorized: ((String) -> Unit)? = null

    /** Which server answered last: "Hosted" or "Local". */
    @Volatile
    var activeTarget: String = "Hosted"

    private const val HOSTED_BASE_URL =
        "https://insy7315-api-repository.onrender.com"

    private const val LOCAL_BASE_URL =
        "http://10.0.2.2:5000"

    const val WEB_BASE_URL =
        "https://insy7315-g3-2026-insy7315-2026-project-xfft.onrender.com"
    private val jsonType =
        "application/json; charset=utf-8".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val httpSlow = http.newBuilder()
        .readTimeout(70, TimeUnit.SECONDS)
        .build()

    fun get(path: String): ApiResult =
        call(path) { url ->
            Request.Builder()
                .url(url)
                .get()
                .build()
        }

    fun post(path: String, body: String): ApiResult =
        call(path) { url ->
            Request.Builder()
                .url(url)
                .post(body.toRequestBody(jsonType))
                .build()
        }

    fun put(path: String, body: String): ApiResult =
        call(path) { url ->
            Request.Builder()
                .url(url)
                .put(body.toRequestBody(jsonType))
                .build()
        }

    fun patch(path: String, body: String = "{}"): ApiResult =
        call(path) { url ->
            Request.Builder()
                .url(url)
                .patch(body.toRequestBody(jsonType))
                .build()
        }

    fun delete(path: String): ApiResult =
        call(path) { url ->
            Request.Builder()
                .url(url)
                .delete()
                .build()
        }

    private fun call(
        path: String,
        requestFactory: (String) -> Request
    ): ApiResult {

        val hostedUrl = HOSTED_BASE_URL + path

        try {
            return execute(requestFactory(hostedUrl)).also { activeTarget = "Hosted" }
        } catch (e: IOException) {
            // Railway could not be reached.
            // Only now do we try the local API.
        }

        val localUrl = LOCAL_BASE_URL + path

        return try {
            execute(requestFactory(localUrl)).also { activeTarget = "Local" }
        } catch (e: IOException) {
            ApiResult.Offline(e)
        }
    }

    private fun execute(original: Request): ApiResult {
        val client = if (original.method == "GET") http else httpSlow

        // Attach the JWT, but never to sign-in / register / handoff calls.
        val sentToken = authToken?.takeIf {
            it.isNotBlank() && !original.url.encodedPath.startsWith("/api/auth/")
        }
        val request = if (sentToken != null) {
            original.newBuilder().header("Authorization", "Bearer $sentToken").build()
        } else {
            original
        }

        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()

            if (response.isSuccessful) {
                ApiResult.Success(text)
            } else {
                if (response.code == 401 && sentToken != null) {
                    onUnauthorized?.invoke(sentToken)
                }
                ApiResult.Failure(
                    response.code,
                    errorMessage(text, response.code)
                )
            }
        }
    }

    private fun errorMessage(body: String, code: Int): String =
        try {
            JSONObject(body)
                .optString("error")
                .ifBlank { "Request failed ($code)" }
        } catch (e: Exception) {
            "Request failed ($code)"
        }
}
