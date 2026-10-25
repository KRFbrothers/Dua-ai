package com.dua.ai.backup.drive

import com.dua.ai.backup.BackupCipher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

data class DriveBackupFile(val id: String, val name: String, val size: Long, val createdAt: Long, val md5: String)

interface DriveBackupRemote {
    suspend fun email(): String
    suspend fun backups(): List<DriveBackupFile>
    suspend fun recoverInterruptedUploads(): List<DriveBackupFile> = emptyList()
    suspend fun upload(file: File, onProgress: (Int) -> Unit): DriveBackupFile
    suspend fun retainThree(uploaded: DriveBackupFile)
    suspend fun download(selected: DriveBackupFile, target: File)
}

/** Small REST surface. Redirects are disabled so bearer tokens and resumable sessions stay on the Drive origin. */
class DriveBackupApi(private val token: String, private val owner: String, private val guard: () -> Unit,
    client: OkHttpClient = httpClient, baseUrl: String = "https://www.googleapis.com/", private val onUnauthorized: () -> Unit = {}) : DriveBackupRemote {
    private val client = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    private val origin = baseUrl.toHttpUrl().also { require(it.isHttps && it.username.isEmpty() && it.password.isEmpty()) }
    private fun url(path: String) = origin.resolve(path)!!.newBuilder()
    private fun request(url: HttpUrl): Request.Builder {
        guard()
        require(url.scheme == origin.scheme && url.host == origin.host && url.port == origin.port) { "Invalid Drive upload origin." }
        return Request.Builder().url(url).header("Authorization", "Bearer $token").header("Accept", "application/json")
    }
    override suspend fun email(): String = json(request(url("drive/v3/about").addQueryParameter("fields", "user(emailAddress)").build()).get().build())
        .getJSONObject("user").getString("emailAddress")

    override suspend fun backups(): List<DriveBackupFile> = entries()
        .filter { it.optJSONObject("appProperties")?.optString("duaState") == "complete" }.map(::parse)
        .sortedWith(compareByDescending<DriveBackupFile> { it.createdAt }.thenByDescending { it.id })

    override suspend fun recoverInterruptedUploads(): List<DriveBackupFile> {
        val recovered = mutableListOf<DriveBackupFile>()
        for (item in entries()) {
            val properties = item.optJSONObject("appProperties") ?: continue
            if (properties.optString("duaState") != "pending") continue
            // Drive may have received the final chunk before the process lost its acknowledgement.
            // Promote only bytes whose final Drive size/checksum match this app's pre-upload metadata.
            val complete = JSONObject(item.toString()).apply { getJSONObject("appProperties").put("duaState", "complete") }
            val verified = runCatching { parse(complete) }.getOrNull() ?: continue
            val marked = json(request(url("drive/v3/files/${verified.id}").addQueryParameter("fields", FIELDS).build())
                .patch(JSONObject().put("appProperties", complete.getJSONObject("appProperties")).toString().toRequestBody(JSON)).build())
            recovered += parse(marked).also { check(it.id == verified.id && it.md5 == verified.md5 && it.size == verified.size) }
        }
        return recovered
    }

    private suspend fun entries(): List<JSONObject> {
        require(owner.matches(Regex("[0-9a-f-]{36}")))
        val found = mutableListOf<JSONObject>()
        var page: String? = null
        val seenPages = mutableSetOf<String>()
        do {
            val query = url("drive/v3/files").addQueryParameter("spaces", "appDataFolder")
                .addQueryParameter("q", "trashed = false and appProperties has { key='duaType' and value='$MARKER' } and appProperties has { key='duaOwner' and value='$owner' }")
                .addQueryParameter("fields", "nextPageToken,files($FIELDS)").addQueryParameter("pageSize", "100").addQueryParameter("orderBy", "createdTime desc")
            page?.let { query.addQueryParameter("pageToken", it) }
            val data = json(request(query.build()).get().build())
            val files = data.getJSONArray("files")
            for (index in 0 until files.length()) {
                val item = files.getJSONObject(index)
                found += item
                require(found.size <= 1000) { "Too many Drive backups. No old backups were deleted." }
            }
            page = data.optString("nextPageToken").takeIf { it.isNotBlank() }
            require(page == null || seenPages.add(page!!) && seenPages.size <= 10) { "Drive inventory is incomplete. No old backups were deleted." }
        } while (page != null)
        require(found.map { it.getString("id") }.distinct().size == found.size) { "Drive inventory contains duplicate entries." }
        return found
    }

    override suspend fun upload(file: File, onProgress: (Int) -> Unit): DriveBackupFile {
        val length = file.length()
        require(length in 1..MAX_ENCRYPTED_BYTES)
        val checksum = withContext(Dispatchers.IO) { digest(file) }
        val properties = JSONObject().put("duaType", MARKER).put("duaOwner", owner).put("duaState", "pending")
            .put("duaSize", length.toString()).put("duaMd5", checksum)
        val metadata = JSONObject().put("name", "dua-auto-${Instant.now().toString().replace(':', '-')}-${UUID.randomUUID().toString().take(8)}.duabackup")
            .put("parents", org.json.JSONArray(listOf("appDataFolder"))).put("mimeType", "application/octet-stream").put("appProperties", properties)
        val init = call(request(url("upload/drive/v3/files").addQueryParameter("uploadType", "resumable").addQueryParameter("fields", FIELDS).build())
            .header("X-Upload-Content-Type", "application/octet-stream").header("X-Upload-Content-Length", length.toString())
            .post(metadata.toString().toRequestBody(JSON)).build())
        accepted(init)
        val session = init.headers["Location"]?.toHttpUrl() ?: throw DriveFailure("Drive did not return an upload session. Retry.", true)
        // Do not persist the session URL or Google access token.
        request(session)
        var offset = 0L
        var retries = 0
        var uploaded: JSONObject? = null
        while (offset < length) {
            currentCoroutineContext().ensureActive(); guard()
            val count = minOf(CHUNK_BYTES, length - offset)
            val result = try {
                call(request(session).header("Content-Range", "bytes $offset-${offset + count - 1}/$length")
                    .put(FileChunk(file, offset, count, guard)).build())
            } catch (failure: IOException) {
                if (++retries > 2) throw DriveFailure("Drive upload was interrupted. Retry on an unmetered connection.", true)
                call(request(session).header("Content-Range", "bytes */$length").put(ByteArray(0).toRequestBody(OCTETS)).build())
            }
            if (result.code == 308) {
                val range = result.headers["Range"]
                val next = if (range == null) 0L else Regex("bytes=0-([0-9]+)").matchEntire(range)?.groupValues?.get(1)?.toLongOrNull()?.plus(1)
                    ?: throw DriveFailure("Drive returned an invalid upload range.")
                require(next in 0..length && next <= offset + count) { "Drive returned an invalid upload offset." }
                if (next <= offset) { if (++retries > 2) throw DriveFailure("Drive upload made no progress. Retry.", true) } else retries = 0
                offset = next
                onProgress((offset * 100 / length).toInt())
            } else if (result.code in 200..299) {
                uploaded = JSONObject(result.text); offset = length; onProgress(100)
            } else {
                accepted(result)
            }
        }
        val received = uploaded ?: throw DriveFailure("Drive upload did not finish. No older backups were deleted.")
        val id = received.getString("id").also(::validId)
        require(received.optString("md5Checksum") == checksum && received.optString("size") == length.toString()) {
            "Drive backup verification failed. No older backups were deleted."
        }
        properties.put("duaState", "complete")
        val completed = json(request(url("drive/v3/files/$id").addQueryParameter("fields", FIELDS).build())
            .patch(JSONObject().put("appProperties", properties).toString().toRequestBody(JSON)).build())
        return parse(completed).also { require(it.id == id && it.md5 == checksum && it.size == length) }
    }

    override suspend fun retainThree(uploaded: DriveBackupFile) {
        val all = backups()
        check(all.any { it.id == uploaded.id && it.md5 == uploaded.md5 }) { "New backup is not in Drive's inventory. Older backups were kept." }
        check(all.take(3).any { it.id == uploaded.id }) { "Drive creation dates need review. Older backups were kept." }
        all.drop(3).forEach { file ->
            guard(); validId(file.id)
            accepted(call(request(url("drive/v3/files/${file.id}").build()).delete().build()))
        }
    }

    override suspend fun download(selected: DriveBackupFile, target: File) {
        // Re-list to confirm the selected ID is still a completed, app-owned backup for this owner.
        val actual = backups().firstOrNull { it.id == selected.id } ?: throw DriveFailure("This Drive backup is no longer available. Refresh the list.")
        check(actual.md5 == selected.md5 && actual.size == selected.size) { "Drive backup changed. Refresh the list." }
        validId(actual.id)
        val req = request(url("drive/v3/files/${actual.id}").addQueryParameter("alt", "media").build()).get().build()
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val call = client.newCall(req)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching { response.use {
                            accepted(HttpResult(it.code, it.headers, "")); guard()
                            val body = it.body ?: throw DriveFailure("Drive backup is empty.")
                            require(body.contentLength() < 0 || body.contentLength() == actual.size) { "Drive backup size changed." }
                            val bytes = body.byteStream().use { input -> target.outputStream().use { output ->
                                BackupCipher.copyBounded(input, output, actual.size) { check(!call.isCanceled()); guard() }
                            } }
                            require(bytes == actual.size && digest(target) == actual.md5) { "Drive backup download is incomplete or changed. Nothing was restored." }
                        } }
                        if (continuation.isActive) continuation.resumeWith(result)
                    }
                })
            }
            guard()
        } catch (failure: Exception) { target.delete(); throw failure }
    }

    private fun parse(item: JSONObject): DriveBackupFile {
        val props = item.getJSONObject("appProperties")
        val id = item.getString("id").also(::validId)
        val name = item.getString("name")
        val size = item.getString("size").toLong()
        val checksum = item.getString("md5Checksum")
        require(props.getString("duaType") == MARKER && props.getString("duaOwner") == owner && props.getString("duaState") == "complete" &&
            props.getString("duaMd5") == checksum && props.getString("duaSize") == size.toString() && checksum.matches(Regex("[0-9a-f]{32}")) &&
            size in 1..MAX_ENCRYPTED_BYTES && name.length <= 200 && name.endsWith(".duabackup")) { "Drive backup metadata could not be verified." }
        return DriveBackupFile(id, name, size, Instant.parse(item.getString("createdTime")).toEpochMilli(), checksum)
    }
    private suspend fun json(request: Request): JSONObject = call(request).also(::accepted).let { JSONObject(it.text) }
    private data class HttpResult(val code: Int, val headers: Headers, val text: String)
    private suspend fun call(request: Request): HttpResult = suspendCancellableCoroutine { continuation ->
        guard()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    val bytes = ByteArrayOutputStream()
                    it.body?.byteStream()?.use { input -> BackupCipher.copyBounded(input, bytes, 1024 * 1024) { check(!call.isCanceled()); guard() } }
                    HttpResult(it.code, it.headers, bytes.toString("UTF-8"))
                } }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }
    private fun accepted(result: HttpResult) {
        if (result.code in 200..299) return
        if (result.code == 401) { onUnauthorized(); throw DriveSetupRequired() }
        if (result.code == 403) throw DriveSetupRequired(forbiddenMessage(result.text))
        throw DriveFailure("Drive request failed (HTTP ${result.code}). Existing backups were kept.", result.code == 408 || result.code == 429 || result.code >= 500)
    }
    /** Only translate known reason codes. Never display Google's raw body, URLs or token metadata. */
    private fun forbiddenMessage(body: String): String {
        val error = runCatching { JSONObject(body).optJSONObject("error") }.getOrNull()
        val reasons = listOf("errors", "details").flatMap { key ->
            val entries = error?.optJSONArray(key)
            (0 until (entries?.length() ?: 0)).mapNotNull { entries?.optJSONObject(it)?.optString("reason") }
        }.toSet()
        return when {
            reasons.any { it in setOf("accessNotConfigured", "SERVICE_DISABLED", "API_DISABLED") } ->
                "Enable Google Drive API in the Google Cloud project containing Dua AI's existing Android OAuth client, then reconnect. This may differ from your Cloud Run project."
            reasons.any { it in setOf("insufficientPermissions", "ACCESS_TOKEN_SCOPE_INSUFFICIENT") } ->
                "Drive backup permission is missing. Add drive.appdata under Google Auth Platform → Data Access in the Android OAuth project's settings, then reconnect Google Drive and allow backup access."
            "storageQuotaExceeded" in reasons ->
                "Google Drive storage is full. Free space in this Google account, then retry the backup. Existing backups were kept."
            else -> "Drive refused backup access (HTTP 403). Check Drive permission and Google Drive API setup in the project containing the Android OAuth client, then reconnect."
        }
    }
    private class FileChunk(private val file: File, private val offset: Long, private val count: Long, private val guard: () -> Unit) : RequestBody() {
        override fun contentType() = OCTETS
        override fun contentLength() = count
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) {
            RandomAccessFile(file, "r").use { input ->
                input.seek(offset)
                val buffer = ByteArray(64 * 1024)
                var left = count
                try { while (left > 0) {
                    try { guard() } catch (_: Exception) { throw IOException("Drive backup account or permission changed.") }
                    val read = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                    if (read <= 0) throw IOException("Encrypted backup changed while uploading.")
                    sink.write(buffer, 0, read); left -= read
                } } finally { buffer.fill(0) }
            }
        }
    }
    companion object {
        const val MAX_ENCRYPTED_BYTES = BackupCipher.MAX_BYTES + 2 * 1024 * 1024
        private const val MARKER = "full-backup-v1"
        private const val FIELDS = "id,name,size,md5Checksum,createdTime,appProperties"
        private const val CHUNK_BYTES = 8L * 1024 * 1024
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val OCTETS = "application/octet-stream".toMediaType()
        private val httpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()
        private fun validId(id: String) { require(id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "Invalid Drive file ID." } }
        fun digest(file: File): String {
            val hash = MessageDigest.getInstance("MD5")
            file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) } }
            return hash.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
