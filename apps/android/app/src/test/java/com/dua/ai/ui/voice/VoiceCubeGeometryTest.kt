package com.dua.ai.ui.voice

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class VoiceCubeGeometryTest {
    @Test fun projectionRemainsFiniteAndFitsTheCapsuleThroughoutRotation() {
        for (yaw in 0..360 step 5) {
            val faces = VoiceCubeGeometry.faces(yaw.toFloat(), 14f)
            assertEquals(6, faces.size)
            faces.flatMap { it.points }.forEach { point ->
                val (x, y) = VoiceCubeGeometry.project(point)
                assertTrue(x.isFinite() && y.isFinite())
                // Canvas scales by .28 of the capsule; 1.7 stays within half its width.
                assertTrue(abs(x) < 1.7f && abs(y) < 1.7f)
            }
        }
    }
    @Test fun aCompleteTurnReturnsToTheSameGeometry() {
        val first = VoiceCubeGeometry.faces(32f, 10f).flatMap { it.points }
        val last = VoiceCubeGeometry.faces(392f, 10f).flatMap { it.points }
        first.zip(last).forEach { (a, b) ->
            assertEquals(a.x, b.x, .00001f)
            assertEquals(a.y, b.y, .00001f)
            assertEquals(a.z, b.z, .00001f)
        }
    }
    @Test fun farFacesArePaintedBeforeNearFaces() {
        for (yaw in 0..360 step 15) {
            val depths = VoiceCubeGeometry.faces(yaw.toFloat(), -20f).map { it.depth }
            assertEquals(depths.sorted(), depths)
        }
    }
}
