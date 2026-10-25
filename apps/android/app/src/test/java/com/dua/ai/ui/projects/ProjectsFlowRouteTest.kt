package com.dua.ai.ui.projects

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import com.dua.ai.account.CloudSnapshot
import com.dua.ai.account.CloudSnapshotCodec
import com.dua.ai.projects.ProjectCodec
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.dua.ai.viewmodel.ChatViewModel
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h800dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
class ProjectsFlowRouteTest {
    @get:Rule val compose=createComposeRule()
    private lateinit var vm:ChatViewModel
    private val store=ViewModelStore()
    @Before fun setup() {
        val app=RuntimeEnvironment.getApplication()
        for(name in listOf("dua_chat_history","dua_preferences","dua_account_session","dua_auto_sync","dua_files")) app.getSharedPreferences(name,0).edit().clear().commit()
        vm=ChatViewModel(app).also{store.put("projects",it)}
        compose.setContent { DuaTheme(LocalPreferences(theme="light")) { ProjectsFeatureFlow(vm,{}, {},{}, {},{}) } }
    }
    @After fun cleanup(){store.clear()}
    private fun create():String {
        compose.onNodeWithText("No projects yet").assertIsDisplayed()
        compose.onNodeWithContentDescription("New Project").performClick()
        compose.onNodeWithText("Writing",substring=false).performScrollTo().performClick()
        assertEquals(1,vm.localSettings.value.projects.size)
        compose.onNodeWithContentDescription("Project name").performTextReplacement("Owner project")
        compose.onNodeWithText("Create Project").performClick()
        val p=vm.localSettings.value.projects.single{it.id!=ProjectCodec.PERSONAL}
        assertEquals("Owner project",p.name);assertEquals("writing",p.details!!.icon);assertEquals("purple",p.details.color)
        assertTrue(vm.savedChats.value.isEmpty());assertEquals(p.id,vm.projectId.value)
        compose.onNodeWithText("Owner project").assertIsDisplayed()
        return p.id
    }
    private fun option(text:String){compose.onNodeWithContentDescription("Project options").performClick();compose.onNodeWithText(text,substring=false).performScrollTo().performClick()}
    @Test fun createPinAndInstructionsUseRealViewModelAndPersistAcrossRestart() {
        val id=create()
        option("Pin project")
        assertTrue(vm.localSettings.value.projects.single{it.id==id}.details!!.pinned)
        option("Edit instructions")
        compose.onNodeWithContentDescription("Project instructions").performTextReplacement("Cite only this project's sources")
        compose.onNodeWithText("Save",substring=false).performClick()
        val project=vm.localSettings.value.projects.single{it.id==id}
        assertEquals("Cite only this project's sources",project.instructions)
        assertEquals(project,CloudSnapshotCodec.decode(CloudSnapshotCodec.encode(CloudSnapshot(vm.savedChats.value,emptyList(),vm.localSettings.value.projects))).projects.single{it.id==id})
        val reopened=ChatViewModel(RuntimeEnvironment.getApplication()).also{store.put("reopened",it)}
        assertEquals(project,reopened.localSettings.value.projects.single{it.id==id})
    }
    @Test fun deleteRequiresConfirmationAndRetainsAnAuthoritativeTombstone() {
        val id=create()
        option("Delete project")
        assertFalse(vm.localSettings.value.projects.single{it.id==id}.details!!.deleted)
        compose.onNodeWithText("Cancel",substring=false).performClick()
        assertFalse(vm.localSettings.value.projects.single{it.id==id}.details!!.deleted)
        option("Delete project")
        compose.onNode(hasText("Delete project") and hasClickAction()).performClick()
        val removed=vm.localSettings.value.projects.single{it.id==id}
        assertTrue(removed.archived);assertTrue(removed.details!!.deleted)
        compose.onNodeWithText("No projects yet").assertIsDisplayed()
        assertEquals("personal",vm.projectId.value)
    }
}
