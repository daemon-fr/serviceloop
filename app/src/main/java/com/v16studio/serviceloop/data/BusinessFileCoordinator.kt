package com.v16studio.serviceloop.data

import kotlinx.coroutines.sync.Mutex

/** Serializes DB/file checkpoints so a Complete backup cannot race a durable file mutation. */
internal object BusinessFileCoordinator {
    val mutex = Mutex()
}
