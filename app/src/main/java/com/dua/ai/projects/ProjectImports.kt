package com.dua.ai.projects

import com.dua.ai.history.ChatBackup
import java.util.UUID

object ProjectImports {
    /** Reuse identical settings; differing instructions/tasks/memory get a new project, never overwrite. */
    fun prepare(current: List<ChatProject>, backup: ChatBackup): ChatBackup {
        ProjectCodec.validate(current); ProjectCodec.validate(backup.projects)
        val projects = current.toMutableList()
        val mapping = backup.projects.associate { imported ->
            val existing = projects.find { it.copy(id = imported.id) == imported }
            val id = existing?.id ?: UUID.randomUUID().toString().also { fresh ->
                projects += imported.copy(id = fresh, name = (imported.name.take(69) + " (imported)"))
            }
            imported.id to id
        }
        ProjectCodec.validate(projects)
        return ChatBackup(backup.chats.map { it.copy(projectId = mapping.getValue(it.projectId)) }, projects)
    }
}
