package com.dua.ai.ui.chat

import android.graphics.BitmapFactory
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Site icons for source chips, fetched straight from the source site (`https://<domain>/favicon.ico`),
 * so no third-party icon service learns which sources you read. Cached in memory for the session;
 * failures fall back to a letter.
 */
object Favicons {
    private const val MAX_BYTES = 100 * 1024
    private val DOMAIN = Regex("^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
    private val cache = object : LinkedHashMap<String, ImageBitmap?>(64, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > 96
    }
    private val client by lazy {
        OkHttpClient.Builder().followRedirects(true).followSslRedirects(false)
            .connectTimeout(4, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(8, TimeUnit.SECONDS).build()
    }

    fun validDomain(domain: String) = DOMAIN.matches(domain.lowercase())

    suspend fun load(domain: String): ImageBitmap? {
        val key = domain.lowercase()
        if (!validDomain(key)) return null
        synchronized(cache) { if (cache.containsKey(key)) return cache[key] }
        val icon = withContext(Dispatchers.IO) {
            runCatching {
                client.newCall(Request.Builder().url("https://$key/favicon.ico").header("Accept", "image/*").build()).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null || body.contentLength() > MAX_BYTES) return@use null
                    val bytes = body.source().let { source -> source.request(MAX_BYTES + 1L); source.buffer.snapshot().toByteArray() }
                    if (bytes.size > MAX_BYTES) null else BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                }
            }.getOrNull()
        }
        synchronized(cache) { cache[key] = icon }
        return icon
    }
}

/** Icon loader for source chips; screenshot tests and previews provide null (letters only, no network). */
val LocalFaviconLoader = staticCompositionLocalOf<(suspend (String) -> ImageBitmap?)?> { Favicons::load }
