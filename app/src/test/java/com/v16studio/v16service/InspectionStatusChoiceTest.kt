package com.v16studio.v16service

import com.v16studio.v16service.ui.designsystem.allocateInspectionChoiceWidths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class InspectionStatusChoiceTest {
    @Test fun balancedOptionsStayFiftyFiftyWhenBothComfortablyFit() {
        val allocation = allocateInspectionChoiceWidths(320, 8, 48, 48)
        assertFalse(allocation.vertical)
        assertEquals(156, allocation.firstWidth)
        assertEquals(156, allocation.secondWidth)
    }

    @Test fun longerFirstOptionExpandsOnlyAsMuchAsNeeded() {
        val allocation = allocateInspectionChoiceWidths(320, 8, 180, 48)
        assertFalse(allocation.vertical)
        assertEquals(180, allocation.firstWidth)
        assertEquals(132, allocation.secondWidth)
    }

    @Test fun longerSecondOptionIsHandledSymmetrically() {
        val allocation = allocateInspectionChoiceWidths(320, 8, 48, 180)
        assertFalse(allocation.vertical)
        assertEquals(132, allocation.firstWidth)
        assertEquals(180, allocation.secondWidth)
    }

    @Test fun comfortFloorRelaxesBeforeOneLineRequirement() {
        val allocation = allocateInspectionChoiceWidths(320, 8, 201, 110)
        assertFalse(allocation.vertical)
        assertEquals(201, allocation.firstWidth)
        assertEquals(111, allocation.secondWidth)
    }

    @Test fun impossibleOneLinePairFallsBackToVertical() {
        assertTrue(allocateInspectionChoiceWidths(320, 8, 190, 140).vertical)
    }

    @Test fun allocationConsumesWidthAfterGap() {
        val allocation = allocateInspectionChoiceWidths(360, 8, 90, 110)
        assertEquals(352, allocation.firstWidth + allocation.secondWidth)
    }

    @Test fun selectionDoesNotSwitchPairModeNearAllocationThreshold() {
        data class RequirementScenario(
            val name: String,
            val fontScale: Float,
            val firstRequiredWidth: Int,
            val secondRequiredWidth: Int,
        )

        val scenarios = listOf(
            RequirementScenario("normal", 1f, 180, 48),
            RequirementScenario("large-label", 1.3f, (180 * 1.3f).roundToInt(), (48 * 1.3f).roundToInt()),
            RequirementScenario("large-font", 2f, 180 * 2, 48 * 2),
        )
        val sweptWidths = 280..360

        scenarios.forEach { scenario ->
            val threshold = 8 + scenario.firstRequiredWidth + scenario.secondRequiredWidth
            val boundaryWidths = listOf(threshold - 1, threshold, threshold + 1)
            val widths = (sweptWidths + boundaryWidths).distinct().sorted()
            val allocations = widths.map { width ->
                allocateInspectionChoiceWidths(
                    rowWidth = width,
                    gap = 8,
                    firstRequiredWidth = scenario.firstRequiredWidth,
                    secondRequiredWidth = scenario.secondRequiredWidth,
                )
            }

            assertTrue("${scenario.name}: width immediately below threshold must stack", allocations[widths.indexOf(threshold - 1)].vertical)
            assertFalse("${scenario.name}: exact threshold must fit horizontally", allocations[widths.indexOf(threshold)].vertical)
            assertFalse("${scenario.name}: width above threshold must fit horizontally", allocations[widths.indexOf(threshold + 1)].vertical)
            assertTrue(
                "${scenario.name}: horizontal mode must remain stable after the threshold",
                allocations.zipWithNext().all { (before, after) -> !after.vertical || before.vertical },
            )

            widths.forEachIndexed { index, width ->
                val initial = allocations[index]
                val afterSelection = allocateInspectionChoiceWidths(
                    rowWidth = width,
                    gap = 8,
                    firstRequiredWidth = scenario.firstRequiredWidth,
                    secondRequiredWidth = scenario.secondRequiredWidth,
                )
                assertEquals("${scenario.name} selection changed allocation at ${width}px", initial, afterSelection)
                if (!initial.vertical) assertEquals(width - 8, initial.firstWidth + initial.secondWidth)
            }
        }
    }
}
