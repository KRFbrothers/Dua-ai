package com.dua.ai.setup

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IntegrationCapabilitiesTest {
    @Test fun chatPinRequiresExplicitBackendSupport() {
        fun read(raw: String) = IntegrationCapabilities.configuration(JSONObject(raw), IntegrationState(chatPinEnabled = true)).chatPinEnabled
        assertFalse(read("{}")); assertFalse(read("""{"history":{"pin":"true"}}"""))
        assertTrue(read("""{"history":{"pin":true}}"""))
    }
    @Test fun chatDeletionRequiresExplicitBackendSupport() {
        fun read(raw: String) = IntegrationCapabilities.configuration(JSONObject(raw), IntegrationState(chatDeletionEnabled = true)).chatDeletionEnabled
        assertFalse(read("{}")); assertFalse(read("""{"history":{"delete":"true"}}"""))
        assertTrue(read("""{"history":{"delete":true}}"""))
    }
    @Test fun renameRequiresExplicitBackendSupportAndRefreshClearsOldSupport() {
        fun read(raw: String) = IntegrationCapabilities.configuration(JSONObject(raw), IntegrationState(chatRenameEnabled = true)).chatRenameEnabled
        assertFalse(read("{}"))
        assertFalse(read("""{"history":{"rename":"true"}}"""))
        assertTrue(read("""{"history":{"rename":true}}"""))
    }
    @Test fun projectMemoryRequiresExplicitOwnerBackendCapability() {
        fun read(raw: String) = IntegrationCapabilities.configuration(JSONObject(raw), IntegrationState(projectMemoryEnabled = true)).projectMemoryEnabled
        assertFalse(read("{}"))
        assertFalse(read("""{"projects":{"enabled":true}}"""))
        assertFalse(read("""{"projects":{"enabled":false,"memory":true}}"""))
        assertFalse(read("""{"projects":{"enabled":true,"memory":"true"}}"""))
        assertTrue(read("""{"projects":{"enabled":true,"memory":true}}"""))
    }
    @Test fun dueDatesRequireNewBackendCapabilityAlongsideOwnerTasks() {
        fun enabled(raw: String) = IntegrationCapabilities.configuration(JSONObject(raw), IntegrationState()).taskDueDatesEnabled
        assertFalse(enabled("""{"projects":{"enabled":true,"tasks":true}}"""))
        assertFalse(enabled("""{"projects":{"enabled":true,"tasks":true,"dueDates":"true"}}"""))
        assertFalse(enabled("""{"projects":{"enabled":false,"tasks":true,"dueDates":true}}"""))
        assertTrue(enabled("""{"projects":{"enabled":true,"tasks":true,"dueDates":true}}"""))
    }
    @Test fun tasksRequireExplicitServerSupportAndAnEnabledOwnerProjectBackend() {
        fun read(raw: String) = IntegrationCapabilities.configuration(JSONObject(raw), IntegrationState(projectTasksEnabled = true))
        assertFalse(read("{}").projectTasksEnabled)
        assertFalse(read("""{"projects":{"enabled":true}}""").projectTasksEnabled)
        assertFalse(read("""{"projects":{"enabled":false,"tasks":true}}""").projectTasksEnabled)
        assertFalse(read("""{"projects":{"enabled":true,"tasks":"true"}}""").projectTasksEnabled)
        assertTrue(read("""{"projects":{"enabled":true,"tasks":true}}""").projectTasksEnabled)
    }
    @Test fun inChatWebCapabilitiesAreExplicitAndKeepSetupState() {
        val existing = IntegrationState(searchConfigured = true, busy = true, notice = "Refreshing")
        val next = IntegrationCapabilities.configuration(JSONObject("""{"web":{"searchConfigured":true,"pageReadingAvailable":true}}"""), existing)
        assertTrue(next.webSearchConfigured)
        assertTrue(next.pageReadingAvailable)
        assertTrue(next.searchConfigured)
        assertTrue(next.busy)
        assertEquals("Refreshing", next.notice)
        val malformed = IntegrationCapabilities.configuration(JSONObject("""{"web":{"searchConfigured":"true","pageReadingAvailable":"true"}}"""), next)
        assertFalse(malformed.webSearchConfigured)
        assertFalse(malformed.pageReadingAvailable)
        val missing = IntegrationCapabilities.configuration(JSONObject(), next)
        assertFalse(missing.webSearchConfigured)
        assertFalse(missing.pageReadingAvailable)
    }
    @Test fun generatedVoiceRequiresBothActualBackendConfigurationAndPersonalOwnerRestriction() {
        val json = JSONObject().put("groups", org.json.JSONArray(IntegrationCapabilities.GROUPS.map {
            JSONObject().put("id", it).put("title", it).put("state", "partial")
        })).put("advanced", JSONObject().put("voice", JSONObject().put("generatedAudio", JSONObject().put("configured", true))))
        assertFalse(IntegrationCapabilities.decode(json).generatedVoiceConfigured)
        json.put("account", JSONObject().put("ownerRestricted", true))
        assertTrue(IntegrationCapabilities.decode(json).generatedVoiceConfigured)
        json.getJSONObject("advanced").getJSONObject("voice").getJSONObject("generatedAudio").put("configured", "true")
        assertFalse(IntegrationCapabilities.decode(json).generatedVoiceConfigured)
    }
    @Test fun providerNamesMustBeSupportedAndConfigurationIsExplicit() {
        val providers = IntegrationCapabilities.providers(JSONObject("""{"providers":[{"name":"ChatGPT","configured":true,"model":"example"},{"name":"Gemini","configured":false},{"name":"unknown","configured":true}]}"""))
        assertEquals(listOf("ChatGPT", "Gemini"), providers.map { it.name })
        assertTrue(providers[0].configured)
        assertFalse(providers[1].configured)
        assertFalse(providers[0].supportsAttachments)
    }
    @Test fun explicitAttachmentTypesRemainAuthoritativeForEverySelectedFile() {
        val provider = IntegrationCapabilities.providers(JSONObject("""{"providers":[{"name":"ChatGPT","configured":true,"supportsAttachments":true,"attachmentTypes":["image/jpeg","text/plain"]}]}""")).single()
        assertEquals(listOf("image/jpeg", "text/plain"), provider.attachmentTypes)
        assertNull(IntegrationCapabilities.attachmentError(provider, listOf("image/jpeg", "text/plain")))
        val error = IntegrationCapabilities.attachmentError(provider, listOf("text/plain", "application/pdf"))!!
        assertTrue(error.contains("ChatGPT supports .jpg/.jpeg, .txt files"))
        assertTrue(error.contains("Your selected files were kept"))
        assertFalse(error.contains(".pdf"))
    }
    @Test fun explicitAttachmentTypesNeverAddImplicitMimeSupport() {
        val provider = IntegrationCapabilities.providers(JSONObject("""{"providers":[{"name":"Gemini","configured":true,"supportsAttachments":true,"attachmentTypes":["image/png"]}]}""")).single()
        assertEquals(listOf("image/png"), provider.attachmentTypes)
        assertNull(IntegrationCapabilities.attachmentError(provider, listOf("image/png")))
        assertTrue(IntegrationCapabilities.attachmentError(provider, listOf("text/plain"))!!.contains("Gemini supports .png files"))
    }
    @Test fun explicitAttachmentTypesDropUnknownAndMalformedEntriesAndDeduplicate() {
        val provider = IntegrationCapabilities.providers(JSONObject("""{"providers":[{"name":"Claude","configured":true,"supportsAttachments":true,"attachmentTypes":["image/png","application/x-unknown",42,null,"image/png",{},"text/plain"]}]}""")).single()
        assertEquals(listOf("image/png", "text/plain"), provider.attachmentTypes)
        val error = IntegrationCapabilities.attachmentError(provider, listOf("application/x-unknown"))!!
        assertTrue(error.contains("Claude supports .png, .txt files"))
        assertFalse(error.contains("application/x-unknown"))
    }
    @Test fun olderProviderConfigurationUsesFullGeminiAndDuaSupportAndTxtForOtherProviders() {
        val entries = org.json.JSONArray(IntegrationCapabilities.MODELS.map {
            JSONObject().put("name", it).put("configured", true).put("supportsAttachments", true)
        })
        val providers = IntegrationCapabilities.providers(JSONObject().put("providers", entries))
        providers.forEach { provider ->
            val expected = if (provider.name in listOf("Dua", "Gemini")) IntegrationCapabilities.ATTACHMENT_TYPES else listOf("text/plain")
            assertEquals(provider.name, expected, provider.attachmentTypes)
            assertNull(IntegrationCapabilities.attachmentError(provider, listOf("text/plain")))
            if (provider.name !in listOf("Dua", "Gemini")) {
                assertTrue(IntegrationCapabilities.attachmentError(provider, listOf("image/jpeg"))!!.contains("${provider.name} supports .txt files"))
            }
        }
        val legacy = IntegrationCapabilities.providers(JSONObject("""{"geminiConfigured":true}"""))
        assertEquals(listOf("Dua", "Gemini"), legacy.map { it.name })
        legacy.forEach { assertEquals(IntegrationCapabilities.ATTACHMENT_TYPES, it.attachmentTypes) }
    }
    @Test fun disabledEmptyAndMalformedAttachmentCapabilitiesDoNotEnableFallbackTypes() {
        listOf(
            """{"supportsAttachments":false}""",
            """{"supportsAttachments":false,"attachmentTypes":["text/plain"]}""",
            """{"supportsAttachments":true,"attachmentTypes":[]}""",
            """{"supportsAttachments":true,"attachmentTypes":["application/x-unknown",42,null]}""",
            """{"supportsAttachments":true,"attachmentTypes":"text/plain"}"""
        ).forEach { capability ->
            val item = JSONObject(capability).put("name", "Gemini").put("configured", true)
            val provider = IntegrationCapabilities.providers(JSONObject().put("providers", org.json.JSONArray().put(item))).single()
            assertEquals(emptyList<String>(), provider.attachmentTypes)
            assertNull(IntegrationCapabilities.attachmentError(provider, emptyList()))
            assertTrue(IntegrationCapabilities.attachmentError(provider, listOf("text/plain"))!!.contains("Gemini does not support file attachments"))
        }
    }
    @Test fun searchLinksRejectExecutableSchemesCredentialsAndCleartext() {
        assertNull(IntegrationCapabilities.safeUrl("javascript:alert(1)"))
        assertNull(IntegrationCapabilities.safeUrl("http://example.org"))
        assertNull(IntegrationCapabilities.safeUrl("https://user:password@example.org"))
        assertEquals("https://example.org/news", IntegrationCapabilities.safeUrl("https://example.org/news"))
        val results = IntegrationCapabilities.search(JSONObject("""{"results":[{"title":"safe","url":"https://example.org"},{"title":"unsafe","url":"javascript:alert(1)"}]}"""))
        assertEquals(1, results.size)
    }
    @Test fun setupRequiresAllEightGroups() {
        val error = runCatching { IntegrationCapabilities.decode(JSONObject("""{"groups":[{"id":"voice","title":"Voice","state":"available"}]}""")) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
    @Test fun repositoryLinksMustPointToGithub() {
        val results = IntegrationCapabilities.repositories(JSONObject("""{"repositories":[{"name":"good","url":"https://github.com/example/project"},{"name":"bad","url":"https://example.org"}]}"""))
        assertEquals(listOf("good"), results.map { it.name })
    }
    @Test fun reasoningSupportComesFromTheServerWithSafeDefaults() {
        val json = JSONObject("""{"providers":[{"name":"Gemini","configured":true},{"name":"Grok","configured":true,"reasoning":false},
            {"name":"Claude","configured":true},{"name":"ChatGPT","configured":true,"reasoning":true}]}""")
        val parsed = IntegrationCapabilities.providers(json).associate { it.name to it.supportsReasoning }
        assertEquals(mapOf("Gemini" to true, "Grok" to false, "Claude" to false, "ChatGPT" to true), parsed)
        assertTrue(IntegrationCapabilities.fallbackProviders.all { it.supportsReasoning })
    }
    @Test fun voiceV2ConfigReportsReplyVoiceWithoutFeatureSetupAndOldBackendsKeepLegacyState() {
        val legacy = IntegrationState(generatedVoiceConfigured = true)
        val old = IntegrationCapabilities.configuration(JSONObject(), legacy)
        assertNull(old.voice); assertTrue(old.generatedVoiceConfigured)
        val voice = JSONObject().put("version", 1).put("tts", JSONObject().put("enabled", true).put("providers", org.json.JSONArray().put(
            JSONObject().put("id", "xai").put("name", "Grok").put("voices", org.json.JSONArray().put(JSONObject().put("id", "eve").put("name", "Eve"))))))
        val next = IntegrationCapabilities.configuration(JSONObject().put("voice", voice), IntegrationState())
        assertTrue(next.generatedVoiceConfigured); assertEquals("xai", next.voice!!.defaultProvider)
        voice.getJSONObject("tts").put("enabled", false)
        assertFalse(IntegrationCapabilities.configuration(JSONObject().put("voice", voice), legacy).generatedVoiceConfigured)
    }
}
