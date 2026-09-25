package com.v16studio.v16service

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.v16studio.v16service.data.*
import com.v16studio.v16service.domain.BusinessTime
import com.v16studio.v16service.domain.CustomerContactInput
import com.v16studio.v16service.domain.AdHocWorkInput
import com.v16studio.v16service.domain.WorkSubjectType
import java.io.File
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONArray
import org.json.JSONObject
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NativeEnvelopeContractTest {
    private lateinit var database: V16ServiceDatabase
    private lateinit var attachmentRoot: File
    private val exporterId = TechnicianIdCodec.generate()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val time = object : BusinessTime {
        override val zoneId = ZoneId.of("Europe/Bucharest")
        override fun instant() = Instant.parse("2026-09-22T10:00:00Z")
    }

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, V16ServiceDatabase::class.java).allowMainThreadQueries().build()
        V16ServiceDatabase.configureStage4Tracking(database.openHelper.writableDatabase); V16ServiceDatabase.configureReminderDefaults(database.openHelper.writableDatabase)
        kotlinx.coroutines.runBlocking { database.dispatchDao().insertTechnicianIdentity(TechnicianIdentityEntity("primary", exporterId, "Device technician", 1L, 1L)) }
        attachmentRoot = File(context.cacheDir, "b045-${System.nanoTime()}").apply { mkdirs() }
    }

    @After fun tearDown() { database.close(); attachmentRoot.deleteRecursively() }

    @Test fun codecRoundTripsTinyValidFullWorkspace() {
        val original = packageValue()
        val decoded = V16ServiceSyncCodec.decode(V16ServiceSyncCodec.encode(original))
        assertEquals(original, decoded)
        assertEquals(1, V16ServiceSyncCodec.preview(decoded).counts.customers)
        assertEquals(exporterId, decoded.manifest.exporterId)
        val envelope = V16ServiceSyncEnvelopeCodec.decode(V16ServiceSyncCodec.encode(original))
        assertEquals(1, envelope.manifest.formatVersion)
        assertEquals(1, envelope.manifest.sections.single { it.name == "register" }.version)
        assertEquals(1, envelope.manifest.sections.single { it.name == "team" }.version)
        assertEquals("office contact", decoded.register.customerContacts.single().personName)
        assertEquals("Technician note", decoded.team.technicians.single().notes)
    }

    @Test fun customerContactsKeepNotesAndTransactionalOrder() = runTest {
        database.v16ServiceDao().insertCustomers(listOf(CustomerEntity("order-customer", "CU-ORDER", "Order customer")))
        val repository = RoomV16ServiceRepository(database, time)
        val first = repository.createCustomerContact(CustomerContactInput("order-customer", "Office", "PHONE", "123", "Internal first note"))
        val second = repository.createCustomerContact(CustomerContactInput("order-customer", "Manager", "EMAIL", "manager@example.com", "Internal second note"))
        repository.moveCustomerContact("order-customer", second, -1)
        assertEquals(listOf(second, first), database.v16ServiceDao().customerContacts("order-customer").map { it.id })
        repository.updateCustomerContact(first, CustomerContactInput("order-customer", "Office", "SMS", "456", "Edited note"))
        assertEquals(2, database.v16ServiceDao().customerContacts("order-customer").last().position)
        assertEquals("Edited note", database.v16ServiceDao().customerContacts("order-customer").last().notes)
        repository.deleteCustomerContact("order-customer", second)
        val remaining = database.v16ServiceDao().customerContacts("order-customer").single()
        assertEquals(first, remaining.id)
        assertEquals(1, remaining.position)
        assertEquals("456", remaining.value)
    }

    @Test fun assignedVisitCreatesCanonicalWorkAndLinkedOutboxAtomically() = runTest {
        val dao = database.v16ServiceDao()
        val dispatch = database.dispatchDao()
        dao.insertCustomers(listOf(CustomerEntity("assigned-customer", "CU-ASG", "Assigned customer")))
        dao.insertSites(listOf(SiteEntity("assigned-site", "assigned-customer", "ST-ASG", "Assigned site", null, null)))
        dispatch.insertTechnician(DispatchTechnicianEntity("tech-assigned", "Assigned technician", 1, 1))
        dispatch.insertTeam(DispatchTeamEntity("team-assigned", "Assigned team", 1, 1))
        dispatch.insertTeamMember(DispatchTeamMemberEntity("team-assigned", "tech-assigned", true))
        val repository = RoomV16ServiceRepository(database, time)
        val input = AssignedVisitInput(
            siteId = "assigned-site", newCustomerSite = null, planIds = emptyList(),
            tasks = listOf(AdHocWorkInput("Inspect site", WorkSubjectType.SITE)),
            serviceDate = "2026-09-23", scheduledAtEpochMillis = null,
            teamIds = listOf("team-assigned"), workAssignees = listOf(listOf("tech-assigned")),
            managerReference = "JOB-1", instructions = "Check access",
        )
        val before = dao.visitCount()
        assertTrue(runCatching { repository.createAssignedVisit(input.copy(workAssignees = listOf(listOf("outside-team")))) }.isFailure)
        assertEquals(before, dao.visitCount())
        val visitId = repository.createAssignedVisit(input)
        assertEquals(before + 1, dao.visitCount())
        assertEquals("BOOKED", dao.visit(visitId)?.state)
        val work = dao.visitWorkItems(visitId).single()
        val outbox = dispatch.outboxVisits().single { it.localVisitId == visitId }
        val overlayItem = dispatch.outboxItems(outbox.dispatchVisitId).single()
        assertEquals(work.id, overlayItem.localWorkItemId)
        assertEquals("Inspect site", work.serviceNameSnapshot)
        assertEquals("JOB-1", outbox.managerReference)
        assertEquals(listOf("tech-assigned"), dispatch.outboxItemAssignees(overlayItem.dispatchItemId).map { it.technicianId })
    }

    @Test fun localIdentityAndTrustedIdsAreStableAndManageable() = runTest {
        val store = V16ServicePeerTrustStore(database)
        val original = store.localIdentity()
        assertEquals(exporterId, original.technicianId)
        assertEquals(V16ServiceSourceTrust.TRUSTED, store.assess(exporterId).trust)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { store.add(exporterId, "This device") } }
        val peerId = TechnicianIdCodec.generate()
        val added = store.add(peerId, "  Pandomur coordinator  ")
        assertEquals("Pandomur coordinator", added.name)
        assertEquals(V16ServiceSourceTrust.TRUSTED, store.assess(peerId).trust)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { store.add(peerId, "Duplicate") } }
        store.rename(peerId, "Regional coordinator")
        assertEquals("Regional coordinator", store.assess(peerId).friendlyName)
        assertEquals(original.technicianId, store.localIdentity().technicianId)
        store.remove(peerId)
        assertEquals(V16ServiceSourceTrust.NOT_TRUSTED, store.assess(peerId).trust)
    }

    @Test fun fullWorkspaceMutationRequiresTrustedExporterAndPreservesLocalIdentityAndTrust() = runTest {
        val dao = database.v16ServiceDao()
        dao.insertCustomers(listOf(CustomerEntity("old-customer", "OLD-001", "Old customer")))
        val outsiderId = TechnicianIdCodec.generate()
        val untrusted = packageValue().copy(manifest = packageValue().manifest.copy(exporterId = outsiderId))
        val repo = RoomV16ServiceRepository(database, time, attachmentRoot = attachmentRoot)
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.importV16ServiceSync(untrusted) } }
        assertNotNull(dao.customer("old-customer"))
        assertNull(dao.customer("customer-1"))
        val store = V16ServicePeerTrustStore(database)
        store.add(outsiderId, "Office")
        repo.importV16ServiceSync(untrusted)
        assertNull(dao.customer("old-customer"))
        assertNotNull(dao.customer("customer-1"))
        assertEquals("office@example.com", dao.customerContacts("customer-1").single().value)
        assertEquals(exporterId, store.localIdentity().technicianId)
        assertEquals("Office", store.trustedIds().single().name)
        store.remove(outsiderId)
        val currentCustomer = dao.customer("customer-1")!!
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.importV16ServiceSync(untrusted) } }
        assertEquals(currentCustomer, dao.customer("customer-1"))
    }

    @Test fun recoveryBackupRestoresTrustedIdsAndStableIdentity() = runTest {
        val store = V16ServicePeerTrustStore(database)
        val local = store.localIdentity()
        val peerId = TechnicianIdCodec.generate()
        store.add(peerId, "Backup coordinator")
        val repo = RoomV16ServiceRepository(database, time, attachmentRoot = attachmentRoot)
        val passphrase = "trusted peers backup".toCharArray()
        val inspection = repo.inspectBackup(repo.createBackup(passphrase).bytes, passphrase)
        store.remove(peerId)
        repo.restoreBackup(inspection, "REPLACE", false)
        assertEquals(peerId, store.trustedIds().single().peerId)
        assertEquals(local.technicianId, store.localIdentity().technicianId)
    }

    @Test fun recoveryCurrentShapeRoundTripPreservesB049HistoryAndOwnedDerivative() = runTest {
        val dao = database.v16ServiceDao()
        dao.insertCustomers(listOf(CustomerEntity("b049-customer", "CU-B049", "B049 customer")))
        dao.insertCustomerContacts(listOf(
            CustomerContactEntity("b049-contact", "b049-customer", "Ada", "EMAIL", "ada@example.test", 1, 2, "Internal instructions", 1),
            CustomerContactEntity("b049-contact-2", "b049-customer", "Bea", "PHONE", "+40123456789", 2, 2, null, 2),
        ))
        dao.insertSites(listOf(SiteEntity("b049-site", "b049-customer", "ST-B049", "Site", null, null)))
        dao.insertVisits(listOf(WorkingVisitEntity("b049-visit", "V-B049", "b049-customer", "b049-site", "2026-09-22", "B049 customer", "Site", null, "COMPLETED", 2)))
        dao.insertWorkItems(listOf(WorkItemEntity("b049-work", "b049-visit", null, null, null, null, null, null, "Inspect", null, null, null, null, false, null, null, subjectType = "SITE")))
        dao.insertFinalRecord(FinalRecordEntity("b049-final-record", "b049-visit", "b049-final-revision", 2))
        dao.insertFinalRevision(FinalRecordRevisionEntity("b049-final-revision", "b049-final-record", 1, "V-B049", "2026-09-22", 2, "B049 customer", "Site", null, "Business", "Technician", null, null, null, "UTC", null))
        dao.insertFinalWorkItems(listOf(FinalWorkItemEntity("b049-final-work", "b049-final-revision", 1, "b049-work", null, null, null, null, null, null, null, "Inspect", null, null, "DONE", null, null, false, null, null, null, null, null, null, subjectType = "SITE", followUpsSnapshotJson = FinalFollowUpSnapshot.capture(0, emptyList()))))
        val photoBytes = "owned B049 private evidence".toByteArray()
        val photoPath = "attachments/b049-private.jpg"
        File(attachmentRoot, photoPath).apply { parentFile!!.mkdirs(); writeBytes(photoBytes) }
        val photoHash = java.security.MessageDigest.getInstance("SHA-256").digest(photoBytes).joinToString("") { "%02x".format(it) }
        dao.insertAttachments(listOf(AttachmentEntity("b049-photo", "WORK_ITEM", "b049-work", photoPath, photoHash, null, "image/jpeg", false, "PRESENT", photoBytes.size.toLong(), "Private proof", "PRIVATE")))
        dao.insertFinalPhotos(listOf(FinalPhotoEntryEntity("b049-final-photo", "b049-final-work", 1, "b049-photo", photoPath, photoHash, photoBytes.size.toLong(), "image/jpeg", "Private proof", includedInCustomerReport = false, visibility = "PRIVATE")))
        val result = RemoteFinalResultEntity(
            id = "b049-remote", resultId = "b049-result", sourceFinalRevisionId = "b049-revision", dispatchVisitId = "b049-dispatch-visit", dispatchItemId = "b049-dispatch-item",
            localVisitId = "b049-visit", localWorkItemId = "b049-work", technicianId = exporterId, technicianName = "Ada", technicianDesignation = null,
            customerId = "b049-customer", customerSnapshotJson = "{}", siteSnapshotJson = "{}", subjectSnapshotJson = "{}",
            serviceDate = "2026-09-22", outcome = "PERFORMED", workPerformed = "Inspected", notPerformedReason = null,
            checklistJson = "[]", findingsJson = "[]", partsJson = "[]", internalNotes = "Private history", followUpsJson = "[]", recurrenceJson = "{}", provenanceJson = "{}", importedAtEpochMillis = 3, sourcePayloadJson = FinalSourceSnapshot.encode("WORK_RESULT", 1, 1, JSONObject().put("resultId", "b049-result").put("sourceFinalRevisionId", "b049-revision").put("technicianId", exporterId).put("dispatchVisitId", "b049-dispatch-visit").put("dispatchItemId", "b049-dispatch-item").put("serviceDate", "2026-09-22").put("outcome", "PERFORMED")),
        )
        dao.insertRemoteFinalResult(result)
        val receipt = WorkResultReceiptEntity("b049-receipt", "b049-package", "b049-result", "b049-revision", exporterId, exporterId, "b049-dispatch-visit", "b049-dispatch-item", 1, "material", 3, "payload", "APPLIED", null, 3, null)
        dao.insertWorkResultReceipt(receipt)
        val report = AggregateReportEntity("b049-aggregate", "b049-customer", "{}", null, null, null, null, "{}", 4, "READY")
        dao.insertAggregateReport(report)
        val source = AggregateReportSourceEntity("b049-aggregate", 1, "b049-revision", "REMOTE", "b049-visit", "b049-remote")
        dao.insertAggregateSources(listOf(source))
        dao.insertAggregateRendition(AggregateReportRenditionEntity("b049-rendition", "b049-aggregate", null, null, null, null, 4, "PENDING", null))
        val derivativeBytes = "retained B049 evidence".toByteArray()
        val derivativePath = "retained-images/b049.jpg"
        File(attachmentRoot, derivativePath).apply { parentFile!!.mkdirs(); writeBytes(derivativeBytes) }
        val derivativeHash = java.security.MessageDigest.getInstance("SHA-256").digest(derivativeBytes).joinToString("") { "%02x".format(it) }
        val retained = RetainedImageEntity("b049-retained", "FINAL_PHOTO", "b049-source-photo", null, 5, derivativePath, derivativeHash, derivativeBytes.size.toLong(), 12, 8, "image/jpeg", 4)
        dao.insertRetainedImage(retained)

        val recovery = RecoveryPackage(database, attachmentRoot)
        val passphrase = "B049 recovery round trip".toCharArray()
        val inspection = recovery.inspect(recovery.create(passphrase, false).bytes, passphrase)
        assertTrue(inspection.complete)
        dao.renameCustomer("b049-customer", "Changed after backup")
        File(attachmentRoot, derivativePath).delete()
        recovery.restore(inspection)

        assertEquals("B049 customer", dao.customer("b049-customer")?.name)
        assertEquals(listOf("b049-contact", "b049-contact-2"), dao.customerContacts("b049-customer").map { it.id })
        assertEquals("Internal instructions", dao.customerContacts("b049-customer").first().notes)
        assertEquals(listOf(1, 2), dao.customerContacts("b049-customer").map { it.position })
        assertEquals(result, dao.remoteFinalResult(result.technicianId, "b049-result", "b049-revision"))
        assertEquals(receipt, dao.workResultReceipt(receipt.exporterId, "b049-result", "b049-revision"))
        assertEquals(report, dao.aggregateReport("b049-aggregate"))
        assertEquals(listOf(source), dao.aggregateSources("b049-aggregate"))
        assertEquals(retained, dao.retainedImage("FINAL_PHOTO", "b049-source-photo"))
        assertEquals(derivativeBytes.toList(), File(attachmentRoot, derivativePath).readBytes().toList())
        assertEquals("PRIVATE", dao.attachment("b049-photo")?.visibility)
        assertEquals(false, dao.finalPhotos("b049-final-work").single().includedInCustomerReport)
        assertEquals("PRIVATE", dao.finalPhotos("b049-final-work").single().visibility)
    }

    @Test fun initialImportReplacesBusinessRowsAndPreservesDeviceState() = runTest {
        val dao = database.v16ServiceDao()
        val identity = database.dispatchDao().technicianIdentity()!!
        dao.insertCustomers(listOf(CustomerEntity("old-customer", "OLD-001", "Old customer")))
        val preferences = ReminderPreferencesEntity(dueSoonHorizonDays = 42)
        dao.upsertReminderPreferences(preferences)
        val oldMetadata = RecoveryMetadataEntity(datasetId = "old-dataset", firstBusinessWriteAtEpochMillis = 1L, lastBusinessWriteAtEpochMillis = 2L, lastBackupAttemptAtEpochMillis = 3L, lastVerifiedFullBackupAtEpochMillis = 4L, lastVerifiedSnapshotAtEpochMillis = 5L, lastVerifiedDestination = "old", lastVerifiedSize = 6L, backupReminderDays = 19, restoredFromIncompleteCopy = true, restrictedRecoveryState = false, adoptionToken = "old-token")
        dao.upsertRecoveryMetadata(oldMetadata)
        val repo = RoomV16ServiceRepository(database, time, attachmentRoot = attachmentRoot)

        val result = repo.importV16ServiceSync(packageValue())

        assertEquals(1, dao.customerCount())
        assertNull(dao.customer("old-customer"))
        assertNotNull(dao.customer("customer-1"))
        assertNotNull(dao.reusableTemplate("template-1"))
        assertNotNull(dao.plan("plan-1"))
        assertNotNull(dao.obligation("plan-1-obligation-1"))
        assertNotNull(dao.visit("visit-1"))
        assertNotNull(dao.workItem("work-1"))
        assertNotNull(dao.claimForObligation("plan-1-obligation-1"))
        assertNotNull(dao.followUp("followup-1"))
        assertNotNull(dao.contactNote("note-1"))
        assertEquals(1, database.dispatchDao().technicians().size)
        assertEquals("Technician note", database.dispatchDao().technician("import-tech")!!.notes)
        assertEquals(1, database.dispatchDao().outboxVisits().size)
        assertTrue(repo.dueServices().isNotEmpty())
        assertNull(dao.obligation("plan-1-obligation-1")?.consumedAtEpochMillis)
        assertEquals(identity, database.dispatchDao().technicianIdentity())
        assertEquals(preferences, dao.reminderPreferences())
        val newMetadata = dao.recoveryMetadata()!!
        assertNotEquals(oldMetadata.datasetId, newMetadata.datasetId)
        assertEquals(time.instant().toEpochMilli(), newMetadata.firstBusinessWriteAtEpochMillis)
        assertEquals(19, newMetadata.backupReminderDays)
        assertNull(newMetadata.lastVerifiedFullBackupAtEpochMillis)
        assertFalse(newMetadata.restoredFromIncompleteCopy)
        assertFalse(newMetadata.restrictedRecoveryState)
        assertNull(newMetadata.adoptionToken)
        assertEquals(1, result.counts.dispatchDrafts)
    }

    @Test fun invalidReferenceRollsBackAfterReplacementHasStarted() = runTest {
        val dao = database.v16ServiceDao()
        dao.insertCustomers(listOf(CustomerEntity("old-customer", "OLD-001", "Old customer")))
        dao.upsertRecoveryMetadata(RecoveryMetadataEntity(datasetId = "old-dataset", firstBusinessWriteAtEpochMillis = 1L, lastBusinessWriteAtEpochMillis = 2L, lastBackupAttemptAtEpochMillis = null, lastVerifiedFullBackupAtEpochMillis = null, lastVerifiedSnapshotAtEpochMillis = null, lastVerifiedDestination = null, lastVerifiedSize = null))
        val repo = RoomV16ServiceRepository(database, time, attachmentRoot = attachmentRoot)
        val invalidVisit = packageValue().visits.bookedVisits.single().copy(work = listOf(SyncBookedWork("work-bad", "AD_HOC", null, "Bad task", "EQUIPMENT", "equipment-1", null, "missing-template")))
        val invalid = packageValue().copy(visits = packageValue().visits.copy(bookedVisits = listOf(invalidVisit)))

        assertThrows(RuntimeException::class.java) { kotlinx.coroutines.runBlocking { repo.importV16ServiceSync(invalid) } }
        assertNotNull(dao.customer("old-customer"))
        assertNull(dao.customer("customer-1"))
        assertEquals("old-dataset", dao.recoveryMetadata()?.datasetId)
    }

    @Test fun universalEnvelopePreservesWorkAndSharesTemplatesThroughCurrentDataTransfer() {
        val work = dispatchPackage()
        val decodedWork = V16ServiceSyncEnvelopeCodec.unwrapWorkAssignment(
            V16ServiceSyncEnvelopeCodec.wrapWorkAssignment(work, exporterId),
        )
        assertEquals(work, decodedWork)

        val transfer = InspectionTemplateTransfer(
            "2026-09-22T10:00:00Z",
            listOf(InspectionTemplateTransferEntry(
                "IT-1", "Safety", 1,
                listOf(DispatchInspectionItem(1, "Guard", "STATUS", null, true, null)),
            )),
        )
        val transferBytes = V16ServiceSyncEnvelopeCodec.wrapTemplateTransfer(transfer, exporterId)
        val envelope = V16ServiceSyncEnvelopeCodec.decode(transferBytes)
        assertEquals(1, envelope.manifest.formatVersion)
        assertEquals("DATA_TRANSFER", envelope.manifest.purpose)
        val payload = DataTransferCodec.decode(transferBytes)
        assertEquals(1, payload.familyVersions.getValue(DataTransferFamily.INSPECTION_TEMPLATES))
        assertEquals(setOf(DataTransferFamily.INSPECTION_TEMPLATES), payload.families.keys)

        val decodedTemplates = V16ServiceSyncEnvelopeCodec.unwrapTemplateTransfer(transferBytes)
        val originalTemplate = transfer.templates.single()
        assertEquals(transfer.generatedAt, decodedTemplates.generatedAt)
        assertEquals(
            originalTemplate.copy(
                fingerprint = InspectionTemplateCodec.fingerprint(originalTemplate),
                originWorkspaceId = exporterId,
                sourceEntityId = originalTemplate.reference,
            ),
            decodedTemplates.templates.single(),
        )
    }

    @Test fun purposeRoutingRejectsUnknownAndFamilyFilteringKeepsReferencesValid() {
        val value = packageValue()
        val filtered = filterFullWorkspacePackage(value, setOf(SyncContentFamily.VISITS))
        assertTrue(filtered.register.customers.isNotEmpty())
        assertTrue(filtered.register.sites.isNotEmpty())
        assertTrue(filtered.plans.plans.isNotEmpty())
        assertTrue(filtered.followups.followUps.isEmpty())
        V16ServiceSyncCodec.decode(V16ServiceSyncCodec.encode(filtered))
        val invalidManifest = V16ServiceSyncManifest("x", "Unknown", "NOT_A_PURPOSE", "2026-09-22T10:00:00Z", sections = listOf(V16ServiceSyncSectionDeclaration("x", 1, "x.json")), exporterId = exporterId)
        assertThrows(IllegalArgumentException::class.java) { V16ServiceSyncEnvelopeCodec.encode(invalidManifest, mapOf("x" to byteArrayOf(1))) }
    }

    @Test fun removingParentFamilyClosesSelectedDependents() {
        val value = packageValue()
        val selected = SyncContentFamily.entries.toSet()
        val withoutCustomers = removeSyncContentFamily(value, selected, SyncContentFamily.CUSTOMERS)
        assertEquals(setOf(SyncContentFamily.BUSINESS_PROFILE, SyncContentFamily.INSPECTION_TEMPLATES, SyncContentFamily.TECHNICIANS, SyncContentFamily.TEAMS), withoutCustomers)
        val withoutTechnicians = removeSyncContentFamily(value, selected, SyncContentFamily.TECHNICIANS)
        assertFalse(SyncContentFamily.TEAMS in withoutTechnicians)
        assertFalse(SyncContentFamily.DISPATCH_DRAFTS in withoutTechnicians)
    }

    private fun dispatchPackage() = DispatchPackage(
        packageId = "package-1",
        createdAt = "2026-09-22T10:00:00Z",
        senderLabel = "Coordinator",
        customers = listOf(DispatchCustomer("CU-1", "Customer")),
        sites = listOf(DispatchSite("ST-1", "CU-1", "Site", "Address")),
        equipment = emptyList(),
        visits = listOf(
            DispatchVisit(
                dispatchVisitId = "DV-1", generation = 1, managerReference = "JOB-1", serviceDate = "2026-09-23",
                appointmentLocalTime = null, appointmentZoneId = "Europe/Bucharest", siteReference = "ST-1", instructions = null,
                teams = listOf(DispatchTeamSnapshot("TEAM-1", "Team", listOf("tech-1"), listOf("tech-1"))),
                participants = listOf(DispatchTechnicianSnapshot("tech-1", "Technician")), leaderTechnicianIds = listOf("tech-1"),
                work = listOf(DispatchWork("ITEM-1", WorkSubjectType.SITE, taskName = "Inspect site", assignedTechnicians = listOf(DispatchTechnicianSnapshot("tech-1", "Technician")))),
            ),
        ),
    )

    private fun packageValue() = V16ServiceSyncPackage(
        manifest = V16ServiceSyncManifest("sync-1", "B045 test workspace", "FULL_WORKSPACE", "2026-09-22T10:00:00Z", exporterId = exporterId),
        business = SyncBusinessProfile("Test business", "Test technician", "+40", "test@example.com", "Test street", "Europe/Bucharest"),
        register = SyncRegister(
            customers = listOf(SyncCustomer("customer-1", "CU-001", "Customer One", null, null, null, null, "STANDARD", "ACTIVE")),
            sites = listOf(SyncSite("site-1", "customer-1", "ST-001", "Main site", "Test address", null, null, null, null, true, "ACTIVE")),
            equipment = listOf(SyncEquipment("equipment-1", "site-1", "EQ-001", "unit-1", "Boiler", "Maker", "Model", "Serial", null, "ACTIVE")),
            customerContacts = listOf(SyncCustomerContact("contact-1", "customer-1", "office contact", "EMAIL", "office@example.com", position = 1)),
        ),
        inspections = SyncInspections(listOf(SyncTemplate("template-1", "IT-001", "Annual inspection", "ACTIVE", 1, listOf(SyncTemplateItem(1, "Condition", "STATUS", null, true, null))))),
        plans = SyncPlans(listOf(SyncPlan("plan-1", "P-001", "equipment-1", "Annual service", 1, "YEARS", "2026-09-22", "template-1", "ACTIVE"))),
        team = SyncTeamDirectory(listOf(SyncTechnician("import-tech", "Imported technician", "Lead", "Technician note")), listOf(SyncTeam("team-1", "Imported team", listOf("import-tech"), listOf("import-tech")))),
        visits = SyncVisits(
            bookedVisits = listOf(SyncBookedVisit("visit-1", "V-001", "site-1", "2026-09-23", "09:30", "Europe/Bucharest", listOf(SyncBookedWork("work-1", "PLAN", "plan-1", null, "EQUIPMENT", null, null, null)))),
            dispatchDrafts = listOf(SyncDispatchDraft("dispatch-1", "M-001", "site-1", "2026-09-25", "08:00", "Europe/Bucharest", "Bring tools", listOf("team-1"), listOf(SyncDispatchItem("dispatch-item-1", "PLAN", "plan-1", listOf("import-tech"))))),
        ),
        followups = SyncFollowUps(
            followUps = listOf(SyncFollowUp("followup-1", "FU-001", "CONTACT", "Call customer", "2026-09-22", "OPEN", "customer-1", "site-1", null, "Private note")),
            contactNotes = listOf(SyncContactNote("note-1", "CN-001", "customer-1", "site-1", null, "EMAIL", "2026-09-19T08:00:00Z", "Sent details", null)),
        ),
    )


}
