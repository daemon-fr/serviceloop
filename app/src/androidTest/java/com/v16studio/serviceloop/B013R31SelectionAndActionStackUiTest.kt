package com.v16studio.serviceloop

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopActionStack
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopEntityRecord
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopSecondaryButton
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopBrandStrip
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class B013R31SelectionAndActionStackUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun selectableEntityRecordKeepsSelectionIndependentFromCardNavigationAndClearsGlyphSpace() {
        val checked = mutableStateOf(false)
        var opened = false
        val title = "P-001 · A deliberately long service title that wraps naturally"
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    ServiceLoopEntityRecord(
                        title = title,
                        context = "EQ-001 · Compressor\nCustomer · Site",
                        metadata = "Due 2026-09-12",
                        selectionChecked = checked.value,
                        onSelectionChange = { checked.value = it },
                        onClick = { opened = true },
                    )
                }
            }
        }

        compose.onNodeWithTag("entity-record-selection", useUnmergedTree = true).assertIsOff().performClick().assertIsOn()
        assertTrue(checked.value)
        assertFalse(opened)
        compose.onNodeWithTag("entity-record-selection-icon", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("entity-record-card", useUnmergedTree = true).performClick()
        assertTrue(opened)
    }

    @Test
    fun shortTitleStaysOneLineLongTitleGrowsAndSelectionGlyphDoesNotOverlapTitle() {
        val title = mutableStateOf("P-001 · Short service")
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    ServiceLoopEntityRecord(
                        title = title.value,
                        selectionChecked = false,
                        onSelectionChange = {},
                        onClick = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        val titleNode = { compose.onNodeWithTag("entity-record-title", useUnmergedTree = true).fetchSemanticsNode() }
        val shortHeight = titleNode().boundsInRoot.height
        title.value = "P-001 · A deliberately long service title that wraps naturally"
        compose.waitForIdle()
        val longTitle = titleNode()
        val selectionIcon = compose.onNodeWithTag("entity-record-selection-icon", useUnmergedTree = true).fetchSemanticsNode()

        assertTrue("short title should remain one line (height=$shortHeight)", shortHeight < 50f)
        assertTrue("long title should grow only when it wraps (short=$shortHeight long=${longTitle.boundsInRoot.height})", longTitle.boundsInRoot.height > shortHeight)
        assertTrue("selection icon must clear title", selectionIcon.boundsInRoot.right <= longTitle.boundsInRoot.left)
    }

    @Test
    fun selectionGlyphIsTwentyFourDpAndClearsShortCardContext() {
        compose.runOnUiThread { compose.activity.setContent { ServiceLoopTheme {
            ServiceLoopEntityRecord("P-001 · Short service","EQ-001 · Compressor",selectionChecked=false,onSelectionChange={},onClick={})
        } } }
        compose.waitForIdle()
        val icon=compose.onNodeWithTag("entity-record-selection-icon",true).fetchSemanticsNode().boundsInRoot
        val title=compose.onNodeWithTag("entity-record-title",true).fetchSemanticsNode().boundsInRoot
        val context=compose.onNodeWithTag("entity-record-context",true).fetchSemanticsNode().boundsInRoot
        val expected=24f*compose.activity.resources.displayMetrics.density
        assertTrue("visible glyph should be approximately 24dp",kotlin.math.abs(icon.width-expected)<2f)
        assertTrue("icon needs a meaningful gap before title",title.left-icon.right>=8f*compose.activity.resources.displayMetrics.density)
        assertTrue("icon must finish above first context line",icon.bottom<context.top)
    }

    @Test
    fun actionStackGivesFullWidthCommandsAPositiveDesignSystemGap() {
        compose.runOnUiThread {
            compose.activity.setContent {
                ServiceLoopTheme {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        ServiceLoopActionStack {
                            ServiceLoopSecondaryButton("First", {}, Modifier.fillMaxWidth().testTag("action-first"))
                            ServiceLoopSecondaryButton("Second", {}, Modifier.fillMaxWidth().testTag("action-second"))
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        val first = compose.onNodeWithTag("action-first", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("action-second", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("action stack gap must be positive", second.top - first.bottom > 0f)
    }

    @Test
    fun brandWordmarkIsCenteredBetweenEqualLineRegions() {
        compose.runOnUiThread { compose.activity.setContent { ServiceLoopTheme { ServiceLoopBrandStrip(Modifier.width(360.dp).testTag("brand-strip")) } } }
        compose.waitForIdle()
        val strip=compose.onNodeWithTag("brand-strip",true).fetchSemanticsNode().boundsInRoot
        val wordmark=compose.onNodeWithTag("serviceloop-wordmark",true).fetchSemanticsNode().boundsInRoot
        val left=compose.onNodeWithTag("brand-left-line",true).fetchSemanticsNode().boundsInRoot
        val right=compose.onNodeWithTag("brand-right-line",true).fetchSemanticsNode().boundsInRoot
        assertTrue("wordmark must remain mathematically centered",kotlin.math.abs(wordmark.center.x-strip.center.x)<2f)
        assertTrue("line regions must have equal width",kotlin.math.abs(left.width-right.width)<2f)
        compose.onNodeWithText("ServiceLoop").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class B013R31OwnerSurfaceRenderTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "b013-r4")
        check(directory.exists() || directory.mkdirs())
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        FileOutputStream(File(directory, name)).use { check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        screenshot.recycle()
    }

    @Test
    fun rendersDueReminderAndTechnicianSurfacesOnCanonicalDevice() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty() }
        capture("home.png")
        compose.onNodeWithText("Work").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("field-search-due-services").fetchSemanticsNodes().isNotEmpty() }
        capture("due-services.png")
        compose.onAllNodesWithTag("entity-record-selection",useUnmergedTree=true)[0].performClick()
        capture("due-services-unchecked.png")
        compose.onNodeWithTag("work-more-actions").assertDoesNotExist()

        compose.onNodeWithText("Home").performClick()
        compose.onNodeWithText("Settings").performClick()
        capture("settings.png")
        compose.onNodeWithText("Reminders").performClick()
        compose.onNodeWithTag("reminder-settings").assertIsDisplayed()
        capture("reminder-settings.png")

        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithText("Team role settings").performClick()
        compose.onNodeWithText("Open Technician identity").performClick()
        compose.onNodeWithTag("technician-identity").assertIsDisplayed()
        capture("technician-identity.png")
    }

    @Test
    fun rendersEquipmentPrivateNoteAboveActions() {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("root-home").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Register").performClick()
        compose.onNodeWithTag("content-tab-Equipment").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("entity-record-card",true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("T-01 · Treadmill 01 — window side").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("equipment-private-note",true).fetchSemanticsNodes().isNotEmpty() }
        val note=compose.onNodeWithTag("equipment-private-note",true).fetchSemanticsNode().boundsInRoot
        val actions=compose.onNodeWithTag("equipment-actions",true).fetchSemanticsNode().boundsInRoot
        assertTrue("private note must be above equipment actions",note.bottom<actions.top)
        capture("equipment-private-note.png")
    }
}
