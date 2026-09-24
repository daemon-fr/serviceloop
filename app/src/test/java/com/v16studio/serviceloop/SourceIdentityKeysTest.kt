package com.v16studio.serviceloop

import com.v16studio.serviceloop.data.SourceIdentityKeys
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceIdentityKeysTest {
    @Test fun separatorsAndNullRevisionCannotAliasAnotherSource() {
        assertNotEquals(SourceIdentityKeys.evidence("origin", "photo|revision", "one"),
            SourceIdentityKeys.evidence("origin", "photo", "revision|one"))
        assertNotEquals(SourceIdentityKeys.evidence("origin", "photo", null),
            SourceIdentityKeys.evidence("origin", "photo", "standalone"))
    }
}
