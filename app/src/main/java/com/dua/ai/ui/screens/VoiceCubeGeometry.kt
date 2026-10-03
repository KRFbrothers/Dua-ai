package com.dua.ai.ui.screens

import kotlin.math.cos
import kotlin.math.sin

/** Six real faces, rotated in 3D and depth-sorted before the Canvas projects them. */
internal object VoiceCubeGeometry {
    data class Point(val x: Float, val y: Float, val z: Float)
    data class Face(val points: List<Point>, val shade: Float) {
        val depth: Float get() = points.map { it.z }.average().toFloat()
    }
    private val vertices = listOf(
        Point(-1f, -1f, -1f), Point(1f, -1f, -1f), Point(1f, 1f, -1f), Point(-1f, 1f, -1f),
        Point(-1f, -1f, 1f), Point(1f, -1f, 1f), Point(1f, 1f, 1f), Point(-1f, 1f, 1f)
    )
    private val indices = listOf(listOf(0, 1, 2, 3), listOf(4, 5, 6, 7), listOf(0, 4, 7, 3),
        listOf(1, 5, 6, 2), listOf(0, 1, 5, 4), listOf(3, 2, 6, 7))
    fun faces(yaw: Float, pitch: Float): List<Face> {
        val y = Math.toRadians(yaw.toDouble()).toFloat()
        val x = Math.toRadians(pitch.toDouble()).toFloat()
        val rotated = vertices.map { v ->
            val rx = v.x * cos(y) + v.z * sin(y)
            val rz = -v.x * sin(y) + v.z * cos(y)
            Point(rx, v.y * cos(x) - rz * sin(x), v.y * sin(x) + rz * cos(x))
        }
        return indices.mapIndexed { i, face -> Face(face.map { rotated[it] }, .40f + i * .08f) }.sortedBy { it.depth }
    }
    fun project(point: Point): Pair<Float, Float> {
        val perspective = 5f / (5f - point.z)
        return point.x * perspective to point.y * perspective
    }
}
