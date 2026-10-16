package com.dua.ai.backup

import com.dua.ai.account.AccountRepository
import com.dua.ai.files.SignedUpload
import com.dua.ai.files.StoredFileClient
import com.dua.ai.files.StoredFileTypes
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URI

data class BackupRemoteFile(val path: String, val name: String, val mimeType: String, val size: Long)

/** All URLs come from the owner-checked base backend; no provider credentials enter an archive. */
class BackupFileTransfer(private val account: AccountRepository, private val client: OkHttpClient = StoredFileClient.uploadClient) {
    suspend fun inventory(owner: String): List<BackupRemoteFile> {
        checkOwner(owner)
        val json = account.request("/api/files/list", authenticated = true)
        checkOwner(owner)
        require(!json.optBoolean("truncated")) { "The cloud inventory is incomplete. A full backup cannot be made." }
        val files = json.getJSONArray("files")
        require(files.length() <= FullBackupArchive.MAX_FILES) { "There are too many files for one backup." }
        return (0 until files.length()).map { index ->
            val file = files.getJSONObject(index)
            val path = file.getString("path")
            require(StoredFileTypes.PATH.matches(path) && path.startsWith("$owner/")) { "File inventory does not belong to this account." }
            val mime = if (path.endsWith(".mp4")) "video/mp4" else StoredFileTypes.storedType(path, file.optString("mimeType"))
                ?: error("A cloud file type cannot be backed up: ${file.optString("name").take(60)}")
            val raw = file.get("size")
            require(raw is Number && raw.toDouble() == raw.toLong().toDouble() && raw.toLong() in 1..FullBackupArchive.MAX_FILE_BYTES) { "A file is empty or exceeds the 256 MB backup limit." }
            BackupRemoteFile(path, file.getString("name").take(200), mime, raw.toLong())
        }.also { require(it.map { file -> file.path }.distinct().size == it.size) { "Duplicate cloud file paths." } }
    }

    suspend fun download(file: BackupRemoteFile, owner: String, target: File) {
        checkOwner(owner)
        val json = account.request("/api/files/url", "POST", JSONObject().put("path", file.path), authenticated = true)
        checkOwner(owner)
        val url = json.getString("url")
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "Invalid backup file URL." }
        suspendCancellableCoroutine<Unit> { continuation ->
            val call = client.newCall(Request.Builder().url(url).get().build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            check(it.isSuccessful) { "A raw file could not be downloaded. A full backup was not completed." }
                            val body = it.body ?: error("A raw file is empty.")
                            require(body.contentLength() < 0 || body.contentLength() == file.size) { "A raw file changed size. Retry." }
                            val copied = body.byteStream().use { input -> target.outputStream().use { output ->
                                BackupCipher.copyBounded(input, output, file.size) { check(!call.isCanceled()) { "Backup cancelled." } }
                            } }
                            require(copied == file.size) { "A raw file is incomplete. Retry." }
                        }
                    }
                    if (result.isFailure) target.delete()
                    if (continuation.isActive) continuation.resumeWith(result)
                }
            })
        }
        checkOwner(owner)
    }

    suspend fun restore(asset: BackupAsset, bytes: File, owner: String): String {
        checkOwner(owner)
        val path = restorePath(asset, owner)
        val body = JSONObject().put("path", path).put("mimeType", asset.mimeType).put("size", asset.size)
        val json = account.request("/api/files/restore-sign", "POST", body, authenticated = true)
        val signed = SignedUpload.parse(json)
        require(signed.path == path && signed.path.startsWith("$owner/")) { "Restore returned a different file path." }
        require(asset.size <= signed.maxBytes) { "The file exceeds the current storage limit." }
        checkOwner(owner)
        StoredFileClient(client).upload(signed, asset.size, { bytes.inputStream() }, {})
        checkOwner(owner)
        return signed.path
    }

    private fun checkOwner(owner: String) { check(account.state.value.user?.id == owner) { "Account changed. Nothing was imported. Sign in and retry." } }

    companion object {
        /** Stable insert-only paths make retries idempotent, including formerly in-memory attachments. */
        fun restorePath(asset: BackupAsset, owner: String): String = asset.path ?: run {
            val extension = when (asset.mimeType) {
                "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "application/pdf" -> "pdf"
                "text/plain" -> "txt"; "text/csv" -> "csv"; "text/markdown" -> "md"; "application/json" -> "json"
                "audio/mp4" -> "m4a"; "audio/mpeg" -> "mp3"; "audio/wav" -> "wav"; "audio/ogg" -> "ogg"
                "audio/opus" -> "opus"; "audio/webm" -> "webm"; "audio/flac" -> "flac"; "audio/aac" -> "aac"
                else -> error("Inline backup file type cannot be restored.")
            }
            val stable = listOf(asset.sha256, asset.chatId, asset.messageId, asset.mimeType).joinToString("\u0000")
            "$owner/${java.util.UUID.nameUUIDFromBytes(stable.toByteArray(Charsets.UTF_8))}.$extension"
        }
    }
}
