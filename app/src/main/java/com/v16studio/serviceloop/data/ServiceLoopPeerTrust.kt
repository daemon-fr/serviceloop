package com.v16studio.serviceloop.data

import androidx.room.withTransaction

enum class ServiceLoopSourceTrust { TRUSTED, NOT_TRUSTED }

data class ServiceLoopTrustDecision(
    val sourceId: String,
    val friendlyName: String?,
    val trust: ServiceLoopSourceTrust,
) {
    val canImport: Boolean get() = trust == ServiceLoopSourceTrust.TRUSTED
}

/** Device-local peer IDs and trust registry. IDs identify a peer; they do not prove possession. */
class ServiceLoopPeerTrustStore(private val database: ServiceLoopDatabase) {
    private val dispatch = database.dispatchDao()

    suspend fun localIdentity(): TechnicianIdentityEntity = database.withTransaction {
        dispatch.technicianIdentity() ?: run {
            val now = System.currentTimeMillis()
            val row = TechnicianIdentityEntity(
                technicianId = TechnicianIdCodec.generate(),
                displayName = "Technician",
                createdAtEpochMillis = now,
                modifiedAtEpochMillis = now,
            )
            dispatch.insertTechnicianIdentity(row)
            row
        }
    }

    suspend fun trustedIds(): List<TrustedServiceLoopIdEntity> = dispatch.trustedServiceLoopIds()

    suspend fun assess(exporterId: String): ServiceLoopTrustDecision {
        val normalized = TechnicianIdCodec.normalize(exporterId)
            ?: throw IllegalArgumentException("ServiceLoop file source ID is invalid")
        val own = localIdentity().technicianId
        val ownCanonical = TechnicianIdCodec.normalize(own) ?: error("Local technician identity is invalid")
        val trusted = dispatch.trustedServiceLoopId(normalized)
        return when {
            normalized == ownCanonical -> ServiceLoopTrustDecision(normalized, "This device", ServiceLoopSourceTrust.TRUSTED)
            trusted != null -> ServiceLoopTrustDecision(normalized, trusted.name, ServiceLoopSourceTrust.TRUSTED)
            else -> ServiceLoopTrustDecision(normalized, null, ServiceLoopSourceTrust.NOT_TRUSTED)
        }
    }

    suspend fun requireTrusted(exporterId: String) {
        val decision = assess(exporterId)
        require(decision.canImport) { "Add this ServiceLoop source ID to Trusted IDs before importing." }
    }

    /** Caller owns a Room transaction so the final import decision and first mutation share one boundary. */
    suspend fun requireTrustedInCurrentTransaction(exporterId: String) {
        val normalized = TechnicianIdCodec.normalize(exporterId)
            ?: throw IllegalArgumentException("ServiceLoop file source ID is invalid")
        val ownId = dispatch.technicianIdentity()?.technicianId ?: run {
            val now = System.currentTimeMillis()
            TechnicianIdentityEntity(technicianId = TechnicianIdCodec.generate(), displayName = "Technician", createdAtEpochMillis = now, modifiedAtEpochMillis = now)
                .also { dispatch.insertTechnicianIdentity(it) }.technicianId
        }
        val ownCanonical = TechnicianIdCodec.normalize(ownId) ?: error("Local technician identity is invalid")
        require(normalized == ownCanonical || dispatch.trustedServiceLoopId(normalized) != null) {
            "Add this ServiceLoop source ID to Trusted IDs before importing."
        }
    }

    suspend fun add(peerIdInput: String, nameInput: String): TrustedServiceLoopIdEntity {
        val peerId = TechnicianIdCodec.normalize(peerIdInput)
            ?: throw IllegalArgumentException("Enter a valid ServiceLoop ID")
        val name = nameInput.trim()
        require(name.isNotEmpty()) { "Friendly name is required" }
        require(name.length <= 80) { "Friendly name must be 80 characters or fewer" }
        val own = localIdentity().technicianId
        val ownCanonical = TechnicianIdCodec.normalize(own) ?: error("Local technician identity is invalid")
        require(peerId != ownCanonical) { "This device's own ID is already trusted" }
        require(dispatch.trustedServiceLoopId(peerId) == null) { "This ID is already trusted" }
        val now = System.currentTimeMillis()
        val entity = TrustedServiceLoopIdEntity(peerId, name, now, now)
        dispatch.insertTrustedServiceLoopId(entity)
        return entity
    }

    suspend fun rename(peerIdInput: String, nameInput: String) {
        val peerId = TechnicianIdCodec.normalize(peerIdInput) ?: throw IllegalArgumentException("Invalid ServiceLoop ID")
        val row = dispatch.trustedServiceLoopId(peerId) ?: error("Trusted ID no longer exists")
        val name = nameInput.trim()
        require(name.isNotEmpty()) { "Friendly name is required" }
        require(name.length <= 80) { "Friendly name must be 80 characters or fewer" }
        dispatch.updateTrustedServiceLoopId(row.copy(name = name, modifiedAtEpochMillis = System.currentTimeMillis()))
    }

    suspend fun remove(peerIdInput: String) {
        val peerId = TechnicianIdCodec.normalize(peerIdInput) ?: throw IllegalArgumentException("Invalid ServiceLoop ID")
        require(dispatch.deleteTrustedServiceLoopId(peerId) == 1) { "Trusted ID no longer exists" }
    }
}
