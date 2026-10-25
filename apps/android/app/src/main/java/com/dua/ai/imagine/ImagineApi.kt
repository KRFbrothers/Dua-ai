package com.dua.ai.imagine

import com.dua.ai.BuildConfig
import com.dua.ai.account.AccountApi
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * /api/imagine on the Dua backend (owner sign-in required; provider keys stay on the server).
 * Image requests can take a while (several images, stored before replying), so this client waits
 * longer than account calls. Video uses a job: submit returns at once, then [status] is polled.
 */
class ImagineApi(baseUrl: String = BuildConfig.DUA_API_BASE_URL, client: OkHttpClient = longClient, appToken: String = "") {
    private val api = AccountApi(baseUrl, client, appToken)

    suspend fun image(prompt: String, options: ImagineOptions, sourcePaths: List<String>, token: String): ImageResult =
        ImagineCodec.imageResult(api.request("/api/imagine/image", "POST", ImagineCodec.imageBody(prompt, options, sourcePaths), token))

    suspend fun video(prompt: String, options: ImagineOptions, sourcePath: String?, token: String): VideoJob =
        ImagineCodec.videoJob(api.request("/api/imagine/video", "POST", ImagineCodec.videoBody(prompt, options, sourcePath), token))

    suspend fun status(jobId: String, token: String): VideoStatus =
        ImagineCodec.videoStatus(api.request("/api/imagine/status", "POST", JSONObject().put("jobId", jobId), token))

    suspend fun usage(token: String): ImagineUsage = ImagineCodec.usage(api.request("/api/imagine/usage", token = token))

    companion object {
        val longClient: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS).writeTimeout(30, TimeUnit.SECONDS).readTimeout(180, TimeUnit.SECONDS).build()
    }
}
