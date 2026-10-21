package com.dua.ai.network

import org.json.JSONObject

/** Events of the opt-in `/api/chat` stream (see web/src/core/sse.mjs for the wire format). */
sealed interface ReplyEvent {
    data class Thinking(val delta: String) : ReplyEvent
    data class Text(val delta: String) : ReplyEvent
    /** Web sources (Brave before the text; provider-native search after it). The reply text also ends with a Sources footer. */
    data class Sources(val sources: List<com.dua.ai.model.ReplySource>) : ReplyEvent { val count: Int get() = sources.size }
    /** The server summarised the first [covered] sent turns into [summary] (Phase 2 long context). */
    data class Context(val summary: String, val covered: Int) : ReplyEvent
    data class Notice(val message: String) : ReplyEvent
    /** Transcripts the server made for stored audio files: storage path -> transcript. */
    data class Files(val transcripts: Map<String, String>) : ReplyEvent
    /** Code run [index]: the code (first event) or its output and outcome (second event). */
    data class Code(val index: Int, val language: String? = null, val code: String? = null, val output: String? = null, val outcome: String? = null) : ReplyEvent
    /** Build mode: the repository and every file read so far (each event replaces the previous one). */
    data class Build(val info: com.dua.ai.model.BuildInfo) : ReplyEvent
    /** Build mode: a change proposal for the owner to review (nothing was written to GitHub). */
    data class Proposal(val proposal: com.dua.ai.model.BuildProposal) : ReplyEvent
    /** Agent step update (Cloud Run): [step] id, user-facing [label], finished when [done]. */
    data class Progress(val step: String, val label: String, val done: Boolean) : ReplyEvent
    /** Dua Agent generate_image: stored image(s) plus short-lived signed links (path -> url) for the media cache. */
    data class Media(val media: List<com.dua.ai.imagine.GeneratedMedia>, val links: Map<String, String>) : ReplyEvent
    /** Dua Agent remember: a proposed memory note; the user reviews it before anything is saved. */
    data class Memory(val fact: String) : ReplyEvent
    /** Dua Agent write action waiting for the owner's Approve / Reject (nothing was done yet). */
    data class Approval(val approval: com.dua.ai.model.AgentApproval) : ReplyEvent
    data class Coding(val update: com.dua.ai.model.AgentCodingUpdate) : ReplyEvent
    data class Saved(val receipt: com.dua.ai.account.ReplySaved) : ReplyEvent
    data class Done(val truncated: Boolean) : ReplyEvent
    data class Failed(val message: String, val status: Int) : ReplyEvent
}

/**
 * Incremental SSE parser: feed one line at a time (without the newline). Returns an event when a
 * blank line completes one. Comment lines (heartbeats) and unknown event types are ignored.
 */
class ChatStreamParser {
    private val data = StringBuilder()

    fun line(line: String): ReplyEvent? {
        if (line.isEmpty()) return flush()
        if (line.startsWith(":")) return null
        val field = line.substringBefore(':')
        var value = if (line.contains(':')) line.substringAfter(':') else ""
        if (value.startsWith(" ")) value = value.substring(1)
        if (field == "data") {
            if (data.isNotEmpty()) data.append('\n')
            require(data.length + value.length <= MAX_EVENT_CHARS) { "Reply stream event is too large." }
            data.append(value)
        }
        return null
    }

    /** Completes a trailing event when the stream ends without a final blank line. */
    fun finish(): ReplyEvent? = flush()

    private fun flush(): ReplyEvent? {
        if (data.isEmpty()) return null
        val raw = data.toString()
        data.setLength(0)
        return decode(raw)
    }

    companion object {
        /** Build proposals carry whole files (server cap 1 MB of content plus capped diffs), so events can be a few MB. */
        const val MAX_EVENT_CHARS = 4_000_000
        const val MAX_AGENT_MEDIA = 4
        const val MAX_MEMORY_FACT = 200
        const val MEMORY_NOTICE = "Memory note suggested. Review it in Settings › Integrations before it is saved."

        private fun sources(json: JSONObject): List<com.dua.ai.model.ReplySource> {
            val array = json.optJSONArray("sources") ?: return emptyList()
            return (0 until minOf(array.length(), 20)).mapNotNull { array.optJSONObject(it) }.mapNotNull { item ->
                val url = com.dua.ai.setup.IntegrationCapabilities.safeUrl(item.optString("url")) ?: return@mapNotNull null
                val domain = item.optString("domain").takeIf { Regex("[A-Za-z0-9.-]{1,253}").matches(it) }
                com.dua.ai.model.ReplySource(item.optInt("id", 0), item.optString("title").take(200), url, domain)
            }
        }

        fun decode(raw: String): ReplyEvent? {
            val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
            return when (json.optString("type")) {
                "thinking" -> json.optString("delta").takeIf { it.isNotEmpty() }?.let { ReplyEvent.Thinking(it) }
                "text" -> json.optString("delta").takeIf { it.isNotEmpty() }?.let { ReplyEvent.Text(it) }
                "sources" -> ReplyEvent.Sources(sources(json))
                "context" -> {
                    val summary = json.optString("summary"); val covered = json.optInt("covered", 0)
                    if (summary.isNotBlank() && summary.length <= com.dua.ai.model.ChatSummary.MAX_CHARS && covered > 0) ReplyEvent.Context(summary, covered) else null
                }
                "notice" -> json.optString("message").takeIf { it.isNotBlank() }?.let { ReplyEvent.Notice(it.take(300)) }
                "files" -> json.optJSONArray("files")?.let { array ->
                    (0 until array.length()).mapNotNull { array.optJSONObject(it) }.mapNotNull { item ->
                        val path = item.optString("path"); val transcript = item.optString("transcript")
                        if (com.dua.ai.files.StoredFileTypes.PATH.matches(path) && transcript.isNotBlank()) path to transcript.take(com.dua.ai.files.FileRefCodec.MAX_TRANSCRIPT_CHARS) else null
                    }.toMap().takeIf { it.isNotEmpty() }?.let { ReplyEvent.Files(it) }
                }
                "code" -> {
                    val index = json.optInt("index", -1)
                    val code = if (json.has("code")) json.optString("code") else null
                    val output = if (json.has("output")) json.optString("output") else null
                    val outcome = if (json.has("outcome")) json.optString("outcome") else null
                    if (index !in 0 until com.dua.ai.model.CodeRuns.MAX_RUNS || (code.isNullOrBlank() && output == null && outcome == null)) null
                    else ReplyEvent.Code(index, if (json.has("language")) json.optString("language") else null, code, output, outcome)
                }
                "build" -> com.dua.ai.model.BuildInfos.fromJson(json)?.let { ReplyEvent.Build(it) }
                "proposal" -> com.dua.ai.model.BuildProposals.fromJson(json)?.let { ReplyEvent.Proposal(it) }
                "progress" -> {
                    val step = json.optString("step"); val label = json.optString("label").trim()
                    if (com.dua.ai.model.AgentProgress.validId(step) && label.isNotEmpty())
                        ReplyEvent.Progress(step, label.take(com.dua.ai.model.AgentProgress.MAX_LABEL), json.optString("state") == "done") else null
                }
                "media" -> runCatching { com.dua.ai.imagine.ImagineCodec.imageResult(json) }.getOrNull()
                    ?.let { ReplyEvent.Media(it.media.take(MAX_AGENT_MEDIA), it.links) }
                "memory" -> json.optString("fact").trim().replace(Regex("\\s+"), " ")
                    .takeIf { it.isNotEmpty() && it.length <= MAX_MEMORY_FACT }?.let { ReplyEvent.Memory(it) }
                "approval" -> com.dua.ai.model.AgentApprovals.fromEvent(json)?.let { ReplyEvent.Approval(it) }
                "coding" -> com.dua.ai.model.AgentCodingUpdate.fromEvent(json)?.let { ReplyEvent.Coding(it) }
                "saved" -> ReplyEvent.Saved(com.dua.ai.account.ReplyPersistenceCodec.receipt(json))
                "done" -> ReplyEvent.Done(json.optBoolean("truncated"))
                "error" -> ReplyEvent.Failed(json.optString("message").takeIf { it.isNotBlank() }?.take(300)
                    ?: "Reply stopped unexpectedly. Partial reply kept; retry.", json.optInt("status", 502))
                else -> null
            }
        }
    }
}

/** Running state of one streamed reply; [snapshot] is what the chat shows. */
class StreamAccumulator(private val startedAt: Long, private val clock: () -> Long = System::currentTimeMillis) {
    private val text = StringBuilder()
    private val thinking = StringBuilder()
    private var thinkingSeconds: Int? = null
    private var notice: String? = null
    private var codeRuns: List<com.dua.ai.model.CodeRun> = emptyList()
    private var build: com.dua.ai.model.BuildInfo? = null
    private var proposal: com.dua.ai.model.BuildProposal? = null
    private var progress: List<com.dua.ai.model.AgentStep> = emptyList()
    private var media: List<com.dua.ai.imagine.GeneratedMedia> = emptyList()
    private var approvals: List<com.dua.ai.model.AgentApproval> = emptyList()
    private var coding: com.dua.ai.model.AgentCoding? = null
    var finished = false
        private set

    /** Applies one event; cheap (no copies) so it can run per delta. Read [snapshot] when rendering. */
    fun apply(event: ReplyEvent) {
        when (event) {
            is ReplyEvent.Thinking -> if (thinking.length < com.dua.ai.model.ChatMessage.MAX_THINKING_CHARS)
                thinking.append(event.delta.take(com.dua.ai.model.ChatMessage.MAX_THINKING_CHARS - thinking.length))
            is ReplyEvent.Text -> {
                if (thinking.isNotEmpty() && thinkingSeconds == null) thinkingSeconds = elapsedSeconds()
                text.append(event.delta)
            }
            is ReplyEvent.Notice -> notice = event.message
            is ReplyEvent.Progress -> progress = com.dua.ai.model.AgentProgress.apply(progress, event.step, event.label, event.done)
            is ReplyEvent.Media -> media = (media + event.media).distinctBy { it.key }.take(ChatStreamParser.MAX_AGENT_MEDIA)
            is ReplyEvent.Memory -> if (notice == null) notice = ChatStreamParser.MEMORY_NOTICE
            is ReplyEvent.Approval -> approvals = com.dua.ai.model.AgentApprovals.merge(approvals, event.approval)
            is ReplyEvent.Build -> build = event.info
            is ReplyEvent.Proposal -> proposal = event.proposal
            is ReplyEvent.Code -> codeRuns = com.dua.ai.model.CodeRuns.merge(codeRuns, event.index, event.language, event.code, event.output, event.outcome)
            is ReplyEvent.Coding -> coding = event.update.apply(coding)
            is ReplyEvent.Saved -> thinkingSeconds = event.receipt.thinkingSeconds
            is ReplyEvent.Done -> {
                finished = true
                progress = com.dua.ai.model.AgentProgress.finish(progress)
                if (thinking.isNotEmpty() && thinkingSeconds == null) thinkingSeconds = elapsedSeconds()
                if (event.truncated) notice = "Reply reached the output limit and may be cut off."
            }
            is ReplyEvent.Sources, is ReplyEvent.Failed, is ReplyEvent.Files, is ReplyEvent.Context -> Unit
        }
    }

    /** Current reply. When [ended] (done, error or stop) thinking time is fixed even without answer text. */
    fun snapshot(ended: Boolean = finished): StreamSnapshot {
        val visibleThinking = thinking.toString().takeIf { it.isNotBlank() }
        return StreamSnapshot(text.toString(), visibleThinking,
            if (visibleThinking == null) null else thinkingSeconds ?: if (ended) elapsedSeconds() else null,
            notice, finished, codeRuns, build, proposal, progress, media, approvals, if (ended) coding?.ended() else coding)
    }

    private fun elapsedSeconds() = (((clock() - startedAt) + 500) / 1000).toInt().coerceIn(1, 86400)
}

data class StreamSnapshot(val text: String = "", val thinking: String? = null, val thinkingSeconds: Int? = null, val notice: String? = null, val finished: Boolean = false,
    val codeRuns: List<com.dua.ai.model.CodeRun> = emptyList(), val build: com.dua.ai.model.BuildInfo? = null,
    val proposal: com.dua.ai.model.BuildProposal? = null, val progress: List<com.dua.ai.model.AgentStep> = emptyList(),
    val media: List<com.dua.ai.imagine.GeneratedMedia> = emptyList(),
    val approvals: List<com.dua.ai.model.AgentApproval> = emptyList(),
    val coding: com.dua.ai.model.AgentCoding? = null)
