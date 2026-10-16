package com.dua.ai.backup

import com.dua.ai.files.StoredFileTypes
import com.dua.ai.history.ChatBackup
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.settings.PreferencesCodec
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class BackupAsset(val id: String, val path: String?, val name: String, val mimeType: String, val size: Long,
    val sha256: String, val chatId: String? = null, val messageId: String? = null)
data class FullBackupData(val backup: ChatBackup, val memory: List<String>, val ownerId: String?, val assets: List<BackupAsset>)
data class PreparedFullBackup(val data: FullBackupData, val files: Map<String, File>, val directory: File)

object FullBackupArchive {
    const val MAX_FILES = 10_000
    const val MAX_FILE_BYTES = 256L * 1024 * 1024
    const val MAX_ASSET_BYTES = BackupCipher.MAX_BYTES - 32L * 1024 * 1024
    private const val MANIFEST_BYTES = 8L * 1024 * 1024
    private val idPattern = Regex("[0-9]{5}")
    private val uuidPattern = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun writeZip(prepared: PreparedFullBackup, target: File, checkActive: () -> Unit = {}) {
        validate(prepared.data)
        val backup = ChatHistoryCodec.exportBackup(prepared.data.backup.chats, projects = prepared.data.backup.projects).toByteArray(Charsets.UTF_8)
        val manifest = manifest(prepared.data).toString().toByteArray(Charsets.UTF_8)
        require(manifest.size <= MANIFEST_BYTES) { "Backup file inventory is too large." }
        ZipOutputStream(BufferedOutputStream(target.outputStream())).use { zip ->
            zip.setLevel(0)
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            entry("manifest.json", manifest)
            entry("chats.json", backup)
            prepared.data.assets.forEach { asset ->
                checkActive()
                val file = prepared.files[asset.id] ?: error("Backup file is missing.")
                require(file.length() == asset.size && digest(file, checkActive) == asset.sha256) { "A backup file changed. Retry." }
                zip.putNextEntry(ZipEntry("files/${asset.id}"))
                file.inputStream().use { BackupCipher.copyBounded(it, zip, asset.size, checkActive) }
                zip.closeEntry()
            }
        }
        require(target.length() <= BackupCipher.MAX_BYTES) { "Backup exceeds the 1 GB archive limit." }
    }

    /** Only call after the entire encrypted stream has authenticated. ZIP names are never used as disk paths. */
    fun readZip(zipPath: File, directory: File, checkActive: () -> Unit = {}): PreparedFullBackup {
        require(directory.mkdirs() || directory.isDirectory) { "Backup workspace could not be created." }
        ZipInputStream(BufferedInputStream(zipPath.inputStream())).use { zip ->
            fun read(name: String, limit: Long): ByteArray {
                val entry = zip.nextEntry ?: error("Backup metadata is missing.")
                require(entry.name == name && !entry.isDirectory) { "Unexpected backup entry." }
                val out = ByteArrayOutputStream()
                BackupCipher.copyBounded(zip, out, limit, checkActive)
                zip.closeEntry()
                return out.toByteArray()
            }
            val header = JSONObject(strictUtf8(read("manifest.json", MANIFEST_BYTES)))
            require(header.optString("format") == "dua-full-backup" && header.opt("version") == 1) { "Unsupported full backup version." }
            val memory = header.getJSONArray("memory").let { array -> (0 until array.length()).map { array.get(it) as? String ?: error("Invalid memory note.") } }
            val owner = if (header.isNull("ownerId")) null else header.get("ownerId") as? String ?: error("Invalid backup owner.")
            val assets = header.getJSONArray("assets").let { array ->
                require(array.length() <= MAX_FILES) { "Backup has too many files." }
                (0 until array.length()).map { index ->
                    val item = array.getJSONObject(index)
                    fun nullable(name: String): String? = if (item.isNull(name)) null else item.get(name) as? String ?: error("Invalid backup reference.")
                    val size = item.get("size")
                    require(size is Number && size.toDouble() == size.toLong().toDouble()) { "Invalid backup size." }
                    BackupAsset(item.getString("id"), nullable("path"), item.getString("name"), item.getString("mimeType"), size.toLong(),
                        item.getString("sha256"), nullable("chatId"), nullable("messageId"))
                }
            }
            val backup = ChatHistoryCodec.readBackup(ByteArrayInputStream(read("chats.json", ChatHistoryCodec.MAX_IMPORT_BYTES.toLong())))
            val data = FullBackupData(backup, memory, owner, assets)
            validate(data)
            val files = assets.associate { asset ->
                checkActive()
                val file = File(directory, "asset-${asset.id}")
                val entry = zip.nextEntry ?: error("Backup raw file is missing.")
                require(entry.name == "files/${asset.id}" && !entry.isDirectory) { "Unexpected backup raw file." }
                val count = file.outputStream().use { BackupCipher.copyBounded(zip, it, asset.size, checkActive) }
                zip.closeEntry()
                require(count == asset.size && digest(file, checkActive) == asset.sha256) { "Backup file verification failed. Nothing was restored." }
                asset.id to file
            }
            require(zip.nextEntry == null) { "Backup contains unexpected entries." }
            return PreparedFullBackup(data, files, directory)
        }
    }

    fun validate(data: FullBackupData) {
        PreferencesCodec.validate(LocalPreferences(memory = data.memory))
        require(data.ownerId == null || uuidPattern.matches(data.ownerId)) { "Invalid backup owner." }
        require(data.assets.size <= MAX_FILES && data.assets.map { it.id }.distinct().size == data.assets.size) { "Invalid backup inventory." }
        require(data.assets.mapNotNull { it.path }.distinct().size == data.assets.count { it.path != null }) { "Duplicate stored files in backup." }
        var total = 0L
        data.assets.forEach { asset ->
            require(idPattern.matches(asset.id) && asset.name.isNotBlank() && asset.name.length <= 200 &&
                asset.size in 1..MAX_FILE_BYTES && asset.sha256.matches(Regex("[a-f0-9]{64}")) &&
                (StoredFileTypes.kind(asset.mimeType) != "file" || asset.mimeType == "video/mp4")) { "Invalid backup file." }
            total = Math.addExact(total, asset.size)
            if (asset.path != null) require(StoredFileTypes.PATH.matches(asset.path) && data.ownerId != null && asset.path.startsWith(data.ownerId + "/") && asset.chatId == null && asset.messageId == null) { "Backup contains a file from another account." }
            else require(data.backup.chats.any { chat -> chat.id == asset.chatId && chat.messages.any { it.id == asset.messageId && it.isUser } }) { "Backup attachment has no user message." }
        }
        require(total <= MAX_ASSET_BYTES) { "Raw files exceed the full backup size limit." }
        data.backup.chats.forEach { chat -> chat.messages.forEach { message ->
            require(message.files.size + data.assets.count { it.path == null && it.chatId == chat.id && it.messageId == message.id } <= 20) {
                "A message has too many attachments to restore completely."
            }
        } }
        val paths = data.assets.mapNotNull { it.path }.toSet()
        val referenced = data.backup.chats.flatMap { chat -> chat.messages.flatMap { message -> message.files.map { it.path } + message.media.mapNotNull { it.path } } } + data.backup.projects.flatMap { p -> p.sources.map { it.path } }
        require(paths.containsAll(referenced)) { "Some referenced files are unavailable. A complete backup could not be made." }
    }

    fun mergeMemory(current: List<String>, imported: List<String>): List<String> = (current + imported).distinct().also {
        PreferencesCodec.validate(LocalPreferences(memory = it))
    }

    fun digest(file: File, checkActive: () -> Unit = {}): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { checkActive(); val count = input.read(buffer); if (count < 0) break; hash.update(buffer, 0, count) }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    private fun manifest(data: FullBackupData): JSONObject = JSONObject().put("format", "dua-full-backup").put("version", 1)
        .put("ownerId", data.ownerId ?: JSONObject.NULL).put("memory", JSONArray(data.memory)).put("assets", JSONArray().apply {
            data.assets.forEach { asset -> put(JSONObject().put("id", asset.id).put("path", asset.path ?: JSONObject.NULL).put("name", asset.name)
                .put("mimeType", asset.mimeType).put("size", asset.size).put("sha256", asset.sha256)
                .put("chatId", asset.chatId ?: JSONObject.NULL).put("messageId", asset.messageId ?: JSONObject.NULL)) }
        })

    private fun strictUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
}
