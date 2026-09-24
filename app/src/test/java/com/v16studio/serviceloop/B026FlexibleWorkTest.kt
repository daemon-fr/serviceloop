package com.v16studio.serviceloop

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.serviceloop.data.*
import com.v16studio.serviceloop.domain.*
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class B026FlexibleWorkTest {
    private lateinit var db: ServiceLoopDatabase
    private lateinit var root: File
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime { override val zoneId = ZoneId.of("Europe/Bucharest"); override fun instant() = Instant.parse("2026-09-05T10:00:00Z") }
    private val repo get() = RoomServiceLoopRepository(db, time, attachmentRoot = root)

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, ServiceLoopDatabase::class.java).allowMainThreadQueries().build()
        ServiceLoopDatabase.configureStage4Tracking(db.openHelper.writableDatabase)
        ServiceLoopDatabase.configureReminderDefaults(db.openHelper.writableDatabase)
        root = File(context.cacheDir, "b026-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun close() { db.close(); root.deleteRecursively() }

    @Test fun standardPlanCreationRemainsAllowedAndOneTimePlanCreationIsRejected() = runTest {
        val ids = seedBranch()
        assertNotNull(repo.createPlan(ids.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01")))
        db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer(ids.customer)!!.copy(customerType = CustomerType.ONE_TIME.code))
        val beforePlans = db.serviceLoopDao().planCount()
        val failure = runCatching { repo.createPlan(ids.equipment, PlanInput("Blocked", 1, "YEARS", "2026-10-01")) }.exceptionOrNull()
        assertEquals("Recurring service requires a Standard customer", failure?.message)
        assertEquals(beforePlans, db.serviceLoopDao().planCount())
    }

    @Test fun oneTimeVisitCreatesItsOwnCustomerSiteAndLocalTasksAtomically() = runTest {
        val beforeCustomers = db.serviceLoopDao().customerCount()
        val beforeSites = db.serviceLoopDao().siteCount()
        val beforeVisits = db.serviceLoopDao().visitCount()
        val visitId = repo.createOneTimeVisit(
            OneTimeVisitInput("Walk-in customer", phone = "123", locationLabel = "Boiler room", address = "Service address"),
            listOf(
                AdHocWorkInput("Inspect room", WorkSubjectType.SITE),
                AdHocWorkInput("Identify pump", WorkSubjectType.EQUIPMENT, equipmentDescription = "Blue pump beside the heater"),
            ),
            "BOOKED",
            "2026-09-06",
        )

        val visit = db.serviceLoopDao().visit(visitId)!!
        val customer = db.serviceLoopDao().customer(visit.customerId)!!
        val site = db.serviceLoopDao().site(visit.siteId)!!
        val work = db.serviceLoopDao().visitWorkItems(visitId)
        assertEquals(CustomerType.ONE_TIME.code, customer.customerType)
        assertEquals(customer.id, site.customerId)
        assertEquals("Boiler room", site.name)
        assertEquals(listOf("Inspect room", "Identify pump"), work.map { it.serviceNameSnapshot })
        assertTrue(work.all { it.servicePlanId == null && it.capturedObligationId == null })
        assertEquals(beforeCustomers + 1, db.serviceLoopDao().customerCount())
        assertEquals(beforeSites + 1, db.serviceLoopDao().siteCount())
        assertEquals(beforeVisits + 1, db.serviceLoopDao().visitCount())
    }

    @Test fun newCustomerVisitAndRegisterCreationPersistTheRequestedCustomerType() = runTest {
        val standardVisit = repo.createNewCustomerVisit(
            NewCustomerVisitInput("Quick standard", locationLabel = "Standard site", customerType = CustomerType.STANDARD),
            listOf(AdHocWorkInput("Inspect standard", WorkSubjectType.SITE)),
            "BOOKED",
            "2026-09-06",
        )
        val oneTimeVisit = repo.createNewCustomerVisit(
            NewCustomerVisitInput("Quick one-time", locationLabel = "One-time site", customerType = CustomerType.ONE_TIME),
            listOf(AdHocWorkInput("Inspect one-time", WorkSubjectType.SITE)),
            "BOOKED",
            "2026-09-07",
        )
        val oneTimeCustomer = repo.createCustomerWithFirstSite(
            CustomerInput("Register one-time", customerType = CustomerType.ONE_TIME),
            SiteInput("Registered site", "1 Test Street", isDefault = true),
        ).first

        assertEquals(CustomerType.STANDARD.code, db.serviceLoopDao().customer(db.serviceLoopDao().visit(standardVisit)!!.customerId)!!.customerType)
        assertEquals(CustomerType.ONE_TIME.code, db.serviceLoopDao().customer(db.serviceLoopDao().visit(oneTimeVisit)!!.customerId)!!.customerType)
        assertEquals(CustomerType.ONE_TIME.code, db.serviceLoopDao().customer(oneTimeCustomer)!!.customerType)
        assertEquals(1, db.serviceLoopDao().visitWorkItems(standardVisit).size)
        assertEquals(1, db.serviceLoopDao().visitWorkItems(oneTimeVisit).size)
    }

    @Test fun customerTypeEditPreservesTheBranchAndAllowsOneTimeToStandard() = runTest {
        val customer = repo.createCustomer(CustomerInput("Convertible customer"))
        val site = repo.createSite(customer, SiteInput("Main site", "1 Test Street", isDefault = true))
        val equipment = repo.createEquipment(site, EquipmentInput("Unit"))

        repo.updateCustomer(customer, CustomerInput("Convertible customer", customerType = CustomerType.ONE_TIME))
        assertEquals(CustomerType.ONE_TIME, repo.customer(customer)!!.customerType)
        assertEquals(site, db.serviceLoopDao().sitesForCustomer(customer).single().id)
        assertEquals(site, db.serviceLoopDao().equipment(equipment)!!.siteId)

        repo.updateCustomer(customer, CustomerInput("Convertible customer", customerType = CustomerType.STANDARD))
        assertEquals(CustomerType.STANDARD, repo.customer(customer)!!.customerType)
        assertEquals(site, db.serviceLoopDao().sitesForCustomer(customer).single().id)
        assertEquals(equipment, db.serviceLoopDao().equipmentForSite(site).single().id)
    }

    @Test fun standardToOneTimeIsBlockedForAnyPersistedPlanStateWithoutPartialMutation() = runTest {
        val ids = seedBranch()
        val planId = repo.createPlan(ids.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01"))
        val originalCustomer = db.serviceLoopDao().customer(ids.customer)!!
        val originalPlan = db.serviceLoopDao().plan(planId)!!

        listOf("ACTIVE", "PAUSED", "ENDED").forEach { planState ->
            db.serviceLoopDao().updatePlan(originalPlan.copy(state = planState))
            val failure = runCatching {
                repo.updateCustomer(ids.customer, CustomerInput("Customer", customerType = CustomerType.ONE_TIME))
            }.exceptionOrNull()
            assertEquals("A customer with recurring service plans cannot be marked one-time", failure?.message)
            assertEquals(originalCustomer, db.serviceLoopDao().customer(ids.customer))
            assertEquals(planState, db.serviceLoopDao().plan(planId)!!.state)
            assertEquals(originalPlan.currentObligationId, db.serviceLoopDao().plan(planId)!!.currentObligationId)
        }
        assertEquals("This customer has recurring service plans and cannot be marked one-time.", repo.customer(ids.customer)!!.oneTimeBlockReason)
    }

    @Test fun oneTimeSiteNameUsesAddressThenCustomerFallback() = runTest {
        val withAddress = repo.createOneTimeVisit(OneTimeVisitInput("Ion Popescu", address = "Strada Exemplu 10"), listOf(AdHocWorkInput("Inspect", WorkSubjectType.SITE)), "BOOKED", "2026-09-06")
        val withoutAddress = repo.createOneTimeVisit(OneTimeVisitInput("Ion Popescu"), listOf(AdHocWorkInput("Inspect", WorkSubjectType.SITE)), "BOOKED", "2026-09-07")
        assertEquals("Strada Exemplu 10", db.serviceLoopDao().site(db.serviceLoopDao().visit(withAddress)!!.siteId)!!.name)
        assertEquals("Ion Popescu", db.serviceLoopDao().site(db.serviceLoopDao().visit(withoutAddress)!!.siteId)!!.name)
    }

    @Test fun invalidOneTimeTaskRollsBackCustomerSiteAndVisit() = runTest {
        val beforeCustomers = db.serviceLoopDao().customerCount()
        val beforeSites = db.serviceLoopDao().siteCount()
        val beforeVisits = db.serviceLoopDao().visitCount()
        val beforeSnapshots = db.serviceLoopDao().templateSnapshotCount()
        val beforeChecklistItems = db.serviceLoopDao().checklistSnapshotItemCount()
        val failure = runCatching {
            repo.createOneTimeVisit(
                OneTimeVisitInput("Rollback customer"),
                listOf(AdHocWorkInput("Valid", WorkSubjectType.SITE), AdHocWorkInput("Invalid", WorkSubjectType.SITE, equipmentDescription = "not allowed")),
                "BOOKED",
                "2026-09-06",
            )
        }.exceptionOrNull()
        assertEquals("SITE work cannot have an Equipment description", failure?.message)
        assertEquals(beforeCustomers, db.serviceLoopDao().customerCount())
        assertEquals(beforeSites, db.serviceLoopDao().siteCount())
        assertEquals(beforeVisits, db.serviceLoopDao().visitCount())
        assertEquals(beforeSnapshots, db.serviceLoopDao().templateSnapshotCount())
        assertEquals(beforeChecklistItems, db.serviceLoopDao().checklistSnapshotItemCount())
    }

    @Test fun repeatedOneTimeVisitsRemainIndependent() = runTest {
        val first = repo.createOneTimeVisit(OneTimeVisitInput("Same customer"), listOf(AdHocWorkInput("First", WorkSubjectType.SITE)), "BOOKED", "2026-09-06")
        val second = repo.createOneTimeVisit(OneTimeVisitInput("Same customer"), listOf(AdHocWorkInput("Second", WorkSubjectType.SITE)), "BOOKED", "2026-09-07")
        assertTrue(first != second)
        val firstVisit = db.serviceLoopDao().visit(first)!!
        val secondVisit = db.serviceLoopDao().visit(second)!!
        assertTrue(firstVisit.customerId != secondVisit.customerId)
        assertEquals("First", db.serviceLoopDao().visitWorkItems(first).single().serviceNameSnapshot)
        assertEquals("Second", db.serviceLoopDao().visitWorkItems(second).single().serviceNameSnapshot)
    }

    @Test fun adHocTemplateIsSnapshottedAtCreationAndNotReplacedOnStart() = runTest {
        val ids = seedBranch()
        val templateId = repo.createTemplate("Initial checklist", listOf(TemplateItemDraft("Pressure", "NUMBER", "bar", required = true)))
        val visitId = repo.createVisitForSite(ids.site, emptyList(), listOf(AdHocWorkInput("Inspect", WorkSubjectType.EQUIPMENT, ids.equipment, reusableTemplateId = templateId)), "BOOKED", "2026-09-06")
        val before = db.serviceLoopDao().visitWorkItems(visitId).single()
        val beforeSnapshot = db.serviceLoopDao().templateSnapshot(before.templateSnapshotId!!)
        repo.reviseTemplate(templateId, "Changed checklist", listOf(TemplateItemDraft("Temperature", "NUMBER", "C")))
        repo.startVisit(visitId)
        val after = db.serviceLoopDao().visitWorkItems(visitId).single()
        assertEquals(before.templateSnapshotId, after.templateSnapshotId)
        assertEquals("Initial checklist", beforeSnapshot?.templateName)
        assertEquals(beforeSnapshot, db.serviceLoopDao().templateSnapshot(after.templateSnapshotId!!))
    }

    @Test fun adHocCreationRejectsCrossSiteEquipmentBeforeVisitExists() = runTest {
        val source = seedBranch()
        val destination = seedOneTimeBranch()
        val beforeVisits = db.serviceLoopDao().visitCount()
        val failure = runCatching {
            repo.createVisitForSite(source.site, emptyList(), listOf(AdHocWorkInput("Wrong machine", WorkSubjectType.EQUIPMENT, destination.equipment)), "BOOKED", "2026-09-06")
        }.exceptionOrNull()
        assertEquals("Equipment must belong to this visit site", failure?.message)
        assertEquals(beforeVisits, db.serviceLoopDao().visitCount())
    }

    @Test fun oneTimeSiteCanReceiveLaterAdHocVisitWithoutDuplicatingItsBranch() = runTest {
        val ids = seedOneTimeBranch()
        val beforeCustomers = db.serviceLoopDao().customerCount()
        val beforeSites = db.serviceLoopDao().siteCount()
        val visitId = repo.createVisitForSite(ids.site, emptyList(), listOf(AdHocWorkInput("Return visit", WorkSubjectType.SITE)), "BOOKED", "2026-09-07")
        assertEquals(beforeCustomers, db.serviceLoopDao().customerCount())
        assertEquals(beforeSites, db.serviceLoopDao().siteCount())
        assertEquals(CustomerType.ONE_TIME.code, db.serviceLoopDao().customer(ids.customer)!!.customerType)
        assertEquals(ids.site, db.serviceLoopDao().visit(visitId)!!.siteId)
    }

    @Test fun mixedStandardVisitKeepsPlanClaimSeparateFromAdHocWorkAndPreservesOrder() = runTest {
        val ids = seedBranch()
        val planId = repo.createPlan(ids.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01"))
        val visitId = repo.createVisitForSite(
            ids.site,
            listOf(planId),
            listOf(
                AdHocWorkInput("Inspect area", WorkSubjectType.SITE),
                AdHocWorkInput("Check motor", WorkSubjectType.EQUIPMENT, ids.equipment),
                AdHocWorkInput("Identify loose unit", WorkSubjectType.EQUIPMENT, equipmentDescription = "Loose unit by the door"),
            ),
            "WORKING",
            "2026-09-05",
        )
        val work = db.serviceLoopDao().visitWorkItems(visitId)
        assertEquals(4, work.size)
        assertEquals(listOf("Annual", "Inspect area", "Check motor", "Identify loose unit"), work.map { it.serviceNameSnapshot })
        assertEquals(1, work.count { it.capturedObligationId != null })
        assertTrue(work.drop(1).all { it.servicePlanId == null && it.capturedObligationId == null })
        assertEquals(WorkSubjectType.SITE.code, work[1].subjectType)
        assertEquals(ids.equipment, work[2].equipmentId)
        assertEquals("Loose unit by the door", work[3].equipmentDescriptionSnapshot)
    }

    @Test fun recurringBookedTemplateStillRefreshesAtStart() = runTest {
        val ids = seedBranch()
        val templateId = repo.createTemplate("Recurring r1", listOf(TemplateItemDraft("Pressure", "NUMBER")))
        val planId = repo.createPlan(ids.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01", reusableTemplateId = templateId))
        val visitId = repo.createVisitForSite(ids.site, listOf(planId), emptyList(), "BOOKED", "2026-09-06")
        repo.reviseTemplate(templateId, "Recurring r2", listOf(TemplateItemDraft("Temperature", "NUMBER")))
        repo.startVisit(visitId)
        val snapshotId = db.serviceLoopDao().visitWorkItems(visitId).single().templateSnapshotId!!
        assertEquals(2, db.serviceLoopDao().templateSnapshot(snapshotId)!!.revision)
        assertEquals("Recurring r2", db.serviceLoopDao().templateSnapshot(snapshotId)!!.templateName)
    }

    @Test fun addAdHocWorkSupportsAllSubjectsForLocalBookedVisit() = runTest {
        val ids = seedBranch()
        val visitId = repo.createVisitForSite(ids.site, emptyList(), listOf(AdHocWorkInput("Initial", WorkSubjectType.SITE)), "BOOKED", "2026-09-06")
        repo.addAdHocWork(visitId, AdHocWorkInput("Known", WorkSubjectType.EQUIPMENT, ids.equipment))
        repo.addAdHocWork(visitId, AdHocWorkInput("Unknown", WorkSubjectType.EQUIPMENT, equipmentDescription = "Unregistered unit"))
        val work = db.serviceLoopDao().visitWorkItems(visitId)
        assertEquals(listOf("Initial", "Known", "Unknown"), work.map { it.serviceNameSnapshot })
        assertNull(work[0].equipmentId)
        assertEquals(ids.equipment, work[1].equipmentId)
        assertEquals("Unregistered unit", work[2].equipmentDescriptionSnapshot)
    }

    @Test fun promotionEnablesAPlanOnExistingEquipment() = runTest {
        val ids = seedOneTimeBranch()
        repo.makeCustomerStandard(ids.customer)
        val planId = repo.createPlan(ids.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01"))
        assertEquals(ids.equipment, db.serviceLoopDao().plan(planId)!!.equipmentId)
    }

    @Test fun linkRestrictionsRejectSiteAndAlreadyKnownTasks() = runTest {
        val ids = seedBranch()
        val siteVisit = repo.createVisitForSite(ids.site, emptyList(), listOf(AdHocWorkInput("Site task", WorkSubjectType.SITE)), "WORKING", "2026-09-05")
        val siteWork = db.serviceLoopDao().visitWorkItems(siteVisit).single().id
        assertEquals("This task is not awaiting equipment identification", runCatching { repo.linkWorkItemEquipment(siteWork, ids.equipment) }.exceptionOrNull()?.message)
        val knownVisit = repo.createVisitForSite(ids.site, emptyList(), listOf(AdHocWorkInput("Known task", WorkSubjectType.EQUIPMENT, ids.equipment)), "WORKING", "2026-09-05")
        val knownWork = db.serviceLoopDao().visitWorkItems(knownVisit).single().id
        assertEquals("This task is not awaiting equipment identification", runCatching { repo.linkWorkItemEquipment(knownWork, ids.equipment) }.exceptionOrNull()?.message)
    }

    @Test fun oneTimeCustomerPromotionIsInPlaceAndIdempotent() = runTest {
        val ids = seedOneTimeBranch()
        assertEquals(CustomerType.ONE_TIME.code, db.serviceLoopDao().customer(ids.customer)!!.customerType)
        repo.makeCustomerStandard(ids.customer)
        val promoted = db.serviceLoopDao().customer(ids.customer)!!
        assertEquals(CustomerType.STANDARD.code, promoted.customerType)
        repo.makeCustomerStandard(ids.customer)
        assertEquals(promoted.id, db.serviceLoopDao().customer(ids.customer)!!.id)
        assertEquals(ids.site, db.serviceLoopDao().site(ids.site)!!.id)
    }

    @Test fun equipmentLinkIsWorkingOnlyAndKeepsSiteBoundary() = runTest {
        val ids = seedBranch()
        val other = seedOneTimeBranch()
        val visitId = repo.createVisitForSite(ids.site, emptyList(), listOf(AdHocWorkInput("Identify unit", WorkSubjectType.EQUIPMENT, equipmentDescription = "Unregistered unit")), "BOOKED", "2026-09-06")
        val workId = db.serviceLoopDao().visitWorkItems(visitId).single().id
        assertEquals("Equipment can be linked only while the visit is Working", runCatching { repo.linkWorkItemEquipment(workId, ids.equipment) }.exceptionOrNull()?.message)
        repo.startVisit(visitId)
        val context = repo.equipmentLinkContext(workId)
        assertTrue(context.equipment.any { it.id == ids.equipment })
        assertEquals("Equipment must belong to this visit site", runCatching { repo.linkWorkItemEquipment(workId, other.equipment) }.exceptionOrNull()?.message)
        repo.linkWorkItemEquipment(workId, ids.equipment)
        assertEquals(ids.equipment, db.serviceLoopDao().workItem(workId)!!.equipmentId)
    }

    @Test fun linkingEquipmentPreservesEveryWorkingDraftAndHistoryRow() = runTest {
        val ids = seedBranch()
        val workId = seedRichWorkingUnidentifiedWork("preserve-existing", ids)
        val beforeItem = db.serviceLoopDao().workItem(workId)!!
        val beforeVisit = db.serviceLoopDao().visit(beforeItem.visitId)!!
        val beforePublic = db.serviceLoopDao().publicDraft(workId)!!
        val beforePrivate = db.serviceLoopDao().privateDraft(workId)!!
        val beforeBuffers = db.serviceLoopDao().workingInputBuffers(workId)
        val beforeResponses = db.serviceLoopDao().responses(workId)
        val beforeParts = db.serviceLoopDao().parts(workId)
        val beforeAttachments = db.serviceLoopDao().workItemAttachments(workId)
        val attachmentBytes = beforeAttachments.map { File(root, it.storedRelativePath).readBytes() }

        repo.linkWorkItemEquipment(workId, ids.equipment)

        val afterItem = db.serviceLoopDao().workItem(workId)!!
        assertEquals(beforeItem.id, afterItem.id)
        assertEquals(beforeVisit.id, db.serviceLoopDao().visit(afterItem.visitId)!!.id)
        assertEquals(beforeItem.visitId, afterItem.visitId)
        assertEquals(beforeItem.serviceNameSnapshot, afterItem.serviceNameSnapshot)
        assertEquals(beforeItem.templateSnapshotId, afterItem.templateSnapshotId)
        assertEquals(beforePublic, db.serviceLoopDao().publicDraft(workId))
        assertEquals(beforePrivate, db.serviceLoopDao().privateDraft(workId))
        assertEquals(beforeBuffers, db.serviceLoopDao().workingInputBuffers(workId))
        assertEquals(beforeResponses, db.serviceLoopDao().responses(workId))
        assertEquals(beforeParts, db.serviceLoopDao().parts(workId))
        assertEquals(beforeAttachments, db.serviceLoopDao().workItemAttachments(workId))
        assertEquals(attachmentBytes.single().toList(), afterItem.let { db.serviceLoopDao().workItemAttachments(workId).single().let { attachment -> File(root, attachment.storedRelativePath).readBytes().toList() } })
        assertEquals(ids.equipment, afterItem.equipmentId)
        val equipment = db.serviceLoopDao().equipment(ids.equipment)!!
        assertEquals(equipment.name, afterItem.equipmentNameSnapshot)
        assertEquals(equipment.reference, afterItem.equipmentReferenceSnapshot)
        assertEquals(equipment.technicianIdentifier, afterItem.equipmentIdentifierSnapshot)
        assertEquals(equipment.make, afterItem.equipmentMakeSnapshot)
        assertEquals(equipment.model, afterItem.equipmentModelSnapshot)
        assertEquals(equipment.serialNumber, afterItem.equipmentSerialSnapshot)
        assertNull(afterItem.equipmentDescriptionSnapshot)
        assertEquals(beforeItem.outcome, afterItem.outcome)
        assertEquals(beforeItem.notPerformedReason, afterItem.notPerformedReason)
        assertEquals(beforeItem.fulfillsCurrentObligation, afterItem.fulfillsCurrentObligation)
        assertEquals(beforeItem.confirmedNextDueDate, afterItem.confirmedNextDueDate)
        assertEquals(beforeItem.nextDueDateCalculated, afterItem.nextDueDateCalculated)
        assertEquals(beforeItem.nextDueOverrideReason, afterItem.nextDueOverrideReason)
    }

    @Test fun creatingAndLinkingEquipmentAddsItToTheVisitSite() = runTest {
        val ids = seedOneTimeBranch()
        val visitId = repo.createOneTimeVisit(OneTimeVisitInput("One-time"), listOf(AdHocWorkInput("Identify new unit", WorkSubjectType.EQUIPMENT, equipmentDescription = "New unit")), "WORKING", "2026-09-05")
        val workId = db.serviceLoopDao().visitWorkItems(visitId).single().id
        val equipmentId = repo.createAndLinkEquipment(workId, EquipmentInput("New unit", make = "Maker", model = "Model"))
        assertEquals(equipmentId, db.serviceLoopDao().workItem(workId)!!.equipmentId)
        assertEquals(db.serviceLoopDao().visit(visitId)!!.siteId, db.serviceLoopDao().equipment(equipmentId)!!.siteId)
        assertEquals(equipmentId, repo.equipment(equipmentId)!!.id)
    }

    @Test fun creatingAndLinkingEquipmentPreservesWorkingDraftIdentity() = runTest {
        val ids = seedBranch()
        val workId = seedRichWorkingUnidentifiedWork("preserve-created", ids)
        val beforeItem = db.serviceLoopDao().workItem(workId)!!
        val beforePublic = db.serviceLoopDao().publicDraft(workId)!!
        val beforePrivate = db.serviceLoopDao().privateDraft(workId)!!
        val beforeBuffers = db.serviceLoopDao().workingInputBuffers(workId)
        val beforeResponses = db.serviceLoopDao().responses(workId)
        val beforeParts = db.serviceLoopDao().parts(workId)
        val beforeAttachments = db.serviceLoopDao().workItemAttachments(workId)

        val equipmentId = repo.createAndLinkEquipment(workId, EquipmentInput("New linked unit", make = "Maker", model = "Model", serialNumber = "SN-2"))

        val afterItem = db.serviceLoopDao().workItem(workId)!!
        assertEquals(beforeItem.id, afterItem.id)
        assertEquals(beforeItem.visitId, afterItem.visitId)
        assertEquals(beforeItem.serviceNameSnapshot, afterItem.serviceNameSnapshot)
        assertEquals(beforeItem.templateSnapshotId, afterItem.templateSnapshotId)
        assertEquals(beforePublic, db.serviceLoopDao().publicDraft(workId))
        assertEquals(beforePrivate, db.serviceLoopDao().privateDraft(workId))
        assertEquals(beforeBuffers, db.serviceLoopDao().workingInputBuffers(workId))
        assertEquals(beforeResponses, db.serviceLoopDao().responses(workId))
        assertEquals(beforeParts, db.serviceLoopDao().parts(workId))
        assertEquals(beforeAttachments, db.serviceLoopDao().workItemAttachments(workId))
        assertEquals(equipmentId, afterItem.equipmentId)
        assertEquals("New linked unit", afterItem.equipmentNameSnapshot)
        assertEquals("SN-2", afterItem.equipmentSerialSnapshot)
        assertNull(afterItem.equipmentDescriptionSnapshot)
        assertEquals(0, db.serviceLoopDao().plansForEquipment(equipmentId).size)
        assertEquals(db.serviceLoopDao().visit(afterItem.visitId)!!.siteId, db.serviceLoopDao().equipment(equipmentId)!!.siteId)
    }

    @Test fun updatePlanDefensivelyRejectsOneTimeOwnerWithoutMutation() = runTest {
        val ids = seedBranch(); val plan = repo.createPlan(ids.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01")); val before = db.serviceLoopDao().plan(plan)!!
        db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer(ids.customer)!!.copy(customerType = CustomerType.ONE_TIME.code))
        val failure = runCatching { repo.updatePlan(plan, PlanInput("Changed", 2, "MONTHS", "2026-10-01", dueDateChangeReason = "test")) }.exceptionOrNull()
        assertEquals("Recurring service requires a Standard customer", failure?.message)
        assertEquals(before, db.serviceLoopDao().plan(plan))
    }

    @Test fun equipmentSummariesCarryTheirOwningCustomerTypeAcrossAllProjections() = runTest {
        val ids = seedOneTimeBranch()

        assertEquals(CustomerType.ONE_TIME, repo.equipmentList().single { it.id == ids.equipment }.customerType)
        assertEquals(CustomerType.ONE_TIME, repo.customer(ids.customer)!!.equipment.single().customerType)
        assertEquals(CustomerType.ONE_TIME, repo.site(ids.site)!!.equipment.single().customerType)
        val visitSite = repo.visitSites().single { it.id == ids.site }
        assertEquals(CustomerType.ONE_TIME, visitSite.customerType)
        assertEquals(CustomerType.ONE_TIME, visitSite.equipment.single().customerType)
    }

    @Test fun equipmentWithPlansCannotMoveToOneTimeCustomerWithoutAnyMutation() = runTest {
        val source = seedBranch()
        val planId = repo.createPlan(source.equipment, PlanInput("Annual", 1, "YEARS", "2026-09-01"))
        val beforePlan = db.serviceLoopDao().plan(planId)!!
        val beforeObligation = db.serviceLoopDao().obligation(beforePlan.currentObligationId!!)!!
        val destination = seedOneTimeBranch()

        assertEquals(CustomerType.ONE_TIME, repo.moveReview(source.equipment).destinations.single { it.id == destination.site }.customerType)
        val failure = runCatching { repo.moveEquipment(source.equipment, destination.site, "2026-09-05", "Not allowed", acknowledged = true) }.exceptionOrNull()

        assertEquals("Recurring service requires a Standard customer", failure?.message)
        assertEquals(source.site, db.serviceLoopDao().equipment(source.equipment)!!.siteId)
        assertEquals(beforePlan, db.serviceLoopDao().plan(planId))
        assertEquals(beforeObligation, db.serviceLoopDao().obligation(beforeObligation.id))
        assertEquals(0, db.serviceLoopDao().equipmentMoveCount(source.equipment))
        assertTrue(db.serviceLoopDao().allChangeEntries().none { it.subjectId == source.equipment && it.changeType in setOf("MOVE", "MOVE_IN") })
    }

    @Test fun equipmentWithoutPlansMayMoveToOneTimeCustomer() = runTest {
        val source = seedBranch()
        val destination = seedOneTimeBranch()

        assertTrue(repo.moveEquipment(source.equipment, destination.site, "2026-09-05", "Ad-hoc move", acknowledged = true))

        assertEquals(destination.site, db.serviceLoopDao().equipment(source.equipment)!!.siteId)
        assertEquals(1, db.serviceLoopDao().equipmentMoveCount(source.equipment))
        assertEquals(2, db.serviceLoopDao().allChangeEntries().count { it.subjectId == source.equipment && it.changeType in setOf("MOVE", "MOVE_IN") })
    }

    @Test fun siteAndUnidentifiedEquipmentFinalizeWithoutRecurrenceOrFakeEquipment() = runTest {
        val siteIds = seedBranch(); val siteVisit = seedVisit("site-visit", siteIds); val siteWork = seedWork(siteVisit, siteIds, WorkSubjectType.SITE)
        val siteRecord = finalize(siteVisit, siteWork); val siteFinal = db.serviceLoopDao().finalWorkItems(db.serviceLoopDao().finalRecord(siteRecord)!!.currentRevisionId).single()
        assertEquals(WorkSubjectType.SITE.code, siteFinal.subjectType); assertNull(siteFinal.equipmentId); assertNull(siteFinal.equipmentName); assertNull(siteFinal.equipmentReference); assertFalse(siteFinal.fulfilledObligation)
        val unknownVisit = seedVisit("unknown-visit", siteIds); val unknownWork = seedWork(unknownVisit, siteIds, WorkSubjectType.EQUIPMENT, description = "Copy machine beside back-office desk")
        val unknownRecord = finalize(unknownVisit, unknownWork); val unknownFinal = db.serviceLoopDao().finalWorkItems(db.serviceLoopDao().finalRecord(unknownRecord)!!.currentRevisionId).single()
        assertEquals(WorkSubjectType.EQUIPMENT.code, unknownFinal.subjectType); assertNull(unknownFinal.equipmentId); assertEquals("Copy machine beside back-office desk", unknownFinal.equipmentDescription); assertFalse(unknownFinal.fulfilledObligation)
        assertTrue(db.serviceLoopDao().allPlans().isEmpty())
    }

    @Test fun malformedSiteAndUnidentifiedRecurringRowsAreRejectedAtFinalization() = runTest {
        val ids = seedBranch(); val visit = seedVisit("invalid-visit", ids)
        val invalid = WorkItemEntity("invalid", visit, ids.equipment, null, null, null, "Equipment", "EQ-1", "Invalid site", null, null, null, null, false, "PERFORMED", false, subjectType = WorkSubjectType.SITE.code)
        db.serviceLoopDao().insertWorkItems(listOf(invalid)); db.serviceLoopDao().insertPublicDrafts(listOf(WorkItemPublicDraftEntity("invalid", "Done"))); db.serviceLoopDao().insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity("invalid", "")))
        assertTrue(runCatching { repo.finalizeVisit(visit) }.isFailure)
        val unknown = WorkItemEntity("unknown-invalid", visit, null, "plan", null, null, null, null, "Invalid recurrence", "P-1", "2026-09-01", 1, "YEARS", false, "PERFORMED", false, subjectType = WorkSubjectType.EQUIPMENT.code)
        assertTrue(runCatching { WorkSubjectValidator.validateWorkItem(unknown, CustomerType.STANDARD, ids.equipment) }.isFailure)
    }

    @Test fun correctionAndCorrectiveFollowUpPreserveNonEquipmentSubjects() = runTest {
        val ids = seedBranch(); val visit = seedVisit("correction-visit", ids); val work = seedWork(visit, ids, WorkSubjectType.SITE); val record = finalize(visit, work)
        val follow = repo.createCorrectiveFollowUp(work, "Return to inspect", "2026-09-10", "")
        assertNull(db.serviceLoopDao().followUp(follow)!!.equipmentId); assertEquals(ids.customer, db.serviceLoopDao().followUp(follow)!!.customerId); assertEquals(ids.site, db.serviceLoopDao().followUp(follow)!!.siteId)
        val correction = repo.openCorrection(record); repo.saveCorrection(correction.copy(reason = "Correct the note", items = correction.items.map { it.copy(publicWorkNote = "Corrected note") })); val revision = repo.commitCorrection(record)
        val corrected = db.serviceLoopDao().finalWorkItems(revision).single(); assertEquals(WorkSubjectType.SITE.code, corrected.subjectType); assertNull(corrected.equipmentId); assertNull(corrected.equipmentName); assertNull(corrected.equipmentReference)
    }

    @Test fun publicProjectionAndHistoryKeepAllThreeSubjectFormsTruthful() = runTest {
        val ids = seedBranch(); val visit = seedVisit("mixed-visit", ids)
        val site = seedWork(visit, ids, WorkSubjectType.SITE, id = "site-line"); val unknown = seedWork(visit, ids, WorkSubjectType.EQUIPMENT, id = "unknown-line", description = "Exterior shutter"); val known = seedWork(visit, ids, WorkSubjectType.EQUIPMENT, id = "known-line", equipmentId = ids.equipment)
        listOf(site, unknown, known).forEach { repo.savePublicWork(it, "Work $it"); repo.saveCompletionDraft(it, "PERFORMED", false, null, null, null, null) }
        val record = (repo.finalizeVisit(visit) as FinalizeResult.Success).recordId; val detail = repo.finalRecord(record)!!; val lines = detail.public.lines
        assertEquals(setOf(WorkSubjectType.SITE, WorkSubjectType.EQUIPMENT), lines.map { it.subjectType }.toSet()); assertTrue(lines.any { it.equipmentDescription == "Exterior shutter" && it.equipmentReference == null }); assertTrue(lines.any { it.equipmentReference == "EQ-1" && it.equipmentName == "Equipment" }); assertTrue(lines.any { it.subjectType == WorkSubjectType.SITE && it.equipmentName == null && it.equipmentReference == null && it.equipmentIdentification == null })
        val history = repo.history(HistoryQuery(scope = HistoryScope(HistoryScopeType.SITE, ids.site), type = HistoryType.SERVICE_RECORDS)); assertEquals(3, history.count { it.routeType == "RECORD" }); assertEquals(1, history.count { it.equipmentId == ids.equipment }); assertEquals(2, history.count { it.equipmentId == null })
    }

    @Test fun directoryAndRecordsExportsCarryB026Truth() = runTest {
        val ids = seedBranch(); db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer(ids.customer)!!.copy(customerType = CustomerType.ONE_TIME.code)); val directory = repo.directoryCsv(true, false).toString(Charsets.UTF_8); assertTrue(directory.lineSequence().first().contains("customer_type")); assertTrue(directory.contains("\"ONE_TIME\"")); assertEquals(0, repo.validateDirectoryCsv(directory.toByteArray()).errors)
        val visit = seedVisit("csv-visit", ids)
        val siteWork = seedWork(visit, ids, WorkSubjectType.SITE, id = "csv-site")
        val unknownWork = seedWork(visit, ids, WorkSubjectType.EQUIPMENT, id = "csv-unknown", description = "Unregistered unit")
        val knownWork = seedWork(visit, ids, WorkSubjectType.EQUIPMENT, id = "csv-known", equipmentId = ids.equipment)
        listOf(siteWork, unknownWork, knownWork).forEach { work -> repo.savePublicWork(work, "Completed $work"); repo.saveCompletionDraft(work, "PERFORMED", false, null, null, null, null) }
        db.serviceLoopDao().upsertBusinessProfile(BusinessProfileEntity(businessName = "Business", technicianName = "Technician", phone = null, email = null, postalAddress = null, zoneId = "Europe/Bucharest", modifiedAtEpochMillis = 1))
        val record = (repo.finalizeVisit(visit) as FinalizeResult.Success).recordId
        val bytes = repo.recordsCsvPackage(true, false, true, ids.customer)
        val workRows = csvRows(bytes, "work_items.csv")
        val finalRows = csvRows(bytes, "final_work_items.csv")
        assertCsvSubject(workRows, "SITE", equipmentId = "", equipmentDescription = "")
        assertCsvSubject(workRows, "EQUIPMENT", equipmentId = "", equipmentDescription = "Unregistered unit")
        assertCsvSubject(workRows, "EQUIPMENT", equipmentId = ids.equipment, equipmentDescription = "")
        assertCsvSubject(finalRows, "SITE", equipmentId = "", equipmentDescription = "")
        assertCsvSubject(finalRows, "EQUIPMENT", equipmentId = "", equipmentDescription = "Unregistered unit")
        assertCsvSubject(finalRows, "EQUIPMENT", equipmentId = ids.equipment, equipmentDescription = "")
        assertTrue(finalRows.single { it["equipment_id"] == ids.equipment }["equipment_reference"] == "EQ-1")
        assertNotNull(repo.finalRecord(record))
    }

    @Test fun currentRecoveryRoundTripPreservesOneTimeAndFlexibleFinalSubjects() = runTest {
        val ids = seedBranch(); db.serviceLoopDao().updateCustomer(db.serviceLoopDao().customer(ids.customer)!!.copy(customerType = CustomerType.ONE_TIME.code))
        val siteVisit = seedVisit("recovery-site", ids); val siteWork = seedWork(siteVisit, ids, WorkSubjectType.SITE); finalize(siteVisit, siteWork)
        val unknownVisit = seedVisit("recovery-unknown", ids); val unknownWork = seedWork(unknownVisit, ids, WorkSubjectType.EQUIPMENT, description = "Unregistered unit"); finalize(unknownVisit, unknownWork)
        val recovery = RecoveryPackage(db, root); val passphrase = "b026 recovery passphrase".toCharArray(); val backup = recovery.create(passphrase, false); val inspection = recovery.inspect(backup.bytes, passphrase)
        recovery.eraseDatabaseAndOwnedFiles("replacement-dataset"); recovery.restore(inspection)
        assertEquals(CustomerType.ONE_TIME.code, db.serviceLoopDao().customer(ids.customer)!!.customerType)
        assertTrue(db.serviceLoopDao().finalWorkItems(db.serviceLoopDao().finalRecord( db.serviceLoopDao().allFinalRecords().first { it.visitId == siteVisit }.id)!!.currentRevisionId).single().subjectType == WorkSubjectType.SITE.code)
        assertEquals("Unregistered unit", db.serviceLoopDao().finalWorkItems(db.serviceLoopDao().finalRecord(db.serviceLoopDao().allFinalRecords().first { it.visitId == unknownVisit }.id)!!.currentRevisionId).single().equipmentDescription)
    }

    private data class Branch(val customer: String, val site: String, val equipment: String)

    private suspend fun seedBranch(): Branch {
        db.serviceLoopDao().insertCustomers(listOf(CustomerEntity("c", "CU-1", "Customer")))
        db.serviceLoopDao().insertSites(listOf(SiteEntity("s", "c", "ST-1", "Site", null, null, isDefault = true)))
        db.serviceLoopDao().insertEquipment(listOf(EquipmentEntity("e", "s", "EQ-1", "ID-1", "Equipment", "Maker", "Model", "Serial", null)))
        return Branch("c", "s", "e")
    }

    private suspend fun seedOneTimeBranch(): Branch {
        db.serviceLoopDao().insertCustomers(listOf(CustomerEntity("one-time-c", "CU-OT", "One-time Customer", customerType = CustomerType.ONE_TIME.code)))
        db.serviceLoopDao().insertSites(listOf(SiteEntity("one-time-s", "one-time-c", "ST-OT", "One-time Site", null, null, isDefault = true)))
        db.serviceLoopDao().insertEquipment(listOf(EquipmentEntity("one-time-e", "one-time-s", "EQ-OT", "ID-OT", "One-time Equipment", "Maker", "Model", "Serial", null)))
        return Branch("one-time-c", "one-time-s", "one-time-e")
    }

    private suspend fun seedVisit(id: String, branch: Branch): String {
        db.serviceLoopDao().insertVisits(listOf(WorkingVisitEntity(id, "V-$id", branch.customer, branch.site, "2026-09-05", "Customer", "Site", null, "WORKING", 1, "CU-1", "ST-1", "Business", "Technician", null, null, null, "Europe/Bucharest")))
        return id
    }

    private suspend fun seedRichWorkingUnidentifiedWork(idSuffix: String, branch: Branch): String {
        val visitId = seedVisit("visit-$idSuffix", branch)
        val workId = "work-$idSuffix"
        db.serviceLoopDao().insertTemplateSnapshots(listOf(TemplateSnapshotEntity("snapshot-$idSuffix", null, "Immutable checklist", 1, time.instant().toEpochMilli())))
        db.serviceLoopDao().insertChecklistItems(listOf(ChecklistItemSnapshotEntity("check-$idSuffix", "snapshot-$idSuffix", 0, "Pressure", "NUMBER", "bar", true, "Private guidance")))
        db.serviceLoopDao().insertWorkItems(listOf(WorkItemEntity(workId, visitId, null, null, null, "snapshot-$idSuffix", null, null, "Identify pump", null, null, null, null, true, "NOT_PERFORMED", false, notPerformedReason = "Awaiting access", subjectType = WorkSubjectType.EQUIPMENT.code, equipmentDescriptionSnapshot = "Blue pump beside heater")))
        db.serviceLoopDao().insertPublicDrafts(listOf(WorkItemPublicDraftEntity(workId, "")))
        db.serviceLoopDao().insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(workId, "")))
        repo.savePublicWork(workId, "Public work draft")
        repo.savePrivateNote(workId, "Private draft")
        repo.saveWorkingInputBuffer(workId, ServiceDraftFieldKeys.WORK, "raw public draft")
        repo.saveWorkingInputBuffer(workId, ServiceDraftFieldKeys.PRIVATE, "raw private draft")
        repo.saveResponse(workId, "check-$idSuffix", ResponseDisposition.VALUE, "12.5", null)
        repo.addPart(workId, "Seal", "2", "pcs")
        val attachmentId = "attachment-$idSuffix"
        val relativePath = "attachments/$attachmentId/original.bin"
        File(root, relativePath).apply { parentFile?.mkdirs(); writeBytes(byteArrayOf(1, 2, 3, 5, 8)) }
        db.serviceLoopDao().insertAttachments(listOf(AttachmentEntity(attachmentId, "WORK_ITEM", workId, relativePath, "hash-$idSuffix", "evidence.bin", "application/octet-stream", false, "PRESENT", 5, "Attachment metadata")))
        return workId
    }

    private suspend fun seedWork(visit: String, branch: Branch, subject: WorkSubjectType, id: String = "work-$visit", equipmentId: String? = null, description: String? = null): String {
        val known = equipmentId != null
        val item = WorkItemEntity(id, visit, equipmentId, null, null, null, if (known) "Equipment" else null, if (known) "EQ-1" else null, "Service", null, null, null, null, false, null, false, subjectType = subject.code, equipmentDescriptionSnapshot = description)
        db.serviceLoopDao().insertWorkItems(listOf(item)); db.serviceLoopDao().insertPublicDrafts(listOf(WorkItemPublicDraftEntity(id, ""))); db.serviceLoopDao().insertPrivateDrafts(listOf(WorkItemPrivateDraftEntity(id, "")))
        return id
    }

    private suspend fun finalize(visit: String, work: String): String {
        repo.savePublicWork(work, "Completed")
        repo.saveCompletionDraft(work, "PERFORMED", false, null, null, null, null)
        db.serviceLoopDao().upsertBusinessProfile(BusinessProfileEntity(businessName = "Business", technicianName = "Technician", phone = null, email = null, postalAddress = null, zoneId = "Europe/Bucharest", modifiedAtEpochMillis = 1))
        return when (val result = repo.finalizeVisit(visit)) { is FinalizeResult.Success -> result.recordId; is FinalizeResult.Blocked -> error("Finalize blocked: ${result.message}") }
    }

    private fun assertCsvSubject(rows: List<Map<String, String>>, subjectType: String, equipmentId: String, equipmentDescription: String) {
        assertTrue(rows.any { it["subject_type"] == subjectType && it["equipment_id"] == equipmentId && it["equipment_description"] == equipmentDescription })
    }

    private fun csvRows(bytes: ByteArray, entryName: String): List<Map<String, String>> {
        val lines = zipEntry(bytes, entryName).lineSequence().filter(String::isNotBlank).map { line -> line.removePrefix("\"").removeSuffix("\"").split("\",\"") }.toList()
        val headers = lines.first()
        return lines.drop(1).map { values -> headers.zip(values).toMap() }
    }

    private fun zipEntry(bytes: ByteArray, entryName: String): String = ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == entryName) return zip.readBytes().toString(Charsets.UTF_8)
        }
        error("ZIP entry missing: $entryName")
    }
}
