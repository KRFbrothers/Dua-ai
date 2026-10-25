package com.dua.ai.imagine

import com.dua.ai.files.StoredFileTypes
import com.dua.ai.setup.IntegrationCapabilities
import org.json.JSONArray
import org.json.JSONObject

/**
 * A generated image or video. [path] points into the owner's private `dua-files` folder; it is null only
 * when a finished video was too large to store and [url] holds the provider's temporary link instead.
 */
data class GeneratedMedia(val path: String?, val mimeType: String, val size: Long = 0, val duration: Int? = null, val url: String? = null) {
    val isVideo: Boolean get() = mimeType == ImagineCodec.VIDEO
    /** Stable key for caches and Compose keys. */
    val key: String get() = path ?: url.orEmpty()
}

/** What an Imagine reply was asked for and how far it got. Stored with the chat on this device. */
data class ImagineInfo(
    val kind: String,
    val prompt: String,
    val aspectRatio: String = "auto",
    val count: Int = 1,
    val provider: String? = null,
    val model: String? = null,
    val seconds: Int? = null,
    val resolution: String? = null,
    val edit: Boolean = false,
    /** Signed server job token while a video is being made. */
    val jobId: String? = null,
    val status: String = DONE,
    val progress: Int = 0,
    val startedAt: Long = 0,
    val error: String? = null,
) {
    val isVideo: Boolean get() = kind == VIDEO
    val pending: Boolean get() = status == PENDING
    companion object {
        const val IMAGE = "image"; const val VIDEO = "video"
        const val PENDING = "pending"; const val DONE = "done"; const val FAILED = "failed"; const val EXPIRED = "expired"; const val CANCELLED = "cancelled"
        val STATUSES = setOf(PENDING, DONE, FAILED, EXPIRED, CANCELLED)
    }
}

/** Composer choices for the next Imagine request (session only, like Reasoning and Search). */
data class ImagineOptions(val kind: String = ImagineInfo.IMAGE, val aspectRatio: String = "1:1", val count: Int = 1,
    val duration: Int = 5, val resolution: String = "720p", val provider: String? = null)

data class ImagineProvider(val id: String, val name: String, val model: String)

/** The `imagine` block of /api/config. Older backends omit it, which hides Imagine. */
data class ImagineConfig(
    val imageEnabled: Boolean = false, val imageReason: String = "",
    val providers: List<ImagineProvider> = emptyList(), val defaultProvider: String = "xai",
    val imageAspects: List<String> = ImagineCodec.ASPECTS, val maxImages: Int = 4, val maxSourceImages: Int = 3, val dailyImages: Int = 0,
    val videoEnabled: Boolean = false, val videoReason: String = "", val videoModel: String = "",
    val videoAspects: List<String> = ImagineCodec.ASPECTS, val durations: List<Int> = listOf(5, 8, 10, 15), val defaultSeconds: Int = 5,
    val resolutions: List<String> = listOf("480p", "720p"), val defaultResolution: String = "720p", val dailyVideoSeconds: Int = 0,
    val pollAfterSeconds: Int = 5, val maxSourceBytes: Long = 15L * 1024 * 1024,
) {
    val enabled: Boolean get() = imageEnabled || videoEnabled
    fun providerName(id: String?): String = providers.firstOrNull { it.id == id }?.name ?: providers.firstOrNull { it.id == defaultProvider }?.name ?: "Grok Imagine"
    /** Options adjusted to what the server currently offers. */
    fun normalize(options: ImagineOptions): ImagineOptions {
        val kind = when { options.kind == ImagineInfo.VIDEO && videoEnabled -> ImagineInfo.VIDEO; imageEnabled -> ImagineInfo.IMAGE; videoEnabled -> ImagineInfo.VIDEO; else -> options.kind }
        val aspects = if (kind == ImagineInfo.VIDEO) videoAspects else imageAspects
        return options.copy(kind = kind,
            aspectRatio = options.aspectRatio.takeIf { it in aspects } ?: aspects.firstOrNull { it != "auto" } ?: "auto",
            count = options.count.coerceIn(1, maxImages.coerceAtLeast(1)),
            duration = options.duration.takeIf { it in durations } ?: defaultSeconds.takeIf { it in durations } ?: durations.firstOrNull() ?: 5,
            resolution = options.resolution.takeIf { it in resolutions } ?: defaultResolution.takeIf { it in resolutions } ?: resolutions.firstOrNull() ?: "480p",
            provider = options.provider?.takeIf { id -> providers.any { it.id == id } })
    }
}

data class ImagineUsage(val images: Int, val imageLimit: Int, val videoSeconds: Int, val videoLimit: Int, val resetAt: Long)

/** Parsed POST /api/imagine/image response. */
data class ImageResult(val media: List<GeneratedMedia>, val provider: String, val model: String, val edit: Boolean, val notice: String?,
    /** Short-lived signed links (path to URL) so results show without another round trip. */
    val links: Map<String, String> = emptyMap())
/** Parsed POST /api/imagine/video response. */
data class VideoJob(val jobId: String, val seconds: Int, val resolution: String, val aspectRatio: String, val model: String, val pollAfterSeconds: Int)
/** Parsed POST /api/imagine/status response. */
data class VideoStatus(val status: String, val progress: Int = 0, val media: GeneratedMedia? = null, val error: String? = null, val notice: String? = null, val pollAfterSeconds: Int = 5,
    val link: String? = null)

object ImagineCodec {
    const val VIDEO = "video/mp4"
    val IMAGE_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    val ASPECTS = listOf("auto", "1:1", "3:4", "4:3", "9:16", "16:9")
    /** Same limit as the server: four results per message. */
    const val MAX_MEDIA = 4
    const val MAX_PROMPT = 4000
    /** Jobs older than this are expired on the server too (signed job tokens last 48 hours). */
    const val JOB_MAX_AGE_MS = 48L * 60 * 60 * 1000

    private fun mediaType(value: String) = value in IMAGE_TYPES || value == VIDEO
    private fun strings(array: JSONArray?, allowed: Collection<String>? = null): List<String> =
        if (array == null) emptyList() else (0 until array.length()).mapNotNull { array.opt(it) as? String }.filter { allowed == null || it in allowed }.distinct()
    private fun ints(array: JSONArray?): List<Int> = if (array == null) emptyList() else (0 until array.length()).mapNotNull { (array.opt(it) as? Number)?.toInt() }.filter { it in 1..60 }.distinct()

    /** Reads `imagine` from /api/config; null when the backend predates Imagine. */
    fun config(json: JSONObject?): ImagineConfig? {
        if (json == null) return null
        val image = json.optJSONObject("image") ?: JSONObject()
        val video = json.optJSONObject("video") ?: JSONObject()
        val providers = image.optJSONArray("providers")?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) }
            .mapNotNull { item -> item.optString("id").takeIf { it in setOf("xai", "gemini") }?.let { ImagineProvider(it, item.optString("name", it).take(60), item.optString("model").take(100)) } } } ?: emptyList()
        val durations = ints(video.optJSONArray("durations")).filter { it <= 15 }.ifEmpty { listOf(5, 8, 10, 15) }
        val resolutions = strings(video.optJSONArray("resolutions"), listOf("480p", "720p", "1080p")).ifEmpty { listOf("480p", "720p") }
        return ImagineConfig(
            imageEnabled = image.opt("enabled") == true && providers.isNotEmpty(), imageReason = image.optString("reason").take(300),
            providers = providers, defaultProvider = image.optString("defaultProvider").takeIf { id -> providers.any { it.id == id } } ?: providers.firstOrNull()?.id ?: "xai",
            imageAspects = strings(image.optJSONArray("aspectRatios"), ASPECTS).ifEmpty { ASPECTS },
            maxImages = image.optInt("maxImages", 4).coerceIn(1, MAX_MEDIA), maxSourceImages = image.optInt("maxSourceImages", 3).coerceIn(1, 5),
            dailyImages = image.optInt("dailyLimit").coerceAtLeast(0),
            videoEnabled = video.opt("enabled") == true, videoReason = video.optString("reason").take(300), videoModel = video.optString("model").take(100),
            videoAspects = strings(video.optJSONArray("aspectRatios"), ASPECTS).ifEmpty { ASPECTS },
            durations = durations, defaultSeconds = video.optInt("defaultSeconds", durations.first()).takeIf { it in durations } ?: durations.first(),
            resolutions = resolutions, defaultResolution = video.optString("defaultResolution").takeIf { it in resolutions } ?: resolutions.last(),
            dailyVideoSeconds = video.optInt("dailyLimit").coerceAtLeast(0), pollAfterSeconds = video.optInt("pollAfterSeconds", 5).coerceIn(2, 60),
            maxSourceBytes = json.optLong("maxSourceBytes", 15L * 1024 * 1024).coerceIn(1L, 50L * 1024 * 1024))
    }

    fun imageBody(prompt: String, options: ImagineOptions, sourcePaths: List<String>): JSONObject = JSONObject()
        .put("prompt", prompt.take(MAX_PROMPT)).put("n", options.count).put("aspectRatio", options.aspectRatio)
        .apply { options.provider?.let { put("provider", it) }; if (sourcePaths.isNotEmpty()) put("sourcePaths", JSONArray(sourcePaths)) }

    fun videoBody(prompt: String, options: ImagineOptions, sourcePath: String?): JSONObject = JSONObject()
        .put("duration", options.duration).put("resolution", options.resolution)
        .apply {
            if (prompt.isNotBlank()) put("prompt", prompt.take(MAX_PROMPT))
            // With a source image, "auto" keeps the photo's own shape (xAI stretches it otherwise).
            if (sourcePath != null) put("sourcePath", sourcePath)
            put("aspectRatio", options.aspectRatio)
        }

    private fun media(item: JSONObject?, requirePath: Boolean): GeneratedMedia? {
        if (item == null) return null
        val path = item.optString("path").takeIf { item.has("path") && StoredFileTypes.PATH.matches(it) }
        val mime = item.optString("mimeType")
        val url = item.optString("url").takeIf { item.has("url") }?.let(IntegrationCapabilities::safeUrl)
        if (!mediaType(mime) || (path == null && (requirePath || url == null))) return null
        return GeneratedMedia(path, mime, item.optLong("size").coerceAtLeast(0),
            if (item.has("duration")) item.optInt("duration").takeIf { it in 0..60 } else null, if (path == null) url else null)
    }

    fun imageResult(json: JSONObject): ImageResult {
        val items = json.optJSONArray("images") ?: JSONArray()
        val media = (0 until minOf(items.length(), MAX_MEDIA)).mapNotNull { media(items.optJSONObject(it), requirePath = true) }.filter { !it.isVideo }
        require(media.isNotEmpty()) { "No images came back. Try again." }
        val links = (0 until minOf(items.length(), MAX_MEDIA)).mapNotNull { items.optJSONObject(it) }.mapNotNull { item ->
            val path = item.optString("path"); val url = IntegrationCapabilities.safeUrl(item.optString("url"))
            if (url != null && media.any { it.path == path }) path to url else null }.toMap()
        return ImageResult(media, json.optString("provider"), json.optString("model").take(100), json.optBoolean("edit"), json.optString("notice").takeIf { it.isNotBlank() }?.take(500), links)
    }

    fun videoJob(json: JSONObject): VideoJob {
        val jobId = json.optString("jobId")
        require(jobId.length in 20..2000 && jobId.matches(Regex("[A-Za-z0-9_.-]+"))) { "The server returned an invalid video job." }
        return VideoJob(jobId, json.optInt("seconds", 5), json.optString("resolution", "720p"), json.optString("aspectRatio", "auto"),
            json.optString("model").take(100), json.optInt("pollAfterSeconds", 5).coerceIn(2, 60))
    }

    fun videoStatus(json: JSONObject): VideoStatus {
        val status = json.optString("status")
        require(status in setOf(ImagineInfo.PENDING, ImagineInfo.DONE, ImagineInfo.FAILED, ImagineInfo.EXPIRED)) { "Unexpected video status." }
        val video = if (status == ImagineInfo.DONE) media(json.optJSONObject("video"), requirePath = false)?.takeIf { it.isVideo } ?: error("The finished video could not be read.") else null
        return VideoStatus(status, json.optInt("progress").coerceIn(0, 100), video, json.optString("error").takeIf { it.isNotBlank() }?.take(500),
            json.optString("notice").takeIf { it.isNotBlank() }?.take(500), json.optInt("pollAfterSeconds", 5).coerceIn(2, 60),
            json.optJSONObject("video")?.optString("url")?.let(IntegrationCapabilities::safeUrl))
    }

    fun usage(json: JSONObject): ImagineUsage {
        val images = json.optJSONObject("images"); val video = json.optJSONObject("videoSeconds")
        return ImagineUsage(images?.optInt("used") ?: 0, images?.optInt("limit") ?: 0, video?.optInt("used") ?: 0, video?.optInt("limit") ?: 0, json.optLong("resetAt"))
    }

    // ---- Persistence ----

    /** The server snapshot and phone cache keep temporary provider links; exports keep stored references only. */
    fun mediaArray(items: List<GeneratedMedia>, local: Boolean): JSONArray = JSONArray().apply {
        items.take(MAX_MEDIA).forEach { item ->
            if (item.path == null && !local) return@forEach
            put(JSONObject().apply {
                item.path?.let { put("path", it) }
                put("mimeType", item.mimeType)
                if (item.size > 0) put("size", item.size)
                item.duration?.takeIf { it in 0..60 }?.let { put("duration", it) }
                if (local && item.path == null) item.url?.let { put("url", it) }
            })
        }
    }

    /** Lenient decode: invalid entries are dropped instead of failing the chat. */
    fun mediaFrom(array: JSONArray?, local: Boolean): List<GeneratedMedia> =
        if (array == null) emptyList() else (0 until minOf(array.length(), MAX_MEDIA)).mapNotNull { media(array.optJSONObject(it), requirePath = !local) }

    fun infoJson(info: ImagineInfo): JSONObject = JSONObject().put("kind", info.kind).put("prompt", info.prompt.take(MAX_PROMPT))
        .put("aspectRatio", info.aspectRatio).put("count", info.count).put("status", info.status).put("progress", info.progress).put("startedAt", info.startedAt)
        .put("edit", info.edit).apply {
            info.provider?.let { put("provider", it.take(60)) }; info.model?.let { put("model", it.take(100)) }
            info.seconds?.let { put("seconds", it) }; info.resolution?.let { put("resolution", it) }
            info.jobId?.let { put("jobId", it) }; info.error?.let { put("error", it.take(500)) }
        }

    fun infoFrom(json: JSONObject?): ImagineInfo? {
        if (json == null) return null
        val kind = json.optString("kind").takeIf { it == ImagineInfo.IMAGE || it == ImagineInfo.VIDEO } ?: return null
        val status = json.optString("status").takeIf { it in ImagineInfo.STATUSES } ?: ImagineInfo.DONE
        val jobId = json.optString("jobId").takeIf { json.has("jobId") && it.length in 20..2000 }
        return ImagineInfo(kind, json.optString("prompt").take(MAX_PROMPT), json.optString("aspectRatio", "auto").take(10), json.optInt("count", 1).coerceIn(1, MAX_MEDIA),
            json.optString("provider").takeIf { json.has("provider") }, json.optString("model").takeIf { json.has("model") },
            if (json.has("seconds")) json.optInt("seconds").coerceIn(1, 60) else null, json.optString("resolution").takeIf { json.has("resolution") },
            json.optBoolean("edit"), jobId,
            // A pending job without its token can never finish: show it as failed.
            if (status == ImagineInfo.PENDING && jobId == null) ImagineInfo.FAILED else status,
            json.optInt("progress").coerceIn(0, 100), json.optLong("startedAt").coerceAtLeast(0), json.optString("error").takeIf { json.has("error") }?.take(500))
    }

    /** User-facing label for an aspect ratio. */
    fun aspectLabel(value: String) = if (value == "auto") "Auto" else value

    /** Text kept on the model turn: readable in exports, cloud snapshots and by the chat model as history. */
    fun replyText(info: ImagineInfo, media: List<GeneratedMedia>): String = when {
        info.isVideo && info.status == ImagineInfo.DONE -> "Video (${info.seconds ?: media.firstOrNull()?.duration ?: 0} s, ${info.resolution ?: ""}) created with ${info.provider ?: "Grok Imagine"}: ${info.prompt}".replace(", )", ")")
        info.isVideo && info.pending -> "Creating a ${info.seconds ?: 0}-second video: ${info.prompt}"
        info.isVideo -> "Video not created: ${info.prompt}"
        info.status == ImagineInfo.DONE -> "${if (media.size == 1) "Image" else "${media.size} images"} ${if (info.edit) "edited" else "created"} with ${info.provider ?: "Grok Imagine"}: ${info.prompt}"
        else -> "Image not created: ${info.prompt}"
    }

    /** True once a pending job is older than the server keeps its token. */
    fun stale(info: ImagineInfo, now: Long): Boolean = info.pending && info.startedAt > 0 && now - info.startedAt > JOB_MAX_AGE_MS

    /** Delay before the next status poll: the server's hint, slower after network failures (max 60 s). */
    fun pollDelayMs(pollAfterSeconds: Int, failures: Int): Long = (pollAfterSeconds.coerceIn(2, 60) * 1000L * (1L shl failures.coerceIn(0, 4))).coerceAtMost(60_000L)
}
