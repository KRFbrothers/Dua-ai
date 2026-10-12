package com.dua.ai.setup

import org.json.JSONObject
import java.net.URI

data class ProviderCapability(val name: String, val configured: Boolean, val model: String, val supportsAttachments: Boolean,
    val attachmentTypes: List<String> = IntegrationCapabilities.legacyAttachmentTypes(name, supportsAttachments),
    /** Shows a Thinking block in Reasoning mode (servers before Reasoning mode omit the field). */
    val supportsReasoning: Boolean = name in IntegrationCapabilities.REASONING_DEFAULT)
/** Run code (Phase 5): provider-hosted code execution. [providers] are the models that can run code. */
data class CodeExecutionConfig(val enabled: Boolean, val reason: String = "", val providers: Set<String> = emptySet()) {
    fun available(model: String) = enabled && model in providers
}
data class IntegrationGroup(val id: String, val title: String, val state: String, val requires: List<String>, val limitations: List<String>)
data class SearchResult(val title: String, val url: String, val snippet: String)
data class RepositoryResult(val name: String, val url: String, val description: String, val fullName: String = "", val private: Boolean = false)
/** The Build picker's repository list (owner's repositories from the read-only connector). */
data class BuildRepositories(val loading: Boolean = false, val repositories: List<RepositoryResult> = emptyList(), val error: String = "")
/** Build mode (Phase 5B): read-only repository chat. When not [enabled], [reason] says what setup is missing. */
/** Build pull requests (Phase 5C): whether the owner can turn a proposal into a pull request, and why not. */
data class BuildPrConfig(val enabled: Boolean, val reason: String = "", val draft: Boolean = true)
data class BuildModeConfig(val enabled: Boolean, val reason: String = "", val providers: Set<String> = setOf("Dua", "Gemini"), val maxFiles: Int = 40,
    val pr: BuildPrConfig? = null) {
    fun available(model: String) = enabled && model in providers
}
data class IntegrationState(val groups: List<IntegrationGroup> = emptyList(), val providers: List<ProviderCapability> = IntegrationCapabilities.fallbackProviders,
    val searchConfigured: Boolean = false, val busy: Boolean = false, val notice: String = "", val generatedVoiceConfigured: Boolean = false,
    val webSearchConfigured: Boolean = false, val pageReadingAvailable: Boolean = false,
    /** Server supports stored files (Supabase Storage, owner sign-in required). Older servers omit `files`. */
    val filesEnabled: Boolean = false, val filesMaxBytes: Long = 0,
    /** Providers that search natively (Phase 2); Search also works for others when Brave is configured. */
    val nativeSearch: Set<String> = emptySet(),
    /** X search toggle (Grok only) offered by the server. */
    val xSearchAvailable: Boolean = false,
    /** Server summarises older turns and accepts `context` (Phase 2 long context). */
    val longContext: Boolean = false,
    /** Imagine (Phase 3) offered by the server; null on older backends, which hides Imagine. */
    val imagine: com.dua.ai.imagine.ImagineConfig? = null,
    /** Voice v2 (Phase 4) catalog; null on older backends, which keeps the single AI-voice switch. */
    val voice: com.dua.ai.voice.VoiceConfig? = null,
    /** Run code (Phase 5); null on older backends, which hides the chip. */
    val codeExecution: CodeExecutionConfig? = null,
    /** Build mode (Phase 5B); null on older backends, where the Build sheet explains that a backend update is needed. */
    val build: BuildModeConfig? = null,
    val projectsEnabled: Boolean = false, val projectTasksEnabled: Boolean = false, val taskDueDatesEnabled: Boolean = false, val projectMemoryEnabled: Boolean = false,
    /** Older snapshot validators drop custom titles; wait for an explicit backend capability. */
    val chatRenameEnabled: Boolean = false, val chatDeletionEnabled: Boolean = false, val chatPinEnabled: Boolean = false,
    val serverRepliesEnabled: Boolean = false) {
    /** Search works for [model] natively or through Brave. */
    fun searchAvailable(model: String): Boolean = webSearchConfigured || model in nativeSearch
}

object IntegrationCapabilities {
    val MODELS = listOf("Dua", "ChatGPT", "Gemini", "Claude", "Grok", "Kimi")
    val REASONING_DEFAULT = setOf("Dua", "Gemini", "ChatGPT", "Grok")
    val GROUPS = listOf("providers", "search", "sync", "memory", "connectors", "computer", "voice", "account")
    val ATTACHMENT_TYPES = listOf("image/jpeg", "image/png", "image/webp", "application/pdf", "text/plain")
    val fallbackProviders = listOf("Dua", "Gemini").map { ProviderCapability(it, true, "Gemini", true, ATTACHMENT_TYPES) }
    fun legacyAttachmentTypes(name: String, supportsAttachments: Boolean): List<String> = when {
        !supportsAttachments -> emptyList()
        name == "Dua" || name == "Gemini" -> ATTACHMENT_TYPES
        else -> listOf("text/plain")
    }
    fun configuration(json: JSONObject, current: IntegrationState): IntegrationState {
        val web = json.optJSONObject("web")
        val files = json.optJSONObject("files")
        return current.copy(providers = providers(json),
            chatRenameEnabled = json.optJSONObject("history")?.opt("rename") == true,
            chatPinEnabled = json.optJSONObject("history")?.opt("pin") == true,
            chatDeletionEnabled = json.optJSONObject("history")?.opt("delete") == true,
            serverRepliesEnabled = json.optJSONObject("history")?.opt("persistReplies") == true,
            projectsEnabled = json.optJSONObject("projects")?.opt("enabled") == true,
            projectTasksEnabled = json.optJSONObject("projects")?.opt("enabled") == true && json.optJSONObject("projects")?.opt("tasks") == true,
            taskDueDatesEnabled = json.optJSONObject("projects")?.opt("enabled") == true && json.optJSONObject("projects")?.opt("tasks") == true && json.optJSONObject("projects")?.opt("dueDates") == true,
            projectMemoryEnabled = json.optJSONObject("projects")?.opt("enabled") == true && json.optJSONObject("projects")?.opt("memory") == true,
            filesEnabled = files?.opt("enabled") == true,
            filesMaxBytes = files?.optLong("maxBytes")?.coerceIn(0, 5L * 1024 * 1024 * 1024) ?: 0,
            webSearchConfigured = web?.opt("searchConfigured") == true,
            pageReadingAvailable = web?.opt("pageReadingAvailable") == true,
            nativeSearch = web?.optJSONArray("nativeSearch")?.let { names -> (0 until names.length()).mapNotNull { names.opt(it) as? String }.filter { it in MODELS }.toSet() } ?: emptySet(),
            xSearchAvailable = web?.opt("xSearch") == true,
            longContext = json.optJSONObject("context")?.opt("summaries") == true,
            imagine = com.dua.ai.imagine.ImagineCodec.config(json.optJSONObject("imagine")),
            codeExecution = codeExecution(json.optJSONObject("code")),
            build = build(json.optJSONObject("build")))
            .let { next -> val voice = com.dua.ai.voice.VoiceCodec.config(json.optJSONObject("voice"))
                // Phase 4 backends report reply-voice availability here, so it is known without opening Feature setup.
                next.copy(voice = voice, generatedVoiceConfigured = voice?.ttsEnabled ?: current.generatedVoiceConfigured) }
    }
    fun codeExecution(json: JSONObject?): CodeExecutionConfig? {
        if (json == null) return null
        val names = json.optJSONArray("providers")?.let { array -> (0 until array.length()).mapNotNull { array.opt(it) as? String }.filter { it in MODELS }.toSet() } ?: emptySet()
        return CodeExecutionConfig(json.opt("enabled") == true && names.isNotEmpty(), json.optString("reason").take(300), names)
    }
    fun build(json: JSONObject?): BuildModeConfig? {
        if (json == null) return null
        val names = json.optJSONArray("providers")?.let { array -> (0 until array.length()).mapNotNull { array.opt(it) as? String }.filter { it in MODELS }.toSet() } ?: emptySet()
        val pr = json.optJSONObject("pr")?.let { BuildPrConfig(it.opt("enabled") == true && it.optString("approval") == "owner", it.optString("reason").take(300), it.optBoolean("draft", true)) }
        return BuildModeConfig(json.opt("enabled") == true && names.isNotEmpty() && json.opt("readOnly") == true, json.optString("reason").take(300), names, json.optInt("maxFiles", 40).coerceIn(1, 100), pr)
    }
    fun providers(json: JSONObject): List<ProviderCapability> {
        val array = json.optJSONArray("providers") ?: return fallbackProviders.map { it.copy(configured = json.optBoolean("geminiConfigured", true), model = json.optString("model", "Gemini").take(100)) }
        return (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { item ->
            val name = item.optString("name")
            if (name !in MODELS) null else {
                val supportsAttachments = item.optBoolean("supportsAttachments")
                val types = when {
                    !supportsAttachments -> emptyList()
                    !item.has("attachmentTypes") -> legacyAttachmentTypes(name, true)
                    else -> item.optJSONArray("attachmentTypes")?.let { values ->
                        (0 until values.length()).mapNotNull { values.opt(it) as? String }.filter { it in ATTACHMENT_TYPES }.distinct()
                    } ?: emptyList()
                }
                ProviderCapability(name, item.optBoolean("configured"), item.optString("model").take(100), supportsAttachments, types,
                    if (item.has("reasoning")) item.optBoolean("reasoning") else name in REASONING_DEFAULT)
            }
        } }.distinctBy { it.name }
    }
    fun attachmentError(provider: ProviderCapability, mimeTypes: List<String>): String? {
        val supportedTypes = if (provider.supportsAttachments) provider.attachmentTypes else emptyList()
        if (mimeTypes.all { it in supportedTypes }) return null
        val extensions = supportedTypes.map { type -> when (type) {
            "image/jpeg" -> ".jpg/.jpeg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "application/pdf" -> ".pdf"
            "text/plain" -> ".txt"
            else -> type
        } }.joinToString(", ")
        return if (extensions.isEmpty()) "${provider.name} does not support file attachments. Remove selected files or choose another configured provider. Your selected files were kept."
            else "${provider.name} supports $extensions files. Remove unsupported files or choose another configured provider. Your selected files were kept." +
                if ("text/plain" in supportedTypes) " Text and code files (.csv, .md, .json, .kt, …) count as .txt." else ""
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
        return IntegrationState(groups, providers(json), json.optJSONObject("search")?.optBoolean("configured") == true).copy(generatedVoiceConfigured =
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
                val fullName = item.optString("fullName", item.optString("full_name")).takeIf { com.dua.ai.model.BuildInfos.validRepository(it) }
                    ?: URI(url).path.trim('/').takeIf { com.dua.ai.model.BuildInfos.validRepository(it) }.orEmpty()
                RepositoryResult(item.optString("name", item.optString("full_name")).take(200), url, item.optString("description").take(1000), fullName, item.optBoolean("private"))
            }
        } }
    }
}
