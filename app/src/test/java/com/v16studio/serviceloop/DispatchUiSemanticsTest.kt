package com.v16studio.serviceloop

import com.v16studio.serviceloop.ui.canceledDispatchVisitMessage
import com.v16studio.serviceloop.domain.VisitCancellationOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class DispatchUiSemanticsTest {
    @Test fun canceledDispatchCopyMatchesCancellationProvenance() {
        assertEquals("Canceled on this device.", canceledDispatchVisitMessage(VisitCancellationOrigin.LOCAL.code))
        assertEquals("Canceled by Coordinator.", canceledDispatchVisitMessage(VisitCancellationOrigin.COORDINATOR.code))
        assertEquals("Canceled because this Technician assignment was removed.", canceledDispatchVisitMessage(VisitCancellationOrigin.ASSIGNMENT_REMOVAL.code))
        assertEquals("Canceled.", canceledDispatchVisitMessage(null))
    }
}
