package com.dua.ai.ui.theme

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Raw `Color(0x...)` literals belong only in the theme package (`ui/theme`). Screens must read
 * colours from MaterialTheme.colorScheme, DuaTheme.colors or DuaFixedColors.
 */
class NoRawHexColorTest {
    private val sources = File("src/main/java")
    private val themeDir = File(sources, "com/dua/ai/ui/theme").canonicalFile

    @Test fun rawHexColorsOnlyLiveInTheThemePackage() {
        assertTrue("Kotlin sources not found at ${sources.absolutePath}", sources.isDirectory)
        val offenders = sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.canonicalPath.startsWith(themeDir.path + File.separator) }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    if ("Color(0x" in line) "${file.relativeTo(sources)}:${index + 1}: ${line.trim()}" else null
                }
            }
            .toList()
        assertTrue(
            "Raw Color(0x...) found outside ui/theme. Add a theme token instead:\n" + offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }
}
