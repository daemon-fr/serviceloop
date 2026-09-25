package com.v16studio.v16service.data

import androidx.room.withTransaction

enum class V16ServiceSourceTrust { TRUSTED, NOT_TRUSTED }

data class V16ServiceTrustDecision(
    val sourceId: String,
    val friendlyName: String?,
    val trust: V16ServiceSourceTrust,
) {
    val canImport: Boolean get() = trust == V16ServiceSourceTrust.TRUSTED
}

/** Device-local peer IDs and trust registry. IDs identify a peer; they do not prove possession. */
class V16ServicePeerTrustStore(private val database: V16ServiceDatabase) {
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

    suspend fun trustedIds(): List<TrustedV16ServiceIdEntity> = dispatch.trustedV16ServiceIds()

    suspend fun assess(exporterId: String): V16ServiceTrustDecision {
        val normalized = TechnicianIdCodec.normalize(exporterId)
            ?: throw IllegalArgumentException("V16 Service file source ID is invalid")
        val own = localIdentity().technicianId
        val ownCanonical = TechnicianIdCodec.normalize(own) ?: error("Local technician identity is invalid")
        val trusted = dispatch.trustedV16ServiceId(normalized)
        return when {
            normalized == ownCanonical -> V16ServiceTrustDecision(normalized, "This device", V16ServiceSourceTrust.TRUSTED)
            trusted != null -> V16ServiceTrustDecision(normalized, trusted.name, V16ServiceSourceTrust.TRUSTED)
            else -> V16ServiceTrustDecision(normalized, null, V16ServiceSourceTrust.NOT_TRUSTED)
        }
    }

    suspend fun requireTrusted(exporterId: String) {
        val decision = assess(exporterId)
        require(decision.canImport) { "Add this V16 Service source ID to Trusted IDs before importing." }
    }

    /** Caller owns a Room transaction so the final import decision and first mutation share one boundary. */
    suspend fun requireTrustedInCurrentTransaction(exporterId: String) {
        val normalized = TechnicianIdCodec.normalize(exporterId)
            ?: throw IllegalArgumentException("V16 Service file source ID is invalid")
        val ownId = dispatch.technicianIdentity()?.technicianId ?: run {
            val now = System.currentTimeMillis()
            TechnicianIdentityEntity(technicianId = TechnicianIdCodec.generate(), displayName = "Technician", createdAtEpochMillis = now, modifiedAtEpochMillis = now)
                .also { dispatch.insertTechnicianIdentity(it) }.technicianId
        }
        val ownCanonical = TechnicianIdCodec.normalize(ownId) ?: error("Local technician identity is invalid")
        require(normalized == ownCanonical || dispatch.trustedV16ServiceId(normalized) != null) {
            "Add this V16 Service source ID to Trusted IDs before importing."
        }
    }

    suspend fun add(peerIdInput: String, nameInput: String): TrustedV16ServiceIdEntity {
        val peerId = TechnicianIdCodec.normalize(peerIdInput)
            ?: throw IllegalArgumentException("Enter a valid V16 Service ID")
        val name = nameInput.trim()
        require(name.isNotEmpty()) { "Friendly name is required" }
        require(name.length <= 80) { "Friendly name must be 80 characters or fewer" }
        val own = localIdentity().technicianId
        val ownCanonical = TechnicianIdCodec.normalize(own) ?: error("Local technician identity is invalid")
        require(peerId != ownCanonical) { "This device's own ID is already trusted" }
        require(dispatch.trustedV16ServiceId(peerId) == null) { "This ID is already trusted" }
        val now = System.currentTimeMillis()
        val entity = TrustedV16ServiceIdEntity(peerId, name, now, now)
        dispatch.insertTrustedV16ServiceId(entity)
        return entity
    }

    suspend fun rename(peerIdInput: String, nameInput: String) {
        val peerId = TechnicianIdCodec.normalize(peerIdInput) ?: throw IllegalArgumentException("Invalid V16 Service ID")
        val row = dispatch.trustedV16ServiceId(peerId) ?: error("Trusted ID no longer exists")
        val name = nameInput.trim()
        require(name.isNotEmpty()) { "Friendly name is required" }
        require(name.length <= 80) { "Friendly name must be 80 characters or fewer" }
        dispatch.updateTrustedV16ServiceId(row.copy(name = name, modifiedAtEpochMillis = System.currentTimeMillis()))
    }

    suspend fun remove(peerIdInput: String) {
        val peerId = TechnicianIdCodec.normalize(peerIdInput) ?: throw IllegalArgumentException("Invalid V16 Service ID")
        require(dispatch.deleteTrustedV16ServiceId(peerId) == 1) { "Trusted ID no longer exists" }
    }
}
