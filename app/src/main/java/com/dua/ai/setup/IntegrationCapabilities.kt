package com.dua.ai.setup

import org.json.JSONObject
import java.net.URI

data class ProviderCapability(val name: String, val configured: Boolean, val model: String, val supportsAttachments: Boolean)
data class IntegrationGroup(val id: String, val title: String, val state: String, val requires: List<String>, val limitations: List<String>)
data class SearchResult(val title: String, val url: String, val snippet: String)
data class RepositoryResult(val name: String, val url: String, val description: String)
data class IntegrationState(val groups: List<IntegrationGroup> = emptyList(), val providers: List<ProviderCapability> = IntegrationCapabilities.fallbackProviders,
    val searchConfigured: Boolean = false, val googleConfigured: Boolean = false, val busy: Boolean = false, val notice: String = "", val generatedVoiceConfigured: Boolean = false)

object IntegrationCapabilities {
    val MODELS = listOf("Dua", "ChatGPT", "Gemini", "Claude", "Grok", "Kimi")
    val GROUPS = listOf("providers", "search", "sync", "memory", "connectors", "computer", "voice", "account")
    val fallbackProviders = listOf("Dua", "Gemini").map { ProviderCapability(it, true, "Gemini", true) }
    fun providers(json: JSONObject): List<ProviderCapability> {
        val array = json.optJSONArray("providers") ?: return fallbackProviders.map { it.copy(configured = json.optBoolean("geminiConfigured", true), model = json.optString("model", "Gemini").take(100)) }
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
            val name = item.optString("name")
            if (name !in MODELS) null else ProviderCapability(name, item.optBoolean("configured"), item.optString("model").take(100), item.optBoolean("supportsAttachments"))
        } }.distinctBy { it.name }
    }
    fun decode(json: JSONObject): IntegrationState {
        val array = json.getJSONArray("groups")
        val groups = (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
            val id = item.optString("id")
            if (id !in GROUPS) null else IntegrationGroup(id, item.optString("title").take(100),
                item.optString("state").takeIf { it in listOf("configured", "needs-configuration", "available", "partial") } ?: "needs-configuration",
                strings(item, "requires"), strings(item, "limitations"))
        } }.distinctBy { it.id }
        require(groups.map { it.id }.toSet() == GROUPS.toSet()) { "Setup status is incomplete. Refresh after deploying the backend." }
        return IntegrationState(groups, providers(json), json.optJSONObject("search")?.optBoolean("configured") == true,
            json.optJSONObject("account")?.optBoolean("googleConfigured") == true).copy(generatedVoiceConfigured =
            json.optJSONObject("advanced")?.optJSONObject("voice")?.optJSONObject("generatedAudio")?.opt("configured") == true &&
                json.optJSONObject("account")?.optBoolean("ownerRestricted") == true)
    }
    private fun strings(json: JSONObject, name: String): List<String> {
        val array = json.optJSONArray(name) ?: return emptyList()
        return (0 until array.length()).mapNotNull { array.opt(it) as? String }.take(20).map { it.take(500) }
    }
    fun safeUrl(value: String): String? = runCatching {
        val url = URI(value)
        require(url.scheme == "https" && !url.host.isNullOrBlank() && url.userInfo == null && value.length <= 2000)
        value
    }.getOrNull()
    fun search(json: JSONObject): List<SearchResult> {
        val array = json.getJSONArray("results")
        return (0 until array.length()).take(10).mapNotNull { array.optJSONObject(it)?.let { item ->
            safeUrl(item.optString("url"))?.let { url -> SearchResult(item.optString("title").take(300), url, item.optString("snippet").take(2000)) }
        } }
    }
    fun repositories(json: JSONObject): List<RepositoryResult> {
        val array = json.optJSONArray("repositories") ?: json.optJSONArray("results") ?: return emptyList()
        return (0 until array.length()).take(100).mapNotNull { array.optJSONObject(it)?.let { item ->
            safeUrl(item.optString("url", item.optString("html_url")))?.takeIf { URI(it).host == "github.com" }?.let { url ->
                RepositoryResult(item.optString("name", item.optString("full_name")).take(200), url, item.optString("description").take(1000))
            }
        } }
    }
}
