package com.dua.ai.projects

import org.json.JSONArray
import org.json.JSONObject
import com.dua.ai.files.FileRef
import com.dua.ai.files.FileRefCodec
import com.dua.ai.files.StoredFileTypes

data class ProjectTask(val id: String, val title: String, val completed: Boolean = false, val dueAt: Long? = null)
data class ProjectMemory(val enabled: Boolean = false, val notes: List<String> = emptyList())
data class ProjectDetails(val createdAt: Long, val updatedAt: Long, val icon: String = "folder", val color: String = "purple",
    val pinned: Boolean = false, val deleted: Boolean = false)
data class ChatProject(val id: String, val name: String, val instructions: String = "", val archived: Boolean = false,
    val tasks: List<ProjectTask> = emptyList(), val memory: ProjectMemory = ProjectMemory(),
    val details: ProjectDetails? = null, val sources: List<FileRef> = emptyList())

/** Projects are part of the owner snapshot; device preferences hold only their cache. */
object ProjectCodec {
    const val PERSONAL = "personal"
    const val MAX_PROJECTS = 20
    const val MAX_STORED_PROJECTS = 100
    fun hasCapacity(projects: List<ChatProject>) = projects.size < MAX_STORED_PROJECTS && projects.count { it.details?.deleted != true } < MAX_PROJECTS
    const val MAX_TASKS = 100
    const val MAX_TASK_TITLE = 200
    const val MAX_DUE_AT = 253402300799999L
    val defaults get() = listOf(ChatProject(PERSONAL, "Personal"))
    val ICONS = setOf("folder", "book", "money", "writing")
    val COLORS = setOf("purple", "coral", "blue", "teal")
    const val MAX_SOURCES = 3
    /** Internal Home workspace and untouched legacy starter definitions are never project-list examples. */
    fun userProjects(projects: List<ChatProject>, chats: List<com.dua.ai.history.SavedChat>) = projects.filter { p ->
        p.id != PERSONAL && p.details?.deleted != true && (!p.archived || p.details != null) &&
            (p.details != null || p.id !in setOf("coding", "business") || p.name != p.id.replaceFirstChar { it.uppercase() } ||
                p.instructions.isNotBlank() || p.tasks.isNotEmpty() || p.memory != ProjectMemory() || chats.any { it.projectId == p.id })
    }
    fun validId(value: String) = value.matches(Regex("[A-Za-z0-9_-]{1,100}"))
    fun validate(projects: List<ChatProject>): List<ChatProject> {
        require(projects.size in 1..MAX_STORED_PROJECTS && projects.count { it.details?.deleted != true } <= MAX_PROJECTS && projects.map { it.id }.distinct().size == projects.size && projects.any { it.id == PERSONAL }) { "Keep Personal and at most 20 projects." }
        require(projects.all { validId(it.id) && it.name.isNotBlank() && it.name.length <= 80 && it.instructions.length <= 4000 }) { "Invalid project name or instructions." }
        projects.forEach { project ->
            project.details?.let { d ->
                require(d.createdAt in 0..MAX_DUE_AT && d.updatedAt in d.createdAt..MAX_DUE_AT && d.icon in ICONS && d.color in COLORS) { "Invalid project details." }
                require(project.id != PERSONAL && (!d.deleted || project.archived)) { "Invalid deleted project." }
            }
            require(project.sources.size <= MAX_SOURCES && project.sources.map { it.path }.distinct().size == project.sources.size &&
                project.sources.all { StoredFileTypes.PATH.matches(it.path) && it.name.isNotBlank() && it.name.length <= 200 && it.size in 1..52428800 && StoredFileTypes.kind(it.mimeType) != "file" && it.transcript == null }) { "Invalid project sources." }

            require(project.memory.notes.size <= 10 && project.memory.notes.all { it.isNotBlank() && it.length <= 200 }) { "Keep at most 10 memory notes, each up to 200 characters." }
            require(project.id != PERSONAL || project.memory == ProjectMemory()) { "Personal uses its existing memory settings." }
            require(project.tasks.size <= MAX_TASKS && project.tasks.map { it.id }.distinct().size == project.tasks.size &&
                project.tasks.all { validId(it.id) && it.title.isNotBlank() && it.title.length <= MAX_TASK_TITLE && (it.dueAt == null || it.dueAt in 1..MAX_DUE_AT) }) { "Invalid task or due date. Keep at most 100 tasks per project, with titles up to 200 characters." }
        }
        require(projects.any { !it.archived }) { "Keep at least one active project." }
        return projects
    }
    internal fun encodedProject(project: ChatProject) = JSONObject().put("id", project.id).put("name", project.name).put("instructions", project.instructions).put("archived", project.archived).apply {
        project.details?.let { d -> put("details", JSONObject().put("createdAt", d.createdAt).put("updatedAt", d.updatedAt)
            .put("icon", d.icon).put("color", d.color).put("pinned", d.pinned).put("deleted", d.deleted)) }
        if (project.sources.isNotEmpty()) put("sources", FileRefCodec.array(project.sources))
        // Default memory preserves older snapshot signatures.
        if (project.memory != ProjectMemory()) put("memory", JSONObject().put("enabled", project.memory.enabled).put("notes", JSONArray(project.memory.notes)))
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
        require(value.length() in 1..MAX_STORED_PROJECTS) { "Invalid projects." }
        return validate((0 until value.length()).map { index ->
            val p = value.getJSONObject(index)
            val fields = p.keys().asSequence().toSet()
            require(fields.containsAll(setOf("id", "name", "instructions", "archived")) && fields.all { it in setOf("id", "name", "instructions", "archived", "tasks", "memory", "details", "sources") }) { "Invalid project fields." }
            val memory = if (!p.has("memory")) ProjectMemory() else p.getJSONObject("memory").let { m ->
                require(m.keys().asSequence().toSet() == setOf("enabled", "notes")) { "Invalid project memory fields." }
                val notes = m.getJSONArray("notes")
                require(notes.length() <= 10) { "Too many memory notes." }
                ProjectMemory(m.get("enabled") as? Boolean ?: error("Invalid memory switch."),
                    (0 until notes.length()).map { notes.get(it) as? String ?: error("Invalid memory note.") })
            }
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
            val details = if (!p.has("details")) null else p.getJSONObject("details").let { d ->
                require(d.keys().asSequence().toSet() == setOf("createdAt", "updatedAt", "icon", "color", "pinned", "deleted")) { "Invalid project details." }
                fun time(key: String): Long { val n = d.get(key); require(n is Number && n.toDouble() == n.toLong().toDouble()); return n.toLong() }
                ProjectDetails(time("createdAt"), time("updatedAt"), d.getString("icon"), d.getString("color"),
                    d.get("pinned") as? Boolean ?: error("Invalid project pin"), d.get("deleted") as? Boolean ?: error("Invalid project deletion"))
            }
            val sources = if (!p.has("sources")) emptyList() else p.getJSONArray("sources").let { a ->
                require(a.length() <= MAX_SOURCES)
                (0 until a.length()).map { index ->
                    val f = a.getJSONObject(index)
                    require(f.keys().asSequence().toSet() == setOf("path", "name", "mimeType", "size")) { "Invalid project source fields." }
                    val size = f.get("size"); require(size is Number && size.toDouble().isFinite() && size.toDouble() == size.toLong().toDouble())
                    FileRef(f.get("path") as? String ?: error("Invalid source path"), f.get("name") as? String ?: error("Invalid source name"),
                        f.get("mimeType") as? String ?: error("Invalid source type"), size.toLong())
                }
            }
            ChatProject(p.get("id") as? String ?: error("Invalid project ID."), p.get("name") as? String ?: error("Invalid project name."),
                p.get("instructions") as? String ?: error("Invalid project instructions."), p.get("archived") as? Boolean ?: error("Invalid project archive flag."), tasks, memory, details, sources)
        })
    }
}
