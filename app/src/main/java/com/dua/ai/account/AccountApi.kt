package com.dua.ai.account

import com.dua.ai.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.net.URL

class AccountApi(private val baseUrl: String = BuildConfig.DUA_API_BASE_URL, private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).connectTimeout(10, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()) {
    suspend fun request(path: String, method: String = "GET", body: JSONObject? = null, token: String? = null): JSONObject {
        val base = URL(baseUrl.trimEnd('/') + "/"); require(base.protocol == "https") { "Account backend requires HTTPS." }
        val builder = Request.Builder().url(URL(base, path.removePrefix("/"))).header("Accept", "application/json").header("x-dua-native", "1")
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (method != "GET") builder.method(method, (body ?: JSONObject()).toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.build()); continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) { continuation.resumeWith(Result.failure(error)) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        require(it.header("Content-Type").orEmpty().contains("application/json", true)) { "Account backend unavailable." }
                        val buffer = ByteArrayOutputStream(); it.body?.byteStream()?.use { stream -> val chunk = ByteArray(8192); while (true) { val count = stream.read(chunk); if (count < 0) break; require(buffer.size() + count <= 5 * 1024 * 1024) { "Account response too large." }; buffer.write(chunk, 0, count) } }
                        val json = JSONObject(buffer.toString("UTF-8")); if (!it.isSuccessful) throw AccountException(it.code, json.optString("error", "Account request failed.")); json
                    } }; continuation.resumeWith(result)
                }
            })
        }
    }
}
interface SessionPersistence { fun read(): AccountSession?; fun write(value: AccountSession?) }
class AccountException(val status: Int, message: String) : Exception(message)
data class AccountUser(val id: String, val email: String, val displayName: String, val avatarUrl: String = "", val phoneNumber: String = "")
/** Only https profile photos (Google's googleusercontent.com, or what the backend reports) are loaded; anything else is dropped. */
fun safeAvatarUrl(value: String?): String = value?.trim()?.takeIf { it.length <= 2048 && it.startsWith("https://") && !it.any(Char::isWhitespace) }.orEmpty()
/** Last successful login method is a local UI hint, never an authorization claim. */
enum class AccountProvider(val wire: String) { Google("google"), Email("email");
    companion object { fun fromWire(value: String): AccountProvider? = entries.firstOrNull { it.wire == value } }
}
data class AccountSession(val user: AccountUser, val accessToken: String, val refreshToken: String, val expiresAt: Long,
    val provider: AccountProvider? = null)
object AccountSessionCodec {
    fun user(json: JSONObject) = AccountUser(json.getString("id").also { require(it.matches(Regex("[a-fA-F0-9-]{36}"))) }, json.getString("email"), json.optString("displayName"), safeAvatarUrl(json.optString("avatarUrl")), PhoneNumbers.normalize(json.optString("phoneNumber")).orEmpty())
    fun decode(json: JSONObject): AccountSession {
        val tokens = json.getJSONObject("session")
        val access = tokens.getString("accessToken"); val refresh = tokens.getString("refreshToken")
        require(access.length <= 10000 && refresh.length <= 10000 && access.matches(Regex("[A-Za-z0-9._-]+")) && refresh.matches(Regex("[A-Za-z0-9._-]+")))
        return AccountSession(user(json.getJSONObject("user")), access, refresh, tokens.getLong("expiresAt"), AccountProvider.fromWire(json.optString("loginProvider")))
    }
    fun encode(value: AccountSession) = JSONObject().put("user", JSONObject().put("id", value.user.id).put("email", value.user.email).put("displayName", value.user.displayName).put("avatarUrl", value.user.avatarUrl).put("phoneNumber", value.user.phoneNumber))
        .apply { value.provider?.let { put("loginProvider", it.wire) } }
        .put("session", JSONObject().put("accessToken", value.accessToken).put("refreshToken", value.refreshToken).put("expiresAt", value.expiresAt))
}
