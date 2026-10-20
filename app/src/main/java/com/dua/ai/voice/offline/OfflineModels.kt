package com.dua.ai.voice.offline

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/** A small Vosk model from alphacephei.com (size and MD5 from the official model-list.json). */
data class OfflineModel(val id: String, val name: String, val label: String, val sizeBytes: Long, val md5: String) {
    val url: String get() = "https://alphacephei.com/vosk/models/$name.zip"
    val sizeText: String get() = "%d MB".format(sizeBytes / 1_000_000)
}

object OfflineModels {
    /** Dictation modes: off, only when the phone has no recognizer, or always offline. */
    val MODES = listOf("off", "fallback", "always")
    // Vosk has no small Urdu model, so Urdu is not offered.
    val catalog = listOf(
        OfflineModel("hi", "vosk-model-small-hi-0.22", "Hindi", 44_458_845, "80f1265262c9a8a515f2707498e1b485"),
        OfflineModel("en-in", "vosk-model-small-en-in-0.4", "English (India)", 37_573_330, "62fd085f33c8c5fc01a235030e2a1fe3"),
        OfflineModel("en-us", "vosk-model-small-en-us-0.15", "English (US)", 41_205_931, "09ab50ccd62b674cbaa231b825f9c1cb"),
    )
    fun byId(id: String): OfflineModel? = catalog.firstOrNull { it.id == id }

    /** Whether composer dictation should use the offline model now. */
    fun useOffline(mode: String, modelReady: Boolean, phoneRecognizerAvailable: Boolean): Boolean =
        modelReady && when (mode) { "always" -> true; "fallback" -> !phoneRecognizerAvailable; else -> false }
}

sealed interface ModelStatus {
    data object NotInstalled : ModelStatus
    data class Downloading(val bytes: Long, val total: Long) : ModelStatus { val fraction: Float get() = if (total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f }
    data object Installing : ModelStatus
    data object Installed : ModelStatus
    data class Failed(val message: String) : ModelStatus
}

/** Models live in app-private storage: <files>/vosk/<model name>/, with a marker written after a complete unzip. */
class OfflineModelStore(val root: File) {
    fun dir(model: OfflineModel) = File(root, model.name)
    private fun marker(model: OfflineModel) = File(dir(model), ".dua-installed")
    fun installed(model: OfflineModel): Boolean = marker(model).isFile
    fun partFile(model: OfflineModel) = File(root, "${model.name}.zip.part")

    fun delete(model: OfflineModel) {
        dir(model).deleteRecursively(); partFile(model).delete(); File(root, ".staging-${model.name}").deleteRecursively()
    }

    /** Verifies [zip] (exact size + MD5), unzips it safely and marks the model installed. Throws on any problem. */
    fun install(model: OfflineModel, zip: File) {
        if (zip.length() != model.sizeBytes) throw IOException("Download was incomplete. Try again.")
        if (md5(zip) != model.md5) throw IOException("Download was damaged (checksum mismatch). Try again.")
        val staging = File(root, ".staging-${model.name}").apply { deleteRecursively(); mkdirs() }
        try {
            unzip(zip, staging)
            val unpacked = File(staging, model.name)
            if (!File(unpacked, "am").isDirectory || !File(unpacked, "conf").isDirectory) throw IOException("Model files are missing. Try again.")
            dir(model).deleteRecursively()
            if (!unpacked.renameTo(dir(model))) throw IOException("Could not save the model.")
            marker(model).writeText(model.name)
        } finally { staging.deleteRecursively() }
    }

    companion object {
        fun md5(file: File): String {
            val digest = MessageDigest.getInstance("MD5")
            file.inputStream().use { input -> val buffer = ByteArray(64 * 1024); while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) } }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /** Unzips into [target], rejecting entries that would escape it (zip slip). */
        fun unzip(zip: File, target: File) {
            val base = target.canonicalFile
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val out = File(base, entry.name).canonicalFile
                    if (out != base && !out.path.startsWith(base.path + File.separator)) throw IOException("Unsafe file in model archive.")
                    if (entry.isDirectory) out.mkdirs() else { out.parentFile?.mkdirs(); out.outputStream().use { input.copyTo(it) } }
                }
            }
        }
    }
}

/** Opens the model URL; returns the body stream and its content length (-1 if unknown). Replaced by a fake in tests. */
fun interface ModelSource { fun open(url: String): Pair<InputStream, Long> }

val HttpModelSource = ModelSource { url ->
    val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 30_000; instanceFollowRedirects = true }
    if (connection.responseCode != 200) { connection.disconnect(); throw IOException("Model server error ${connection.responseCode}. Try again later.") }
    connection.inputStream to connection.contentLengthLong
}

/** Downloads, verifies and installs offline models. One download at a time per model; survives leaving the screen. */
class OfflineModelManager(val store: OfflineModelStore, private val source: ModelSource, private val onWifi: () -> Boolean,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO), private val catalog: List<OfflineModel> = OfflineModels.catalog) {
    private val mutable = MutableStateFlow(catalog.associate { it.id to (if (store.installed(it)) ModelStatus.Installed else ModelStatus.NotInstalled) })
    val status: StateFlow<Map<String, ModelStatus>> = mutable.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()

    private fun model(id: String) = catalog.firstOrNull { it.id == id }
    fun ready(id: String): File? = model(id)?.takeIf(store::installed)?.let(store::dir)

    private fun set(id: String, value: ModelStatus) = mutable.update { it + (id to value) }

    fun download(id: String) {
        val model = model(id) ?: return
        synchronized(jobs) {
            if (jobs[id]?.isActive == true || store.installed(model)) return
            if (!onWifi()) { set(id, ModelStatus.Failed("Connect to Wi-Fi to download (${model.sizeText}).")); return }
            set(id, ModelStatus.Downloading(0, model.sizeBytes))
            jobs[id] = scope.launch {
                try { fetch(model); set(id, ModelStatus.Installing); store.install(model, store.partFile(model)); set(id, ModelStatus.Installed) }
                catch (error: kotlinx.coroutines.CancellationException) { store.delete(model); set(id, ModelStatus.NotInstalled); throw error }
                catch (error: Exception) { store.delete(model); set(id, ModelStatus.Failed(error.message ?: "Download failed. Try again.")) }
                finally { store.partFile(model).delete() }
            }
        }
    }

    private suspend fun fetch(model: OfflineModel) {
        store.root.mkdirs()
        if (store.root.usableSpace in 0 until model.sizeBytes * 3) throw IOException("Not enough storage. Free about ${model.sizeBytes * 3 / 1_000_000} MB.")
        val (input, length) = source.open(model.url)
        if (length >= 0 && length != model.sizeBytes) { input.close(); throw IOException("Unexpected model size. Try again later.") }
        input.use { body ->
            store.partFile(model).outputStream().use { out ->
                val buffer = ByteArray(64 * 1024); var total = 0L; var lastReport = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    if (!onWifi()) throw IOException("Wi-Fi disconnected. Download stopped.")
                    val n = body.read(buffer); if (n < 0) break
                    out.write(buffer, 0, n); total += n
                    if (total > model.sizeBytes) throw IOException("Download was larger than expected.")
                    if (total - lastReport >= 256 * 1024) { lastReport = total; set(model.id, ModelStatus.Downloading(total, model.sizeBytes)) }
                }
            }
        }
    }

    fun cancel(id: String) { synchronized(jobs) { jobs.remove(id)?.cancel() } }

    fun delete(id: String) {
        val model = model(id) ?: return
        cancel(id)
        scope.launch { store.delete(model); set(id, ModelStatus.NotInstalled) }
    }

    companion object {
        @Volatile private var instance: OfflineModelManager? = null
        fun get(context: Context): OfflineModelManager = instance ?: synchronized(this) {
            instance ?: context.applicationContext.let { app ->
                OfflineModelManager(OfflineModelStore(File(app.filesDir, "vosk")), HttpModelSource, { unmetered(app) })
            }.also { instance = it }
        }

        /** Wi-Fi (or another unmetered network). */
        fun unmetered(context: Context): Boolean {
            val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED))
        }
    }
}
