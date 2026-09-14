package com.v16studio.serviceloop

import com.v16studio.serviceloop.domain.ServiceDocumentationMode
import com.v16studio.serviceloop.domain.ServiceEntryStatus
import com.v16studio.serviceloop.domain.ServiceProgressItem
import com.v16studio.serviceloop.domain.VisitServiceProgress
import com.v16studio.serviceloop.domain.WorkSubjectType
import com.v16studio.serviceloop.domain.serviceDocumentationMode
import com.v16studio.serviceloop.domain.serviceEntryStatus
import com.v16studio.serviceloop.domain.serviceProgressGroups
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ServiceProgressTest {
    @Test fun preferredResumeHonorsAttentionThenLocalWorkThenChoice() {
        val items = listOf(
            item("leader", 1, ServiceEntryStatus.IN_PROGRESS, ServiceDocumentationMode.LEADER_OBSERVE),
            item("ready", 2, ServiceEntryStatus.READY),
            item("choice", 3, ServiceEntryStatus.NOT_STARTED, ServiceDocumentationMode.CHOICE_REQUIRED),
            item("attention", 4, ServiceEntryStatus.NEEDS_ATTENTION),
        )

        assertEquals("attention", progress(items).preferredResumeItem()?.workItemId)
        assertEquals(listOf("ready", "choice", "attention"), progress(items).actionableItems.map { it.workItemId })
    }

    @Test fun nextServicePrefersLaterSameEquipmentThenLaterAndEarlier() {
        val items = listOf(
            item("site", 1, ServiceEntryStatus.NOT_STARTED, subject = WorkSubjectType.SITE),
            item("a1", 2, ServiceEntryStatus.READY, equipmentId = "a"),
            item("b", 3, ServiceEntryStatus.IN_PROGRESS, equipmentId = "b"),
            item("a2", 4, ServiceEntryStatus.NOT_STARTED, equipmentId = "a"),
            item("a3", 5, ServiceEntryStatus.NOT_STARTED, equipmentId = "a"),
        )

        assertEquals("a2", progress(items).nextService("a1")?.workItemId)
        assertEquals("a3", progress(items).nextService("a2")?.workItemId)
        assertEquals("site", progress(items).nextService("a3")?.workItemId)
    }

    @Test fun groupIdentityUsesStableEquipmentOrWorkItemIdentity() {
        val known = item("known", 1, ServiceEntryStatus.NOT_STARTED, equipmentId = "equipment-7", equipmentName = "Boiler", equipmentReference = "EQ-7")
        val knownLater = item("known-later", 2, ServiceEntryStatus.NOT_STARTED, equipmentId = "equipment-7", equipmentName = "Boiler", equipmentReference = "EQ-7")
        val unknownA = item("unknown-a", 3, ServiceEntryStatus.NOT_STARTED, equipmentDescription = "Boiler")
        val unknownB = item("unknown-b", 4, ServiceEntryStatus.NOT_STARTED, equipmentDescription = "Boiler")
        val blankUnknown = item("unknown-blank", 5, ServiceEntryStatus.NOT_STARTED, equipmentDescription = " ")
        val site = item("site", 6, ServiceEntryStatus.NOT_STARTED, subject = WorkSubjectType.SITE)
        val siteLater = item("site-later", 7, ServiceEntryStatus.NOT_STARTED, subject = WorkSubjectType.SITE)
        val grouped = progress(listOf(known, knownLater, unknownA, unknownB, blankUnknown, site, siteLater))
        assertEquals(listOf("EQUIPMENT:equipment-7", "WORK:unknown-a", "WORK:unknown-b", "WORK:unknown-blank", "SITE"), grouped.groups.map { it.key })
        assertEquals("EQ-7 · Boiler", grouped.groups.first().label)
        assertEquals(listOf("known", "known-later"), grouped.groups.first().items.map { it.workItemId })
        assertEquals("Boiler", grouped.groupFor("unknown-a")?.label)
        assertEquals("Equipment not specified", grouped.groupFor("unknown-blank")?.label)
        assertEquals(listOf("site", "site-later"), grouped.groupFor("site")?.items?.map { it.workItemId })
        assertSame(unknownA, grouped.groupFor("unknown-a")?.items?.single())
    }

    @Test fun entryStatusKeepsStartedNoChecklistSeparateFromNotStarted() {
        assertEquals(ServiceEntryStatus.NOT_STARTED, serviceEntryStatus(hasActivity = false, checklistComplete = true))
        assertEquals(ServiceEntryStatus.READY, serviceEntryStatus(hasActivity = true, checklistComplete = true))
        assertEquals(ServiceEntryStatus.IN_PROGRESS, serviceEntryStatus(hasActivity = true, checklistComplete = false))
        assertEquals(ServiceEntryStatus.NEEDS_ATTENTION, serviceEntryStatus(hasActivity = true, checklistComplete = true, hasUnresolvedRawBuffer = true))
        assertEquals(ServiceEntryStatus.NEEDS_ATTENTION, serviceEntryStatus(hasActivity = true, checklistComplete = true, hasMissingIssueDescription = true))
    }

    @Test fun documentationModeNeverInfersLocalEditingForHandedOffWork() {
        assertEquals(ServiceDocumentationMode.LOCAL, serviceDocumentationMode(null, null))
        assertEquals(ServiceDocumentationMode.LOCAL, serviceDocumentationMode("ASSIGNED", "DOCUMENT_LOCAL"))
        assertEquals(ServiceDocumentationMode.CHOICE_REQUIRED, serviceDocumentationMode("ASSIGNED", "PENDING"))
        assertEquals(ServiceDocumentationMode.LEADER_OBSERVE, serviceDocumentationMode("LEADER_VISIBLE", null))
        assertEquals(ServiceDocumentationMode.DEFERRED, serviceDocumentationMode("ASSIGNED", "DEFERRED"))
    }

    @Test fun documentationModeHonorsDeferredAndAssignmentRemovalPrecedence() {
        assertEquals(ServiceDocumentationMode.DEFERRED, serviceDocumentationMode("LEADER_VISIBLE", "DEFERRED"))
        assertEquals(ServiceDocumentationMode.DEFERRED, serviceDocumentationMode("ASSIGNMENT_REMOVED", "DOCUMENT_LOCAL"))
        assertEquals(ServiceDocumentationMode.DEFERRED, serviceDocumentationMode("ASSIGNED", "ASSIGNMENT_REMOVED"))
        assertEquals(ServiceDocumentationMode.CHOICE_REQUIRED, serviceDocumentationMode("LEADER_VISIBLE", "PENDING"))
        assertEquals(ServiceDocumentationMode.LEADER_OBSERVE, serviceDocumentationMode("LEADER_VISIBLE", "LEADER_OBSERVE"))
        assertEquals(ServiceDocumentationMode.DEFERRED, serviceDocumentationMode("ASSIGNED", "UNKNOWN"))
    }

    private fun progress(items: List<ServiceProgressItem>) = VisitServiceProgress("visit", "V-1", "Customer", "Site", "2026-09-14", items, serviceProgressGroups(items))

    private fun item(
        id: String,
        position: Int,
        status: ServiceEntryStatus,
        mode: ServiceDocumentationMode = ServiceDocumentationMode.LOCAL,
        equipmentId: String? = null,
        equipmentName: String? = null,
        equipmentReference: String? = null,
        equipmentDescription: String? = null,
        subject: WorkSubjectType = WorkSubjectType.EQUIPMENT,
    ) = ServiceProgressItem(id, position, subject, equipmentId, equipmentName, equipmentReference, equipmentDescription, id, status, mode)
}
