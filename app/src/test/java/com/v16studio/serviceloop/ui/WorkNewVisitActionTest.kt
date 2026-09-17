package com.v16studio.serviceloop.ui

import com.v16studio.serviceloop.ui.designsystem.ServiceLoopUiTokens
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkNewVisitActionTest {
    @Test fun initialResolutionWaitsForMeaningfulLayoutAndShortPageNeverFloats() {
        var state = WorkNewVisitActionState.UNRESOLVED
        state = resolveWorkNewVisitActionState(state, WorkNewVisitLayout(0, 0, false))
        assertEquals(WorkNewVisitActionState.UNRESOLVED, state)

        state = resolveWorkNewVisitActionState(state, WorkNewVisitLayout(5, 0, false))
        assertEquals(WorkNewVisitActionState.UNRESOLVED, state)

        state = resolveWorkNewVisitActionState(state, WorkNewVisitLayout(2, 2, true))
        assertEquals(WorkNewVisitActionState.DOCKED, state)
    }

    @Test fun longPageFloatsThenDocksOnceAndNeverFloatsAgain() {
        var state = resolveWorkNewVisitActionState(
            WorkNewVisitActionState.UNRESOLVED,
            WorkNewVisitLayout(24, 8, false),
        )
        assertEquals(WorkNewVisitActionState.FLOATING, state)

        state = resolveWorkNewVisitActionState(state, WorkNewVisitLayout(24, 8, true))
        assertEquals(WorkNewVisitActionState.DOCKED, state)
        state = resolveWorkNewVisitActionState(state, WorkNewVisitLayout(24, 8, false))
        assertEquals(WorkNewVisitActionState.DOCKED, state)
    }

    @Test fun onlyFloatingStateRetainsFloatingClearance() {
        assertEquals(ServiceLoopUiTokens.Size.touchMin + ServiceLoopUiTokens.Space.lg, workNewVisitListBottomPadding(WorkNewVisitActionState.FLOATING))
        assertEquals(ServiceLoopUiTokens.Space.sm, workNewVisitListBottomPadding(WorkNewVisitActionState.UNRESOLVED))
        assertEquals(ServiceLoopUiTokens.Space.sm, workNewVisitListBottomPadding(WorkNewVisitActionState.DOCKED))
    }
}
