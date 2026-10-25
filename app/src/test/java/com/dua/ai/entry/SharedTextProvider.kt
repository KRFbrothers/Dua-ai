package com.dua.ai.entry

import android.content.ContentProvider
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real resolver reads with a controllable metadata phase, without external URI grants or networking. */
class SharedTextProvider : ContentProvider() {
    var blockFirstMetadata = false
    val releaseMetadata = CountDownLatch(1)
    override fun onCreate() = true
    override fun getType(uri: Uri) = "text/plain"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): MatrixCursor {
        if (blockFirstMetadata && uri.lastPathSegment == "first.txt") check(releaseMetadata.await(5, TimeUnit.SECONDS))
        return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(uri.lastPathSegment, 5L)) }
    }
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val file = java.io.File(context!!.cacheDir, uri.lastPathSegment!!).apply { writeText("hello") }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
}
