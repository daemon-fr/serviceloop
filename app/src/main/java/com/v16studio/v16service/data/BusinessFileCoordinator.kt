package com.v16studio.v16service.data

import kotlinx.coroutines.sync.Mutex

/** Serializes DB/file checkpoints so a Complete backup cannot race a durable file mutation. */
internal object BusinessFileCoordinator {
    val mutex = Mutex()
    val photoReportMetadataMutex = Mutex()
}
