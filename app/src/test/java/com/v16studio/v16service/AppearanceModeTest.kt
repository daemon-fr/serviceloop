package com.v16studio.v16service

import com.v16studio.v16service.ui.theme.AppearanceMode
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
        assertEquals("Always use V16 Service light appearance.", AppearanceMode.LIGHT.meaning)
        assertEquals("Always use V16 Service dark appearance.", AppearanceMode.DARK.meaning)
    }
}
