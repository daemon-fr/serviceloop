package com.v16studio.v16service

import com.v16studio.v16service.report.technicianReportName
import org.junit.Assert.assertEquals
import org.junit.Test

class TechnicianReportNameTest {
    @Test fun designationIsAppendedWithOneSeparatorAndBlankValueIsOmitted() {
        assertEquals("Alex Dobre · Team Leader", technicianReportName("Alex Dobre", "Team Leader"))
        assertEquals("Alex Dobre", technicianReportName("Alex Dobre", null))
        assertEquals("Alex Dobre", technicianReportName("Alex Dobre", "   "))
    }
}
