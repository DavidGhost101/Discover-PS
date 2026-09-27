package com.example.e2e

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the Firebase Emulator Suite over REST (host 10.0.2.2 from the Android emulator):
 * resets state, bootstraps the first administrator and reads the emails the Auth emulator
 * "sent" (verification / password reset links). Never used against a real project.
 */
object EmulatorBackend {
    const val HOST = "10.0.2.2"
    const val PROJECT = "demo-discovery-primary"
    private const val AUTH = "http://$HOST:9099"
    private const val FIRESTORE = "http://$HOST:8080"
    private const val KEY = "emulator-only-api-key"

    private fun request(method: String, url: String, body: JSONObject? = null, owner: Boolean = false): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        if (owner) connection.setRequestProperty("Authorization", "Bearer owner")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
        }
        val code = connection.responseCode
        val text = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.readText().orEmpty()
        check(code in 200..299) { "$method $url failed with $code: $text" }
        return text
    }

    fun reset() {
        request("DELETE", "$AUTH/emulator/v1/projects/$PROJECT/accounts")
        request("DELETE", "$FIRESTORE/emulator/v1/projects/$PROJECT/databases/(default)/documents")
    }

    fun createLogin(email: String, password: String): String {
        val response = request(
            "POST", "$AUTH/identitytoolkit.googleapis.com/v1/accounts:signUp?key=$KEY",
            JSONObject().put("email", email).put("password", password).put("returnSecureToken", true)
        )
        return JSONObject(response).getString("localId")
    }

    /** Writes /users/{uid} bypassing security rules (the first admin is bootstrapped by the project owner). */
    fun writeProfile(uid: String, email: String, name: String, role: String, active: Boolean) {
        fun str(v: String) = JSONObject().put("stringValue", v)
        val fields = JSONObject()
            .put("uid", str(uid)).put("email", str(email)).put("phoneNumber", str(""))
            .put("displayName", str(name)).put("role", str(role)).put("schoolId", str("discovery-primary"))
            .put("active", JSONObject().put("booleanValue", active))
            .put("createdAt", JSONObject().put("nullValue", JSONObject.NULL))
            .put("updatedAt", JSONObject().put("nullValue", JSONObject.NULL))
        val write = JSONObject().put(
            "update",
            JSONObject().put("name", "projects/$PROJECT/databases/(default)/documents/users/$uid").put("fields", fields)
        )
        request(
            "POST", "$FIRESTORE/v1/projects/$PROJECT/databases/(default)/documents:commit",
            JSONObject().put("writes", JSONArray().put(write)), owner = true
        )
    }

    fun markEmailVerified(uid: String) {
        request(
            "POST", "$AUTH/identitytoolkit.googleapis.com/v1/projects/$PROJECT/accounts:update",
            JSONObject().put("localId", uid).put("emailVerified", true), owner = true
        )
    }

    private fun latestCode(email: String, type: String): String {
        val codes = JSONObject(request("GET", "$AUTH/emulator/v1/projects/$PROJECT/oobCodes")).getJSONArray("oobCodes")
        var found: String? = null
        for (i in 0 until codes.length()) {
            val c = codes.getJSONObject(i)
            if (c.getString("email").equals(email, ignoreCase = true) && c.getString("requestType") == type) found = c.getString("oobCode")
        }
        return checkNotNull(found) { "No $type email was sent to $email" }
    }

    fun hasEmail(email: String, type: String): Boolean = runCatching { latestCode(email, type) }.isSuccess

    /** Completes the "set your password" link the app emailed to [email]. */
    fun completePasswordReset(email: String, newPassword: String) {
        request(
            "POST", "$AUTH/identitytoolkit.googleapis.com/v1/accounts:resetPassword?key=$KEY",
            JSONObject().put("oobCode", latestCode(email, "PASSWORD_RESET")).put("newPassword", newPassword)
        )
    }

    /** Opens the verification link the app emailed to [email]. */
    fun completeEmailVerification(email: String) {
        request(
            "POST", "$AUTH/identitytoolkit.googleapis.com/v1/accounts:update?key=$KEY",
            JSONObject().put("oobCode", latestCode(email, "VERIFY_EMAIL"))
        )
    }
}
