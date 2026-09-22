package com.v16studio.serviceloop

import com.v16studio.serviceloop.report.technicianReportName
import org.junit.Assert.assertEquals
import org.junit.Test

class TechnicianReportNameTest {
    @Test fun designationIsAppendedWithOneSeparatorAndBlankValueIsOmitted() {
        assertEquals("Alex Dobre · Team Leader", technicianReportName("Alex Dobre", "Team Leader"))
        assertEquals("Alex Dobre", technicianReportName("Alex Dobre", null))
        assertEquals("Alex Dobre", technicianReportName("Alex Dobre", "   "))
    }
}
