package com.dua.ai.history

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class ChatHistoryBackupRulesTest {
    @Test fun chatHistoryIsExcludedFromAllBackupModes() {
        val rulesDirectory = File("src/main/res/xml")
        val legacyRules = parseRules(File(rulesDirectory, "account_backup_rules.xml"))
        val extractionRules = parseRules(File(rulesDirectory, "account_data_extraction_rules.xml"))

        assertTrue(hasChatHistoryExclusion(legacyRules.documentElement))
        assertTrue(hasChatHistoryExclusion(extractionRules.getElementsByTagName("cloud-backup").item(0) as Element))
        assertTrue(hasChatHistoryExclusion(extractionRules.getElementsByTagName("device-transfer").item(0) as Element))
    }
    @Test fun deviceReminderConsentAndDeliveryHistoryAreExcludedFromBackupAndTransfer() {
        val rulesDirectory = File("src/main/res/xml")
        val legacy = parseRules(File(rulesDirectory, "account_backup_rules.xml"))
        val extraction = parseRules(File(rulesDirectory, "account_data_extraction_rules.xml"))
        for (scope in listOf(legacy.documentElement, extraction.getElementsByTagName("cloud-backup").item(0) as Element,
            extraction.getElementsByTagName("device-transfer").item(0) as Element)) {
            val entries = scope.getElementsByTagName("exclude")
            assertTrue((0 until entries.length).any { index -> (entries.item(index) as Element).let {
                it.getAttribute("domain") == "sharedpref" && it.getAttribute("path") == "dua_task_reminders.xml"
            } })
        }
    }

    private fun parseRules(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)

    private fun hasChatHistoryExclusion(parent: Element): Boolean {
        val excludes = parent.getElementsByTagName("exclude")
        return (0 until excludes.length).any { index ->
            val rule = excludes.item(index) as Element
            rule.getAttribute("domain") == "sharedpref" && rule.getAttribute("path") == "dua_chat_history.xml"
        }
    }
}
