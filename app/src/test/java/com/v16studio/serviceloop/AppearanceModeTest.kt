package com.v16studio.serviceloop

import com.v16studio.serviceloop.ui.theme.AppearanceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceModeTest {
    @Test fun missingAndInvalidValuesUseSystemDefault() {
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStored(null))
        assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStored("unknown"))
    }

    @Test fun supportedModesCarryTheRequiredMeaning() {
        assertEquals("System default", AppearanceMode.SYSTEM.label)
        assertEquals("Follow Android appearance.", AppearanceMode.SYSTEM.meaning)
        assertEquals("Always use ServiceLoop light appearance.", AppearanceMode.LIGHT.meaning)
        assertEquals("Always use ServiceLoop dark appearance.", AppearanceMode.DARK.meaning)
    }
}
