package com.dua.ai.projects

import org.json.JSONArray
import org.json.JSONObject

data class ProjectTask(val id: String, val title: String, val completed: Boolean = false, val dueAt: Long? = null)
data class ChatProject(val id: String, val name: String, val instructions: String = "", val archived: Boolean = false,
    val tasks: List<ProjectTask> = emptyList())

/** Projects are part of the owner snapshot; device preferences hold only their cache. */
object ProjectCodec {
    const val PERSONAL = "personal"
    const val MAX_PROJECTS = 20
    const val MAX_TASKS = 100
    const val MAX_TASK_TITLE = 200
    const val MAX_DUE_AT = 253402300799999L
    val defaults get() = listOf(ChatProject(PERSONAL, "Personal"), ChatProject("coding", "Coding"), ChatProject("business", "Business"))
    fun validId(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,100}"))
    fun validate(projects: List<ChatProject>): List<ChatProject> {
        require(projects.size in 1..MAX_PROJECTS && projects.map { it.id }.distinct().size == projects.size && projects.any { it.id == PERSONAL }) { "Keep Personal and at most 20 projects." }
        require(projects.all { validId(it.id) && it.name.isNotBlank() && it.name.length <= 80 && it.instructions.length <= 4000 }) { "Invalid project name or instructions." }
        projects.forEach { project ->
            require(project.tasks.size <= MAX_TASKS && project.tasks.map { it.id }.distinct().size == project.tasks.size &&
                project.tasks.all { validId(it.id) && it.title.isNotBlank() && it.title.length <= MAX_TASK_TITLE && (it.dueAt == null || it.dueAt in 1..MAX_DUE_AT) }) { "Invalid task or due date. Keep at most 100 tasks per project, with titles up to 200 characters." }
        }
        require(projects.any { !it.archived }) { "Keep at least one active project." }
        return projects
    }
    internal fun encodedProject(project: ChatProject) = JSONObject().put("id", project.id).put("name", project.name).put("instructions", project.instructions).put("archived", project.archived).apply {
        // Empty lists keep the pre-Tasks wire format and existing sync baselines unchanged.
        if (project.tasks.isNotEmpty()) put("tasks", JSONArray().apply { project.tasks.forEach {
            put(JSONObject().put("id", it.id).put("title", it.title).put("completed", it.completed).apply { it.dueAt?.let { at -> put("dueAt", at) } })
        } })
    }
    fun toJson(projects: List<ChatProject>) = JSONArray().apply {
        validate(projects).forEach { put(encodedProject(it)) }
    }
    fun fromJson(value: JSONArray?): List<ChatProject> {
        if (value == null) return defaults
        require(value.length() in 1..MAX_PROJECTS) { "Invalid projects." }
        return validate((0 until value.length()).map { index ->
            val p = value.getJSONObject(index)
            val fields = p.keys().asSequence().toSet()
            require(fields == setOf("id", "name", "instructions", "archived") || fields == setOf("id", "name", "instructions", "archived", "tasks")) { "Invalid project fields." }
            val tasks = if (!p.has("tasks")) emptyList() else p.getJSONArray("tasks").let { array ->
                require(array.length() <= MAX_TASKS) { "Too many tasks." }
                (0 until array.length()).map { taskIndex ->
                    val task = array.getJSONObject(taskIndex)
                    val taskFields = task.keys().asSequence().toSet()
                    require(taskFields == setOf("id", "title", "completed") || taskFields == setOf("id", "title", "completed", "dueAt")) { "Invalid task fields." }
                    val dueAt = if (!task.has("dueAt")) null else task.get("dueAt").let { raw ->
                        require(raw is Number && raw.toDouble().isFinite() && raw.toDouble() == raw.toLong().toDouble() && raw.toLong() in 1..MAX_DUE_AT) { "Invalid task due date." }
                        raw.toLong()
                    }
                    ProjectTask(task.get("id") as? String ?: error("Invalid task ID."), task.get("title") as? String ?: error("Invalid task title."),
                        task.get("completed") as? Boolean ?: error("Invalid task completion flag."), dueAt)
                }
            }
            ChatProject(p.get("id") as? String ?: error("Invalid project ID."), p.get("name") as? String ?: error("Invalid project name."),
                p.get("instructions") as? String ?: error("Invalid project instructions."), p.get("archived") as? Boolean ?: error("Invalid project archive flag."), tasks)
        })
    }
}
