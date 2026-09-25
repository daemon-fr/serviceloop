package com.v16studio.v16service

import com.v16studio.v16service.data.TechnicianIdCodec
import org.junit.Assert.*
import org.junit.Test

class TechnicianIdCodecTest {
    @Test fun knownChecksumVectorIsStable() {
        assertEquals("YJ", TechnicianIdCodec.checksum("0123456789AB"))
        assertEquals("V16S-0123-4567-89AB-YJ", TechnicianIdCodec.normalize("v16s0123456789abyj"))
    }

    @Test fun generationUsesCanonicalShapeAlphabetChecksumAndIsUnique() {
        val values = List(1_000) { TechnicianIdCodec.generate() }
        assertEquals(values.size, values.toSet().size)
        values.forEach {
            assertTrue(it.matches(Regex("V16S-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{2}")))
            assertEquals(it, TechnicianIdCodec.normalize(it))
        }
    }

    @Test fun validationRejectsMutationsAmbiguityAndBadLengths() {
        val valid = "V16S-0123-4567-89AB-YJ"
        assertNotNull(TechnicianIdCodec.normalize(valid.lowercase().replace("-", "")))
        assertNull(TechnicianIdCodec.normalize(valid.replace('A', 'C')))
        assertNull(TechnicianIdCodec.normalize(valid.dropLast(1) + "7"))
        assertNull(TechnicianIdCodec.normalize(valid.replace('0', 'O')))
        assertNull(TechnicianIdCodec.normalize("random arbitrary text"))
        assertNull(TechnicianIdCodec.normalize(valid.dropLast(1)))
        assertNull(TechnicianIdCodec.normalize("${valid}0"))
    }

}
