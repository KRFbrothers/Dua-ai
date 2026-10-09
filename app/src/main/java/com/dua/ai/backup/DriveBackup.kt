package com.dua.ai.backup

import android.accounts.Account
import android.content.Context
import androidx.work.*
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.history.ChatHistoryCodec
import com.dua.ai.settings.PreferencesCodec
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/** No OAuth tokens are persisted. Google Play services manages grants and token refresh. */
object DriveBackup {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    private const val WORK = "dua-drive-backup"
    fun preferences(context: Context) = context.getSharedPreferences("dua_drive_backup", Context.MODE_PRIVATE)
    fun authorization(email: String): AuthorizationRequest = AuthorizationRequest.builder()
        .setAccount(Account(email, "com.google"))
        .setRequestedScopes(listOf(Scope(SCOPE))).build()

    fun enable(context: Context, email: String) {
        val prefs = preferences(context)
        val editor = prefs.edit()
        if (prefs.getString("email", null) != email) editor.clear()
        check(editor.putString("email", email).putString("generation", UUID.randomUUID().toString()).putBoolean("enabled", true).commit()) { "Backup settings could not be saved." }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DriveBackupWorker>(24, TimeUnit.HOURS).setConstraints(constraints).build())
        WorkManager.getInstance(context).enqueueUniqueWork("$WORK-now", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DriveBackupWorker>().setConstraints(constraints).build())
    }
    fun disable(context: Context) {
        check(preferences(context).edit().putBoolean("enabled", false).remove("email").remove("generation").commit()) { "Backup settings could not be saved." }
        WorkManager.getInstance(context).cancelUniqueWork(WORK)
        WorkManager.getInstance(context).cancelUniqueWork("$WORK-now")
    }
    fun readLocal(context: Context): CloudSnapshot {
        val raw = context.getSharedPreferences("dua_chat_history", 0).getString("history", null)
        val settings = context.getSharedPreferences("dua_preferences", 0).getString("preferences", null)
        return CloudSnapshot(raw?.let { ChatHistoryCodec.decode(it).second }.orEmpty(),
            settings?.let { PreferencesCodec.decode(it).memory }.orEmpty())
    }
}

class DrivePermissionRequired : Exception("Reconnect Google Drive to allow backups.")

/** Immutable snapshots prevent one device from overwriting another device's backup. */
class DriveBackupApi(
    private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build(),
    private val baseUrl: String = "https://www.googleapis.com"
) {
    private fun request(token: String, path: String) = Request.Builder().url(baseUrl + path).header("Authorization", "Bearer $token")
    private fun execute(request: Request): String = client.newCall(request).execute().use { response ->
        if (response.code == 401 || response.code == 403) throw DrivePermissionRequired()
        check(response.isSuccessful) { "Google Drive request failed (${response.code}). Try again." }
        val body = response.body ?: error("Google Drive returned an empty response.")
        require(body.contentLength() <= 4 * 1024 * 1024 + 8192) { "Backup is too large." }
        val output = java.io.ByteArrayOutputStream()
        body.byteStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= 4 * 1024 * 1024 + 8192) { "Backup is too large." }
                output.write(buffer, 0, count)
            }
        }
        output.toString("UTF-8")
    }
    fun upload(token: String, snapshot: CloudSnapshot) {
        val payload = CloudSnapshotCodec.encode(snapshot).toString()
        val metadata = JSONObject().put("name", "dua-backup-${System.currentTimeMillis()}-${UUID.randomUUID()}.json")
            .put("parents", org.json.JSONArray(listOf("appDataFolder")))
            .put("appProperties", JSONObject().put("duaBackup", "1"))
        // OkHttp generates each part's Content-Type from its body and rejects that header in addPart.
        val jsonType = "application/json; charset=UTF-8".toMediaType()
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(metadata.toString().toRequestBody(jsonType))
            .addPart(payload.toRequestBody(jsonType)).build()
        execute(request(token, "/upload/drive/v3/files?uploadType=multipart&fields=id").post(body).build())
    }
    fun downloadLatest(token: String): CloudSnapshot {
        val url = (baseUrl + "/drive/v3/files").toHttpUrl().newBuilder()
            .addQueryParameter("spaces", "appDataFolder")
            .addQueryParameter("q", "trashed = false and appProperties has { key='duaBackup' and value='1' }")
            .addQueryParameter("orderBy", "createdTime desc").addQueryParameter("pageSize", "1")
            .addQueryParameter("fields", "files(id)").build()
        val files = JSONObject(execute(Request.Builder().url(url).header("Authorization", "Bearer $token").build())).getJSONArray("files")
        check(files.length() > 0) { "No Dua backup was found in this Google Drive account." }
        val id = files.getJSONObject(0).getString("id")
        require(id.matches(Regex("[A-Za-z0-9_-]+"))) { "Invalid Drive file." }
        return CloudSnapshotCodec.decode(JSONObject(execute(request(token, "/drive/v3/files/$id?alt=media").build())))
    }
}

class DriveBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = DriveBackup.preferences(applicationContext)
        val email = prefs.getString("email", null)
        val generation = prefs.getString("generation", null)
        if (!prefs.getBoolean("enabled", false) || email == null) return@withContext Result.success()
        try {
            val result = Tasks.await(Identity.getAuthorizationClient(applicationContext).authorize(DriveBackup.authorization(email)), 60, TimeUnit.SECONDS)
            if (result.hasResolution()) throw DrivePermissionRequired()
            val token = result.accessToken ?: throw DrivePermissionRequired()
            val snapshot = DriveBackup.readLocal(applicationContext)
            val encoded = CloudSnapshotCodec.encode(snapshot).toString()
            val hash = java.security.MessageDigest.getInstance("SHA-256").digest(encoded.toByteArray()).joinToString("") { "%02x".format(it) }
            if (!prefs.getBoolean("enabled", false) || prefs.getString("email", null) != email || prefs.getString("generation", null) != generation) return@withContext Result.success()
            if (prefs.getString("hash", null) != hash) {
                DriveBackupApi().upload(token, snapshot)
                if (prefs.getBoolean("enabled", false) && prefs.getString("email", null) == email && prefs.getString("generation", null) == generation) {
                    prefs.edit().putString("hash", hash).putLong("lastBackup", System.currentTimeMillis()).putString("status", "Backup saved to Google Drive.").commit()
                }
            }
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: DrivePermissionRequired) {
            prefs.edit().putString("status", "Drive permission is needed. Reconnect from Cloud sync.").commit()
            Result.failure()
        } catch (_: Exception) {
            prefs.edit().putString("status", "Backup failed. Will retry when online; local data is safe.").commit()
            Result.retry()
        }
    }
}
