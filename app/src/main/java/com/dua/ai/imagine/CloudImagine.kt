package com.dua.ai.imagine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Imagine on the Google Cloud engine (Vertex AI Gemini image model). Offered only when the engine's GET / says
 * `"imagine": {"image": true}`; otherwise the app keeps using the Node API (dua-api). One image per request, no photo edits.
 */
data class CloudImagine(val model: String, val dailyLimit: Int, val aspects: List<String>) {
    companion object {
        const val PROVIDER = "google-cloud"
        const val NAME = "Nano Banana (Google Cloud)"

        /** The engine's `imagine` block, or null when absent, off or malformed. */
        fun parse(body: String): CloudImagine? = runCatching {
            val block = JSONObject(body).optJSONObject("imagine") ?: return@runCatching null
            if (block.opt("image") != true) return@runCatching null
            val aspects = block.optJSONArray("aspectRatios")?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String } }
                ?.filter { it in ImagineCodec.ASPECTS }.orEmpty().ifEmpty { ImagineCodec.ASPECTS }
            CloudImagine(block.optString("model").take(100), block.optInt("dailyLimit", 0).coerceAtLeast(0), aspects)
        }.getOrNull()

        /**
         * Imagine settings with Google Cloud added as the default image provider (the Node API's providers stay for photo edits
         * and video). Without a Node API Imagine config, Cloud Run alone enables text-to-image.
         */
        fun merge(base: ImagineConfig?, cloud: CloudImagine?): ImagineConfig? {
            if (cloud == null) return base
            val config = base ?: ImagineConfig(imageAspects = cloud.aspects)
            val providers = listOf(ImagineProvider(PROVIDER, NAME, cloud.model)) + config.providers.filterNot { it.id == PROVIDER }
            return config.copy(imageEnabled = true, imageReason = "", providers = providers, defaultProvider = PROVIDER,
                dailyImages = if (config.imageEnabled) config.dailyImages else cloud.dailyLimit)
        }

        /** Google Cloud makes new images only; edits (source photos) and other chosen providers go to the Node API. */
        fun routes(cloud: CloudImagine?, options: ImagineOptions, sources: Int): Boolean =
            cloud != null && options.kind == ImagineInfo.IMAGE && sources == 0 && (options.provider == null || options.provider == PROVIDER)

        private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()

        suspend fun fetch(baseUrl: String, appToken: String): CloudImagine? = withContext(Dispatchers.IO) {
            runCatching {
                val base = URL(baseUrl.trimEnd('/') + "/")
                if (base.protocol != "https") return@runCatching null
                val builder = Request.Builder().url(base).get().header("Accept", "application/json")
                if (appToken.isNotBlank()) builder.header("X-Dua-App-Token", appToken)
                client.newCall(builder.build()).execute().use { response ->
                    if (response.code !in 200..299) return@use null
                    val source = response.body?.source() ?: return@use null
                    source.request(65537)
                    if (source.buffer.size > 65536) return@use null
                    parse(source.readUtf8())
                }
            }.getOrNull()
        }
    }
}
