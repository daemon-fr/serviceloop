package com.v16studio.serviceloop.data

import com.v16studio.serviceloop.domain.WorkSubjectType
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.LinkedHashMap
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

const val SERVICE_LOOP_SYNC_MIME = "application/vnd.serviceloop.sync+zip"

data class ServiceLoopSyncManifest(
    val syncId: String,
    val title: String,
    val purpose: String,
    val generatedAt: String,
    val generatedWith: String = "ServiceLoop",
    val sections: List<ServiceLoopSyncSectionDeclaration> = emptyList(),
)

data class ServiceLoopSyncSectionDeclaration(
    val name: String,
    val version: Int,
    val path: String,
)

data class ServiceLoopSyncEnvelope(
    val manifest: ServiceLoopSyncManifest,
    val sections: Map<String, ByteArray>,
) {
    fun section(name: String): ByteArray = sections[name] ?: error("Missing ServiceLoop sync section: $name")
}

data class SyncBusinessProfile(
    val businessName: String,
    val technicianName: String,
    val phone: String?,
    val email: String?,
    val postalAddress: String?,
    val zoneId: String,
)

data class SyncCustomer(
    val id: String,
    val reference: String,
    val name: String,
    val contactName: String?,
    val phone: String?,
    val email: String?,
    val privateNote: String?,
    val customerType: String,
    val state: String,
)

data class SyncSite(
    val id: String,
    val customerId: String,
    val reference: String,
    val name: String,
    val address: String?,
    val privateAccessNote: String?,
    val contactName: String?,
    val phone: String?,
    val email: String?,
    val isDefault: Boolean,
    val state: String,
)

data class SyncEquipment(
    val id: String,
    val siteId: String,
    val reference: String,
    val technicianIdentifier: String?,
    val name: String,
    val make: String?,
    val model: String?,
    val serialNumber: String?,
    val privateNote: String?,
    val state: String,
)

data class SyncRegister(
    val customers: List<SyncCustomer>,
    val sites: List<SyncSite>,
    val equipment: List<SyncEquipment>,
)

data class SyncTemplateItem(
    val position: Int,
    val label: String,
    val responseType: String,
    val unit: String?,
    val required: Boolean,
    val privateGuidance: String?,
)

data class SyncTemplate(
    val id: String,
    val reference: String,
    val name: String,
    val state: String,
    val revision: Int,
    val items: List<SyncTemplateItem>,
)

data class SyncInspections(val templates: List<SyncTemplate>)

data class SyncPlan(
    val id: String,
    val reference: String,
    val equipmentId: String,
    val name: String,
    val intervalCount: Int,
    val intervalUnit: String,
    val currentDueDate: String,
    val templateId: String?,
    val state: String,
)

data class SyncPlans(val plans: List<SyncPlan>)

data class SyncTechnician(val technicianId: String, val displayName: String, val designation: String?)

data class SyncTeam(
    val id: String,
    val name: String,
    val memberIds: List<String>,
    val leaderIds: List<String>,
)

data class SyncTeamDirectory(val technicians: List<SyncTechnician>, val teams: List<SyncTeam>)

data class SyncBookedWork(
    val id: String,
    val kind: String,
    val planId: String?,
    val taskName: String?,
    val subjectType: String,
    val equipmentId: String?,
    val equipmentDescription: String?,
    val templateId: String?,
)

data class SyncBookedVisit(
    val id: String,
    val reference: String,
    val siteId: String,
    val serviceDate: String,
    val appointmentTime: String?,
    val zoneId: String,
    val work: List<SyncBookedWork>,
)

data class SyncDispatchItem(
    val dispatchItemId: String,
    val kind: String,
    val planId: String?,
    val assignedTechnicianIds: List<String>,
    val taskName: String? = null,
    val subjectType: String = WorkSubjectType.EQUIPMENT.code,
    val equipmentId: String? = null,
    val equipmentDescription: String? = null,
    val templateId: String? = null,
)

data class SyncDispatchDraft(
    val id: String,
    val managerReference: String?,
    val siteId: String,
    val serviceDate: String,
    val appointmentTime: String?,
    val zoneId: String,
    val instructions: String?,
    val teamIds: List<String>,
    val items: List<SyncDispatchItem>,
)

data class SyncVisits(
    val bookedVisits: List<SyncBookedVisit>,
    val dispatchDrafts: List<SyncDispatchDraft>,
)

data class SyncFollowUp(
    val id: String,
    val reference: String,
    val type: String,
    val title: String,
    val dueDate: String,
    val state: String,
    val customerId: String,
    val siteId: String?,
    val equipmentId: String?,
    val privatePlanningNote: String?,
)

data class SyncContactNote(
    val id: String,
    val reference: String,
    val customerId: String,
    val siteId: String?,
    val equipmentId: String?,
    val channel: String,
    val occurredAt: String,
    val outcome: String,
    val privateNote: String?,
)

data class SyncFollowUps(
    val followUps: List<SyncFollowUp>,
    val contactNotes: List<SyncContactNote>,
)

data class ServiceLoopSyncPackage(
    val manifest: ServiceLoopSyncManifest,
    val business: SyncBusinessProfile,
    val register: SyncRegister,
    val inspections: SyncInspections,
    val plans: SyncPlans,
    val team: SyncTeamDirectory,
    val visits: SyncVisits,
    val followups: SyncFollowUps,
)

data class SyncImportCounts(
    val businessProfiles: Int,
    val customers: Int,
    val sites: Int,
    val equipment: Int,
    val templates: Int,
    val plans: Int,
    val technicians: Int,
    val teams: Int,
    val bookedVisits: Int,
    val followUps: Int,
    val contactNotes: Int,
    val dispatchDrafts: Int,
)

data class SyncImportPreview(val packageValue: ServiceLoopSyncPackage, val counts: SyncImportCounts)

enum class SyncContentFamily(val label: String) {
    BUSINESS_PROFILE("Business profile"),
    CUSTOMERS("Customers"),
    SITES("Sites"),
    EQUIPMENT("Equipment"),
    INSPECTION_TEMPLATES("Inspection templates"),
    SERVICE_PLANS("Service plans"),
    TECHNICIANS("Technicians"),
    TEAMS("Teams"),
    VISITS("Visits"),
    FOLLOW_UPS("Follow-ups"),
    CONTACT_NOTES("Contact notes"),
    DISPATCH_DRAFTS("Dispatch drafts"),
}

fun SyncContentFamily.count(value: ServiceLoopSyncPackage): Int = when (this) {
    SyncContentFamily.BUSINESS_PROFILE -> 1
    SyncContentFamily.CUSTOMERS -> value.register.customers.size
    SyncContentFamily.SITES -> value.register.sites.size
    SyncContentFamily.EQUIPMENT -> value.register.equipment.size
    SyncContentFamily.INSPECTION_TEMPLATES -> value.inspections.templates.size
    SyncContentFamily.SERVICE_PLANS -> value.plans.plans.size
    SyncContentFamily.TECHNICIANS -> value.team.technicians.size
    SyncContentFamily.TEAMS -> value.team.teams.size
    SyncContentFamily.VISITS -> value.visits.bookedVisits.size
    SyncContentFamily.FOLLOW_UPS -> value.followups.followUps.size
    SyncContentFamily.CONTACT_NOTES -> value.followups.contactNotes.size
    SyncContentFamily.DISPATCH_DRAFTS -> value.visits.dispatchDrafts.size
}

/** Returns the smallest safe FULL_WORKSPACE selection for the requested families. */
fun normalizeSyncContentSelection(value: ServiceLoopSyncPackage, requested: Set<SyncContentFamily>): Set<SyncContentFamily> {
    val selected = requested.toMutableSet().apply { add(SyncContentFamily.BUSINESS_PROFILE) }
    fun add(vararg families: SyncContentFamily) { families.forEach { selected += it } }
    if (SyncContentFamily.SITES in selected) add(SyncContentFamily.CUSTOMERS)
    if (SyncContentFamily.EQUIPMENT in selected) add(SyncContentFamily.CUSTOMERS, SyncContentFamily.SITES)
    if (SyncContentFamily.SERVICE_PLANS in selected) add(SyncContentFamily.CUSTOMERS, SyncContentFamily.SITES, SyncContentFamily.EQUIPMENT)
    if (SyncContentFamily.TEAMS in selected) add(SyncContentFamily.TECHNICIANS)
    if (SyncContentFamily.VISITS in selected) add(SyncContentFamily.CUSTOMERS, SyncContentFamily.SITES)
    if (SyncContentFamily.FOLLOW_UPS in selected || SyncContentFamily.CONTACT_NOTES in selected) add(SyncContentFamily.CUSTOMERS)
    if (SyncContentFamily.DISPATCH_DRAFTS in selected) add(SyncContentFamily.CUSTOMERS, SyncContentFamily.SITES, SyncContentFamily.TECHNICIANS, SyncContentFamily.TEAMS)
    if (SyncContentFamily.SERVICE_PLANS in selected && value.plans.plans.any { it.templateId != null }) add(SyncContentFamily.INSPECTION_TEMPLATES)
    if (SyncContentFamily.VISITS in selected) {
        val visits = value.visits.bookedVisits
        if (visits.any { visit -> visit.work.any { it.equipmentId != null } }) add(SyncContentFamily.EQUIPMENT)
        if (visits.any { visit -> visit.work.any { it.planId != null } }) add(SyncContentFamily.SERVICE_PLANS)
        if (visits.any { visit -> visit.work.any { it.templateId != null } }) add(SyncContentFamily.INSPECTION_TEMPLATES)
    }
    if (SyncContentFamily.DISPATCH_DRAFTS in selected) {
        if (value.visits.dispatchDrafts.any { it.items.any { item -> item.equipmentId != null } }) add(SyncContentFamily.EQUIPMENT)
        if (value.visits.dispatchDrafts.any { it.items.any { item -> item.planId != null } }) add(SyncContentFamily.SERVICE_PLANS)
        if (value.visits.dispatchDrafts.any { it.items.any { item -> item.templateId != null } }) add(SyncContentFamily.INSPECTION_TEMPLATES)
    }
    if (SyncContentFamily.FOLLOW_UPS in selected) {
        if (value.followups.followUps.any { it.siteId != null }) add(SyncContentFamily.SITES)
        if (value.followups.followUps.any { it.equipmentId != null }) add(SyncContentFamily.EQUIPMENT)
    }
    if (SyncContentFamily.CONTACT_NOTES in selected) {
        if (value.followups.contactNotes.any { it.siteId != null }) add(SyncContentFamily.SITES)
        if (value.followups.contactNotes.any { it.equipmentId != null }) add(SyncContentFamily.EQUIPMENT)
    }
    // Visit/draft references can add a parent family after the first dependency pass.
    if (SyncContentFamily.SERVICE_PLANS in selected) add(SyncContentFamily.EQUIPMENT, SyncContentFamily.SITES, SyncContentFamily.CUSTOMERS)
    if (SyncContentFamily.EQUIPMENT in selected) add(SyncContentFamily.SITES, SyncContentFamily.CUSTOMERS)
    if (SyncContentFamily.SITES in selected) add(SyncContentFamily.CUSTOMERS)
    if (SyncContentFamily.SERVICE_PLANS in selected && value.plans.plans.any { it.templateId != null }) add(SyncContentFamily.INSPECTION_TEMPLATES)
    return selected.filterTo(linkedSetOf()) { it.count(value) > 0 || it == SyncContentFamily.BUSINESS_PROFILE }
}

/** Removes a family and any selected family whose package content requires it. */
fun removeSyncContentFamily(value: ServiceLoopSyncPackage, selected: Set<SyncContentFamily>, removed: SyncContentFamily): Set<SyncContentFamily> {
    val result = (selected - removed).toMutableSet()
    fun dependencies(family: SyncContentFamily): Set<SyncContentFamily> = buildSet {
        when (family) {
            SyncContentFamily.SITES -> add(SyncContentFamily.CUSTOMERS)
            SyncContentFamily.EQUIPMENT -> add(SyncContentFamily.SITES)
            SyncContentFamily.SERVICE_PLANS -> { add(SyncContentFamily.EQUIPMENT); add(SyncContentFamily.INSPECTION_TEMPLATES) }
            SyncContentFamily.TEAMS -> add(SyncContentFamily.TECHNICIANS)
            SyncContentFamily.VISITS -> { add(SyncContentFamily.SITES); if (value.visits.bookedVisits.any { visit -> visit.work.any { it.equipmentId != null } }) add(SyncContentFamily.EQUIPMENT); if (value.visits.bookedVisits.any { visit -> visit.work.any { it.planId != null } }) add(SyncContentFamily.SERVICE_PLANS); if (value.visits.bookedVisits.any { visit -> visit.work.any { it.templateId != null } }) add(SyncContentFamily.INSPECTION_TEMPLATES) }
            SyncContentFamily.FOLLOW_UPS -> { add(SyncContentFamily.CUSTOMERS); if (value.followups.followUps.any { it.siteId != null }) add(SyncContentFamily.SITES); if (value.followups.followUps.any { it.equipmentId != null }) add(SyncContentFamily.EQUIPMENT) }
            SyncContentFamily.CONTACT_NOTES -> { add(SyncContentFamily.CUSTOMERS); if (value.followups.contactNotes.any { it.siteId != null }) add(SyncContentFamily.SITES); if (value.followups.contactNotes.any { it.equipmentId != null }) add(SyncContentFamily.EQUIPMENT) }
            SyncContentFamily.DISPATCH_DRAFTS -> { add(SyncContentFamily.CUSTOMERS); add(SyncContentFamily.SITES); add(SyncContentFamily.TECHNICIANS); add(SyncContentFamily.TEAMS); if (value.visits.dispatchDrafts.any { it.items.any { item -> item.equipmentId != null } }) add(SyncContentFamily.EQUIPMENT); if (value.visits.dispatchDrafts.any { it.items.any { item -> item.planId != null } }) add(SyncContentFamily.SERVICE_PLANS); if (value.visits.dispatchDrafts.any { it.items.any { item -> item.templateId != null } }) add(SyncContentFamily.INSPECTION_TEMPLATES) }
            else -> Unit
        }
    }
    var changed: Boolean
    do { changed = false; result.toList().forEach { family -> if (family != SyncContentFamily.BUSINESS_PROFILE && dependencies(family).any { it !in result } && result.remove(family)) changed = true } } while (changed)
    result += SyncContentFamily.BUSINESS_PROFILE
    return result
}

fun filterFullWorkspacePackage(value: ServiceLoopSyncPackage, requested: Set<SyncContentFamily>): ServiceLoopSyncPackage {
    val selected = normalizeSyncContentSelection(value, requested)
    return value.copy(
        register = value.register.copy(
            customers = value.register.customers.filter { SyncContentFamily.CUSTOMERS in selected },
            sites = value.register.sites.filter { SyncContentFamily.SITES in selected },
            equipment = value.register.equipment.filter { SyncContentFamily.EQUIPMENT in selected },
        ),
        inspections = value.inspections.copy(templates = value.inspections.templates.filter { SyncContentFamily.INSPECTION_TEMPLATES in selected }),
        plans = value.plans.copy(plans = value.plans.plans.filter { SyncContentFamily.SERVICE_PLANS in selected }),
        team = value.team.copy(
            technicians = value.team.technicians.filter { SyncContentFamily.TECHNICIANS in selected },
            teams = value.team.teams.filter { SyncContentFamily.TEAMS in selected },
        ),
        visits = value.visits.copy(
            bookedVisits = value.visits.bookedVisits.filter { SyncContentFamily.VISITS in selected },
            dispatchDrafts = value.visits.dispatchDrafts.filter { SyncContentFamily.DISPATCH_DRAFTS in selected },
        ),
        followups = value.followups.copy(
            followUps = value.followups.followUps.filter { SyncContentFamily.FOLLOW_UPS in selected },
            contactNotes = value.followups.contactNotes.filter { SyncContentFamily.CONTACT_NOTES in selected },
        ),
    )
}

data class SyncImportResult(val counts: SyncImportCounts, val importedAtEpochMillis: Long, val zoneId: String)

object ServiceLoopSyncEnvelopeCodec {
    const val FORMAT = "ServiceLoopSync"
    const val FORMAT_VERSION = 1
    const val MAX_PACKAGE_BYTES = 16 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 32L * 1024 * 1024
    const val MAX_ENTRIES = 16

    fun encode(manifest: ServiceLoopSyncManifest, sections: Map<String, ByteArray>): ByteArray {
        require(manifest.syncId.isNotBlank() && manifest.title.isNotBlank())
        require(manifest.purpose in setOf("FULL_WORKSPACE", "WORK_ASSIGNMENT", "TEMPLATE_SHARE"))
        Instant.parse(manifest.generatedAt)
        require(manifest.generatedWith == "ServiceLoop")
        require(manifest.sections.size == sections.size && manifest.sections.map { it.name }.toSet().size == sections.size)
        val entries = linkedMapOf("manifest.json" to manifestJson(manifest).toByteArray(Charsets.UTF_8))
        manifest.sections.forEach { declaration ->
            require(declaration.version > 0 && declaration.path == declaration.path.trim() && isSafeEntryName(declaration.path))
            entries[declaration.path] = sections[declaration.name] ?: error("Missing section ${declaration.name}")
        }
        require(entries.size <= MAX_ENTRIES)
        return ByteArrayOutputStream().also { output -> ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
    }

    fun decode(bytes: ByteArray): ServiceLoopSyncEnvelope {
        require(bytes.size <= MAX_PACKAGE_BYTES) { "Selected sync is too large" }
        val entries = LinkedHashMap<String, ByteArray>()
        var expanded = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(entries.size < MAX_ENTRIES) { "Sync contains too many entries" }
                require(!entry.isDirectory && isSafeEntryName(entry.name)) { "Unsafe sync entry" }
                require(entries.put(entry.name, zip.readBounded(MAX_EXPANDED_BYTES - expanded)) == null) { "Duplicate sync entry" }
                expanded += entries.getValue(entry.name).size
                require(expanded <= MAX_EXPANDED_BYTES) { "Expanded sync is too large" }
            }
        }
        val manifestObject = JSONObject(entries["manifest.json"]?.toString(Charsets.UTF_8) ?: error("Sync manifest is missing"))
        val manifest = parseManifest(manifestObject)
        require(entries.keys == (setOf("manifest.json") + manifest.sections.map { it.path })) { "Sync entries do not match the manifest" }
        return ServiceLoopSyncEnvelope(manifest, manifest.sections.associate { it.name to entries.getValue(it.path) })
    }

    fun wrapWorkAssignment(value: DispatchPackage): ByteArray = encode(
        ServiceLoopSyncManifest(value.packageId, "ServiceLoop work assignment", "WORK_ASSIGNMENT", value.createdAt, sections = listOf(ServiceLoopSyncSectionDeclaration("working", DispatchPackageCodec.CURRENT_VERSION, "working.json"))),
        mapOf("working" to DispatchPackageCodec.encode(value)),
    )

    fun unwrapWorkAssignment(bytes: ByteArray): DispatchPackage {
        val envelope = decode(bytes)
        require(envelope.manifest.purpose == "WORK_ASSIGNMENT") { "This ServiceLoop file is not a work assignment" }
        require(envelope.manifest.sections == listOf(ServiceLoopSyncSectionDeclaration("working", DispatchPackageCodec.CURRENT_VERSION, "working.json"))) { "Invalid work-assignment sections" }
        return DispatchPackageCodec.decode(envelope.section("working"))
    }

    fun wrapTemplateShare(value: InspectionTemplateTransfer): ByteArray = encode(
        ServiceLoopSyncManifest(UUID.randomUUID().toString(), "ServiceLoop inspection templates", "TEMPLATE_SHARE", value.generatedAt, sections = listOf(ServiceLoopSyncSectionDeclaration("inspections", InspectionTemplateCodec.CURRENT_VERSION, "inspections.json"))),
        mapOf("inspections" to InspectionTemplateCodec.encode(value)),
    )

    fun unwrapTemplateShare(bytes: ByteArray): InspectionTemplateTransfer {
        val envelope = decode(bytes)
        require(envelope.manifest.purpose == "TEMPLATE_SHARE") { "This ServiceLoop file is not a template share" }
        require(envelope.manifest.sections == listOf(ServiceLoopSyncSectionDeclaration("inspections", InspectionTemplateCodec.CURRENT_VERSION, "inspections.json"))) { "Invalid template-share sections" }
        return InspectionTemplateCodec.decode(envelope.section("inspections"))
    }

    private fun manifestJson(manifest: ServiceLoopSyncManifest) = JSONObject()
        .put("format", FORMAT).put("formatVersion", FORMAT_VERSION).put("syncId", manifest.syncId)
        .put("title", manifest.title).put("purpose", manifest.purpose).put("generatedAt", manifest.generatedAt)
        .put("generatedWith", manifest.generatedWith)
        .put("sections", JSONArray().also { array -> manifest.sections.forEach { array.put(JSONObject().put("name", it.name).put("version", it.version).put("path", it.path)) } }).toString()

    private fun parseManifest(o: JSONObject): ServiceLoopSyncManifest {
        require(o.requiredText("format") == FORMAT && o.getInt("formatVersion") == FORMAT_VERSION)
        val purpose = o.requiredText("purpose")
        require(purpose in setOf("FULL_WORKSPACE", "WORK_ASSIGNMENT", "TEMPLATE_SHARE"))
        val generatedAt = o.requiredText("generatedAt"); Instant.parse(generatedAt)
        require(o.requiredText("generatedWith") == "ServiceLoop")
        val sectionsObject = o.getJSONArray("sections")
        val sections = (0 until sectionsObject.length()).map { index ->
            val section = sectionsObject.getJSONObject(index)
            ServiceLoopSyncSectionDeclaration(section.requiredText("name"), section.getInt("version"), section.requiredText("path"))
        }
        require(sections.isNotEmpty() && sections.map { it.name }.distinct().size == sections.size && sections.map { it.path }.distinct().size == sections.size)
        require(sections.all { isSafeEntryName(it.path) })
        return ServiceLoopSyncManifest(o.requiredText("syncId"), o.requiredText("title"), purpose, generatedAt, "ServiceLoop", sections)
    }

    private fun isSafeEntryName(name: String) = name.isNotBlank() && !name.contains('/') && !name.contains('\\') && !name.contains(':') && name != "." && name != ".."
    private fun InputStream.readBounded(remaining: Long): ByteArray { require(remaining >= 0); val out = ByteArrayOutputStream(); val buffer = ByteArray(8192); while (true) { val count = read(buffer); if (count < 0) break; require(out.size() + count <= remaining) { "Expanded sync is too large" }; out.write(buffer, 0, count) }; return out.toByteArray() }
    private fun JSONObject.requiredText(name: String, max: Int = 4_000): String = getString(name).trim().also { require(it.isNotEmpty() && it.length <= max) { "Invalid $name" } }
}

object ServiceLoopSyncCodec {
    const val FORMAT = "ServiceLoopSync"
    const val FORMAT_VERSION = 1
    const val PURPOSE_FULL_WORKSPACE = "FULL_WORKSPACE"
    const val MAX_PACKAGE_BYTES = 16 * 1024 * 1024
    const val MAX_EXPANDED_BYTES = 32L * 1024 * 1024
    const val MAX_ENTRIES = 16
    private val sectionNames = listOf("business", "register", "inspections", "plans", "team", "visits", "followups")
    private val expectedEntries = (listOf("manifest.json") + sectionNames.map { "$it.json" }).toSet()

    fun preview(value: ServiceLoopSyncPackage): SyncImportPreview = SyncImportPreview(value, SyncImportCounts(
        businessProfiles = 1,
        customers = value.register.customers.size,
        sites = value.register.sites.size,
        equipment = value.register.equipment.size,
        templates = value.inspections.templates.size,
        plans = value.plans.plans.size,
        technicians = value.team.technicians.size,
        teams = value.team.teams.size,
        bookedVisits = value.visits.bookedVisits.size,
        followUps = value.followups.followUps.size,
        contactNotes = value.followups.contactNotes.size,
        dispatchDrafts = value.visits.dispatchDrafts.size,
    ))

    fun encode(value: ServiceLoopSyncPackage): ByteArray {
        validatePackage(value)
        val sectionDeclarations = sectionNames.map { ServiceLoopSyncSectionDeclaration(it, 1, "$it.json") }
        return ServiceLoopSyncEnvelopeCodec.encode(
            value.manifest.copy(purpose = PURPOSE_FULL_WORKSPACE, sections = sectionDeclarations),
            mapOf(
                "business" to businessJson(value.business).toByteArray(Charsets.UTF_8),
                "register" to registerJson(value.register).toByteArray(Charsets.UTF_8),
                "inspections" to inspectionsJson(value.inspections).toByteArray(Charsets.UTF_8),
                "plans" to plansJson(value.plans).toByteArray(Charsets.UTF_8),
                "team" to teamJson(value.team).toByteArray(Charsets.UTF_8),
                "visits" to visitsJson(value.visits).toByteArray(Charsets.UTF_8),
                "followups" to followupsJson(value.followups).toByteArray(Charsets.UTF_8),
            ),
        )
    }

    fun decode(bytes: ByteArray): ServiceLoopSyncPackage {
        val envelope = ServiceLoopSyncEnvelopeCodec.decode(bytes)
        require(envelope.manifest.purpose == PURPOSE_FULL_WORKSPACE) { "Unsupported ServiceLoop sync purpose" }
        require(envelope.manifest.sections == sectionNames.map { ServiceLoopSyncSectionDeclaration(it, 1, "$it.json") }) { "Sync entries do not match the v1 contract" }
        val sections = envelope.sections
        return ServiceLoopSyncPackage(
            // Keep the B045 package model value-compatible; section declarations belong to the generic envelope.
            manifest = envelope.manifest.copy(sections = emptyList()),
            business = parseBusiness(JSONObject(sections.getValue("business").toString(Charsets.UTF_8))),
            register = parseRegister(JSONObject(sections.getValue("register").toString(Charsets.UTF_8))),
            inspections = parseInspections(JSONObject(sections.getValue("inspections").toString(Charsets.UTF_8))),
            plans = parsePlans(JSONObject(sections.getValue("plans").toString(Charsets.UTF_8))),
            team = parseTeam(JSONObject(sections.getValue("team").toString(Charsets.UTF_8))),
            visits = parseVisits(JSONObject(sections.getValue("visits").toString(Charsets.UTF_8))),
            followups = parseFollowups(JSONObject(sections.getValue("followups").toString(Charsets.UTF_8))),
        ).also(::validatePackage)
    }

    private fun manifestJson(value: ServiceLoopSyncPackage) = JSONObject().put("format", FORMAT).put("formatVersion", FORMAT_VERSION).put("syncId", value.manifest.syncId).put("title", value.manifest.title).put("purpose", PURPOSE_FULL_WORKSPACE).put("generatedAt", value.manifest.generatedAt).put("generatedWith", "ServiceLoop").put("sections", JSONArray().also { array -> sectionNames.forEach { name -> array.put(JSONObject().put("name", name).put("version", 1).put("path", "$name.json")) } }).toString()
    private fun businessJson(value: SyncBusinessProfile) = JSONObject().put("businessProfile", JSONObject().put("businessName", value.businessName).put("technicianName", value.technicianName).putNullable("phone", value.phone).putNullable("email", value.email).putNullable("postalAddress", value.postalAddress).put("zoneId", value.zoneId)).toString()
    private fun registerJson(value: SyncRegister) = JSONObject().put("customers", JSONArray().also { a -> value.customers.forEach { c -> a.put(JSONObject().put("id", c.id).put("reference", c.reference).put("name", c.name).putNullable("contactName", c.contactName).putNullable("phone", c.phone).putNullable("email", c.email).putNullable("privateNote", c.privateNote).put("customerType", c.customerType).put("state", c.state)) } }).put("sites", JSONArray().also { a -> value.sites.forEach { s -> a.put(JSONObject().put("id", s.id).put("customerId", s.customerId).put("reference", s.reference).put("name", s.name).putNullable("address", s.address).putNullable("privateAccessNote", s.privateAccessNote).putNullable("contactName", s.contactName).putNullable("phone", s.phone).putNullable("email", s.email).put("isDefault", s.isDefault).put("state", s.state)) } }).put("equipment", JSONArray().also { a -> value.equipment.forEach { e -> a.put(JSONObject().put("id", e.id).put("siteId", e.siteId).put("reference", e.reference).putNullable("technicianIdentifier", e.technicianIdentifier).put("name", e.name).putNullable("make", e.make).putNullable("model", e.model).putNullable("serialNumber", e.serialNumber).putNullable("privateNote", e.privateNote).put("state", e.state)) } }).toString()
    private fun inspectionsJson(value: SyncInspections) = JSONObject().put("templates", JSONArray().also { a -> value.templates.forEach { t -> a.put(JSONObject().put("id", t.id).put("reference", t.reference).put("name", t.name).put("state", t.state).put("revision", t.revision).put("items", JSONArray().also { items -> t.items.forEach { i -> items.put(JSONObject().put("position", i.position).put("label", i.label).put("responseType", i.responseType).putNullable("unit", i.unit).put("required", i.required).putNullable("privateGuidance", i.privateGuidance)) } })) } }).toString()
    private fun plansJson(value: SyncPlans) = JSONObject().put("plans", JSONArray().also { a -> value.plans.forEach { p -> a.put(JSONObject().put("id", p.id).put("reference", p.reference).put("equipmentId", p.equipmentId).put("name", p.name).put("intervalCount", p.intervalCount).put("intervalUnit", p.intervalUnit).put("currentDueDate", p.currentDueDate).putNullable("templateId", p.templateId).put("state", p.state)) } }).toString()
    private fun teamJson(value: SyncTeamDirectory) = JSONObject().put("technicians", JSONArray().also { a -> value.technicians.forEach { t -> a.put(JSONObject().put("technicianId", t.technicianId).put("displayName", t.displayName).putNullable("designation", t.designation)) } }).put("teams", JSONArray().also { a -> value.teams.forEach { t -> a.put(JSONObject().put("id", t.id).put("name", t.name).put("memberIds", JSONArray(t.memberIds)).put("leaderIds", JSONArray(t.leaderIds))) } }).toString()
    private fun visitsJson(value: SyncVisits) = JSONObject().put("bookedVisits", JSONArray().also { a -> value.bookedVisits.forEach { v -> a.put(JSONObject().put("id", v.id).put("reference", v.reference).put("siteId", v.siteId).put("serviceDate", v.serviceDate).putNullable("appointmentTime", v.appointmentTime).put("zoneId", v.zoneId).put("work", JSONArray().also { items -> v.work.forEach { w -> items.put(JSONObject().put("id", w.id).put("kind", w.kind).putNullable("planId", w.planId).putNullable("taskName", w.taskName).put("subjectType", w.subjectType).putNullable("equipmentId", w.equipmentId).putNullable("equipmentDescription", w.equipmentDescription).putNullable("templateId", w.templateId)) } })) } }).put("dispatchDrafts", JSONArray().also { a -> value.dispatchDrafts.forEach { v -> a.put(JSONObject().put("id", v.id).putNullable("managerReference", v.managerReference).put("siteId", v.siteId).put("serviceDate", v.serviceDate).putNullable("appointmentTime", v.appointmentTime).put("zoneId", v.zoneId).putNullable("instructions", v.instructions).put("teamIds", JSONArray(v.teamIds)).put("items", JSONArray().also { items -> v.items.forEach { i -> items.put(JSONObject().put("dispatchItemId", i.dispatchItemId).put("kind", i.kind).putNullable("planId", i.planId).put("assignedTechnicianIds", JSONArray(i.assignedTechnicianIds)).putNullable("taskName", i.taskName).put("subjectType", i.subjectType).putNullable("equipmentId", i.equipmentId).putNullable("equipmentDescription", i.equipmentDescription).putNullable("templateId", i.templateId)) } })) } }).toString()
    private fun followupsJson(value: SyncFollowUps) = JSONObject().put("followUps", JSONArray().also { a -> value.followUps.forEach { f -> a.put(JSONObject().put("id", f.id).put("reference", f.reference).put("type", f.type).put("title", f.title).put("dueDate", f.dueDate).put("state", f.state).put("customerId", f.customerId).putNullable("siteId", f.siteId).putNullable("equipmentId", f.equipmentId).putNullable("privatePlanningNote", f.privatePlanningNote)) } }).put("contactNotes", JSONArray().also { a -> value.contactNotes.forEach { n -> a.put(JSONObject().put("id", n.id).put("reference", n.reference).put("customerId", n.customerId).putNullable("siteId", n.siteId).putNullable("equipmentId", n.equipmentId).put("channel", n.channel).put("occurredAt", n.occurredAt).put("outcome", n.outcome).putNullable("privateNote", n.privateNote)) } }).toString()

    private fun parseManifest(o: JSONObject): ServiceLoopSyncManifest {
        require(o.requiredText("format") == FORMAT && o.getInt("formatVersion") == FORMAT_VERSION && o.requiredText("purpose") == PURPOSE_FULL_WORKSPACE) { "Unsupported ServiceLoop sync" }
        val generatedAt = o.requiredText("generatedAt"); Instant.parse(generatedAt)
        require(o.requiredText("generatedWith") == "ServiceLoop") { "Unsupported sync generator" }
        return ServiceLoopSyncManifest(o.requiredText("syncId"), o.requiredText("title"), PURPOSE_FULL_WORKSPACE, generatedAt)
    }

    private fun parseBusiness(o: JSONObject): SyncBusinessProfile { val p=o.getJSONObject("businessProfile"); val zone=p.requiredText("zoneId"); ZoneId.of(zone); return SyncBusinessProfile(p.requiredText("businessName"),p.requiredText("technicianName"),p.nullableText("phone"),p.nullableText("email"),p.nullableText("postalAddress"),zone) }
    private fun parseRegister(o: JSONObject): SyncRegister {
        fun arr(name:String, read:(JSONObject)->Unit) { val a=o.getJSONArray(name); for(i in 0 until a.length()) read(a.getJSONObject(i)) }
        val customers=mutableListOf<SyncCustomer>(); arr("customers"){c->customers+=SyncCustomer(c.requiredText("id"),c.requiredText("reference"),c.requiredText("name"),c.nullableText("contactName"),c.nullableText("phone"),c.nullableText("email"),c.nullableText("privateNote"),c.requiredText("customerType"),c.requiredText("state"))}
        val sites=mutableListOf<SyncSite>(); arr("sites"){s->sites+=SyncSite(s.requiredText("id"),s.requiredText("customerId"),s.requiredText("reference"),s.requiredText("name"),s.nullableText("address"),s.nullableText("privateAccessNote"),s.nullableText("contactName"),s.nullableText("phone"),s.nullableText("email"),s.getBoolean("isDefault"),s.requiredText("state"))}
        val equipment=mutableListOf<SyncEquipment>(); arr("equipment"){e->equipment+=SyncEquipment(e.requiredText("id"),e.requiredText("siteId"),e.requiredText("reference"),e.nullableText("technicianIdentifier"),e.requiredText("name"),e.nullableText("make"),e.nullableText("model"),e.nullableText("serialNumber"),e.nullableText("privateNote"),e.requiredText("state"))}
        return SyncRegister(customers,sites,equipment)
    }
    private fun parseInspections(o: JSONObject): SyncInspections { val templates=o.getJSONArray("templates").let { a -> List(a.length()){ val t=a.getJSONObject(it); val items=t.getJSONArray("items").let { x->List(x.length()){ val i=x.getJSONObject(it); SyncTemplateItem(i.getInt("position"),i.requiredText("label"),i.requiredText("responseType"),i.nullableText("unit"),i.getBoolean("required"),i.nullableText("privateGuidance")) } }; SyncTemplate(t.requiredText("id"),t.requiredText("reference"),t.requiredText("name"),t.requiredText("state"),t.getInt("revision"),items) } }; return SyncInspections(templates) }
    private fun parsePlans(o: JSONObject): SyncPlans { val a=o.getJSONArray("plans"); return SyncPlans(List(a.length()){ val p=a.getJSONObject(it); SyncPlan(p.requiredText("id"),p.requiredText("reference"),p.requiredText("equipmentId"),p.requiredText("name"),p.getInt("intervalCount"),p.requiredText("intervalUnit"),p.requiredText("currentDueDate"),p.nullableText("templateId"),p.requiredText("state")) }) }
    private fun stringList(o:JSONObject,name:String)=o.getJSONArray(name).let{a->List(a.length()){a.getString(it).trim().also{v->require(v.isNotEmpty())}}}
    private fun parseTeam(o:JSONObject):SyncTeamDirectory { val t=o.getJSONArray("technicians").let{a->List(a.length()){val v=a.getJSONObject(it);SyncTechnician(v.requiredText("technicianId"),v.requiredText("displayName"),v.nullableText("designation"))}}; val teams=o.getJSONArray("teams").let{a->List(a.length()){val v=a.getJSONObject(it);SyncTeam(v.requiredText("id"),v.requiredText("name"),stringList(v,"memberIds"),stringList(v,"leaderIds"))}};return SyncTeamDirectory(t,teams) }
    private fun parseWork(o:JSONObject)=SyncBookedWork(o.requiredText("id"),o.requiredText("kind"),o.nullableText("planId"),o.nullableText("taskName"),o.requiredText("subjectType"),o.nullableText("equipmentId"),o.nullableText("equipmentDescription"),o.nullableText("templateId"))
    private fun parseVisits(o:JSONObject):SyncVisits { val booked=o.getJSONArray("bookedVisits").let{a->List(a.length()){val v=a.getJSONObject(it);val time=v.nullableText("appointmentTime");time?.let{LocalTime.parse(it)};LocalDate.parse(v.requiredText("serviceDate"));ZoneId.of(v.requiredText("zoneId"));SyncBookedVisit(v.requiredText("id"),v.requiredText("reference"),v.requiredText("siteId"),v.requiredText("serviceDate"),time,v.requiredText("zoneId"),v.getJSONArray("work").let{x->List(x.length()){parseWork(x.getJSONObject(it))}})}}; val drafts=o.getJSONArray("dispatchDrafts").let{a->List(a.length()){val v=a.getJSONObject(it);val time=v.nullableText("appointmentTime");time?.let{LocalTime.parse(it)};LocalDate.parse(v.requiredText("serviceDate"));ZoneId.of(v.requiredText("zoneId"));SyncDispatchDraft(v.requiredText("id"),v.nullableText("managerReference"),v.requiredText("siteId"),v.requiredText("serviceDate"),time,v.requiredText("zoneId"),v.nullableText("instructions"),stringList(v,"teamIds"),v.getJSONArray("items").let{x->List(x.length()){val i=x.getJSONObject(it);SyncDispatchItem(i.requiredText("dispatchItemId"),i.requiredText("kind"),i.nullableText("planId"),stringList(i,"assignedTechnicianIds"),i.nullableText("taskName"),i.requiredText("subjectType"),i.nullableText("equipmentId"),i.nullableText("equipmentDescription"),i.nullableText("templateId"))}})}}; return SyncVisits(booked,drafts) }
    private fun parseFollowups(o:JSONObject):SyncFollowUps { val f=o.getJSONArray("followUps").let{a->List(a.length()){val v=a.getJSONObject(it);LocalDate.parse(v.requiredText("dueDate"));SyncFollowUp(v.requiredText("id"),v.requiredText("reference"),v.requiredText("type"),v.requiredText("title"),v.requiredText("dueDate"),v.requiredText("state"),v.requiredText("customerId"),v.nullableText("siteId"),v.nullableText("equipmentId"),v.nullableText("privatePlanningNote"))}}; val n=o.getJSONArray("contactNotes").let{a->List(a.length()){val v=a.getJSONObject(it);Instant.parse(v.requiredText("occurredAt"));SyncContactNote(v.requiredText("id"),v.requiredText("reference"),v.requiredText("customerId"),v.nullableText("siteId"),v.nullableText("equipmentId"),v.requiredText("channel"),v.requiredText("occurredAt"),v.requiredText("outcome"),v.nullableText("privateNote"))}}; return SyncFollowUps(f,n) }

    private fun validatePackage(value: ServiceLoopSyncPackage) {
        require(value.manifest.purpose == PURPOSE_FULL_WORKSPACE && value.manifest.syncId.isNotBlank() && value.manifest.title.isNotBlank()); Instant.parse(value.manifest.generatedAt); ZoneId.of(value.business.zoneId)
        require(value.register.customers.map { it.id }.size == value.register.customers.map { it.id }.toSet().size); require(value.register.sites.map { it.id }.size == value.register.sites.map { it.id }.toSet().size); require(value.register.equipment.map { it.id }.size == value.register.equipment.map { it.id }.toSet().size)
        val customers=value.register.customers.map { it.id }.toSet(); val sites=value.register.sites.map { it.id }.toSet(); val equipment=value.register.equipment.map { it.id }.toSet(); val templates=value.inspections.templates.map { it.id }.toSet(); val plans=value.plans.plans.map { it.id }.toSet(); val techs=value.team.technicians.map { it.technicianId }.toSet(); val teams=value.team.teams.map { it.id }.toSet()
        require(value.register.sites.all { it.customerId in customers }); require(value.register.equipment.all { it.siteId in sites }); require(value.inspections.templates.all { it.state in setOf("ACTIVE","DISABLED") && it.revision > 0 && it.items.map { item -> item.position }.toSet().size == it.items.size && it.items.all { item -> item.position > 0 } }); require(value.plans.plans.all { it.state == "ACTIVE" && it.equipmentId in equipment && it.intervalCount > 0 && it.intervalUnit in setOf("DAYS","WEEKS","MONTHS","YEARS") && it.templateId?.let { id -> id in templates } != false }); value.plans.plans.forEach { LocalDate.parse(it.currentDueDate) }
        require(value.team.technicians.map { it.technicianId }.size == techs.size && value.team.teams.map { it.id }.size == teams.size); require(value.team.teams.all { team -> team.memberIds.all { it in techs } && team.leaderIds.all { it in team.memberIds } })
        value.visits.bookedVisits.forEach { visit -> require(visit.siteId in sites && visit.work.isNotEmpty()); visit.work.forEach { work -> when(work.kind) { "PLAN" -> require(work.planId in plans && work.taskName == null && work.templateId == null); "AD_HOC" -> { require(work.planId == null && !work.taskName.isNullOrBlank() && work.templateId in templates); WorkSubjectType.fromCode(work.subjectType); if(work.equipmentId != null) require(work.equipmentId in equipment) }; else -> error("Unsupported booked work kind") } } }
        value.visits.dispatchDrafts.forEach { draft -> require(draft.siteId in sites && draft.teamIds.all { it in teams } && draft.items.isNotEmpty()); draft.items.forEach { item -> when(item.kind) { "PLAN" -> require(item.planId in plans && item.taskName == null && item.equipmentId == null && item.templateId == null); "AD_HOC" -> { require(item.planId == null && !item.taskName.isNullOrBlank()); WorkSubjectType.fromCode(item.subjectType); if(item.equipmentId != null) require(item.equipmentId in equipment); if(item.templateId != null) require(item.templateId in templates) }; else -> error("Unsupported dispatch work kind") }; require(item.assignedTechnicianIds.all { it in techs }) } }
        value.followups.followUps.forEach { f -> require(f.state == "OPEN" && f.type in setOf("CONTACT","CORRECTIVE") && f.customerId in customers && f.siteId?.let { it in sites } != false && f.equipmentId?.let { it in equipment } != false); LocalDate.parse(f.dueDate) }; value.followups.contactNotes.forEach { n -> require(n.customerId in customers && n.siteId?.let { it in sites } != false && n.equipmentId?.let { it in equipment } != false); Instant.parse(n.occurredAt); require(n.channel in setOf("CALL","SMS","EMAIL","IN_PERSON","OTHER")) }
    }

    private fun isSafeEntryName(name:String)=name.isNotBlank()&&!name.contains('/')&&!name.contains('\\')&&!name.contains(':')&&name!="."&&name!=".."
    private fun InputStream.readBounded(remaining:Long):ByteArray { require(remaining>=0); val out=ByteArrayOutputStream(); val buffer=ByteArray(8192); while(true){val count=read(buffer);if(count<0)break;require(out.size()+count<=remaining){"Expanded sync is too large"};out.write(buffer,0,count)};return out.toByteArray() }
    private fun JSONObject.putNullable(name:String,value:String?):JSONObject=put(name,value?:JSONObject.NULL)
    private fun JSONObject.requiredText(name:String,max:Int=4_000):String=getString(name).trim().also{require(it.isNotEmpty()&&it.length<=max){"Invalid $name"}}
    private fun JSONObject.nullableText(name:String,max:Int=4_000):String?=if(!has(name)||isNull(name))null else getString(name).trim().also{require(it.length<=max){"$name is too long"}}.takeIf{it.isNotEmpty()}
}
