package com.v16studio.v16service.ui

import com.v16studio.v16service.ui.designsystem.V16ServiceUiTokens
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
        assertEquals(V16ServiceUiTokens.Size.touchMin + V16ServiceUiTokens.Space.lg, workNewVisitListBottomPadding(WorkNewVisitActionState.FLOATING))
        assertEquals(V16ServiceUiTokens.Space.sm, workNewVisitListBottomPadding(WorkNewVisitActionState.UNRESOLVED))
        assertEquals(V16ServiceUiTokens.Space.sm, workNewVisitListBottomPadding(WorkNewVisitActionState.DOCKED))
    }
}
