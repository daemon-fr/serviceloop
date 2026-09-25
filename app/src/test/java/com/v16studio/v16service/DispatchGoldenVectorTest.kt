package com.v16studio.v16service

import com.v16studio.v16service.data.DispatchInspectionItem
import com.v16studio.v16service.data.DispatchInspectionSnapshot
import com.v16studio.v16service.data.DispatchPackageCodec
import com.v16studio.v16service.data.DispatchTeamSnapshot
import com.v16studio.v16service.data.DispatchTechnicianSnapshot
import com.v16studio.v16service.data.DispatchVisit
import com.v16studio.v16service.data.DispatchWork
import com.v16studio.v16service.domain.WorkSubjectType
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Literal compatibility vectors for the material representation accepted before CP1.
 * The values were derived from the data-class toString() codec in 041a6c2 and are
 * deliberately not calculated from the current implementation at test time.
 */
class DispatchGoldenVectorTest {
    private val tech1 = DispatchTechnicianSnapshot("TECH-1", "Ana", "North")
    private val tech2 = DispatchTechnicianSnapshot("TECH-2", "Bogdan", null)
    private val tech3 = DispatchTechnicianSnapshot("TECH-3", "Cezar", "South")

    private fun ordinaryVisit() = DispatchVisit(
        "DV-GOLD-ORDINARY", 1, "JOB-ORDINARY", "2026-09-12", "09:30", "Europe/Bucharest", "ST-D", "Ring office",
        teams = listOf(DispatchTeamSnapshot("TEAM-1", "Field", listOf("TECH-1"), emptyList())),
        participants = listOf(tech1),
        leaderTechnicianIds = emptyList(),
        work = listOf(DispatchWork("ITEM-1", WorkSubjectType.EQUIPMENT, "EQ-D", null, "Annual service", "PLAN-1", "2026-09-12", listOf(tech1), null)),
    )

    private fun multiVisit() = DispatchVisit(
        "DV-GOLD-MULTI", 7, "JOB-MULTI", "2026-10-03", null, "Europe/Bucharest", "ST-M", "Call before arrival",
        teams = listOf(
            DispatchTeamSnapshot("TEAM-B", "Response", listOf("TECH-2", "TECH-1"), listOf("TECH-2")),
            DispatchTeamSnapshot("TEAM-A", "North", listOf("TECH-3", "TECH-1"), listOf("TECH-1")),
        ),
        participants = listOf(tech3, tech1, tech2),
        leaderTechnicianIds = listOf("TECH-2", "TECH-1"),
        work = listOf(
            DispatchWork("ITEM-2", WorkSubjectType.SITE, null, null, "Inspect site", null, null, listOf(tech2, tech1), null),
            DispatchWork("ITEM-1", WorkSubjectType.EQUIPMENT, "EQ-M", null, "Annual service", "PLAN-M", "2026-10-03", listOf(tech3, tech1), null),
        ),
    )

    private fun inspectionSnapshot() = DispatchInspectionSnapshot(
        "SNAP-GOLD-1", "Safety checklist", "TPL-SAFETY", 4,
        listOf(
            DispatchInspectionItem(1, "Guard intact", "STATUS", null, true, "Keep private"),
            DispatchInspectionItem(2, "Measured pressure", "NUMBER", "bar", false, null),
        ),
    )

    @Test
    fun materialHashMatchesCheckpointOneCompatibilityVectors() {
        val snapshot = inspectionSnapshot()
        assertEquals("f7bab8b823145a6445c4693d94431b926aa8fd4c2e37d9e7ee5b7dabe5b028c9", DispatchPackageCodec.materialHash(ordinaryVisit()))
        assertEquals("16928ad751fc7e4c1edfe3c9d354db5f7c08863ebb23fea1ffceb9ed98f8b040", DispatchPackageCodec.materialHash(multiVisit()))
        assertEquals(
            "e1f714104acf98d8a67756db7b41257bec5eedc69611660e1a5b57f0a21ce3cc",
            DispatchPackageCodec.materialHash(ordinaryVisit().copy(work = listOf(ordinaryVisit().work.single().copy(inspectionSnapshotId = snapshot.snapshotId))), listOf(snapshot)),
        )
        assertEquals(
            "8207f83d256c6b4da8bbd694c42debfbd5954fcc1b73162019cacf1e15d84fb6",
            DispatchPackageCodec.materialHash(ordinaryVisit().copy(transportLifecycle = "CANCELED", cancellationReason = "Customer unavailable")),
        )
    }

    @Test
    fun normalizedCollectionOrderRetainsTheMultiVisitGoldenHash() {
        val visit = multiVisit()
        val reordered = visit.copy(
            teams = visit.teams.reversed().map { it.copy(memberIds = it.memberIds.reversed(), leaderIds = it.leaderIds.reversed()) },
            participants = visit.participants.reversed(),
            leaderTechnicianIds = visit.leaderTechnicianIds.reversed(),
            work = visit.work.reversed().map { it.copy(assignedTechnicians = it.assignedTechnicians.reversed()) },
        )
        assertEquals("16928ad751fc7e4c1edfe3c9d354db5f7c08863ebb23fea1ffceb9ed98f8b040", DispatchPackageCodec.materialHash(reordered))
    }

    @Test
    fun contentAddressedSnapshotIdMatchesCheckpointOneCompatibilityVector() {
        assertEquals("snapshot-d895bfc1abcf8713356fbc38", DispatchPackageCodec.contentAddressedSnapshotId(inspectionSnapshot()))
    }
}
