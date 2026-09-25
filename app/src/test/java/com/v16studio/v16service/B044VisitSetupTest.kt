package com.v16studio.v16service

import com.v16studio.v16service.data.validateNewBookedVisitDate
import com.v16studio.v16service.domain.CustomerType
import com.v16studio.v16service.domain.WorkSubjectType
import com.v16studio.v16service.ui.CustomerCreationDraft
import com.v16studio.v16service.ui.VisitSetupDraft
import com.v16studio.v16service.ui.VisitSetupMode
import com.v16studio.v16service.ui.VisitSetupTaskEditorState
import com.v16studio.v16service.ui.VisitSetupTaskDraft
import com.v16studio.v16service.ui.VisitSetupTemplateSuggestion
import com.v16studio.v16service.ui.applyVisitSetupTemplateSuggestion
import com.v16studio.v16service.ui.dispatchItemIdForWorkKey
import com.v16studio.v16service.ui.restoreVisitSetupTaskEditorState
import com.v16studio.v16service.ui.saveVisitSetupTaskEditorState
import com.v16studio.v16service.ui.selectVisitSetupTemplateExplicitly
import com.v16studio.v16service.ui.suggestVisitSetupTemplate
import com.v16studio.v16service.ui.VisitSetupTemplateSuggestionSource
import com.v16studio.v16service.ui.visitSetupIsDirty
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class B044VisitSetupTest {
    @Test
    fun taskEditorStateSaverPreservesEquipmentTemplateAndEditingIdentity() {
        val original = VisitSetupTaskEditorState(
            taskName = "Inspect HVAC unit",
            subjectType = WorkSubjectType.EQUIPMENT,
            equipmentId = "equipment-1",
            equipmentDescription = "Roof unit",
            reusableTemplateId = "template-1",
            editingTaskId = "task-1",
            templateSelectionExplicit = true,
        )
        val saved = saveVisitSetupTaskEditorState(original)

        assertEquals(original, restoreVisitSetupTaskEditorState(saved))
    }

    @Test
    fun customerCreationDraftProducesCanonicalCustomerAndDefaultSite() {
        val input = CustomerCreationDraft(
            name = "Northside Foods",
            contactName = "Mara",
            phone = "555-0100",
            email = "mara@example.com",
            privateNote = "Gate code is private",
            customerType = CustomerType.ONE_TIME,
            siteName = "Main plant",
            siteAddress = "1 Test Road",
        ).toInput()

        assertEquals("Northside Foods", input.customer.name)
        assertEquals(CustomerType.ONE_TIME, input.customer.customerType)
        assertEquals("Main plant", input.site.name)
        assertEquals("1 Test Road", input.site.address)
        assertTrue(input.site.isDefault)
    }

    @Test
    fun visitSetupDirtyStateIgnoresPresentationModeAndSiteSelectionOnly() {
        val baseline = VisitSetupDraft(serviceDate = "2026-09-23")
        assertFalse(visitSetupIsDirty(baseline, baseline.copy(mode = VisitSetupMode.NEW)))
        assertFalse(visitSetupIsDirty(baseline, baseline.copy(siteId = "site-1")))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(selectedPlanIds = setOf("plan-1"))))
        assertTrue(visitSetupIsDirty(baseline, baseline.copy(tasks = listOf(VisitSetupTaskDraft(taskName = "Inspect", subjectType = WorkSubjectType.SITE, equipmentId = null, equipmentDescription = "", reusableTemplateId = null)))))
    }

    @Test
    fun dispatchPlanItemIdsAreStableWithinAnEditorSessionAndUniquePerWorkKey() {
        val ids = mutableMapOf<String, String>()

        val first = dispatchItemIdForWorkKey(ids, "PLAN:annual-pump")
        val sameWork = dispatchItemIdForWorkKey(ids, "PLAN:annual-pump")
        val otherWork = dispatchItemIdForWorkKey(ids, "PLAN:boiler-room")

        assertEquals(first, sameWork)
        assertNotEquals(first, otherWork)
        assertTrue(first.matches(Regex("[0-9a-f-]{36}")))
        assertEquals(setOf("PLAN:annual-pump", "PLAN:boiler-room"), ids.keys)
    }

    @Test
    fun newBookedVisitsRejectDatesBeforeTheBusinessDateButAllowTodayAndFuture() {
        val today = LocalDate.of(2026, 9, 22)

        assertThrows(IllegalArgumentException::class.java) {
            validateNewBookedVisitDate("BOOKED", "2026-09-21", today)
        }
        validateNewBookedVisitDate("BOOKED", "2026-09-22", today)
        validateNewBookedVisitDate("BOOKED", "2026-09-23", today)
        validateNewBookedVisitDate("HISTORICAL", "2026-09-21", today)
    }

    @Test
    fun contextualSuggestionUsesOneActiveGlobalTemplate() {
        val suggestion = suggestVisitSetupTemplate(WorkSubjectType.SITE, null, emptyMap(), listOf(activeTemplate("one")))

        assertEquals("one", suggestion.autoSelectedTemplateId)
        assertEquals(VisitSetupTemplateSuggestionSource.ONLY_ACTIVE_TEMPLATE, suggestion.source)
    }

    @Test
    fun contextualSuggestionDoesNotGuessBetweenMultipleGlobalTemplates() {
        val suggestion = suggestVisitSetupTemplate(WorkSubjectType.SITE, null, emptyMap(), listOf(activeTemplate("one"), activeTemplate("two")))

        assertEquals(null, suggestion.autoSelectedTemplateId)
        assertTrue(suggestion.preferredTemplateIds.isEmpty())
    }

    @Test
    fun equipmentContextWinsAndFiltersInactiveAndDuplicateBindings() {
        val suggestion = suggestVisitSetupTemplate(
            WorkSubjectType.EQUIPMENT,
            "equipment-1",
            mapOf("equipment-1" to setOf("one", "one", "disabled"), "equipment-2" to setOf("other")),
            listOf(activeTemplate("one"), activeTemplate("other"), activeTemplate("disabled", "DISABLED")),
        )

        assertEquals("one", suggestion.autoSelectedTemplateId)
        assertEquals(VisitSetupTemplateSuggestionSource.EQUIPMENT_PLAN, suggestion.source)
        assertEquals(listOf("one"), suggestion.preferredTemplateIds)
    }

    @Test
    fun ambiguousEquipmentContextIsPreferredButNotAutoSelected() {
        val suggestion = suggestVisitSetupTemplate(
            WorkSubjectType.EQUIPMENT,
            "equipment-1",
            mapOf("equipment-1" to setOf("one", "two")),
            listOf(activeTemplate("one"), activeTemplate("two")),
        )

        assertEquals(null, suggestion.autoSelectedTemplateId)
        assertEquals(listOf("one", "two"), suggestion.preferredTemplateIds)
    }

    @Test
    fun siteContextSuggestsOneTemplateAndUnspecifiedEquipmentUsesSiteContext() {
        val suggestion = suggestVisitSetupTemplate(
            WorkSubjectType.EQUIPMENT,
            null,
            mapOf("equipment-1" to setOf("one"), "equipment-2" to setOf("one")),
            listOf(activeTemplate("one"), activeTemplate("other")),
        )

        assertEquals("one", suggestion.autoSelectedTemplateId)
        assertEquals(VisitSetupTemplateSuggestionSource.SITE_PLANS, suggestion.source)
    }

    @Test
    fun explicitTemplateChoiceAndExplicitNoneAreNeverOverwrittenByContext() {
        val suggestion = VisitSetupTemplateSuggestion(
            autoSelectedTemplateId = "suggested",
            source = VisitSetupTemplateSuggestionSource.EQUIPMENT_PLAN,
        )
        val explicit = VisitSetupTaskEditorState(reusableTemplateId = "chosen", templateSelectionExplicit = true)
        val explicitNone = VisitSetupTaskEditorState(templateSelectionExplicit = true)

        assertEquals(explicit, applyVisitSetupTemplateSuggestion(explicit, suggestion))
        assertEquals(explicitNone, applyVisitSetupTemplateSuggestion(explicitNone, suggestion))
    }

    @Test
    fun returnedTemplateWinsAutoSuggestionAndBecomesExplicit() {
        val auto = VisitSetupTaskEditorState(reusableTemplateId = "auto")
        val returned = selectVisitSetupTemplateExplicitly(auto, "created")

        assertEquals("created", returned.reusableTemplateId)
        assertTrue(returned.templateSelectionExplicit)
        assertEquals(returned, applyVisitSetupTemplateSuggestion(returned, VisitSetupTemplateSuggestion("other")))
    }

    private fun activeTemplate(id: String, state: String = "ACTIVE") =
        com.v16studio.v16service.domain.TemplateSummary(id, "T-$id", id, 1, 1, state)
}
