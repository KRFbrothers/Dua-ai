package com.dua.ai.projects

/** Explicit test fixtures replace the app's old auto-created Coding/Business examples. */
object ProjectFixtures {
    val owned get() = ProjectCodec.defaults + ChatProject("coding", "Coding") + ChatProject("business", "Business")
}
