package com.v16studio.v16service

import com.v16studio.v16service.domain.ServiceDocumentationMode
import com.v16studio.v16service.domain.ServiceEntryStatus
import com.v16studio.v16service.domain.ServiceProgressItem
import com.v16studio.v16service.domain.VisitServiceProgress
import com.v16studio.v16service.domain.WorkSubjectType
import com.v16studio.v16service.domain.serviceDocumentationMode
import com.v16studio.v16service.domain.serviceEntryStatus
import com.v16studio.v16service.domain.serviceProgressGroups
import com.v16studio.v16service.ui.progressSummary
import com.v16studio.v16service.ui.serviceProgressGroupSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ServiceProgressTest {
    @Test fun progressSummaryNamesTheThingBeingCountedAndPluralizesItsVerb() {
        assertEquals(
            "1 service needs attention",
            progress(listOf(item("attention", 1, ServiceEntryStatus.NEEDS_ATTENTION))).let(::progressSummary),
        )
        assertEquals(
            "2 services need attention",
            progress(listOf(
                item("attention-a", 1, ServiceEntryStatus.NEEDS_ATTENTION, equipmentId = "equipment"),
                item("attention-b", 2, ServiceEntryStatus.NEEDS_ATTENTION, equipmentId = "equipment"),
            )).let(::progressSummary),
        )
        assertEquals(
            "1 service ready for review · 1 service in progress",
            progress(listOf(
                item("working", 1, ServiceEntryStatus.IN_PROGRESS, equipmentId = "working-equipment"),
                item("ready", 2, ServiceEntryStatus.READY, equipmentId = "ready-equipment"),
            )).let(::progressSummary),
        )
    }

    @Test fun groupSummaryNamesTheGroupCountAndKeepsDocumentationOwnershipTruth() {
        val local = item("local", 1, ServiceEntryStatus.NEEDS_ATTENTION, equipmentId = "equipment")
        val observed = item("observed", 2, ServiceEntryStatus.NEEDS_ATTENTION, mode = ServiceDocumentationMode.LEADER_OBSERVE, equipmentId = "equipment")
        assertEquals("2 services · 1 service needs attention", serviceProgressGroupSummary(serviceProgressGroups(listOf(local, observed)).single()))
    }

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

    @Test fun entryStatusUsesActivityAttentionAndCanonicalReadiness() {
        assertEquals(ServiceEntryStatus.NOT_STARTED, serviceEntryStatus(hasActivity = false, completionReady = true))
        assertEquals(ServiceEntryStatus.IN_PROGRESS, serviceEntryStatus(hasActivity = true))
        assertEquals(ServiceEntryStatus.READY, serviceEntryStatus(hasActivity = true, completionReady = true))
        assertEquals(ServiceEntryStatus.NEEDS_ATTENTION, serviceEntryStatus(hasActivity = true, hasUnresolvedRawBuffer = true))
        assertEquals(ServiceEntryStatus.NEEDS_ATTENTION, serviceEntryStatus(hasActivity = true, hasMissingIssueDescription = true))
        assertEquals(ServiceEntryStatus.NEEDS_ATTENTION, serviceEntryStatus(hasActivity = true, hasInvalidExplicitAnswer = true, completionReady = true))
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
