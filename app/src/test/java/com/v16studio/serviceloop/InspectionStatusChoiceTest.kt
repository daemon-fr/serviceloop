package com.v16studio.serviceloop

import com.v16studio.serviceloop.ui.designsystem.allocateInspectionChoiceWidths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
