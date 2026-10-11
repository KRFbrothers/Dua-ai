package com.dua.ai.projects

import org.json.JSONArray
import org.json.JSONObject

data class ChatProject(val id: String, val name: String, val instructions: String = "", val archived: Boolean = false)

/** Projects are part of the owner snapshot; device preferences hold only their cache. */
object ProjectCodec {
    const val PERSONAL = "personal"
    const val MAX_PROJECTS = 20
    val defaults get() = listOf(ChatProject(PERSONAL, "Personal"), ChatProject("coding", "Coding"), ChatProject("business", "Business"))
    fun validId(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,100}"))
    fun validate(projects: List<ChatProject>): List<ChatProject> {
        require(projects.size in 1..MAX_PROJECTS && projects.map { it.id }.distinct().size == projects.size && projects.any { it.id == PERSONAL }) { "Keep Personal and at most 20 projects." }
        require(projects.all { validId(it.id) && it.name.isNotBlank() && it.name.length <= 80 && it.instructions.length <= 4000 }) { "Invalid project name or instructions." }
        require(projects.any { !it.archived }) { "Keep at least one active project." }
        return projects
    }
    fun toJson(projects: List<ChatProject>) = JSONArray().apply {
        validate(projects).forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("instructions", it.instructions).put("archived", it.archived)) }
    }
    fun fromJson(value: JSONArray?): List<ChatProject> {
        if (value == null) return defaults
        require(value.length() in 1..MAX_PROJECTS) { "Invalid projects." }
        return validate((0 until value.length()).map { index ->
            val p = value.getJSONObject(index)
            require(p.keys().asSequence().toSet() == setOf("id", "name", "instructions", "archived")) { "Invalid project fields." }
            ChatProject(p.get("id") as? String ?: error("Invalid project ID."), p.get("name") as? String ?: error("Invalid project name."),
                p.get("instructions") as? String ?: error("Invalid project instructions."), p.get("archived") as? Boolean ?: error("Invalid project archive flag."))
        })
    }
}
