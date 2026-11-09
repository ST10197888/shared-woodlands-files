package com.woodlands.mobile

import java.security.MessageDigest

/**
 * The website hands password storage to ASP.NET Core Identity. This local prototype has no
 * server, so account passwords are salted and SHA-256 hashed before being written to the
 * on-device SQLite database — never stored in plain text. Good enough for a coursework
 * prototype; a production build would still move auth behind a real API.
 */
object Security {
    private const val SALT = "woodlands-mobile-prototype::"

    fun hash(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest((SALT + password).toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun matches(password: String, hash: String): Boolean = hash(password) == hash
}
