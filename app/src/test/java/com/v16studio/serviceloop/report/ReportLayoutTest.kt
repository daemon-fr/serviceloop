package com.v16studio.serviceloop.report

import com.v16studio.serviceloop.domain.PublicChecklistItem
import com.v16studio.serviceloop.domain.PublicReportModel
import com.v16studio.serviceloop.domain.PublicDispatchProvenance
import com.v16studio.serviceloop.domain.PublicWorkLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReportLayoutTest {
    @Test fun measuredWrappingSplitsLongUnbrokenTokensWithinPrintableWidth() {
        val token = "S".repeat(120)
        val pages = FixedServiceRecordPdf.layout(report(listOf(line(1, token, "Routine work"))))
        val lines = pages.flatMap { it.lines }

        assertTrue(lines.all { FixedServiceRecordPdf.measuredWidth(it) <= FixedServiceRecordPdf.CONTENT_WIDTH + 0.01f })
        val tokenPieces = lines.map { it.text }.filter { text -> text.isNotEmpty() && text.all { it == 'S' } }
        assertEquals(token, tokenPieces.joinToString(""))
        assertTrue(tokenPieces.size > 1)
    }

    @Test fun headingHeavyAndLongContentStayAboveFooterAndPaginateWithoutOrphanHeadings() {
        val longFinding = (1..220).joinToString(" ") { "Detailed public finding sentence $it." }
        val lines = (1..55).map { line(it, "Maker Model Serial-$it", longFinding) }
        val pages = FixedServiceRecordPdf.layout(report(lines))

        assertTrue(pages.size > 2)
        assertTrue(pages.all { it.contentHeight <= FixedServiceRecordPdf.CONTENT_HEIGHT })
        assertTrue(pages.flatMap { it.lines }.all { FixedServiceRecordPdf.measuredWidth(it) <= FixedServiceRecordPdf.CONTENT_WIDTH + 0.01f })
        assertFalse(pages.any { it.lines.lastOrNull()?.style in setOf(FixedServiceRecordPdf.LineStyle.SECTION, FixedServiceRecordPdf.LineStyle.SUBSECTION, FixedServiceRecordPdf.LineStyle.TABLE_HEADER) })
    }

    @Test fun workItemSubsectionsAndTableHeadersKeepFollowingContentOnTheSamePage() {
        val pages = FixedServiceRecordPdf.layout(report((1..40).map { number -> line(number, "Maker Model Serial-$number", "Routine work") }))

        assertTrue(pages.size > 2)
        pages.forEach { page ->
            page.lines.forEachIndexed { index, current ->
                when (current.style) {
                    FixedServiceRecordPdf.LineStyle.SUBSECTION -> assertTrue(index + 1 < page.lines.size)
                    FixedServiceRecordPdf.LineStyle.TABLE_HEADER -> assertTrue(index + 1 < page.lines.size && page.lines[index + 1].style == FixedServiceRecordPdf.LineStyle.TABLE_ROW)
                    else -> Unit
                }
            }
        }
        val checklistPage = pages.first { page -> page.lines.any { it.text == "Inspection checklist" } }
        val checklistIndex = checklistPage.lines.indexOfFirst { it.text == "Inspection checklist" }
        assertTrue(checklistIndex + 1 < checklistPage.lines.size)
        assertEquals(FixedServiceRecordPdf.LineStyle.TABLE_HEADER, checklistPage.lines[checklistIndex + 1].style)
    }

    @Test fun longChecklistStillSpansPagesAndKeepsEveryPageWithinFooterBoundary() {
        val checklist = (1..120).map { position -> PublicChecklistItem(position, "Check $position", "STATUS", null, true, "OK", null, null) }
        val pages = FixedServiceRecordPdf.layout(report(listOf(line(1, "Pump", "Routine work").copy(checklist = checklist))))

        assertTrue(pages.size > 2)
        assertTrue(pages.all { it.contentHeight <= FixedServiceRecordPdf.CONTENT_HEIGHT })
        assertTrue(pages.flatMap { it.lines }.count { it.style == FixedServiceRecordPdf.LineStyle.TABLE_ROW } >= 120)
    }

    @Test fun sectionMovesWithSubsectionAndFirstBodyLineAtExactBoundary() {
        val boundary = (0..1000).firstNotNullOfOrNull { paddingWords ->
            val pages = FixedServiceRecordPdf.layout(
                report(listOf(line(1, "Maker Model Serial-1", "Routine work"))).copy(
                    businessContact = "Contact ${"padding ".repeat(paddingWords)}".trim(),
                ),
            )
            val sectionPageIndex = pages.indexOfFirst { page ->
                page.lines.any { it.style == FixedServiceRecordPdf.LineStyle.SECTION && it.text == "Work completed" }
            }
            if (sectionPageIndex <= 0) return@firstNotNullOfOrNull null

            val remainingHeight = FixedServiceRecordPdf.CONTENT_HEIGHT - pages[sectionPageIndex - 1].contentHeight
            if (
                remainingHeight >= FixedServiceRecordPdf.LineStyle.SECTION.height + FixedServiceRecordPdf.LineStyle.SUBSECTION.height &&
                remainingHeight < FixedServiceRecordPdf.LineStyle.SECTION.height + FixedServiceRecordPdf.LineStyle.SUBSECTION.height + FixedServiceRecordPdf.LineStyle.BODY.height
            ) {
                pages to sectionPageIndex
            } else {
                null
            }
        } ?: error("Could not construct the requested SECTION/SUBSECTION boundary")

        val pages = boundary.first
        val sectionPage = pages[boundary.second]
        val sectionIndex = sectionPage.lines.indexOfFirst {
            it.style == FixedServiceRecordPdf.LineStyle.SECTION && it.text == "Work completed"
        }

        assertTrue(sectionIndex >= 0)
        assertEquals(FixedServiceRecordPdf.LineStyle.SUBSECTION, sectionPage.lines[sectionIndex + 1].style)
        assertTrue(sectionPage.lines[sectionIndex + 1].text.startsWith("01 ·"))
        assertEquals(FixedServiceRecordPdf.LineStyle.BODY, sectionPage.lines[sectionIndex + 2].style)
        assertTrue(sectionPage.lines[sectionIndex + 2].text.startsWith("Equipment identification:"))
        assertFalse(
            pages[boundary.second - 1].lines.last().style in setOf(
                FixedServiceRecordPdf.LineStyle.SECTION,
                FixedServiceRecordPdf.LineStyle.SUBSECTION,
                FixedServiceRecordPdf.LineStyle.TABLE_HEADER,
            ),
        )
    }

    @Test fun customerReportUsesOnlyShortTechnicianReference() {
        val fullId="12345678-1234-1234-1234-123456789abc"
        val model=report(listOf(line(1,"Pump","Serviced").copy(dispatchItemId="dispatch-item",dispatchAssignment="Alex"))).copy(dispatch=PublicDispatchProvenance("dispatch-visit",2,"JOB-7","Office",fullId,"John"))
        val text=FixedServiceRecordPdf.layout(model).flatMap{it.lines}.joinToString("\n"){it.text}
        assertTrue(text.contains("Documented by: John"));assertTrue(text.contains("Technician reference: 12345678"));assertFalse(text.contains(fullId));assertFalse(text.contains("Dispatch item:"));assertTrue(text.contains("Inspection checklist"));assertTrue(text.contains("Findings & follow-up"))
    }

    @Test fun flexibleSubjectsRenderOnlyTheRelevantEquipmentContent() {
        val site = PublicWorkLine(
            position = 1, equipmentName = null, equipmentReference = null, equipmentIdentification = null,
            serviceName = "General premises inspection", outcome = "PERFORMED", publicWorkNote = null,
            notPerformedReason = null, fulfilledObligation = false, oldDueDate = null, nextDueDate = null,
            checklist = emptyList(), subjectType = com.v16studio.serviceloop.domain.WorkSubjectType.SITE,
        )
        val unidentified = site.copy(
            position = 2, serviceName = "Ad-hoc equipment inspection",
            subjectType = com.v16studio.serviceloop.domain.WorkSubjectType.EQUIPMENT,
            equipmentDescription = "Copy machine beside back-office desk",
        )
        val known = site.copy(
            position = 3, serviceName = "Known equipment service",
            subjectType = com.v16studio.serviceloop.domain.WorkSubjectType.EQUIPMENT,
            equipmentName = "Copy machine", equipmentReference = "EQ-7", equipmentIdentification = "ID-7",
        )

        val siteText = FixedServiceRecordPdf.layout(report(listOf(site))).flatMap { it.lines }.joinToString("\n") { it.text }
        val unidentifiedText = FixedServiceRecordPdf.layout(report(listOf(unidentified))).flatMap { it.lines }.joinToString("\n") { it.text }
        val knownText = FixedServiceRecordPdf.layout(report(listOf(known))).flatMap { it.lines }.joinToString("\n") { it.text }

        assertTrue(siteText.contains("Service: General premises inspection"))
        assertFalse(siteText.contains("null")); assertFalse(siteText.contains("Equipment identification:")); assertFalse(siteText.contains("EQ-")); assertFalse(siteText.contains("Equipment "))
        assertTrue(unidentifiedText.contains("Copy machine beside back-office desk"))
        assertTrue(unidentifiedText.contains("Service: Ad-hoc equipment inspection"))
        assertFalse(unidentifiedText.contains("null")); assertFalse(unidentifiedText.contains("Equipment identification:")); assertFalse(unidentifiedText.contains("EQ-"))
        assertTrue(knownText.contains("EQ-7 · Copy machine")); assertTrue(knownText.contains("Equipment identification: ID-7")); assertTrue(knownText.contains("Service: Known equipment service")); assertFalse(knownText.contains("null"))
    }

    private fun report(lines: List<PublicWorkLine>) = PublicReportModel(
        "record", "revision", 1, "V-LAYOUT", "2026-09-05", 1,
        "Service Business", "Technician", "service@example.invalid", "Customer", "Site", "Address", lines, "CU-1", "ST-1",
    )

    private fun line(position: Int, identification: String, work: String) = PublicWorkLine(
        position, "Equipment $position", "EQ-$position", identification, "Condition inspection", "PARTLY_PERFORMED", work, null, false,
        "2026-09-01", null,
        listOf(PublicChecklistItem(1, "Detailed finding", "STATUS", null, true, "ISSUE_FOUND", null, work)),
        "P-$position", true,
    )
}
