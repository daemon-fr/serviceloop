package com.v16studio.serviceloop

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.v16studio.serviceloop.ui.designsystem.InspectionStatusChoice
import com.v16studio.serviceloop.ui.designsystem.ServiceLoopInspectionStatusGrid
import com.v16studio.serviceloop.ui.icons.ServiceLoopIcons
import com.v16studio.serviceloop.ui.theme.ServiceLoopTheme
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InspectionStatusChoiceGeometryTest {
    @get:Rule val compose = createComposeRule()
    private var renderedDensity = 1f
    private var selectedChoice = mutableStateOf("0")

    @Test
    fun notApplicableAndOkSelectionPreservesAllOptionBounds() {
        renderSelectable(listOf("Not applicable", "OK", "Not checked", "Issue found"), widthDp = 320)
        val initial = optionBounds()

        compose.onNodeWithTag("grid-choice-1").assertIsNotSelected().performClick().assertIsSelected()
        val afterOk = optionBounds()
        assertBoundsUnchanged(initial, afterOk, "selecting OK")

        compose.onNodeWithTag("grid-choice-0").assertIsNotSelected().performClick().assertIsSelected()
        assertBoundsUnchanged(initial, optionBounds(), "selecting Not applicable")
    }

    @Test
    fun notCheckedAndIssueFoundSelectionPreservesAllOptionBounds() {
        renderSelectable(listOf("Not applicable", "OK", "Not checked", "Issue found"), widthDp = 320, initialSelection = 2)
        val initial = optionBounds()

        compose.onNodeWithTag("grid-choice-3").assertIsNotSelected().performClick().assertIsSelected()
        val afterIssueFound = optionBounds()
        assertBoundsUnchanged(initial, afterIssueFound, "selecting Issue found")

        compose.onNodeWithTag("grid-choice-2").assertIsNotSelected().performClick().assertIsSelected()
        assertBoundsUnchanged(initial, optionBounds(), "selecting Not checked")
    }

    @Test
    fun selectionDoesNotSwitchPairModeNearAllocationThreshold() {
        val width = mutableStateOf(280)
        selectedChoice = mutableStateOf("1")
        compose.setContent {
            ServiceLoopTheme {
                val density = LocalDensity.current
                renderedDensity = density.density
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                    Box(Modifier.width(width.value.dp).testTag("grid-parent")) {
                        ServiceLoopInspectionStatusGrid(
                            choices = listOf("Long inspection answer text", "Short", "Other", "OK").mapIndexed { index, label ->
                                InspectionStatusChoice(
                                    key = index.toString(),
                                    label = label,
                                    icon = ServiceLoopIcons.Circle,
                                    semanticColor = Color(0xFF08666B),
                                    testTag = "grid-choice-$index",
                                )
                            },
                            selectedKey = selectedChoice.value,
                            onSelected = { selectedChoice.value = it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()

        val observedModes = mutableSetOf<String>()
        (280..360).forEach { widthDp ->
            compose.runOnIdle {
                width.value = widthDp
                selectedChoice.value = "1"
            }
            compose.waitForIdle()
            val initial = optionBounds()
            val initialMode = pairMode(initial, 0)
            observedModes += initialMode

            compose.onNodeWithTag("grid-choice-0").performClick().assertIsSelected()
            val afterSelection = optionBounds()
            assertEquals("selection must not change the first-pair layout mode at ${widthDp}dp", initialMode, pairMode(afterSelection, 0))
            assertBoundsUnchanged(initial, afterSelection, "selecting long answer at ${widthDp}dp")
        }
        assertTrue("the sampled widths should cover both sides of the allocation threshold: $observedModes", observedModes.size == 2)
    }

    @Test
    fun largeFontSelectionPreservesLayoutModeAndAllOptionBoundsAt320Dp() {
        renderSelectable(
            listOf("Not applicable", "OK", "Not checked", "Issue found"),
            widthDp = 320,
            fontScale = 2f,
        )
        val initial = optionBounds()
        val initialModes = listOf(pairMode(initial, 0), pairMode(initial, 2))

        compose.onNodeWithTag("grid-choice-3").performClick().assertIsSelected()
        val afterIssueFound = optionBounds()
        assertEquals(initialModes, listOf(pairMode(afterIssueFound, 0), pairMode(afterIssueFound, 2)))
        assertBoundsUnchanged(initial, afterIssueFound, "selecting Issue found at 320dp/fontScale 2")

        compose.onNodeWithTag("grid-choice-0").performClick().assertIsSelected()
        val afterNotApplicable = optionBounds()
        assertEquals(initialModes, listOf(pairMode(afterNotApplicable, 0), pairMode(afterNotApplicable, 2)))
        assertBoundsUnchanged(initial, afterNotApplicable, "selecting Not applicable at 320dp/fontScale 2")
    }

    @Test
    fun selectionMovesRadioSemanticsAndChangesVisualTreatmentWithoutChangingGeometry() {
        renderSelectable(listOf("Not applicable", "OK", "Not checked", "Issue found"), widthDp = 320)
        compose.onNodeWithTag("grid-choice-0").assertIsSelected()
        compose.onNodeWithTag("grid-choice-1").assertIsNotSelected()
        val initial = optionBounds()
        val before = compose.onNodeWithTag("grid-parent").captureToImage().asAndroidBitmap()

        compose.onNodeWithTag("grid-choice-1").performClick().assertIsSelected()
        compose.onNodeWithTag("grid-choice-0").assertIsNotSelected()
        val after = compose.onNodeWithTag("grid-parent").captureToImage().asAndroidBitmap()

        assertBoundsUnchanged(initial, optionBounds(), "selection visual treatment")
        assertTrue("selected label/icon treatment should change pixels", changedPixelCount(before, after) > 20)
    }

    @Test
    fun unequalFirstAllocationIsMeasuredAndPlacedWithoutOverflow() {
        render(listOf("Long inspection answer text", "OK", "Short", "Other"), widthDp = 360)

        assertUnequalHorizontalRow("grid-choice-0", "grid-choice-1")
    }

    @Test
    fun unequalSecondAllocationIsMeasuredAndPlacedWithoutOverflow() {
        render(listOf("Short", "Long inspection answer text", "Other", "OK"), widthDp = 360)

        assertUnequalHorizontalRow("grid-choice-0", "grid-choice-1")
    }

    @Test
    fun notApplicableAndOkKeepOneLineLabelsAndSubstantialTouchSurfaces() {
        render(listOf("Not applicable", "OK", "Short", "Other"), widthDp = 230)

        val grid = bounds("grid-parent")
        val notApplicable = bounds("grid-choice-0")
        val ok = bounds("grid-choice-1")
        assertTrue("Not applicable should receive the larger allocation", notApplicable.width > ok.width)
        assertTrue("OK should retain a substantial touch surface: grid=$grid ok=$ok", ok.width >= grid.width * 0.30f)
        assertInside(grid, notApplicable)
        assertInside(grid, ok)
        assertTrue("Not applicable must remain one line", notApplicable.height <= dp(52f))
        assertTrue("OK must remain one line", ok.height <= dp(52f))
    }

    @Test
    fun notCheckedAndIssueFoundKeepAsymmetricBoundsInsideGrid() {
        render(listOf("Short", "OK", "Not checked", "Issue found"), widthDp = 240)

        assertUnequalHorizontalRow("grid-choice-2", "grid-choice-3")
        val notChecked = bounds("grid-choice-2")
        val issueFound = bounds("grid-choice-3")
        assertTrue("Not checked should remain one line: $notChecked", notChecked.height <= dp(52f))
        assertTrue("Issue found should remain one line: $issueFound", issueFound.height <= dp(52f))
    }

    @Test
    fun ordinaryPortraitWidthKeepsTheAdoptedTwoByTwoOrder() {
        render(listOf("Not applicable", "OK", "Not checked", "Issue found"), widthDp = 320)

        val grid = bounds("grid-parent")
        val choices = (0..3).map { bounds("grid-choice-$it") }
        assertEquals("first row should remain horizontal", choices[0].top, choices[1].top, 1.5f)
        assertEquals("second row should remain horizontal", choices[2].top, choices[3].top, 1.5f)
        assertTrue("second row should follow first row", choices[2].top > choices[0].bottom)
        choices.forEach { assertInside(grid, it) }
    }

    @Test
    fun firstPairFallsBackIndependentlyWhenOnlyFirstPairCannotFit() {
        render(listOf("Long inspection answer text", "Short", "Second", "OK"), widthDp = 220)

        val grid = bounds("grid-parent")
        val first = bounds("grid-choice-0")
        val second = bounds("grid-choice-1")
        val third = bounds("grid-choice-2")
        val fourth = bounds("grid-choice-3")
        assertTrue("first pair should stack", second.top > first.bottom)
        assertEquals(third.top, fourth.top, 1.5f)
        assertEquals("grid=$grid third=$third fourth=$fourth", grid.width, fourth.right - third.left, 1.5f)
        assertInside(grid, first)
        assertInside(grid, second)
        assertInside(grid, third)
        assertInside(grid, fourth)
    }

    @Test
    fun secondPairFallsBackIndependentlyWhenOnlySecondPairCannotFit() {
        render(listOf("First", "OK", "Long inspection answer text", "Short"), widthDp = 220)

        val grid = bounds("grid-parent")
        val first = bounds("grid-choice-0")
        val second = bounds("grid-choice-1")
        val third = bounds("grid-choice-2")
        val fourth = bounds("grid-choice-3")
        assertEquals("row choices should share a top edge: grid=$grid first=$first second=$second", first.top, second.top, 1.5f)
        assertTrue("second pair should stack", fourth.top > third.bottom)
        assertEquals("grid=$grid first=$first second=$second", grid.width, second.right - first.left, 1.5f)
        assertInside(grid, first)
        assertInside(grid, second)
        assertInside(grid, third)
        assertInside(grid, fourth)
    }

    @Test
    fun bothPairsFallBackToOneFullWidthStackWithoutReordering() {
        render(
            listOf(
                "Long inspection answer text",
                "Long inspection answer text",
                "Long inspection answer text",
                "Long inspection answer text",
            ),
            widthDp = 220,
        )

        val grid = bounds("grid-parent")
        val choices = (0..3).map { bounds("grid-choice-$it") }
        assertTrue("all four choices should stack", choices.zipWithNext().all { (previous, next) -> next.top > previous.bottom })
        choices.forEach { choice ->
            assertEquals(grid.width, choice.width, 1.5f)
            assertInside(grid, choice)
        }
    }

    @Test
    fun representativeLargeFontAt320DpStaysInsideParentWithoutHorizontalOverflow() {
        render(listOf("Not applicable", "OK", "Not checked", "Issue found"), widthDp = 320, fontScale = 2f)

        val grid = bounds("grid-parent")
        (0..3).forEach { index ->
            val choice = bounds("grid-choice-$index")
            assertInside(grid, choice)
            assertTrue("choice $index must retain a visible positive width", choice.width > 0f)
        }
    }

    @Test
    fun capturesFocusedOwnerCorrectionInspectionStates() {
        data class CaptureCase(val name: String, val labels: List<String>, val widthDp: Int, val darkTheme: Boolean = false)
        val cases = listOf(
            CaptureCase("inspection-na-ok-asymmetric.png", listOf("Not applicable", "OK", "Short", "Other"), 230),
            CaptureCase("inspection-not-checked-issue-asymmetric.png", listOf("Short", "OK", "Not checked", "Issue found"), 240),
            CaptureCase("inspection-first-row-stack.png", listOf("Long inspection answer text", "Short", "Second", "OK"), 220),
            CaptureCase("inspection-na-ok-asymmetric-dark.png", listOf("Not applicable", "OK", "Short", "Other"), 230, darkTheme = true),
        )
        val caseIndex = mutableStateOf(0)
        compose.setContent {
            val current = cases[caseIndex.value]
            ServiceLoopTheme(darkTheme = current.darkTheme) {
                val density = LocalDensity.current
                renderedDensity = density.density
                CompositionLocalProvider(LocalDensity provides Density(density.density, 1f)) {
                    Box(Modifier.width(current.widthDp.dp).testTag("grid-parent")) {
                        ServiceLoopInspectionStatusGrid(
                            choices = current.labels.mapIndexed { index, label ->
                                InspectionStatusChoice(index.toString(), label, ServiceLoopIcons.Circle, Color(0xFF08666B), "grid-choice-$index")
                            },
                            selectedKey = "0",
                            onSelected = {},
                        )
                    }
                }
            }
        }
        cases.forEachIndexed { index, current ->
            compose.waitForIdle()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.waitForIdleSync()
            val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "serviceloop-owner-correction")
            check(directory.exists() || directory.mkdirs())
            File(directory, current.name).outputStream().use { output ->
                check(compose.onNodeWithTag("grid-parent").captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            if (index < cases.lastIndex) compose.runOnIdle { caseIndex.value = index + 1 }
        }
    }

    private fun render(labels: List<String>, widthDp: Int, fontScale: Float = 1f, darkTheme: Boolean = false) {
        compose.setContent {
            ServiceLoopTheme(darkTheme = darkTheme) {
                val density = LocalDensity.current
                renderedDensity = density.density
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.width(widthDp.dp).testTag("grid-parent")) {
                        ServiceLoopInspectionStatusGrid(
                            choices = labels.mapIndexed { index, label ->
                                InspectionStatusChoice(
                                    key = index.toString(),
                                    label = label,
                                    icon = ServiceLoopIcons.Circle,
                                    semanticColor = Color(0xFF08666B),
                                    testTag = "grid-choice-$index",
                                )
                            },
                            selectedKey = "0",
                            onSelected = {},
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun renderSelectable(
        labels: List<String>,
        widthDp: Int,
        fontScale: Float = 1f,
        initialSelection: Int = 0,
    ) {
        selectedChoice = mutableStateOf(initialSelection.toString())
        compose.setContent {
            ServiceLoopTheme {
                val density = LocalDensity.current
                renderedDensity = density.density
                CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                    Box(Modifier.width(widthDp.dp).testTag("grid-parent")) {
                        ServiceLoopInspectionStatusGrid(
                            choices = labels.mapIndexed { index, label ->
                                InspectionStatusChoice(
                                    key = index.toString(),
                                    label = label,
                                    icon = ServiceLoopIcons.Circle,
                                    semanticColor = Color(0xFF08666B),
                                    testTag = "grid-choice-$index",
                                )
                            },
                            selectedKey = selectedChoice.value,
                            onSelected = { selectedChoice.value = it },
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun optionBounds() = (0..3).map { bounds("grid-choice-$it") }

    private fun pairMode(bounds: List<androidx.compose.ui.geometry.Rect>, firstIndex: Int): String =
        if (kotlin.math.abs(bounds[firstIndex].top - bounds[firstIndex + 1].top) <= 1.5f) "horizontal" else "vertical"

    private fun assertBoundsUnchanged(
        expected: List<androidx.compose.ui.geometry.Rect>,
        actual: List<androidx.compose.ui.geometry.Rect>,
        label: String,
    ) {
        assertEquals("$label should preserve all four options", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (before, after) ->
            assertEquals("$label option $index left", before.left, after.left, 0.5f)
            assertEquals("$label option $index top", before.top, after.top, 0.5f)
            assertEquals("$label option $index width", before.width, after.width, 0.5f)
            assertEquals("$label option $index height", before.height, after.height, 0.5f)
        }
    }

    private fun changedPixelCount(before: Bitmap, after: Bitmap): Int {
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        var changed = 0
        for (y in 0 until before.height) {
            for (x in 0 until before.width) {
                if (before.getPixel(x, y) != after.getPixel(x, y)) changed++
            }
        }
        return changed
    }

    private fun assertUnequalHorizontalRow(firstTag: String, secondTag: String) {
        val grid = bounds("grid-parent")
        val first = bounds(firstTag)
        val second = bounds(secondTag)
        assertEquals("row choices should share a top edge: grid=$grid first=$first second=$second", first.top, second.top, 1.5f)
        assertTrue("the row must use an asymmetric allocation: grid=$grid first=$first second=$second", kotlin.math.abs(first.width - second.width) > 1.5f)
        assertEquals("grid=$grid first=$first second=$second", grid.width, second.right - first.left, 1.5f)
        assertTrue("the first choice must end before the second starts", first.right <= second.left + 1.5f)
        assertInside(grid, first)
        assertInside(grid, second)
    }

    private fun bounds(tag: String) = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot

    private fun assertInside(parent: androidx.compose.ui.geometry.Rect, child: androidx.compose.ui.geometry.Rect) {
        assertTrue("child left out of parent: $child vs $parent", child.left >= parent.left - 1.5f)
        assertTrue("child right out of parent: $child vs $parent", child.right <= parent.right + 1.5f)
        assertTrue("child top out of parent: $child vs $parent", child.top >= parent.top - 1.5f)
        assertTrue("child bottom out of parent: $child vs $parent", child.bottom <= parent.bottom + 1.5f)
    }

    private fun dp(value: Float): Float = value * renderedDensity
}
