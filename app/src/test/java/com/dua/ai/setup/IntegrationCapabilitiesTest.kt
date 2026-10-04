package com.dua.ai.setup

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class IntegrationCapabilitiesTest {
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
}
