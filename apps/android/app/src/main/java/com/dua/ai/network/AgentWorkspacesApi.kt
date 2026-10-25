package com.dua.ai.network

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class AgentSavedWorkspace(val id: String, val base: String, val revision: Int, val updated: Long, val expires: Long)
sealed interface AgentWorkspacesResult {
    data class Ok(val items: List<AgentSavedWorkspace>) : AgentWorkspacesResult
    data object Disabled : AgentWorkspacesResult
    data class Failed(val message: String) : AgentWorkspacesResult
}

/** Owner-only metadata, never source contents. Redirects cannot forward session credentials. */
class AgentWorkspacesApi(
    private val baseUrl: String = com.dua.ai.BuildConfig.DUA_CHAT_BASE_URL,
    private val appToken: String = com.dua.ai.BuildConfig.DUA_APP_TOKEN,
    client: OkHttpClient = DuaApiClient.sharedClient,
) {
    private val http = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    suspend fun list(token: String?): AgentWorkspacesResult {
        val base = runCatching { URL(baseUrl.trimEnd('/') + "/") }.getOrNull()
        if (base == null || base.protocol != "https" || base.host.isBlank() || base.userInfo != null || base.query != null || base.ref != null)
            return AgentWorkspacesResult.Failed("Saved drafts need the Google Cloud engine.")
        if (token == null || token.length > 10_000 || !Regex("[A-Za-z0-9._-]+").matches(token))
            return AgentWorkspacesResult.Failed("Sign in from Profile to view saved drafts.")
        val builder = Request.Builder().url(URL(base, "api/agent/workspaces"))
            .header("Accept", "application/json").header("x-dua-native", "1").header("Authorization", "Bearer $token")
        if (appToken.isNotBlank()) builder.header("X-Dua-App-Token", appToken)
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(AgentWorkspacesResult.Failed("Couldn't reach Dua. Try again."))
                }
                override fun onResponse(call: Call, response: Response) {
                    if (response.code == 401) {
                        response.close()
                        if (continuation.isActive) continuation.resumeWithException(DuaApiException("Sign in to view saved drafts.", 401))
                        return
                    }
                    val result = response.use { r ->
                        runCatching {
                            val body = r.body?.source()?.let { source ->
                                source.request(65_537L)
                                require(source.buffer.size <= 65_536L)
                                JSONObject(source.readUtf8())
                            }
                            when {
                                r.code == 503 && body?.optString("code") == "not_configured" -> AgentWorkspacesResult.Disabled
                                r.code == 401 || r.code == 403 -> AgentWorkspacesResult.Failed("Sign in as the owner to view saved drafts.")
                                r.isSuccessful && body != null -> AgentWorkspacesResult.Ok(parse(body))
                                else -> AgentWorkspacesResult.Failed("Saved drafts are unavailable. Try again later.")
                            }
                        }.getOrElse { AgentWorkspacesResult.Failed("Couldn't read the saved drafts. Try again.") }
                    }
                    if (continuation.isActive) continuation.resume(result)
                }
            })
        }
    }
    private fun parse(json: JSONObject): List<AgentSavedWorkspace> {
        val rows = json.getJSONArray("checkpoints")
        require(rows.length() <= 20)
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val id = row.getString("id"); val base = row.getString("base_sha")
            val revision = row.getInt("revision")
            val updated = row.getDouble("updated"); val expires = row.getDouble("expires")
            require(Regex("[0-9a-f]{32}").matches(id) && Regex("[0-9a-f]{40}").matches(base) && revision > 0)
            require(updated.isFinite() && expires.isFinite() && updated > 0 && expires >= updated && expires < 1e11)
            AgentSavedWorkspace(id, base, revision, (updated * 1000).toLong(), (expires * 1000).toLong())
        }.also { require(it.map { w -> w.id }.distinct().size == it.size) }
    }
}
