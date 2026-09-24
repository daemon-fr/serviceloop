package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.TechnicianIdCodec
import org.junit.Assert.*
import org.junit.Test

class TechnicianIdCodecTest {
    @Test fun knownChecksumVectorIsStable() {
        assertEquals("J6", TechnicianIdCodec.checksum("0123456789AB"))
        assertEquals("SLT-0123-4567-89AB-J6", TechnicianIdCodec.normalize("slt0123456789abj6"))
    }

    @Test fun generationUsesCanonicalShapeAlphabetChecksumAndIsUnique() {
        val values = List(1_000) { TechnicianIdCodec.generate() }
        assertEquals(values.size, values.toSet().size)
        values.forEach {
            assertTrue(it.matches(Regex("SLT-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{2}")))
            assertEquals(it, TechnicianIdCodec.normalize(it))
        }
    }

    @Test fun validationRejectsMutationsAmbiguityAndBadLengths() {
        val valid = "SLT-0123-4567-89AB-J6"
        assertNotNull(TechnicianIdCodec.normalize(valid.lowercase().replace("-", "")))
        assertNull(TechnicianIdCodec.normalize(valid.replace('A', 'C')))
        assertNull(TechnicianIdCodec.normalize(valid.dropLast(1) + "7"))
        assertNull(TechnicianIdCodec.normalize(valid.replace('0', 'O')))
        assertNull(TechnicianIdCodec.normalize("random arbitrary text"))
        assertNull(TechnicianIdCodec.normalize(valid.dropLast(1)))
        assertNull(TechnicianIdCodec.normalize("${valid}0"))
    }

}
