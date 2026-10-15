package com.dua.ai.files

import com.dua.ai.attachments.AttachmentReader
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class ProjectSourceDownloadTest {
    private fun client(bytes: ByteArray, code: Int = 200) = StoredFileClient(OkHttpClient.Builder().addInterceptor { chain ->
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("Mock source response")
            .body(bytes.toResponseBody()).build()
    }.build())
    @Test fun signedSourceIsReadUsingTheExistingInlineFileRules() = runBlocking {
        val attachment = client("Project notes".toByteArray()).readAttachment("https://storage.example/signed/source", "Notes.txt", "text/plain")
        assertEquals("Notes.txt", attachment.name)
        assertEquals("Project notes", String(Base64.getDecoder().decode(attachment.data)))
    }
    @Test fun sourceDownloadRejectsInsecureLinksAndOversizedOrUnavailableBodies() = runBlocking {
        assertThrows(IllegalArgumentException::class.java) { runBlocking { client(byteArrayOf()).readAttachment("http://storage.example/source", "Notes.txt", "text/plain") } }
        assertThrows(Exception::class.java) { runBlocking { client(ByteArray(AttachmentReader.MAX_BYTES + 1)).readAttachment("https://storage.example/source", "Source.pdf", "application/pdf") } }
        assertThrows(IllegalStateException::class.java) { runBlocking { client(byteArrayOf(),403).readAttachment("https://storage.example/source", "Notes.txt", "text/plain") } }
        Unit
    }
}
