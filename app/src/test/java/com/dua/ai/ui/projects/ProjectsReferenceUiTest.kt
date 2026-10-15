package com.dua.ai.ui.projects

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import com.dua.ai.ui.theme.ProjectTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.dua.ai.files.FileRef
import com.dua.ai.history.SavedChat
import com.dua.ai.model.ChatMessage
import com.dua.ai.projects.*
import com.dua.ai.settings.LocalPreferences
import com.dua.ai.ui.theme.DuaTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Synthetic test data only; the production route reads the owner's real definitions and history. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[34],qualifiers="w360dp-h800dp-mdpi")
class ProjectsReferenceUiTest(private val dark: Boolean, private val compact: Boolean) {
    companion object { @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="dark={0},compact={1}")
        fun modes()=listOf(arrayOf(false,false),arrayOf(true,false),arrayOf(false,true),arrayOf(true,true)) }
    @get:Rule val compose=createComposeRule()
    private val project=ChatProject("owner-created", "Test project", details=ProjectDetails(1700000000000,1700000000000,"book","blue"))
    private val userChat=SavedChat("real-chat", "Dua", "", listOf(ChatMessage("u","My project question",true),ChatMessage("r","Actual latest reply",false)),1700000000100,projectId=project.id)
    private fun show(content:@Composable ()->Unit)=compose.setContent {
        val density=LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density,if(compact)2f else 1f)) {
            DuaTheme(LocalPreferences(theme=if(dark)"dark" else "light")) {
                Box(Modifier.requiredSize(if(compact)320.dp else 360.dp,if(compact)568.dp else 752.dp).background(ProjectTheme.colors.background)){content()}
            }
        }
    }
    private fun capture(name:String){val file=File("build/approved-ui/projects-$name-${if(dark)"dark" else "light"}-${if(compact)"compact" else "regular"}.png");file.parentFile!!.mkdirs();if (compose.onAllNodes(isRoot()).fetchSemanticsNodes().size > 1) android.view.inspector.WindowInspector.getGlobalWindowViews().last().captureRoboImage(file.absolutePath) else compose.onRoot().captureRoboImage(file.absolutePath)}
    @Test fun emptyProjectsNeverShowHomeOrLegacyExamples() {
        show { ProjectsList(ProjectCodec.defaults + ChatProject("coding","Coding") + ChatProject("business","Business"),emptyList(),false,"",{}, {},{}) }
        compose.onNodeWithText("No projects yet").assertIsDisplayed()
        for(name in listOf("Personal","Coding","Business"))compose.onNodeWithText(name).assertDoesNotExist()
        compose.onNodeWithContentDescription("New Project").assertIsDisplayed().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Search projects").assertIsDisplayed()
        capture("empty-list")
    }
    @Test fun ownerProjectsFilterAndSearchLiveWithoutCreatingRows() {
        val projects=ProjectCodec.defaults+project+project.copy(id="second-owner",name="Another test")
        var opened=""
        show { ProjectsList(projects,listOf(userChat),false,"",{}, {},{opened=it}) }
        compose.onNodeWithContentDescription("Search projects").performTextInput("Another")
        compose.onNodeWithText("Test project").assertDoesNotExist()
        compose.onNodeWithText("Another test").assertIsDisplayed().performClick()
        assertEquals("second-owner",opened)
        compose.onNodeWithText("Shared with you").performScrollTo().performClick()
        compose.onNodeWithText("No shared projects").assertIsDisplayed()
        compose.onNodeWithText("Another test").assertDoesNotExist()
        capture("filtered-list")
    }
    @Test fun newProjectPresetsFillInputButOnlyCreateButtonSaves() {
        var result:List<String>?=null
        show { NewProjectScreen(notice="",busy=false,onBack={},onSave={n,i,g,c->result=listOf(n,i,g,c);true}) }
        compose.onNodeWithText("Create Project").assertIsNotEnabled()
        compose.onNodeWithText("Investing").performScrollTo().performClick()
        assertNull(result)
        compose.onNodeWithContentDescription("Project name").assertTextEquals("Investing")
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithContentDescription("Project instructions").performTextReplacement("Use only my project sources")
        compose.onNodeWithText("Save",substring=false).performClick()
        capture("new")
        compose.onNodeWithText("Create Project").performClick()
        assertEquals(listOf("Investing","Use only my project sources","money","teal"),result)
    }
    private fun detail(onAction:(ProjectAction)->Unit={},onOpenChat:(String)->Unit={},onSend:()->Unit={},draft:String="") {
        val state=mutableStateOf(draft)
        val mode=mutableStateOf("Chat")
        show { ProjectDetailScreen(project,listOf(userChat,userChat.copy(id="foreign",projectId="other",messages=listOf(ChatMessage("x","Other project's private message",false)))),
            state.value,emptyList(),false,"",mode.value,{mode.value=it},{},onAction,onOpenChat,{}, {},{}, {},{},
            {state.value=it},onSend,{}, {},{}, {},{}, {}) }
    }
    @Test fun chatsShowOnlySelectedProjectAndTheActualLatestMessage() {
        var opened=""
        detail(onOpenChat={opened=it})
        compose.onNodeWithText("Actual latest reply").assertIsDisplayed().performClick()
        assertEquals("real-chat",opened)
        compose.onNodeWithText("Other project's private message").assertDoesNotExist()
        compose.onNodeWithText("Sources",substring=false).assertIsDisplayed()
        compose.onNodeWithContentDescription("New project chat message").assertIsDisplayed()
        capture("chats")
    }
    @Test fun sourcesEmptyStateIsDecorativeAndDoesNotPretendToConnectDriveOrNotion() {
        detail()
        compose.onNodeWithText("Sources",substring=false).performClick()
        compose.onNodeWithText("Give Dua AI more context").assertExists()
        compose.onNodeWithText("Upload Sources to give Dua AI deeper context about your project.").assertExists()
        compose.onNodeWithText("Add",substring=false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("ChatGPT",substring=true).assertDoesNotExist()
        capture("sources")
    }
    @Test fun overflowDispatchesAllSixRealActions() {
        val hits=mutableListOf<ProjectAction>()
        detail(onAction={hits+=it})
        for((label,action)in listOf("New project chat" to ProjectAction.NewChat,"Pin project" to ProjectAction.Pin,"Edit project" to ProjectAction.Edit,
            "Edit instructions" to ProjectAction.Instructions,"Add to home" to ProjectAction.AddHome,"Delete project" to ProjectAction.Delete)) {
            compose.onNodeWithContentDescription("Project options").performClick()
            if(label=="New project chat")capture("overflow")
            compose.onNodeWithText(label,substring=false).performScrollTo().performClick()
            assertEquals(action,hits.last())
        }
    }
    @Test fun subtitleDropdownSelectsWorkWithoutSimulatingABackend() {
        detail()
        compose.onNodeWithContentDescription("Project mode, Chat").performClick()
        compose.onNodeWithText("Work",substring=false).assertIsDisplayed()
        capture("mode-menu")
        compose.onNodeWithText("Work",substring=false).performClick()
        compose.onNodeWithContentDescription("Project mode, Work").assertIsDisplayed()
        compose.onNodeWithText("Work is coming soon").assertIsDisplayed()
        compose.onNodeWithContentDescription("New project chat message").assertDoesNotExist()
    }
    @Test fun attachmentPopupContainsExactlyTheFourConfirmedItems() {
        detail()
        compose.onNodeWithContentDescription("Add project chat attachment").performClick()
        for(label in listOf("Camera","Photos","Files","Plugins"))compose.onNodeWithText(label,substring=false).assertExists()
        compose.onNodeWithText("Audio",substring=false).assertDoesNotExist()
        compose.onNodeWithText("Search",substring=false).assertDoesNotExist()
        capture("attachments")
    }
    @Test fun typingExpandsComposerAndSendsThroughExistingHandler() {
        var sent=0
        detail(onSend={sent++})
        compose.onNodeWithContentDescription("New project chat message").performTextInput("My task")
        val field=compose.onNodeWithContentDescription("New project chat message").fetchSemanticsNode().boundsInRoot
        val add=compose.onNodeWithContentDescription("Add project chat attachment").fetchSemanticsNode().boundsInRoot
        assertTrue(field.bottom<=add.top)
        compose.onNodeWithContentDescription("Send project message").performClick()
        assertEquals(1,sent)
        capture("composer-expanded")
    }
}
