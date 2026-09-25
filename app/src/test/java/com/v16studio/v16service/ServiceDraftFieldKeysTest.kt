package com.v16studio.v16service

import com.v16studio.v16service.domain.ServiceDraftFieldKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceDraftFieldKeysTest {
    @Test
    fun parseQuestionFieldAcceptsOnlySupportedDurableQuestionKeys() {
        assertEquals(
            ServiceDraftFieldKeys.ParsedQuestionField("q1", ServiceDraftFieldKeys.QuestionFieldKind.VALUE),
            ServiceDraftFieldKeys.parseQuestionField("question:q1:value"),
        )
        assertEquals(
            ServiceDraftFieldKeys.ParsedQuestionField("q1", ServiceDraftFieldKeys.QuestionFieldKind.ISSUE),
            ServiceDraftFieldKeys.parseQuestionField("question:q1:issue"),
        )
        assertEquals(
            ServiceDraftFieldKeys.ParsedQuestionField("q1", ServiceDraftFieldKeys.QuestionFieldKind.NOT_APPLICABLE),
            ServiceDraftFieldKeys.parseQuestionField("question:q1:na"),
        )
    }

    @Test
    fun parseQuestionFieldRejectsMalformedOrUnknownKeys() {
        listOf(
            "question::value",
            "question:q1:other",
            "question:q1:value:extra",
            "garbage",
        ).forEach { fieldKey -> assertNull(ServiceDraftFieldKeys.parseQuestionField(fieldKey)) }
    }
}
