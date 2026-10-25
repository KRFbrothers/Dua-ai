package com.dua.ai.ui.theme

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Bundled Home font stays the unmodified OFL release and its license notices keep shipping in the APK. */
class FontLicenseNoticesTest {
    private val main = File("src/main")

    @Test fun googleSansIsTheUnmodifiedOflReleaseWithItsNotices() {
        val font = File(main, "res/font/home_google_sans_variable.ttf").readBytes()
        val sha = MessageDigest.getInstance("SHA-256").digest(font).joinToString("") { "%02x".format(it) }
        assertEquals("d0a87d835a944b8b40d0e82a5651bb59ab97b936a2aeed5946eb57e7b2a3a90a", sha)
        val ofl = File(main, "assets/licenses/GoogleSans-OFL.txt").readText()
        assertTrue(ofl.contains("SIL Open Font License, Version 1.1") && ofl.contains("Google Sans Project Authors"))
        for (name in listOf("GoogleSans-NOTICE.txt", "GoogleSans-TRADEMARKS.md", "Inter-OFL.txt"))
            assertTrue(name, File(main, "assets/licenses/$name").length() > 0)
    }
}
