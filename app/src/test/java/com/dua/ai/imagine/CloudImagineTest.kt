package com.dua.ai.imagine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudImagineTest {
    private val cloud = CloudImagine("gemini-3.1-flash-lite-image", 20, ImagineCodec.ASPECTS)

    @Test fun capabilityOnlyWhenEngineSaysImageTrue() {
        assertEquals(cloud, CloudImagine.parse("""{"agent":true,"imagine":{"image":true,"model":"gemini-3.1-flash-lite-image","maxImages":1,"dailyLimit":20,
            "aspectRatios":["auto","1:1","3:4","4:3","9:16","16:9"]}}"""))
        assertNull(CloudImagine.parse("""{"agent":true}"""))
        assertNull(CloudImagine.parse("""{"imagine":{"image":false}}"""))
        assertNull(CloudImagine.parse("""{"imagine":{"image":"true"}}"""))
        assertNull(CloudImagine.parse("<html>"))
    }

    @Test fun mergeAddsGoogleCloudAsDefaultAndKeepsVercelProviders() {
        val vercel = ImagineConfig(imageEnabled = true, providers = listOf(ImagineProvider("xai", "Grok Imagine", "grok-imagine")), defaultProvider = "xai",
            videoEnabled = true, dailyImages = 10)
        val merged = CloudImagine.merge(vercel, cloud)!!
        assertEquals(listOf(CloudImagine.PROVIDER, "xai"), merged.providers.map { it.id })
        assertEquals(CloudImagine.PROVIDER, merged.defaultProvider)
        assertTrue(merged.videoEnabled && merged.imageEnabled)
        assertEquals(CloudImagine.NAME, merged.providerName(null))
        assertSame(vercel, CloudImagine.merge(vercel, null))
        // Vercel Imagine off or missing: Cloud Run alone enables new images.
        val alone = CloudImagine.merge(null, cloud)!!
        assertTrue(alone.imageEnabled); assertFalse(alone.videoEnabled); assertEquals(20, alone.dailyImages)
        assertTrue(CloudImagine.merge(ImagineConfig(imageEnabled = false, imageReason = "No key"), cloud)!!.imageEnabled)
        assertNull(CloudImagine.merge(null, null))
    }

    @Test fun onlyNewImagesGoToGoogleCloud() {
        val image = ImagineOptions()
        assertTrue(CloudImagine.routes(cloud, image, sources = 0))
        assertTrue(CloudImagine.routes(cloud, image.copy(provider = CloudImagine.PROVIDER), 0))
        assertFalse(CloudImagine.routes(cloud, image, sources = 1))  // edits stay on Vercel
        assertFalse(CloudImagine.routes(cloud, image.copy(provider = "xai"), 0))
        assertFalse(CloudImagine.routes(cloud, image.copy(kind = ImagineInfo.VIDEO), 0))
        assertFalse(CloudImagine.routes(null, image, 0))
    }

    @Test fun cloudRunResponseIsTheExistingImagineFormat() {
        val path = "8f14e45f-ceea-467f-a0e6-3c4b1d2f9a10/0b5f0a4e-3f7e-4d55-9a3b-2c1d0e9f8a7b.png"
        val result = ImagineCodec.imageResult(JSONObject("""{"images":[{"path":"$path","mimeType":"image/png","size":1234,
            "url":"https://proj.supabase.co/storage/v1/object/sign/dua-files/$path?token=abc","expiresIn":3600}],
            "provider":"gemini","model":"gemini-3.1-flash-lite-image","edit":false,"usage":{"images":{"used":3,"limit":20},"resetAt":1760000000}}"""))
        assertEquals(listOf(path), result.media.map { it.path })
        assertEquals("image/png", result.media.single().mimeType)
        assertTrue(result.links[path]!!.contains("token=abc"))
        assertFalse(result.edit)
    }
}
