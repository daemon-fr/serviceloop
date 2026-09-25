package com.v16studio.v16service

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.v16studio.v16service.domain.OperationalDashboardProjection
import com.v16studio.v16service.domain.OperationalDashboardSection
import com.v16studio.v16service.domain.OperationalWorkItem
import com.v16studio.v16service.domain.OperationalWorkKind
import com.v16studio.v16service.domain.OperationalWorkState
import com.v16studio.v16service.domain.WorkScope
import com.v16studio.v16service.ui.designsystem.OperationalDashboard
import com.v16studio.v16service.ui.theme.V16ServiceTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationalDashboardUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun defaultExpansionToggleSemanticsAndFiveItemPreviewAreStable() {
        val projection = OperationalDashboardProjection(
            WorkScope.Customer("customer-a"),
            listOf(
                section(OperationalWorkKind.VISIT, OperationalWorkState.IN_PROGRESS, 1),
                section(OperationalWorkKind.VISIT, OperationalWorkState.OVERDUE, 6),
            ),
        )
        var viewAllCount = 0
        compose.setContent {
            V16ServiceTheme {
                OperationalDashboard(
                    projection = projection,
                    onOpenItem = {},
                    onViewAll = { viewAllCount = it.itemCount },
                    modifier = Modifier.width(360.dp),
                )
            }
        }

        val workingHeader = compose.onNodeWithTag("operational-section-header-visit-in_progress")
        val overdueHeader = compose.onNodeWithTag("operational-section-header-visit-overdue")
        workingHeader.assertIsDisplayed().assertHasClickAction()
        overdueHeader.assertIsDisplayed().assertHasClickAction()
        val workingSemantics = workingHeader.fetchSemanticsNode().config
        assertEquals(Role.Button, workingSemantics[SemanticsProperties.Role])
        assertEquals("Expanded", workingSemantics[SemanticsProperties.StateDescription])
        assertEquals("Collapsed", overdueHeader.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onNodeWithTag("operational-work-row-VISIT-IN_PROGRESS-1").assertIsDisplayed()
        compose.onAllNodesWithTag("operational-work-row-VISIT-OVERDUE-1").assertCountEquals(0)

        overdueHeader.performClick()
        assertEquals("Expanded", overdueHeader.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        (1..5).forEach { index -> compose.onNodeWithTag("operational-work-row-VISIT-OVERDUE-$index").assertIsDisplayed() }
        compose.onAllNodesWithTag("operational-work-row-VISIT-OVERDUE-6").assertCountEquals(0)
        compose.onNodeWithText("View all 6 overdue visits").fetchSemanticsNode()
        compose.onNodeWithText("View all 6 overdue visits").performClick()
        assertEquals(6, viewAllCount)

        workingHeader.performClick()
        assertEquals("Collapsed", workingHeader.fetchSemanticsNode().config[SemanticsProperties.StateDescription])
        compose.onAllNodesWithTag("operational-work-row-VISIT-IN_PROGRESS-1").assertCountEquals(0)
        compose.onNodeWithTag("operational-work-row-VISIT-OVERDUE-1").assertIsDisplayed()
    }

    @Test fun emptyProjectionRendersAnEmptyMessageWithoutZeroCountHeaders() {
        compose.setContent {
            V16ServiceTheme {
                OperationalDashboard(
                    projection = OperationalDashboardProjection(WorkScope.Global, emptyList()),
                    onOpenItem = {},
                    onViewAll = {},
                )
            }
        }
        compose.onNodeWithTag("operational-dashboard-empty").assertIsDisplayed()
        listOf("visit-in_progress", "visit-overdue", "service-overdue", "follow_up-overdue").forEach { key ->
            compose.onAllNodesWithTag("operational-section-header-$key").assertCountEquals(0)
        }
        compose.onNodeWithText("No current work needs attention.").assertIsDisplayed()
    }

    private fun section(kind: OperationalWorkKind, state: OperationalWorkState, count: Int) = OperationalDashboardSection(
        kind = kind,
        state = state,
        title = com.v16studio.v16service.domain.OperationalWorkClassifier.sectionTitle(kind, state),
        items = (1..count).map { index ->
            OperationalWorkItem(
                kind = kind,
                state = state,
                recordId = "$kind-$state-$index",
                customerId = "customer-a",
                siteId = "site-a",
                equipmentId = null,
                displayReference = "REF-$index",
                displayTitle = "Work $index",
                displayContext = "North site",
                dueDate = LocalDate.parse("2026-09-16").plusDays(index.toLong()),
                scheduledAtEpochMillis = null,
                modifiedAtEpochMillis = index.toLong(),
            )
        },
    )
}
